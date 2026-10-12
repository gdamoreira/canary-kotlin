package org.opentibia.canary.systems.bestiary

import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType

/**
 * Charm combat procs, ported from `src/io/iobestiary.cpp`:
 * `parseCharmCombat`, `parseOffensiveCharmCombat`, `parseDefensiveCharmCombat`,
 * `parsePassiveCharmCombat`, `parseOffensiveCharmCombatDamage` and
 * `parseCharmCarnage`.
 *
 * Canary's `Player`/`Creature`/`Monster`/`Game` dependencies are replaced by the
 * small seams below (AGENTS.md 1.1: no global singletons). Every method takes a
 * [CombatDamage] and mutates it exactly like the C++ original; the actual
 * `Combat::doCombatHealth` dispatch is injected through [combatHealth].
 */
class CharmCombat(
    private val world: CharmCombatWorld = NoCharmCombatWorld,
    private val combatHealth: (
        caster: CharmCombatPlayer,
        target: CharmCombatCreature,
        damage: CombatDamage,
        params: CharmCombatParams,
    ) -> Unit = { _, _, _, _ -> },
) {
    /**
     * `IOBestiary::parseOffensiveCharmCombatDamage` (iobestiary.cpp:23-40).
     *
     * Sets the charm damage type/value and appends the `(name charm)` log
     * suffix when `Charm::logMessage` is set.
     */
    fun parseOffensiveCharmCombatDamage(
        charm: Charm,
        damage: Int,
        charmDamage: CombatDamage,
        charmParams: CharmCombatParams,
    ) {
        charmDamage.primaryType = charm.damageType
        charmDamage.primaryValue = damage
        charmDamage.extension = true
        if (charmDamage.exString.isNotEmpty()) {
            charmDamage.exString += ", "
        }
        if (charm.logMessage) {
            charmDamage.exString += "(${charm.name.lowercase()} charm)"
        }

        charmParams.impactEffect = charm.effect
        charmParams.combatType = charmDamage.primaryType
        charmParams.aggressive = true
        charmParams.soundImpactEffect = charm.soundImpactEffect
        charmParams.soundCastEffect = charm.soundCastEffect
    }

    /**
     * `IOBestiary::parseCharmCarnage` (iobestiary.cpp:42-49).
     *
     * Builds a fresh offensive charm damage payload and dispatches it.
     */
    fun parseCharmCarnage(
        charm: Charm,
        player: CharmCombatPlayer,
        target: CharmCombatCreature,
        damage: Int,
    ) {
        val charmDamage = CombatDamage()
        val charmParams = CharmCombatParams()
        parseOffensiveCharmCombatDamage(charm, damage, charmDamage, charmParams)
        combatHealth(player, target, charmDamage, charmParams)
    }

    /**
     * `IOBestiary::parseOffensiveCharmCombat` (iobestiary.cpp:51-120).
     *
     * Returns whether the caller should dispatch the resulting damage. Carnage
     * and cripple return `false` because they act on the world directly.
     *
     * PARITY GAP: Canary's `maxLevelsLimit` and `maxHealthLimit` are function
     * `static` locals; the mutable `maxLevelsLimit` therefore leaks `6` from a
     * previous carnage call into later offensive charms. The leak is a latent
     * bug and is not reproduced here: every invocation starts from the declared
     * `2`/`0.08` values.
     */
    fun parseOffensiveCharmCombat(
        charm: Charm,
        player: CharmCombatPlayer,
        target: CharmCombatCreature,
        charmDamage: CombatDamage,
        charmParams: CharmCombatParams,
    ): Boolean {
        var maxLevelsLimit = MAX_LEVELS_LIMIT
        var value: Int
        val targetPosition = target.position
        val monster = target.monster

        when (charm.id) {
            CharmRune.CHARM_WOUND,
            CharmRune.CHARM_ENFLAME,
            CharmRune.CHARM_POISON,
            CharmRune.CHARM_FREEZE,
            CharmRune.CHARM_ZAP,
            CharmRune.CHARM_CURSE,
            CharmRune.CHARM_DIVINE,
            -> value = minOf(
                ceilToInt(player.level * maxLevelsLimit.toDouble()),
                ceilToInt(target.maxHealth * (charm.percent / 100.0)),
            )

            CharmRune.CHARM_CARNAGE -> {
                if (monster == null || !monster.isDead()) {
                    return false
                }

                maxLevelsLimit = CARNAGE_LEVELS_LIMIT
                value = target.maxHealth

                for ((dx, dy) in CARNAGE_OFFSETS) {
                    val damagePosition = CharmPosition(targetPosition.x + dx, targetPosition.y + dy, targetPosition.z)
                    if (!world.tileExists(damagePosition)) {
                        continue
                    }

                    world.addMagicEffect(damagePosition, CONST_ME_DRAWBLOOD)

                    val topCreature = world.topMonsterAt(damagePosition) ?: continue
                    val damage = minOf(
                        ceilToInt(value * (charm.percent / 100.0)),
                        player.level * maxLevelsLimit,
                    )
                    parseCharmCarnage(charm, player, topCreature, -damage)
                }
                return false
            }

            CharmRune.CHARM_OVERPOWER -> value = minOf(
                ceilToInt(target.maxHealth * MAX_HEALTH_LIMIT),
                ceilToInt(player.maxHealth * (charm.percent / 100.0)),
            )

            CharmRune.CHARM_OVERFLUX -> value = minOf(
                ceilToInt(target.maxHealth * MAX_HEALTH_LIMIT),
                ceilToInt(player.maxMana * (charm.percent / 100.0)),
            )

            CharmRune.CHARM_CRIPPLE -> {
                val cripple = Condition.createCondition(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_PARALYZE,
                    10000,
                    0,
                ) as ConditionSpeed
                cripple.setFormulaVars(-1f, 0f, -1f, 0f)
                target.addCondition(cripple)
                return false
            }

            else -> return false
        }

        parseOffensiveCharmCombatDamage(charm, -value, charmDamage, charmParams)
        return true
    }

    /**
     * `IOBestiary::parseDefensiveCharmCombat` (iobestiary.cpp:122-162).
     *
     * Parry returns `true` (dispatch the reflected damage); dodge, adrenaline
     * and numb act directly and return `false`.
     */
    fun parseDefensiveCharmCombat(
        charm: Charm,
        player: CharmCombatPlayer,
        target: CharmCombatCreature,
        realDamage: Int,
        checkArmor: Boolean,
        charmDamage: CombatDamage,
        charmParams: CharmCombatParams,
    ): Boolean {
        when (charm.id) {
            CharmRune.CHARM_PARRY -> {
                charmDamage.primaryType = CombatType.NEUTRALDAMAGE
                charmDamage.primaryValue = -realDamage
                charmDamage.extension = true
                if (charmDamage.exString.isNotEmpty()) {
                    charmDamage.exString += ", "
                }
                if (charm.logMessage) {
                    charmDamage.exString += "(${charm.name.lowercase()} charm)"
                }
                charmParams.aggressive = true
                charmParams.blockedByArmor = checkArmor
                return true
            }

            CharmRune.CHARM_DODGE -> {
                world.addMagicEffect(target.position, charm.effect)
            }

            CharmRune.CHARM_ADRENALINE -> {
                val adrenaline = Condition.createCondition(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_HASTE,
                    10000,
                    0,
                ) as ConditionSpeed
                adrenaline.setFormulaVars(2.5f, 40f, 2.5f, 40f)
                player.addCondition(adrenaline)
            }

            CharmRune.CHARM_NUMB -> {
                val numb = Condition.createCondition(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_PARALYZE,
                    10000,
                    0,
                ) as ConditionSpeed
                numb.setFormulaVars(-1f, 0f, -1f, 0f)
                target.addCondition(numb)
            }

            else -> Unit
        }

        return false
    }

    /**
     * `IOBestiary::parsePassiveCharmCombat` (iobestiary.cpp:164-189).
     *
     * Only fatal hold has a combat-time side effect; the remaining passive
     * charms are handled by their own call sites.
     */
    fun parsePassiveCharmCombat(
        charm: Charm,
        player: CharmCombatPlayer,
        target: CharmCombatCreature,
        value: Int,
        charmDamage: CombatDamage,
        charmParams: CharmCombatParams,
    ): Boolean {
        when (charm.id) {
            CharmRune.CHARM_BLESS,
            CharmRune.CHARM_GUT,
            CharmRune.CHARM_LOW,
            CharmRune.CHARM_SAVAGE,
            CharmRune.CHARM_SCAVENGE,
            CharmRune.CHARM_VAMP,
            CharmRune.CHARM_VOID,
            CharmRune.CHARM_VOIDINVERSION,
            -> Unit

            CharmRune.CHARM_FATAL -> target.monster?.setFatalHoldDuration(FATAL_HOLD_DURATION)

            else -> Unit
        }

        return false
    }

    /**
     * `IOBestiary::parseCharmCombat` (iobestiary.cpp:191-225).
     *
     * Always sends `Charm::cancelMessage`, then dispatches the accumulated
     * damage when the type handler asked for it.
     */
    fun parseCharmCombat(
        charm: Charm?,
        player: CharmCombatPlayer?,
        target: CharmCombatCreature?,
        realDamage: Int = 0,
        checkArmor: Boolean = false,
    ): Boolean {
        if (charm == null || player == null || target == null) {
            return false
        }

        val charmDamage = CombatDamage()
        val charmParams = CharmCombatParams()

        val callCombat = when (charm.type) {
            CharmType.CHARM_OFFENSIVE ->
                parseOffensiveCharmCombat(charm, player, target, charmDamage, charmParams)

            CharmType.CHARM_DEFENSIVE ->
                parseDefensiveCharmCombat(charm, player, target, realDamage, checkArmor, charmDamage, charmParams)

            CharmType.CHARM_PASSIVE ->
                parsePassiveCharmCombat(charm, player, target, realDamage, charmDamage, charmParams)

            CharmType.CHARM_UNDEFINED -> return false
        }

        if (charm.cancelMessage.isNotEmpty()) {
            player.sendCancelMessage(charm.cancelMessage)
        }

        if (callCombat) {
            combatHealth(player, target, charmDamage, charmParams)
        }

        return true
    }

    companion object {
        /** `CONST_ME_DRAWBLOOD` (src/utils/utils_definitions.hpp:61). */
        const val CONST_ME_DRAWBLOOD = 1

        private const val MAX_LEVELS_LIMIT = 2
        private const val CARNAGE_LEVELS_LIMIT = 6
        private const val MAX_HEALTH_LIMIT = 0.08
        private const val FATAL_HOLD_DURATION = 30000

        /** `offsets` (iobestiary.cpp:54). */
        private val CARNAGE_OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        private fun ceilToInt(value: Double): Int = Math.ceil(value).toInt()
    }
}

/** Canary `Position` subset used by the charm world lookups. */
data class CharmPosition(val x: Int, val y: Int, val z: Int)

/**
 * Canary `CombatParams` subset mutated by the charm procs
 * (src/creatures/combat/combat.hpp:82-110).
 */
class CharmCombatParams(
    var combatType: CombatType = CombatType.NONE,
    var impactEffect: Int = 0,
    var soundImpactEffect: Int = 0,
    var soundCastEffect: Int = 0,
    var aggressive: Boolean = false,
    var blockedByArmor: Boolean = false,
)

/** Creature seam for the charm procs (Canary `Creature`). */
interface CharmCombatCreature {
    val maxHealth: Int
    val position: CharmPosition
    val monster: CharmCombatMonster?

    fun isDead(): Boolean

    fun addCondition(condition: Condition)
}

/** Player seam for the charm procs (Canary `Player`). */
interface CharmCombatPlayer : CharmCombatCreature {
    val level: Int
    val maxMana: Int

    fun sendCancelMessage(message: String)
}

/** Monster seam for the charm procs (Canary `Monster`). */
interface CharmCombatMonster {
    fun isDead(): Boolean

    fun setFatalHoldDuration(duration: Int)
}

/** World seam replacing `g_game().map` / `g_game().addMagicEffect`. */
interface CharmCombatWorld {
    fun tileExists(position: CharmPosition): Boolean

    fun topMonsterAt(position: CharmPosition): CharmCombatCreature?

    fun addMagicEffect(position: CharmPosition, effect: Int)
}

/** Default no-op world used when a caller only exercises the damage math. */
private object NoCharmCombatWorld : CharmCombatWorld {
    override fun tileExists(position: CharmPosition): Boolean = false

    override fun topMonsterAt(position: CharmPosition): CharmCombatCreature? = null

    override fun addMagicEffect(position: CharmPosition, effect: Int) = Unit
}
