package org.opentibia.canary.creatures.players

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.TeamFinder
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.bindings.core.returnMessage
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.CreatureWindowWriter
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.network.protocol.InventoryIdEntry
import org.opentibia.canary.network.protocol.MiscWindowWriter
import org.opentibia.canary.network.protocol.ProtocolFrameSink
import org.opentibia.canary.network.protocol.ProtocolSink
import org.opentibia.canary.network.protocol.StoreWindowWriter
import org.opentibia.canary.network.protocol.bosstiary.BosstiaryWindowWriter
import org.opentibia.canary.network.protocol.creature.CreaturePacketWriter
import org.opentibia.canary.network.protocol.creature.CreatureUpdateWriter
import org.opentibia.canary.network.protocol.depot.DepotSearchWriter
import org.opentibia.canary.network.protocol.forge.ForgeDataWriter
import org.opentibia.canary.network.protocol.forge.ForgeErrorWriter
import org.opentibia.canary.network.protocol.forge.ForgeHistorySerializer
import org.opentibia.canary.network.protocol.forge.ForgeResult
import org.opentibia.canary.network.protocol.forge.ForgeWindow
import org.opentibia.canary.network.protocol.forge.ForgeWindowSerializer
import org.opentibia.canary.network.protocol.map.TilePacketWriter
import org.opentibia.canary.network.protocol.misc.MiscDataWriter
import org.opentibia.canary.network.protocol.misc.MiscEffectWriter
import org.opentibia.canary.network.protocol.misc.MiscSessionWriter
import org.opentibia.canary.network.protocol.npcshop.NpcShopWindowSerializer
import org.opentibia.canary.network.protocol.party.PartyWindowWriter
import org.opentibia.canary.network.protocol.player.PlayerWindowWriter
import org.opentibia.canary.network.protocol.player.TextWindowWriter
import org.opentibia.canary.network.protocol.teamfinder.TeamFinderWindowWriter
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.forge.ForgeHistory
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * The extended per-player `ProtocolGame` surface behind the seam-only
 * `Player::send*` members (AGENTS.md 1.1: `Player::client` is the per-player
 * [ProtocolSink]). Wave 53 verified 38 of them as one-line
 * `if (client) client->send*(...)` forwards whose frames already have ported
 * writers; this interface adds those forwards to the sink so the call sites can
 * emit on the state changes that invoke them.
 *
 * Everything not listed here keeps the base [ProtocolSink] contract; a `null`
 * [Player.protocolSink] (or a sink that is not a [PlayerSendSink]) models
 * `Player::client == nullptr` and every sender is a no-op.
 */
interface PlayerSendSink : ProtocolSink {

    /** `Player::sendLeaderTeamFinder(bool reset)` (player.cpp:1808). */
    fun sendLeaderTeamFinder(reset: Boolean)

    /** `Player::sendTeamFinderList()` (player.cpp:1814). */
    fun sendTeamFinderList()

    /** `Player::sendAddMarker(pos, markType, desc)` (player.cpp:8396). */
    fun sendAddMarker(pos: Position, markType: Int, desc: String)

    /** `Player::sendBasicData()` (player.cpp:2354). */
    fun sendBasicData()

    /** `Player::sendExivaRestrictions()` (player.cpp:9242). */
    fun sendExivaRestrictions()

    /** `Player::sendItemsPrice()` (player.cpp:4545). */
    fun sendItemsPrice()

    /** `Player::sendTakeScreenshot(type, skillId, skillLevel, achievementName, raceId, bestiaryStep)` (player.cpp:8550). */
    fun sendTakeScreenshot(
        screenshotType: MiscDataWriter.ScreenshotType,
        skillId: Int = 0,
        skillLevel: Int = 0,
        achievementName: String = "",
        raceId: Int = 0,
        bestiaryStep: Int = 0,
    )

    /** `Player::sendClientCheck()` (player.cpp:2280). */
    fun sendClientCheck()

    /** `Player::sendEnterWorld()` (player.cpp:8522). */
    fun sendEnterWorld()

    /** `Player::sendGameNews()` (player.cpp:2286). */
    fun sendGameNews()

    /** `Player::sendExperienceTracker(rawExp, finalExp)` (player.cpp:2535). */
    fun sendExperienceTracker(rawExp: Long, finalExp: Long)

    /** `Player::sendFightModes()` (player.cpp:8528). */
    fun sendFightModes()

    /** `Player::sendUpdateTile(tile, pos)` (player.cpp:8767). */
    fun sendUpdateTile(tile: Tile?, pos: Position)

    /** `Player::sendCreatureTurn(creature)` (player.cpp:8809). */
    fun sendCreatureTurn(creature: Creature)

    /** `Player::sendTextWindow(item, maxlen, canWrite)` (player.cpp:2390). */
    fun sendTextWindow(item: Item, maxlen: Int, canWrite: Boolean)

    /** `Player::sendInventoryIds()` (player.cpp:9287). */
    fun sendInventoryIds()

    /** `Player::sendCoinBalance()` (player.cpp:9275). */
    fun sendCoinBalance()

    /** `Player::sendCreatureChangeOutfit(creature, outfit)` (player.cpp:8861). */
    fun sendCreatureChangeOutfit(creature: Creature, outfit: Outfit)

    /** `Player::sendCreatureReload(creature)` (player.cpp:8837). */
    fun sendCreatureReload(creature: Creature)

    /** `Player::sendCreatureEmblem(creature)` (player.cpp:7306). */
    fun sendCreatureEmblem(creature: Creature)

    /** `Player::sendCreatureSquare(creature, color)` (player.cpp:8849). */
    fun sendCreatureSquare(creature: Creature, color: Int)

    /** `Player::sendPartyCreatureUpdate(creature)` (player.cpp:2145). */
    fun sendPartyCreatureUpdate(creature: Creature)

    /** `Player::sendPartyCreatureShield(creature)` (player.cpp:2151). */
    fun sendPartyCreatureShield(creature: Creature)

    /** `Player::sendPartyCreatureSkull(creature)` (player.cpp:2157). */
    fun sendPartyCreatureSkull(creature: Creature)

    /** `Player::sendPartyCreatureHealth(creature, healthPercent)` (player.cpp:2163). */
    fun sendPartyCreatureHealth(creature: Creature, healthPercent: Int)

    /** `Player::sendPartyPlayerMana(player, manaPercent)` (player.cpp:2169). */
    fun sendPartyPlayerMana(player: Player, manaPercent: Int)

    /** `Player::sendPartyCreatureShowStatus(creature, showStatus)` (player.cpp:2175). */
    fun sendPartyCreatureShowStatus(creature: Creature, showStatus: Boolean)

    /** `Player::sendPartyPlayerVocation(player)` (player.cpp:2181). */
    fun sendPartyPlayerVocation(player: Player)

    /** `Player::sendForgingData()` (player.cpp:4551). */
    fun sendForgingData()

    /** `Player::sendOpenForge()` (player.cpp:11795). */
    fun sendOpenForge()

    /** `Player::sendForgeError(returnValue)` (player.cpp:11801). */
    fun sendForgeError(returnValue: ReturnValue)

    /** `Player::sendForgeResult(...)` (player.cpp:11807). */
    fun sendForgeResult(
        actionType: ForgeAction_t,
        leftItemId: Int,
        leftTier: Int,
        rightItemId: Int,
        rightTier: Int,
        success: Boolean,
        bonus: Int,
        coreCount: Int,
        convergence: Boolean,
    )

    /** `Player::sendForgeHistory(page)` (player.cpp:11813). */
    fun sendForgeHistory(page: Int)

    /** `Player::sendDepotItems(itemMap, count)` (player.cpp:9257). */
    fun sendDepotItems(entries: List<DepotSearchWriter.DepotItemCount>, count: Int)

    /** `Player::sendCloseDepotSearch()` (player.cpp:9263). */
    fun sendCloseDepotSearch()

    /** `Player::sendShop(npc)` (player.cpp:2402). */
    fun sendShop(npc: Npc)

    /** `Player::sendCloseShop()` (player.cpp:2442). */
    fun sendCloseShop()

    /** `Player::sendBosstiaryEntryChanged(bossid)` (player.cpp:12732). */
    fun sendBosstiaryEntryChanged(bossId: Long)

    /**
     * `Player::sendNetworkMessage(NetworkMessage&)` (player.cpp:8534) forwards
     * the staged message buffer to `client->writeToOutputBuffer(message)`. The
     * port's Lua `NetworkMessage` does not expose its staged bytes (PARITY GAP),
     * so the payload crosses this seam as the already-serialized byte array.
     */
    fun sendNetworkMessage(payload: ByteArray)
}

/**
 * [PlayerSendSink] over the [ProtocolFrameSink] transport, resolving each frame
 * from the attached [player], the world [map] and the injected providers that
 * replace the `g_game()`/`g_configManager()`/`g_spells()` globals the Canary
 * `ProtocolGame` senders read (AGENTS.md 1.1).
 *
 * The null-object defaults reproduce Canary's behavior for an empty world: no
 * team finder, no item prices, no warmode/forge configuration overrides. Hosts
 * (the composition root, `Game`) inject the live lookups.
 */
class PlayerFrameSink(
    player: Player,
    map: GameMap,
    private val frameAllocatorProvider: () -> ByteBufAllocator = { UnpooledByteBufAllocator.DEFAULT },
    private val frameWriter: (ByteBuf) -> Unit = { it.release() },
    /** `g_game().getItemsPrice()` (game.hpp) behind `sendItemsPrice` (protocolgame.cpp:10801). */
    private val itemPrices: () -> Map<Int, Map<Int, Long>> = { emptyMap() },
    /** `Item::items[itemId].upgradeClassification > 0` tier-byte gate (protocolgame.cpp:10816). */
    private val itemHasUpgradeClassification: (Int) -> Boolean = { false },
    /** `Player::getPremiumDays()` behind `sendBasicData` (protocolgame.cpp:5834). */
    private val premiumDays: () -> Int = { 0 },
    /** `getTimeNow()` behind the premium expiry (protocolgame.cpp:5834). */
    private val premiumNowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    /** `g_spells().getSpellsByVocation(vocationId)` filtered list (protocolgame.cpp:5851-5858). */
    private val basicDataSpells: () -> List<MiscDataWriter.BasicDataSpell> = { emptyList() },
    /** `Vocation::getMagicShield()` (protocolgame.cpp:5889). */
    private val magicShield: (Player) -> Boolean = { p ->
        p.vocations?.getVocation(p.vocationId)?.getMagicShield() ?: false
    },
    /** `vocationId != 0 || group >= GROUP_TYPE_GAMEMASTER` (protocolgame.cpp:5844-5848). */
    private val preyWindowUnlocked: (Player) -> Boolean = { p ->
        p.vocationId != 0 || (p.group?.id ?: 0) >= GROUP_TYPE_GAMEMASTER
    },
    /** `Player::coinBalance`/`coinTransferableBalance` (player.hpp:1811-1812). */
    private val coinBalances: (Player) -> Pair<Long, Long> = { 0L to 0L },
    /** `g_game().getTeamFinder(player)` behind `sendLeaderTeamFinder` (protocolgame.cpp:3753). */
    private val teamFinder: (Player) -> TeamFinder? = { null },
    /** `g_game().getTeamFinderList()` behind `sendTeamFinderList` (protocolgame.cpp:3683). */
    private val teamFinderList: () -> Map<Long, TeamFinder> = { emptyMap() },
    /** `g_game().getPlayerByGUID(guid)` behind the finder row resolution (protocolgame.cpp:3685). */
    private val playerByGuid: (Long) -> Player? = { null },
    /** `g_game().removeTeamFinderListed(guid)` on the reset frame (protocolgame.cpp:3758). */
    private val removeTeamFinderListed: (Long) -> Unit = {},
    /** `g_game().getWorldType() == WORLD_TYPE_PVP` behind the party skull (protocolgame.cpp:8366). */
    private val pvpWorld: () -> Boolean = { false },
    /** `Player::windowTextId` (player.hpp) behind `sendTextWindow` (protocolgame.cpp:8965). */
    private val windowTextId: (Player) -> Long = { 0L },
    /** The `ProtocolGame::sendOpenForge` window inputs (protocolgame.cpp:7170). */
    private val forgeWindow: () -> ForgeWindow = { ForgeWindow() },
    /** `g_configManager()` forge values plus the stored dust level (protocolgame.cpp:7087). */
    private val forgingDataFrame: () -> ForgeDataWriter.ForgingDataFrame? = { null },
    /** `Player::forgeHistory().get()` behind `sendForgeHistory` (protocolgame.cpp:7483). */
    private val forgeHistoryEntries: (Player) -> List<ForgeHistory> = { p -> p.forgeHistory().get() },
    /** `Player::getAllSaleItemIdAndCount(map)` behind the hidden-shop gate (protocolgame.cpp:6553). */
    private val shopInventoryMap: (Player) -> Map<Int, Int> = { emptyMap() },
    /** `player->kv()->get("npc-shop-hidden-sell-item")` (protocolgame.cpp:6551). */
    private val shopHiddenSellItems: (Player) -> Boolean = { false },
) : ProtocolFrameSink(player, map), PlayerSendSink {

    override fun frameAllocator(): ByteBufAllocator = frameAllocatorProvider()

    override fun writeFrame(frame: ByteBuf): Unit = frameWriter(frame)

    private inline fun emitFrame(build: ByteBuf.() -> Unit) {
        val out = frameAllocator().buffer()
        out.build()
        writeFrame(out)
    }

    // --- team finder -------------------------------------------------------------

    override fun sendLeaderTeamFinder(reset: Boolean) {
        val assemble = teamFinder(player) ?: return

        if (reset) {
            emitFrame { TeamFinderWindowWriter.writeLeaderTeamFinder(this, true, null) }
            removeTeamFinderListed(player.getGUID())
            return
        }

        val frame = leaderFrame(assemble) ?: return
        emitFrame { TeamFinderWindowWriter.writeLeaderTeamFinder(this, false, frame) }
    }

    private fun leaderFrame(assemble: TeamFinder): TeamFinderWindowWriter.TeamFinderLeaderFrame? {
        val leader = playerByGuid(assemble.leaderGuid) ?: return null

        val members = mutableListOf<TeamFinderWindowWriter.TeamFinderMember>()
        for ((guid, status) in assemble.membersMap) {
            val member = playerByGuid(guid) ?: continue
            members += TeamFinderWindowWriter.TeamFinderMember(
                guid = member.getGUID(),
                name = member.name,
                level = member.level,
                vocationClientId = member.getPlayerVocationEnum(),
                status = status,
            )
        }

        return TeamFinderWindowWriter.TeamFinderLeaderFrame(
            minLevel = assemble.minLevel,
            maxLevel = assemble.maxLevel,
            vocationIds = assemble.vocationIDs,
            teamSlots = assemble.teamSlots,
            freeSlots = assemble.freeSlots,
            timestamp = assemble.timestamp,
            teamType = assemble.teamType,
            bossId = assemble.bossID,
            huntType = assemble.hunt_type,
            huntArea = assemble.hunt_area,
            questId = assemble.questID,
            leaderGuid = leader.getGUID(),
            leaderName = leader.name,
            leaderLevel = leader.level,
            leaderVocationClientId = leader.getPlayerVocationEnum(),
            members = members,
        )
    }

    override fun sendTeamFinderList() {
        val listings = teamFinderList().map { (_, assemble) ->
            val leader = playerByGuid(assemble.leaderGuid) ?: return@map null

            var status = 0
            var onlineMembers = 0
            for ((guid, memberStatus) in assemble.membersMap) {
                if (playerByGuid(guid) == null) {
                    continue
                }
                if (guid == player.getGUID()) {
                    status = memberStatus
                }
                if (memberStatus == TeamFinder.MEMBER_STATUS_ACCEPTED) {
                    onlineMembers += 1
                }
            }

            TeamFinderWindowWriter.TeamFinderListingFrame(
                leaderGuid = leader.getGUID(),
                leaderName = leader.name,
                minLevel = assemble.minLevel,
                maxLevel = assemble.maxLevel,
                vocationIds = assemble.vocationIDs,
                teamSlots = assemble.teamSlots,
                freeSlots = assemble.freeSlots,
                timestamp = assemble.timestamp,
                teamType = assemble.teamType,
                bossId = assemble.bossID,
                huntType = assemble.hunt_type,
                huntArea = assemble.hunt_area,
                questId = assemble.questID,
                onlineMembers = onlineMembers,
                status = status,
            )
        }

        if (listings.any { it == null }) {
            return
        }

        emitFrame {
            TeamFinderWindowWriter.writeTeamFinderList(
                this,
                listings.filterNotNull(),
            )
        }
    }

    // --- misc markers/data -------------------------------------------------------

    override fun sendAddMarker(pos: Position, markType: Int, desc: String) {
        emitFrame { MiscEffectWriter.writeAddMarker(this, pos, markType, desc) }
    }

    override fun sendBasicData() {
        val frame = MiscDataWriter.BasicDataFrame(
            premium = player.isPremium(),
            premiumDays = premiumDays(),
            nowSeconds = premiumNowSeconds(),
            vocationClientId = player.getPlayerVocationEnum(),
            preyWindowUnlocked = preyWindowUnlocked(player),
            spells = basicDataSpells(),
            magicShield = magicShield(player),
        )

        emitFrame { MiscDataWriter.writeBasicData(this, frame) }
    }

    override fun sendExivaRestrictions() {
        val restrictions = player.getExivaRestrictions()
        val flags = MiscDataWriter.ExivaRestrictionFlags(
            allowAll = if (restrictions.allowAll) 1 else 0,
            allowOwnGuild = if (restrictions.allowOwnGuild) 1 else 0,
            allowOwnParty = if (restrictions.allowOwnParty) 1 else 0,
            allowVipList = if (restrictions.allowVipList) 1 else 0,
            allowPlayerWhitelist = if (restrictions.allowPlayerWhitelist) 1 else 0,
            allowGuildWhitelist = if (restrictions.allowGuildWhitelist) 1 else 0,
        )

        emitFrame { MiscDataWriter.writeExivaRestrictions(this, flags) }
    }

    override fun sendItemsPrice() {
        val entries = mutableListOf<MiscDataWriter.ItemPriceEntry>()
        for ((itemId, tierPrices) in itemPrices()) {
            for ((tier, price) in tierPrices) {
                entries += MiscDataWriter.ItemPriceEntry(
                    itemId = itemId,
                    tier = tier.takeIf { itemHasUpgradeClassification(itemId) },
                    price = price,
                )
            }
        }

        emitFrame { MiscDataWriter.writeItemsPrice(this, entries) }
    }

    override fun sendTakeScreenshot(
        screenshotType: MiscDataWriter.ScreenshotType,
        skillId: Int,
        skillLevel: Int,
        achievementName: String,
        raceId: Int,
        bestiaryStep: Int,
    ) {
        emitFrame {
            MiscDataWriter.writeTakeScreenshot(
                this,
                screenshotType,
                skillId = skillId,
                skillLevel = skillLevel,
                achievementName = achievementName,
                raceId = raceId,
                bestiaryStep = bestiaryStep,
                playerLevel = player.level,
            )
        }
    }

    override fun sendClientCheck() {
        emitFrame { MiscSessionWriter.writeClientCheck(this) }
    }

    override fun sendEnterWorld() {
        emitFrame { MiscSessionWriter.writeEnterWorld(this) }
    }

    override fun sendGameNews() {
        emitFrame { MiscSessionWriter.writeGameNews(this) }
    }

    override fun sendExperienceTracker(rawExp: Long, finalExp: Long) {
        emitFrame { MiscWindowWriter.writeExperienceTracker(this, rawExp, finalExp) }
    }

    override fun sendFightModes() {
        emitFrame {
            PlayerWindowWriter.writeFightModes(
                this,
                chaseMode = player.chaseMode == ChaseMode.Follow,
                secureMode = player.secureMode,
            )
        }
    }

    // --- tile sends --------------------------------------------------------------

    override fun sendUpdateTile(tile: Tile?, pos: Position) {
        // `ProtocolGame::sendUpdateTile` (protocolgame.cpp:8540-8543) drops the
        // refresh when the position is outside the client view.
        if (!CreaturePacketWriter.canSee(player, pos)) {
            return
        }

        emitFrame {
            TilePacketWriter.writeUpdateTile(this, player, tile, pos) { creature ->
                appearanceOf(creature)
            }
        }
    }

    override fun sendTextWindow(item: Item, maxlen: Int, canWrite: Boolean) {
        emitFrame { TextWindowWriter.writeTextWindow(this, windowTextId(player), item, maxlen, canWrite) }
    }

    // --- inventory/coin ----------------------------------------------------------

    override fun sendInventoryIds() {
        val entries = player.getInventoryItemsId().map { entry ->
            InventoryIdEntry(itemId = entry.itemId, tier = entry.tier ?: 0, amount = entry.count)
        }

        emitFrame { GameWindowWriter.writeInventoryIds(this, entries) }
    }

    override fun sendCoinBalance() {
        val (coins, transferable) = coinBalances(player)
        emitFrame { StoreWindowWriter.writeCoinBalance(this, coins, transferable) }
    }

    // --- creature sends ----------------------------------------------------------

    /**
     * `Player::sendCreatureAppear` (player.cpp:8785-8802). A missing tile is
     * silent; an out-of-range client stack position falls back to
     * `Player::sendUpdateTile` (protocolgame.cpp:8540), otherwise the 0x6A
     * frame is emitted. The base [ProtocolFrameSink.sendCreatureAppear] keeps
     * the plain [ProtocolSink] transports that have no tile-refresh sender.
     */
    override fun sendCreatureAppear(creature: Creature, position: Position, isLogin: Boolean) {
        val tile = creature.getTile() ?: return
        val stackpos = tile.getClientIndexOfCreature(player, creature)
        if (stackpos < 0 || stackpos >= MAX_STACKPOS) {
            sendUpdateTile(tile, position)
            return
        }
        super.sendCreatureAppear(creature, position, isLogin)
    }

    /**
     * `Player::sendCreatureTurn` (player.cpp:8809-8827): the player-level
     * `canSeeCreature` gate and the `getClientIndexOfCreature` stack-position
     * fallback to `sendUpdateTile`, then `ProtocolGame::sendCreatureTurn`
     * (protocolgame.cpp:7988-8004) with its own `canSee(creature)` gate
     * (removed flag, invisibility/ghost and the client viewport).
     */
    override fun sendCreatureTurn(creature: Creature) {
        val tile = creature.getTile() ?: return
        if (!player.canSeeCreature(creature)) {
            return
        }

        val stackpos = tile.getClientIndexOfCreature(player, creature)
        if (stackpos < 0 || stackpos >= MAX_STACKPOS) {
            sendUpdateTile(tile, creature.position)
            return
        }

        if (creature.isRemoved() || !CreaturePacketWriter.canSee(player, creature.position)) {
            return
        }

        emitFrame {
            CreaturePacketWriter.writeCreatureTurn(
                this,
                creature.position,
                stackpos,
                creature.id,
                creature.direction,
                walkthrough = player.canWalkthroughEx(creature),
            )
        }
    }

    override fun sendCreatureChangeOutfit(creature: Creature, outfit: Outfit) {
        sendCreatureOutfit(creature, outfit)
    }

    override fun sendCreatureReload(creature: Creature) {
        val tile = creature.getTile() ?: return
        val stackpos = tile.getClientIndexOfCreature(player, creature)
        if (stackpos < 0 || stackpos >= MAX_STACKPOS) {
            return
        }

        emitFrame {
            TilePacketWriter.writeUpdateTileCreature(
                this,
                creature.position,
                stackpos,
                appearanceOf(creature),
            )
        }
    }

    override fun sendCreatureEmblem(creature: Creature) {
        val tile = creature.getTile() ?: return
        val stackpos = tile.getClientIndexOfCreature(player, creature)

        emitFrame { CreatureUpdateWriter.writeCreatureEmblem(this, creature.position, stackpos, appearanceOf(creature)) }
    }

    override fun sendCreatureSquare(creature: Creature, color: Int) {
        // `ProtocolGame::sendCreatureSquare` (protocolgame.cpp:4723-4740) gates
        // on `canSee(creature)` (protocolgame.cpp:2376-2386): removed, not
        // visible to the player (ghost/invisibility) or out of the client view.
        // The `isCipsoft860Profile` 0x86 branch (4730-4732) is intentionally
        // out of scope: the port serializes the current protocol profile only.
        if (
            creature.isRemoved() ||
            !player.canSeeCreature(creature) ||
            !CreaturePacketWriter.canSee(player, creature.position)
        ) {
            return
        }

        emitFrame { CreatureWindowWriter.writeCreatureSquare(this, creature.id, color) }
    }

    // --- party sends -------------------------------------------------------------

    override fun sendPartyCreatureUpdate(creature: Creature) {
        emitFrame { PartyWindowWriter.writePartyCreatureUpdate(this, creature.id, appearanceOf(creature)) }
    }

    override fun sendPartyCreatureShield(creature: Creature) {
        emitFrame {
            PartyWindowWriter.writePartyCreatureShield(
                this,
                creature.id,
                appearanceOf(creature),
                player.getPartyShield(creature as? Player).value,
            )
        }
    }

    override fun sendPartyCreatureSkull(creature: Creature) {
        if (!pvpWorld()) {
            return
        }

        emitFrame {
            PartyWindowWriter.writePartyCreatureSkull(
                this,
                creature.id,
                appearanceOf(creature),
                player.pvpPlayer.getSkullClient(creature).value,
                pvpWorld = true,
            )
        }
    }

    override fun sendPartyCreatureHealth(creature: Creature, healthPercent: Int) {
        emitFrame { PartyWindowWriter.writePartyCreatureHealth(this, creature.id, appearanceOf(creature), healthPercent) }
    }

    override fun sendPartyPlayerMana(player: Player, manaPercent: Int) {
        emitFrame { PartyWindowWriter.writePartyPlayerMana(this, player.id, appearanceOf(player), manaPercent) }
    }

    override fun sendPartyCreatureShowStatus(creature: Creature, showStatus: Boolean) {
        emitFrame { PartyWindowWriter.writePartyCreatureShowStatus(this, creature.id, appearanceOf(creature), showStatus) }
    }

    override fun sendPartyPlayerVocation(player: Player) {
        emitFrame {
            PartyWindowWriter.writePartyPlayerVocation(
                this,
                player.id,
                appearanceOf(player),
                player.getPlayerVocationEnum(),
            )
        }
    }

    // --- forge sends -------------------------------------------------------------

    override fun sendForgingData() {
        val frame = forgingDataFrame() ?: return
        emitFrame { ForgeDataWriter.writeForgingData(this, frame) }
    }

    override fun sendOpenForge() {
        emitFrame { ForgeWindowSerializer.writeOpenForge(this, forgeWindow()) }
    }

    override fun sendForgeError(returnValue: ReturnValue) {
        emitFrame { ForgeErrorWriter.writeForgeError(this, returnMessage(returnValue.ordinal)) }
    }

    override fun sendForgeResult(
        actionType: ForgeAction_t,
        leftItemId: Int,
        leftTier: Int,
        rightItemId: Int,
        rightTier: Int,
        success: Boolean,
        bonus: Int,
        coreCount: Int,
        convergence: Boolean,
    ) {
        val result = ForgeResult(
            actionType = actionType,
            leftItemId = leftItemId,
            leftTier = leftTier,
            rightItemId = rightItemId,
            rightTier = rightTier,
            success = success,
            bonus = bonus,
            coreCount = coreCount,
            convergence = convergence,
        )

        emitFrame { ForgeWindowSerializer.writeForgeResult(this, result) }
    }

    override fun sendForgeHistory(page: Int) {
        emitFrame { ForgeHistorySerializer.writeForgeHistory(this, page, forgeHistoryEntries(player)) }
    }

    // --- depot/shop/bosstiary ----------------------------------------------------

    override fun sendDepotItems(entries: List<DepotSearchWriter.DepotItemCount>, count: Int) {
        emitFrame { DepotSearchWriter.writeDepotItems(this, entries, count) }
    }

    override fun sendCloseDepotSearch() {
        emitFrame { DepotSearchWriter.writeCloseDepotSearch(this) }
    }

    override fun sendShop(npc: Npc) {
        emitFrame {
            NpcShopWindowSerializer.writeShop(
                this,
                npcName = npc.name,
                shopBlocks = npc.getShopItemVector(player.getGUID()),
                currency = npc.getCurrency(),
                hideSellItemsNotInInventory = shopHiddenSellItems(player),
                inventoryMap = shopInventoryMap(player),
            )
        }
    }

    override fun sendCloseShop() {
        emitFrame { NpcShopWindowSerializer.writeCloseShop(this) }
    }

    override fun sendBosstiaryEntryChanged(bossId: Long) {
        emitFrame { BosstiaryWindowWriter.writeBosstiaryEntryChanged(this, bossId) }
    }

    // --- generic network message -------------------------------------------------

    override fun sendNetworkMessage(payload: ByteArray) {
        emitFrame { writeBytes(payload) }
    }

    private companion object {
        /** `MAX_STACKPOS` (`src/server/network/protocol/protocolgame.hpp`). */
        const val MAX_STACKPOS = 10

        /** `GROUP_TYPE_GAMEMASTER` (`src/enums/account_group_type.hpp:21`). */
        const val GROUP_TYPE_GAMEMASTER = 4
    }
}

/**
 * The extended protocol surface of the attached [Player.protocolSink], or `null`
 * when no client is attached (or the sink predates [PlayerSendSink]). Every
 * `Player::send*` extension below gates on it exactly like Canary's
 * `if (client)`.
 */
val Player.playerSendSink: PlayerSendSink?
    get() = protocolSink as? PlayerSendSink

/** `Player::sendLeaderTeamFinder(reset)` (player.cpp:1808). */
fun Player.sendLeaderTeamFinder(reset: Boolean) {
    playerSendSink?.sendLeaderTeamFinder(reset)
}

/** `Player::sendTeamFinderList()` (player.cpp:1814). */
fun Player.sendTeamFinderList() {
    playerSendSink?.sendTeamFinderList()
}

/** `Player::sendAddMarker(pos, markType, desc)` (player.cpp:8396). */
fun Player.sendAddMarker(pos: Position, markType: Int, desc: String) {
    playerSendSink?.sendAddMarker(pos, markType, desc)
}

/** `Player::sendBasicData()` (player.cpp:2354). */
fun Player.sendBasicData() {
    playerSendSink?.sendBasicData()
}

/** `Player::sendExivaRestrictions()` (player.cpp:9242). */
fun Player.sendExivaRestrictions() {
    playerSendSink?.sendExivaRestrictions()
}

/** `Player::sendItemsPrice()` (player.cpp:4545). */
fun Player.sendItemsPrice() {
    playerSendSink?.sendItemsPrice()
}

/** `Player::sendTakeScreenshot(...)` (player.cpp:8550). */
fun Player.sendTakeScreenshot(
    screenshotType: MiscDataWriter.ScreenshotType,
    skillId: Int = 0,
    skillLevel: Int = 0,
    achievementName: String = "",
    raceId: Int = 0,
    bestiaryStep: Int = 0,
) {
    playerSendSink?.sendTakeScreenshot(screenshotType, skillId, skillLevel, achievementName, raceId, bestiaryStep)
}

/** `Player::sendClientCheck()` (player.cpp:2280). */
fun Player.sendClientCheck() {
    playerSendSink?.sendClientCheck()
}

/** `Player::sendEnterWorld()` (player.cpp:8522). */
fun Player.sendEnterWorld() {
    playerSendSink?.sendEnterWorld()
}

/** `Player::sendGameNews()` (player.cpp:2286). */
fun Player.sendGameNews() {
    playerSendSink?.sendGameNews()
}

/** `Player::sendExperienceTracker(rawExp, finalExp)` (player.cpp:2535). */
fun Player.sendExperienceTracker(rawExp: Long, finalExp: Long) {
    playerSendSink?.sendExperienceTracker(rawExp, finalExp)
}

/** `Player::sendFightModes()` (player.cpp:8528). */
fun Player.sendFightModes() {
    playerSendSink?.sendFightModes()
}

/** `Player::sendUpdateTile(tile, pos)` (player.cpp:8767). */
fun Player.sendUpdateTile(tile: Tile?, pos: Position) {
    playerSendSink?.sendUpdateTile(tile, pos)
}

/** `Player::sendCreatureTurn(creature)` (player.cpp:8809). */
fun Player.sendCreatureTurn(creature: Creature) {
    playerSendSink?.sendCreatureTurn(creature)
}

/** `Player::sendTextWindow(item, maxlen, canWrite)` (player.cpp:2390). */
fun Player.sendTextWindow(item: Item, maxlen: Int, canWrite: Boolean) {
    playerSendSink?.sendTextWindow(item, maxlen, canWrite)
}

/** `Player::sendInventoryIds()` (player.cpp:9287). */
fun Player.sendInventoryIds() {
    playerSendSink?.sendInventoryIds()
}

/** `Player::sendCoinBalance()` (player.cpp:9275). */
fun Player.sendCoinBalance() {
    playerSendSink?.sendCoinBalance()
}

/** `Player::sendCreatureChangeOutfit(creature, outfit)` (player.cpp:8861). */
fun Player.sendCreatureChangeOutfit(creature: Creature, outfit: Outfit) {
    playerSendSink?.sendCreatureChangeOutfit(creature, outfit)
}

/** `Player::sendCreatureReload(creature)` (player.cpp:8837). */
fun Player.sendCreatureReload(creature: Creature) {
    playerSendSink?.sendCreatureReload(creature)
}

/** `Player::sendCreatureEmblem(creature)` (player.cpp:7306). */
fun Player.sendCreatureEmblem(creature: Creature) {
    playerSendSink?.sendCreatureEmblem(creature)
}

/** `Player::sendCreatureSquare(creature, color)` (player.cpp:8849). */
fun Player.sendCreatureSquare(creature: Creature, color: Int) {
    playerSendSink?.sendCreatureSquare(creature, color)
}

/** `Player::sendPartyCreatureUpdate(creature)` (player.cpp:2145). */
fun Player.sendPartyCreatureUpdate(creature: Creature) {
    playerSendSink?.sendPartyCreatureUpdate(creature)
}

/** `Player::sendPartyCreatureShield(creature)` (player.cpp:2151). */
fun Player.sendPartyCreatureShield(creature: Creature) {
    playerSendSink?.sendPartyCreatureShield(creature)
}

/** `Player::sendPartyCreatureSkull(creature)` (player.cpp:2157). */
fun Player.sendPartyCreatureSkull(creature: Creature) {
    playerSendSink?.sendPartyCreatureSkull(creature)
}

/** `Player::sendPartyCreatureHealth(creature, healthPercent)` (player.cpp:2163). */
fun Player.sendPartyCreatureHealth(creature: Creature, healthPercent: Int) {
    playerSendSink?.sendPartyCreatureHealth(creature, healthPercent)
}

/** `Player::sendPartyPlayerMana(player, manaPercent)` (player.cpp:2169). */
fun Player.sendPartyPlayerMana(player: Player, manaPercent: Int) {
    playerSendSink?.sendPartyPlayerMana(player, manaPercent)
}

/** `Player::sendPartyCreatureShowStatus(creature, showStatus)` (player.cpp:2175). */
fun Player.sendPartyCreatureShowStatus(creature: Creature, showStatus: Boolean) {
    playerSendSink?.sendPartyCreatureShowStatus(creature, showStatus)
}

/** `Player::sendPartyPlayerVocation(player)` (player.cpp:2181). */
fun Player.sendPartyPlayerVocation(player: Player) {
    playerSendSink?.sendPartyPlayerVocation(player)
}

/** `Player::sendForgingData()` (player.cpp:4551). */
fun Player.sendForgingData() {
    playerSendSink?.sendForgingData()
}

/** `Player::sendOpenForge()` (player.cpp:11795). */
fun Player.sendOpenForge() {
    playerSendSink?.sendOpenForge()
}

/** `Player::sendForgeError(returnValue)` (player.cpp:11801). */
fun Player.sendForgeError(returnValue: ReturnValue) {
    playerSendSink?.sendForgeError(returnValue)
}

/** `Player::sendForgeResult(...)` (player.cpp:11807). */
@Suppress("LongParameterList")
fun Player.sendForgeResult(
    actionType: ForgeAction_t,
    leftItemId: Int,
    leftTier: Int,
    rightItemId: Int,
    rightTier: Int,
    success: Boolean,
    bonus: Int,
    coreCount: Int,
    convergence: Boolean,
) {
    playerSendSink?.sendForgeResult(
        actionType,
        leftItemId,
        leftTier,
        rightItemId,
        rightTier,
        success,
        bonus,
        coreCount,
        convergence,
    )
}

/** `Player::sendForgeHistory(page)` (player.cpp:11813). */
fun Player.sendForgeHistory(page: Int) {
    playerSendSink?.sendForgeHistory(page)
}

/** `Player::sendDepotItems(itemMap, count)` (player.cpp:9257). */
fun Player.sendDepotItems(entries: List<DepotSearchWriter.DepotItemCount>, count: Int) {
    playerSendSink?.sendDepotItems(entries, count)
}

// `Player::sendCloseDepotSearch()` (player.cpp:9263) is a member seam on
// `Player` (`var sendCloseDepotSearch`), which shadows an extension of the same
// name; call sites reach the sink through [playerSendSink] instead.

/** `Player::sendShop(npc)` (player.cpp:2402). */
fun Player.sendShop(npc: Npc) {
    playerSendSink?.sendShop(npc)
}

/** `Player::sendCloseShop()` (player.cpp:2442). */
fun Player.sendCloseShop() {
    playerSendSink?.sendCloseShop()
}

/** `Player::sendBosstiaryEntryChanged(bossid)` (player.cpp:12732). */
fun Player.sendBosstiaryEntryChanged(bossId: Long) {
    playerSendSink?.sendBosstiaryEntryChanged(bossId)
}

/** `Player::sendNetworkMessage(NetworkMessage&)` (player.cpp:8534). */
fun Player.sendNetworkMessage(payload: ByteArray) {
    playerSendSink?.sendNetworkMessage(payload)
}
