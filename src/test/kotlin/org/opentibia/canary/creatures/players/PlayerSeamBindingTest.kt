package org.opentibia.canary.creatures.players

import io.netty.buffer.UnpooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.forge.ForgeDataWriter
import org.opentibia.canary.systems.wheel.WheelOnThink

/**
 * State-change wiring tests for the seam-only `Player::send*` bindings: a
 * mutation through the ported `Player`/`Game` entry point must reach the
 * per-player [PlayerFrameSink] and emit the Canary frame for that sender.
 *
 * Canary references:
 *  - `Player::triggerTranscendence` -> `sendSkills`/`sendStats`/`sendBasicData` (player.cpp:11215-11217)
 *  - `Player::updateState` -> `sendInventoryIds`/`sendStats` (player.cpp:6056-6064)
 *  - `Player::setFightMode` -> `sendStats`/`sendSkills` (player.cpp:6215-6220)
 *  - `Game::playerSetFightModes` (game.cpp:7044)
 *  - `Player::sendForgingData` (player.cpp:4551, called from game.cpp:7536, 7641)
 *  - `Game::reloadCreature` -> `Player::sendCreatureReload` (game.cpp:7822)
 *  - `Game::playerRequestCloseDepotSearch` -> `Player::sendCloseDepotSearch` (game.cpp:6933)
 *  - `Party::updatePlayerMana` / `updatePlayerVocation` -> `sendPartyPlayerMana`/`sendPartyPlayerVocation` (party.cpp:846-882)
 *  - `Player::sendPlayerPartyIcons` -> `sendPartyCreatureShield`/`sendPartyCreatureSkull` (player.cpp:7797-7800)
 */
class PlayerSeamBindingTest {

    private val center = Position(1000, 1000, 7)
    private val adjacent = Position(1001, 1000, 7)

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

    private class Bound(val player: Player, val frames: MutableList<ByteArray>)

    private fun bind(player: Player, map: GameMap = GameMap(), pvpWorld: Boolean = false): Bound {
        val frames = mutableListOf<ByteArray>()
        player.protocolSink = PlayerFrameSink(
            player = player,
            map = map,
            frameAllocatorProvider = { UnpooledByteBufAllocator.DEFAULT },
            frameWriter = { frame ->
                val bytes = ByteArray(frame.readableBytes())
                frame.readBytes(bytes)
                frame.release()
                frames.add(bytes)
            },
            pvpWorld = { pvpWorld },
        )
        return Bound(player, frames)
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun opcodes(frames: List<ByteArray>): List<Byte> = frames.map { it[0] }

    @Test
    fun `triggerTranscendence emits skills stats basic data and the attention message`() {
        val player = newPlayer()
        player.addItem(
            Item(1, 1, ItemType(id = 1, name = "Legs", upgradeClassification = 1)).apply { setTier(1) },
            Slots_t.CONST_SLOT_LEGS,
        )
        player.wheel().setOnThinkTimer(WheelOnThink.AVATAR_FORGE, 0)
        val bound = bind(player)

        player.triggerTranscendence(
            now = 10_000L,
            randomChance = 0.0,
            zoneType = { ZoneType.ZONE_NORMAL },
            checkLastAggressiveActionWithin = { true },
        )

        assertThat(opcodes(bound.frames)).containsExactly(
            0xA1.toByte(),
            0xA0.toByte(),
            0x9F.toByte(),
            0xB4.toByte(),
        )
    }

    @Test
    fun `updateState emits inventory ids and stats`() {
        val bound = bind(newPlayer())

        bound.player.updateState()

        assertThat(opcodes(bound.frames)).containsExactly(0xF5.toByte(), 0xA0.toByte())
    }

    @Test
    fun `setFightMode resends stats and skills`() {
        val bound = bind(newPlayer())

        bound.player.setFightMode(FightMode.Defense)

        assertThat(opcodes(bound.frames)).containsExactly(0xA0.toByte(), 0xA1.toByte())
    }

    @Test
    fun `playerSetFightModes resends stats and skills`() {
        val game = Game(GameMap(), GameDispatcher())
        val bound = bind(newPlayer())
        game.addPlayer(bound.player)

        game.playerSetFightModes(bound.player.id, FightMode.Defense, chaseMode = true, secureMode = false)

        assertThat(opcodes(bound.frames)).containsExactly(0xA0.toByte(), 0xA1.toByte())
        assertThat(bound.player.chaseMode).isEqualTo(org.opentibia.canary.creatures.ChaseMode.Follow)
    }

    @Test
    fun `the forging data seam emits through the player sink`() {
        val game = Game(GameMap(), GameDispatcher())
        val player = newPlayer()
        val frames = mutableListOf<ByteArray>()
        player.protocolSink = PlayerFrameSink(
            player = player,
            map = game.map,
            frameAllocatorProvider = { UnpooledByteBufAllocator.DEFAULT },
            frameWriter = { frame ->
                val out = ByteArray(frame.readableBytes())
                frame.readBytes(out)
                frame.release()
                frames.add(out)
            },
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
            },
        )

        game.sendForgingData(player)

        assertThat(frames).hasSize(1)
        assertThat(frames[0][0]).isEqualTo(0x86.toByte())
    }

    @Test
    fun `reloadCreature emits the reload frame to every same-floor spectator`() {
        val game = Game(GameMap(), GameDispatcher())
        game.map.getOrCreateTile(center).also { it.ground = Item(id = 100) }
        game.map.getOrCreateTile(adjacent).also { it.ground = Item(id = 100) }
        val observer = newPlayer()
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = adjacent)
        assertThat(game.placeCreature(rat, rat.position)).isTrue()
        val bound = bind(observer, game.map)

        game.reloadCreature(rat)

        assertThat(bound.frames).hasSize(1)
        assertThat(bound.frames[0]).startsWith(*bytes(0x6B, 0xE9, 0x03, 0xE8, 0x03, 0x07, 0x01))
    }

    @Test
    fun `playerRequestCloseDepotSearch emits the close depot search frame`() {
        val game = Game(GameMap(), GameDispatcher())
        val bound = bind(newPlayer())
        game.addPlayer(bound.player)
        bound.player.setDepotSearchIsOpen(1234, 0)

        game.playerRequestCloseDepotSearch(bound.player.id)

        assertThat(bound.frames).containsExactly(bytes(0x9A))
    }

    @Test
    fun `partyUpdatePlayerMana reaches every member and the leader`() {
        val game = Game(GameMap(), GameDispatcher())
        val leader = bind(newPlayer(id = 1, name = "Leader"))
        val member = bind(newPlayer(id = 2, name = "Member"))
        val party = Party(leader.player)
        assertThat(party.invitePlayer(member.player)).isTrue()
        assertThat(party.joinParty(member.player)).isTrue()

        game.partyUpdatePlayerMana(party, newPlayer(id = 3, name = "Target"), 50)

        assertThat(member.frames).hasSize(1)
        assertThat(member.frames[0]).endsWith(*bytes(0x8B, 0x03, 0x00, 0x00, 0x00, 0x0B, 0x32))
        assertThat(leader.frames).hasSize(1)
        assertThat(leader.frames[0]).endsWith(*bytes(0x8B, 0x03, 0x00, 0x00, 0x00, 0x0B, 0x32))
    }

    @Test
    fun `partyUpdatePlayerVocation reaches every member and the leader`() {
        val game = Game(GameMap(), GameDispatcher())
        val leader = bind(newPlayer(id = 1, name = "Leader"))
        val member = bind(newPlayer(id = 2, name = "Member"))
        val party = Party(leader.player)
        assertThat(party.invitePlayer(member.player)).isTrue()
        assertThat(party.joinParty(member.player)).isTrue()

        game.partyUpdatePlayerVocation(party, newPlayer(id = 3, name = "Target"))

        assertThat(member.frames).hasSize(1)
        assertThat(member.frames[0]).startsWith(*bytes(0x8B, 0x03, 0x00, 0x00, 0x00, 0x00))
        assertThat(leader.frames).hasSize(1)
        assertThat(leader.frames[0]).startsWith(*bytes(0x8B, 0x03, 0x00, 0x00, 0x00, 0x00))
    }

    @Test
    fun `sendPlayerPartyIcons emits the shield and skull frames through the sink`() {
        val bound = bind(newPlayer(), pvpWorld = true)
        val target = newPlayer(id = 7, name = "Mate")

        bound.player.sendPlayerPartyIcons(target)

        assertThat(bound.frames).hasSize(2)
        assertThat(bound.frames).allSatisfy { frame -> assertThat(frame[0]).isEqualTo(0x8B.toByte()) }
    }

    @Test
    fun `the seam senders are inert without an attached sink`() {
        val player = newPlayer()

        player.sendBasicData()
        player.sendInventoryIds()
        player.sendFightModes()
        player.sendCloseShop()

        assertThat(player.playerSendSink).isNull()
    }
}
