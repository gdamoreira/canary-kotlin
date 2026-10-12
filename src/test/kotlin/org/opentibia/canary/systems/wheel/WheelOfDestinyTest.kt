package org.opentibia.canary.systems.wheel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WheelOfDestinyTest {

    private val wheel = IOWheel()

    @Test
    fun `point budget is one per level above fifty`() {
        assertThat(PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 50).getWheelPoints()).isEqualTo(0)
        assertThat(PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 51).getWheelPoints()).isEqualTo(1)
        assertThat(PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 100).getWheelPoints()).isEqualTo(50)
        assertThat(PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 250).getWheelPoints()).isEqualTo(200)
    }

    @Test
    fun `points per level feature flag scales the point budget`() {
        assertThat(PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 100, pointsPerLevel = 1).getWheelPoints())
            .isEqualTo(50)
        assertThat(PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 100, pointsPerLevel = 2).getWheelPoints())
            .isEqualTo(100)
    }

    @Test
    fun `extra points are included by default and excluded on request`() {
        val wheel = PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 100, extraPoints = 7)
        assertThat(wheel.getWheelPoints()).isEqualTo(57)
        assertThat(wheel.getWheelPoints(includeExtraPoints = false)).isEqualTo(50)
    }

    @Test
    fun `revelation stats follow the stage`() {
        assertThat(wheel.getRevelationStatByStage(WheelStageEnum.ONE)).isEqualTo(4 to 4)
        assertThat(wheel.getRevelationStatByStage(WheelStageEnum.TWO)).isEqualTo(9 to 9)
        assertThat(wheel.getRevelationStatByStage(WheelStageEnum.THREE)).isEqualTo(20 to 20)
    }

    @Test
    fun `max points per slot follows the tier`() {
        val state = PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 250)
        assertThat(state.getMaxPointsPerSlot(WheelSlots.SLOT_GREEN_50)).isEqualTo(50)
        assertThat(state.getMaxPointsPerSlot(WheelSlots.SLOT_GREEN_TOP_75)).isEqualTo(75)
        assertThat(state.getMaxPointsPerSlot(WheelSlots.SLOT_GREEN_TOP_100)).isEqualTo(100)
        assertThat(state.getMaxPointsPerSlot(WheelSlots.SLOT_GREEN_TOP_150)).isEqualTo(150)
        assertThat(state.getMaxPointsPerSlot(WheelSlots.SLOT_GREEN_200)).isEqualTo(200)
    }

    @Test
    fun `slot prioritary order matches the canary switch`() {
        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_GREEN_50)).isEqualTo(0)
        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_RED_50)).isEqualTo(0)
        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_BLUE_50)).isEqualTo(0)
        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_PURPLE_50)).isEqualTo(0)

        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_GREEN_TOP_75)).isEqualTo(1)
        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_BLUE_BOTTOM_75)).isEqualTo(1)

        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_GREEN_TOP_100)).isEqualTo(2)
        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_RED_MIDDLE_100)).isEqualTo(2)

        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_GREEN_TOP_150)).isEqualTo(3)
        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_PURPLE_BOTTOM_150)).isEqualTo(3)

        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_GREEN_200)).isEqualTo(4)
        assertThat(wheel.getSlotPrioritaryOrder(WheelSlots.SLOT_BLUE_200)).isEqualTo(4)
    }

    @Test
    fun `vocation detection uses the cip ids`() {
        assertThat(wheel.isKnight(1)).isTrue()
        assertThat(wheel.isPaladin(2)).isTrue()
        assertThat(wheel.isSorcerer(3)).isTrue()
        assertThat(wheel.isDruid(4)).isTrue()
        assertThat(wheel.isMonk(5)).isTrue()
        assertThat(wheel.isKnight(4)).isFalse()
    }

    @Test
    fun `green 200 grants knight health mana and battle instinct at full`() {
        val state = PlayerWheel(
            IOWheel.VOCATION_KNIGHT_CIP,
            level = 250,
            pointsBySlot = mutableMapOf(WheelSlots.SLOT_GREEN_200 to 200),
        )
        val data = wheel.buildBonusData(state)

        assertThat(data.stats.health).isEqualTo(600)
        assertThat(data.stats.mana).isEqualTo(200)
        assertThat(data.instant.battleInstinct).isTrue()
    }

    @Test
    fun `green 50 grants knight capacity and a green vessel resonance at full`() {
        val state = PlayerWheel(
            IOWheel.VOCATION_KNIGHT_CIP,
            level = 100,
            pointsBySlot = mutableMapOf(WheelSlots.SLOT_GREEN_50 to 50),
        )
        val data = wheel.buildBonusData(state)

        assertThat(data.stats.capacity).isEqualTo(250)
        assertThat(data.unlockedVesselResonances[WheelGemAffinity.Green.value]).isEqualTo(1)
    }

    @Test
    fun `green top 150 grants mitigation per point and mana leech at full`() {
        val state = PlayerWheel(
            IOWheel.VOCATION_DRUID_CIP,
            level = 300,
            pointsBySlot = mutableMapOf(WheelSlots.SLOT_GREEN_TOP_150 to 150),
        )
        val data = wheel.buildBonusData(state)

        assertThat(data.mitigation).isCloseTo(4.5f, org.assertj.core.data.Offset.offset(0.0001f))
        assertThat(data.leech.manaLeech).isEqualTo(0.25)
    }

    @Test
    fun `unused points subtract allocated points`() {
        val state = PlayerWheel(
            IOWheel.VOCATION_KNIGHT_CIP,
            level = 250,
            pointsBySlot = mutableMapOf(
                WheelSlots.SLOT_GREEN_50 to 50,
                WheelSlots.SLOT_GREEN_TOP_75 to 75,
            ),
        )
        assertThat(state.getWheelPoints()).isEqualTo(200)
        assertThat(state.getUnusedPoints()).isEqualTo(75)
    }

    @Test
    fun `enum values preserve canary numbering`() {
        assertThat(WheelGemBasicModifier.Vocation_Mana_FireResistance.value).isEqualTo(33)
        assertThat(WheelGemBasicModifier.Vocation_Capacity.value).isEqualTo(48)
        assertThat(WheelSlots.entries.size).isEqualTo(36)
        assertThat(WheelStagePoints.THREE.value).isEqualTo(1000)
    }
}
