package org.opentibia.canary.network.protocol.imbuement

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item

class InventoryImbuementsWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes inventory imbuements 0x5D with empty and filled slots`() {
        val out = Unpooled.buffer()

        InventoryImbuementsWriter.writeInventoryImbuements(
            out,
            listOf(
                InventoryImbuementsWriter.InventoryImbuementEntry(
                    slot = 1,
                    item = Item(100),
                    imbuements = listOf(
                        null,
                        InventoryImbuementsWriter.InventoryImbuementInfo(
                            baseName = "Cloud",
                            name = "Strike",
                            iconId = 11,
                            duration = 3600L,
                            active = true,
                        ),
                    ),
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x5D,
                0x01,
                0x01,
                0x64, 0x00,
                0x02,
                0x00,
                0x01,
                0x0C, 0x00, 0x43, 0x6C, 0x6F, 0x75, 0x64, 0x20, 0x53, 0x74, 0x72, 0x69, 0x6B, 0x65,
                0x0B, 0x00,
                0x10, 0x0E, 0x00, 0x00,
                0x01,
            )
        )
    }

    @Test
    fun `writes an item with no imbuement slots as only the slot and item`() {
        val out = Unpooled.buffer()

        InventoryImbuementsWriter.writeInventoryImbuements(
            out,
            listOf(
                InventoryImbuementsWriter.InventoryImbuementEntry(
                    slot = 4,
                    item = Item(200),
                    imbuements = emptyList(),
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x5D,
                0x01,
                0x04,
                0xC8, 0x00,
                0x00,
            )
        )
    }
}
