package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType

/**
 * Faithful port of Canary `struct CombatParams`
 * (src/creatures/combat/combat.hpp:82-110).
 *
 * All value fields keep the exact C++ names and defaults, including
 * `aggressive = true`, `blockedByArmor = false` and `blockedByShield = false`
 * (the gates that decide whether `Game::combatBlockHit` calls
 * `Creature::blockHit` with defense/armor checks).
 *
 * PARITY GAP: the five `std::unique_ptr` callbacks (`valueCallback`,
 * `tileCallback`, `targetCallback`, `chainCallback`, `chainPickerCallback`)
 * are dependency-injection seams into the map/spell/chain systems and are not
 * part of this dispatch layer.
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
)
