package org.opentibia.canary.network.protocol.prey

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.systems.prey.PreyBonus
import org.opentibia.canary.systems.prey.PreyDataState
import org.opentibia.canary.systems.prey.PreyOption
import org.opentibia.canary.systems.prey.PreySlot

class PreyWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun preySlot(id: Int): PreySlot = PreySlot(
        id = id,
        uniformRandom = { _, _ -> 1 },
        now = { 1_000L },
    )

    private val dragon = PreyMonsterWindow(
        name = "Dragon",
        outfit = Outfit(lookType = 34, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4, lookAddons = 5),
    )

    private val rat = PreyMonsterWindow(
        name = "Rat",
        outfit = Outfit(lookType = 0, lookTypeEx = 21),
    )

    private val monsters = mapOf(34 to dragon, 35 to rat)

    @Test
    fun `writes prey time left 0xE7 with the slot id and little endian seconds`() {
        val out = Unpooled.buffer()
        val slot = preySlot(id = 1)
        slot.bonusTimeLeft = 3600

        PreyWindowWriter.writePreyTimeLeft(out, slot)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE7,
                0x01,
                0x10, 0x0E,
            )
        )
    }

    @Test
    fun `writes active prey data 0xE8 with monster outfit bonus and reroll countdown`() {
        val out = Unpooled.buffer()
        val slot = preySlot(id = 0)
        slot.state = PreyDataState.Active
        slot.selectedRaceId = 34
        slot.bonus = PreyBonus.Damage
        slot.bonusPercentage = 9
        slot.bonusRarity = 2
        slot.bonusTimeLeft = 3600
        slot.option = PreyOption.AutomaticReroll
        slot.freeRerollTimeStamp = 10_000L
        slot.raceIdList.add(34)

        PreyWindowWriter.writePreyData(
            out,
            slot,
            monsterLookup = { monsters[it] },
            bestiaryRaceIds = emptyList(),
            premium = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE8,
                0x00,
                0x02,
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E,
                0x22, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05,
                0x00,
                0x09, 0x00,
                0x02,
                0x10, 0x0E,
                0x06, 0x00, 0x00, 0x00,
                0x01,
            )
        )
    }

    @Test
    fun `writes selection prey data 0xE8 with one outfit per valid race id`() {
        val out = Unpooled.buffer()
        val slot = preySlot(id = 0)
        slot.state = PreyDataState.Selection
        slot.freeRerollTimeStamp = 10_000L
        slot.raceIdList.add(34)
        slot.raceIdList.add(35)

        PreyWindowWriter.writePreyData(
            out,
            slot,
            monsterLookup = { monsters[it] },
            bestiaryRaceIds = emptyList(),
            premium = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE8,
                0x00,
                0x03,
                0x02,
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E,
                0x22, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05,
                0x03, 0x00, 0x52, 0x61, 0x74,
                0x00, 0x00, 0x15, 0x00,
                0x06, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes selection change monster 0xE8 with the kept bonus before the grid`() {
        val out = Unpooled.buffer()
        val slot = preySlot(id = 0)
        slot.state = PreyDataState.SelectionChangeMonster
        slot.bonus = PreyBonus.Loot
        slot.bonusPercentage = 19
        slot.bonusRarity = 4
        slot.freeRerollTimeStamp = 10_000L
        slot.raceIdList.add(34)

        PreyWindowWriter.writePreyData(
            out,
            slot,
            monsterLookup = { monsters[it] },
            bestiaryRaceIds = emptyList(),
            premium = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE8,
                0x00,
                0x04,
                0x03,
                0x13, 0x00,
                0x04,
                0x01,
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E,
                0x22, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05,
                0x06, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes list selection 0xE8 with the full bestiary race id list`() {
        val out = Unpooled.buffer()
        val slot = preySlot(id = 0)
        slot.state = PreyDataState.ListSelection
        slot.freeRerollTimeStamp = 10_000L

        PreyWindowWriter.writePreyData(
            out,
            slot,
            monsterLookup = { monsters[it] },
            bestiaryRaceIds = listOf(34, 200),
            premium = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE8,
                0x00,
                0x05,
                0x02, 0x00,
                0x22, 0x00,
                0xC8, 0x00,
                0x06, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the empty inactive slot 0xE8 when a race id has no monster type`() {
        val out = Unpooled.buffer()
        val slot = preySlot(id = 0)
        slot.state = PreyDataState.Selection
        slot.raceIdList.add(999)

        PreyWindowWriter.writePreyData(
            out,
            slot,
            monsterLookup = { monsters[it] },
            bestiaryRaceIds = emptyList(),
            premium = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE8,
                0x00,
                0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes locked prey 0xE8 with the premium flag and inactive prey 0xE8 without payload`() {
        val out = Unpooled.buffer()
        val locked = preySlot(id = 0)
        locked.state = PreyDataState.Locked
        locked.freeRerollTimeStamp = 10_000L
        val inactive = preySlot(id = 2)
        inactive.state = PreyDataState.Inactive
        inactive.freeRerollTimeStamp = 10_000L

        PreyWindowWriter.writePreyData(
            out,
            locked,
            monsterLookup = { monsters[it] },
            bestiaryRaceIds = emptyList(),
            premium = true,
            now = 4_000L,
        )
        PreyWindowWriter.writePreyData(
            out,
            inactive,
            monsterLookup = { monsters[it] },
            bestiaryRaceIds = emptyList(),
            premium = false,
            now = 4_000L,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE8,
                0x00,
                0x00,
                0x01,
                0x06, 0x00, 0x00, 0x00,
                0x00,
                0xE8,
                0x02,
                0x01,
                0x06, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes prey prices 0xE9 for the current profile without the legacy task block`() {
        val out = Unpooled.buffer()

        PreyWindowWriter.writePreyPrices(
            out,
            preyRerollPrice = 20_000L,
            preyBonusRerollPrice = 1,
            preySelectListPrice = 5,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE9,
                0x20, 0x4E, 0x00, 0x00,
                0x01,
                0x05,
            )
        )
    }

    @Test
    fun `writes prey prices 0xE9 with the legacy task hunting prices when the feature is absent`() {
        val out = Unpooled.buffer()

        PreyWindowWriter.writePreyPrices(
            out,
            preyRerollPrice = 20_000L,
            preyBonusRerollPrice = 1,
            preySelectListPrice = 5,
            officialTaskboardPackets = false,
            taskHuntingRerollPrice = 40_000L,
            taskHuntingSelectListPrice = 5,
            taskHuntingBonusRerollPrice = 1,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE9,
                0x20, 0x4E, 0x00, 0x00,
                0x01,
                0x05,
                0x40, 0x9C, 0x00, 0x00,
                0x40, 0x9C, 0x00, 0x00,
                0x05,
                0x01,
            )
        )
    }
}
