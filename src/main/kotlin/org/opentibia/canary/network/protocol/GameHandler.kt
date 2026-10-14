package org.opentibia.canary.network.protocol

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.MarketEscrow
import org.opentibia.canary.io.MarketEscrowItemType
import org.opentibia.canary.io.MarketEscrowPlayer
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Items
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
import org.opentibia.canary.persistence.PlayerPersistenceService
import org.opentibia.canary.systems.imbuement.ImbuementAction
import org.opentibia.canary.systems.imbuement.ImbuementEngine
import org.opentibia.canary.systems.imbuement.ImbuementItem
import org.opentibia.canary.systems.imbuement.ImbuementPlayer
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.rewardchest.ReturnValue

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
    ),
) : SimpleChannelInboundHandler<ByteBuf>() {

    private var player: Player? = null
    private var xteaCipher: XteaCipher? = null
    private val scriptingDispatch = ScriptingDispatch(luaEnvironment, dispatcher)

    /**
     * Production `IOMarket` escrow over this handler's `Game`
     * (`IOMarket::processExpiredOffers`, iomarket.cpp:155-232). The composition
     * root builds the production `IOMarket` with it so expired sell offers reach
     * the seller's inbox and expired buy offers credit the bank balance.
     */
    val marketEscrow: MarketEscrow = GameMarketEscrow(game, itemTypes)

    init {
        val imbuementEngine = imbuements?.let(::ImbuementEngine)
        if (imbuementEngine != null) {
            game.createScrollImbuement = { player, imbuement ->
                imbuementEngine.createScrollImbuement(
                    imbuement,
                    ImbuementPlayerAdapter(game, player, itemTypes),
                )
            }
            game.onApplyImbuement = { player, imbuement, item, slot ->
                val adapter = ImbuementPlayerAdapter(game, player, itemTypes)
                val view = ImbuementItem(item).also {
                    it.parent = adapter
                    it.holdingPlayer = adapter
                }
                imbuementEngine.onApplyImbuement(imbuement, view, slot, adapter)
            }
            game.onClearImbuement = { player, item, slot ->
                val adapter = ImbuementPlayerAdapter(game, player, itemTypes)
                val view = ImbuementItem(item).also {
                    it.parent = adapter
                    it.holdingPlayer = adapter
                }
                imbuementEngine.onClearImbuement(view, slot, adapter)
            }
            game.sendImbuementResult = { player, message -> player.sendTextMessage(message) }
        }
    }

    companion object {
        const val PACKET_SELF_APPEAR = 0x0A
        const val PACKET_DISCONNECT = 0x14
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

        if (msg.readableBytes() > RsaCipher.BLOCK_SIZE) {
            val skip = msg.readableBytes() - RsaCipher.BLOCK_SIZE
            msg.skipBytes(skip)
        }

        if (msg.readableBytes() < RsaCipher.BLOCK_SIZE) {
            ctx.close()
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

        val account = accountRepository.authenticate(accountName, password)
        val characterMatch = account?.characters?.find { it.name.equals(characterName, ignoreCase = true) }

        val encoder = ctx.pipeline().get(TibiaMessageEncoder::class.java)
        encoder?.xteaCipher = cipher
        encoder?.checksumEnabled = true

        if (characterMatch == null) {
            sendDisconnect(ctx, "Character not found or credentials invalid.")
            return
        }

        // Initialize player entity
        val spawnPos = Position(1000, 1000, 7)
        val activePlayer = Player(
            id = 1L,
            name = characterMatch.name,
            maxHealth = 150,
            health = 150,
            maxMana = 50,
            mana = 50,
            speed = 220,
            position = spawnPos
        )
        this.player = activePlayer

        // `Player::client` is the per-session ProtocolGame; the port models it
        // as the per-player sink over this channel (fire-and-forget writes).
        activePlayer.protocolSink = NettyProtocolSink(ctx.channel(), activePlayer, gameMap)

        // Canary restores the persisted player systems during login
        // (IOLoginData::loadPlayer) before firing the login creature events
        // (Player::onPlacedCreature -> CreatureEvents::playerLogin).
        persistenceService?.load(activePlayer)

        // Canary registers the player while placing the creature:
        // `Game::placeCreature` -> `Player::addList` -> `Game::addPlayer`
        // (src/creatures/players/player.cpp:4519, src/game/game.cpp:11624),
        // before `Player::onPlacedCreature` fires the login creature events.
        dispatcher.dispatch(DispatcherLane.WorldCommit) {
            game.addPlayer(activePlayer)
        }

        scriptingDispatch.playerLogin(activePlayer)

        // Canary checks the players record from `Player::onCreatureAppear`
        // (src/creatures/players/player.cpp:12300); the server owns the online-count
        // tracking and fires the `record` global events.
        onPlayerAppear()

        logger.info { "Player '${activePlayer.name}' logged into game world" }
        sendLoginSuccess(ctx, activePlayer)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val activePlayer = player
        if (activePlayer != null) {
            // `Player::client == nullptr` on disconnect; pending writes against
            // the closed channel are dropped by the sink.
            activePlayer.protocolSink = null

            // Canary unregisters the player on logout (`Player::removeList`,
            // src/creatures/players/player.cpp:4519 -> `Game::removePlayer`,
            // src/game/game.cpp:11631).
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                game.removePlayer(activePlayer)
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
                    )
                )
            } finally {
                frame.release()
            }
        }
    }

    private fun sendLoginSuccess(ctx: ChannelHandlerContext, player: Player) {
        val out = ctx.alloc().buffer()
        // 1. Self Appear (0x0A)
        out.writeByte(PACKET_SELF_APPEAR)
        out.writeIntLE(player.id.toInt())
        out.writeShortLE(50) // Graphics speed
        out.writeByte(0) // Can report bugs

        // 2. Map Description (0x64)
        MapPacketWriter.writeMapDescription(out, gameMap, player.position)

        ctx.writeAndFlush(out)
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
    private val game: Game,
    private val itemTypes: Items? = null,
) : MarketEscrow {

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
 *  - `Player::setVarSkill` has no Kotlin counterpart (the domain models no
 *    `varSkills` array), `Player::setBonusCapacity` has no `bonusCapacity`
 *    field, and `Player::sendStats` has no player seam; those callbacks are
 *    inert.
 *  - `Player::getStorageValue` lives in the Lua operations layer, not on
 *    `Player`, so the shrine gate reports -1 (unset).
 *  - `Player::updateImbuementTrackerStats` has no protocol seam.
 */
internal class ImbuementPlayerAdapter(
    private val game: Game,
    private val player: Player,
    private val itemTypes: Items?,
) : ImbuementPlayer {

    override fun getCapacity(): Int = player.capacity.toInt()

    /** PARITY GAP: `Player` models no `varSkills` array. */
    override fun setVarSkill(skill: Skill, value: Int) = Unit

    override fun setVarStats(stat: Stat, value: Int) {
        player.setVarStats(stat, value)
    }

    override fun changeSpeed(value: Int) {
        game.changeSpeed(player, value)
    }

    /** PARITY GAP: `Player` models no `bonusCapacity` field. */
    override fun setBonusCapacity(value: Int) = Unit

    /** PARITY GAP: `Player::sendStats` has no player-level seam. */
    override fun sendStats() = Unit

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
        val realItem = Item.create(item.id, count, itemTypes)
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
