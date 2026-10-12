package org.opentibia.canary.systems.bestiary

import org.opentibia.canary.combat.CombatType

/**
 * Charm runes: definitions and per-player state.
 *
 * Ported from Canary:
 *  - `src/io/iobestiary.hpp` (class Charm, struct PlayerCharmsByMonster)
 *  - `src/creatures/creatures_definitions.hpp:391-431` (charmCategory_t, charm_t, charmRune_t)
 *  - `src/creatures/players/player.hpp:113-116` (struct CharmInfo)
 *
 * The combat handlers live in [CharmCombat]; this file only owns the charm
 * definition and per-player state.
 */

/** `charmCategory_t` (creatures_definitions.hpp:391-395). */
enum class CharmCategory(val value: Int) {
    CHARM_ALL(0),
    CHARM_MAJOR(1),
    CHARM_MINOR(2),
}

/** `charm_t` (creatures_definitions.hpp:397-402). */
enum class CharmType(val value: Int) {
    CHARM_UNDEFINED(0),
    CHARM_OFFENSIVE(1),
    CHARM_DEFENSIVE(2),
    CHARM_PASSIVE(3),
}

/** `charmRune_t : int8_t` (creatures_definitions.hpp:404-431). */
enum class CharmRune(val value: Int) {
    CHARM_NONE(-1),
    CHARM_WOUND(0),
    CHARM_ENFLAME(1),
    CHARM_POISON(2),
    CHARM_FREEZE(3),
    CHARM_ZAP(4),
    CHARM_CURSE(5),
    CHARM_CRIPPLE(6),
    CHARM_PARRY(7),
    CHARM_DODGE(8),
    CHARM_ADRENALINE(9),
    CHARM_NUMB(10),
    CHARM_CLEANSE(11),
    CHARM_BLESS(12),
    CHARM_SCAVENGE(13),
    CHARM_GUT(14),
    CHARM_LOW(15),
    CHARM_DIVINE(16),
    CHARM_VAMP(17),
    CHARM_VOID(18),
    CHARM_SAVAGE(19),
    CHARM_FATAL(20),
    CHARM_VOIDINVERSION(21),
    CHARM_CARNAGE(22),
    CHARM_OVERPOWER(23),
    CHARM_OVERFLUX(24),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }
        fun fromValue(value: Int): CharmRune? = byValue[value]
    }
}

/**
 * Mirrors Canary `class Charm` (iobestiary.hpp:31-63) for the fields the engine
 * core uses. `points` and `chance` are supplied by the Lua registrations in
 * `data/scripts/systems/bestiary_charms.lua` (which stay Lua).
 */
data class Charm(
    val name: String = "",
    val id: CharmRune = CharmRune.CHARM_NONE,
    val description: String = "",
    val category: CharmCategory = CharmCategory.CHARM_ALL,
    val type: CharmType = CharmType.CHARM_UNDEFINED,
    val points: List<Int> = emptyList(),
    val binary: Int = 0,
    val cancelMessage: String = "",
    val logMessage: Boolean = false,
    val damageType: CombatType = CombatType.NONE,
    val effect: Int = 0,
    val soundImpactEffect: Int = 0,
    val soundCastEffect: Int = 0,
    val percent: Double = 0.0,
    val chance: List<Double> = emptyList(),
) {
    /** `Charm::getChance` (iobestiary.hpp:38-43). */
    fun getChance(tier: Int): Double = if (tier < chance.size) chance[tier] else 0.0
}

/** Mirrors Canary `struct CharmInfo` (player.hpp:113-116). */
data class CharmInfo(
    var raceId: Int = 0,
    var tier: Int = 0,
)

/** Mirrors Canary `struct PlayerCharmsByMonster` (iobestiary.hpp:26-29). */
data class PlayerCharmsByMonster(
    var major: CharmRune = CharmRune.CHARM_NONE,
    var minor: CharmRune = CharmRune.CHARM_NONE,
)
