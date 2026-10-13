package org.opentibia.canary.combat

import java.util.Random

/**
 * Port of the combat-related Lua-facing global bindings
 * (src/lua/functions/core/game/global_functions.cpp).
 *
 * Only the damage construction and `Combat` dispatch of the C++ bindings are
 * ported; Lua argument extraction itself stays in the Lua bridge (AGENTS.md
 * 1.4: Lua stays Lua).
 */
object GlobalFunctions {

    /**
     * `GlobalFunctions::luaDoTargetCombatHealth`
     * (src/lua/functions/core/game/global_functions.cpp:348-397).
     *
     * Builds the `normal_random(min, max)` primary damage of [combatType] and
     * dispatches it through [Combat.doCombatHealth]. Healing damage and
     * positive manadrain mark the params non-aggressive
     * (global_functions.cpp:385-388).
     */
    fun doTargetCombatHealth(
        caster: CombatTarget?,
        target: CombatTarget,
        combatType: CombatType,
        min: Int,
        max: Int,
        params: CombatParams = CombatParams(),
        random: Random,
        origin: CombatOrigin = CombatOrigin.SPELL,
        instantSpellName: String = "",
        runeSpellName: String = "",
    ) {
        params.combatType = combatType

        val damage = CombatDamage()
        damage.origin = origin
        damage.primaryType = combatType
        damage.primaryValue = normalRandom(min, max, random)
        damage.instantSpellName = instantSpellName
        damage.runeSpellName = runeSpellName

        if (combatType == CombatType.HEALING || (combatType == CombatType.MANADRAIN && damage.primaryValue > 0)) {
            params.aggressive = false
        }

        Combat.doCombatHealth(caster, target, damage, params, random)
    }

    /**
     * `GlobalFunctions::luaDoTargetCombatMana`
     * (src/lua/functions/core/game/global_functions.cpp:429-464).
     *
     * Negative [min] + [max] drains and marks the params aggressive; positive
     * values restore. Dispatches `COMBAT_MANADRAIN` through
     * [Combat.doCombatMana].
     */
    fun doTargetCombatMana(
        caster: CombatTarget?,
        target: CombatTarget,
        min: Int,
        max: Int,
        params: CombatParams = CombatParams(),
        random: Random,
        origin: CombatOrigin = CombatOrigin.SPELL,
        instantSpellName: String = "",
        runeSpellName: String = "",
    ) {
        params.aggressive = min + max < 0

        val damage = CombatDamage()
        damage.origin = origin
        damage.primaryType = CombatType.MANADRAIN
        damage.primaryValue = normalRandom(min, max, random)
        damage.instantSpellName = instantSpellName
        damage.runeSpellName = runeSpellName

        Combat.doCombatMana(caster, target, damage, params, random)
    }
}
