package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf

/**
 * Outbound forging data serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendForgingData` (0x86, protocolgame.cpp:7087-7168)
 *
 * The forge configuration values come from `g_configManager()`
 * (`FORGE_COST_ONE_SLIVER`, `FORGE_SLIVER_AMOUNT`, `FORGE_CORE_COST`,
 * `FORGE_MAX_DUST`, ...); the fixed "increase stored dust limit" byte 75 stays
 * hardcoded exactly like Canary. `sendForgingData` ends with
 * `parseSendResourceBalance()`, which emits separate
 * `sendResourceBalance` frames; those stay with the caller (`StoreWindowWriter`).
 * `std::map` iteration is ascending, so callers pass the price maps in
 * ascending tier order. Every multi-byte field is Little-Endian.
 */
object ForgeDataWriter {

    const val PACKET_FORGING_DATA: Int = 0x86

    /** `msg.addByte(75)` comment "cost to increase stored dust limit" (protocolgame.cpp:7144). */
    private const val INCREASE_DUST_COST = 75

    /** One `ItemClassification::tiers` entry (`src/items/items_definitions.hpp`). */
    data class ForgingTierPrice(
        val tier: Int,
        val regularPrice: Long,
    )

    /** One `ItemClassification` (`ItemClassification::id` and its tier prices). */
    data class ForgingClassification(
        val id: Int,
        val tiers: List<ForgingTierPrice>,
    )

    /** The forge config bytes (`g_configManager()` values) plus the stored dust level. */
    data class ForgingDataConfig(
        val costOneSliver: Int,
        val sliverAmount: Int,
        val coreCost: Int,
        val dustLevel: Int,
        val maxDust: Int,
        val fusionDustCost: Int,
        val convergenceFusionDustCost: Int,
        val transferDustCost: Int,
        val convergenceTransferDustCost: Int,
        val baseSuccessRate: Int,
        val bonusSuccessRate: Int,
        val tierLossReduction: Int,
    )

    /** The full 0x86 payload. */
    data class ForgingDataFrame(
        val classifications: List<ForgingClassification>,
        val tierCorePrices: Map<Int, Int>,
        val convergenceFusionPrices: Map<Int, Long>,
        val convergenceTransferPrices: Map<Int, Long>,
        val config: ForgingDataConfig,
    )

    /** `sendForgingData` (protocolgame.cpp:7092-7167). */
    fun writeForgingData(out: ByteBuf, frame: ForgingDataFrame) {
        out.writeByte(PACKET_FORGING_DATA)

        out.writeByte(frame.classifications.size)
        for (classification in frame.classifications) {
            out.writeByte(classification.id)
            out.writeByte(classification.tiers.size)
            for (tier in classification.tiers) {
                out.writeByte(tier.tier - 1)
                out.writeLongLE(tier.regularPrice)
            }
        }

        // Exalted core table per tier (protocolgame.cpp:7117-7121).
        out.writeByte(frame.tierCorePrices.size)
        for ((tier, cores) in frame.tierCorePrices) {
            out.writeByte(tier)
            out.writeByte(cores)
        }

        // Convergence fusion prices per tier (protocolgame.cpp:7124-7128).
        out.writeByte(frame.convergenceFusionPrices.size)
        for ((tier, price) in frame.convergenceFusionPrices) {
            out.writeByte(tier - 1)
            out.writeLongLE(price)
        }

        // Convergence transfer prices per tier (protocolgame.cpp:7131-7135).
        out.writeByte(frame.convergenceTransferPrices.size)
        for ((tier, price) in frame.convergenceTransferPrices) {
            out.writeByte(tier)
            out.writeLongLE(price)
        }

        val config = frame.config
        out.writeByte(config.costOneSliver)
        out.writeByte(config.sliverAmount)
        out.writeByte(config.coreCost)
        out.writeByte(INCREASE_DUST_COST)
        out.writeShortLE(config.dustLevel)
        out.writeShortLE(config.maxDust)
        out.writeByte(config.fusionDustCost)
        out.writeByte(config.convergenceFusionDustCost)
        out.writeByte(config.transferDustCost)
        out.writeByte(config.convergenceTransferDustCost)
        out.writeByte(config.baseSuccessRate)
        out.writeByte(config.bonusSuccessRate)
        out.writeByte(config.tierLossReduction)
    }
}
