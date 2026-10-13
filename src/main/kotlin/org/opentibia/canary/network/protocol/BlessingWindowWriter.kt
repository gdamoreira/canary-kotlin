package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Outbound blessing window serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendBlessingWindow` (0x9B, protocolgame.cpp:5899-5968)
 *  - `ProtocolGame::sendBlessStatus`    (0x9C, protocolgame.cpp:5970-5998)
 *  - `calculateEquipmentLoss`           (src/utils/tools.cpp:2251-2276)
 *  - `calculateMaxPvpReduction`         (src/utils/tools.cpp:2278-2290)
 *
 * The `!player`/`oldProtocol`/`version < 1100` guards stay with the caller.
 * `Blessings` values 1-8 map to `1 << value` blessing ids; `TwistOfFate` is
 * omitted from the retro rows and from the held-blessing count. Every
 * multi-byte field is Little-Endian.
 */
object BlessingWindowWriter {

    const val PACKET_BLESSING_WINDOW: Int = 0x9B
    const val PACKET_BLESS_STATUS: Int = 0x9C

    /** `magic_enum::enum_values<Blessings>()` blessing ids in enum order. */
    private val BLESSING_IDS = intArrayOf(2, 4, 8, 16, 32, 64, 128, 256)

    /** `Blessings::TwistOfFate` index in [BLESSING_IDS]. */
    private const val TWIST_OF_FATE_INDEX = 0

    /**
     * `sendBlessingWindow` (protocolgame.cpp:5899-5968).
     *
     * [blessingCounts]/[storeCounts] are `Player::getBlessingCount` (with and
     * without the store count) indexed by `Blessings` enum value minus one.
     * [glow] is pre-resolved by the caller; `sendBlessStatus` applies the
     * `INVENTORY_GLOW`/`ADVENTURERSBLESSING_LEVEL` expression.
     */
    fun writeBlessingWindow(
        out: ByteBuf,
        blessingCounts: List<Int>,
        storeCounts: List<Int>,
        retro: Boolean = false,
        promoted: Boolean = false,
        hasSkull: Boolean = false,
        usingAol: Boolean = false,
    ) {
        out.writeByte(PACKET_BLESSING_WINDOW)
        out.writeByte(if (retro) 0x07 else 0x08)

        for (index in BLESSING_IDS.indices) {
            if (retro && index == TWIST_OF_FATE_INDEX) {
                continue
            }
            out.writeShortLE(BLESSING_IDS[index])
            out.writeByte(blessingCounts[index])
            out.writeByte(storeCounts[index])
        }

        var blessCount = 0
        for (index in BLESSING_IDS.indices) {
            if (index == TWIST_OF_FATE_INDEX) {
                continue
            }
            if (blessingCounts[index] > 0) {
                blessCount++
            }
        }

        val factor = if (retro) 6.31 else 8.0
        val skillReduction = factor * blessCount
        val promotionReduction = if (promoted) 30 else 0
        val minReduction = (skillReduction + promotionReduction).toInt()
        val maxLossPvpDeath = calculateMaxPvpReduction(blessCount, promoted)

        out.writeByte(if (promoted) 0x01 else 0x00)
        out.writeByte(30)
        out.writeByte(minReduction and 0xFF)
        out.writeByte(if (retro) minReduction and 0xFF else maxLossPvpDeath)
        out.writeByte(minReduction and 0xFF)

        if (hasSkull) {
            out.writeByte(100)
            out.writeByte(100)
        } else if (usingAol) {
            out.writeByte(0)
            out.writeByte(0)
        } else {
            val equipmentLoss = calculateEquipmentLoss(blessCount, true).toInt()
            out.writeByte(equipmentLoss and 0xFF)
            out.writeByte(equipmentLoss and 0xFF)
        }

        out.writeByte(if (hasSkull) 0x01 else 0x00)
        out.writeByte(if (usingAol) 0x01 else 0x00)
        out.writeByte(0x00)
    }

    /**
     * `sendBlessStatus` (protocolgame.cpp:5970-5998). [glow] is the caller's
     * resolved `INVENTORY_GLOW`/`ADVENTURERSBLESSING_LEVEL` expression; the
     * trailing level byte is `3` for seven or more blessings (green), `2` for
     * five or six (normal) and `1` below five (disabled).
     */
    fun writeBlessStatus(out: ByteBuf, blessCount: Int, glow: Boolean = false, oldProtocol: Boolean = false) {
        out.writeByte(PACKET_BLESS_STATUS)

        if (oldProtocol) {
            out.writeShortLE(if (blessCount >= 5) 0x01 else 0x00)
            return
        }

        out.writeShortLE(if (glow) 0x01 else 0x00)
        out.writeByte(
            when {
                blessCount >= 7 -> 3
                blessCount >= 5 -> 2
                else -> 1
            },
        )
    }

    /** `calculateEquipmentLoss` (`src/utils/tools.cpp:2251-2276`). */
    fun calculateEquipmentLoss(blessingAmount: Int, isContainer: Boolean = false): Double {
        val lossPercent = when (blessingAmount) {
            0 -> 10.0
            1 -> 7.0
            2 -> 4.5
            3 -> 2.5
            4 -> 1.0
            else -> 0.0
        }
        return if (isContainer) lossPercent * 10 else lossPercent
    }

    /** `calculateMaxPvpReduction` (`src/utils/tools.cpp:2278-2290`). */
    fun calculateMaxPvpReduction(blessCount: Int, isPromoted: Boolean = false): Int {
        var result = 80 + (2 * blessCount) - (blessCount / 3)
        if (blessCount == 5) {
            result -= 1
        }
        if (isPromoted) {
            result += 6
        }
        return result
    }
}
