package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Outbound supply stash serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendOpenStash` (0x29, protocolgame.cpp:10858-10872)
 *
 * A `StashItemList` is a `std::map<uint16_t, uint32_t>` (`protocolgame.hpp:84`),
 * so Canary emits entries in ascending item-id order; this serializer preserves
 * the caller-provided insertion order. Canary has no separate
 * `sendUpdateStash`; stash mutations re-open the window through `sendOpenStash`.
 * Every multi-byte field is Little-Endian; the `!player`/`oldProtocol` guard
 * stays with the caller.
 */
object StashWindowWriter {

    const val PACKET_OPEN_STASH: Int = 0x29

    /** `sendOpenStash` (protocolgame.cpp:10863-10871). */
    fun writeOpenStash(out: ByteBuf, items: Map<Int, Int>) {
        out.writeByte(PACKET_OPEN_STASH)
        out.writeShortLE(items.size)
        for ((itemId, count) in items) {
            out.writeShortLE(itemId)
            out.writeIntLE(count)
        }
    }
}
