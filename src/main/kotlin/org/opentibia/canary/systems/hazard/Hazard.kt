package org.opentibia.canary.systems.hazard

import java.util.Random
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.config.FeatureFlags

/**
 * Engine core of the Gnomprona Hazard difficulty system.
 *
 * This is a faithful port of the C++ engine only. The Lua side
 * (`data/libs/systems/hazard.lua`, `data/scripts/eventcallbacks/monster/ondroploot_hazard.lua`)
 * owns hazard-zone placement, level tables and bonus loot rolls and stays Lua
 * (AGENTS.md 1.4).
 *
 * Sources:
 *  - Monster flags: src/creatures/monsters/monster.hpp:260-274,460-464,
 *    src/creatures/monsters/monster.cpp:3152-3190
 *  - Player points + attack parsers: src/creatures/players/player.cpp:5513-5647
 *  - Experience bonus: src/creatures/players/player.cpp:3641-3646
 *  - Combat dispatch: src/game/game.cpp:8193-8201
 *  - Config defaults: src/config/configmanager.cpp:153,180,277-288
 *  - Storage key: src/utils/const.hpp:34
 *  - RNG: src/utils/tools.cpp:466-475 (`normal_random`, normal(0.5, 0.25) in [0,1])
 */

/** Combat type ids used by the hazard path (src/creatures/creatures_definitions.hpp:841,852). */
object CombatType {
    const val COMBAT_HEALING = 7
    const val COMBAT_NONE = 255
}

/**
 * Canary `HAZARD_*` / `TOGGLE_HAZARDSYSTEM` configuration with the configured
 * defaults (src/config/configmanager.cpp:153,180,277-288).
 */
data class HazardConfig(
    val toggleHazardSystem: Boolean = true,
    val criticalChance: Int = 750,
    val criticalInterval: Int = 2000,
    val criticalMultiplier: Int = 25,
    val damageMultiplier: Int = 200,
    val defenseMultiplier: Int = 0,
    val dodgeMultiplier: Int = 85,
    val expBonusMultiplier: Double = 2.0,
    val lootBonusMultiplier: Int = 2,
    val podsDamage: Int = 5,
    val podsDropMultiplier: Int = 87,
    val podsTimeToDamage: Int = 2000,
    val podsTimeToSpawn: Int = 4000,
    val spawnPlunderMultiplier: Int = 25,
) {
    companion object {
        /**
         * Builds the hazard configuration from the Canary `HAZARD_*` /
         * `TOGGLE_HAZARDSYSTEM` feature flags (configmanager.cpp:153,180,277-288).
         * The `FeatureFlags` defaults match this type's defaults exactly.
         */
        fun fromFeatureFlags(flags: FeatureFlags): HazardConfig = HazardConfig(
            toggleHazardSystem = flags.toggleHazardSystem,
            criticalChance = flags.hazardCriticalChance,
            criticalInterval = flags.hazardCriticalInterval,
            criticalMultiplier = flags.hazardCriticalMultiplier,
            damageMultiplier = flags.hazardDamageMultiplier,
            defenseMultiplier = flags.hazardDefenseMultiplier,
            dodgeMultiplier = flags.hazardDodgeMultiplier,
            expBonusMultiplier = flags.hazardExpBonusMultiplier.toDouble(),
            lootBonusMultiplier = flags.hazardLootBonusMultiplier,
            podsDamage = flags.hazardPodsDamage,
            podsDropMultiplier = flags.hazardPodsDropMultiplier,
            podsTimeToDamage = flags.hazardPodsTimeToDamage,
            podsTimeToSpawn = flags.hazardPodsTimeToSpawn,
            spawnPlunderMultiplier = flags.hazardSpawnPlunderMultiplier,
        )
    }
}

/**
 * The subset of Canary `CombatDamage` (src/creatures/creatures_definitions.hpp:1720-1760)
 * that the hazard parsers read or mutate.
 */
class HazardDamage(
    var primaryType: Int = CombatType.COMBAT_NONE,
    var primaryValue: Int = 0,
    var secondaryType: Int = CombatType.COMBAT_NONE,
    var secondaryValue: Int = 0,
    var critical: Boolean = false,
    var extension: Boolean = false,
    var exString: String = "",
    var hazardDodge: Boolean = false,
)

/**
 * Self-contained mirror of the Monster hazard flags
 * (src/creatures/monsters/monster.hpp:460-464). Intentionally *not* an edit to
 * Monster.kt (task constraint).
 */
class MonsterHazard {
    private var hazard = false
    private var hazardCrit = false
    private var hazardDodge = false
    private var hazardDamageBoost = false
    private var hazardDefenseBoost = false

    // src/creatures/monsters/monster.cpp:3153-3190
    fun getHazard(): Boolean = hazard
    fun setHazard(value: Boolean) { hazard = value }

    fun getHazardSystemCrit(): Boolean = hazardCrit
    fun setHazardSystemCrit(value: Boolean) { hazardCrit = value }

    fun getHazardSystemDodge(): Boolean = hazardDodge
    fun setHazardSystemDodge(value: Boolean) { hazardDodge = value }

    fun getHazardSystemDamageBoost(): Boolean = hazardDamageBoost
    fun setHazardSystemDamageBoost(value: Boolean) { hazardDamageBoost = value }

    fun getHazardSystemDefenseBoost(): Boolean = hazardDefenseBoost
    fun setHazardSystemDefenseBoost(value: Boolean) { hazardDefenseBoost = value }
}

/**
 * Self-contained mirror of the Player hazard state and attack parsers
 * (src/creatures/players/player.cpp:5513-5647). Intentionally *not* an edit to
 * Player.kt (task constraint).
 *
 * Constructor seams replace Canary globals (AGENTS.md 1.1): `normalRandom`
 * replaces `normal_random`, `now` replaces `OTSYS_TIME`, and `partyPoints` /
 * `partyLeaderPoints` replace `m_party->getMembers()` / `m_party->getLeader()`.
 * Protocol side effects (`setIcon`/`removeIcon`) are out of engine scope.
 */
class PlayerHazard(
    private val config: HazardConfig = HazardConfig(),
    private val normalRandom: (min: Int, max: Int) -> Int = defaultNormalRandom(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val partyPoints: () -> List<Int> = { emptyList() },
    private val partyLeaderPoints: () -> Int? = { null },
) {
    /** `getStorageValue(STORAGEVALUE_HAZARDCOUNT)` default (Canary storage default is -1). */
    private var hazardCount: Int = -1

    private var lastHazardSystemCriticalHit: Long = 0L

    var reloadHazardSystemPointsCounter: Boolean = true
        private set

    fun getLastHazardSystemCriticalHit(): Long = lastHazardSystemCriticalHit

    // src/creatures/players/player.cpp:5628-5639
    fun setHazardSystemPoints(count: Int) {
        if (!config.toggleHazardSystem) {
            return
        }
        hazardCount = max(0, min(0xFFFF, count))
        reloadHazardSystemPointsCounter = true
    }

    // src/creatures/players/player.cpp:5641-5647
    fun getHazardSystemPoints(): Int {
        val points = hazardCount
        if (points <= 0) {
            return 0
        }
        return max(0, min(0xFFFF, points))
    }

    // src/creatures/players/player.cpp:5513-5570
    fun parseAttackRecvHazardSystem(damage: HazardDamage, monster: MonsterHazard) {
        if (!monster.getHazard()) {
            return
        }
        if (!config.toggleHazardSystem) {
            return
        }
        if (damage.primaryType == CombatType.COMBAT_HEALING) {
            return
        }

        val points = lowestPartyHazardPoints()
        if (points == 0) {
            return
        }

        var stage: Int
        val chance = normalRandom(1, 10000)
        val critChance = config.criticalChance
        // Critical chance
        if (monster.getHazardSystemCrit() &&
            (lastHazardSystemCriticalHit + config.criticalInterval) <= now() &&
            chance <= critChance &&
            !damage.critical
        ) {
            damage.critical = true
            damage.extension = true
            damage.exString = "(Hazard)"

            stage = toUShort((points - 1) * toUShort(config.criticalMultiplier))
            damage.primaryValue += ceil(damage.primaryValue.toDouble() * (5000 + stage) / 10000).toInt()
            damage.secondaryValue += ceil(damage.secondaryValue.toDouble() * (5000 + stage) / 10000).toInt()
            lastHazardSystemCriticalHit = now()
        }

        // Damage boost (Canary intentionally allows this on top of a hazard crit).
        if (monster.getHazardSystemDamageBoost()) {
            stage = toUShort(points * toUShort(config.damageMultiplier))
            if (stage != 0) {
                damage.extension = true
                damage.exString = "(Hazard)"
                damage.primaryValue += ceil(damage.primaryValue.toDouble() * stage / 10000).toInt()
                if (damage.secondaryValue != 0) {
                    damage.secondaryValue += ceil(damage.secondaryValue.toDouble() * stage / 10000).toInt()
                }
            }
        }
    }

    // src/creatures/players/player.cpp:5572-5624
    fun parseAttackDealtHazardSystem(damage: HazardDamage, monster: MonsterHazard) {
        if (!config.toggleHazardSystem) {
            return
        }
        if (!monster.getHazard()) {
            return
        }
        if (damage.primaryType == CombatType.COMBAT_HEALING) {
            return
        }

        val points = lowestPartyHazardPoints()
        if (points == 0) {
            return
        }

        // Dodge chance
        var stage: Int
        if (monster.getHazardSystemDodge()) {
            stage = toUShort(points * toUShort(config.dodgeMultiplier))
            val chance = normalRandom(1, 10000)
            if (chance <= stage) {
                damage.primaryValue = 0
                damage.secondaryValue = 0
                damage.hazardDodge = true
                return
            }
        }
        if (monster.getHazardSystemDefenseBoost()) {
            stage = toUShort(points * toUShort(config.defenseMultiplier))
            if (stage != 0) {
                damage.exString = "(hazard -${formatStage(stage)}%)"
                damage.primaryValue -= ceil(damage.primaryValue.toDouble() * stage / 10000).toInt()
                if (damage.secondaryValue != 0) {
                    damage.secondaryValue -= ceil(damage.secondaryValue.toDouble() * stage / 10000).toInt()
                }
            }
        }
    }

    // src/creatures/players/player.cpp:3641-3646
    fun hazardExperience(exp: Long, monster: MonsterHazard): Long {
        val points = getHazardSystemPoints()
        if (!monster.getHazard() || points <= 0) {
            return exp
        }
        val bonus = (exp * (1.75 * points * config.expBonusMultiplier)) / 100.0
        return exp + bonus.toLong()
    }

    // src/creatures/players/player.cpp:5526-5537
    private fun lowestPartyHazardPoints(): Int {
        var points = getHazardSystemPoints()
        for (memberPoints in partyPoints()) {
            if (memberPoints < points) {
                points = memberPoints
            }
        }
        val leaderPoints = partyLeaderPoints()
        if (leaderPoints != null && leaderPoints < points) {
            points = leaderPoints
        }
        return points
    }

    companion object {
        // src/utils/const.hpp:34
        const val STORAGEVALUE_HAZARDCOUNT = 112550

        /**
         * Faithful port of Canary `normal_random` (src/utils/tools.cpp:466-475):
         * `std::normal_distribution<float>(0.5f, 0.25f)` rejected outside [0,1],
         * then `min + lround(v * (max - min))`.
         */
        fun defaultNormalRandom(random: Random = Random()): (min: Int, max: Int) -> Int = { minNumber, maxNumber ->
            var v: Float
            do {
                v = 0.5f + random.nextGaussian().toFloat() * 0.25f
            } while (v < 0.0f || v > 1.0f)

            val a = min(minNumber, maxNumber)
            val b = max(minNumber, maxNumber)
            a + Math.round(v * (b - a))
        }

        private fun toUShort(value: Int): Int = value and 0xFFFF

        private fun formatStage(stage: Int): String {
            val percent = stage / 100.0
            return if (percent == percent.toLong().toDouble()) {
                percent.toLong().toString()
            } else {
                percent.toString()
            }
        }
    }
}

/**
 * Mirror of Canary `Game::handleHazardSystemAttack` (src/game/game.cpp:8193-8201).
 */
object HazardSystem {
    fun handleHazardSystemAttack(
        damage: HazardDamage,
        player: PlayerHazard,
        monster: MonsterHazard,
        isPlayerAttacker: Boolean,
    ) {
        if (damage.primaryValue != 0 && monster.getHazard()) {
            if (isPlayerAttacker) {
                player.parseAttackDealtHazardSystem(damage, monster)
            } else {
                player.parseAttackRecvHazardSystem(damage, monster)
            }
        }
    }

    /**
     * `Game::handleHazardSystemAttack` (src/game/game.cpp:8193-8201), connected
     * to the combat core's [CombatDamage].
     *
     * The hazard parsers operate on the plain-integer [HazardDamage] mirror, so
     * this overload crosses the seam in both directions: it snapshots the
     * relevant [CombatDamage] fields, runs the parser, then writes the mutated
     * values back. Damage types keep their numeric value, which is why
     * [CombatDamage.primaryType] can be compared with the hazard `COMBAT_*`
     * constants.
     */
    fun handleHazardSystemAttack(
        damage: CombatDamage,
        player: PlayerHazard,
        monster: MonsterHazard,
        isPlayerAttacker: Boolean,
    ) {
        if (damage.primaryValue == 0 || !monster.getHazard()) {
            return
        }

        val view = HazardDamage(
            primaryType = damage.primaryType.value,
            primaryValue = damage.primaryValue,
            secondaryType = damage.secondaryType.value,
            secondaryValue = damage.secondaryValue,
            critical = damage.critical,
            extension = damage.extension,
            exString = damage.exString,
            hazardDodge = damage.hazardDodge,
        )

        if (isPlayerAttacker) {
            player.parseAttackDealtHazardSystem(view, monster)
        } else {
            player.parseAttackRecvHazardSystem(view, monster)
        }

        damage.primaryValue = view.primaryValue
        damage.secondaryValue = view.secondaryValue
        damage.critical = view.critical
        damage.extension = view.extension
        damage.exString = view.exString
        damage.hazardDodge = view.hazardDodge
    }
}
