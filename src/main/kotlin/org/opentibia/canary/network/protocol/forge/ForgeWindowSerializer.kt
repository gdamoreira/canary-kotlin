package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import org.opentibia.canary.systems.forge.ForgeAction_t

/**
 * Data required to serialize the forge window (client opcode `0x87`,
 * `ProtocolGame::sendOpenForge`, src/server/network/protocol/protocolgame.cpp:7170).
 *
 * Every section is keyed exactly like the Canary `std::map` inputs, so
 * ascending-key iteration (over `itemId`, then `tier`, then slot) reproduces the
 * Canary byte order:
 *  - [fusionItems]: itemId -> tier -> count (entries with `count >= 2`).
 *  - [convergenceFusionItems]: slot position -> itemId -> tier -> count.
 *  - [convergenceTransferItems]: slot position -> itemId -> tier -> count.
 *  - [donorTierItems]: itemId -> tier -> count (tier donors).
 *  - [receiveTierItems]: itemId -> tier -> count (tier receivers).
 *  - [maxConfigTier]: `FORGE_MAX_ITEM_TIER`, filters convergence fusion entries.
 *  - [dustLevel]: player dust limit, written as the trailing `uint16_t`.
 */
data class ForgeWindow(
    val fusionItems: Map<Int, Map<Int, Int>> = emptyMap(),
    val convergenceFusionItems: Map<Int, Map<Int, Map<Int, Int>>> = emptyMap(),
    val convergenceTransferItems: Map<Int, Map<Int, Map<Int, Int>>> = emptyMap(),
    val donorTierItems: Map<Int, Map<Int, Int>> = emptyMap(),
    val receiveTierItems: Map<Int, Map<Int, Int>> = emptyMap(),
    val maxConfigTier: Int = 10,
    val dustLevel: Int = 0,
)

/**
 * Result of a forge fusion/transfer, mirroring
 * `ProtocolGame::sendForgeResult` (protocolgame.cpp:7445). [leftTier]/[rightTier]
 * are not swapped with the item ids for a convergence fusion.
 */
data class ForgeResult(
    val actionType: ForgeAction_t,
    val leftItemId: Int,
    val leftTier: Int,
    val rightItemId: Int,
    val rightTier: Int,
    val success: Boolean,
    val bonus: Int,
    val coreCount: Int,
    val convergence: Boolean,
)

/**
 * Outbound forge serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *  - `ProtocolGame::sendOpenForge`   (opcode 0x87)
 *  - `ProtocolGame::sendForgeResult` (opcode 0x8A)
 *
 * We compute each section's count up front instead of Canary's skip/backpatch:
 * the emitted bytes are identical because the backpatch only fills a placeholder
 * with a count that is fully known after the section is enumerated.
 */
object ForgeWindowSerializer {

    const val PACKET_OPEN_FORGE: Int = 0x87
    const val PACKET_FORGE_RESULT: Int = 0x8A

    /**
     * Serializes the forge window into [out].
     *
     * [upgradeClassification] supplies `ItemType::upgradeClassification` for the
     * transfer receiver matching (protocolgame.cpp:7310,7324); callers without an
     * item registry can leave it at the default `0`.
     */
    fun writeOpenForge(
        out: ByteBuf,
        window: ForgeWindow,
        upgradeClassification: (Int) -> Int = { 0 },
    ) {
        out.writeByte(PACKET_OPEN_FORGE)
        writeFusionSection(out, window.fusionItems)
        writeConvergenceFusionSection(out, window.convergenceFusionItems, window.maxConfigTier)
        writeTransferSection(out, window.donorTierItems, window.receiveTierItems, upgradeClassification)
        writeConvergenceTransferSection(out, window.convergenceTransferItems)
        out.writeShortLE(window.dustLevel)
    }

    /** protocolgame.cpp:7220-7247. [fusionItems] entries keep only `count >= 2`. */
    private fun writeFusionSection(out: ByteBuf, fusionItems: Map<Int, Map<Int, Int>>) {
        val entries = ArrayList<Triple<Int, Int, Int>>()
        for ((itemId, tierCounts) in fusionItems.ascending()) {
            for ((tier, count) in tierCounts.ascending()) {
                if (count >= 2) {
                    entries += Triple(itemId, tier, count)
                }
            }
        }

        // Canary accumulates into a uint8_t then writes it as uint16_t.
        out.writeShortLE(entries.size and 0xFF)

        for ((itemId, tier, count) in entries) {
            out.writeByte(0x01)
            out.writeShortLE(itemId)
            out.writeByte(tier)
            out.writeShortLE(count)
        }
    }

    /** protocolgame.cpp:7249-7288. Convergence fusion entries with `tier >= maxConfigTier` are skipped. */
    private fun writeConvergenceFusionSection(
        out: ByteBuf,
        convergenceFusionItems: Map<Int, Map<Int, Map<Int, Int>>>,
        maxConfigTier: Int,
    ) {
        val slots = ArrayList<List<Triple<Int, Int, Int>>>()
        for ((_, itemMaps) in convergenceFusionItems.ascending()) {
            val entries = ArrayList<Triple<Int, Int, Int>>()
            for ((itemId, tierCounts) in itemMaps.ascending()) {
                for ((tier, count) in tierCounts.ascending()) {
                    if (tier < maxConfigTier) {
                        entries += Triple(itemId, tier, count)
                    }
                }
            }
            if (entries.isNotEmpty()) {
                slots += entries
            }
        }

        out.writeShortLE(slots.size)
        for (entries in slots) {
            out.writeByte(entries.size and 0xFF)
            for ((itemId, tier, count) in entries) {
                out.writeShortLE(itemId)
                out.writeByte(tier)
                out.writeShortLE(count)
            }
        }
    }

    /** protocolgame.cpp:7290-7333. */
    private fun writeTransferSection(
        out: ByteBuf,
        donorTierItems: Map<Int, Map<Int, Int>>,
        receiveTierItems: Map<Int, Map<Int, Int>>,
        upgradeClassification: (Int) -> Int,
    ) {
        out.writeByte(donorTierItems.size and 0xFF)

        for ((itemId, tierCounts) in donorTierItems.ascending()) {
            val donorClassification = upgradeClassification(itemId)

            out.writeShortLE(tierCounts.size)
            for ((tier, count) in tierCounts.ascending()) {
                out.writeShortLE(itemId)
                out.writeByte(tier)
                out.writeShortLE(count)
            }

            val receivers = receiveTierItems.ascending()
                .filter { (receiveItemId, _) -> upgradeClassification(receiveItemId) == donorClassification }

            out.writeShortLE(receivers.size)
            if (receivers.isEmpty()) {
                continue
            }

            for ((receiveItemId, receiveTierCounts) in receivers) {
                for ((_, count) in receiveTierCounts.ascending()) {
                    out.writeShortLE(receiveItemId)
                    out.writeShortLE(count)
                }
            }
        }
    }

    /** protocolgame.cpp:7335-7392. Empty slots are dropped entirely. */
    private fun writeConvergenceTransferSection(
        out: ByteBuf,
        convergenceTransferItems: Map<Int, Map<Int, Map<Int, Int>>>,
    ) {
        val slots = ArrayList<ConvergenceTransferSlot>()
        for ((_, itemMaps) in convergenceTransferItems.ascending()) {
            val donors = ArrayList<Triple<Int, Int, Int>>()
            val receivers = ArrayList<Pair<Int, Int>>()
            var receiverCount = 0

            for ((itemId, tierCounts) in itemMaps.ascending()) {
                for ((tier, count) in tierCounts.ascending()) {
                    if (tier >= 1) {
                        donors += Triple(itemId, tier, count)
                    } else {
                        receivers += itemId to count
                        receiverCount++
                    }
                }
            }

            if (donors.isEmpty() && receiverCount == 0) {
                continue
            }

            slots += ConvergenceTransferSlot(donors, receivers, receiverCount)
        }

        out.writeByte(slots.size and 0xFF)
        for (slot in slots) {
            out.writeShortLE(slot.donors.size)
            for ((itemId, tier, count) in slot.donors) {
                out.writeShortLE(itemId)
                out.writeByte(tier)
                out.writeShortLE(count)
            }
            out.writeShortLE(slot.receiverCount)
            for ((itemId, count) in slot.receivers) {
                out.writeShortLE(itemId)
                out.writeShortLE(count)
            }
        }
    }

    /**
     * protocolgame.cpp:7445-7481.
     *
     * A convergence fusion forces success and swaps the two item ids (but not
     * their tiers) before the fields are written; transfer always emits a `0x00`
     * bonus byte.
     */
    fun writeForgeResult(out: ByteBuf, result: ForgeResult) {
        out.writeByte(PACKET_FORGE_RESULT)
        out.writeByte(result.actionType.value)
        out.writeByte(if (result.convergence) 0x01 else 0x00)

        var leftItemId = result.leftItemId
        var rightItemId = result.rightItemId
        var success = result.success
        if (result.convergence && result.actionType == ForgeAction_t.FUSION) {
            success = true
            val swap = leftItemId
            leftItemId = rightItemId
            rightItemId = swap
        }

        out.writeByte(if (success) 0x01 else 0x00)
        out.writeShortLE(leftItemId)
        out.writeByte(result.leftTier)
        out.writeShortLE(rightItemId)
        out.writeByte(result.rightTier)

        if (result.actionType == ForgeAction_t.TRANSFER) {
            out.writeByte(0x00)
            return
        }

        out.writeByte(result.bonus)
        if (result.bonus == 2) {
            out.writeByte(result.coreCount)
        } else if (result.bonus in 4..8) {
            out.writeShortLE(leftItemId)
            out.writeByte(result.leftTier)
        }
    }

    private data class ConvergenceTransferSlot(
        val donors: List<Triple<Int, Int, Int>>,
        val receivers: List<Pair<Int, Int>>,
        val receiverCount: Int,
    )

    private fun <K : Comparable<K>, V> Map<K, V>.ascending(): List<Map.Entry<K, V>> =
        entries.sortedBy { it.key }
}
