package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.GameMap

/**
 * Test double for [ProtocolSink] that records every emitted frame as a byte
 * array instead of writing it to a channel. Used by the sink unit tests and by
 * the state-change wiring tests (AGENTS.md 4: no live sockets).
 */
class InMemoryProtocolSink(
    player: Player,
    map: GameMap = GameMap(),
) : ProtocolFrameSink(player, map) {

    /** Every frame emitted in call order, copied out of its `ByteBuf`. */
    val frames = mutableListOf<ByteArray>()

    override fun frameAllocator(): ByteBufAllocator = UnpooledByteBufAllocator.DEFAULT

    override fun writeFrame(frame: ByteBuf) {
        val bytes = ByteArray(frame.readableBytes())
        frame.readBytes(bytes)
        frame.release()
        frames.add(bytes)
    }
}
