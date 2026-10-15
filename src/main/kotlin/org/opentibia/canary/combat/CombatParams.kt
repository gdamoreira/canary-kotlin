package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType

/**
 * `TileCallback` (src/creatures/combat/combat.hpp:42-49), reduced to the
 * resolved `onTileCombat(creature, tile)` Lua call
 * (`Combat::combatTileEffects`, combat.cpp:1242-1244). The callback scripts
 * stay Lua (AGENTS.md 1.4); the bridge resolves the call.
 */
fun interface CombatTileCallback {
    /** `TileCallback::onTileCombat(creature, tile)` (`combat.cpp:2077-2103`). */
    fun onTileCombat(caster: CombatActor?, tile: CombatTile)
}

/**
 * `TargetCallback` (src/creatures/combat/combat.hpp:51-58), reduced to the
 * resolved `onTargetCombat(creature, target)` Lua call fired per affected
 * target (`Combat::CombatFunc` combat.cpp:1630-1632, `Combat::doCombatHealth`
 * combat.cpp:1699-1701). The callback scripts stay Lua (AGENTS.md 1.4); the
 * bridge resolves the call.
 */
fun interface CombatTargetCallback {
    /** `TargetCallback::onTargetCombat(creature, target)` (`combat.cpp:2105-2132`). */
    fun onTargetCombat(caster: CombatActor?, target: CombatActor)
}

/**
 * The numeric operands Canary pushes after the `player` userdata into the
 * `onGetPlayerMinMaxValues` Lua call of `ValueCallback::getMinMaxValues`
 * (src/creatures/combat/combat.cpp:2001-2028).
 */
sealed interface CombatCallbackOperands {
    /**
     * `COMBAT_FORMULA_LEVELMAGIC` case (combat.cpp:2001-2006):
     * `(player->getLevel(), ValueCallback::getMagicLevelSkill(player, damage))`.
     */
    data class LevelMagic(val level: Int, val magicLevelSkill: Int) : CombatCallbackOperands

    /**
     * `COMBAT_FORMULA_SKILL` case (combat.cpp:2016-2028):
     * `(attackSkill, attackValue, attackFactor)` after
     * `Weapon::calculateSkillFormula` resolved them (`attackValue` stays the
     * C++ `7` default when no weapon resolved).
     */
    data class Skill(val attackSkill: Int, val attackValue: Int, val attackFactor: Float) : CombatCallbackOperands
}

/**
 * `ValueCallback` (`src/creatures/combat/combat.hpp:26-40`), reduced to the
 * resolved Lua `onGetPlayerMinMaxValues` call (combat.cpp:1982-2068).
 *
 * The scripts stay Lua (AGENTS.md 1.4); the bridge resolves the two returned
 * numbers. Canary pushes the player and the formula operands into the call;
 * [getMinMaxValues] receives those operands ([operands]) so the bridge can
 * forward them exactly.
 */
interface CombatValueCallback {
    /**
     * `ValueCallback::type` (combat.hpp:38), set by `Combat::setCallback`
     * (combat.cpp:672-688) independently of the `Combat` formula type; it
     * selects the operand shape and the secondary-damage split
     * (combat.cpp:2046-2063).
     */
    val type: FormulaType

    /**
     * The two `onGetPlayerMinMaxValues` numbers under the callback's formula
     * type, or null when the C++ `reserveScriptEnv`/`setCallbackId` early
     * return (combat.cpp:1983-1993) leaves the damage at 0.
     */
    fun getMinMaxValues(useCharges: Boolean, operands: CombatCallbackOperands): Pair<Int, Int>?
}

/**
 * Faithful port of Canary `struct CombatParams`
 * (src/creatures/combat/combat.hpp:82-110).
 *
 * All value fields keep the exact C++ names and defaults, including
 * `aggressive = true`, `blockedByArmor = false` and `blockedByShield = false`
 * (the gates that decide whether `Game::combatBlockHit` calls
 * `Creature::blockHit` with defense/armor checks).
 *
 * PARITY GAP: the `chainCallback` and `chainPickerCallback` callbacks are
 * dependency-injection seams into the chain system and are not part of this
 * dispatch layer; [tileCallback]/[targetCallback] carry the resolved
 * `onTileCombat`/`onTargetCombat` Lua calls and [valueCallback] the resolved
 * min/max values.
 */
class CombatParams(
    /** `conditionList` (combat.hpp:83). */
    var conditionList: MutableList<Condition> = mutableListOf(),

    /** `itemId` (combat.hpp:91); `params.itemId != 0` becomes the `field` flag (combat.cpp:1697). */
    var itemId: Int = 0,

    /** `dispelType` (combat.hpp:93). */
    var dispelType: ConditionType = ConditionType.CONDITION_NONE,

    /** `combatType` (combat.hpp:94). */
    var combatType: CombatType = CombatType.NONE,

    /** `origin` (combat.hpp:95), `ORIGIN_SPELL` in C++. */
    var origin: CombatOrigin = CombatOrigin.SPELL,

    /** `impactEffect` (combat.hpp:97), `CONST_ME_NONE` in C++. */
    var impactEffect: Int = 0,

    /** `soundImpactEffect` (combat.hpp:98), `SoundEffect_t::SILENCE` in C++. */
    var soundImpactEffect: Int = 0,

    /** `distanceEffect` (combat.hpp:100), `CONST_ANI_NONE` in C++. */
    var distanceEffect: Int = 0,

    /** `soundCastEffect` (combat.hpp:101), `SoundEffect_t::SILENCE` in C++. */
    var soundCastEffect: Int = 0,

    /** `blockedByArmor` (combat.hpp:103). */
    var blockedByArmor: Boolean = false,

    /** `blockedByShield` (combat.hpp:104). */
    var blockedByShield: Boolean = false,

    /** `targetCasterOrTopMost` (combat.hpp:105). */
    var targetCasterOrTopMost: Boolean = false,

    /** `aggressive` (combat.hpp:106). */
    var aggressive: Boolean = true,

    /** `useCharges` (combat.hpp:107). */
    var useCharges: Boolean = false,

    /** `chainEffect` (combat.hpp:109), `CONST_ME_NONE` in C++. */
    var chainEffect: Int = 0,

    /** `valueCallback` (combat.hpp:84); null when no callback was registered. */
    var valueCallback: CombatValueCallback? = null,

    /** `tileCallback` (combat.hpp:85); null when no callback was registered. */
    var tileCallback: CombatTileCallback? = null,

    /** `targetCallback` (combat.hpp:86); null when no callback was registered. */
    var targetCallback: CombatTargetCallback? = null,
)
