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
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.IOMarket
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
    private val dispatcher: GameDispatcher = GameDispatcher(),
    private val rsaCipher: RsaCipher = RsaCipher(),
    private val luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() },
    /** `Item::items` registry (`src/items/items.hpp:389`) loaded by the bootstrap. */
    private val itemTypes: Items? = null,
    /** `g_imbuements()` registry (imbuements.cpp:47) loaded by the bootstrap. */
    private val imbuements: Imbuements? = null,
    /** `g_database()` source backing the composed `IOMarket::getInstance()` (iomarket.cpp). */
    private val dataSource: DataSource? = null,
    suppliedGame: Game? = null,
) : ListenerBoot {
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
                dispatcher.schedule(delayMillis, DispatcherLane.WorldCommit) {
                    ioMarket?.checkExpiredOffers()
                }
            },
        )
    }

    /**
     * `g_game()` equivalent shared by every game connection served by this listener,
     * composed with the booted item/imbuement registries and market.
     */
    val game: Game = suppliedGame ?: Game(
        gameMap,
        dispatcher,
        itemTypes = itemTypes,
        ioMarket = ioMarket,
        imbuements = imbuements,
    )

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
        imbuements?.let { registry -> ImbuementEngine(registry, ImbuementDecay(registry, dispatcher)) }

    private val scriptingDispatch = ScriptingDispatch(luaEnvironment, dispatcher)

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

        // `IOMarket::checkExpiredOffers()` runs during server startup
        // (src/canary_server.cpp:230) and reschedules itself from there.
        ioMarket?.let { market ->
            dispatcher.dispatch(DispatcherLane.WorldCommit) { market.checkExpiredOffers() }
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
