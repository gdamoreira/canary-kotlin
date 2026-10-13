package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * One requested object of the `0xF3` store object-info request, mirroring the
 * `ObjectInfoEntry` struct of `ProtocolGame::parseGetObjectInfo`
 * (protocolgame.cpp:4342-4366).
 */
data class ObjectInfoEntry(
    val id: Int,
    val data: Int,
)

/**
 * Object info request (client opcode `0xF3`), mirroring
 * `ProtocolGame::parseGetObjectInfo` (protocolgame.cpp:4342-4366): a count byte
 * followed by that many `uint16_t` id + data byte entries.
 *
 * Canary stops reading entries as soon as fewer than 3 bytes remain
 * (`msg.canRead(3)`), so a truncated trailing entry yields a partial list
 * rather than a decode failure. The `0xF4` response sender is not ported
 * (PARITY GAP).
 */
data class ObjectInfoCommand(
    val objects: List<ObjectInfoEntry>,
)

/**
 * Decoder for the store object-info client opcode.
 *
 *  - `0xF3` `ProtocolGame::parseGetObjectInfo` (protocolgame.cpp:4342)
 */
object ObjectInfoPacketHandler {

    const val OPCODE_GET_OBJECT_INFO: Int = 0xF3

    /** `msg.canRead(3)` entry guard (protocolgame.cpp:4350). */
    private const val ENTRY_SIZE: Int = 3

    fun parse(opcode: Int, buffer: ByteBuf): ObjectInfoCommand? {
        if (opcode != OPCODE_GET_OBJECT_INFO) {
            return null
        }
        if (buffer.readableBytes() < 1) {
            return null
        }

        val count = buffer.readUnsignedByte().toInt()
        val objects = ArrayList<ObjectInfoEntry>(count)
        for (index in 0 until count) {
            if (buffer.readableBytes() < ENTRY_SIZE) {
                break
            }
            val id = buffer.readUnsignedShortLE()
            val data = buffer.readUnsignedByte().toInt()
            objects.add(ObjectInfoEntry(id = id, data = data))
        }
        return ObjectInfoCommand(objects = objects)
    }
}
