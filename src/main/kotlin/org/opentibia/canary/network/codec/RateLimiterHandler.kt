package org.opentibia.canary.network.codec

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

private val logger = KotlinLogging.logger {}

class RateLimiterHandler(
    private val maxPacketsPerSecond: Int = 100
) : ChannelInboundHandlerAdapter() {

    private var currentSecond: Long = System.currentTimeMillis() / 1000
    private var packetCount: Int = 0

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val now = System.currentTimeMillis() / 1000
        if (now != currentSecond) {
            currentSecond = now
            packetCount = 0
        }

        packetCount++
        if (packetCount > maxPacketsPerSecond) {
            logger.warn { "Rate limit exceeded (${packetCount}/s > ${maxPacketsPerSecond}/s). Disconnecting ${ctx.channel().remoteAddress()}." }
            ctx.close()
            return
        }

        ctx.fireChannelRead(msg)
    }
}
