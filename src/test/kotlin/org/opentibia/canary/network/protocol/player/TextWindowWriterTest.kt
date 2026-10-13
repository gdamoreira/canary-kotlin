package org.opentibia.canary.network.protocol.player

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType

class TextWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes text window 0x96 with the writable item and max length`() {
        val out = Unpooled.buffer()
        val item = Item(id = 1949, type = ItemType(id = 1949, name = "book", canWriteText = true))
        item.setAttribute(ItemAttribute.TEXT, "hi")
        item.setAttribute(ItemAttribute.WRITER, "Ann")

        TextWindowWriter.writeTextWindow(out, windowTextId = 7L, item = item, maxlen = 100, canWrite = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x96,
                0x07, 0x00, 0x00, 0x00,
                0x9D, 0x07,
                0x64, 0x00,
                0x02, 0x00, 0x68, 0x69,
                0x03, 0x00, 0x41, 0x6E, 0x6E,
                0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes text window 0x96 with the read-only double length and empty writer and date`() {
        val out = Unpooled.buffer()
        val item = Item(id = 1949, type = ItemType(id = 1949, name = "book"))
        item.setAttribute(ItemAttribute.TEXT, "hello")

        TextWindowWriter.writeTextWindow(out, windowTextId = 7L, item = item, maxlen = 0, canWrite = false)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x96,
                0x07, 0x00, 0x00, 0x00,
                0x9D, 0x07,
                0x05, 0x00,
                0x05, 0x00, 0x68, 0x65, 0x6C, 0x6C, 0x6F,
                0x00, 0x00,
                0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes text window 0x96 with the formatted written date`() {
        val out = Unpooled.buffer()
        val item = Item(id = 1949, type = ItemType(id = 1949, name = "book"))
        item.setAttribute(ItemAttribute.TEXT, "x")
        item.setAttribute(ItemAttribute.DATE, 1_700_000_000L)

        TextWindowWriter.writeTextWindow(out, windowTextId = 7L, item = item, maxlen = 0, canWrite = false)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x96,
                0x07, 0x00, 0x00, 0x00,
                0x9D, 0x07,
                0x01, 0x00,
                0x01, 0x00, 0x78,
                0x00, 0x00,
                0x00,
                0x13, 0x00,
                0x32, 0x30, 0x32, 0x33, 0x2D, 0x31, 0x31, 0x2D, 0x31, 0x34,
                0x20, 0x32, 0x32, 0x3A, 0x31, 0x33, 0x3A, 0x32, 0x30,
            )
        )
    }

    @Test
    fun `writes text window 0x96 for a static container item`() {
        val out = Unpooled.buffer()

        TextWindowWriter.writeTextWindow(
            out,
            windowTextId = 7L,
            itemType = ItemType(id = 1987, name = "bag", isContainer = true),
            text = "ab",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x96,
                0x07, 0x00, 0x00, 0x00,
                0xC3, 0x07,
                0x00,
                0x02, 0x00,
                0x02, 0x00, 0x61, 0x62,
                0x00, 0x00,
                0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes text window 0x96 for a classified item with the tier byte`() {
        val out = Unpooled.buffer()

        TextWindowWriter.writeTextWindow(
            out,
            windowTextId = 1L,
            itemType = ItemType(id = 1000, name = "tiered", upgradeClassification = 4),
            text = "a",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x96,
                0x01, 0x00, 0x00, 0x00,
                0xE8, 0x03,
                0x00,
                0x01, 0x00,
                0x01, 0x00, 0x61,
                0x00, 0x00,
                0x00,
                0x00, 0x00,
            )
        )
    }
}
