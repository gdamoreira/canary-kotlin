package org.opentibia.canary.combat

/**
 * Faithful port of Canary `enum formulaType_t`.
 *
 * Source: src/creatures/creatures_definitions.hpp:290-295
 */
enum class FormulaType(val value: Int) {
    UNDEFINED(0),
    LEVELMAGIC(1),
    SKILL(2),
    DAMAGE(3),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun fromValue(value: Int): FormulaType = byValue.getValue(value)
    }
}
