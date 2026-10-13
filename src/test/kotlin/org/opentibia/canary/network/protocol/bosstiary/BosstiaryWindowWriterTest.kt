package org.opentibia.canary.network.protocol.bosstiary

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BosstiaryWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes the bosstiary list 0x73 with the boss race kill count and tracker byte`() {
        val out = Unpooled.buffer()

        BosstiaryWindowWriter.writeBosstiaryList(
            out,
            listOf(
                BosstiaryWindowWriter.BosstiaryListEntry(bossId = 100L, bossRace = 0, killCount = 25L, tracked = true),
                BosstiaryWindowWriter.BosstiaryListEntry(bossId = 200L, bossRace = 1, killCount = 0L),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x73,
                0x02, 0x00,
                0x64, 0x00, 0x00, 0x00,
                0x00,
                0x19, 0x00, 0x00, 0x00,
                0x00,
                0x01,
                0xC8, 0x00, 0x00, 0x00,
                0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes bosstiary slots 0x62 with slot one boosted boss and the unassigned list`() {
        val out = Unpooled.buffer()

        BosstiaryWindowWriter.writeBosstiarySlots(
            out,
            BosstiaryWindowWriter.BosstiarySlotsFrame(
                playerBossPoints = 1500L,
                pointsNextBonus = 3000L,
                currentBonus = 25,
                removePrice = 100L,
                slotOneBossId = 100L,
                slotOne = BosstiaryWindowWriter.BosstiarySlotBytes(
                    bossRace = 0,
                    killCount = 100L,
                    lootBonus = 25,
                    killBonus = 0,
                    inactive = false,
                    removePrice = 100L,
                ),
                slotTwoBossId = 0L,
                todaySlotUnlocked = true,
                boostedBossId = 200L,
                boostedBoss = BosstiaryWindowWriter.BosstiarySlotBytes(
                    bossRace = 1,
                    killCount = 5L,
                    lootBonus = 50,
                    killBonus = 11,
                    inactive = false,
                    removePrice = 0L,
                ),
                finishedBosses = listOf(
                    BosstiaryWindowWriter.BosstiaryUnlockedBoss(bossId = 100L, bossRace = 0),
                    BosstiaryWindowWriter.BosstiaryUnlockedBoss(bossId = 200L, bossRace = 1),
                    BosstiaryWindowWriter.BosstiaryUnlockedBoss(bossId = 300L, bossRace = 2),
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x62,
                0xDC, 0x05, 0x00, 0x00,
                0xB8, 0x0B, 0x00, 0x00,
                0x19, 0x00,
                0x1A, 0x00,
                0x01,
                0x64, 0x00, 0x00, 0x00,
                0x00,
                0x64, 0x00, 0x00, 0x00,
                0x19, 0x00,
                0x00,
                0x00,
                0x64, 0x00, 0x00, 0x00,
                0x00,
                0x01,
                0x00, 0x00, 0x00, 0x00,
                0x01,
                0xC8, 0x00, 0x00, 0x00,
                0x01,
                0x05, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x0B,
                0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0x01,
                0x01, 0x00,
                0x2C, 0x01, 0x00, 0x00,
                0x02,
            )
        )
    }

    @Test
    fun `writes bosstiary slots 0x62 with both slots filled and no unassigned bosses`() {
        val out = Unpooled.buffer()

        BosstiaryWindowWriter.writeBosstiarySlots(
            out,
            BosstiaryWindowWriter.BosstiarySlotsFrame(
                playerBossPoints = 2000L,
                pointsNextBonus = 0L,
                currentBonus = 0,
                removePrice = 0L,
                slotOneBossId = 100L,
                slotOne = BosstiaryWindowWriter.BosstiarySlotBytes(
                    bossRace = 0,
                    killCount = 1L,
                    lootBonus = 0,
                    killBonus = 0,
                    inactive = false,
                    removePrice = 0L,
                ),
                slotTwoBossId = 200L,
                slotTwo = BosstiaryWindowWriter.BosstiarySlotBytes(
                    bossRace = 1,
                    killCount = 2L,
                    lootBonus = 0,
                    killBonus = 0,
                    inactive = true,
                    removePrice = 0L,
                ),
                finishedBosses = listOf(
                    BosstiaryWindowWriter.BosstiaryUnlockedBoss(bossId = 100L, bossRace = 0),
                    BosstiaryWindowWriter.BosstiaryUnlockedBoss(bossId = 200L, bossRace = 1),
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x62,
                0xD0, 0x07, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x01, 0x00,
                0x01,
                0x64, 0x00, 0x00, 0x00,
                0x00,
                0x01, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x00,
                0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0x01,
                0xC8, 0x00, 0x00, 0x00,
                0x01,
                0x02, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x00,
                0x01,
                0x00, 0x00, 0x00, 0x00,
                0x01,
                0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the empty bosstiary slots 0x62 with the slot two unlock cost`() {
        val out = Unpooled.buffer()

        BosstiaryWindowWriter.writeBosstiarySlots(
            out,
            BosstiaryWindowWriter.BosstiarySlotsFrame(
                playerBossPoints = 0L,
                pointsNextBonus = 0L,
                currentBonus = 0,
                removePrice = 0L,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x62,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x01, 0x00,
                0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0xDC, 0x05, 0x00, 0x00,
                0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the boss cooldown timer 0xBD with little endian seconds`() {
        val out = Unpooled.buffer()

        BosstiaryWindowWriter.writeBosstiaryCooldownTimer(
            out,
            listOf(
                BosstiaryWindowWriter.BosstiaryCooldown(bossRaceId = 100, timerSeconds = 3600L),
                BosstiaryWindowWriter.BosstiaryCooldown(bossRaceId = 200, timerSeconds = 0L),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBD,
                0x02, 0x00,
                0x64, 0x00, 0x00, 0x00,
                0x10, 0x0E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xC8, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the bosstiary entry changed 0xE6 with the boss id`() {
        val out = Unpooled.buffer()

        BosstiaryWindowWriter.writeBosstiaryEntryChanged(out, bossId = 200L)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE6,
                0xC8, 0x00, 0x00, 0x00,
            )
        )
    }
}
