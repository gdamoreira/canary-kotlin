package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item

class InspectionWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes an item inspection 0x76 from an item id and description pairs`() {
        val out = Unpooled.buffer()

        InspectionWindowWriter.writeItemInspection(
            out,
            playerId = 5L,
            cyclopedia = false,
            itemId = 100,
            itemCount = 1,
            itemName = "Sword",
            item = null,
            descriptions = listOf("Attack" to "15"),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x76, 0x00, 0x00,
                0x05, 0x00, 0x00, 0x00,
                0x01,
                0x05, 0x00, 0x53, 0x77, 0x6F, 0x72, 0x64,
                0x64, 0x00,
                0x00,
                0x01,
                0x06, 0x00, 0x41, 0x74, 0x74, 0x61, 0x63, 0x6B,
                0x02, 0x00, 0x31, 0x35,
            )
        )
    }

    @Test
    fun `writes the cyclopedia item inspection from an item instance`() {
        val out = Unpooled.buffer()

        InspectionWindowWriter.writeItemInspection(
            out,
            playerId = 1L,
            cyclopedia = true,
            itemId = 200,
            itemCount = 0,
            itemName = "Ring",
            item = Item(200),
            descriptions = emptyList(),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x76, 0x00, 0x01,
                0x01, 0x00, 0x00, 0x00,
                0x01,
                0x04, 0x00, 0x52, 0x69, 0x6E, 0x67,
                0xC8, 0x00,
                0x00,
                0x00,
            )
        )
    }
}
