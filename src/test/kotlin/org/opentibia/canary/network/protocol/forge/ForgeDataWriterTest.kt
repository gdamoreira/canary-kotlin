package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ForgeDataWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes forging data 0x86 with classifications core prices and config bytes`() {
        val out = Unpooled.buffer()

        ForgeDataWriter.writeForgingData(
            out,
            ForgeDataWriter.ForgingDataFrame(
                classifications = listOf(
                    ForgeDataWriter.ForgingClassification(
                        id = 1,
                        tiers = listOf(ForgeDataWriter.ForgingTierPrice(tier = 2, regularPrice = 1000L)),
                    ),
                ),
                tierCorePrices = linkedMapOf(2 to 3),
                convergenceFusionPrices = linkedMapOf(2 to 5000L),
                convergenceTransferPrices = linkedMapOf(2 to 6000L),
                config = ForgeDataWriter.ForgingDataConfig(
                    costOneSliver = 20,
                    sliverAmount = 3,
                    coreCost = 50,
                    dustLevel = 100,
                    maxDust = 325,
                    fusionDustCost = 100,
                    convergenceFusionDustCost = 130,
                    transferDustCost = 100,
                    convergenceTransferDustCost = 160,
                    baseSuccessRate = 50,
                    bonusSuccessRate = 15,
                    tierLossReduction = 50,
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x86,
                0x01,
                0x01,
                0x01,
                0x01,
                0xE8, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01,
                0x02, 0x03,
                0x01,
                0x01,
                0x88, 0x13, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01,
                0x02,
                0x70, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x14,
                0x03,
                0x32,
                0x4B,
                0x64, 0x00,
                0x45, 0x01,
                0x64,
                0x82,
                0x64,
                0xA0,
                0x32,
                0x0F,
                0x32,
            )
        )
    }

    @Test
    fun `writes empty forging tables with the config trailing bytes`() {
        val out = Unpooled.buffer()

        ForgeDataWriter.writeForgingData(
            out,
            ForgeDataWriter.ForgingDataFrame(
                classifications = emptyList(),
                tierCorePrices = emptyMap(),
                convergenceFusionPrices = emptyMap(),
                convergenceTransferPrices = emptyMap(),
                config = ForgeDataWriter.ForgingDataConfig(
                    costOneSliver = 0,
                    sliverAmount = 0,
                    coreCost = 0,
                    dustLevel = 0,
                    maxDust = 0,
                    fusionDustCost = 0,
                    convergenceFusionDustCost = 0,
                    transferDustCost = 0,
                    convergenceTransferDustCost = 0,
                    baseSuccessRate = 0,
                    bonusSuccessRate = 0,
                    tierLossReduction = 0,
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x86,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x4B,
                0x00, 0x00,
                0x00, 0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            )
        )
    }
}
