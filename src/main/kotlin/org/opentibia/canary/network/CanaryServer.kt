package org.opentibia.canary.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.GlobalEventExecutor
import javax.sql.DataSource
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.InMemoryAccountRepository
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.spawns.MonsterSpawnPath
import org.opentibia.canary.creatures.spawns.dispatcherSpawnScheduler
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.GameState_t
import org.opentibia.canary.game.scheduling.CreatureAsyncTaskBuckets
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.iologindata.IOLoginData
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.codec.RateLimiterHandler
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.protocol.GameHandler
import org.opentibia.canary.network.protocol.GameMarketEscrow
import org.opentibia.canary.network.protocol.LoginHandler
import org.opentibia.canary.network.protocol.StatusHandler
import org.opentibia.canary.systems.imbuement.ImbuementDecay
import org.opentibia.canary.systems.imbuement.ImbuementEngine
import org.opentibia.canary.systems.imbuement.Imbuements
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/** Lifecycle seam for the network listeners (`CanaryServer::start`/`stop`). */
interface ListenerBoot {
    fun start()
    fun stop()

    /**
     * `ConnectionManager::closeAll` equivalent (src/game/game.cpp:9510): shutdown every
     * live client connection before the dispatcher and persistence are drained.
     */
    fun closeConnections() {}
}

/**
 * Netty-based TCP Server managing inbound client connections across Login (7171) and Game (7172) ports.
 */
class CanaryServer(
    private val loginPort: Int = 7171,
    private val gamePort: Int = 7172,
    private val accountRepository: AccountRepository = InMemoryAccountRepository(),
    private val gameMap: GameMap = GameMap(),
    /**
     * `g_dispatcher()` shared by the world. When absent the composition root
     * owns one composed with [configManager] (`getConfiguredDispatcherBudgets`,
     * dispatcher.cpp:38-49); a booted dispatcher stays caller-owned.
     */
    dispatcher: GameDispatcher? = null,
    private val rsaCipher: RsaCipher = RsaCipher(),
    private val luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() },
    /** `Item::items` registry (`src/items/items.hpp:389`) loaded by the bootstrap. */
    private val itemTypes: Items? = null,
    /** `g_imbuements()` registry (imbuements.cpp:47) loaded by the bootstrap. */
    private val imbuements: Imbuements? = null,
    /** `g_database()` source backing the composed `IOMarket::getInstance()` (iomarket.cpp). */
    private val dataSource: DataSource? = null,
    suppliedGame: Game? = null,
    /**
     * `g_configManager()` composed by the bootstrap: read by the world `Game`
     * for the `Player` host values (`MAX_MESSAGEBUFFER`, game.cpp:11624) and by
     * the dispatcher's adaptive budgets. `null` keeps the getter defaults.
     */
    private val configManager: ConfigManager? = null,
    /**
     * `g_monsters()` (`src/creatures/monsters/monsters.hpp:242`) composed by the
     * bootstrap; the production monster spawn path resolves block names and
     * builds each spawned monster from its registered type. `null` keeps the
     * spawn path uncomposed (embedded/test use).
     */
    internal val monsters: Monsters? = null,
    /**
     * `Creature::getCreatureAsyncTaskBuckets` shared world state
     * (creature.cpp:34-55) composed by the bootstrap; when absent the listener
     * owns one over its dispatcher. The same instance reaches [game] and
     * [monsterPipelinesHost] so monster and non-monster batches share the world
     * buckets.
     */
    suppliedCreatureAsyncTaskBuckets: CreatureAsyncTaskBuckets? = null,
    /**
     * `g_monsterComputeService()` (src/game/scheduling/monster_compute_service.hpp:101)
     * started by the bootstrap (`CanaryServer::run`, canary_server.cpp:215) and
     * shared with the world dispatcher's `WorkerCompletion` lane. Composing it
     * here builds the production [monsterPipelinesHost]; `null` keeps the
     * monster pipeline host uncomposed (embedded/test use).
     */
    private val monsterComputeService: MonsterComputeService? = null,
    /**
     * `g_vocations()` registry (src/creatures/players/vocations/vocation.hpp:122)
     * loaded by the bootstrap; consumed by the world [Game] highscore queries
     * (game.cpp:9968) and player vocation accessors.
     */
    internal val vocations: Vocations? = null,
    /**
     * `IOLoginData::loadPlayerById` login-load seam (src/io/iologindata.cpp:81-86)
     * handed to every game session ([createGameHandler]) so `ProtocolGame::login`
     * materializes the persisted player before `Game::placeCreature`
     * (protocolgame.cpp:1023). `null` keeps the stub player shell.
     */
    internal val ioLoginData: IOLoginData? = null,
) : ListenerBoot {
    /**
     * `g_dispatcher()` (`CanaryServer::run`, src/canary_server.cpp:132): the
     * injected booted dispatcher or one this composition root owns.
     */
    private val dispatcher: GameDispatcher = dispatcher ?: GameDispatcher(config = configManager)

    /**
     * `Creature::getCreatureAsyncTaskBuckets` shared world state
     * (creature.cpp:34-55): the bootstrap-owned registry or one this listener
     * owns, shared by the `Game` non-monster bindings and [monsterPipelinesHost].
     */
    internal val creatureAsyncTaskBuckets: CreatureAsyncTaskBuckets =
        suppliedCreatureAsyncTaskBuckets ?: CreatureAsyncTaskBuckets(dispatcher = { this.dispatcher })

    /**
     * `Player::muteCountMap` (player.hpp:281): the C++ static map shared by
     * every player, replaced by this composition-root-owned instance
     * (AGENTS.md 1.1) and injected into each session's `Player`.
     */
    internal val muteCountMap: MutableMap<Long, Int> = mutableMapOf()

    val scriptingEnvironment: LuaEnvironment get() = luaEnvironment

    /**
     * `IOMarket::getInstance()` composition over the booted [dataSource]. The escrow
     * resolves the listener-owned `g_game()` lazily (see [GameMarketEscrow]) and the
     * expired-offer recheck reschedules on the world lane
     * (`IOMarket::checkExpiredOffers`, iomarket.cpp:234-247) instead of the C++
     * Maintenance lane, keeping player inbox/bank mutations confined (AGENTS.md 1.2).
     */
    val ioMarket: IOMarket? = dataSource?.let { source ->
        IOMarket(
            source,
            escrow = GameMarketEscrow({ game }, itemTypes, imbuements),
            scheduleCheck = { delayMillis ->
                this.dispatcher.schedule(delayMillis, DispatcherLane.WorldCommit) {
                    ioMarket?.checkExpiredOffers()
                }
            },
        )
    }

    /**
     * Production `MonsterPipelinesHost` composition: the bootstrap-started
     * [monsterComputeService] owns the off-thread computes, the world map
     * resolves the spectator sectors, `g_game().getCreatureByID`
     * (game.cpp:1296) re-resolves the deferred targets and the world
     * [creatureAsyncTaskBuckets] are shared with [game]'s non-monster batches.
     * The dispatcher is attached before any monster can register
     * (`Creature::getCreatureAsyncTaskBuckets` reads it at enqueue time) and the
     * remaining coordinator seams are bound after [game] exists.
     */
    internal val monsterPipelinesHost: MonsterPipelinesHost? = monsterComputeService?.let { service ->
        MonsterPipelinesHost(
            service = service,
            mapAccess = gameMap,
            resolveCreature = { id -> game.getCreatureByID(id) },
            creatureAsyncTaskBuckets = creatureAsyncTaskBuckets,
        ).also { host ->
            host.dispatcher = this.dispatcher
            host.monsterPerfTestFriendlyFire = {
                val key = ConfigKeys.byName("MONSTER_PERF_TEST_FRIENDLY_FIRE")
                key != null && (configManager?.getBoolean(key, key.defaultValue as Boolean) ?: false)
            }
        }
    }

    /**
     * `g_game()` equivalent shared by every game connection served by this listener,
     * composed with the booted item/imbuement/vocation registries, market, config,
     * world-shared async buckets and the production [monsterPipelinesHost]. The
     * booted [monsters] registry backs the `g_monsters().getMonsterType` provider
     * (`Monster::m_monsterType`, monster.hpp:363).
     */
    val game: Game = suppliedGame ?: Game(
        gameMap,
        this.dispatcher,
        itemTypes = itemTypes,
        ioMarket = ioMarket,
        imbuements = imbuements,
        vocations = vocations,
        configManager = configManager,
        creatureAsyncTaskBuckets = creatureAsyncTaskBuckets,
        monsterHost = monsterPipelinesHost,
        monsterTypeProvider = { name -> monsters?.getMonsterType(name) },
    )

    init {
        // Coordinator call-site bindings of `MonsterPipelinesHost` over the
        // composed world (`Game::addCreatureCheck`/`removeCreatureCheck`,
        // game.cpp:7659/7687, and `Game::internalCreatureTurn`, game.cpp:7599).
        monsterPipelinesHost?.apply {
            addCreatureCheck = { monster -> game.addCreatureCheck(monster) }
            removeCreatureCheck = { monster -> game.removeCreatureCheck(monster) }
            internalCreatureTurn = { monster, direction -> game.internalCreatureTurn(monster, direction) }

            // `CreatureEvents::getEventByName` (creatureevent.cpp:43-52): the
            // boot-time registry lookup consulted by `Creature::registerCreatureEvent`
            // (creature.cpp:1674-1693), so unknown creature event names are
            // rejected in production.
            creatureEventRegistered = { name -> luaEnvironment.creatureEvents.get(name) != null }

            // `MonsterTypeFunctions::luaMonsterTypeRegisterEvent`
            // (monster_type_functions.cpp:1235-1242): a late registration walks
            // `g_game().getMonsters()` and pushes the event onto every live
            // monster of the type unless the game is still starting up.
            luaEnvironment.worldOperations?.attachMonsterEvents(
                liveMonsterEvents = { monsterType, name -> registerCreatureEventForType(monsterType, name) },
                isStartup = { game.getGameState() == GameState_t.GAME_STATE_STARTUP },
            )
        }
    }

    // --- monster spawn path (`IOMap::loadMonsters` + `SpawnsMonster::startup`) ---

    /**
     * Production monster spawn path: `IOMap::loadMonsters`
     * (src/io/iomap.hpp:32-46) over the booted `g_monsters()` registry plus the
     * `map.spawnsMonster.startup()` half of `Game::setGameState(GAME_STATE_INIT)`
     * (src/game/game.cpp:970). [start] loads the map's spawn XML and starts the
     * engine on the WorldCommit lane so every block places through
     * `Game::internalPlaceCreature` (src/game/game.cpp:1495) confined to the
     * world thread (AGENTS.md 1.2); [stop] drains it like `Game::shutdown`
     * (src/game/game.cpp:9502). Composed only when a `Monsters` registry and a
     * config manager are injected.
     */
    internal val monsterSpawnPath: MonsterSpawnPath? =
        if (monsters != null && configManager != null) {
            MonsterSpawnPath(
                game = game,
                map = gameMap,
                monsterTypes = monsters,
                spawnScheduler = dispatcherSpawnScheduler(this.dispatcher),
                config = configManager,
                // `Monster::onSpawn(position)` (monster.cpp:627-648) dispatched by
                // `SpawnMonster::spawnMonster` (spawn_monster.cpp:220) reaches the
                // world host entry of the placed monster.
                onMonsterSpawn = { monster, position -> monsterPipelinesHost?.onSpawn(monster.id, position) },
            )
        } else {
            null
        }

    /**
     * `g_game().transformItem(item, item->getID(), charges - 1)` binding
     * (player.cpp:1065, 7588, 7697, 7718) shared by every session's
     * `Player`/`PlayerFeatures`. The charges are read exactly like the
     * `PlayerCombatBonusState`/`PlayerMagicAffinityState` `getCharges` seam
     * (`ItemAttribute.CHARGES`).
     */
    internal val transformItemCharges: (Item) -> Unit = { item ->
        val charges = (item.getAttribute(ItemAttribute.CHARGES) as? Number)?.toInt() ?: 0
        game.transformItem(item, item.id, charges - 1)
    }

    /**
     * Shared `g_imbuementDecay()` engine
     * (src/creatures/players/imbuements/imbuements.hpp:167) composed once at the
     * server root over the server-owned [dispatcher]: decay ticks arm on the
     * server lifecycle and [stop] drains the tracker before the bootstrap shuts
     * the dispatcher down (AGENTS.md deferred-callback lifetime safety). Every
     * connection's [GameHandler] binds `Game::playerApplyImbuement` /
     * `playerClearImbuement` to this one engine.
     */
    internal val imbuementEngine: ImbuementEngine? =
        imbuements?.let { registry -> ImbuementEngine(registry, ImbuementDecay(registry, this.dispatcher)) }

    /**
     * World-confined Lua event caller. `creatureEventsOf` is
     * `Creature::getCreatureEvents` (creature.hpp:952): a live monster resolves
     * to its host entry's attached named events (registered by
     * `Monster::Monster` and `MonsterTypeFunctions::luaMonsterTypeRegisterEvent`),
     * every other creature keeps the permissive global walk until its
     * `registerEvent` binding is wired.
     */
    private val scriptingDispatch = ScriptingDispatch(
        luaEnvironment,
        this.dispatcher,
        creatureEventsOf = { creature ->
            if (creature is Monster) monsterPipelinesHost?.typeStateOf(creature.id)?.creatureEvents else null
        },
    )

    /** `Game::playersOnline` equivalent; see [checkPlayersRecord]. */
    private val playersOnline = AtomicInteger(0)

    /** `Game::playersRecord` equivalent; see [checkPlayersRecord]. */
    private val playersRecord = AtomicInteger(0)

    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var loginChannelFuture: ChannelFuture? = null
    private var gameChannelFuture: ChannelFuture? = null
    private val stopped = AtomicBoolean(false)

    /** Live client connections; `ConnectionManager` registry equivalent for `closeAll`. */
    private val connectionChannels: ChannelGroup = DefaultChannelGroup("canary-connections", GlobalEventExecutor.INSTANCE)

    override fun start() {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        // 1. Login & Status Server Bootstrap (Port 7171)
        val loginBootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    connectionChannels.add(ch)
                    val pipeline = ch.pipeline()
                    pipeline.addLast("rateLimiter", RateLimiterHandler(maxPacketsPerSecond = 50))
                    pipeline.addLast("frameDecoder", TibiaFrameDecoder())
                    pipeline.addLast("messageEncoder", TibiaMessageEncoder())
                    pipeline.addLast("statusHandler", StatusHandler())
                    pipeline.addLast("loginHandler", LoginHandler(rsaCipher, accountRepository))
                }
            })

        loginChannelFuture = loginBootstrap.bind(loginPort).sync()
        logger.info { "Canary Login/Status listener running on port $loginPort" }

        // 2. Game World Server Bootstrap (Port 7172)
        val gameBootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    connectionChannels.add(ch)
                    val pipeline = ch.pipeline()
                    pipeline.addLast("rateLimiter", RateLimiterHandler(maxPacketsPerSecond = 100))
                    pipeline.addLast("frameDecoder", TibiaFrameDecoder())
                    pipeline.addLast("messageEncoder", TibiaMessageEncoder())
                    pipeline.addLast("gameHandler", createGameHandler())
                }
            })

        gameChannelFuture = gameBootstrap.bind(gamePort).sync()
        logger.info { "Canary Game World listener running on port $gamePort" }

        // `g_dispatcher().setQueueLatencyLoggingEnabled(true)`
        // (src/canary_server.cpp:311): the world dispatcher starts collecting
        // its queue-latency/internal-work telemetry once the server is online.
        dispatcher.setQueueLatencyLoggingEnabled(true)

        // `IOMarket::checkExpiredOffers()` runs during server startup
        // (src/canary_server.cpp:230) and reschedules itself from there.
        ioMarket?.let { market ->
            dispatcher.dispatch(DispatcherLane.WorldCommit) { market.checkExpiredOffers() }
        }

        // `Map::loadMap(loadMonsters=true)` -> `IOMap::loadMonsters`
        // (src/map/map.cpp:199-201) parses the spawn XML during map boot, then
        // `Game::setGameState(GAME_STATE_INIT)` starts the engine
        // (src/game/game.cpp:970). The start is dispatched to the WorldCommit
        // lane so `SpawnMonster::startup` places every block through
        // `Game::internalPlaceCreature` on the world thread (AGENTS.md 1.2); a
        // listener without a running dispatcher (embedded use) starts inline,
        // mirroring `Game::addCreatureCheck` (game.cpp:7676-7684).
        monsterSpawnPath?.let { spawns ->
            spawns.load()
            if (dispatcher.isRunning) {
                dispatcher.dispatch(DispatcherLane.WorldCommit) { spawns.startup() }
            } else {
                spawns.startup()
            }
        }

        // Canary fires the `startup` global events during `GAME_STATE_INIT`
        // (g_globalEvents().startup(), src/game/game.cpp:988) before the world serves
        // players. Confined to the WorldCommit lane by ScriptingDispatch.
        scriptingDispatch.globalStartup()
    }

    /** Builds the per-connection game handler wired to this server's record tracking. */
    internal fun createGameHandler(): GameHandler = GameHandler(
        rsaCipher,
        accountRepository,
        gameMap,
        dispatcher,
        luaEnvironment,
        onPlayerAppear = { onPlayerAppear() },
        onPlayerDisappear = { onPlayerDisappear() },
        itemTypes = itemTypes,
        ioMarket = ioMarket,
        imbuements = imbuements,
        imbuementEngine = imbuementEngine,
        game = game,
        muteCountMap = muteCountMap,
        transformItemCharges = transformItemCharges,
        ioLoginData = ioLoginData,
        vocations = vocations,
        monsterTypeProvider = { name -> monsters?.getMonsterType(name) },
    )

    /**
     * Mirrors `Player::onCreatureAppear` -> `Game::checkPlayersRecord`
     * (src/creatures/players/player.cpp:12300, src/game/game.cpp:9625): counts the player
     * online and fires every registered `record` global event when the count beats the
     * stored record. Returns whether a record event was registered and dispatched.
     */
    internal fun onPlayerAppear(): Boolean {
        playersOnline.incrementAndGet()
        return checkPlayersRecord()
    }

    /** Mirrors the online-player decrement of `Game::removeCreature`/player logout. */
    internal fun onPlayerDisappear() {
        playersOnline.decrementAndGet()
    }

    /**
     * `Game::checkPlayersRecord` (src/game/game.cpp:9625-9636): when the online count beats
     * the stored record, advances the record and fires each registered `record` event with
     * `(playersRecord, previousRecord)`. The CAS keeps the counters safe for concurrent
     * logins; the Lua callbacks themselves stay confined to the WorldCommit lane.
     */
    internal fun checkPlayersRecord(): Boolean {
        val current = playersOnline.get()
        var record = playersRecord.get()
        while (current > record) {
            if (playersRecord.compareAndSet(record, current)) {
                return scriptingDispatch.globalRecord(current.toLong(), record.toLong())
            }
            record = playersRecord.get()
        }
        return false
    }

    override fun closeConnections() {
        connectionChannels.close().sync()
        logger.info { "Canary Kotlin live connections closed" }
    }

    override fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        // `Game::shutdown` clears the spawns (src/game/game.cpp:9502): cancel
        // every pending spawn check before the bootstrap drains the dispatcher,
        // so no callback can outlive its owner (AGENTS.md deferred-callback
        // lifetime safety).
        monsterSpawnPath?.shutdown()
        // Drain the shared decay engine before the listener/event-loop teardown;
        // the bootstrap shuts the dispatcher down after the listeners
        // (ShutdownDrain order), so no callback can outlive its owner.
        imbuementEngine?.decay?.shutdown()
        loginChannelFuture?.channel()?.close()?.sync()
        gameChannelFuture?.channel()?.close()?.sync()
        // Join the I/O producers before the bootstrap drains the dispatcher and globals.
        workerGroup?.shutdownGracefully(0, 5, TimeUnit.SECONDS)?.sync()
        bossGroup?.shutdownGracefully(0, 5, TimeUnit.SECONDS)?.sync()
        logger.info { "Canary Kotlin Server listeners stopped" }
    }
}
