package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.protocol.MiscWindowWriter

/**
 * Outbound forge error composition, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendForgeError` (0xED/0x14 + 0x89, protocolgame.cpp:7526-7529)
 *
 * `sendForgeError` is `sendMessageDialog(getReturnMessage(returnValue))`
 * followed by `closeForgeWindow()`; the `ReturnValue` -> message resolution
 * stays with the caller and both frames reuse [MiscWindowWriter]. Every
 * multi-byte field is Little-Endian.
 */
object ForgeErrorWriter {

    /** `sendForgeError` (protocolgame.cpp:7526-7529). */
    fun writeForgeError(out: ByteBuf, message: String) {
        MiscWindowWriter.writeMessageDialog(out, message)
        MiscWindowWriter.writeCloseForgeWindow(out)
    }
}
