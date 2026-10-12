package org.opentibia.canary.combat

/**
 * Faithful port of Canary `enum CombatType_t` (uint8_t).
 *
 * Source: src/creatures/creatures_definitions.hpp:833-853
 */
enum class CombatType(val value: Int) {
    PHYSICALDAMAGE(0),
    FIREDAMAGE(1),
    EARTHDAMAGE(2),
    ENERGYDAMAGE(3),
    UNDEFINEDDAMAGE(4),
    LIFEDRAIN(5),
    MANADRAIN(6),
    HEALING(7),
    DROWNDAMAGE(8),
    ICEDAMAGE(9),
    HOLYDAMAGE(10),
    DEATHDAMAGE(11),
    AGONYDAMAGE(12),
    NEUTRALDAMAGE(13),

    /** `COMBAT_COUNT` sentinel used for array sizing in C++. */
    COUNT(14),

    /** Server read only: `COMBAT_NONE = 255`. */
    NONE(255),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun fromValue(value: Int): CombatType = byValue.getValue(value)
    }
}
