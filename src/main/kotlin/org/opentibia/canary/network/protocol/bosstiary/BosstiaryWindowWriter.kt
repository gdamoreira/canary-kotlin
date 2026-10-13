package org.opentibia.canary.network.protocol.bosstiary

import io.netty.buffer.ByteBuf

/**
 * Outbound bosstiary serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::parseSendBosstiary`        (0x73, protocolgame.cpp:11206-11246)
 *  - `ProtocolGame::parseSendBosstiarySlots`   (0x62, protocolgame.cpp:11248-11381)
 *  - `ProtocolGame::sendBosstiarySlotsBytes`   (protocolgame.cpp:251-259)
 *  - `ProtocolGame::sendBosstiaryCooldownTimer`(0xBD, protocolgame.cpp:11508-11553)
 *  - `ProtocolGame::sendBosstiaryEntryChanged` (0xE6, protocolgame.cpp:11555-11564)
 *
 * The fixed `ProtocolGame::sendBosstiaryData` 0x61 frame already lives in
 * `SocialWindowWriter.writeBosstiaryData`. Every multi-byte field is
 * Little-Endian. Values Canary reads from the live player / `IOBosstiary`
 * registry (points, bonuses, cooldowns, unlocked lists) are passed in by the
 * caller; the `oldProtocol` and invalid-boss-race guards stay with the caller.
 */
object BosstiaryWindowWriter {

    const val PACKET_BOSSTIARY_LIST: Int = 0x73
    const val PACKET_BOSSTIARY_SLOTS: Int = 0x62
    const val PACKET_BOSSTIARY_COOLDOWN: Int = 0xBD
    const val PACKET_BOSSTIARY_ENTRY_CHANGED: Int = 0xE6

    /** `parseSendBosstiary` entry bytes: boss id, race, kill count, zero and tracker flag. */
    data class BosstiaryListEntry(
        val bossId: Long,
        val bossRace: Int,
        val killCount: Long,
        val tracked: Boolean = false,
    )

    /** One `IOBosstiary::getBosstiaryFinished` entry (boss id + bosstiary race). */
    data class BosstiaryUnlockedBoss(
        val bossId: Long,
        val bossRace: Int,
    )

    /**
     * The seven `sendBosstiarySlotsBytes` fields (protocolgame.cpp:251-259):
     * race, kill count, loot bonus, kill bonus, race again, remove price and the
     * boosted-boss inactive flag.
     */
    data class BosstiarySlotBytes(
        val bossRace: Int,
        val killCount: Long,
        val lootBonus: Int,
        val killBonus: Int,
        val inactive: Boolean,
        val removePrice: Long,
    )

    /**
     * The full 0x62 payload. [slotOne]/[slotTwo]/[boostedBoss] must be non-null
     * whenever their unlock flag resolves true and their boss id is non-zero,
     * mirroring the state Canary has in `parseSendBosstiarySlots`
     * (protocolgame.cpp:11248-11381). The second slot unlock cost is the fixed
     * `slotTwoPoints = 1500`.
     */
    data class BosstiarySlotsFrame(
        val playerBossPoints: Long,
        val pointsNextBonus: Long,
        val currentBonus: Int,
        val removePrice: Long,
        val slotOneBossId: Long = 0,
        val slotOne: BosstiarySlotBytes? = null,
        val slotTwoBossId: Long = 0,
        val slotTwo: BosstiarySlotBytes? = null,
        val todaySlotUnlocked: Boolean = false,
        val boostedBossId: Long = 0,
        val boostedBoss: BosstiarySlotBytes? = null,
        val finishedBosses: List<BosstiaryUnlockedBoss> = emptyList(),
    )

    /** One `Player` boss-cooldown KV entry as `sendBosstiaryCooldownTimer` reads it. */
    data class BosstiaryCooldown(
        val bossRaceId: Int,
        val timerSeconds: Long,
    )

    /** `parseSendBosstiary` 0x73 (protocolgame.cpp:11213-11245), in registry order. */
    fun writeBosstiaryList(out: ByteBuf, entries: List<BosstiaryListEntry>) {
        out.writeByte(PACKET_BOSSTIARY_LIST)
        out.writeShortLE(entries.size)
        for (entry in entries) {
            out.writeIntLE(entry.bossId.toInt())
            out.writeByte(entry.bossRace)
            out.writeIntLE(entry.killCount.toInt())
            out.writeByte(0)
            out.writeByte(if (entry.tracked) 0x01 else 0x00)
        }
    }

    /** `parseSendBosstiarySlots` 0x62 (protocolgame.cpp:11248-11381). */
    fun writeBosstiarySlots(out: ByteBuf, frame: BosstiarySlotsFrame) {
        out.writeByte(PACKET_BOSSTIARY_SLOTS)

        out.writeIntLE(frame.playerBossPoints.toInt())
        out.writeIntLE(frame.pointsNextBonus.toInt())
        out.writeShortLE(frame.currentBonus)
        out.writeShortLE(frame.currentBonus + 1)

        // `getBosstiaryFinished` minus the boosted boss (protocolgame.cpp:11302-11307).
        val remaining = frame.finishedBosses.filter { it.bossId != frame.boostedBossId }
        var unlockedSize = remaining.size

        val slotOneUnlocked = unlockedSize > 0
        out.writeByte(if (slotOneUnlocked) 0x01 else 0x00)
        out.writeIntLE((if (slotOneUnlocked) frame.slotOneBossId else 0L).toInt())
        if (slotOneUnlocked && frame.slotOneBossId != 0L) {
            frame.slotOne?.let { writeSlotBytes(out, it) }
            unlockedSize--
        }

        val slotTwoUnlocked = frame.playerBossPoints >= SLOT_TWO_POINTS
        out.writeByte(if (slotTwoUnlocked) 0x01 else 0x00)
        out.writeIntLE((if (slotTwoUnlocked) frame.slotTwoBossId else SLOT_TWO_POINTS).toInt())
        if (slotTwoUnlocked && frame.slotTwoBossId != 0L) {
            frame.slotTwo?.let { writeSlotBytes(out, it) }
            unlockedSize--
        }

        out.writeByte(if (frame.todaySlotUnlocked) 0x01 else 0x00)
        out.writeIntLE(frame.boostedBossId.toInt())
        if (frame.todaySlotUnlocked && frame.boostedBossId != 0L) {
            frame.boostedBoss?.let { writeSlotBytes(out, it) }
        }

        val hasUnlockedBosses = unlockedSize != 0
        out.writeByte(if (hasUnlockedBosses) 0x01 else 0x00)
        if (hasUnlockedBosses) {
            val unassigned = remaining.filter {
                it.bossId != frame.slotOneBossId && it.bossId != frame.slotTwoBossId
            }
            out.writeShortLE(unassigned.size)
            for (boss in unassigned) {
                out.writeIntLE(boss.bossId.toInt())
                out.writeByte(boss.bossRace)
            }
        }
    }

    /**
     * `sendBosstiaryCooldownTimer` 0xBD (protocolgame.cpp:11508-11553): the
     * `schedulePercentage` scaling is applied by the caller before [timerSeconds]
     * reaches this frame.
     */
    fun writeBosstiaryCooldownTimer(out: ByteBuf, cooldowns: List<BosstiaryCooldown>) {
        out.writeByte(PACKET_BOSSTIARY_COOLDOWN)
        out.writeShortLE(cooldowns.size)
        for (cooldown in cooldowns) {
            out.writeIntLE(cooldown.bossRaceId)
            out.writeLongLE(cooldown.timerSeconds)
        }
    }

    /** `sendBosstiaryEntryChanged` 0xE6 (protocolgame.cpp:11555-11564). */
    fun writeBosstiaryEntryChanged(out: ByteBuf, bossId: Long) {
        out.writeByte(PACKET_BOSSTIARY_ENTRY_CHANGED)
        out.writeIntLE(bossId.toInt())
    }

    /** `sendBosstiarySlotsBytes` (protocolgame.cpp:251-259). */
    private fun writeSlotBytes(out: ByteBuf, slot: BosstiarySlotBytes) {
        out.writeByte(slot.bossRace)
        out.writeIntLE(slot.killCount.toInt())
        out.writeShortLE(slot.lootBonus)
        out.writeByte(slot.killBonus)
        out.writeByte(slot.bossRace)
        out.writeIntLE(slot.removePrice.toInt())
        out.writeByte(if (slot.inactive) 0x01 else 0x00)
    }

    /** `uint32_t slotTwoPoints = 1500` (protocolgame.cpp:11323). */
    private const val SLOT_TWO_POINTS = 1500
}
