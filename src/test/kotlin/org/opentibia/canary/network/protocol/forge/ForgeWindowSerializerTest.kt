package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.forge.ForgeAction_t

class ForgeWindowSerializerTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `serializes an empty forge window with header fusion transfer and dust sections`() {
        val out = Unpooled.buffer()

        ForgeWindowSerializer.writeOpenForge(out, ForgeWindow(maxConfigTier = 10, dustLevel = 0))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x87, // PACKET_OPEN_FORGE
                0x00, 0x00, // fusion total count
                0x00, 0x00, // convergence fusion count
                0x00, // transfer total count
                0x00, // convergence transfer count
                0x00, 0x00, // dust level
            )
        )
    }

    @Test
    fun `serializes fusion items and only emits entries with count at least two`() {
        val out = Unpooled.buffer()
        val window = ForgeWindow(
            fusionItems = mapOf(
                1000 to mapOf(1 to 3, 2 to 1),
            ),
            maxConfigTier = 10,
            dustLevel = 0,
        )

        ForgeWindowSerializer.writeOpenForge(out, window)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x87,
                0x01, 0x00, // one fusable entry
                0x01, // friend marker
                0xE8, 0x03, // itemId 1000 LE
                0x01, // tier
                0x03, 0x00, // count 3 LE
                0x00, 0x00, // convergence fusion count
                0x00, // transfer total count
                0x00, // convergence transfer count
                0x00, 0x00, // dust level
            )
        )
    }

    @Test
    fun `serializes convergence fusion grouped per slot and ordered by slot then item`() {
        val out = Unpooled.buffer()
        val window = ForgeWindow(
            convergenceFusionItems = mapOf(
                2 to mapOf(2000 to mapOf(1 to 3)),
                3 to mapOf(3000 to mapOf(5 to 1)),
            ),
            maxConfigTier = 10,
            dustLevel = 0,
        )

        ForgeWindowSerializer.writeOpenForge(out, window)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x87,
                0x00, 0x00, // fusion total count
                0x02, 0x00, // two convergence fusion slots
                0x01, // slot 2 item count
                0xD0, 0x07, 0x01, 0x03, 0x00, // itemId 2000, tier 1, count 3
                0x01, // slot 3 item count
                0xB8, 0x0B, 0x05, 0x01, 0x00, // itemId 3000, tier 5, count 1
                0x00, // transfer total count
                0x00, // convergence transfer count
                0x00, 0x00, // dust level
            )
        )
    }

    @Test
    fun `skips convergence fusion entries at or above the configured max tier`() {
        val out = Unpooled.buffer()
        val window = ForgeWindow(
            convergenceFusionItems = mapOf(2 to mapOf(2000 to mapOf(10 to 3))),
            maxConfigTier = 10,
            dustLevel = 0,
        )

        ForgeWindowSerializer.writeOpenForge(out, window)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x87,
                0x00, 0x00,
                0x00, 0x00, // no eligible slot
                0x00,
                0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `serializes donor and receiver transfer grouped by matching upgrade classification`() {
        val out = Unpooled.buffer()
        val window = ForgeWindow(
            donorTierItems = mapOf(1000 to mapOf(2 to 4)),
            receiveTierItems = mapOf(
                1000 to mapOf(0 to 1),
                2000 to mapOf(0 to 2),
            ),
            maxConfigTier = 10,
            dustLevel = 0,
        )
        val classification = { itemId: Int -> if (itemId == 1000 || itemId == 2000) 5 else 0 }

        ForgeWindowSerializer.writeOpenForge(out, window, classification)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x87,
                0x00, 0x00, // fusion total
                0x00, 0x00, // convergence fusion count
                0x01, // one donor item
                0x01, 0x00, // donor tier count
                0xE8, 0x03, 0x02, 0x04, 0x00, // itemId 1000, tier 2, count 4
                0x02, 0x00, // two matching receivers
                0xE8, 0x03, 0x01, 0x00, // receiver 1000 count 1
                0xD0, 0x07, 0x02, 0x00, // receiver 2000 count 2
                0x00, // convergence transfer count
                0x00, 0x00, // dust level
            )
        )
    }

    @Test
    fun `omits receiver bytes when no classification matches the donor`() {
        val out = Unpooled.buffer()
        val window = ForgeWindow(
            donorTierItems = mapOf(1000 to mapOf(2 to 4)),
            receiveTierItems = mapOf(2000 to mapOf(0 to 2)),
            maxConfigTier = 10,
            dustLevel = 0,
        )
        val classification = { itemId: Int -> if (itemId == 1000) 5 else 9 }

        ForgeWindowSerializer.writeOpenForge(out, window, classification)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x87,
                0x00, 0x00,
                0x00, 0x00,
                0x01,
                0x01, 0x00,
                0xE8, 0x03, 0x02, 0x04, 0x00,
                0x00, 0x00, // zero matching receivers
                0x00, // convergence transfer count
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `serializes convergence transfer donors and receivers per slot`() {
        val out = Unpooled.buffer()
        val window = ForgeWindow(
            convergenceTransferItems = mapOf(
                2 to mapOf(
                    1000 to mapOf(1 to 4, 0 to 5),
                    2000 to mapOf(0 to 2),
                ),
            ),
            maxConfigTier = 10,
            dustLevel = 0,
        )

        ForgeWindowSerializer.writeOpenForge(out, window)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x87,
                0x00, 0x00, // fusion total
                0x00, 0x00, // convergence fusion count
                0x00, // transfer total count
                0x01, // one convergence transfer slot
                0x01, 0x00, // donor count
                0xE8, 0x03, 0x01, 0x04, 0x00, // donor itemId 1000, tier 1, count 4
                0x02, 0x00, // receiver count
                0xE8, 0x03, 0x05, 0x00, // receiver 1000 count 5
                0xD0, 0x07, 0x02, 0x00, // receiver 2000 count 2
                0x00, 0x00, // dust level
            )
        )
    }

    @Test
    fun `writes the player dust level as a little endian uint16`() {
        val out = Unpooled.buffer()

        ForgeWindowSerializer.writeOpenForge(
            out,
            ForgeWindow(maxConfigTier = 10, dustLevel = 245),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x87,
                0x00, 0x00,
                0x00, 0x00,
                0x00,
                0x00,
                0xF5, 0x00, // dust level 245 LE
            )
        )
    }

    @Test
    fun `serializes a successful fusion result in canary order`() {
        val out = Unpooled.buffer()

        ForgeWindowSerializer.writeForgeResult(
            out,
            ForgeResult(
                actionType = ForgeAction_t.FUSION,
                leftItemId = 0x0102,
                leftTier = 3,
                rightItemId = 0x0304,
                rightTier = 5,
                success = true,
                bonus = 0,
                coreCount = 0,
                convergence = false,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8A,
                0x00, // action fusion
                0x00, // convergence
                0x01, // success
                0x02, 0x01, // left item LE
                0x03, // left tier
                0x04, 0x03, // right item LE
                0x05, // right tier
                0x00, // bonus none
            )
        )
    }

    @Test
    fun `forces transfer result bonus byte to zero regardless of rolled bonus`() {
        val out = Unpooled.buffer()

        ForgeWindowSerializer.writeForgeResult(
            out,
            ForgeResult(
                actionType = ForgeAction_t.TRANSFER,
                leftItemId = 0x0102,
                leftTier = 3,
                rightItemId = 0x0304,
                rightTier = 5,
                success = false,
                bonus = 7,
                coreCount = 0,
                convergence = false,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8A,
                0x01, // action transfer
                0x00, // convergence
                0x00, // success
                0x02, 0x01,
                0x03,
                0x04, 0x03,
                0x05,
                0x00, // bonus forced none for transfer
            )
        )
    }

    @Test
    fun `swaps left and right item ids and forces success for a convergence fusion result`() {
        val out = Unpooled.buffer()

        ForgeWindowSerializer.writeForgeResult(
            out,
            ForgeResult(
                actionType = ForgeAction_t.FUSION,
                leftItemId = 0x0102,
                leftTier = 3,
                rightItemId = 0x0304,
                rightTier = 5,
                success = false,
                bonus = 0,
                coreCount = 0,
                convergence = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8A,
                0x00,
                0x01, // convergence
                0x01, // success forced
                0x04, 0x03, // swapped left item = right id
                0x03, // left tier unchanged
                0x02, 0x01, // swapped right item = left id
                0x05, // right tier unchanged
                0x00,
            )
        )
    }

    @Test
    fun `writes core count when the fusion bonus keeps cores`() {
        val out = Unpooled.buffer()

        ForgeWindowSerializer.writeForgeResult(
            out,
            ForgeResult(
                actionType = ForgeAction_t.FUSION,
                leftItemId = 0x0102,
                leftTier = 3,
                rightItemId = 0x0304,
                rightTier = 5,
                success = true,
                bonus = 2,
                coreCount = 9,
                convergence = false,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8A, 0x00, 0x00, 0x01,
                0x02, 0x01, 0x03, 0x04, 0x03, 0x05,
                0x02, // bonus cores kept
                0x09, // core count
            )
        )
    }

    @Test
    fun `writes retained left item and tier for high fusion bonuses`() {
        val out = Unpooled.buffer()

        ForgeWindowSerializer.writeForgeResult(
            out,
            ForgeResult(
                actionType = ForgeAction_t.FUSION,
                leftItemId = 0x0102,
                leftTier = 3,
                rightItemId = 0x0304,
                rightTier = 5,
                success = true,
                bonus = 8,
                coreCount = 0,
                convergence = false,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8A, 0x00, 0x00, 0x01,
                0x02, 0x01, 0x03, 0x04, 0x03, 0x05,
                0x08, // high bonus
                0x02, 0x01, // retained left item LE
                0x03, // left tier
            )
        )
    }

    @Test
    fun `opens the forge window frame through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            ForgeWindowSerializer.writeOpenForge(out, ForgeWindow(maxConfigTier = 10, dustLevel = 0))

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x87)
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
