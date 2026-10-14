package org.opentibia.canary.systems.proficiency

/**
 * Weapon Proficiencies enums and data model. Ported verbatim from Canary:
 *  - src/enums/weapon_proficiency.hpp
 *  - src/io/io_bosstiary.hpp (BosstiaryRarity_t)
 *
 * Combat-application methods (applyAutoAttackCritical, applySpellAugment, ...)
 * depend on Canary's CombatDamage and are not ported here; the JSON loader and
 * the remaining gaps are noted in docs/development/plan/060-weapon-proficiencies-system.md.
 */

enum class WeaponProficiencyBonus(val value: Int) {
    ATTACK_DAMAGE(0),
    DEFENSE_BONUS(1),
    WEAPON_SHIELD_MODIFIER(2),
    SKILL_BONUS(3),
    SPECIALIZED_MAGIC_LEVEL(4),
    SPELL_AUGMENT(5),
    WEAPON_PROFICIENCY_BESTIARY(6),
    POWERFUL_FOE_BONUS(7),
    CRITICAL_HIT_CHANCE(8),
    ELEMENTAL_HIT_CHANCE(9),
    RUNE_CRITICAL_HIT_CHANCE(10),
    AUTO_ATTACK_CRITICAL_HIT_CHANCE(11),
    CRITICAL_EXTRA_DAMAGE(12),
    ELEMENTAL_CRITICAL_EXTRA_DAMAGE(13),
    RUNE_CRITICAL_EXTRA_DAMAGE(14),
    AUTO_ATTACK_CRITICAL_EXTRA_DAMAGE(15),
    MANA_LEECH(16),
    LIFE_LEECH(17),
    MANA_GAIN_ON_HIT(18),
    LIFE_GAIN_ON_HIT(19),
    MANA_GAIN_ON_KILL(20),
    LIFE_GAIN_ON_KILL(21),
    PERFECT_SHOT_DAMAGE(22),
    RANGED_HIT_CHANCE(23),
    ATTACK_RANGE(24),
    SKILL_PERCENTAGE_AUTO_ATTACK(25),
    SKILL_PERCENTAGE_SPELL_DAMAGE(26),
    SKILL_PERCENTAGE_SPELL_HEALING(27),
    ALPHA_STRIKE_EXTRA_DAMAGE(28),
    OMEGA_STRIKE_EXTRA_DAMAGE(29),
    ARMOR_PENETRATION(30),
    ELEMENTAL_PIERCE(31),
}

enum class SkillPercentageType(val value: Int) {
    AutoAttack(0),
    SpellDamage(1),
    SpellHealing(2),
}

enum class WeaponProficiencySpellBoost(val value: Int) {
    MANA(0),
    COOLDOWN(1),
    GROUP_COOLDOWN(2),
    SECONDARY_GROUP_COOLDOWN(3),
    MANA_LEECH(4),
    MANA_LEECH_CHANCE(5),
    LIFE_LEECH(6),
    LIFE_LEECH_CHANCE(7),
    DAMAGE(8),
    DAMAGE_REDUCTION(9),
    HEAL(10),
    CRITICAL_DAMAGE(11),
    CRITICAL_CHANCE(12),
}

enum class WeaponProficiencyHealth(val value: Int) {
    LIFE(0),
    MANA(1),
}

enum class WeaponProficiencyGain(val value: Int) {
    HIT(0),
    KILL(1),
}

enum class WeaponProficiencyExperienceTier(val value: Int) {
    Easy(1),
    Medium(2),
    Hard(3),
}

enum class BosstiaryRarity(val value: Int) {
    RARITY_BANE(0),
    RARITY_ARCHFOE(1),
    RARITY_NEMESIS(2),
}

data class ProficiencyPerk(
    var level: Int = 0,
    var index: Int = 0,
    var value: Double = 0.0,
    var spellId: Int = 0,
    var range: Int = 0,
    var bestiaryId: Int = 0,
    var bestiaryName: String = "",
    var augmentType: Int = 0,
    var skillId: Int = 0,
    var element: Int = 0,
    var type: WeaponProficiencyBonus = WeaponProficiencyBonus.ATTACK_DAMAGE,
)

data class ProficiencyLevel(
    val perks: MutableList<ProficiencyPerk> = mutableListOf(),
)

data class Proficiency(
    var id: Int = 0,
    val level: MutableList<ProficiencyLevel> = mutableListOf(),
    var maxLevel: Int = 0,
)

data class WeaponProficiencyData(
    var experience: Int = 0,
    val perks: MutableList<ProficiencyPerk> = mutableListOf(),
    var mastered: Boolean = false,
)

data class WeaponProficiencyBonusStat(
    var element: Int = 0,
    var value: Int = 0,
)

data class WeaponProficiencyPerfectShotBonus(
    var range: Int = 0,
    var damage: Double = 0.0,
)

data class SkillPercentage(
    var skill: Int = 0,
    var spellHealing: Double = 0.0,
    var autoAttack: Double = 0.0,
    var spellDamage: Double = 0.0,
)

data class WeaponProficiencyCriticalBonus(
    var chance: Double = 0.0,
    var damage: Double = 0.0,
)
