package org.opentibia.canary.network.protocol.creature

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.GameWindowWriter

/**
 * Outbound creature refresh serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendCreatureEmblem` (0x6C + 0x6A, protocolgame.cpp:4665-4685)
 *  - `ProtocolGame::sendUpdateCreature` (0x6B, protocolgame.cpp:11061-11086)
 *
 * Both senders write the shared `AddCreature` body
 * ([GameWindowWriter.writeCreature]) for an unknown creature: the emblem
 * refresh removes the tile thing first (suppressed at stack positions >= 10 by
 * `RemoveTileThing`, while the re-add still happens) and re-adds it with
 * `removedKnown = creature->getID()`; the update sender writes the tile
 * position and `removedKnown = 0`. The `canSee`/tile/`getClientIndexOfCreature`
 * gates and the stack-position lookup stay with the caller. Every multi-byte
 * field is Little-Endian.
 */
object CreatureUpdateWriter {

    const val PACKET_ADD_CREATURE: Int = 0x6A
    const val PACKET_UPDATE_CREATURE: Int = 0x6B

    /** `sendCreatureEmblem` (protocolgame.cpp:4665-4685). */
    fun writeCreatureEmblem(out: ByteBuf, pos: Position, stackpos: Int, appearance: CreatureAppearanceFrame) {
        GameWindowWriter.writeRemoveTileThing(out, pos, stackpos)

        out.writeByte(PACKET_ADD_CREATURE)
        out.writePosition(pos)
        out.writeByte(stackpos)
        GameWindowWriter.writeCreature(out, appearance)
    }

    /** `sendUpdateCreature` (protocolgame.cpp:11061-11086). */
    fun writeUpdateCreature(out: ByteBuf, pos: Position, stackpos: Int, appearance: CreatureAppearanceFrame) {
        out.writeByte(PACKET_UPDATE_CREATURE)
        out.writePosition(pos)
        out.writeByte(stackpos)
        GameWindowWriter.writeCreature(out, appearance)
    }

    private fun ByteBuf.writePosition(pos: Position) {
        writeShortLE(pos.x)
        writeShortLE(pos.y)
        writeByte(pos.z)
    }
}
