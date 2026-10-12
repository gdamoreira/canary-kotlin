package org.opentibia.canary.combat.condition

/**
 * Value mirrors of the Canary structures/enums the remaining conditions use.
 *
 * They are 1:1 transcriptions of the C++ definitions (field order, defaults and
 * numeric values) because those values cross the creature condition storage, the
 * wire protocol and the Lua bridge.
 */

/**
 * `Outfit_t` (`src/creatures/creatures_definitions.hpp:1832-1850`).
 *
 * Mutable fields mirror the C++ aggregate: `ConditionOutfit` assigns individual
 * members, so the Kotlin mirror must stay mutable (a `copy()` is used wherever
 * Canary performs-by-value assignment).
 */
data class Outfit(
    var lookType: Int = 0,
    var lookTypeEx: Int = 0,
    var lookMount: Int = 0,
    var lookHead: Int = 0,
    var lookBody: Int = 0,
    var lookLegs: Int = 0,
    var lookFeet: Int = 0,
    var lookAddons: Int = 0,
    var lookMountHead: Int = 0,
    var lookMountBody: Int = 0,
    var lookMountLegs: Int = 0,
    var lookMountFeet: Int = 0,
    var lookFamiliarsType: Int = 0,
    var lookWing: Int = 0,
    var lookAura: Int = 0,
    var lookEffect: Int = 0,
    var lookShader: Int = 0,
)

/** `LightInfo` (`src/creatures/creatures_definitions.hpp:1717-1723`). */
data class LightInfo(
    var level: Int = 0,
    var color: Int = 215,
)

/** `MonkData_t` (`src/creatures/creatures_definitions.hpp:30-34`). */
enum class MonkData(val value: Int) {
    Harmony(0x00),
    Serenity(0x01),
    Virtue(0x02),
}

/** `SpellGroup_t` (`src/creatures/creatures_definitions.hpp:761-774`). */
enum class SpellGroup(val value: Int) {
    SPELLGROUP_NONE(0),
    SPELLGROUP_ATTACK(1),
    SPELLGROUP_HEALING(2),
    SPELLGROUP_SUPPORT(3),
    SPELLGROUP_SPECIAL(4),
    SPELLGROUP_CONJURE(5),
    SPELLGROUP_CRIPPLING(6),
    SPELLGROUP_FOCUS(7),
    SPELLGROUP_ULTIMATESTRIKES(8),
    SPELLGROUP_BURSTS_OF_NATURE(9),
    SPELLGROUP_GREAT_BEAMS(10),
    SPELLGROUP_VIRTUE(11);

    companion object {
        /**
         * `static_cast<SpellGroup_t>(subId)` (`condition.cpp:2930`, `2951`).
         * Unknown values fall back to `SPELLGROUP_NONE`, mirroring the C++ cast
         * producing a value outside the enum's named range.
         */
        fun fromValue(value: Int): SpellGroup =
            entries.firstOrNull { it.value == value } ?: SPELLGROUP_NONE
    }
}
