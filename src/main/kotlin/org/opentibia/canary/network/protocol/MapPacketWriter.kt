package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Legacy login-path map description stub. It only serializes one floor and
 * omits creatures, the multi-floor z-stack and the per-tile stack-position
 * rules. The faithful `ProtocolGame::sendMapDescription` /
 * `GetMapDescription` / `GetFloorDescription` / `GetTileDescription` port lives
 * in [org.opentibia.canary.network.protocol.map.MapDescriptionWriter]; this
 * object stays only until the login call site can supply the session's
 * creature-appearance resolver (`ProtocolGame::checkCreatureAsKnown`).
 */
object MapPacketWriter {

    const val PACKET_MAP_DESCRIPTION: Byte = 0x64

    /**
     * Viewport dimensions in OpenTibia:
     * 18 tiles wide (8 West + center + 9 East), 14 tiles tall (6 North + center + 7 South)
     */
    fun writeMapDescription(out: ByteBuf, map: GameMap, center: Position) {
        out.writeByte(PACKET_MAP_DESCRIPTION.toInt())
        out.writeShortLE(center.x)
        out.writeShortLE(center.y)
        out.writeByte(center.z)

        val startX = center.x - 8
        val endX = center.x + 9
        val startY = center.y - 6
        val endY = center.y + 7

        var skipCount = -1

        for (y in startY..endY) {
            for (x in startX..endX) {
                val tile = map.getTile(Position(x, y, center.z))
                if (tile != null && tile.ground != null) {
                    if (skipCount >= 0) {
                        out.writeByte(skipCount)
                        out.writeByte(0xFF) // Tile separator
                        skipCount = -1
                    }
                    // Write ground item ID
                    out.writeShortLE(tile.ground!!.id)

                    // Write stacked items
                    for (item in tile.items) {
                        out.writeShortLE(item.id)
                    }
                } else {
                    skipCount++
                    if (skipCount == 255) {
                        out.writeByte(skipCount)
                        out.writeByte(0xFF)
                        skipCount = -1
                    }
                }
            }
        }

        if (skipCount >= 0) {
            out.writeByte(skipCount)
            out.writeByte(0xFF)
        }
    }
}
