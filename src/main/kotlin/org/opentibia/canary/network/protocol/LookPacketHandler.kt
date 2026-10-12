package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import java.util.Locale

object DescriptionFormatter {

    fun formatItem(item: Item): String {
        val itemName = item.type?.name ?: "an item"
        val weightStr = String.format(Locale.US, "%.2f", item.weight)
        return if (item.count > 1) {
            "You see ${item.count} ${itemName}s. It weighs $weightStr oz."
        } else {
            "You see a $itemName. It weighs $weightStr oz."
        }
    }

    fun formatCreature(creature: Creature): String {
        val healthPercent = if (creature.maxHealth > 0) (creature.health * 100) / creature.maxHealth else 0
        return "You see a ${creature.name}. Health: $healthPercent%"
    }
}

object LookPacketHandler {

    const val PACKET_MESSAGE_TEXT: Byte = 0xB4.toByte()
    const val MESSAGE_INFO_DESCR: Byte = 0x13

    fun handleLook(player: Player, payload: ByteBuf, map: GameMap): ByteBuf? {
        if (payload.readableBytes() < 8) return null

        val x = payload.readUnsignedShortLE()
        val y = payload.readUnsignedShortLE()
        val z = payload.readUnsignedByte().toInt()
        val itemId = payload.readUnsignedShortLE()
        val stackpos = payload.readUnsignedByte().toInt()

        val tile = map.getTile(Position(x, y, z)) ?: return null
        val item = tile.items.find { it.id == itemId } ?: (if (tile.ground?.id == itemId) tile.ground else null) ?: Item(itemId)

        val descriptionText = DescriptionFormatter.formatItem(item)

        val out = Unpooled.buffer()
        out.writeByte(PACKET_MESSAGE_TEXT.toInt())
        out.writeByte(MESSAGE_INFO_DESCR.toInt())
        out.writeTibiaString(descriptionText)
        return out
    }
}
