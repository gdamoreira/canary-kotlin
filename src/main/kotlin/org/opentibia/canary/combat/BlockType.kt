package org.opentibia.canary.combat

/**
 * Faithful port of Canary `enum BlockType_t` (uint8_t).
 *
 * Source: src/creatures/creatures_definitions.hpp:516-522
 */
enum class BlockType(val value: Int) {
    NONE(0),
    DEFENSE(1),
    ARMOR(2),
    IMMUNITY(3),
    DODGE(4),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun fromValue(value: Int): BlockType = byValue.getValue(value)
    }
}
