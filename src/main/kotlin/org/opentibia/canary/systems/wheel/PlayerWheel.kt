package org.opentibia.canary.systems.wheel

import org.opentibia.canary.config.FeatureFlags

/**
 * Mirror of the deterministic point-budget surface of Canary's `PlayerWheel`
 * (src/creatures/players/components/wheel/player_wheel.cpp), plus the minimal
 * gem-state mutations consumed by [WheelApplication]. The full component also
 * owns stages, persistence, the player item/gold economy and protocol
 * serialization; those are not ported here. Remaining gaps are noted in
 * docs/Plan/050-wheel-of-destiny-engine.md.
 */
class PlayerWheel(
    val vocationCipId: Int,
    val level: Int,
    private val extraPoints: Int = 0,
    /**
     * `PlayerWheel::m_pointsPerLevel`, set from `WHEEL_POINTS_PER_LEVEL`
     * (src/creatures/players/components/wheel/player_wheel.cpp:378,
     * configmanager.cpp:385), default `1`.
     */
    private val pointsPerLevel: Int = FeatureFlags().wheelPointsPerLevel,
    private var modsMaxGrade: Int = 0,
    val pointsBySlot: MutableMap<WheelSlots, Int> = mutableMapOf(),
    val gems: WheelGemState = WheelGemState(),
    private val config: WheelConfig = WheelConfig(),
) {
    fun getPointsBySlotType(slot: WheelSlots): Int = pointsBySlot[slot] ?: 0

    /**
     * Mirror of `PlayerWheel::setPointsBySlotType`
     * (player_wheel.cpp:4016). The C++ guards an out-of-range array index;
     * the enum-typed map cannot go out of range here.
     */
    fun setPointsBySlotType(slot: WheelSlots, points: Int) {
        pointsBySlot[slot] = points
    }

    fun getGem(index: Int): PlayerWheelGem? = gems.revealedGems.getOrNull(index)

    /**
     * Mirror of `PlayerWheel::getGemRevealCost` (player_wheel.cpp:1114): the
     * configured `WHEEL_ATELIER_REVEAL_*_COST` for the gem quality.
     */
    fun getGemRevealCost(quality: WheelGemQuality): Long = when (quality) {
        WheelGemQuality.Lesser -> config.revealLesserCost.toLong()
        WheelGemQuality.Regular -> config.revealRegularCost.toLong()
        WheelGemQuality.Greater -> config.revealGreaterCost.toLong()
    }

    /**
     * Mirror of `PlayerWheel::revealGem` (player_wheel.cpp:1132) gem generation
     * and state effect; the gem item requirement, gold atelier cost and the
     * vocation supreme-modifier pool are applied by [WheelApplication] through
     * the [WheelPlayer] seam (see docs note / PARITY GAPS). The injected
     * [WheelRandom] replaces the C++ `uniform_random` global, matching
     * [WheelGems.selectBasicModifier2].
     */
    fun revealGem(quality: WheelGemQuality, random: WheelRandom): PlayerWheelGem {
        val affinity = WheelGemAffinity.entries[random.uniformRandom(0, WheelGemAffinity.entries.size - 1)]
        val basicModifier1 = WheelGems.basicSlot1Allowed[random.uniformRandom(0, WheelGems.basicSlot1Allowed.size - 1)]
        val basicModifier2 = if (quality.value >= WheelGemQuality.Regular.value) {
            WheelGems.selectBasicModifier2(basicModifier1, random)
        } else {
            null
        }
        val gem = PlayerWheelGem(
            affinity = affinity,
            quality = quality,
            basicModifier1 = basicModifier1,
            basicModifier2 = basicModifier2,
        )
        gems.revealedGems.add(gem)
        return gem
    }

    /**
     * Mirror of `PlayerWheel::destroyGem` (player_wheel.cpp:1212) state effects:
     * locked gems are refused, the gem is unset from any active vessel, then
     * removed from the revealed list. The fragment refund is credited by
     * [WheelApplication] through the [WheelPlayer] seam.
     */
    fun destroyGem(index: Int): Boolean {
        val gem = getGem(index) ?: return false
        if (gem.locked) {
            return false
        }
        for (affinity in WheelGemAffinity.entries) {
            if (gems.activeGems[affinity.value] === gem) {
                gems.activeGems[affinity.value] = null
            }
        }
        gems.revealedGems.removeAt(index)
        return true
    }

    /**
     * Mirror of `PlayerWheel::getGemRotateCost` (player_wheel.cpp:1096): the
     * configured `WHEEL_ATELIER_ROTATE_*_COST` for the gem quality.
     */
    fun getGemRotateCost(quality: WheelGemQuality): Long = when (quality) {
        WheelGemQuality.Lesser -> config.rotateLesserCost.toLong()
        WheelGemQuality.Regular -> config.rotateRegularCost.toLong()
        WheelGemQuality.Greater -> config.rotateGreaterCost.toLong()
    }

    /**
     * Mirror of `PlayerWheel::switchGemDomain` (player_wheel.cpp:1287) state
     * effect: rotated by `convertWheelGemAffinityToDomain`. Locked gems are
     * refused; the gold cost is charged by [WheelApplication] through the
     * [WheelPlayer] seam.
     */
    fun switchGemDomain(index: Int): Boolean {
        val gem = getGem(index) ?: return false
        if (gem.locked) {
            return false
        }
        gem.affinity = convertWheelGemAffinityToDomain(gem.affinity)
        return true
    }

    fun toggleGemLock(index: Int): Boolean {
        val gem = getGem(index) ?: return false
        gem.locked = !gem.locked
        return true
    }

    /**
     * Mirror of `PlayerWheel::improveGemGrade` (player_wheel.cpp:1448) grade
     * mutation, including the `m_modsMaxGrade` increment at grade 3. The
     * fragment/gold consumption is charged by [WheelApplication] through the
     * [WheelPlayer] seam.
     */
    fun improveGemGrade(fragmentType: WheelFragmentType, position: Int): Boolean {
        val grades = when (fragmentType) {
            WheelFragmentType.Lesser -> gems.basicGrades
            WheelFragmentType.Greater -> gems.supremeGrades
        }
        if (position !in grades.indices) {
            return false
        }
        val grade = grades[position]
        if (grade >= MAX_GEM_GRADE) {
            return false
        }
        grades[position] = grade + 1
        if (grade + 1 == MAX_GEM_GRADE) {
            modsMaxGrade++
        }
        return true
    }

    /**
     * Mirror of `PlayerWheel::getLesserGradeCost` (player_wheel.cpp:1508): the
     * `(gold, lesser fragments)` cost of reaching the target basic grade; an
     * unknown grade returns `(0, 0)` like the C++ empty tuple.
     */
    fun getLesserGradeCost(grade: Int): Pair<Long, Int> = when (grade) {
        1 -> 2_000_000L to 5
        2 -> 5_000_000L to 15
        3 -> 30_000_000L to 30
        else -> 0L to 0
    }

    /**
     * Mirror of `PlayerWheel::getGreaterGradeCost` (player_wheel.cpp:1521): the
     * `(gold, greater fragments)` cost of reaching the target supreme grade; an
     * unknown grade returns `(0, 0)` like the C++ empty tuple.
     */
    fun getGreaterGradeCost(grade: Int): Pair<Long, Int> = when (grade) {
        1 -> 5_000_000L to 5
        2 -> 12_000_000L to 15
        3 -> 75_000_000L to 30
        else -> 0L to 0
    }

    fun setActiveGem(affinity: WheelGemAffinity, index: Int): Boolean {
        val gem = getGem(index) ?: return false
        if (gem.affinity != affinity) {
            return false
        }
        gems.activeGems[affinity.value] = gem
        return true
    }

    fun removeActiveGem(affinity: WheelGemAffinity) {
        gems.activeGems[affinity.value] = null
    }

    fun getMaxPointsPerSlot(slot: WheelSlots): Int = when (slot) {
        in FIFTY_SLOTS -> 50
        in SEVENTY_FIVE_SLOTS -> 75
        in HUNDRED_SLOTS -> 100
        in HUNDRED_FIFTY_SLOTS -> 150
        in TWO_HUNDRED_SLOTS -> 200
        else -> 0
    }

    fun getWheelPoints(includeExtraPoints: Boolean = true): Int {
        var totalPoints = maxOf(0, level - MIN_LEVEL_TO_START_COUNT_POINTS) * pointsPerLevel
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
        const val MAX_GEM_GRADE = 3

        /**
         * Mirror of `convertWheelGemAffinityToDomain` (src/utils/tools.cpp:2190):
         * Green(0)->Red(1), Red(1)->Purple(3), Blue(2)->Green(0), Purple(3)->Blue(2).
         */
        fun convertWheelGemAffinityToDomain(affinity: WheelGemAffinity): WheelGemAffinity = when (affinity) {
            WheelGemAffinity.Green -> WheelGemAffinity.Red
            WheelGemAffinity.Red -> WheelGemAffinity.Purple
            WheelGemAffinity.Blue -> WheelGemAffinity.Green
            WheelGemAffinity.Purple -> WheelGemAffinity.Blue
        }

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
