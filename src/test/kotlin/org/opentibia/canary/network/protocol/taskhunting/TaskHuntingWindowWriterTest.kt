package org.opentibia.canary.network.protocol.taskhunting

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.PreyTaskDifficult
import org.opentibia.canary.systems.taskhunting.TaskHuntingOption
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot
import org.opentibia.canary.systems.taskhunting.TaskMonsterInfo

class TaskHuntingWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun taskSlot(id: Int): TaskHuntingSlot = TaskHuntingSlot(
        id = id,
        uniformRandom = { _, _ -> 1 },
        now = { 1_000L },
    )

    private val easyOption = TaskHuntingOption(
        difficult = PreyTaskDifficult.Easy,
        rarity = 1,
        firstKills = 25,
        firstReward = 10,
        secondKills = 50,
        secondReward = 20,
    )

    @Test
    fun `writes task hunting base data 0xBA with monster difficulties and option table`() {
        val out = Unpooled.buffer()

        TaskHuntingWindowWriter.writeTaskHuntingBaseData(
            out,
            monsters = listOf(
                TaskMonsterInfo(raceid = 34, name = "Dragon", bestiaryStars = 2),
                TaskMonsterInfo(raceid = 35, name = "Rat", bestiaryStars = 5),
            ),
            options = listOf(
                easyOption,
                TaskHuntingOption(
                    difficult = PreyTaskDifficult.Medium,
                    rarity = 2,
                    firstKills = 25,
                    firstReward = 11,
                    secondKills = 50,
                    secondReward = 22,
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBA,
                0x02, 0x00,
                0x22, 0x00, 0x02,
                0x23, 0x00, 0x03,
                0x02,
                0x01, 0x01, 0x19, 0x00, 0x0A, 0x00, 0x32, 0x00, 0x14, 0x00,
                0x02, 0x02, 0x19, 0x00, 0x0B, 0x00, 0x32, 0x00, 0x16, 0x00,
            )
        )
    }

    @Test
    fun `writes active task hunting data 0xBB with the first stage kill target`() {
        val out = Unpooled.buffer()
        val slot = taskSlot(id = 0)
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 34
        slot.upgrade = false
        slot.currentKills = 12
        slot.rarity = 3
        slot.freeRerollTimeStamp = 10_000L

        TaskHuntingWindowWriter.writeTaskHuntingData(
            out,
            slot,
            option = easyOption,
            creatureUnlocked = { false },
            bestiaryRaceIds = emptyList(),
            premium = false,
            officialTaskboardPackets = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBB,
                0x00,
                0x04,
                0x22, 0x00,
                0x00,
                0x19, 0x00,
                0x0C, 0x00,
                0x03,
                0x06, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes completed task hunting data 0xBB with kills capped at the upgrade target`() {
        val out = Unpooled.buffer()
        val slot = taskSlot(id = 1)
        slot.state = PreyTaskDataState.Completed
        slot.selectedRaceId = 34
        slot.upgrade = true
        slot.currentKills = 60
        slot.rarity = 4
        slot.freeRerollTimeStamp = 10_000L

        TaskHuntingWindowWriter.writeTaskHuntingData(
            out,
            slot,
            option = easyOption,
            creatureUnlocked = { false },
            bestiaryRaceIds = emptyList(),
            premium = false,
            officialTaskboardPackets = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBB,
                0x01,
                0x05,
                0x22, 0x00,
                0x01,
                0x32, 0x00,
                0x32, 0x00,
                0x04,
                0x06, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes selection 0xBB with the unlocked flag per race id`() {
        val out = Unpooled.buffer()
        val slot = taskSlot(id = 2)
        slot.state = PreyTaskDataState.Selection
        slot.freeRerollTimeStamp = 10_000L
        slot.raceIdList.add(34)
        slot.raceIdList.add(35)

        TaskHuntingWindowWriter.writeTaskHuntingData(
            out,
            slot,
            option = null,
            creatureUnlocked = { it == 34 },
            bestiaryRaceIds = emptyList(),
            premium = false,
            officialTaskboardPackets = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBB,
                0x02,
                0x02,
                0x02, 0x00,
                0x22, 0x00, 0x01,
                0x23, 0x00, 0x00,
                0x06, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes list selection 0xBB with the bestiary unlock flags`() {
        val out = Unpooled.buffer()
        val slot = taskSlot(id = 0)
        slot.state = PreyTaskDataState.ListSelection
        slot.freeRerollTimeStamp = 10_000L

        TaskHuntingWindowWriter.writeTaskHuntingData(
            out,
            slot,
            option = null,
            creatureUnlocked = { it == 35 },
            bestiaryRaceIds = listOf(34, 35),
            premium = false,
            officialTaskboardPackets = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBB,
                0x00,
                0x03,
                0x02, 0x00,
                0x22, 0x00, 0x00,
                0x23, 0x00, 0x01,
                0x06, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes locked and inactive task slots 0xBB`() {
        val out = Unpooled.buffer()
        val locked = taskSlot(id = 1)
        locked.state = PreyTaskDataState.Locked
        locked.freeRerollTimeStamp = 10_000L
        val inactive = taskSlot(id = 0)
        inactive.state = PreyTaskDataState.Inactive
        inactive.freeRerollTimeStamp = 10_000L

        TaskHuntingWindowWriter.writeTaskHuntingData(
            out,
            locked,
            option = null,
            creatureUnlocked = { false },
            bestiaryRaceIds = emptyList(),
            premium = true,
            officialTaskboardPackets = false,
            now = 4_000L,
        )
        TaskHuntingWindowWriter.writeTaskHuntingData(
            out,
            inactive,
            option = null,
            creatureUnlocked = { false },
            bestiaryRaceIds = emptyList(),
            premium = false,
            officialTaskboardPackets = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBB,
                0x01,
                0x00,
                0x01,
                0x06, 0x00, 0x00, 0x00,
                0xBB,
                0x00,
                0x01,
                0x06, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes nothing when the official taskboard feature owns the packet`() {
        val out = Unpooled.buffer()
        val slot = taskSlot(id = 0)
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 34
        slot.freeRerollTimeStamp = 10_000L

        TaskHuntingWindowWriter.writeTaskHuntingData(
            out,
            slot,
            option = easyOption,
            creatureUnlocked = { false },
            bestiaryRaceIds = emptyList(),
            premium = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes nothing when an active slot has no reward option`() {
        val out = Unpooled.buffer()
        val slot = taskSlot(id = 0)
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 34
        slot.freeRerollTimeStamp = 10_000L

        TaskHuntingWindowWriter.writeTaskHuntingData(
            out,
            slot,
            option = null,
            creatureUnlocked = { false },
            bestiaryRaceIds = emptyList(),
            premium = false,
            officialTaskboardPackets = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).isEmpty()
    }
}
