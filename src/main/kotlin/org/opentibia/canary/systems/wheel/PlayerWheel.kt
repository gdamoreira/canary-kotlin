package org.opentibia.canary.systems.wheel

/**
 * Mirror of the deterministic point-budget surface of Canary's `PlayerWheel`
 * (src/creatures/players/components/wheel/player_wheel.cpp). The full component
 * also owns gems, stages and protocol serialization; only the budget/allocation
 * primitives needed by the bonus engine are ported here. Remaining gaps are noted
 * in docs/Plan/050-wheel-of-destiny-engine.md.
 */
class PlayerWheel(
    val vocationCipId: Int,
    val level: Int,
    private val extraPoints: Int = 0,
    private val modsMaxGrade: Int = 0,
    val pointsBySlot: MutableMap<WheelSlots, Int> = mutableMapOf(),
) {
    fun getPointsBySlotType(slot: WheelSlots): Int = pointsBySlot[slot] ?: 0

    fun getMaxPointsPerSlot(slot: WheelSlots): Int = when (slot) {
        in FIFTY_SLOTS -> 50
        in SEVENTY_FIVE_SLOTS -> 75
        in HUNDRED_SLOTS -> 100
        in HUNDRED_FIFTY_SLOTS -> 150
        in TWO_HUNDRED_SLOTS -> 200
        else -> 0
    }

    fun getWheelPoints(includeExtraPoints: Boolean = true): Int {
        var totalPoints = maxOf(0, level - MIN_LEVEL_TO_START_COUNT_POINTS) * POINTS_PER_LEVEL
        if (includeExtraPoints) {
            totalPoints += extraPoints
        }
        return totalPoints
    }

    fun getUnusedPoints(): Int {
        var totalPoints = getWheelPoints()
        if (totalPoints == 0) {
            return 0
        }
        totalPoints += modsMaxGrade
        for (slot in WheelSlots.entries) {
            totalPoints -= getPointsBySlotType(slot)
        }
        return totalPoints
    }

    companion object {
        const val MIN_LEVEL_TO_START_COUNT_POINTS = 50
        const val POINTS_PER_LEVEL = 1

        private val FIFTY_SLOTS = setOf(
            WheelSlots.SLOT_BLUE_50, WheelSlots.SLOT_RED_50,
            WheelSlots.SLOT_PURPLE_50, WheelSlots.SLOT_GREEN_50,
        )
        private val SEVENTY_FIVE_SLOTS = setOf(
            WheelSlots.SLOT_GREEN_TOP_75, WheelSlots.SLOT_GREEN_BOTTOM_75,
            WheelSlots.SLOT_RED_TOP_75, WheelSlots.SLOT_RED_BOTTOM_75,
            WheelSlots.SLOT_PURPLE_TOP_75, WheelSlots.SLOT_PURPLE_BOTTOM_75,
            WheelSlots.SLOT_BLUE_TOP_75, WheelSlots.SLOT_BLUE_BOTTOM_75,
        )
        private val HUNDRED_SLOTS = setOf(
            WheelSlots.SLOT_GREEN_BOTTOM_100, WheelSlots.SLOT_GREEN_MIDDLE_100,
            WheelSlots.SLOT_GREEN_TOP_100, WheelSlots.SLOT_RED_BOTTOM_100,
            WheelSlots.SLOT_RED_MIDDLE_100, WheelSlots.SLOT_RED_TOP_100,
            WheelSlots.SLOT_PURPLE_BOTTOM_100, WheelSlots.SLOT_PURPLE_MIDDLE_100,
            WheelSlots.SLOT_PURPLE_TOP_100, WheelSlots.SLOT_BLUE_BOTTOM_100,
            WheelSlots.SLOT_BLUE_MIDDLE_100, WheelSlots.SLOT_BLUE_TOP_100,
        )
        private val HUNDRED_FIFTY_SLOTS = setOf(
            WheelSlots.SLOT_GREEN_TOP_150, WheelSlots.SLOT_GREEN_BOTTOM_150,
            WheelSlots.SLOT_RED_TOP_150, WheelSlots.SLOT_RED_BOTTOM_150,
            WheelSlots.SLOT_PURPLE_TOP_150, WheelSlots.SLOT_PURPLE_BOTTOM_150,
            WheelSlots.SLOT_BLUE_TOP_150, WheelSlots.SLOT_BLUE_BOTTOM_150,
        )
        private val TWO_HUNDRED_SLOTS = setOf(
            WheelSlots.SLOT_GREEN_200, WheelSlots.SLOT_RED_200,
            WheelSlots.SLOT_PURPLE_200, WheelSlots.SLOT_BLUE_200,
        )
    }
}
