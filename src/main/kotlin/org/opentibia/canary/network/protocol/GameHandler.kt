package org.opentibia.canary.network.protocol

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.players.PlayerTask
import org.opentibia.canary.creatures.players.getAllSaleItemIdAndCount
import org.opentibia.canary.creatures.players.updateState
import org.opentibia.canary.creatures.players.vip.InMemoryVipRepository
import org.opentibia.canary.creatures.players.vip.VipEntry
import org.opentibia.canary.creatures.players.vip.VipRepository
import org.opentibia.canary.creatures.players.vip.VipStatus
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.modal.ModalWindowClient
import org.opentibia.canary.game.modal.ModalWindowManager
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.MarketEscrow
import org.opentibia.canary.io.MarketEscrowItemType
import org.opentibia.canary.io.MarketEscrowPlayer
import org.opentibia.canary.io.iologindata.IOLoginData
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Items
import org.opentibia.canary.kv.KVStore
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.livestream.LivestreamViewerLogin
import org.opentibia.canary.network.protocol.livestream.LivestreamViewerTransport
import org.opentibia.canary.network.protocol.misc.MiscSessionWriter
import org.opentibia.canary.persistence.PlayerPersistenceService
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.systems.imbuement.ImbuementAction
import org.opentibia.canary.systems.imbuement.ImbuementDecay
import org.opentibia.canary.systems.imbuement.ImbuementEngine
import org.opentibia.canary.systems.imbuement.ImbuementItem
import org.opentibia.canary.systems.imbuement.ImbuementPlayer
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.mount.Mounts
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Handles Game World connections on port 7172 (`ProtocolGame`).
 */
class GameHandler(
    private val rsaCipher: RsaCipher = RsaCipher(),
    private val accountRepository: AccountRepository,
    private val gameMap: GameMap,
    private val dispatcher: GameDispatcher,
    private val luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() },
    private val persistenceService: PlayerPersistenceService? = null,
    private val onPlayerAppear: () -> Unit = {},
    private val onPlayerDisappear: () -> Unit = {},
    /** `Item::items` registry (src/items/items.hpp:389) used by the market and imbuement compositions. */
    private val itemTypes: Items? = null,
    /** `IOMarket::getInstance()` (game.cpp:10297) shared by the market opcodes. */
    private val ioMarket: IOMarket? = null,
    /** `g_imbuements()` (imbuements.cpp:47) registry backing the composed `ImbuementEngine`. */
    private val imbuements: Imbuements? = null,
    /**
     * `g_imbuementDecay()` (imbuements.hpp:167) engine shared by every game
     * connection; the composition root ([CanaryServer]) builds it once over the
     * server dispatcher so decay ticks arm on the server lifecycle. When null
     * the handler composes its own engine over [dispatcher], so a directly
     * constructed handler still runs decay on the server schedules.
     */
    private val imbuementEngine: ImbuementEngine? = null,
    /**
     * `g_game()` seam shared by every game connection. [CanaryServer] injects its
     * single instance; a directly constructed handler falls back to a private
     * instance over the same map/dispatcher, composed with the injected item,
     * market and imbuement services.
     */
    val game: Game = Game(
        gameMap,
        dispatcher,
        itemTypes = itemTypes,
        ioMarket = ioMarket,
        imbuements = imbuements,
        // `Player::vip()` (player.hpp:739): the directly constructed world
        // resolves the session VIP component for the addList/removeList status
        // broadcasts and the VIP request handlers.
        playerVip = { player -> player.features.vip },
    ),
    /**
     * World-shared `Player::muteCountMap` (player.hpp:281) resolved by the
     * composition root; each logged-in [Player] receives it so the chat
     * escalation survives relogs. A directly constructed handler keeps a
     * per-handler map (its sessions share it).
     */
    private val muteCountMap: MutableMap<Long, Int> = mutableMapOf(),
    /**
     * `g_game().transformItem(item, item->getID(), charges - 1)` seam
     * (player.cpp:1065, 7588, 7697, 7718) bound by [CanaryServer] to its world
     * `Game`; inert when the handler is constructed directly.
     */
    private val transformItemCharges: (Item) -> Unit = {},
    /**
     * `IOLoginData::loadPlayerById` login-load seam (src/io/iologindata.cpp:81-86):
     * `ProtocolGame::login` resolves the character id and materializes the
     * persisted player (vitals, skills, inventory, depot, inbox, stash, kills,
     * spells, feature state) before `Game::placeCreature`
     * (protocolgame.cpp:1023). When `null` the handler keeps the stub player
     * shell so directly constructed handlers (and tests) still serve a session.
     */
    private val ioLoginData: IOLoginData? = null,
    /**
     * `g_vocations()` registry (src/creatures/players/vocations/vocation.hpp:122)
     * attached to every session [Player] before the persisted load so
     * `IOLoginDataLoad::loadPlayerSkill` (iologindata_load_player.cpp:344, the
     * `player.vocations` first lookup) and `Player::getPlayerVocationEnum`
     * (player.cpp:13030) resolve through the booted registry.
     */
    private val vocations: Vocations? = null,
    /**
     * `g_monsters().getMonsterType` provider (`Monsters::getMonsterType`,
     * src/creatures/monsters/monsters.cpp:363) attached to every session
     * [Player]'s `Creature.monsterTypeProvider` seam (monster.hpp:363) for the
     * death-loot type resolution (`Player::death`, player.cpp:4035).
     */
    private val monsterTypeProvider: (String) -> MonsterType? = { null },
    /**
     * `g_kv()` root (`KVStore::getInstance`, src/kv/kv.hpp:188) composed by the
     * bootstrap: every session `Player.kv` is rebound to
     * `g_kv().scoped("player").scoped(guid)` (player.cpp:1398-1400) before the
     * persisted load, so the login exiva sub-load and the kv-backed components
     * read and write the persisted scope. `null` keeps the per-player
     * in-memory store.
     */
    private val kvStore: KVStore? = null,
    /**
     * `IOLoginData` VIP persistence (`account_viplist`/`account_vipgroups`/
     * `account_vipgrouplist`, iologindata.cpp:383-497) injected into every
     * session `PlayerFeatures` (player_vip.cpp:69-219). `null` keeps the
     * per-player in-memory repository.
     */
    private val vipRepository: VipRepository? = null,
    /**
     * `Mounts::getInstance()` definition registry
     * (src/creatures/appearance/mounts/mounts.hpp) injected into every session
     * `PlayerFeatures` so the mount runtime (`Player::toggleMount`/`hasMount`,
     * player.cpp:7918-8128) resolves the booted
     * `{CORE_DIRECTORY}/XML/mounts.xml` definitions. A directly constructed
     * handler keeps a per-handler empty registry.
     */
    private val mountDefinitions: Mounts = Mounts(),
) : SimpleChannelInboundHandler<ByteBuf>() {

    private var player: Player? = null
    private var xteaCipher: XteaCipher? = null

    /**
     * `ProtocolGame::m_isLivestreamViewer` (protocolgame.hpp:646) session state:
     * the viewer transport `castViewerLogin` installed when the login carried
     * the `@livestream` descriptor. Non-null marks a viewer session whose
     * `player` is the watched caster (`player = foundPlayer`,
     * protocolgame.cpp:12389) and whose routing uses the viewer opcode arm with
     * [LivestreamViewerTransport] as the session transport.
     */
    private var livestreamViewer: LivestreamViewerTransport? = null

    /**
     * `ProtocolGame::clientVersionString` (protocolgame.hpp:627) parsed from the
     * current-profile login layout (`onRecvFirstMessage`, protocolgame.cpp:1228-1231).
     * Feeds the `shouldSendWeaponProficiencyDetailList` build-string gate
     * (protocolgame.cpp:109-132) through [GamePacketContext.clientVersionString].
     */
    private var clientVersionString: String = ""

    /**
     * `ProtocolGame::clientVersion` (protocolgame.hpp) parsed from the current
     * layout's `uint32_t` client version (`onRecvFirstMessage`,
     * protocolgame.cpp:1225-1226), compared against `CLIENT_VERSION` by
     * `ProtocolGame::login` (protocolgame.cpp:1383).
     */
    private var clientVersionU32: Int = 0

    /**
     * `ProtocolGame::protocolProfile` (protocolgame.hpp) resolved from the
     * session version through the [ProtocolProfileRegistry] game-login layout
     * (protocolgame.cpp:1191-1197). The current profile is the fallback for
     * versions the port serves through the current wire layout.
     */
    private var sessionProfile: ProtocolProfile = ProtocolProfileRegistry.getCurrentProfile()

    /**
     * Per-session modal-window host (`Player::sendModalWindow`,
     * player.cpp:9021-9028): the client forwards to this session's
     * `ProtocolGame` sink ([Player.protocolSink]) and the host records the
     * window ids `Game::playerAnswerModalWindow` (game.cpp:11144) erases.
     * Registered with the world `Game` during the login placement (WorldCommit
     * lane) and dropped on disconnect.
     */
    private var modalWindowManager: ModalWindowManager? = null

    /**
     * World-confined Lua event caller of this session. [creatureEventsOf] is
     * `Creature::getCreatureEvents` (creature.cpp:1701-1715), so the
     * `think`/`death` loops only fire the events the session player attached
     * through `Creature::registerCreatureEvent` (creature.cpp:1674-1693)
     * instead of the permissive wave-63 global walk.
     */
    private val scriptingDispatch = ScriptingDispatch(
        luaEnvironment,
        dispatcher,
        creatureEventsOf = { creature -> creature.getCreatureEvents() },
    )

    /**
     * `g_imbuementDecay()` engine bound by this handler: the server-owned
     * [imbuementEngine] when injected, otherwise a handler-local engine whose
     * `ImbuementDecay` arms its re-arming tick on [dispatcher]
     * (imbuements.cpp:520-525, 621-631).
     */
    private val composedImbuementEngine: ImbuementEngine? =
        imbuementEngine ?: imbuements?.let { registry ->
            ImbuementEngine(registry, ImbuementDecay(registry, dispatcher))
        }

    /**
     * Production `IOMarket` escrow over this handler's `Game`
     * (`IOMarket::processExpiredOffers`, iomarket.cpp:155-232). The composition
     * root builds the production `IOMarket` with it so expired sell offers reach
     * the seller's inbox and expired buy offers credit the bank balance.
     */
    val marketEscrow: MarketEscrow = GameMarketEscrow({ game }, itemTypes, imbuements)

    init {
        // Host bindings for the `g_game()` seams the ported handlers call on the
        // player they resolved: the C++ `Player::imbuingItem` field
        // (player.hpp:1761) backing `Game::getImbuingItem` (game.cpp:7137) and
        // the walk/follow entry points of `Game::playerMove`/`forcePlayerMove`/
        // targeting (game.cpp:4282-4307, 7030-7040).
        //
        // `Game::updatePlayerHelpers` -> `Player::sendCreatureHelpers`
        // (game.cpp:9690-9699, player.cpp:1820-1823): the 0x94 frame is gated by
        // the spectator's own session (`ProtocolGame::sendCreatureHelpers`
        // returns early unless `oldProtocol`, protocolgame.cpp:10928-10931), so
        // the stateless world seam routes to the spectator's attached sink. The
        // same lambda is idempotent when several handlers share one `Game`.
        game.sendCreatureHelpers = { spectator, creatureId, helpers ->
            spectator.protocolSink?.sendCreatureHelpers(creatureId, helpers)
        }
        game.getImbuingItem = { player -> player.getImbuingItem() }
        // `Item::sendUpdateToClient(player)` / `Container::sendUpdateToClient(player)`
        // (src/items/item.cpp:3648, container.cpp:1201): the corpse item-update
        // tail of `Creature::dropCorpse` (creature.cpp:815) and the quick-loot
        // senders (game.cpp:3752, 6572) reach the target session's
        // `Player::sendUpdateTileItem` (player.cpp:7067) fan-out.
        game.sendItemUpdateToClient = { player, item -> item.sendUpdateToClient(player) }
        game.setNextWalkTask = { player, task -> player.setNextWalkTask(task as? PlayerTask) }
        game.setNextWalkActionTask = { player, task -> player.setNextWalkActionTask(task as? PlayerTask) }
        // The `Game` seam carries only the `ignoreConditions` flag; the C++
        // `WalkStartPolicy` (creature.cpp:326) is not forwarded (PARITY GAP).
        game.startAutoWalk = { player, listDir, ignoreConditions ->
            player.startAutoWalk(listDir, ignoreConditions)
        }
        // `Creature::updateCreatureWalk` (creature.hpp:412) awaits the async
        // `goToFollowCreature_async()` barrier; the bound host call runs the
        // full Pathfinder-batch pipeline (creature.cpp:1046-1056, 2148-2184).
        game.updateCreatureWalk = { player -> player.updateCreatureWalk() }
        // `Player::getAllSaleItemIdAndCount(countMap)` (player.cpp:5982) and
        // `Player::sendSaleItemList(inventoryMap)` (player.cpp:2408) behind
        // `Game::updatePlayerSaleItems` (game.cpp:11596): the deferred
        // shop-open/state sale-list refresh.
        game.getAllSaleItemIdAndCount = { player, countMap -> player.getAllSaleItemIdAndCount(countMap) }
        game.sendSaleItemList = { player, inventoryMap -> player.protocolSink?.sendSaleItemList(inventoryMap) }
        // `Player::updateSaleShopList()` (player.cpp:6051-6054): every
        // inventory-change producer (`Player::postAddNotification`/
        // `postRemoveNotification`) that passes the shop gate lands here. The
        // scheduled flag is latched by `Game.updateSaleShopList` and
        // `Game::updatePlayerSaleItems` defers on the WorldCommit lane.
        game.scheduleSaleUpdate = { target ->
            val playerId = target.id
            dispatcher.dispatch(DispatcherLane.WorldCommit) { game.updatePlayerSaleItems(playerId) }
        }
        // `Player::updateState()` (player.cpp:6056-6064): the inventory refresh
        // followed by the shop tail, which defers `Game::updatePlayerSaleItems`
        // through the dispatcher when the session has an open shop window. The
        // `updateItemsLight` producer is composed over the world's per-player
        // `PlayerLightState` (player.cpp:6284-6305).
        game.updateState = { target ->
            target.updateState(
                updateItemsLight = { game.playerUpdateItemsLight(target) },
                updateSaleShopList = { game.updateSaleShopList(target) },
                hasShopOwner = { game.getShopOwner(target) != null },
            )
        }

        val engine = composedImbuementEngine
        if (engine != null) {
            game.createScrollImbuement = { player, imbuement ->
                engine.createScrollImbuement(
                    imbuement,
                    ImbuementPlayerAdapter(game, player, itemTypes, imbuements),
                )
            }
            game.onApplyImbuement = { player, imbuement, item, slot ->
                // The seam composes the real item into the engine, so the
                // process registry reaches it exactly like the C++
                // `g_imbuements()` lookup in `Item::getImbuementInfo`
                // (src/items/item.cpp:472-483).
                imbuements?.let { item.imbuementRegistry = it }
                val adapter = ImbuementPlayerAdapter(game, player, itemTypes, imbuements)
                val view = ImbuementItem(item).also {
                    it.parent = adapter
                    it.holdingPlayer = adapter
                }
                engine.onApplyImbuement(imbuement, view, slot, adapter)
            }
            game.onClearImbuement = { player, item, slot ->
                imbuements?.let { item.imbuementRegistry = it }
                val adapter = ImbuementPlayerAdapter(game, player, itemTypes, imbuements)
                val view = ImbuementItem(item).also {
                    it.parent = adapter
                    it.holdingPlayer = adapter
                }
                engine.onClearImbuement(view, slot, adapter)
            }
            game.sendImbuementResult = { player, message -> player.sendTextMessage(message) }
        }
    }

    /**
     * `MAX_EXIVA_WHITELIST` (configmanager, default 100) behind the exiva
     * add/remove caps; the port reads the [ConfigKeys] default because the
     * handler has no `g_configManager()` seam (PARITY GAP: config injection).
     */
    private val maxExivaWhitelist: Int =
        (ConfigKeys.byName("MAX_EXIVA_WHITELIST")?.defaultValue as? Int) ?: 100

    companion object {
        /**
         * `sendAddCreature` self branch opcode for the supported profiles
         * (`version >= 980`, protocolgame.cpp:8641): the sink's self branch
         * writes it through [GameWindowWriter.writeSelfAddCreature].
         */
        const val PACKET_SELF_APPEAR = GameWindowWriter.PACKET_SELF_APPEAR
        const val PACKET_DISCONNECT = 0x14

        /**
         * Client protocol version at which `ProtocolGame::sendAddCreature`
         * starts sending the `0xD4` VIP groups dialog (protocolgame.cpp:8746).
         */
        private const val VIP_GROUPS_VERSION = 1100

        /**
         * Bound on the login placement lane round trip; see [placeSessionPlayer].
         * A lane that cannot complete the placement in this window reports the
         * C++ temple-position failure instead of blocking the channel forever.
         */
        private const val PLACEMENT_TIMEOUT_SECONDS = 5L

        /** The zero `Position` `IOLoginDataLoad::loadPlayerBasicInfo` treats as unset. */
        private val ZERO_POSITION = Position(0, 0, 0)

        /** `CreatureEvent::getEventType` kill name (`creatureevent.cpp:163`). */
        private const val KILL_EVENT_TYPE = "kill"
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (player == null) {
            handleGameLogin(ctx, msg)
        } else {
            handleGamePacket(ctx, msg)
        }
    }

    private fun handleGameLogin(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (msg.readableBytes() < 5) {
            ctx.close()
            return
        }

        val checksum = msg.readUnsignedIntLE()
        val payloadLen = msg.readableBytes()
        val calculatedChecksum = Adler32Checksum.compute(msg, msg.readerIndex(), payloadLen)

        if (checksum != calculatedChecksum) {
            logger.warn { "Adler32 checksum mismatch on game login" }
            ctx.close()
            return
        }

        val os = msg.readUnsignedShortLE()
        val version = msg.readUnsignedShortLE()

        // `ProtocolGame::onRecvFirstMessage` (protocolgame.cpp:1191-1210): a
        // version without a registered game-login layout, a disallowed profile
        // or a run without old-protocol support disconnects with the allowed
        // client-protocol description before the RSA block is read. The port's
        // `OLD_PROTOCOL` default is the Canary config default `true`
        // (configmanager.cpp:45); a composition root that disables it has no
        // seam yet (PARITY GAP).
        val layout = ProtocolProfileRegistry.resolveGameLoginLayout(version)
        if (layout == null) {
            sendDisconnect(
                ctx,
                ProtocolProfileRegistry.getUnsupportedClientProtocolMessage(includeOldProtocolProfiles = true),
            )
            return
        }
        val resolvedProfile = ProtocolProfileRegistry.getProfile(layout.profileId)
        if (resolvedProfile == null || !ProtocolProfileRegistry.isProfileAllowed(resolvedProfile.id)) {
            sendDisconnect(ctx, "This client protocol is not supported yet.")
            return
        }
        sessionProfile = resolvedProfile

        if (msg.readableBytes() < RsaCipher.BLOCK_SIZE) {
            ctx.close()
            return
        }

        // The profile layout declares the pre-RSA fields (client version u32,
        // client version string, asset hash, dat revision, preview state); a
        // malformed extras block falls back to the lenient RSA-block skip.
        val rsaStart = msg.readerIndex() + msg.readableBytes() - RsaCipher.BLOCK_SIZE
        clientVersionU32 = 0
        clientVersionString = parseGameLoginExtras(msg, layout, rsaStart) ?: ""
        if (msg.readerIndex() != rsaStart) {
            msg.readerIndex(rsaStart)
        }

        // `ProtocolGame::login` (protocolgame.cpp:1383-1386): a modern client
        // whose declared client version differs from `CLIENT_VERSION` is
        // unsupported.
        if (!sessionProfile.hasFeature(ProtocolFeature.OldProtocolCompat) &&
            layout.hasClientVersionU32 &&
            clientVersionU32 != CLIENT_VERSION
        ) {
            sendDisconnect(
                ctx,
                ProtocolProfileRegistry.getUnsupportedClientProtocolMessage(includeOldProtocolProfiles = true),
            )
            return
        }

        val rsaBytes = ByteArray(RsaCipher.BLOCK_SIZE)
        msg.readBytes(rsaBytes)

        val decrypted = rsaCipher.decrypt(rsaBytes)
        if (!decrypted || rsaBytes[0] != 0.toByte()) {
            logger.warn { "RSA decryption failed on game handshake" }
            ctx.close()
            return
        }

        val rsaBuf = Unpooled.wrappedBuffer(rsaBytes)
        rsaBuf.readByte() // skip check byte 0x00

        val xteaKey = IntArray(4) { rsaBuf.readIntLE() }
        val cipher = XteaCipher(xteaKey)
        this.xteaCipher = cipher

        val isGamemaster = rsaBuf.readByte() != 0.toByte()
        val sessionKey = rsaBuf.readTibiaString()
        val characterName = rsaBuf.readTibiaString()

        val tokens = sessionKey.split("\n")
        val accountName = tokens.getOrNull(0) ?: ""
        val password = tokens.getOrNull(1) ?: ""

        val encoder = ctx.pipeline().get(TibiaMessageEncoder::class.java)
        encoder?.xteaCipher = cipher
        encoder?.checksumEnabled = true

        // `ProtocolGame::onRecvFirstMessage`'s `@livestream` account descriptor
        // (protocolgame.cpp:1409-1417): the viewer login resolves the caster
        // through `ProtocolGame::castViewerLogin` instead of authenticating an
        // account, and the dispatch force-closes the connection when admission
        // fails (`disconnectClient`, protocolgame.cpp:12399).
        if (accountName == LivestreamViewerLogin.ACCOUNT_DESCRIPTOR) {
            loginLivestreamViewer(ctx, characterName, password, version)
            return
        }

        val account = accountRepository.authenticate(accountName, password)
        val characterMatch = account?.characters?.find { it.name.equals(characterName, ignoreCase = true) }

        if (characterMatch == null) {
            sendDisconnect(ctx, "Character not found or credentials invalid.")
            return
        }

        // Canary resolves the character id (`IOLoginDataLoad::preLoadPlayer`)
        // and materializes the persisted systems through
        // `IOLoginData::loadPlayerById(..., false)` before the creature is
        // placed (protocolgame.cpp:1023). The injected loader drives the same
        // load; without one the handler keeps the stub shell.
        val activePlayer = loadSessionPlayer(characterMatch.name, account)
        // Wave 73: the cyclopedia's SQL is dialect-specific; bind the world dialect.
        activePlayer?.let { player -> player.features.cyclopedia.sqlDialect = game.sqlDialect }
        if (activePlayer == null) {
            sendDisconnect(ctx, "Your character could not be loaded, please contact an administrator.")
            return
        }
        this.player = activePlayer

        // `Creature::enqueueAsyncTask` admits async batches through
        // `g_dispatcher()` (creature.cpp:2031-2055); the port attaches the
        // server dispatcher to the session creature so its batches hand back
        // through the `WorkerCompletion` lane instead of running on the
        // calling (Netty) thread (AGENTS.md 1.2).
        activePlayer.asyncTaskDispatcher = dispatcher

        // `Player::client` is the per-session ProtocolGame; the port models it
        // as the per-player sink over this channel (fire-and-forget writes).
        // `g_game().getCreatureByID` feeds the session known-set eviction
        // (`ProtocolGame::checkCreatureAsKnown`, protocolgame.cpp:2346).
        val selfTail = selfAppearTailFor(version, activePlayer)
        activePlayer.protocolSink = NettyProtocolSink(
            ctx.channel(),
            activePlayer,
            gameMap,
            creatureById = { id -> game.getCreatureByID(id) },
            // `Player::getShopOwner` (player.cpp:6123-6125) behind the
            // `Player::sendSaleItemList` gate (player.cpp:2408); the
            // composition root binds the Lua-recorded merchant.
            shopOwner = { player -> game.getShopOwner(player) },
            // `sendAddCreature` self branch (protocolgame.cpp:8641-8682): the
            // negotiated version, profile features, store pair and world-type
            // exiva gate feed the login frame tail.
            selfAppearTail = selfTail,
            // `ProtocolGame::sendExivaRestrictions(true)` resolves the stored
            // guid whitelists through `IOLoginData::getNameByGuid`
            // (protocolgame.cpp:12273) and `Game::getGuild(id, true)->getName()`
            // (protocolgame.cpp:12292).
            playerNameByGuid = { guid -> ioLoginData?.getNameByGuid(guid) ?: "" },
            guildNameById = { guildId -> game.getGuild(guildId, allowOffline = true)?.name ?: "" },
        )

        // `sendAddCreature`'s `!oldProtocol` exiva tail (protocolgame.cpp:8676-8682):
        // the exiva-button byte is written inline by the self appear frame and
        // `sendExivaRestrictions(true)` queues the 0xCA whitelist frame before it,
        // so emit before the placement announces the session.
        if (selfTail.exivaEnabled) {
            activePlayer.protocolSink?.sendExivaRestrictions(isLogin = true)
        }

        // `Player::sendModalWindow` (player.cpp:9021-9028): the per-session
        // `ModalWindowManager` resolves this connection's `ProtocolGame`, so a
        // window the world opens for the player emits through the session sink
        // and the recorded ids route the 0xF9 answers back through
        // `Game::playerAnswerModalWindow` (game.cpp:11144-11176). The host is
        // registered with the world `Game` inside the placement block below so
        // the mutation stays on the WorldCommit lane (AGENTS.md 1.2).
        modalWindowManager = ModalWindowManager(
            scripting = scriptingDispatch,
            client = ModalWindowClient { modalWindow -> activePlayer.protocolSink?.sendModalWindow(modalWindow) },
        )

        // `Creature::onThink`/`Creature::dropCorpse` scripting loops
        // (creature.cpp:157-163, 819-824): the session's `ScriptingDispatch`
        // runs the attached events confined to the WorldCommit lane, and the
        // death loop forwards the full `CreatureEvent::executeOnDeath` argument
        // set (creatureevent.cpp:292-326) the caller resolved; a payload-less
        // `Creature.onDeath` keeps the inert defaults (Creature.kt PARITY GAP).
        activePlayer.onThinkEventsHost = { creature, interval ->
            scriptingDispatch.creatureOnThink(creature, interval.toLong())
        }
        activePlayer.onDeathEventsHost =
            { creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified ->
                scriptingDispatch.creatureOnDeath(
                    creature,
                    corpse,
                    killer,
                    mostDamageKiller,
                    lastHitUnjustified,
                    mostDamageUnjustified,
                )
            }

        // `Creature::deprecatedOnKilledCreature`'s kill-event loop
        // (creature.cpp:1290-1303): the attached
        // `getCreatureEvents(CREATURE_EVENT_KILL)` names filtered through the
        // registry's `kill` event type — the same typed walk
        // `ScriptingDispatch` runs for think/death — submitted to the
        // WorldCommit lane through `creatureEventOnKill`. The master forwarding
        // lives on `Creature.deprecatedOnKilledCreature`, so a summon kill
        // reaches its player master's attached events through this host.
        activePlayer.onKillEventsHost = { killer, target, lastHit ->
            killer.getCreatureEvents()
                .filter { name -> luaEnvironment.creatureEvents.get(name)?.eventType == KILL_EVENT_TYPE }
                .forEach { name -> scriptingDispatch.creatureEventOnKill(name, killer, target, lastHit) }
        }
        // `Player::getProtocolVersion` (player.cpp:1022-1030): the negotiated
        // session client version behind the loot-message colour gate
        // (creature.cpp:789).
        activePlayer.protocolVersionProvider = { clientVersionU32 }

        // Canary restores the persisted player systems during login
        // (IOLoginData::loadPlayer) before firing the login creature events
        // (Player::onPlacedCreature -> CreatureEvents::playerLogin).
        persistenceService?.load(activePlayer)

        // `ProtocolGame::login` placement block (protocolgame.cpp:1053-1063):
        // `g_game().placeCreature(player, player->getLoginPosition()) ||
        // g_game().placeCreature(player, player->getTemplePosition(), false, true)`;
        // `Game::loginRefusal` (game.cpp:12352-12399): STARTUP/MAINTAIN refuse
        // with the exact message (SHUTDOWN refuses silently); CanAlwaysLogin
        // players bypass. The C++ call sites are the game-protocol login gates.
        val refusal = game.loginRefusal(activePlayer)
        if (refusal != null) {
            if (refusal.isNotEmpty()) {
                activePlayer.protocolSink?.sendCancelMessage(refusal)
            }
            ctx.close()
            return
        }
        // a failed placement disconnects with the temple error. The placement
        // itself runs the ported `Creature::addList` -> `Player::addList`
        // (game.cpp:1513, player.cpp:4519), which announces the session on the
        // online VIP lists and registers it through `Game::addPlayer`
        // (game.cpp:11624) before `Player::onPlacedCreature` fires the login
        // creature events.
        seedLoginPosition(activePlayer)
        if (!placeSessionPlayer(activePlayer)) {
            logger.warn { "Player '${activePlayer.name}' temple position is wrong" }
            sendDisconnect(ctx, "Temple position is wrong. Please, contact the administrator.")
            return
        }

        scriptingDispatch.playerLogin(activePlayer)

        // Canary checks the players record from `Player::onCreatureAppear`
        // (src/creatures/players/player.cpp:12300); the server owns the online-count
        // tracking and fires the `record` global events.
        onPlayerAppear()

        logger.info { "Player '${activePlayer.name}' logged into game world" }
        sendLoginSuccess(ctx, activePlayer, version)
    }

    /**
     * `ProtocolGame::castViewerLogin` dispatch (protocolgame.cpp:1409-1417):
     * resolves the caster and drives [LivestreamViewerLogin.castViewerLogin] on
     * the WorldCommit lane (the C++ `dispatchProtocolTask`), then installs the
     * viewer session state (`player = foundPlayer`, `m_isLivestreamViewer`,
     * `acceptPackets`). A rejected login sends the folded
     * `disconnectClient(reason)` frame and closes.
     */
    private fun loginLivestreamViewer(
        ctx: ChannelHandlerContext,
        characterName: String,
        password: String,
        version: Int,
    ): Boolean {
        val oldProtocol = sessionProfile.hasFeature(ProtocolFeature.OldProtocolCompat)

        var accepted = false
        var reason = ""
        var viewer: LivestreamViewerTransport? = null
        var casterPlayer: Player? = null

        val done = CountDownLatch(1)
        val block: () -> Unit = {
            val caster = game.getPlayerByName(characterName)
            if (caster == null) {
                // `canWatchLivestream(foundPlayer = nullptr, ...)` folds to the
                // manager's "Livestream unavailable." reason.
                reason = "Livestream unavailable."
            } else {
                val transport = LivestreamViewerTransport(
                    channel = ctx.channel(),
                    caster = caster,
                    map = gameMap,
                    creatureById = { id -> game.getCreatureByID(id) },
                    selfAppearTail = selfAppearTailFor(version, caster),
                )
                val result = LivestreamViewerLogin.castViewerLogin(
                    playerByName = { name -> game.getPlayerByName(name) },
                    livestream = game.livestream,
                    viewerClient = transport,
                    casterName = characterName,
                    password = password,
                    oldProtocol = oldProtocol,
                    sink = transport,
                )
                accepted = result.accepted
                reason = result.reason
                if (result.accepted) {
                    viewer = transport
                    casterPlayer = caster
                }
            }
            done.countDown()
        }

        val dispatched = dispatcher.isRunning && dispatcher.dispatch(DispatcherLane.WorldCommit) { block() }
        if (!dispatched) {
            block()
        }

        val completed = done.await(PLACEMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val viewerTransport = viewer
        val caster = casterPlayer
        if (!completed || !accepted || viewerTransport == null || caster == null) {
            sendDisconnect(ctx, reason.ifEmpty { "Livestream unavailable." })
            return false
        }

        this.player = caster
        this.livestreamViewer = viewerTransport
        logger.info { "Livestream viewer session watching '${caster.name}'" }
        return true
    }

    /**
     * `ProtocolGame::onRecvFirstMessage`'s pre-RSA profile fields
     * (protocolgame.cpp:1222-1246) for the resolved [GameLoginLayout]: the
     * client version `u32`, the client version string, the asset-hash string,
     * the dat revision `u16` and the preview-state byte. The fields must
     * consume exactly up to [endIndex] (the RSA block); a malformed payload
     * returns `null` so the caller falls back to the lenient skip.
     */
    private fun parseGameLoginExtras(msg: ByteBuf, layout: GameLoginLayout, endIndex: Int): String? {
        val start = msg.readerIndex()
        var versionString = ""

        val parsed = runCatching {
            if (layout.hasClientVersionU32) {
                if (endIndex - msg.readerIndex() < 4) {
                    return null
                }
                clientVersionU32 = msg.readUnsignedIntLE().toInt()
            }
            if (layout.hasClientVersionString) {
                versionString = readBoundedTibiaString(msg, endIndex) ?: return null
            }
            if (layout.hasAssetHashString) {
                if (readBoundedTibiaString(msg, endIndex) == null) {
                    return null
                }
            }
            if (layout.hasContentRevisionU16) {
                if (endIndex - msg.readerIndex() < 2) {
                    return null
                }
                msg.readUnsignedShortLE()
            }
            if (layout.hasPreviewState) {
                if (endIndex - msg.readerIndex() < 1) {
                    return null
                }
                msg.readByte()
            }
        }.isSuccess

        if (!parsed || msg.readerIndex() != endIndex) {
            msg.readerIndex(start)
            return null
        }
        return versionString
    }

    /**
     * Reads one length-prefixed Tibia string that must end at or before
     * [endIndex], without crossing into the RSA block.
     */
    private fun readBoundedTibiaString(msg: ByteBuf, endIndex: Int): String? {
        if (endIndex - msg.readerIndex() < 2) {
            return null
        }
        val start = msg.readerIndex()
        val length = msg.readUnsignedShortLE()
        if (endIndex - msg.readerIndex() < length) {
            msg.readerIndex(start)
            return null
        }
        val bytes = ByteArray(length)
        msg.readBytes(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * Resolves the self appear frame tail from the session protocol state
     * (`ProtocolGame::sendAddCreature` self branch, protocolgame.cpp:8641-8682):
     * the version, the `LoginSpeedFormula`/`OldProtocolCompat` profile features,
     * the `ExpertPvp::isEnabled()` + `ExpertPvpModeByte` controls gate, the
     * `g_configManager()` STORE_IMAGES_URL/STORE_COIN_PACKET pair, the
     * `Game::getWorldType() == WORLD_TYPE_NO_PVP` exiva gate and the inline
     * bug-report flag (`oldProtocol && (version >= 1054 ||
     * InlineLoginBugReportFlag)`, protocolgame.cpp:8653-8658).
     */
    private fun selfAppearTailFor(version: Int, player: Player): SelfAppearTail {
        val oldProtocol = sessionProfile.hasFeature(ProtocolFeature.OldProtocolCompat)
        return SelfAppearTail(
            clientVersion = version,
            loginSpeedFormula = sessionProfile.hasFeature(ProtocolFeature.LoginSpeedFormula),
            expertPvpControlsEnabled = game.expertPvpEnabled &&
                sessionProfile.hasFeature(ProtocolFeature.ExpertPvpModeByte),
            storeImagesUrl = game.storeImagesUrl,
            storeCoinPacket = game.storeCoinPacket,
            oldProtocol = oldProtocol,
            exivaEnabled = !oldProtocol && game.getWorldType() == WorldType.NO_PVP,
            inlineBugReportFlag = oldProtocol &&
                (version >= 1054 || sessionProfile.hasFeature(ProtocolFeature.InlineLoginBugReportFlag)),
            bugReportEnabled = player.getAccountType().value >= AccountType.ACCOUNT_TYPE_NORMAL.value,
        )
    }

    /**
     * `ProtocolGame::login` placement block (protocolgame.cpp:1053-1063): the
     * login position is tried first and the forced temple placement second.
     *
     * The world mutation is confined to the WorldCommit lane (AGENTS.md 1.2);
     * the calling thread waits for the result because the C++ login flow is
     * synchronous and its failure branch disconnects the client. A listener
     * without a running dispatcher runs the block inline, mirroring
     * `CanaryServer.start`'s spawn-startup fallback for embedded use.
     */
    private fun placeSessionPlayer(player: Player): Boolean {
        val placed = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val block: () -> Unit = {
            // `Player::modalWindows`/`Player::client` session wiring: register
            // the session modal host before `Game::addPlayer` announces the
            // session so an answer cannot race the registration.
            modalWindowManager?.let { manager -> game.registerModalWindowManager(player, manager) }
            placed.set(
                game.placeCreature(player, player.getLoginPosition()) ||
                    game.placeCreature(player, player.getTemplePosition(), extendedPos = false, forced = true),
            )
            done.countDown()
        }

        val dispatched = dispatcher.isRunning && dispatcher.dispatch(DispatcherLane.WorldCommit) { block() }
        if (!dispatched) {
            block()
        }

        return done.await(PLACEMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS) && placed.get()
    }

    /**
     * `IOLoginDataLoad::loadPlayerBasicInfo` stores the persisted
     * `posx`/`posy`/`posz` as `Player::loginPosition` and falls back to the
     * temple when it is zero (iologindata_load_player.cpp:167-169, 207-210).
     * The port's loader writes the persisted position to `Player.position`
     * instead (PARITY GAP: IOLoginData loader seam), so a fresh session player
     * starts with a zero login position; seed it from the loaded position so
     * `ProtocolGame::login` places at the spot Canary would.
     */
    private fun seedLoginPosition(player: Player) {
        if (player.getLoginPosition() == ZERO_POSITION) {
            player.setLoginPosition(player.position)
        }
    }

    /**
     * Resolves the session [Player] for [characterName].
     *
     * With an injected [ioLoginData] the character id comes from
     * `IOLoginData::getGuidByName` (src/io/iologindata.cpp:310-320, the port's
     * row-level `IOLoginDataLoad::preLoadPlayer` equivalent) and the persisted
     * state is materialized through [IOLoginData.loadPlayerById] with
     * `disableIrrelevantInfo = false`, exactly like `ProtocolGame::login`
     * (protocolgame.cpp:1023). Returns `null` when the character row is missing
     * or the load fails, mirroring the C++ `disconnectClient("Your character
     * could not be loaded...")` path.
     *
     * Without a loader the stub shell preserves the pre-wiring behavior of a
     * directly constructed handler.
     *
     * [account] is the authenticated account `IOLoginDataLoad::loadPlayerBasicInfo`
     * resolves from the persisted `players.account_id`
     * (iologindata_load_player.cpp:135-138): it is attached before the load so
     * the VIP sub-load's premium cap and every `PlayerVIP` persistence call see
     * the account id.
     */
    private fun loadSessionPlayer(characterName: String, account: Account?): Player? {
        val loader = ioLoginData ?: return Player(
            id = 1L,
            name = characterName,
            maxHealth = 150,
            health = 150,
            maxMana = 50,
            mana = 50,
            speed = 220,
            position = Position(1000, 1000, 7),
            // `Player::muteCountMap` (player.hpp:281) and the world
            // `g_game().transformItem` binding are shared across sessions.
            muteCountMap = muteCountMap,
            transformItemCharges = transformItemCharges,
            vipRepository = vipRepository ?: InMemoryVipRepository(),
            mountDefinitions = mountDefinitions,
        ).also { it.account = account }.also(::bindRegistries)

        val guid = loader.getGuidByName(characterName)
        if (guid == 0L) {
            return null
        }

        val loaded = Player(
            id = guid,
            name = characterName,
            maxHealth = 150,
            health = 150,
            maxMana = 50,
            mana = 50,
            speed = 220,
            position = Position(1000, 1000, 7),
            muteCountMap = muteCountMap,
            transformItemCharges = transformItemCharges,
            vipRepository = vipRepository ?: InMemoryVipRepository(),
            mountDefinitions = mountDefinitions,
        )
        loaded.account = account

        // The registry/provider must be attached before the persisted load:
        // `IOLoginDataLoad::loadPlayerSkill` (iologindata_load_player.cpp:344)
        // reads `player.vocations` first.
        bindRegistries(loaded)

        if (!loader.loadPlayerById(loaded, guid, disableIrrelevantInfo = false)) {
            return null
        }
        return loaded
    }

    /**
     * Attaches the booted `g_vocations()`/`g_monsters().getMonsterType` seams to
     * a session player exactly like the C++ globals they replace: the vocation
     * registry backs `Player::getPlayerVocationEnum` (player.cpp:13030) and the
     * persisted skill load, the monster provider backs the death-loot type
     * resolution (`Player::death`, player.cpp:4035).
     *
     * It also binds the composition-injected `g_kv()` scope as the player's
     * `Player::kv()` (player.cpp:1398-1400) and the
     * `CreatureEvent` registry lookup `Creature::registerCreatureEvent` uses
     * (creature.cpp:1674-1693), so the player-side `registerEvent` path
     * validates names against the booted registry before the persisted load.
     */
    private fun bindRegistries(player: Player) {
        vocations?.let { player.vocations = it }
        player.monsterTypeProvider = monsterTypeProvider
        player.creatureEventRegistered = { name -> luaEnvironment.creatureEvents.get(name) != null }
        kvStore?.let { store ->
            player.kv = store.scoped("player").scoped(player.id.toString())
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val viewer = livestreamViewer
        if (viewer != null) {
            // `ProtocolGame::release`'s viewer branch (protocolgame.cpp:922-926):
            // a viewer session unregisters itself from `g_livestream()` and never
            // removes the watched caster's creature.
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                game.livestream?.removeViewer(viewer)
            }
            livestreamViewer = null
            player = null
            super.channelInactive(ctx)
            return
        }

        val activePlayer = player
        if (activePlayer != null) {
            // `Player::client == nullptr` on disconnect; pending writes against
            // the closed channel are dropped by the sink.
            activePlayer.protocolSink = null

            // `ProtocolGame::logout` (protocolgame.cpp:1178) removes the player
            // creature (`g_game().removeCreature(player, true)`): the
            // `Player::onRemoveCreature(player, isLogout = true)` branch leaves
            // the party, removes the guild online entry and saves the player
            // (player.cpp:12318-12353), then the ported
            // `Creature::removeList()` -> `Game::removePlayer` unregisters the
            // session (game.cpp:1602, player.cpp:4511, game.cpp:11631). The
            // session modal host is dropped with the session so a reused id
            // cannot inherit it.
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                modalWindowManager?.let { game.unregisterModalWindowManager(activePlayer) }
                game.removeCreature(activePlayer, isLogout = true)
            }
            onPlayerDisappear()
        }
        super.channelInactive(ctx)
    }

    private fun handleGamePacket(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val activePlayer = player ?: return
        val cipher = xteaCipher

        // Verify and decrypt incoming game packet if encrypted
        if (cipher != null && msg.readableBytes() >= 4) {
            val checksum = msg.readUnsignedIntLE()
            val payloadStart = msg.readerIndex()
            val payloadLen = msg.readableBytes()
            val calcChecksum = Adler32Checksum.compute(msg, payloadStart, payloadLen)

            if (checksum == calcChecksum) {
                cipher.decrypt(msg, payloadStart, payloadLen)
            } else {
                msg.readerIndex(payloadStart) // fallback if unchecksummed
            }
        }

        // The WorldCommit lane outlives this channelRead; retain the frame so the
        // handler's auto-release does not free it before the routed task reads it
        // (AGENTS.md 1.3). The task releases it after the router is done.
        val frame = msg.retain()
        dispatcher.dispatch {
            try {
                GamePacketRouter.route(
                    activePlayer,
                    frame,
                    GamePacketContext(
                        map = gameMap,
                        dispatcher = dispatcher,
                        luaEnvironment = luaEnvironment,
                        game = game,
                        ioMarket = ioMarket,
                        itemTypes = itemTypes,
                        clientVersionString = clientVersionString,
                        protocolProfile = sessionProfile,
                        // `ProtocolGame::m_isLivestreamViewer` (protocolgame.hpp:646):
                        // the viewer arm routes the session's packets through the
                        // viewer transport instead of the player table.
                        isLivestreamViewer = livestreamViewer != null,
                        sessionSink = livestreamViewer,
                        // `WeaponProficiency::proficiencies` (weapon_proficiency.cpp:79):
                        // the boot registry loaded from `proficiencies.json` rides
                        // the item registry (bootstrap `defaultItemBoot`) so the
                        // `0xB3` action `0x03` selection validates against it.
                        weaponProficiencyRegistry = itemTypes?.weaponProficiencyRegistry ?: emptyMap(),
                        // `IOLoginData::getGuidByName` behind the exiva
                        // player-whitelist apply (protocolgame.cpp:12227); a
                        // directly constructed handler without the loader
                        // resolves nothing, like an empty player table.
                        exivaPlayerGuidByName = { name -> ioLoginData?.getGuidByName(name) ?: 0L },
                        // `IOGuild::getGuildIdByName` behind the exiva
                        // guild-whitelist apply (protocolgame.cpp:12239). The
                        // `IOGuild` instance is not reachable from the handler,
                        // so the id resolves through `Game::getGuildByName`
                        // (`guildRepository.getGuildIdByName` first,
                        // game.cpp:11738-11748); the offline load is the
                        // side-effect difference from the C++ pure id lookup.
                        exivaGuildIdByName = { name ->
                            game.getGuildByName(name, allowOffline = true)?.id ?: 0L
                        },
                        maxExivaWhitelist = maxExivaWhitelist,
                    )
                )
            } finally {
                frame.release()
            }
        }
    }

    private fun sendLoginSuccess(ctx: ChannelHandlerContext, player: Player, version: Int) {
        // The self appear (0x17) is emitted by the placement fanout through the
        // session sink's `ProtocolGame::sendAddCreature` self branch, exactly
        // once (the player is its own spectator, `Game::placeCreature`,
        // game.cpp:1533).
        //
        // `ProtocolGame::sendAddCreature` then queues the login tail frames
        // (protocolgame.cpp:8686-8693): `sendAllowBugReport` (0x1A) and
        // `sendTibiaTime(g_game().getLightHour())` (0xEF) for non-old-protocol
        // profiles, and `sendPendingStateEntered` (0x0A) plus
        // `sendEnterWorld` (0x0F) for `version >= 980`.
        val oldProtocol = sessionProfile.hasFeature(ProtocolFeature.OldProtocolCompat)
        if (!oldProtocol) {
            writeFrame(ctx) { MiscSessionWriter.writeAllowBugReport(this) }
            writeFrame(ctx) { CreatureWindowWriter.writeTibiaTime(this, game.getLightHour()) }
        }
        if (version >= 980) {
            if (!oldProtocol) {
                writeFrame(ctx) { MiscSessionWriter.writePendingStateEntered(this) }
            }
            writeFrame(ctx) { MiscSessionWriter.writeEnterWorld(this) }
        }

        val out = ctx.alloc().buffer()
        MapPacketWriter.writeMapDescription(out, gameMap, player.position)
        ctx.writeAndFlush(out)

        val sink = player.protocolSink
        sink?.sendStats(player)
        sink?.sendSkills(player)

        // VIP burst (0xD4 + one 0xD2 per persisted record).
        sendVipBurst(player, version)
    }

    /** Queues one unframed protocol message on [ctx] (fire-and-forget). */
    private inline fun writeFrame(ctx: ChannelHandlerContext, build: ByteBuf.() -> Unit) {
        val out = ctx.alloc().buffer()
        out.build()
        ctx.writeAndFlush(out)
    }

    /**
     * The `ProtocolGame::login` VIP burst (protocolgame.cpp:8746-8784):
     * `sendVIPGroups()` on `version >= 1100`, then one `sendVIP` per
     * `IOLoginData::getVIPEntries(player->getAccountId())` record. An online
     * VIP resolves its live `PlayerVIP::getStatus`; a missing player — or, for
     * a non-access player, a ghost — reports `VipStatus_t::Offline`
     * (protocolgame.cpp:8750-8784).
     */
    private fun sendVipBurst(player: Player, version: Int) {
        val sink = player.protocolSink ?: return

        if (version >= VIP_GROUPS_VERSION) {
            sink.sendVipGroups()
        }

        for (entry in vipEntriesOf(player)) {
            val vipPlayer = game.getPlayerByGUID(entry.guid)
            val status = when {
                vipPlayer == null -> VipStatus.Offline
                !player.isAccessPlayer() && vipPlayer.isInGhostMode() -> VipStatus.Offline
                else -> vipPlayer.features.vip.getStatus()
            }
            sink.sendVip(entry.guid, entry.name, entry.description, entry.icon, entry.notify, status)
        }
    }

    /**
     * `IOLoginData::getVIPEntries(player->getAccountId())`
     * (protocolgame.cpp:8750) through the injected loader. Without one the
     * in-memory `PlayerVIP` list backs the burst so a directly constructed
     * handler keeps serving the session.
     */
    private fun vipEntriesOf(player: Player): List<VipEntry> {
        val accountId = player.account?.id
        val loader = ioLoginData
        if (loader != null && accountId != null) {
            return loader.getVipEntries(accountId)
        }
        return player.features.vip.getVipEntries()
    }

    private fun sendDisconnect(ctx: ChannelHandlerContext, message: String) {
        val out = ctx.alloc().buffer()
        out.writeByte(PACKET_DISCONNECT)
        out.writeTibiaString(message)
        ctx.writeAndFlush(out).addListener { ctx.close() }
    }
}

/**
 * Production [MarketEscrow] over the live [Game]
 * (`IOMarket::processExpiredOffers`, iomarket.cpp:155-232): resolve the offer
 * owner through `Game::getPlayerByGUID`, deliver the escrowed item through the
 * player inbox and credit the offer price through `Player::setBankBalance`.
 *
 * PARITY GAPS: the Kotlin `Player` carries no online flag (`isOffline` reports
 * false) and no save-manager seam (`save` is inert); the inbox insertion uses
 * `Container::addItem` because the port's `Game::internalAddItem` has no public
 * `Cylinder` overload.
 */
class GameMarketEscrow(
    /**
     * Resolves the shared `g_game()` at call time so the composition root can
     * build the market before the listener-owned `Game` that the escrow feeds
     * (`IOMarket::getInstance()` construction order, iomarket.cpp:155).
     */
    private val gameProvider: () -> Game,
    private val itemTypes: Items? = null,
    /** `g_imbuements()` registry composed onto the delivered items. */
    private val imbuements: Imbuements? = null,
) : MarketEscrow {

    private val game: Game get() = gameProvider()

    private class EscrowPlayerHandle(val player: Player) : MarketEscrowPlayer {
        override val playerId: Long get() = player.getGUID()

        /** PARITY GAP: the Kotlin `Player` has no online flag. */
        override fun isOffline(): Boolean = false

        /** PARITY GAP: no save-manager seam on the network host. */
        override fun save() = Unit
    }

    /** `Item::items[itemId]` (iomarket.cpp:169); null mirrors `itemType.id == 0`. */
    override fun getItemType(itemId: Int): MarketEscrowItemType? {
        val itemType = itemTypes?.get(itemId) ?: return null
        if (itemType.id == 0) {
            return null
        }
        return MarketEscrowItemType(
            id = itemType.id,
            stackable = itemType.isStackable,
            charges = itemType.charges,
        )
    }

    /** `Game::getPlayerByGUID(guid, allowOffline)` (game.cpp:1419). */
    override fun resolvePlayer(playerId: Long, allowOffline: Boolean): MarketEscrowPlayer? =
        game.getPlayerByGUID(playerId, allowOffline)?.let(::EscrowPlayerHandle)

    /** `Game::internalAddItem(player->getInbox(), item, INDEX_WHEREEVER, FLAG_NOLIMIT)`. */
    override fun addItemToInbox(player: MarketEscrowPlayer, itemId: Int, count: Int, tier: Int): Boolean {
        val target = (player as? EscrowPlayerHandle)?.player ?: return false
        val inbox = target.inbox
        if (inbox.capacity <= inbox.size()) {
            return false
        }

        val item = Item(id = itemId, count = count, type = itemTypes?.get(itemId))
        imbuements?.let { item.imbuementRegistry = it }
        if (tier != 0) {
            item.setTier(tier)
        }
        inbox.addItem(item)
        return true
    }

    /** `Player::setBankBalance(player->getBankBalance() + amount)` (iomarket.cpp:226). */
    override fun addBankBalance(player: MarketEscrowPlayer, amount: Long) {
        val target = (player as? EscrowPlayerHandle)?.player ?: return
        game.setBankBalance(target, game.getBankBalance(target) + amount)
    }
}

/**
 * [ImbuementPlayer] bridge over a live [Player] for the production composition:
 * inventory/stash counts, `Game::removeMoney`, `Game::changeSpeed` and the
 * player text/protocol seams.
 *
 * PARITY GAPS (recorded, not invented):
 *  - `Player::getStorageValue` lives in the Lua operations layer, not on
 *    `Player`, so the shrine gate reports -1 (unset).
 *  - `Player::updateImbuementTrackerStats` has no protocol seam.
 */
internal class ImbuementPlayerAdapter(
    private val game: Game,
    private val player: Player,
    private val itemTypes: Items?,
    /** `g_imbuements()` registry composed onto every item the adapter creates. */
    private val imbuements: Imbuements? = null,
) : ImbuementPlayer {

    override fun getCapacity(): Int = player.capacity.toInt()

    /** `Player::setVarSkill` (player.cpp:352-354); the bonus feeds `Player::getSkillLevel` (player.cpp:7468). */
    override fun setVarSkill(skill: Skill, value: Int) {
        player.setVarSkill(skill, value)
    }

    override fun setVarStats(stat: Stat, value: Int) {
        player.setVarStats(stat, value)
    }

    override fun changeSpeed(value: Int) {
        game.changeSpeed(player, value)
    }

    /** `Player::bonusCapacity` (player.hpp:1780); the value feeds `Player::getFreeCapacity` (player.cpp:5468). */
    override fun setBonusCapacity(value: Int) {
        player.bonusCapacity = value
    }

    /** `Player::sendStats` (player.cpp:2347-2352) through the host protocol seam. */
    override fun sendStats() {
        player.sendStats()
    }

    override fun sendSkills() {
        player.sendSkills()
    }

    /** PARITY GAP: storage lives in the Lua operations layer, not on `Player`. */
    override fun getStorageValue(key: Int): Int = -1

    override fun sendImbuementResult(message: String) {
        player.sendTextMessage(message)
    }

    override fun sendTextMessage(message: String) {
        player.sendTextMessage(message)
    }

    override fun removeMoney(amount: Long): Boolean = game.removeMoney(player, amount, 0, true)

    override fun getItemTypeCount(itemId: Int): Int = player.getItemCount(itemId).toInt()

    override fun getStashItemCount(itemId: Int): Int = player.stash.getStashItemCount(itemId)

    override fun getItemTypeName(itemId: Int): String = itemTypes?.get(itemId)?.name ?: ""

    override fun removeItemOfType(itemId: Int, count: Int, subType: Int, ignoreEquipped: Boolean): Boolean {
        var remaining = count
        for (item in player.getInventoryItemsFromId(itemId, ignoreEquipped)) {
            if (remaining <= 0) {
                break
            }
            if (subType != -1 && item.getSubType() != subType) {
                continue
            }
            val remove = minOf(remaining, item.getItemCount())
            player.removeItem(item, remove)
            remaining -= remove
        }
        return remaining <= 0
    }

    override fun withdrawItem(itemId: Int, amount: Int) {
        player.stash.withdrawItem(itemId, amount)
    }

    override fun internalRemoveItem(item: ImbuementItem, count: Int): Boolean {
        val realItem = item.sourceItem ?: return false
        return game.internalRemoveItem(realItem, count) != ReturnValue.NOTPOSSIBLE
    }

    override fun removeItemCountById(itemId: Int, itemAmount: Int, removeFromStash: Boolean): Boolean {
        var remaining = itemAmount
        val inventoryItemCount = getItemTypeCount(itemId)
        if (inventoryItemCount > 0) {
            val removeFromInventory = minOf(inventoryItemCount, itemAmount)
            if (!removeItemOfType(itemId, removeFromInventory, -1, false)) {
                return false
            }
            remaining -= removeFromInventory
        }

        if (remaining <= 0) {
            return true
        }
        if (!removeFromStash) {
            return false
        }

        if (getStashItemCount(itemId) < remaining) {
            return false
        }
        withdrawItem(itemId, remaining)
        return true
    }

    override fun internalAddItem(item: ImbuementItem, count: Int): Boolean {
        val itemType = itemTypes?.get(item.id)
        if (itemType == null || itemType.id == 0) {
            return false
        }
        val realItem = Item.create(item.id, count, itemTypes, imbuements)
        return player.addItem(realItem) == ReturnValue.NOERROR
    }

    override fun openImbuementWindow(action: ImbuementAction, item: ImbuementItem?) {
        player.setImbuingItem(item?.sourceItem)
    }

    /** PARITY GAP: no imbuement-tracker protocol seam. */
    override fun updateImbuementTrackerStats() = Unit

    override fun isInProtectionZone(): Boolean =
        game.map.getTile(player.position)?.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value) == true

    override fun hasConditionInFight(): Boolean = player.hasCondition(ConditionType.CONDITION_INFIGHT)
}
