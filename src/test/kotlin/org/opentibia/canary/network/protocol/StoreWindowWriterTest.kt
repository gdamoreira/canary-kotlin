package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StoreWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes a u64 resource balance 0xEE for money`() {
        val out = Unpooled.buffer()

        StoreWindowWriter.writeResourceBalance(out, StoreWindowWriter.Resource.INVENTORY_MONEY, 123L)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEE,
                0x01,
                0x7B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes a u32 resource balance 0xEE for soulseals on the current payload`() {
        val out = Unpooled.buffer()

        StoreWindowWriter.writeResourceBalance(out, StoreWindowWriter.Resource.SOULSEALS, 5L)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEE,
                0x57,
                0x05, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the seven resources balance frames in bank then money order`() {
        val out = Unpooled.buffer()

        StoreWindowWriter.writeResourcesBalance(
            out,
            money = 1L,
            bank = 2L,
            preyCards = 3L,
            taskHunting = 4L,
            forgeDust = 5L,
            forgeSliver = 6L,
            forgeCores = 7L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEE, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xEE, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xEE, 0x0A, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xEE, 0x32, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xEE, 0x46, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xEE, 0x47, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xEE, 0x48, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes a charm resource balance with a u32 value`() {
        val out = Unpooled.buffer()

        StoreWindowWriter.writeCharmResourceBalance(out, StoreWindowWriter.CharmResource.CHARM, 5L)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEE,
                0x1E,
                0x05, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the four charm resources balance frames`() {
        val out = Unpooled.buffer()

        StoreWindowWriter.writeCharmResourcesBalance(
            out,
            charm = 1L,
            minorCharm = 2L,
            maxCharm = 3L,
            maxMinorCharm = 4L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEE, 0x1E, 0x01, 0x00, 0x00, 0x00,
                0xEE, 0x1F, 0x02, 0x00, 0x00, 0x00,
                0xEE, 0x20, 0x03, 0x00, 0x00, 0x00,
                0xEE, 0x21, 0x04, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the coin balance update and balance frames`() {
        val out = Unpooled.buffer()

        StoreWindowWriter.writeCoinBalance(out, coinBalance = 100L, transferableBalance = 50L)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF2, 0x01,
                0xDF, 0x01,
                0x64, 0x00, 0x00, 0x00,
                0x32, 0x00, 0x00, 0x00,
                0x64, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the special containers available 0x2A with stash and market flags`() {
        val out = Unpooled.buffer()

        StoreWindowWriter.writeSpecialContainersAvailable(out, stashMenu = true, marketMenu = false)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x2A,
                0x01,
                0x00,
            )
        )
    }
}
