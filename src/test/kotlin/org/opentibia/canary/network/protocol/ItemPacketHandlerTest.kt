package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.scripting.ScriptRegistry

class ItemPacketHandlerTest {

    @Test
    fun `parses use item packet 0x82 and invokes registered action`() {
        val map = GameMap()
        val pos = Position(100, 100, 7)
        val tile = map.getOrCreateTile(pos)
        val lever = Item(id = 1945) // Lever
        tile.addItem(lever)

        val player = Player(1, "Hero", 100, 100, position = pos)
        val scripts = ScriptRegistry()

        var leverPulled = false
        scripts.registerAction(itemId = 1945) { _, item, _ ->
            leverPulled = true
            true
        }

        val payload = Unpooled.buffer()
        payload.writeShortLE(pos.x)
        payload.writeShortLE(pos.y)
        payload.writeByte(pos.z)
        payload.writeShortLE(1945) // Item ID
        payload.writeByte(0) // Stackpos
        payload.writeByte(0) // Index

        val handled = ItemPacketHandler.handleUse(player, payload, map, scripts)
        assertThat(handled).isTrue()
        assertThat(leverPulled).isTrue()
    }

    @Test
    fun `parses throw item packet 0x78 and moves ground item to destination tile`() {
        val map = GameMap()
        val fromPos = Position(100, 100, 7)
        val toPos = Position(101, 100, 7)

        val fromTile = map.getOrCreateTile(fromPos)
        val toTile = map.getOrCreateTile(toPos)

        val goldCoin = Item(id = 2148, count = 50)
        fromTile.addItem(goldCoin)

        val player = Player(1, "Hero", 100, 100, position = fromPos)

        val payload = Unpooled.buffer()
        // From Position
        payload.writeShortLE(fromPos.x)
        payload.writeShortLE(fromPos.y)
        payload.writeByte(fromPos.z)
        payload.writeShortLE(2148) // Item ID
        payload.writeByte(0) // Stackpos
        // To Position
        payload.writeShortLE(toPos.x)
        payload.writeShortLE(toPos.y)
        payload.writeByte(toPos.z)
        payload.writeByte(50) // Count

        val handled = ItemPacketHandler.handleThrow(player, payload, map)
        assertThat(handled).isTrue()
        assertThat(fromTile.items).isEmpty()
        assertThat(toTile.items).hasSize(1)
        assertThat(toTile.items.first().id).isEqualTo(2148)
    }
}
