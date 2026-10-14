package org.opentibia.canary.creatures.players

import io.netty.buffer.UnpooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.TeamFinder
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.depot.DepotSearchWriter
import org.opentibia.canary.network.protocol.forge.ForgeDataWriter
import org.opentibia.canary.network.protocol.forge.ForgeWindow
import org.opentibia.canary.network.protocol.misc.MiscDataWriter
import org.opentibia.canary.network.protocol.npcshop.ShopBlock
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Frame-level tests for [PlayerFrameSink], the extended `ProtocolGame` surface
 * behind the 38 `Player::send*` seams wave 53 left as client forwards
 * (player.cpp). Each case drives one sender and asserts the bytes the ported
 * writer emits, resolved from the player's own state and the injected world
 * providers.
 */
class PlayerSendSinkTest {

    private val center = Position(1000, 1000, 7)
    private val adjacent = Position(1001, 1000, 7)

    private class Bound(
        val player: Player,
        val sink: PlayerFrameSink,
        val frames: MutableList<ByteArray>,
    )

    private fun newPlayer(id: Long = 1, name: String = "Hero", position: Position = center): Player =
        Player(
            id = id,
            name = name,
            maxHealth = 150,
            health = 100,
            maxMana = 50,
            mana = 50,
            capacity = 400.0,
            position = position,
        )

    private fun bind(
        player: Player = newPlayer(),
        map: GameMap = GameMap(),
        configure: PlayerFrameSinkBuilder.() -> Unit = {},
    ): Bound {
        val frames = mutableListOf<ByteArray>()
        val builder = PlayerFrameSinkBuilder(player, map, frames)
        builder.configure()
        val sink = builder.build()
        player.protocolSink = sink
        return Bound(player, sink, frames)
    }

    /** Named provider inputs for [PlayerFrameSink], mirroring the C++ globals. */
    private class PlayerFrameSinkBuilder(
        private val player: Player,
        private val map: GameMap,
        private val frames: MutableList<ByteArray>,
    ) {
        var itemPrices: () -> Map<Int, Map<Int, Long>> = { emptyMap() }
        var itemHasUpgradeClassification: (Int) -> Boolean = { false }
        var premiumDays: () -> Int = { 0 }
        var premiumNowSeconds: () -> Long = { 0L }
        var basicDataSpells: () -> List<MiscDataWriter.BasicDataSpell> = { emptyList() }
        var magicShield: (Player) -> Boolean = { false }
        var preyWindowUnlocked: (Player) -> Boolean = { true }
        var coinBalances: (Player) -> Pair<Long, Long> = { 0L to 0L }
        var teamFinder: (Player) -> TeamFinder? = { null }
        var teamFinderList: () -> Map<Long, TeamFinder> = { emptyMap() }
        var playerByGuid: (Long) -> Player? = { null }
        var removeTeamFinderListed: (Long) -> Unit = {}
        var pvpWorld: () -> Boolean = { false }
        var windowTextId: (Player) -> Long = { 0L }
        var forgeWindow: () -> ForgeWindow = { ForgeWindow() }
        var forgingDataFrame: () -> ForgeDataWriter.ForgingDataFrame? = { null }
        var shopInventoryMap: (Player) -> Map<Int, Int> = { emptyMap() }
        var shopHiddenSellItems: (Player) -> Boolean = { false }

        fun build(): PlayerFrameSink = PlayerFrameSink(
            player = player,
            map = map,
            frameAllocatorProvider = { UnpooledByteBufAllocator.DEFAULT },
            frameWriter = { frame ->
                val bytes = ByteArray(frame.readableBytes())
                frame.readBytes(bytes)
                frame.release()
                frames.add(bytes)
            },
            itemPrices = itemPrices,
            itemHasUpgradeClassification = itemHasUpgradeClassification,
            premiumDays = premiumDays,
            premiumNowSeconds = premiumNowSeconds,
            basicDataSpells = basicDataSpells,
            magicShield = magicShield,
            preyWindowUnlocked = preyWindowUnlocked,
            coinBalances = coinBalances,
            teamFinder = teamFinder,
            teamFinderList = teamFinderList,
            playerByGuid = playerByGuid,
            removeTeamFinderListed = removeTeamFinderListed,
            pvpWorld = pvpWorld,
            windowTextId = windowTextId,
            forgeWindow = forgeWindow,
            forgingDataFrame = forgingDataFrame,
            shopInventoryMap = shopInventoryMap,
            shopHiddenSellItems = shopHiddenSellItems,
        )
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun stringBytes(value: String): ByteArray = value.toByteArray()

    // --- team finder -------------------------------------------------------------

    @Test
    fun `sendLeaderTeamFinder reset writes the two byte reset frame and removes the listing`() {
        val bound = bind()
        var removed: Long? = null
        val player = bound.player
        val finder = TeamFinder(leaderGuid = player.getGUID())
        val sink = PlayerFrameSink(
            player,
            GameMap(),
            { UnpooledByteBufAllocator.DEFAULT },
            { frame ->
                val out = ByteArray(frame.readableBytes())
                frame.readBytes(out)
                frame.release()
                bound.frames.add(out)
            },
            teamFinder = { finder },
            removeTeamFinderListed = { removed = it },
        )
        player.protocolSink = sink

        sink.sendLeaderTeamFinder(true)

        assertThat(bound.frames).containsExactly(bytes(0x2C, 0x01))
        assertThat(removed).isEqualTo(player.getGUID())
    }

    @Test
    fun `sendLeaderTeamFinder without a listing writes nothing`() {
        val bound = bind()

        bound.sink.sendLeaderTeamFinder(true)

        assertThat(bound.frames).isEmpty()
    }

    @Test
    fun `sendLeaderTeamFinder writes the leader frame from the injected finder`() {
        var removed: Long? = null
        var finder: TeamFinder? = null
        val bound = bind { removeTeamFinderListed = { removed = it } }
        val player = bound.player
        finder = TeamFinder(
            minLevel = 10,
            maxLevel = 100,
            teamSlots = 4,
            freeSlots = 2,
            timestamp = 1234,
            leaderGuid = player.getGUID(),
        )
        val sink = PlayerFrameSink(
            player,
            GameMap(),
            { UnpooledByteBufAllocator.DEFAULT },
            { frame ->
                val out = ByteArray(frame.readableBytes())
                frame.readBytes(out)
                frame.release()
                bound.frames.add(out)
            },
            teamFinder = { finder },
            playerByGuid = { guid -> if (guid == player.getGUID()) player else null },
        )
        player.protocolSink = sink

        sink.sendLeaderTeamFinder(false)

        assertThat(bound.frames).containsExactly(
            bytes(
                0x2C, 0x00,
                0x0A, 0x00,
                0x64, 0x00,
                0x00,
                0x04, 0x00,
                0x02, 0x00,
                0xD2, 0x04, 0x00, 0x00,
                0x00,
                0x01, 0x00,
                0x01, 0x00, 0x00, 0x00,
                0x04, 0x00,
            ) + stringBytes("Hero") + bytes(0x01, 0x00, 0x00, 0x03, 0x00),
        )
        assertThat(removed).isNull()
    }

    @Test
    fun `sendTeamFinderList resolves the leader and the requesting player status`() {
        val bound = bind()
        val player = bound.player
        val leader = newPlayer(id = 42, name = "Leader")
        val finder = TeamFinder(
            minLevel = 10,
            maxLevel = 100,
            teamSlots = 4,
            freeSlots = 2,
            timestamp = 1234,
            leaderGuid = leader.getGUID(),
        )
        finder.membersMap[player.getGUID()] = TeamFinder.MEMBER_STATUS_ACCEPTED
        val sink = PlayerFrameSink(
            player,
            GameMap(),
            { UnpooledByteBufAllocator.DEFAULT },
            { frame ->
                val out = ByteArray(frame.readableBytes())
                frame.readBytes(out)
                frame.release()
                bound.frames.add(out)
            },
            teamFinderList = { mapOf(leader.getGUID() to finder) },
            playerByGuid = { guid ->
                when (guid) {
                    leader.getGUID() -> leader
                    player.getGUID() -> player
                    else -> null
                }
            },
        )
        player.protocolSink = sink

        sink.sendTeamFinderList()

        assertThat(bound.frames).containsExactly(
            bytes(
                0x2D, 0x00,
                0x01, 0x00,
                0x2A, 0x00, 0x00, 0x00,
                0x06, 0x00,
            ) + stringBytes("Leader") + bytes(
                0x0A, 0x00,
                0x64, 0x00,
                0x00,
                0x04, 0x00,
                0x02, 0x00,
                0xD2, 0x04, 0x00, 0x00,
                0x00,
                0x03,
            ),
        )
    }

    // --- misc markers/data -------------------------------------------------------

    @Test
    fun `sendAddMarker writes the minimap marker frame`() {
        val bound = bind()

        bound.sink.sendAddMarker(Position(100, 200, 7), 2, "hunt")

        assertThat(bound.frames).containsExactly(
            bytes(0xDD, 0x00, 0x64, 0x00, 0xC8, 0x00, 0x07, 0x02, 0x04, 0x00) + stringBytes("hunt"),
        )
    }

    @Test
    fun `sendBasicData writes the resolved basic data frame`() {
        val bound = bind { premiumNowSeconds = { 0L } }

        bound.sink.sendBasicData()

        assertThat(bound.frames).containsExactly(
            bytes(0x9F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00),
        )
    }

    @Test
    fun `sendBasicData writes the premium expiry and the spell list`() {
        val bound = bind {
            premiumDays = { 29 }
            premiumNowSeconds = { 0L }
            magicShield = { true }
            basicDataSpells = {
                listOf(MiscDataWriter.BasicDataSpell(spellId = 42))
            }
        }
        bound.player.premium = true

        bound.sink.sendBasicData()

        val frame = bound.frames.single()
        val expiry = (29 + 1) * 86400
        assertThat(frame).isEqualTo(
            bytes(0x9F, 0x01) + bytes(
                expiry and 0xFF,
                (expiry shr 8) and 0xFF,
                (expiry shr 16) and 0xFF,
                (expiry shr 24) and 0xFF,
            ) + bytes(0x00, 0x01, 0x01, 0x00, 0x2A, 0x00, 0x01),
        )
    }

    @Test
    fun `sendBasicData gates the prey window on the vocation and group`() {
        val player = newPlayer()
        val frames = mutableListOf<ByteArray>()
        val sink = PlayerFrameSink(
            player,
            GameMap(),
            { UnpooledByteBufAllocator.DEFAULT },
            { frame ->
                val out = ByteArray(frame.readableBytes())
                frame.readBytes(out)
                frame.release()
                frames.add(out)
            },
        )

        sink.sendBasicData()

        assertThat(frames.single()[7]).isEqualTo(0x00.toByte())
        assertThat(frames.single()[6]).isEqualTo(0x00.toByte())
    }

    @Test
    fun `sendExivaRestrictions writes the flags and empty update lists`() {
        val bound = bind()

        bound.sink.sendExivaRestrictions()

        assertThat(bound.frames).containsExactly(
            bytes(
                0xCA,
                0x00, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
            ),
        )
    }

    @Test
    fun `sendItemsPrice writes every injected tier price`() {
        val bound = bind {
            itemPrices = { mapOf(1234 to mapOf(1 to 5000L)) }
            itemHasUpgradeClassification = { true }
        }

        bound.sink.sendItemsPrice()

        assertThat(bound.frames).containsExactly(
            bytes(0xCD, 0x01, 0x00, 0xD2, 0x04, 0x01, 0x88, 0x13, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        )
    }

    @Test
    fun `sendTakeScreenshot writes the level-up selector with the player level`() {
        val bound = bind()

        bound.sink.sendTakeScreenshot(MiscDataWriter.ScreenshotType.LevelUp)

        assertThat(bound.frames).containsExactly(bytes(0x75, 0x04, 0x01, 0x00))
    }

    @Test
    fun `sendTakeScreenshot skill-up writes the skill id and level`() {
        val bound = bind()

        bound.sink.sendTakeScreenshot(
            MiscDataWriter.ScreenshotType.SkillUp,
            skillId = 4,
            skillLevel = 33,
        )

        assertThat(bound.frames).containsExactly(bytes(0x75, 0x05, 0x04, 0x21, 0x00))
    }

    @Test
    fun `sendClientCheck writes the probe frame`() {
        val bound = bind()

        bound.sink.sendClientCheck()

        assertThat(bound.frames).containsExactly(bytes(0x63, 0x01, 0x00, 0x00, 0x00, 0x01))
    }

    @Test
    fun `sendEnterWorld writes the enter world frame`() {
        val bound = bind()

        bound.sink.sendEnterWorld()

        assertThat(bound.frames).containsExactly(bytes(0x0F))
    }

    @Test
    fun `sendGameNews writes the game news frame`() {
        val bound = bind()

        bound.sink.sendGameNews()

        assertThat(bound.frames).containsExactly(bytes(0x98, 0x01, 0x00, 0x00, 0x00, 0x01))
    }

    @Test
    fun `sendExperienceTracker writes both experience values`() {
        val bound = bind()

        bound.sink.sendExperienceTracker(5, 7)

        assertThat(bound.frames).containsExactly(
            bytes(
                0xAF,
                0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            ),
        )
    }

    @Test
    fun `sendFightModes writes the chase and secure modes`() {
        val bound = bind()
        bound.player.secureMode = true

        bound.sink.sendFightModes()

        assertThat(bound.frames).containsExactly(bytes(0xA7, 0x00, 0x01, 0x00))
    }

    // --- tile sends --------------------------------------------------------------

    @Test
    fun `sendUpdateTile writes the empty tile description for a missing tile`() {
        val bound = bind()

        bound.sink.sendUpdateTile(null, Position(100, 200, 7))

        assertThat(bound.frames).containsExactly(
            bytes(0x69, 0x64, 0x00, 0xC8, 0x00, 0x07, 0x01, 0xFF),
        )
    }

    @Test
    fun `sendTextWindow writes the item and the max length`() {
        val bound = bind { windowTextId = { 55L } }
        val item = Item(id = 1940)

        bound.sink.sendTextWindow(item, 10, true)

        assertThat(bound.frames).containsExactly(
            bytes(
                0x96, 0x37, 0x00, 0x00, 0x00,
                0x94, 0x07,
                0x0A, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00,
                0x00, 0x00,
            ),
        )
    }

    // --- inventory/coin ----------------------------------------------------------

    @Test
    fun `sendInventoryIds writes the empty inventory count`() {
        val bound = bind()

        bound.sink.sendInventoryIds()

        assertThat(bound.frames).containsExactly(bytes(0xF5, 0x00, 0x00))
    }

    @Test
    fun `sendInventoryIds writes every injected inventory entry`() {
        val bound = bind()
        val player = bound.player
        player.addItem(Item(id = 1234), org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK)

        bound.sink.sendInventoryIds()

        assertThat(bound.frames.last()).isEqualTo(
            bytes(0xF5, 0x01, 0x00, 0xD2, 0x04, 0x00, 0x01),
        )
    }

    @Test
    fun `sendCoinBalance writes both balances`() {
        val bound = bind { coinBalances = { 1000L to 250L } }

        bound.sink.sendCoinBalance()

        assertThat(bound.frames).containsExactly(
            bytes(
                0xF2, 0x01,
                0xDF, 0x01,
                0xE8, 0x03, 0x00, 0x00,
                0xFA, 0x00, 0x00, 0x00,
                0xE8, 0x03, 0x00, 0x00,
            ),
        )
    }

    // --- creature sends ----------------------------------------------------------

    @Test
    fun `sendCreatureChangeOutfit forwards to the creature outfit frame`() {
        val bound = bind()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = center)

        bound.sink.sendCreatureChangeOutfit(rat, Outfit(lookType = 128))

        assertThat(bound.frames).containsExactly(
            bytes(0x8E, 0x88, 0x13, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        )
    }

    @Test
    fun `sendCreatureReload writes the update-tile creature frame`() {
        val game = org.opentibia.canary.game.Game(GameMap(), org.opentibia.canary.game.scheduling.GameDispatcher())
        game.map.getOrCreateTile(center).also { it.ground = Item(id = 100) }
        game.map.getOrCreateTile(adjacent).also { it.ground = Item(id = 100) }
        val observer = newPlayer()
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = adjacent)
        assertThat(game.placeCreature(rat, rat.position)).isTrue()
        val frames = mutableListOf<ByteArray>()
        val sink = PlayerFrameSink(
            observer,
            game.map,
            { UnpooledByteBufAllocator.DEFAULT },
            { frame ->
                val out = ByteArray(frame.readableBytes())
                frame.readBytes(out)
                frame.release()
                frames.add(out)
            },
        )

        sink.sendCreatureReload(rat)

        assertThat(frames).hasSize(1)
        assertThat(frames[0]).startsWith(*bytes(0x6B, 0xE9, 0x03, 0xE8, 0x03, 0x07, 0x01))
    }

    @Test
    fun `sendCreatureEmblem removes then adds the creature on its tile`() {
        val game = org.opentibia.canary.game.Game(GameMap(), org.opentibia.canary.game.scheduling.GameDispatcher())
        game.map.getOrCreateTile(center).also { it.ground = Item(id = 100) }
        game.map.getOrCreateTile(adjacent).also { it.ground = Item(id = 100) }
        val observer = newPlayer()
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = adjacent)
        assertThat(game.placeCreature(rat, rat.position)).isTrue()
        val frames = mutableListOf<ByteArray>()
        val sink = PlayerFrameSink(
            observer,
            game.map,
            { UnpooledByteBufAllocator.DEFAULT },
            { frame ->
                val out = ByteArray(frame.readableBytes())
                frame.readBytes(out)
                frame.release()
                frames.add(out)
            },
        )

        sink.sendCreatureEmblem(rat)

        assertThat(frames).hasSize(1)
        assertThat(frames[0]).startsWith(*bytes(0x6C, 0xE9, 0x03, 0xE8, 0x03, 0x07, 0x01))
    }

    @Test
    fun `sendCreatureSquare writes the square color`() {
        val bound = bind()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = center)

        bound.sink.sendCreatureSquare(rat, 1)

        assertThat(bound.frames).containsExactly(bytes(0x93, 0x88, 0x13, 0x00, 0x00, 0x01, 0x01))
    }

    // --- party sends -------------------------------------------------------------

    @Test
    fun `sendPartyCreatureUpdate writes the party update frame`() {
        val bound = bind()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = center)

        bound.sink.sendPartyCreatureUpdate(rat)

        assertThat(bound.frames).hasSize(1)
        assertThat(bound.frames[0]).startsWith(*bytes(0x8B, 0x88, 0x13, 0x00, 0x00, 0x00))
    }

    @Test
    fun `party shield skull health and vocation fall back to the creature update`() {
        val bound = bind { pvpWorld = { true } }
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = center)

        bound.sink.sendPartyCreatureUpdate(rat)
        val update = bound.frames[0]

        bound.sink.sendPartyCreatureShield(rat)
        bound.sink.sendPartyCreatureSkull(rat)
        bound.sink.sendPartyCreatureHealth(rat, 80)
        bound.sink.sendPartyPlayerVocation(newPlayer(id = 7, name = "Mate"))

        assertThat(bound.frames[1]).isEqualTo(update)
        assertThat(bound.frames[2]).isEqualTo(update)
        assertThat(bound.frames[3]).isEqualTo(update)
        assertThat(bound.frames[4]).startsWith(*bytes(0x8B, 0x07, 0x00, 0x00, 0x00, 0x00))
    }

    @Test
    fun `sendPartyPlayerMana writes the update and the mana percent`() {
        val bound = bind()
        val target = newPlayer(id = 7, name = "Mate")

        bound.sink.sendPartyPlayerMana(target, 50)

        val frame = bound.frames.single()
        assertThat(frame).startsWith(*bytes(0x8B, 0x07, 0x00, 0x00, 0x00, 0x00))
        assertThat(frame).endsWith(*bytes(0x8B, 0x07, 0x00, 0x00, 0x00, 0x0B, 0x32))
    }

    @Test
    fun `sendPartyCreatureShowStatus writes the update and the status indicator`() {
        val bound = bind()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = center)

        bound.sink.sendPartyCreatureShowStatus(rat, true)

        val frame = bound.frames.single()
        assertThat(frame).startsWith(*bytes(0x8B, 0x88, 0x13, 0x00, 0x00, 0x00))
        assertThat(frame).endsWith(*bytes(0x8B, 0x88, 0x13, 0x00, 0x00, 0x0C, 0x01))
    }

    // --- forge sends -------------------------------------------------------------

    @Test
    fun `sendOpenForge writes the forge window frame`() {
        val bound = bind()

        bound.sink.sendOpenForge()

        val frame = bound.frames.single()
        assertThat(frame[0]).isEqualTo(0x87.toByte())
        assertThat(frame).endsWith(*bytes(0x00, 0x00))
    }

    @Test
    fun `sendForgeError writes the dialog message and closes the window`() {
        val bound = bind()

        bound.sink.sendForgeError(ReturnValue.NOTPOSSIBLE)

        val message = "Sorry, not possible."
        assertThat(bound.frames).containsExactly(
            bytes(0xED, 0x14, 0x14, 0x00) + stringBytes(message) + bytes(0x89),
        )
    }

    @Test
    fun `sendForgeResult writes the fusion outcome`() {
        val bound = bind()

        bound.sink.sendForgeResult(
            actionType = ForgeAction_t.FUSION,
            leftItemId = 100,
            leftTier = 1,
            rightItemId = 200,
            rightTier = 2,
            success = true,
            bonus = 0,
            coreCount = 0,
            convergence = false,
        )

        assertThat(bound.frames).containsExactly(
            bytes(0x8A, 0x00, 0x00, 0x01, 0x64, 0x00, 0x01, 0xC8, 0x00, 0x02, 0x00),
        )
    }

    @Test
    fun `sendForgeHistory writes the empty first page`() {
        val bound = bind()

        bound.sink.sendForgeHistory(0)

        assertThat(bound.frames).containsExactly(bytes(0x88, 0x00, 0x00, 0x01, 0x00, 0x00))
    }

    @Test
    fun `sendForgingData writes the injected config frame`() {
        val bound = bind {
            forgingDataFrame = {
                ForgeDataWriter.ForgingDataFrame(
                    classifications = emptyList(),
                    tierCorePrices = emptyMap(),
                    convergenceFusionPrices = emptyMap(),
                    convergenceTransferPrices = emptyMap(),
                    config = ForgeDataWriter.ForgingDataConfig(
                        costOneSliver = 0,
                        sliverAmount = 0,
                        coreCost = 0,
                        dustLevel = 0,
                        maxDust = 0,
                        fusionDustCost = 0,
                        convergenceFusionDustCost = 0,
                        transferDustCost = 0,
                        convergenceTransferDustCost = 0,
                        baseSuccessRate = 0,
                        bonusSuccessRate = 0,
                        tierLossReduction = 0,
                    ),
                )
            }
        }

        bound.sink.sendForgingData()

        assertThat(bound.frames).containsExactly(
            bytes(
                0x86,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x4B,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            ),
        )
    }

    @Test
    fun `sendForgingData is inert without a frame`() {
        val bound = bind()

        bound.sink.sendForgingData()

        assertThat(bound.frames).isEmpty()
    }

    // --- depot/shop/bosstiary ----------------------------------------------------

    @Test
    fun `sendDepotItems writes the tier count rows`() {
        val bound = bind()

        bound.sink.sendDepotItems(listOf(DepotSearchWriter.DepotItemCount(100, 1, 5)), 1)

        assertThat(bound.frames).containsExactly(
            bytes(0x94, 0x01, 0x00, 0x64, 0x00, 0x00, 0x05, 0x00),
        )
    }

    @Test
    fun `sendCloseDepotSearch writes the close frame`() {
        val bound = bind()

        bound.sink.sendCloseDepotSearch()

        assertThat(bound.frames).containsExactly(bytes(0x9A))
    }

    @Test
    fun `sendShop writes the npc name currency and blocks`() {
        val bound = bind()
        val npcType = NpcType("Merchant")
        npcType.info.shopItemVector.add(
            ShopBlock(itemId = 100, itemName = "Gold Coin", itemBuyPrice = 10, itemSellPrice = 5),
        )
        val npc = Npc(id = 2, name = "Merchant", position = center, npcType = npcType)

        bound.sink.sendShop(npc)

        val frame = bound.frames.single()
        assertThat(frame).startsWith(
            *bytes(0x7A, 0x08, 0x00) + stringBytes("Merchant") + bytes(0xD7, 0x0B, 0x00, 0x00, 0x01, 0x00, 0x64, 0x00),
        )
    }

    @Test
    fun `sendCloseShop writes the close shop frame`() {
        val bound = bind()

        bound.sink.sendCloseShop()

        assertThat(bound.frames).containsExactly(bytes(0x7C))
    }

    @Test
    fun `sendBosstiaryEntryChanged writes the boss id`() {
        val bound = bind()

        bound.sink.sendBosstiaryEntryChanged(42)

        assertThat(bound.frames).containsExactly(bytes(0xE6, 0x2A, 0x00, 0x00, 0x00))
    }

    // --- generic network message -------------------------------------------------

    @Test
    fun `sendNetworkMessage writes the staged payload bytes`() {
        val bound = bind()

        bound.sink.sendNetworkMessage(bytes(0x01, 0x02, 0x03))

        assertThat(bound.frames).containsExactly(bytes(0x01, 0x02, 0x03))
    }
}
