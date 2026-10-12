package org.opentibia.canary.creatures

/**
 * Faithful transcription of Canary's `Slots_t`
 * (src/creatures/creatures_definitions.hpp:373-389).
 */
enum class Slots_t(val value: Int) {
    CONST_SLOT_WHEREEVER(0),
    CONST_SLOT_HEAD(1),
    CONST_SLOT_NECKLACE(2),
    CONST_SLOT_BACKPACK(3),
    CONST_SLOT_ARMOR(4),
    CONST_SLOT_RIGHT(5),
    CONST_SLOT_LEFT(6),
    CONST_SLOT_LEGS(7),
    CONST_SLOT_FEET(8),
    CONST_SLOT_RING(9),
    CONST_SLOT_AMMO(10),
    CONST_SLOT_STORE_INBOX(11);

    companion object {
        /** `CONST_SLOT_FIRST = CONST_SLOT_HEAD`. */
        const val FIRST = 1

        /** `CONST_SLOT_LAST = CONST_SLOT_STORE_INBOX`. */
        const val LAST = 11

        fun fromValue(value: Int): Slots_t? = entries.firstOrNull { it.value == value }
    }
}
