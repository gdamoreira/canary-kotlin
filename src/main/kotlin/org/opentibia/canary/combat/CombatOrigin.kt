package org.opentibia.canary.combat

/**
 * Faithful port of Canary `enum CombatOrigin` (uint8_t).
 *
 * Source: src/creatures/creatures_definitions.hpp:313-322
 */
enum class CombatOrigin(val value: Int) {
    NONE(0),
    CONDITION(1),
    SPELL(2),
    MELEE(3),
    RANGED(4),
    HARMONY(5),
    FIST(6),
    WEAPON_PROFICIENCY(7),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun fromValue(value: Int): CombatOrigin = byValue.getValue(value)
    }
}
