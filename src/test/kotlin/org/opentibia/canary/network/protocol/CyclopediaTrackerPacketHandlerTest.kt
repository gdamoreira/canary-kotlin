package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.CyclopediaMonsterTrackerClient
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.map.Position

/**
 * Decode and apply tests for `ProtocolGame::parseCyclopediaMonsterTracker`
 * (client opcode `0x2A`, protocolgame.cpp:3634-3668).
 */
class CyclopediaTrackerPacketHandlerTest {

    @Test
    fun `parses race id and enabled tracker button`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(1234)
        payload.writeByte(1)

        val command = CyclopediaTrackerPacketHandler.parse(payload)

        assertThat(command).isEqualTo(CyclopediaTrackerCommand(monsterRaceId = 1234, trackerButtonType = 1))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses race id and disabled tracker button`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(65535)
        payload.writeByte(0)

        val command = CyclopediaTrackerPacketHandler.parse(payload)

        assertThat(command).isEqualTo(CyclopediaTrackerCommand(monsterRaceId = 65535, trackerButtonType = 0))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated tracker payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        assertThat(CyclopediaTrackerPacketHandler.parse(payload)).isNull()
    }

    @Test
    fun `adds the boss monster when the kill count is nonzero`() {
        val player = hero()
        player.cyclopediaMonsterTrackerClient = RecordingTrackerClient()
        player.features.bestiary.addBestiaryKillCount(1234, 1)
        val boss = MonsterType("Boss").apply { info.raceid = 1234 }

        CyclopediaTrackerPacketHandler.apply(
            player,
            CyclopediaTrackerCommand(monsterRaceId = 1234, trackerButtonType = 1),
            monsterTypeByBossRaceId = { if (it == 1234) boss else null },
        )

        assertThat(player.getCyclopediaMonsterTrackerSet(isBoss = true)).containsExactly(boss)
    }

    @Test
    fun `removes the boss monster when the tracker button disables it`() {
        val player = hero()
        player.cyclopediaMonsterTrackerClient = RecordingTrackerClient()
        player.features.bestiary.addBestiaryKillCount(1234, 1)
        val boss = MonsterType("Boss").apply { info.raceid = 1234 }
        player.addMonsterToCyclopediaTrackerList(boss, isBoss = true, reloadClient = true)

        CyclopediaTrackerPacketHandler.apply(
            player,
            CyclopediaTrackerCommand(monsterRaceId = 1234, trackerButtonType = 0),
            monsterTypeByBossRaceId = { if (it == 1234) boss else null },
        )

        assertThat(player.getCyclopediaMonsterTrackerSet(isBoss = true)).isEmpty()
    }

    @Test
    fun `boss branch ignores a zero bestiary kill count`() {
        val player = hero()
        player.cyclopediaMonsterTrackerClient = RecordingTrackerClient()
        val boss = MonsterType("Boss").apply { info.raceid = 1234 }

        CyclopediaTrackerPacketHandler.apply(
            player,
            CyclopediaTrackerCommand(monsterRaceId = 1234, trackerButtonType = 1),
            monsterTypeByBossRaceId = { if (it == 1234) boss else null },
        )

        assertThat(player.getCyclopediaMonsterTrackerSet(isBoss = true)).isEmpty()
    }

    @Test
    fun `adds the bestiary monster resolved by race id on the non-boss tracker`() {
        val player = hero()
        player.cyclopediaMonsterTrackerClient = RecordingTrackerClient()
        val monster = MonsterType("Rat").apply { info.raceid = 56 }

        CyclopediaTrackerPacketHandler.apply(
            player,
            CyclopediaTrackerCommand(monsterRaceId = 56, trackerButtonType = 1),
            monsterTypeByRaceId = { if (it == 56) monster else null },
        )

        assertThat(player.getCyclopediaMonsterTrackerSet(isBoss = false)).containsExactly(monster)
        assertThat(player.getCyclopediaMonsterTrackerSet(isBoss = true)).isEmpty()
    }

    @Test
    fun `unknown race ids leave both tracker sets untouched`() {
        val player = hero()
        player.cyclopediaMonsterTrackerClient = RecordingTrackerClient()

        CyclopediaTrackerPacketHandler.apply(
            player,
            CyclopediaTrackerCommand(monsterRaceId = 999, trackerButtonType = 1),
        )

        assertThat(player.getCyclopediaMonsterTrackerSet(isBoss = false)).isEmpty()
        assertThat(player.getCyclopediaMonsterTrackerSet(isBoss = true)).isEmpty()
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private class RecordingTrackerClient : CyclopediaMonsterTrackerClient {
        var bosstiaryRefreshes = 0
        val bestiaryEntryChanges = mutableListOf<Int>()
        var trackerRefreshes = 0

        override fun parseSendBosstiary() {
            bosstiaryRefreshes++
        }

        override fun sendBestiaryEntryChanged(raceId: Int) {
            bestiaryEntryChanges.add(raceId)
        }

        override fun refreshCyclopediaMonsterTracker(tracker: Set<MonsterType>, isBoss: Boolean) {
            trackerRefreshes++
        }
    }
}
