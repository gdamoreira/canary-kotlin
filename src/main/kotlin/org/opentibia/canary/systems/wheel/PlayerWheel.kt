package org.opentibia.canary.systems.wheel

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.config.FeatureFlags
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player

private val logger = KotlinLogging.logger {}

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
    /**
     * `Player &m_player` (player_wheel.hpp:494): the C++ constructor reference
     * `explicit PlayerWheel(Player &initPlayer)` (player_wheel.hpp:79) is
     * injected here per AGENTS.md 1.1. `null` on an unwired wheel means the
     * owner checks of [checkBlessingGroveHealingByTarget] and the sibling
     * `check<Spell>ByTarget` methods can never match (PARITY GAP).
     */
    private val player: Player? = null,
    /** `OTSYS_TIME()` (src/utils/tools.hpp) seam for the gift on-think timer. */
    private val otsysTimeMs: () -> Long = System::currentTimeMillis,
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

    // --- stat/stage/instant accumulators (player_wheel.hpp:487-491) -------------

    /** `PlayerWheel::m_stats` (player_wheel.hpp:487), indexed by `WheelStat_t`. */
    private val stats = IntArray(WheelStat.entries.size)

    /** `PlayerWheel::m_stages` (player_wheel.hpp:488), indexed by `WheelStage_t`. */
    private val stages = IntArray(WheelStage.entries.size)

    /** `PlayerWheel::m_majorStats` (player_wheel.hpp:489), indexed by `WheelMajor_t`. */
    private val majorStats = IntArray(WheelMajor.entries.size)

    /** `PlayerWheel::m_instant` (player_wheel.hpp:490), indexed by `WheelInstant_t`. */
    private val instant = BooleanArray(WheelInstant.entries.size)

    /** `PlayerWheel::m_onThink` (player_wheel.hpp:491), indexed by `WheelOnThink_t`. */
    private val onThink = LongArray(WheelOnThink.entries.size)

    /** `PlayerWheel::getStat` (player_wheel.cpp:3843). */
    fun getStat(type: WheelStat): Int = stats[type.value]

    /** `PlayerWheel::addStat` (player_wheel.cpp:3591): out-of-range adds are dropped. */
    fun addStat(type: WheelStat, value: Int) {
        if (type.value >= stats.size) {
            logger.error { "[PlayerWheel::addStat] Type ${type.value} is out of range, value $value" }
            return
        }
        stats[type.value] += value
    }

    /** `PlayerWheel::getStage(WheelStage_t)` (player_wheel.cpp:3813). */
    fun getStage(type: WheelStage): Int = stages[type.value]

    /** `PlayerWheel::setStage` (player_wheel.cpp:3546). */
    fun setStage(type: WheelStage, value: Int) {
        if (type.value >= stages.size) {
            logger.error { "[PlayerWheel::setStage] Type ${type.value} is out of range" }
            return
        }
        stages[type.value] = value
    }

    /** `PlayerWheel::getInstant(WheelInstant_t)` (player_wheel.cpp:3757). */
    fun getInstant(type: WheelInstant): Boolean = instant[type.value]

    /**
     * `PlayerWheel::getInstant(std::string_view)` (player_wheel.cpp:3901): names
     * resolve through the instant table first, then the stage table (the
     * `Ascetic`/`Spiritual Outburst` stages are absent from this mapping,
     * exactly like the C++ table).
     */
    fun getInstant(name: String): Boolean {
        INSTANT_MAPPING[name]?.let { return getInstant(it) }
        VALID_STAGE_MAPPING[name]?.let { return getStage(it) > 0 }
        return false
    }

    /** `PlayerWheel::getStage(std::string_view)` (player_wheel.cpp:3767). */
    fun getStage(name: String): Int {
        INSTANT_MAPPING[name]?.let { return if (getInstant(it)) 1 else 0 }
        STAGE_MAPPING[name]?.let { return getStage(it) }
        return 0
    }

    /** `PlayerWheel::setInstant` (player_wheel.cpp:3582). */
    private fun setInstant(type: WheelInstant, toggle: Boolean) {
        if (type.value >= instant.size) {
            logger.error { "[PlayerWheel::setInstant] Instant type ${type.value} is out of range" }
            return
        }
        instant[type.value] = toggle
    }

    /** `PlayerWheel::getMajorStat` (player_wheel.cpp:3823). */
    fun getMajorStat(type: WheelMajor): Int = majorStats[type.value]

    /** `PlayerWheel::setMajorStat` (player_wheel.cpp:3564). */
    fun setMajorStat(type: WheelMajor, value: Int) {
        if (type.value >= majorStats.size) {
            logger.error { "[PlayerWheel::setMajorStat] Type ${type.value} is out of range, value $value" }
            return
        }
        majorStats[type.value] = value
    }

    /** `PlayerWheel::getOnThinkTimer` (player_wheel.cpp:3890). */
    fun getOnThinkTimer(type: WheelOnThink): Long = onThink[type.value]

    /** `PlayerWheel::setOnThinkTimer` (player_wheel.cpp:3555). */
    fun setOnThinkTimer(type: WheelOnThink, time: Long) {
        if (type.value >= onThink.size) {
            logger.error { "[PlayerWheel::setOnThinkTimer] Type ${type.value} is out of range, value $time" }
            return
        }
        onThink[type.value] = time
    }

    // --- Gift of Life (player_wheel.cpp:3945-3996) --------------------------------

    /**
     * `PlayerWheel::m_player.getStorageValue(STORAGEVALUE_GIFT_OF_LIFE_COOLDOWN_WOD)`
     * storage (src/utils/const.hpp:37) relocated onto the wheel because the
     * Kotlin `Player` does not model the integer storage map yet (PARITY GAP).
     */
    private var giftOfCooldown: Int = 0

    /** `PlayerWheel::getGiftOfLifeTotalCooldown` (player_wheel.cpp:3945). */
    fun getGiftOfLifeTotalCooldown(): Int = when (getStage(WheelStage.GIFT_OF_LIFE)) {
        1 -> 1 * 60 * 60 * 30
        2 -> 1 * 60 * 60 * 20
        3 -> 1 * 60 * 60 * 10
        else -> 0
    }

    /** `PlayerWheel::getGiftOfLifeValue` (player_wheel.cpp:3958). */
    fun getGiftOfLifeValue(): Int = when (getStage(WheelStage.GIFT_OF_LIFE)) {
        1 -> 20
        2 -> 25
        3 -> 30
        else -> 0
    }

    /** `PlayerWheel::getGiftOfCooldown` (player_wheel.cpp:3972). */
    fun getGiftOfCooldown(): Int = if (giftOfCooldown <= 0) 0 else giftOfCooldown

    /** `PlayerWheel::setGiftOfCooldown` (player_wheel.cpp:3979). */
    fun setGiftOfCooldown(value: Int, isOnThink: Boolean) {
        giftOfCooldown = value
        if (!isOnThink) {
            setOnThinkTimer(WheelOnThink.GIFT_OF_LIFE, otsysTimeMs() + 1000)
        }
    }

    /** `PlayerWheel::decreaseGiftOfCooldown` (player_wheel.cpp:3986). */
    fun decreaseGiftOfCooldown(value: Int) {
        val cooldown = getGiftOfCooldown() - value
        if (cooldown <= 0) {
            setOnThinkTimer(WheelOnThink.GIFT_OF_LIFE, otsysTimeMs() + 3_600_000)
            return
        }
        setOnThinkTimer(WheelOnThink.GIFT_OF_LIFE, otsysTimeMs() + value * 1000L)
        setGiftOfCooldown(cooldown, true)
    }

    /**
     * `PlayerWheel::setSpellInstant` (player_wheel.cpp:3609): stage-named
     * entries increment/reset their stage, instant-named entries toggle their
     * bit and clear the dependent major stats, mirroring every C++ branch.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun setSpellInstant(name: String, value: Boolean) {
        when (name) {
            "Ascetic" -> setStageValue(WheelStage.ASCETIC, value)
            "Spiritual Outburst" -> setStageValue(WheelStage.SPIRITUAL_OUTBURST, value)
            "Sanctuary" -> setInstant(WheelInstant.SANCTUARY, value)
            "Guiding Presence" -> setInstant(WheelInstant.GUIDING_PRESENCE, value)
            "Battle Instinct" -> {
                setInstant(WheelInstant.BATTLE_INSTINCT, value)
                if (!getInstant(WheelInstant.BATTLE_INSTINCT)) {
                    setMajorStat(WheelMajor.SHIELD, 0)
                    setMajorStat(WheelMajor.MELEE, 0)
                }
            }
            "Battle Healing" -> setInstant(WheelInstant.BATTLE_HEALING, value)
            "Positional Tactics" -> {
                setInstant(WheelInstant.POSITIONAL_TACTICS, value)
                if (!getInstant(WheelInstant.POSITIONAL_TACTICS)) {
                    setMajorStat(WheelMajor.MAGIC, 0)
                    setMajorStat(WheelMajor.HOLY_RESISTANCE, 0)
                }
            }
            "Ballistic Mastery" -> {
                setInstant(WheelInstant.BALLISTIC_MASTERY, value)
                if (!getInstant(WheelInstant.BALLISTIC_MASTERY)) {
                    setMajorStat(WheelMajor.CRITICAL_DMG, 0)
                    setMajorStat(WheelMajor.PHYSICAL_DMG, 0)
                    setMajorStat(WheelMajor.HOLY_DMG, 0)
                }
            }
            "Healing Link" -> setInstant(WheelInstant.HEALING_LINK, value)
            "Runic Mastery" -> setInstant(WheelInstant.RUNIC_MASTERY, value)
            "Focus Mastery" -> {
                setInstant(WheelInstant.FOCUS_MASTERY, value)
                if (!getInstant(WheelInstant.FOCUS_MASTERY)) {
                    setOnThinkTimer(WheelOnThink.FOCUS_MASTERY, 0)
                }
            }
            "Beam Mastery" -> setStageValue(WheelStage.BEAM_MASTERY, value)
            "Combat Mastery" -> setStageValue(WheelStage.COMBAT_MASTERY, value)
            "Gift of Life" -> setStageValue(WheelStage.GIFT_OF_LIFE, value)
            "Blessing of the Grove" -> setStageValue(WheelStage.BLESSING_OF_THE_GROVE, value)
            "Drain Body" -> setStageValue(WheelStage.DRAIN_BODY, value)
            "Divine Empowerment" -> setStageValue(WheelStage.DIVINE_EMPOWERMENT, value)
            "Divine Grenade" -> setStageValue(WheelStage.DIVINE_GRENADE, value)
            "Twin Burst" -> setStageValue(WheelStage.TWIN_BURST, value)
            "Executioner's Throw" -> setStageValue(WheelStage.EXECUTIONERS_THROW, value)
            "Avatar of Light" -> setStageValue(WheelStage.AVATAR_OF_LIGHT, value)
            "Avatar of Nature" -> setStageValue(WheelStage.AVATAR_OF_NATURE, value)
            "Avatar of Steel" -> setStageValue(WheelStage.AVATAR_OF_STEEL, value)
            "Avatar of Storm" -> setStageValue(WheelStage.AVATAR_OF_STORM, value)
            "Avatar of Balance" -> setStageValue(WheelStage.AVATAR_OF_BALANCE, value)
        }
    }

    private fun setStageValue(type: WheelStage, value: Boolean) {
        if (value) {
            setStage(type, getStage(type) + 1)
        } else {
            setStage(type, 0)
        }
    }

    // --- target-conditional combat bonuses ---------------------------------------

    /**
     * `PlayerWheel::checkBlessingGroveHealingByTarget` (player_wheel.cpp:3172):
     * the healing bonus scales with the target's health percentage and the
     * revelation stage; the owner is never a valid target.
     */
    fun checkBlessingGroveHealingByTarget(target: Creature?): Int {
        if (target == null || target === player) {
            return 0
        }

        var healingBonus = 0
        val stage = getStage(WheelStage.BLESSING_OF_THE_GROVE)
        val healthPercent = Math.round((target.health.toDouble() * 100) / target.maxHealth.toDouble()).toInt()
        if (healthPercent <= 30) {
            if (stage >= 3) {
                healingBonus = 24
            } else if (stage >= 2) {
                healingBonus = 18
            } else if (stage >= 1) {
                healingBonus = 12
            }
        } else if (healthPercent <= 60) {
            if (stage >= 3) {
                healingBonus = 12
            } else if (stage >= 2) {
                healingBonus = 9
            } else if (stage >= 1) {
                healingBonus = 6
            }
        }

        return healingBonus
    }

    /**
     * `PlayerWheel::checkTwinBurstByTarget` (player_wheel.cpp:3201): the damage
     * bonus only applies while the target is above 60% health.
     */
    fun checkTwinBurstByTarget(target: Creature?): Int {
        if (target == null || target === player) {
            return 0
        }

        var damageBonus = 0
        val stage = getStage(WheelStage.TWIN_BURST)
        val healthPercent = Math.round((target.health.toDouble() * 100) / target.maxHealth.toDouble()).toInt()
        if (healthPercent > 60) {
            if (stage >= 3) {
                damageBonus = 60
            } else if (stage >= 2) {
                damageBonus = 40
            } else if (stage >= 1) {
                damageBonus = 20
            }
        }

        return damageBonus
    }

    /**
     * `PlayerWheel::checkExecutionersThrow` (player_wheel.cpp:3222): the damage
     * bonus only applies while the target is at or below 30% health.
     */
    fun checkExecutionersThrow(target: Creature?): Int {
        if (target == null || target === player) {
            return 0
        }

        var damageBonus = 0
        val stage = getStage(WheelStage.EXECUTIONERS_THROW)
        val healthPercent = Math.round((target.health.toDouble() * 100) / target.maxHealth.toDouble()).toInt()
        if (healthPercent <= 30) {
            if (stage >= 3) {
                damageBonus = 150
            } else if (stage >= 2) {
                damageBonus = 125
            } else if (stage >= 1) {
                damageBonus = 100
            }
        }

        return damageBonus
    }

    /** `PlayerWheel::checkDivineGrenade` (player_wheel.cpp:3136). */
    fun checkDivineGrenade(target: Creature?): Int {
        if (target == null || target === player) {
            return 0
        }

        var damageBonus = 0
        val stage = getStage(WheelStage.DIVINE_GRENADE)
        if (stage >= 3) {
            damageBonus = 100
        } else if (stage >= 2) {
            damageBonus = 60
        } else if (stage >= 1) {
            damageBonus = 30
        }

        return damageBonus
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

        /** `instantMapping` of `PlayerWheel::getInstant(std::string_view)` (player_wheel.cpp:3905). */
        private val INSTANT_MAPPING = mapOf(
            "Battle Instinct" to WheelInstant.BATTLE_INSTINCT,
            "Battle Healing" to WheelInstant.BATTLE_HEALING,
            "Positional Tactics" to WheelInstant.POSITIONAL_TACTICS,
            "Ballistic Mastery" to WheelInstant.BALLISTIC_MASTERY,
            "Healing Link" to WheelInstant.HEALING_LINK,
            "Runic Mastery" to WheelInstant.RUNIC_MASTERY,
            "Focus Mastery" to WheelInstant.FOCUS_MASTERY,
            "Sanctuary" to WheelInstant.SANCTUARY,
            "Guiding Presence" to WheelInstant.GUIDING_PRESENCE,
        )

        /**
         * `stageMapping` of `PlayerWheel::getInstant(std::string_view)`
         * (player_wheel.cpp:3917): the `Ascetic`/`Spiritual Outburst` stages are
         * absent from this table in Canary.
         */
        private val VALID_STAGE_MAPPING = mapOf(
            "Beam Mastery" to WheelStage.BEAM_MASTERY,
            "Combat Mastery" to WheelStage.COMBAT_MASTERY,
            "Gift of Life" to WheelStage.GIFT_OF_LIFE,
            "Blessing of the Grove" to WheelStage.BLESSING_OF_THE_GROVE,
            "Drain Body" to WheelStage.DRAIN_BODY,
            "Divine Empowerment" to WheelStage.DIVINE_EMPOWERMENT,
            "Divine Grenade" to WheelStage.DIVINE_GRENADE,
            "Twin Burst" to WheelStage.TWIN_BURST,
            "Executioner's Throw" to WheelStage.EXECUTIONERS_THROW,
            "Avatar of Light" to WheelStage.AVATAR_OF_LIGHT,
            "Avatar of Nature" to WheelStage.AVATAR_OF_NATURE,
            "Avatar of Steel" to WheelStage.AVATAR_OF_STEEL,
            "Avatar of Storm" to WheelStage.AVATAR_OF_STORM,
            "Avatar of Balance" to WheelStage.AVATAR_OF_BALANCE,
        )

        /** `stageMapping` of `PlayerWheel::getStage(std::string_view)` (player_wheel.cpp:3784). */
        private val STAGE_MAPPING = VALID_STAGE_MAPPING + mapOf(
            "Ascetic" to WheelStage.ASCETIC,
            "Spiritual Outburst" to WheelStage.SPIRITUAL_OUTBURST,
        )
    }
}
