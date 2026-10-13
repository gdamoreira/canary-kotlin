package org.opentibia.canary.network.protocol.vip

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.players.vip.VipStatus

class VipWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes updated vip status 0xD3 with guid and status byte`() {
        val out = Unpooled.buffer()

        VipWindowWriter.writeUpdatedVipStatus(out, guid = 1000L, newStatus = VipStatus.Online)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD3,
                0xE8, 0x03, 0x00, 0x00,
                0x01,
            )
        )
    }

    @Test
    fun `suppresses the updated vip status for a training vip on the old protocol`() {
        val out = Unpooled.buffer()

        VipWindowWriter.writeUpdatedVipStatus(
            out,
            guid = 1L,
            newStatus = VipStatus.Training,
            oldProtocol = true,
        )

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes the pre-980 old protocol offline variant without the status byte`() {
        val out = Unpooled.buffer()

        VipWindowWriter.writeUpdatedVipStatus(
            out,
            guid = 2L,
            newStatus = VipStatus.Offline,
            oldProtocol = true,
            version = 970,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD4,
                0x02, 0x00, 0x00, 0x00,
            )
        )
    }
}
