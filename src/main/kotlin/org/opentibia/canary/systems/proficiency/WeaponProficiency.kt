package org.opentibia.canary.systems.proficiency

import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatType
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Combat-application half of the Weapon Proficiencies component, ported from
 * `src/creatures/players/components/weapon_proficiency.cpp`.
 *
 * The eight `apply*` methods mutate a [CombatDamage] exactly like Canary. The
 * `Player` skill/weapon lookups and the target `Monster` metadata are replaced
 * by the [WeaponProficiencyPlayer] / [ProficiencyMonster] seams (AGENTS.md 1.1).
 *
 * The C++ `m_stats`, specialized-magic, skill-bonus, perfect-shot and
 * `applyOn`/`getActiveAugments` surfaces are outside this port (they are not
 * combat-application consumers); the state needed by the eight methods is
 * transcribed from `weapon_proficiency.hpp:143-163`.
 */
class WeaponProficiency(
    private val player: WeaponProficiencyPlayer,
) {
    private val autoAttackCritical = WeaponProficiencyCriticalBonus()
    private val runesCritical = WeaponProficiencyCriticalBonus()
    private val elementCritical = Array(COMBAT_COUNT) { WeaponProficiencyCriticalBonus() }

    private val skillPercentages = linkedMapOf<Int, SkillPercentage>()
    private val spellsBonuses = linkedMapOf<Int, WeaponProficiencySpellBonus>()
    private val bestiaryDamage = linkedMapOf<Int, Double>()

    private var powerfulFoeDamage = 0.0

    // ---- mutators / accessors (weapon_proficiency.cpp:977-1219) --------------

    /** `WeaponProficiency::getPowerfulFoeDamage` (weapon_proficiency.cpp:1075-1077). */
    fun getPowerfulFoeDamage(): Double = powerfulFoeDamage

    /** `WeaponProficiency::addPowerfulFoeDamage` (weapon_proficiency.cpp:1079-1081). */
    fun addPowerfulFoeDamage(percent: Double) {
        powerfulFoeDamage += percent
    }

    /** `WeaponProficiency::getAutoAttackCritical` (weapon_proficiency.cpp:1087-1089). */
    fun getAutoAttackCritical(): WeaponProficiencyCriticalBonus = autoAttackCritical

    /** `WeaponProficiency::addAutoAttackCritical` (weapon_proficiency.cpp:1091-1094). */
    fun addAutoAttackCritical(bonus: WeaponProficiencyCriticalBonus) {
        autoAttackCritical.chance += bonus.chance
        autoAttackCritical.damage += bonus.damage
    }

    /** `WeaponProficiency::getRunesCritical` (weapon_proficiency.cpp:1096-1098). */
    fun getRunesCritical(): WeaponProficiencyCriticalBonus = runesCritical

    /** `WeaponProficiency::addRunesCritical` (weapon_proficiency.cpp:1100-1103). */
    fun addRunesCritical(bonus: WeaponProficiencyCriticalBonus) {
        runesCritical.chance += bonus.chance
        runesCritical.damage += bonus.damage
    }

    /** `WeaponProficiency::getElementCritical` (weapon_proficiency.cpp:1114-1124). */
    fun getElementCritical(type: CombatType): WeaponProficiencyCriticalBonus {
        if (type == CombatType.NONE) {
            return WeaponProficiencyCriticalBonus()
        }
        val index = type.value
        return if (index < elementCritical.size) elementCritical[index] else WeaponProficiencyCriticalBonus()
    }

    /** `WeaponProficiency::addElementCritical` (weapon_proficiency.cpp:1126-1139). */
    fun addElementCritical(type: CombatType, bonus: WeaponProficiencyCriticalBonus) {
        if (type == CombatType.NONE) {
            return
        }
        val index = type.value
        if (index >= elementCritical.size) {
            return
        }
        elementCritical[index].chance += bonus.chance
        elementCritical[index].damage += bonus.damage
    }

    /** `WeaponProficiency::addSkillPercentage` (weapon_proficiency.cpp:1000-1018). */
    fun addSkillPercentage(skill: Int, type: SkillPercentageType, value: Double) {
        val skillPercentage = skillPercentages.getOrPut(skill) {
            SkillPercentage(skill = skill)
        }
        when (type) {
            SkillPercentageType.AutoAttack -> skillPercentage.autoAttack += value
            SkillPercentageType.SpellDamage -> skillPercentage.spellDamage += value
            SkillPercentageType.SpellHealing -> skillPercentage.spellHealing += value
        }
    }

    /** `WeaponProficiency::getSkillPercentage` (weapon_proficiency.cpp:1020-1026). */
    fun getSkillPercentage(skill: Int): SkillPercentage =
        skillPercentages[skill] ?: SkillPercentage(skill = SKILL_NONE)

    /** `WeaponProficiency::getBestiaryDamage` (weapon_proficiency.cpp:1209-1215). */
    fun getBestiaryDamage(race: Int): Double = bestiaryDamage[race] ?: 0.0

    /** `WeaponProficiency::addBestiaryDamage` (weapon_proficiency.cpp:1217-1225). */
    fun addBestiaryDamage(race: Int, bonus: Double) {
        bestiaryDamage[race] = (bestiaryDamage[race] ?: 0.0) + bonus
    }

    /** `WeaponProficiency::addSpellBonus` (weapon_proficiency.cpp:1176-1194). */
    fun addSpellBonus(spellId: Int, bonus: WeaponProficiencySpellBonus) {
        val current = spellsBonuses[spellId]
        if (current == null) {
            spellsBonuses[spellId] = bonus
            return
        }
        current.decrease.cooldown += bonus.decrease.cooldown
        current.decrease.manaCost += bonus.decrease.manaCost
        current.decrease.secondaryGroupCooldown += bonus.decrease.secondaryGroupCooldown
        current.increase.additionalTarget += bonus.increase.additionalTarget
        current.increase.area = current.increase.area || bonus.increase.area
        current.increase.criticalChance += bonus.increase.criticalChance
        current.increase.criticalDamage += bonus.increase.criticalDamage
        current.increase.damage += bonus.increase.damage
        current.increase.damageReduction += bonus.increase.damageReduction
        current.increase.duration += bonus.increase.duration
        current.increase.heal += bonus.increase.heal
        current.leech.life += bonus.leech.life
        current.leech.mana += bonus.leech.mana
    }

    // ---- combat application --------------------------------------------------

    /** `WeaponProficiency::applyAutoAttackCritical` (weapon_proficiency.cpp:1257-1263). */
    fun applyAutoAttackCritical(damage: CombatDamage) {
        if (damage.origin == CombatOrigin.FIST ||
            damage.origin == CombatOrigin.MELEE ||
            damage.origin == CombatOrigin.RANGED
        ) {
            val autoAttackCritical = getAutoAttackCritical()
            damage.criticalChance = (damage.criticalChance + autoAttackCritical.chance * 10000).toInt()
            damage.criticalDamage = (damage.criticalDamage + autoAttackCritical.damage * 10000).toInt()
        }
    }

    /** `WeaponProficiency::applyRunesCritical` (weapon_proficiency.cpp:1265-1271). */
    fun applyRunesCritical(damage: CombatDamage, aggressive: Boolean) {
        if (damage.runeSpellName.isNotEmpty() && aggressive) {
            val runesCritical = getRunesCritical()
            damage.criticalChance = (damage.criticalChance + runesCritical.chance * 10000).toInt()
            damage.criticalDamage = (damage.criticalDamage + runesCritical.damage * 10000).toInt()
        }
    }

    /** `WeaponProficiency::applyElementCritical` (weapon_proficiency.cpp:1273-1283). */
    fun applyElementCritical(damage: CombatDamage) {
        if (damage.primaryType == CombatType.NONE || damage.primaryType.value >= COMBAT_COUNT) {
            return
        }

        val elementCritical = getElementCritical(damage.primaryType)
        if (elementCritical.chance > 0 || elementCritical.damage > 0) {
            damage.criticalChance = (damage.criticalChance + elementCritical.chance * 10000).toInt()
            damage.criticalDamage = (damage.criticalDamage + elementCritical.damage * 10000).toInt()
        }
    }

    /** `WeaponProficiency::applyBestiaryDamage` (weapon_proficiency.cpp:1285-1300). */
    fun applyBestiaryDamage(damage: CombatDamage, monster: ProficiencyMonster?) {
        if (monster == null) {
            return
        }
        val monsterType = monster.monsterType ?: return

        val race = monsterType.bestiaryRace
        if (race > 0) {
            val bestiaryDamage = getBestiaryDamage(race)
            damage.primaryValue = (damage.primaryValue * (1 + bestiaryDamage)).toInt()
            damage.secondaryValue = (damage.secondaryValue * (1 + bestiaryDamage)).toInt()
        }
    }

    /** `WeaponProficiency::applyPowerfulFoeDamage` (weapon_proficiency.cpp:1302-1315). */
    fun applyPowerfulFoeDamage(damage: CombatDamage, monster: ProficiencyMonster?) {
        if (monster == null) {
            return
        }

        val forgeStack = monster.forgeStack
        val monsterType = monster.monsterType
        if (forgeStack > 0 || (monsterType != null && monsterType.isBoss)) {
            val bonusDamage = getPowerfulFoeDamage()
            damage.primaryValue = (damage.primaryValue * (1 + bonusDamage)).toInt()
            damage.secondaryValue = (damage.secondaryValue * (1 + bonusDamage)).toInt()
        }
    }

    /** `WeaponProficiency::applySkillAutoAttackPercentage` (weapon_proficiency.cpp:1317-1343). */
    fun applySkillAutoAttackPercentage(damage: CombatDamage) {
        if (damage.origin != CombatOrigin.FIST &&
            damage.origin != CombatOrigin.MELEE &&
            damage.origin != CombatOrigin.RANGED
        ) {
            return
        }

        if (!player.getWeapon(true)) {
            return
        }

        for (skillPercentage in skillPercentages.values) {
            if (skillPercentage.autoAttack <= 0) {
                continue
            }

            val bonusDamage = player.getSkillLevel(skillPercentage.skill) * skillPercentage.autoAttack

            if (damage.primaryType != CombatType.NONE) {
                damage.primaryValue -= ceil(bonusDamage).toInt()
            }
            if (damage.secondaryType != CombatType.NONE) {
                damage.secondaryValue -= ceil(bonusDamage).toInt()
            }
        }
    }

    /** `WeaponProficiency::applySkillSpellPercentage` (weapon_proficiency.cpp:1345-1376). */
    fun applySkillSpellPercentage(damage: CombatDamage, healing: Boolean = false) {
        if (damage.instantSpellName.isEmpty()) {
            return
        }

        if (healing && damage.primaryType != CombatType.HEALING) {
            return
        }

        if (!player.getWeapon(true)) {
            return
        }

        for (skillPercentage in skillPercentages.values) {
            val skillPercentageValue = if (healing) skillPercentage.spellHealing else skillPercentage.spellDamage
            if (skillPercentageValue <= 0) {
                continue
            }

            val bonusDamage = ceil(player.getSkillLevel(skillPercentage.skill) * skillPercentageValue)

            if (damage.primaryType != CombatType.NONE) {
                damage.primaryValue =
                    (damage.primaryValue + if (damage.primaryValue < 0) -bonusDamage else bonusDamage).toInt()
            }
            if (damage.secondaryType != CombatType.NONE) {
                damage.secondaryValue =
                    (damage.secondaryValue + if (damage.secondaryValue < 0) -bonusDamage else bonusDamage).toInt()
            }
        }
    }

    /** `WeaponProficiency::applySpellAugment` (weapon_proficiency.cpp:1411-1420). */
    fun applySpellAugment(damage: CombatDamage, spellId: Int) {
        val bonus = spellsBonuses[spellId] ?: return

        damage.damageMultiplier += lround(bonus.increase.damage * 100)
        damage.healingMultiplier += lround(bonus.increase.heal * 100)
        damage.criticalChance = (damage.criticalChance + bonus.increase.criticalChance * 10000).toInt()
        damage.criticalDamage = (damage.criticalDamage + bonus.increase.criticalDamage * 10000).toInt()
        damage.lifeLeech = (damage.lifeLeech + bonus.leech.life * 10000).toInt()
        damage.manaLeech = (damage.manaLeech + bonus.leech.mana * 10000).toInt()
    }

    private companion object {
        /** `COMBAT_COUNT` (creatures_definitions.hpp:846). */
        const val COMBAT_COUNT = 14

        /**
         * Faithful port of C `lround`: rounds halfway cases away from zero.
         * Canary uses `std::lround` in `applySpellAugment`.
         */
        fun lround(value: Double): Int =
            if (value >= 0) {
                floor(value + 0.5).toInt()
            } else {
                ceil(value - 0.5).toInt()
            }
    }
}

/** Player seam for the weapon-proficiency combat methods (Canary `Player`). */
interface WeaponProficiencyPlayer {
    fun getSkillLevel(skill: Int): Int

    /** Canary `Player::getWeapon(true)`; only the null check is consumed. */
    fun getWeapon(ignoreWeaponProficiency: Boolean): Boolean
}

/** Monster seam for the weapon-proficiency combat methods (Canary `Monster`). */
interface ProficiencyMonster {
    val monsterType: ProficiencyMonsterType?
    val forgeStack: Int
}

/** `MonsterType` metadata subset (Canary `MonsterType::info`). */
interface ProficiencyMonsterType {
    /** `magic_enum::enum_integer(monsterType->info.bestiaryRace)`. */
    val bestiaryRace: Int
    val isBoss: Boolean
}

/** `WeaponProficiencySpells::Decrease` (src/enums/weapon_proficiency.hpp:178-182). */
data class WeaponProficiencySpellDecrease(
    var cooldown: Int = 0,
    var manaCost: Int = 0,
    var secondaryGroupCooldown: Int = 0,
)

/** `WeaponProficiencySpells::Increase` (src/enums/weapon_proficiency.hpp:167-176). */
data class WeaponProficiencySpellIncrease(
    var area: Boolean = false,
    var damage: Double = 0.0,
    var heal: Double = 0.0,
    var additionalTarget: Int = 0,
    var damageReduction: Double = 0.0,
    var duration: Int = 0,
    var criticalDamage: Double = 0.0,
    var criticalChance: Double = 0.0,
)

/** `WeaponProficiencySpells::Leech` (src/enums/weapon_proficiency.hpp:184-187). */
data class WeaponProficiencySpellLeech(
    var mana: Double = 0.0,
    var life: Double = 0.0,
)

/** `WeaponProficiencySpells::Bonus` (src/enums/weapon_proficiency.hpp:189-193). */
data class WeaponProficiencySpellBonus(
    var leech: WeaponProficiencySpellLeech = WeaponProficiencySpellLeech(),
    var increase: WeaponProficiencySpellIncrease = WeaponProficiencySpellIncrease(),
    var decrease: WeaponProficiencySpellDecrease = WeaponProficiencySpellDecrease(),
)
