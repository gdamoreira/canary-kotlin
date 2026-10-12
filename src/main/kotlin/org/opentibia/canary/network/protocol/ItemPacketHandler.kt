package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.scripting.ScriptRegistry

object ItemPacketHandler {

    fun handleUse(player: Player, payload: ByteBuf, map: GameMap, scripts: ScriptRegistry): Boolean {
        if (payload.readableBytes() < 9) return false

        val x = payload.readUnsignedShortLE()
        val y = payload.readUnsignedShortLE()
        val z = payload.readUnsignedByte().toInt()
        val itemId = payload.readUnsignedShortLE()
        val stackpos = payload.readUnsignedByte().toInt()
        val index = payload.readUnsignedByte().toInt()

        val itemPos = Position(x, y, z)
        val tile = map.getTile(itemPos) ?: return false
        val item = tile.items.find { it.id == itemId } ?: (if (tile.ground?.id == itemId) tile.ground else null) ?: Item(itemId)

        return scripts.executeAction(player, item, itemPos)
    }

    fun handleThrow(player: Player, payload: ByteBuf, map: GameMap): Boolean {
        if (payload.readableBytes() < 14) return false

        val fromX = payload.readUnsignedShortLE()
        val fromY = payload.readUnsignedShortLE()
        val fromZ = payload.readUnsignedByte().toInt()
        val itemId = payload.readUnsignedShortLE()
        val fromStackpos = payload.readUnsignedByte().toInt()

        val toX = payload.readUnsignedShortLE()
        val toY = payload.readUnsignedShortLE()
        val toZ = payload.readUnsignedByte().toInt()
        val count = payload.readUnsignedByte().toInt()

        val fromPos = Position(fromX, fromY, fromZ)
        val toPos = Position(toX, toY, toZ)

        val fromTile = map.getTile(fromPos) ?: return false
        val toTile = map.getTile(toPos) ?: return false

        val itemToMove = fromTile.items.find { it.id == itemId } ?: return false

        fromTile.removeItem(itemToMove)
        toTile.addItem(itemToMove)
        return true
    }
}
