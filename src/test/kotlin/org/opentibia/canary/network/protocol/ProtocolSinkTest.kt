package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Frame-level tests for [ProtocolSink] over the in-memory sink. Each case feeds
 * live entity state through the sink and pins the exact bytes the ported
 * writers emit, so the transport (Netty) and test (in-memory) sinks share one
 * verified serializer path.
 *
 * Canary references:
 *  - `ProtocolGame::sendTextMessage`          (0xB4, protocolgame.cpp:6070)
 *  - `Player::sendCancelMessage`              (0xB4/21, player.cpp:2111)
 *  - `ProtocolGame::sendCloseContainer`       (0x6F, protocolgame.cpp:7981)
 *  - `ProtocolGame::sendCreatureHealth`       (0x8C, protocolgame.cpp:8316)
 *  - `ProtocolGame::sendCreatureOutfit`       (0x8E, protocolgame.cpp:4547)
 *  - `ProtocolGame::sendChangeSpeed`          (0x8F, protocolgame.cpp:8137)
 *  - `ProtocolGame::RemoveTileThing`          (0x6C, protocolgame.cpp:10390)
 *  - `ProtocolGame::sendInventoryItem`        (0x78/0x79, protocolgame.cpp:8876)
 *  - `ProtocolGame::sendContainer`            (0x6E, protocolgame.cpp:6336)
 *  - `ProtocolGame::sendAddContainerItem`     (0x70, protocolgame.cpp:8934)
 *  - `ProtocolGame::sendUpdateContainerItem`  (0x71, protocolgame.cpp:8943)
 *  - `ProtocolGame::sendRemoveContainerItem`  (0x72, protocolgame.cpp:8952)
 *  - `Player::sendCreatureAppear`             (0x6A, player.cpp:8785)
 *  - `Player::sendCreatureMove`               (0x6D, player.cpp:8803)
 *  - `ProtocolGame::sendStats`                (0xA0, protocolgame.cpp:5813)
 *  - `ProtocolGame::sendSkills`               (0xA1, protocolgame.cpp:8161)
 */
class ProtocolSinkTest {

    private val observerPosition = Position(100, 200, 7)

    private fun observer(): Player = Player(
        id = 1,
        name = "Observer",
        maxHealth = 150,
        health = 150,
        maxMana = 50,
        mana = 50,
        capacity = 400.0,
        position = observerPosition,
    )

    private fun monster(
        id: Long = 5000,
        health: Int = 80,
        position: Position = observerPosition,
    ): Monster = Monster(id, "Rat", maxHealth = 100, health = health, position = position)

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private val zeroDouble = bytes(0x04, 0xFF, 0xFF, 0xFF, 0x7F)

    @Test
    fun `sendTextMessage writes the typed text frame 0xB4`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendTextMessage(MessageClass.MESSAGE_FAILURE, "Hi")

        assertThat(sink.frames).containsExactly(
            bytes(0xB4, 0x15, 0x02, 0x00, 'H'.code, 'i'.code),
        )
    }

    @Test
    fun `sendCancelMessage resolves the ReturnValue through getReturnMessage`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendCancelMessage(ReturnValue.NOTPOSSIBLE)

        assertThat(sink.frames).containsExactly(
            bytes(0xB4, 0x15, 0x14, 0x00) + "Sorry, not possible.".toByteArray(),
        )
    }

    @Test
    fun `sendCancelMessage writes a plain cancel text`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendCancelMessage("No way.")

        assertThat(sink.frames).containsExactly(
            bytes(0xB4, 0x15, "No way.".length, 0x00) + "No way.".toByteArray(),
        )
    }

    @Test
    fun `sendCloseContainer writes 0x6F with the container id`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendCloseContainer(5)

        assertThat(sink.frames).containsExactly(bytes(0x6F, 0x05))
    }

    @Test
    fun `sendCreatureHealth writes 0x8C with the clamped health percent`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendCreatureHealth(monster())

        assertThat(sink.frames).containsExactly(
            bytes(0x8C, 0x88, 0x13, 0x00, 0x00, 0x50),
        )
    }

    @Test
    fun `sendCreatureOutfit writes 0x8E with the AddOutfit payload`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendCreatureOutfit(
            monster(),
            Outfit(lookType = 128, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4, lookAddons = 5),
        )

        assertThat(sink.frames).containsExactly(
            bytes(0x8E, 0x88, 0x13, 0x00, 0x00, 0x80, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x00, 0x00),
        )
    }

    @Test
    fun `sendChangeSpeed writes 0x8F with the base speed first`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendChangeSpeed(monster(), stepSpeed = 250)

        assertThat(sink.frames).containsExactly(
            bytes(0x8F, 0x88, 0x13, 0x00, 0x00, 0xC8, 0x00, 0xFA, 0x00),
        )
    }

    @Test
    fun `sendRemoveTileThing writes 0x6C with position and stackpos`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendRemoveTileThing(Position(305, 400, 7), 3)

        assertThat(sink.frames).containsExactly(
            bytes(0x6C, 0x31, 0x01, 0x90, 0x01, 0x07, 0x03),
        )
    }

    @Test
    fun `sendInventoryItem writes 0x78 for an item and 0x79 for an empty slot`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendInventoryItem(Slots_t.CONST_SLOT_BACKPACK, Item(id = 1234))
        sink.sendInventoryItem(Slots_t.CONST_SLOT_BACKPACK, null)

        assertThat(sink.frames).containsExactly(
            bytes(0x78, 0x03, 0xD2, 0x04),
            bytes(0x79, 0x03),
        )
    }

    @Test
    fun `sendAddContainerItem writes 0x70 with cid slot and item`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendAddContainerItem(2, 5, Item(id = 1234))

        assertThat(sink.frames).containsExactly(
            bytes(0x70, 0x02, 0x05, 0x00, 0xD2, 0x04),
        )
    }

    @Test
    fun `sendUpdateContainerItem writes 0x71 with cid slot and item`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendUpdateContainerItem(2, 5, Item(id = 1234))

        assertThat(sink.frames).containsExactly(
            bytes(0x71, 0x02, 0x05, 0x00, 0xD2, 0x04),
        )
    }

    @Test
    fun `sendRemoveContainerItem writes 0x72 with the zero fallback item`() {
        val sink = InMemoryProtocolSink(observer())

        sink.sendRemoveContainerItem(2, 5, null)

        assertThat(sink.frames).containsExactly(
            bytes(0x72, 0x02, 0x05, 0x00, 0x00, 0x00),
        )
    }

    @Test
    fun `sendContainer writes 0x6E with the container window payload`() {
        val sink = InMemoryProtocolSink(observer())
        val container = Container(
            id = 2853,
            capacity = 20,
            type = ItemType(2853, "Backpack", isContainer = true),
        )

        sink.sendContainer(1, container, hasParent = false, firstIndex = 0)

        assertThat(sink.frames).containsExactly(
            bytes(0x6E, 0x01, 0x25, 0x0B, 0x00, 0x08, 0x00) +
                "Backpack".toByteArray() +
                bytes(
                    0x14, // capacity
                    0x00, // hasParent
                    0x00, // depot search + inside depot
                    0x01, // unlocked
                    0x00, // pagination
                    0x00, 0x00, // container size
                    0x00, 0x00, // first index
                    0x00, // written item count
                    0x00, 0x00, // store inbox category marker
                    0x01, // movable
                    0x00, // holding player
                ),
        )
    }

    @Test
    fun `sendCreatureAppear writes the full unknown-creature 0x6A frame`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(observerPosition)
        tile.ground = Item(id = 100)
        val sink = InMemoryProtocolSink(observer(), map)
        val rat = monster()
        tile.internalAddThing(rat)

        sink.sendCreatureAppear(rat, rat.position, isLogin = false)

        assertThat(sink.frames).containsExactly(
            bytes(
                0x6A, 0x64, 0x00, 0xC8, 0x00, 0x07, 0x01,
                0x61, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x88, 0x13, 0x00, 0x00,
                0x01,
                0x03, 0x00, 'R'.code, 'a'.code, 't'.code,
                0x50,
                0x02,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xD7,
                0xC8, 0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0xFF,
                0x00,
                0x01,
            ),
        )
    }

    @Test
    fun `sendCreatureMove writes the 0x6C removal when only the old position is visible`() {
        val sink = InMemoryProtocolSink(observer())
        val oldPos = Position(101, 200, 7)
        val newPos = Position(200, 200, 7)

        sink.sendCreatureMove(
            creature = monster(id = 5001, position = newPos),
            newPos = newPos,
            newStackPos = 0,
            oldPos = oldPos,
            oldStackPos = 0,
            teleport = false,
        )

        assertThat(sink.frames).containsExactly(
            bytes(0x6C, 0x65, 0x00, 0xC8, 0x00, 0x07, 0x00),
        )
    }

    @Test
    fun `sendStats writes 0xA0 from the live player state`() {
        val player = Player(
            id = 1,
            name = "Observer",
            maxHealth = 150,
            health = 100,
            maxMana = 50,
            mana = 50,
            capacity = 400.0,
            position = observerPosition,
        )
        val sink = InMemoryProtocolSink(player)

        sink.sendStats(player)

        assertThat(sink.frames).containsExactly(
            bytes(
                0xA0,
                0x64, 0x00, 0x00, 0x00,
                0x96, 0x00, 0x00, 0x00,
                0x90, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x64, 0x00,
                0x32, 0x00, 0x00, 0x00,
                0x32, 0x00, 0x00, 0x00,
                0x00,
                0x00, 0x00,
                0xDC, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            ),
        )
    }

    @Test
    fun `sendSkills writes 0xA1 with the seven current-profile skill slots`() {
        val player = observer()
        val sink = InMemoryProtocolSink(player)

        sink.sendSkills(player)

        // level 10, base 10, loyalty 10 (no account loyalty bonus) and 0 percent.
        val skillSlot = bytes(0x0A, 0x00, 0x0A, 0x00, 0x0A, 0x00, 0x00, 0x00)
        assertThat(sink.frames).containsExactly(
            bytes(0xA1, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) +
                skillSlot + skillSlot + skillSlot + skillSlot + skillSlot + skillSlot + skillSlot +
                bytes(
                    0x00,
                    0x90, 0x01, 0x00, 0x00,
                    0x90, 0x01, 0x00, 0x00,
                    0x00, 0x00,
                    0x04, 0x00, 0x00,
                ) +
                zeroDouble +
                bytes(0x00) +
                zeroDouble + zeroDouble + zeroDouble + zeroDouble + zeroDouble +
                bytes(
                    0x00, 0x00,
                    0x00, 0x00,
                    0x00, 0x00,
                ) +
                zeroDouble + zeroDouble +
                bytes(0x00, 0x00, 0x00) +
                zeroDouble + zeroDouble + zeroDouble,
        )
    }
}
