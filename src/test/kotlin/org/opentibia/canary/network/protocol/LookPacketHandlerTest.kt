package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.readTibiaString

class LookPacketHandlerTest {

    @Test
    fun `formats item inspection description text`() {
        val swordType = ItemType(id = 2400, name = "Magic Sword", weight = 42.0)
        val sword = Item(id = 2400, count = 1, type = swordType)

        val desc = DescriptionFormatter.formatItem(sword)
        assertThat(desc).contains("You see a Magic Sword")
        assertThat(desc).contains("It weighs 42.00 oz.")
    }

    @Test
    fun `formats monster inspection description text`() {
        val monster = Monster(1001, "Dragon", 1000, 1000, position = Position(100, 100, 7))
        val desc = DescriptionFormatter.formatCreature(monster)
        assertThat(desc).contains("You see a Dragon.")
        assertThat(desc).contains("Health: 100%")
    }

    @Test
    fun `parses look packet 0x8C and builds text message packet`() {
        val map = GameMap()
        val pos = Position(100, 100, 7)
        val tile = map.getOrCreateTile(pos)
        val item = Item(id = 2160, count = 10, type = ItemType(id = 2160, name = "Crystal Coin", weight = 0.1, isStackable = true))
        tile.addItem(item)

        val player = Player(1, "Hero", 100, 100, position = pos)

        val payload = Unpooled.buffer()
        payload.writeShortLE(pos.x)
        payload.writeShortLE(pos.y)
        payload.writeByte(pos.z)
        payload.writeShortLE(2160)
        payload.writeByte(0) // stackpos

        val textResponse = LookPacketHandler.handleLook(player, payload, map)
        assertThat(textResponse).isNotNull

        val opcode = textResponse!!.readUnsignedByte().toInt()
        assertThat(opcode).isEqualTo(0xB4) // MESSAGE_TEXT opcode

        val messageClass = textResponse.readUnsignedByte().toInt()
        assertThat(messageClass).isEqualTo(0x13) // MESSAGE_INFO_DESCR

        val messageText = textResponse.readTibiaString()
        assertThat(messageText).contains("You see 10 Crystal Coins")
    }
}
