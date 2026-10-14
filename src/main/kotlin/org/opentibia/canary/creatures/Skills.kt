package org.opentibia.canary.creatures

/**
 * Faithful transcription of Canary's `skills_t`
 * (src/creatures/creatures_definitions.hpp:466-487).
 *
 * The Kotlin entries keep Canary's exact constant names and integer values so
 * that `value` maps 1:1 onto the C++ enum (including `SKILL_NONE = -1`).
 */
enum class skills_t(val value: Int) {
    SKILL_NONE(-1),
    SKILL_FIST(0),
    SKILL_CLUB(1),
    SKILL_SWORD(2),
    SKILL_AXE(3),
    SKILL_DISTANCE(4),
    SKILL_SHIELD(5),
    SKILL_FISHING(6),
    SKILL_CRITICAL_HIT_CHANCE(7),
    SKILL_CRITICAL_HIT_DAMAGE(8),
    SKILL_LIFE_LEECH_CHANCE(9),
    SKILL_LIFE_LEECH_AMOUNT(10),
    SKILL_MANA_LEECH_CHANCE(11),
    SKILL_MANA_LEECH_AMOUNT(12),
    SKILL_MAGLEVEL(13),
    SKILL_LEVEL(14);

    companion object {
        /** `SKILL_FIRST = SKILL_FIST`. */
        const val FIRST = 0

        /** `SKILL_LAST = SKILL_MANA_LEECH_AMOUNT`. */
        const val LAST = 12

        fun fromValue(value: Int): skills_t? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Mirror of Canary's `struct Skill` (src/creatures/creatures_definitions.hpp:1609-1613):
 * `uint64_t tries = 0; uint16_t level = 10; double percent = 0;`.
 */
class SkillState(
    var tries: Long = 0,
    var level: Int = 10,
    var percent: Double = 0.0,
)

/** `getSkillName` (`src/utils/tools.cpp:1022`). */
fun getSkillName(skill: skills_t): String = when (skill) {
    skills_t.SKILL_FIST -> "fist fighting"
    skills_t.SKILL_CLUB -> "club fighting"
    skills_t.SKILL_SWORD -> "sword fighting"
    skills_t.SKILL_AXE -> "axe fighting"
    skills_t.SKILL_DISTANCE -> "distance fighting"
    skills_t.SKILL_SHIELD -> "shielding"
    skills_t.SKILL_FISHING -> "fishing"
    skills_t.SKILL_CRITICAL_HIT_CHANCE -> "critical hit chance"
    skills_t.SKILL_CRITICAL_HIT_DAMAGE -> "critical extra damage"
    skills_t.SKILL_LIFE_LEECH_CHANCE -> "life leech chance"
    skills_t.SKILL_LIFE_LEECH_AMOUNT -> "life leech"
    skills_t.SKILL_MANA_LEECH_CHANCE -> "mana leech chance"
    skills_t.SKILL_MANA_LEECH_AMOUNT -> "mana leech"
    skills_t.SKILL_MAGLEVEL -> "magic level"
    skills_t.SKILL_LEVEL -> "level"
    skills_t.SKILL_NONE -> "unknown"
}
