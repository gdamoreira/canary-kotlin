package org.opentibia.canary.network.protocol.container

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item

class ContainerItemWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes add container item 0x70 with the cid slot and item`() {
        val out = Unpooled.buffer()

        ContainerItemWriter.writeAddContainerItem(out, cid = 3, slot = 5, item = Item(1000))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x70, 0x03, 0x05, 0x00, 0xE8, 0x03)
        )
    }

    @Test
    fun `writes update container item 0x71 with the cid slot and item`() {
        val out = Unpooled.buffer()

        ContainerItemWriter.writeUpdateContainerItem(out, cid = 7, slot = 0x1234, item = Item(2160))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x71, 0x07, 0x34, 0x12, 0x70, 0x08)
        )
    }

    @Test
    fun `writes remove container item 0x72 with the last item`() {
        val out = Unpooled.buffer()

        ContainerItemWriter.writeRemoveContainerItem(out, cid = 1, slot = 2, lastItem = Item(3031))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x72, 0x01, 0x02, 0x00, 0xD7, 0x0B)
        )
    }

    @Test
    fun `writes remove container item 0x72 with a zero item id when the last item is absent`() {
        val out = Unpooled.buffer()

        ContainerItemWriter.writeRemoveContainerItem(out, cid = 1, slot = 2, lastItem = null)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x72, 0x01, 0x02, 0x00, 0x00, 0x00)
        )
    }
}
