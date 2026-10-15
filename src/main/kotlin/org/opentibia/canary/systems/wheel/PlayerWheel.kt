package org.opentibia.canary.systems.wheel

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.config.FeatureFlags
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.SLOTP_TWO_HAND
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.spells.Spells

private val logger = KotlinLogging.logger {}

/** `ITEM_DIVINE_EMPOWERMENT` (src/utils/utils_definitions.hpp:703). */
private const val ITEM_DIVINE_EMPOWERMENT = 40450

/** `struct PromotionScroll` (player_wheel.hpp:71-75). */
data class PromotionScroll(
    val itemId: Int,
    val name: String,
    val extraPoints: Int,
)

/**
 * `WheelOfDestinyPromotionScrolls` (player_wheel.cpp:365-371): the five
 * promotion scrolls `PlayerWheel::unlockScroll` can grant.
 */
private val WHEEL_OF_DESTINY_PROMOTION_SCROLLS = listOf(
    PromotionScroll(43946, "abridged", 3),
    PromotionScroll(43947, "basic", 5),
    PromotionScroll(43948, "revised", 9),
    PromotionScroll(43949, "extended", 13),
    PromotionScroll(43950, "advanced", 20),
)

/** Inert map access for the default empty nearby-monster snapshot. */
private object NullSpectatorMapAccess : SpectatorMapAccess {
    override fun getMapSector(x: Int, y: Int): SpectatorSector? = null
}

/**
 * Mirror of the deterministic point-budget surface of Canary's `PlayerWheel`
 * (src/creatures/players/components/wheel/player_wheel.cpp), plus the minimal
 * gem-state mutations consumed by [WheelApplication], the spell-upgrade ledger
 * ([getSpellUpgrade]/[upgradeSpell]/[downgradeSpell]/[resetUpgradedSpells]),
 * the revelation additional area/target/duration queries, the promotion-scroll
 * ledger ([unlockScroll]) and the [onThink] instant checks. Persistence, the
 * player item/gold economy and protocol serialization are not ported here.
 * Remaining gaps are noted in docs/development/feature-history.md.
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
    /**
     * `g_spells()` `isMonkShrineSpell` check of `upgradeSpell`
     * (player_wheel.cpp:3476). The registry is injected per AGENTS.md 1.1;
     * `isMonkShrineSpell` is a pure name check (spells.cpp:224-227), so the
     * default empty registry answers exactly like the process-wide one.
     */
    private val spells: Spells = Spells(),
    /**
     * `Spectators().find<Monster>(m_player.getPosition(), false, 1, 1, 1, 1, false)`
     * of `checkBattleInstinct`/`checkPositionalTactics` (player_wheel.cpp:2945,
     * 2969): `g_game().map` is injected here per AGENTS.md 1.1 as in
     * `Player::saySpell`; the default local snapshot is empty.
     */
    private val findNearbyMonsters: (Position) -> Spectators = { position ->
        Spectators(NullSpectatorMapAccess).find<Monster>(position, false, 1, 1, 1, 1, false)
    },
    /** `g_game().reloadCreature(m_player.getPlayer())` (player_wheel.cpp:3391). */
    private val reloadCreature: (Creature) -> Unit = {},
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

    // --- spell upgrades and revelation scrolls (player_wheel.hpp:516-521) --------

    /** `PlayerWheel::m_spellsSelected` (player_wheel.hpp:516). */
    private val spellsSelected = mutableMapOf<String, WheelSpellGrade>()

    /** `PlayerWheel::m_learnedSpellsSelected` (player_wheel.hpp:517). */
    private val learnedSpellsSelected = mutableListOf<String>()

    /**
     * `PlayerWheel::m_beamMasterySpells` (player_wheel.hpp:519): the beam-mastery
     * path is not ported, so this set is only cleared by
     * [resetUpgradedSpells] like the C++ (PARITY GAP).
     */
    private val beamMasterySpells = mutableSetOf<String>()

    /** `PlayerWheel::m_unlockedScrolls` (player_wheel.hpp:521). */
    private val unlockedScrolls = mutableListOf<PromotionScroll>()

    /** `PlayerWheel::getSpellUpgrade` (player_wheel.cpp:3863). */
    fun getSpellUpgrade(name: String): WheelSpellGrade = spellsSelected[name] ?: WheelSpellGrade.NONE

    /**
     * `PlayerWheel::upgradeSpell` (player_wheel.cpp:3475): the spell is learned
     * when the player does not know it and it is not a monk shrine spell, then
     * the selected grade steps `NONE -> REGULAR -> UPGRADED -> MAX` and stays at
     * `MAX`.
     */
    fun upgradeSpell(name: String) {
        if (player?.hasLearnedInstantSpell(name) != true && !spells.isMonkShrineSpell(name)) {
            learnedSpellsSelected.add(name)
            player?.learnInstantSpell(name)
        }
        when (spellsSelected[name]) {
            null, WheelSpellGrade.NONE -> spellsSelected[name] = WheelSpellGrade.REGULAR
            WheelSpellGrade.REGULAR -> spellsSelected[name] = WheelSpellGrade.UPGRADED
            WheelSpellGrade.UPGRADED -> spellsSelected[name] = WheelSpellGrade.MAX
            WheelSpellGrade.MAX -> Unit
        }
    }

    /**
     * `PlayerWheel::downgradeSpell` (player_wheel.cpp:3489): `REGULAR` (and
     * `NONE`) erase the selection; the learned spell is not forgotten.
     */
    fun downgradeSpell(name: String) {
        when (spellsSelected[name]) {
            null, WheelSpellGrade.NONE, WheelSpellGrade.REGULAR -> spellsSelected.remove(name)
            WheelSpellGrade.UPGRADED -> spellsSelected[name] = WheelSpellGrade.REGULAR
            WheelSpellGrade.MAX -> spellsSelected[name] = WheelSpellGrade.UPGRADED
        }
    }

    /**
     * `PlayerWheel::resetUpgradedSpells` (player_wheel.cpp:3456): every wheel
     * learned spell is forgotten, the selection/learned/beam sets are cleared,
     * and all major stats, stages and the focus-mastery timer reset to zero.
     */
    fun resetUpgradedSpells() {
        for (spell in learnedSpellsSelected) {
            val target = player
            if (target != null && target.hasLearnedInstantSpell(spell)) {
                target.forgetInstantSpell(spell)
            }
        }
        creaturesNearby = 0
        spellsSelected.clear()
        learnedSpellsSelected.clear()
        beamMasterySpells.clear()
        for (major in WheelMajor.entries) {
            setMajorStat(major, 0)
        }
        for (stage in WheelStage.entries) {
            setStage(stage, 0)
        }
        setOnThinkTimer(WheelOnThink.FOCUS_MASTERY, 0)
    }

    /**
     * `PlayerWheel::scrollAcquired` (player_wheel.cpp:1801): whether the
     * promotion scroll was already unlocked.
     */
    fun scrollAcquired(scrollName: String): Boolean = unlockedScrolls.any { it.name == scrollName }

    /**
     * `PlayerWheel::unlockScroll` (player_wheel.cpp:1809): an already acquired
     * scroll is refused, a known `WheelOfDestinyPromotionScrolls` name is
     * appended and accepted, anything else is refused.
     */
    fun unlockScroll(scrollName: String): Boolean {
        if (scrollAcquired(scrollName)) {
            return false
        }
        val scroll = WHEEL_OF_DESTINY_PROMOTION_SCROLLS.firstOrNull { it.name == scrollName } ?: return false
        unlockedScrolls.add(scroll)
        return true
    }

    // --- revelation spell queries (player_wheel.cpp:918-991) ---------------------

    /**
     * `PlayerWheel::getSpellAdditionalArea` (player_wheel.cpp:918): the selected
     * grade is scanned from its stage down to 1 for an `increase.area` entry of
     * the vocation table. A `MAX` stage (3) never matches because the table
     * holds only three grade rows (indices 0..2), exactly like the C++.
     */
    fun getSpellAdditionalArea(spellName: String): Boolean {
        val stage = getSpellUpgrade(spellName).value
        if (stage == 0) {
            return false
        }
        return when (vocationCipId) {
            IOWheel.VOCATION_KNIGHT_CIP -> checkSpellArea(WheelSpellTables.knight, spellName, stage)
            IOWheel.VOCATION_PALADIN_CIP -> checkSpellArea(WheelSpellTables.paladin, spellName, stage)
            IOWheel.VOCATION_DRUID_CIP -> checkSpellArea(WheelSpellTables.druid, spellName, stage)
            IOWheel.VOCATION_SORCERER_CIP -> checkSpellArea(WheelSpellTables.sorcerer, spellName, stage)
            IOWheel.VOCATION_MONK_CIP -> checkSpellArea(WheelSpellTables.monk, spellName, stage)
            else -> false
        }
    }

    /**
     * `PlayerWheel::getSpellAdditionalTarget` (player_wheel.cpp:944): the first
     * non-zero `increase.additionalTarget` from the selected grade downwards.
     */
    fun getSpellAdditionalTarget(spellName: String): Int {
        val stage = getSpellUpgrade(spellName).value
        if (stage == 0) {
            return 0
        }
        return when (vocationCipId) {
            IOWheel.VOCATION_KNIGHT_CIP -> checkSpellAdditionalTarget(WheelSpellTables.knight, spellName, stage)
            IOWheel.VOCATION_PALADIN_CIP -> checkSpellAdditionalTarget(WheelSpellTables.paladin, spellName, stage)
            IOWheel.VOCATION_DRUID_CIP -> checkSpellAdditionalTarget(WheelSpellTables.druid, spellName, stage)
            IOWheel.VOCATION_SORCERER_CIP -> checkSpellAdditionalTarget(WheelSpellTables.sorcerer, spellName, stage)
            IOWheel.VOCATION_MONK_CIP -> checkSpellAdditionalTarget(WheelSpellTables.monk, spellName, stage)
            else -> 0
        }
    }

    /**
     * `PlayerWheel::getSpellAdditionalDuration` (player_wheel.cpp:970): the
     * first positive `increase.duration` from the selected grade downwards.
     * Canary has no monk branch here (player_wheel.cpp:976-990), so monk
     * spells always answer `0`.
     */
    fun getSpellAdditionalDuration(spellName: String): Int {
        val stage = getSpellUpgrade(spellName).value
        if (stage == 0) {
            return 0
        }
        return when (vocationCipId) {
            IOWheel.VOCATION_KNIGHT_CIP -> checkSpellAdditionalDuration(WheelSpellTables.knight, spellName, stage)
            IOWheel.VOCATION_PALADIN_CIP -> checkSpellAdditionalDuration(WheelSpellTables.paladin, spellName, stage)
            IOWheel.VOCATION_DRUID_CIP -> checkSpellAdditionalDuration(WheelSpellTables.druid, spellName, stage)
            IOWheel.VOCATION_SORCERER_CIP -> checkSpellAdditionalDuration(WheelSpellTables.sorcerer, spellName, stage)
            else -> 0
        }
    }

    /** `checkSpellArea` (player_wheel.cpp:304-323). */
    private fun checkSpellArea(spellsTable: List<WheelSpellData>, spellName: String, stage: Int): Boolean {
        for (spellTable in spellsTable) {
            val size = spellTable.grade.size
            logger.debug { "spell area stage $stage, grade $size" }
            if (spellTable.name != spellName || stage >= size) {
                continue
            }
            for (grade in stage downTo 1) {
                if (spellTable.grade[grade].increase.area) {
                    logger.debug { "[checkSpellArea] spell with name $spellName, stage $stage, and grade $grade has increase area" }
                    return true
                }
            }
        }
        return false
    }

    /** `checkSpellAdditionalTarget` (player_wheel.cpp:325-343). */
    private fun checkSpellAdditionalTarget(spellsTable: List<WheelSpellData>, spellName: String, stage: Int): Int {
        for (spellTable in spellsTable) {
            val size = spellTable.grade.size
            logger.debug { "spell target stage $stage, grade $size" }
            if (spellTable.name != spellName || stage >= size) {
                continue
            }
            for (grade in stage downTo 1) {
                val additionalTarget = spellTable.grade[grade].increase.additionalTarget
                if (additionalTarget != 0) {
                    return additionalTarget
                }
            }
        }
        return 0
    }

    /** `checkSpellAdditionalDuration` (player_wheel.cpp:345-363). */
    private fun checkSpellAdditionalDuration(spellsTable: List<WheelSpellData>, spellName: String, stage: Int): Int {
        for (spellTable in spellsTable) {
            val size = spellTable.grade.size
            logger.debug { "spell duration stage $stage, grade $size" }
            if (spellTable.name != spellName || stage >= size) {
                continue
            }
            for (grade in stage downTo 1) {
                val duration = spellTable.grade[grade].increase.duration
                if (duration > 0) {
                    return duration
                }
            }
        }
        return 0
    }

    // --- onThink and its instant checks (player_wheel.cpp:2941-3421) -------------

    /** `PlayerWheel::m_creaturesNearby` (player_wheel.hpp:515). */
    private var creaturesNearby = 0

    /** `PlayerWheel::m_specializedMagic` (player_wheel.hpp:513), indexed by the combat enum value. */
    private val specializedMagic = IntArray(CombatType.COUNT.value)

    /** `PlayerWheel::getSpecializedMagic` (player_wheel.cpp:3833). */
    fun getSpecializedMagic(type: CombatType): Int {
        if (type.value >= specializedMagic.size) {
            logger.error { "[PlayerWheel::getSpecializedMagic] Instant type ${type.value}. Error message: out of range" }
            return 0
        }
        return specializedMagic[type.value]
    }

    /** `PlayerWheel::setSpecializedMagic` (player_wheel.cpp:3573). */
    fun setSpecializedMagic(type: CombatType, value: Int) {
        if (type.value >= specializedMagic.size) {
            logger.error { "[PlayerWheel::setSpecializedMagic] Type ${type.value} is out of range, value $value. Error message: out of range" }
            return
        }
        specializedMagic[type.value] = value
    }

    /**
     * `Spectators().find<Monster>(...).excludePlayerMaster().size()` of
     * `checkBattleInstinct`/`checkPositionalTactics` (player_wheel.cpp:2945,
     * 2969); an unwired player has no position and sees no monsters.
     */
    private fun nearbyMonsterCount(): Int {
        val position = player?.position ?: return 0
        return findNearbyMonsters(position).excludePlayerMaster().size()
    }

    /**
     * `PlayerWheel::checkBattleInstinct` (player_wheel.cpp:2941): five or more
     * nearby monsters grant melee `1 * (count - 4)` and shield
     * `6 * (count - 4)`; below five both major stats clear.
     */
    fun checkBattleInstinct(): Boolean {
        setOnThinkTimer(WheelOnThink.BATTLE_INSTINCT, otsysTimeMs() + 2000)
        var updateClient = false
        creaturesNearby = 0
        var monsters = nearbyMonsterCount()
        if (monsters >= 5) {
            creaturesNearby = monsters
            monsters -= 4
            val meleeSkill = 1 * monsters
            val shieldSkill = 6 * monsters
            if (getMajorStat(WheelMajor.MELEE) != meleeSkill || getMajorStat(WheelMajor.SHIELD) != shieldSkill) {
                setMajorStat(WheelMajor.MELEE, meleeSkill)
                setMajorStat(WheelMajor.SHIELD, shieldSkill)
                updateClient = true
            }
        } else if (getMajorStat(WheelMajor.MELEE) != 0 || getMajorStat(WheelMajor.SHIELD) != 0) {
            setMajorStat(WheelMajor.MELEE, 0)
            setMajorStat(WheelMajor.SHIELD, 0)
            updateClient = true
        }
        return updateClient
    }

    /**
     * `PlayerWheel::checkPositionalTactics` (player_wheel.cpp:2965): no nearby
     * monster grants distance 3 and clears the holy/healing specialized magic;
     * otherwise distance clears and both specialized magics become 3.
     */
    fun checkPositionalTactics(): Boolean {
        setOnThinkTimer(WheelOnThink.POSITIONAL_TACTICS, otsysTimeMs() + 2000)
        creaturesNearby = 0
        var updateClient = false
        val monsters = nearbyMonsterCount()
        val holyMagicSkill = 3
        val healingMagicSkill = 3
        val distanceSkill = 3
        if (monsters == 0) {
            creaturesNearby = monsters
            if (getMajorStat(WheelMajor.DISTANCE) != distanceSkill) {
                setMajorStat(WheelMajor.DISTANCE, distanceSkill)
                updateClient = true
            }
            if (getSpecializedMagic(CombatType.HOLYDAMAGE) != 0) {
                setSpecializedMagic(CombatType.HOLYDAMAGE, 0)
                updateClient = true
            }
            if (getSpecializedMagic(CombatType.HEALING) != 0) {
                setSpecializedMagic(CombatType.HEALING, 0)
                updateClient = true
            }
        } else {
            if (getMajorStat(WheelMajor.DISTANCE) != 0) {
                setMajorStat(WheelMajor.DISTANCE, 0)
                updateClient = true
            }
            if (getSpecializedMagic(CombatType.HOLYDAMAGE) != holyMagicSkill) {
                setSpecializedMagic(CombatType.HOLYDAMAGE, holyMagicSkill)
                updateClient = true
            }
            if (getSpecializedMagic(CombatType.HEALING) != healingMagicSkill) {
                setSpecializedMagic(CombatType.HEALING, healingMagicSkill)
                updateClient = true
            }
        }
        return updateClient
    }

    /**
     * `PlayerWheel::checkBallisticMastery` (player_wheel.cpp:3005): a bolt
     * weapon grants 1000 critical damage; an arrow weapon grants 2% physical
     * and 2% holy damage; anything else clears all three.
     */
    fun checkBallisticMastery(): Boolean {
        setOnThinkTimer(WheelOnThink.BALLISTIC_MASTERY, otsysTimeMs() + 2000)
        var updateClient = false
        val newCritical = 1000
        val newHolyBonus = 2
        val newPhysicalBonus = 2
        val item = player?.getWeapon()
        if (item != null && item.getAmmoType() == Ammo_t.AMMO_BOLT) {
            if (getMajorStat(WheelMajor.CRITICAL_DMG) != newCritical) {
                setMajorStat(WheelMajor.CRITICAL_DMG, newCritical)
                updateClient = true
            }
            if (getMajorStat(WheelMajor.PHYSICAL_DMG) != 0 || getMajorStat(WheelMajor.HOLY_DMG) != 0) {
                setMajorStat(WheelMajor.PHYSICAL_DMG, 0)
                setMajorStat(WheelMajor.HOLY_DMG, 0)
                updateClient = true
            }
        } else if (item != null && item.getAmmoType() == Ammo_t.AMMO_ARROW) {
            if (getMajorStat(WheelMajor.CRITICAL_DMG) != 0) {
                setMajorStat(WheelMajor.CRITICAL_DMG, 0)
                updateClient = true
            }
            if (getMajorStat(WheelMajor.PHYSICAL_DMG) != newPhysicalBonus || getMajorStat(WheelMajor.HOLY_DMG) != newHolyBonus) {
                setMajorStat(WheelMajor.PHYSICAL_DMG, newPhysicalBonus)
                setMajorStat(WheelMajor.HOLY_DMG, newHolyBonus)
                updateClient = true
            }
        } else {
            if (getMajorStat(WheelMajor.CRITICAL_DMG) != 0) {
                setMajorStat(WheelMajor.CRITICAL_DMG, 0)
                updateClient = true
            }
            if (getMajorStat(WheelMajor.PHYSICAL_DMG) != 0 || getMajorStat(WheelMajor.HOLY_DMG) != 0) {
                setMajorStat(WheelMajor.PHYSICAL_DMG, 0)
                setMajorStat(WheelMajor.HOLY_DMG, 0)
                updateClient = true
            }
        }
        return updateClient
    }

    /**
     * `PlayerWheel::checkCombatMastery` (player_wheel.cpp:3048): a two-handed
     * weapon grants critical damage by stage and clears defense; otherwise the
     * defense shield skill scales by stage and critical damage clears.
     */
    fun checkCombatMastery(): Boolean {
        setOnThinkTimer(WheelOnThink.COMBAT_MASTERY, otsysTimeMs() + 2000)
        var updateClient = false
        val stage = getStage(WheelStage.COMBAT_MASTERY)
        val item = player?.getWeapon()
        if (item != null && (item.getSlotPosition() and SLOTP_TWO_HAND) != 0) {
            var criticalSkill = 0
            if (stage >= 3) {
                criticalSkill = 1200
            } else if (stage >= 2) {
                criticalSkill = 800
            } else if (stage >= 1) {
                criticalSkill = 400
            }
            if (getMajorStat(WheelMajor.CRITICAL_DMG_2) != criticalSkill) {
                setMajorStat(WheelMajor.CRITICAL_DMG_2, criticalSkill)
                updateClient = true
            }
            if (getMajorStat(WheelMajor.DEFENSE) != 0) {
                setMajorStat(WheelMajor.DEFENSE, 0)
                updateClient = true
            }
        } else {
            if (getMajorStat(WheelMajor.CRITICAL_DMG_2) != 0) {
                setMajorStat(WheelMajor.CRITICAL_DMG_2, 0)
                updateClient = true
            }
            if (getMajorStat(WheelMajor.DEFENSE) == 0) {
                var shieldSkill = 0
                if (stage >= 3) {
                    shieldSkill = 30
                } else if (stage >= 2) {
                    shieldSkill = 20
                } else if (stage >= 1) {
                    shieldSkill = 10
                }
                setMajorStat(WheelMajor.DEFENSE, shieldSkill)
                updateClient = true
            }
        }
        return updateClient
    }

    /**
     * `PlayerWheel::checkDivineEmpowerment` (player_wheel.cpp:3094): an
     * `ITEM_DIVINE_EMPOWERMENT` owned by the player on the current tile grants
     * 8/10/12 damage by stage.
     */
    fun checkDivineEmpowerment(): Boolean {
        var updateClient = false
        setOnThinkTimer(WheelOnThink.DIVINE_EMPOWERMENT, otsysTimeMs() + 1000)

        val target = player ?: return updateClient
        val items = target.getTile()?.items ?: return updateClient

        var damageBonus = 0
        var isOwner = false
        for (item in items) {
            if (item.id == ITEM_DIVINE_EMPOWERMENT && item.isOwner(target.getGUID())) {
                isOwner = true
                break
            }
        }

        if (isOwner) {
            val stage = getStage(WheelStage.DIVINE_EMPOWERMENT)
            if (stage >= 3) {
                damageBonus = 12
            } else if (stage >= 2) {
                damageBonus = 10
            } else if (stage >= 1) {
                damageBonus = 8
            }
        }

        if (damageBonus != getMajorStat(WheelMajor.DAMAGE)) {
            setMajorStat(WheelMajor.DAMAGE, damageBonus)
            updateClient = true
        }
        return updateClient
    }

    /**
     * `PlayerWheel::onThink` (player_wheel.cpp:3369): decrements the gift of
     * life cooldown on its timer, clears every major stat when the player is
     * idle (then returns unless `force`), and runs the timer-gated instant
     * checks.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onThink(force: Boolean = false) {
        var updateClient = false
        creaturesNearby = 0
        // Gift of life (Cooldown)
        if (getGiftOfCooldown() > 0 && getOnThinkTimer(WheelOnThink.GIFT_OF_LIFE) <= otsysTimeMs()) {
            decreaseGiftOfCooldown(1)
        }
        val inFight = player?.hasCondition(ConditionType.CONDITION_INFIGHT) == true
        val protectionZone = player?.getZoneType() == ZoneType.ZONE_PROTECTION
        if (!inFight || protectionZone || (
                !getInstant("Battle Instinct") && !getInstant("Positional Tactics") &&
                    !getInstant("Ballistic Mastery") && !getInstant("Gift of Life") &&
                    !getInstant("Combat Mastery") && !getInstant("Divine Empowerment") &&
                    getGiftOfCooldown() == 0
                )
        ) {
            var mustReset = false
            for (major in WheelMajor.entries) {
                if (getMajorStat(major) != 0) {
                    mustReset = true
                    break
                }
            }

            if (mustReset) {
                for (major in WheelMajor.entries) {
                    setMajorStat(major, 0)
                }
                player?.let { target ->
                    target.protocolSink?.sendSkills(target)
                    target.protocolSink?.sendStats(target)
                    reloadCreature(target)
                }
            }
            if (!force) {
                return
            }
        }
        // Battle Instinct
        if (getInstant("Battle Instinct") &&
            (force || getOnThinkTimer(WheelOnThink.BATTLE_INSTINCT) < otsysTimeMs()) &&
            checkBattleInstinct()
        ) {
            updateClient = true
        }
        // Positional Tactics
        if (getInstant("Positional Tactics") &&
            (force || getOnThinkTimer(WheelOnThink.POSITIONAL_TACTICS) < otsysTimeMs()) &&
            checkPositionalTactics()
        ) {
            updateClient = true
        }
        // Ballistic Mastery
        if (getInstant("Ballistic Mastery") &&
            (force || getOnThinkTimer(WheelOnThink.BALLISTIC_MASTERY) < otsysTimeMs()) &&
            checkBallisticMastery()
        ) {
            updateClient = true
        }
        // Combat Mastery
        if (getInstant("Combat Mastery") &&
            (force || getOnThinkTimer(WheelOnThink.COMBAT_MASTERY) < otsysTimeMs()) &&
            checkCombatMastery()
        ) {
            updateClient = true
        }
        // Divine Empowerment
        if (getInstant("Divine Empowerment") &&
            (force || getOnThinkTimer(WheelOnThink.DIVINE_EMPOWERMENT) < otsysTimeMs()) &&
            checkDivineEmpowerment()
        ) {
            updateClient = true
        }
        if (updateClient) {
            player?.let { target ->
                target.protocolSink?.sendSkills(target)
                target.protocolSink?.sendStats(target)
            }
        }
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
