package org.opentibia.canary.game

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.SLOTP_RING
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.protocol.InMemoryProtocolSink

/**
 * `Game::playerInspectItem` (src/game/game.cpp:1635, 1652) through the default
 * [Game.sendItemInspection] seam: the ported
 * `ProtocolGame::sendItemInspection` window (protocolgame.cpp:2943-2973)
 * consumes the plural `Item::getDescriptions` (src/items/item.cpp:1658-2298)
 * and hands the frame to the player's output buffer.
 */
class ItemInspectionWindowTest {

    @Test
    fun `the default inspection window lists the plural item descriptions`() {
        val items = Items().apply {
            register(
                ItemType(
                    id = 2160, name = "gold coin", isStackable = true, pluralName = "gold coins", weight = 0.1,
                ),
            )
        }
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val player = Player(id = 1, name = "Hero", maxHealth = 100, health = 100, position = center)
        val sink = InMemoryProtocolSink(player)
        player.protocolSink = sink

        game.playerInspectItem(player, 2160, 10, false)

        val frame = Unpooled.wrappedBuffer(sink.frames.single())
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x76)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x00)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x00)
        assertThat(frame.readIntLE()).isEqualTo(1)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x01)
        assertThat(frame.readTibiaString()).isEqualTo("gold coin")
        assertThat(frame.readUnsignedShortLE()).isEqualTo(2160)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(10)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x00)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(3)
        assertThat(frame.readTibiaString()).isEqualTo("Weight")
        assertThat(frame.readTibiaString()).isEqualTo("0.10 oz")
        assertThat(frame.readTibiaString()).isEqualTo("Tradeable In Market")
        assertThat(frame.readTibiaString()).isEqualTo("yes")
        assertThat(frame.readTibiaString()).isEqualTo("Body Position")
        assertThat(frame.readTibiaString()).isEqualTo("weapon hand")
        assertThat(frame.isReadable).isFalse()
    }

    @Test
    fun `the default inspection window flags a cyclopedia inspection`() {
        val items = Items().apply {
            register(ItemType(id = 200, name = "ring", slotPosition = SLOTP_RING, description = "A plain ring."))
        }
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val player = Player(id = 7, name = "Hero", maxHealth = 100, health = 100, position = center)
        val sink = InMemoryProtocolSink(player)
        player.protocolSink = sink

        game.playerInspectItem(player, 200, 0, true)

        val frame = Unpooled.wrappedBuffer(sink.frames.single())
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x76)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x00)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x01)
        assertThat(frame.readIntLE()).isEqualTo(7)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x01)
        assertThat(frame.readTibiaString()).isEqualTo("ring")
        assertThat(frame.readUnsignedShortLE()).isEqualTo(200)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x00)
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(3)
        assertThat(frame.readTibiaString()).isEqualTo("Description")
        assertThat(frame.readTibiaString()).isEqualTo("A plain ring.")
        assertThat(frame.readTibiaString()).isEqualTo("Tradeable In Market")
        assertThat(frame.readTibiaString()).isEqualTo("yes")
        assertThat(frame.readTibiaString()).isEqualTo("Body Position")
        assertThat(frame.readTibiaString()).isEqualTo("finger")
        assertThat(frame.isReadable).isFalse()
    }

    private companion object {
        val center = Position(1000, 1000, 7)
    }
}
