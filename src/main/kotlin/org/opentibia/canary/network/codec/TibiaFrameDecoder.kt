package org.opentibia.canary.network.codec

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

private val logger = KotlinLogging.logger {}

/**
 * Splits the TCP byte stream into individual Tibia packets.
 * Tibia packets are framed with a 2-byte Little-Endian unsigned length prefix.
 */
class TibiaFrameDecoder : ByteToMessageDecoder() {
    companion object {
        const val MAX_PACKET_SIZE = 65535
    }

    private var loggedFirstBytes = false

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (!loggedFirstBytes && input.readableBytes() > 0) {
            loggedFirstBytes = true
            val preview = ByteBufUtil.hexDump(input, input.readerIndex(), minOf(16, input.readableBytes()))
            logger.debug { "First game packet bytes from ${ctx.channel().remoteAddress()}: $preview" }
        }

        if (input.readableBytes() < 2) {
            return
        }

        input.markReaderIndex()
        val packetLength = input.readUnsignedShortLE()

        if (packetLength == 0 || packetLength > MAX_PACKET_SIZE) {
            ctx.close()
            return
        }

        if (input.readableBytes() < packetLength) {
            input.resetReaderIndex()
            return
        }

        // Retain and emit the packet payload without the 2-byte outer length
        val frame = input.readRetainedSlice(packetLength)
        out.add(frame)
    }
}
