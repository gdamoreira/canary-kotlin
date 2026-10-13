package org.opentibia.canary.network.protocol.imbuement

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.protocol.ImbuementPacketHandler

class ImbuementWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private val fireInfo = ImbuementWindowWriter.ImbuementInfoEntry(
        id = 1L,
        baseName = "Basic",
        name = "Fire",
        description = "Fire damage",
        baseId = 1,
        iconId = 1,
        duration = 7200L,
        items = listOf(
            ImbuementWindowWriter.ImbuementCostItem(id = 1, name = "Earth", amount = 25),
            ImbuementWindowWriter.ImbuementCostItem(id = 2, name = "Ruby", amount = 5),
        ),
        price = 7500L,
    )

    private val fireInfoBytes = bytes(
        0x01, 0x00, 0x00, 0x00,
        0x0A, 0x00, 0x42, 0x61, 0x73, 0x69, 0x63, 0x20, 0x46, 0x69, 0x72, 0x65,
        0x0B, 0x00, 0x46, 0x69, 0x72, 0x65, 0x20, 0x64, 0x61, 0x6D, 0x61, 0x67, 0x65,
        0x00,
        0x01, 0x00,
        0x20, 0x1C, 0x00, 0x00,
        0x02,
        0x01, 0x00, 0x05, 0x00, 0x45, 0x61, 0x72, 0x74, 0x68, 0x19, 0x00,
        0x02, 0x00, 0x04, 0x00, 0x52, 0x75, 0x62, 0x79, 0x05, 0x00,
        0x4C, 0x1D, 0x00, 0x00,
    )

    @Test
    fun `writes the open imbuement window 0xEB with the empty item id`() {
        val out = Unpooled.buffer()

        ImbuementWindowWriter.writeImbuementWindow(
            out,
            ImbuementWindowWriter.ImbuementWindowFrame(
                action = ImbuementPacketHandler.IMBUEMENT_ACTION_OPEN,
                hasEmptyScrolls = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEB,
                0x00,
                0x01,
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the picked item imbuement window 0xEB with applied and available imbuements`() {
        val out = Unpooled.buffer()

        ImbuementWindowWriter.writeImbuementWindow(
            out,
            ImbuementWindowWriter.ImbuementWindowFrame(
                action = ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM,
                hasEmptyScrolls = false,
                itemId = 2160,
                itemTier = 3,
                itemSlots = 2,
                applied = listOf(
                    ImbuementWindowWriter.ImbuementAppliedEntry(
                        info = fireInfo,
                        duration = 7200L,
                        removeCost = 500L,
                    ),
                ),
                available = listOf(fireInfo),
                neededItems = linkedMapOf(1 to 30, 2 to 5),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *(
                bytes(
                    0xEB,
                    0x01,
                    0x00,
                    0x70, 0x08,
                    0x03,
                    0x02,
                    0x01,
                ) +
                    fireInfoBytes +
                    bytes(
                        0x20, 0x1C, 0x00, 0x00,
                        0xF4, 0x01, 0x00, 0x00,
                        0x00,
                        0x01, 0x00,
                    ) +
                    fireInfoBytes +
                    bytes(
                        0x02, 0x00, 0x00, 0x00,
                        0x01, 0x00, 0x1E, 0x00,
                        0x02, 0x00, 0x05, 0x00,
                    )
                )
        )
    }

    @Test
    fun `writes the picked item imbuement window 0xEB without the tier byte when unclassified`() {
        val out = Unpooled.buffer()

        ImbuementWindowWriter.writeImbuementWindow(
            out,
            ImbuementWindowWriter.ImbuementWindowFrame(
                action = ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM,
                hasEmptyScrolls = true,
                itemId = 2160,
                itemTier = null,
                itemSlots = 1,
                applied = listOf(null),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEB,
                0x01,
                0x01,
                0x70, 0x08,
                0x01,
                0x00,
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the scroll imbuement window 0xEB with the empty scroll appended to every offer`() {
        val out = Unpooled.buffer()
        val iceInfo = ImbuementWindowWriter.ImbuementInfoEntry(
            id = 2L,
            baseName = "Basic",
            name = "Ice",
            description = "",
            baseId = 1,
            iconId = 2,
            duration = 3600L,
            items = emptyList(),
            price = 100L,
        )

        ImbuementWindowWriter.writeImbuementWindow(
            out,
            ImbuementWindowWriter.ImbuementWindowFrame(
                action = ImbuementPacketHandler.IMBUEMENT_ACTION_SCROLL,
                hasEmptyScrolls = true,
                hasFreeBackpackSlots = true,
                available = listOf(iceInfo),
                scrollAction = true,
                emptyScrollItemName = "Empty Imbuement Scroll",
                neededItems = linkedMapOf(ImbuementWindowWriter.ITEM_EMPTY_IMBUEMENT_SCROLL to 1),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEB,
                0x02,
                0x01,
                0x01,
                0x00,
                0x01, 0x00,
                0x02, 0x00, 0x00, 0x00,
                0x09, 0x00, 0x42, 0x61, 0x73, 0x69, 0x63, 0x20, 0x49, 0x63, 0x65,
                0x00, 0x00,
                0x00,
                0x02, 0x00,
                0x10, 0x0E, 0x00, 0x00,
                0x01,
                0xF2, 0xC8,
                0x16, 0x00, 0x45, 0x6D, 0x70, 0x74, 0x79, 0x20, 0x49, 0x6D, 0x62, 0x75,
                0x65, 0x6D, 0x65, 0x6E, 0x74, 0x20, 0x53, 0x63, 0x72, 0x6F, 0x6C, 0x6C,
                0x01, 0x00,
                0x64, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00,
                0xF2, 0xC8, 0x01, 0x00,
            )
        )
    }
}
