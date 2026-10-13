package org.opentibia.canary.network.protocol.map

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.network.protocol.ItemWireInfo

/**
 * Outbound single-tile frames for the Canary `ProtocolProfileId::Current`
 * profile (`protocol_new` / 15.x: `oldProtocol == false`), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendAddTileItem`        (0x6A, protocolgame.cpp:8485-8496)
 *  - `ProtocolGame::sendUpdateTileItem`     (0x6B, protocolgame.cpp:8498-8509)
 *  - `ProtocolGame::sendUpdateTileCreature` (0x6B, protocolgame.cpp:8523-8538)
 *  - `ProtocolGame::sendUpdateTile`         (0x69, protocolgame.cpp:8540-8559)
 *
 * `ProtocolGame::sendRemoveTileThing` (0x6C) already lives in
 * `GameWindowWriter.writeRemoveTileThing` and is not duplicated here. Every
 * `canSee(pos)` gate stays with the caller.
 */
object TilePacketWriter {

    const val PACKET_UPDATE_TILE: Int = 0x69
    const val PACKET_ADD_TILE_ITEM: Int = 0x6A
    const val PACKET_UPDATE_TILE_ITEM: Int = 0x6B

    /** `sendAddTileItem(pos, stackpos, item)` (protocolgame.cpp:8485-8496). */
    fun writeAddTileItem(
        out: ByteBuf,
        pos: Position,
        stackpos: Int,
        item: Item,
        info: ItemWireInfo = ItemWireInfo.from(item.type),
    ) {
        out.writeByte(PACKET_ADD_TILE_ITEM)
        out.writePosition(pos)
        out.writeByte(stackpos)
        GameWindowWriter.writeItem(out, item, info)
    }

    /** `sendUpdateTileItem(pos, stackpos, item)` (protocolgame.cpp:8498-8509). */
    fun writeUpdateTileItem(
        out: ByteBuf,
        pos: Position,
        stackpos: Int,
        item: Item,
        info: ItemWireInfo = ItemWireInfo.from(item.type),
    ) {
        out.writeByte(PACKET_UPDATE_TILE_ITEM)
        out.writePosition(pos)
        out.writeByte(stackpos)
        GameWindowWriter.writeItem(out, item, info)
    }

    /** `sendUpdateTileCreature(pos, stackpos, creature)` (protocolgame.cpp:8523-8538). */
    fun writeUpdateTileCreature(
        out: ByteBuf,
        pos: Position,
        stackpos: Int,
        appearance: CreatureAppearanceFrame,
    ) {
        out.writeByte(PACKET_UPDATE_TILE_ITEM)
        out.writePosition(pos)
        out.writeByte(stackpos)
        GameWindowWriter.writeCreature(out, appearance)
    }

    /** `sendUpdateTile(tile, pos)` (protocolgame.cpp:8540-8559). */
    fun writeUpdateTile(
        out: ByteBuf,
        player: Player,
        tile: Tile?,
        pos: Position,
        appearanceFor: (Creature) -> CreatureAppearanceFrame,
    ) {
        out.writeByte(PACKET_UPDATE_TILE)
        out.writePosition(pos)

        if (tile != null) {
            MapDescriptionWriter.writeTileDescription(out, player, tile, appearanceFor)
            out.writeByte(0x00)
            out.writeByte(0xFF)
        } else {
            out.writeByte(0x01)
            out.writeByte(0xFF)
        }
    }

    private fun ByteBuf.writePosition(pos: Position) {
        writeShortLE(pos.x)
        writeShortLE(pos.y)
        writeByte(pos.z)
    }
}
