package org.opentibia.canary.network.codec

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimiterHandlerTest {

    @Test
    fun `allows packets within configured rate limit`() {
        val limiter = RateLimiterHandler(maxPacketsPerSecond = 5)
        val channel = EmbeddedChannel(limiter)

        for (i in 1..5) {
            val buf = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
            channel.writeInbound(buf)
        }

        assertThat(channel.isOpen).isTrue()
        assertThat(channel.inboundMessages()).hasSize(5)
    }

    @Test
    fun `closes channel when packet limit is exceeded`() {
        val limiter = RateLimiterHandler(maxPacketsPerSecond = 3)
        val channel = EmbeddedChannel(limiter)

        // Write 4 packets rapidly -> should exceed limit of 3
        for (i in 1..4) {
            channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1)))
        }

        assertThat(channel.isOpen).isFalse()
    }
}
