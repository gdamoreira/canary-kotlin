package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Transcript tests for the Canary combat damage core.
 *
 * Every expectation is derived from the exact C++ formulas/constants in the
 * referenced source lines. RNG-dependent values are asserted within the exact
 * closed intervals guaranteed by Canary `uniform_random`/`normal_random`.
 */
class CombatTest {

    @Test
    fun `CombatType mirrors the exact C++ CombatType_t values`() {
        // src/creatures/creatures_definitions.hpp:833-853
        assertThat(CombatType.PHYSICALDAMAGE.value).isEqualTo(0)
        assertThat(CombatType.FIREDAMAGE.value).isEqualTo(1)
        assertThat(CombatType.EARTHDAMAGE.value).isEqualTo(2)
        assertThat(CombatType.ENERGYDAMAGE.value).isEqualTo(3)
        assertThat(CombatType.UNDEFINEDDAMAGE.value).isEqualTo(4)
        assertThat(CombatType.LIFEDRAIN.value).isEqualTo(5)
        assertThat(CombatType.MANADRAIN.value).isEqualTo(6)
        assertThat(CombatType.HEALING.value).isEqualTo(7)
        assertThat(CombatType.DROWNDAMAGE.value).isEqualTo(8)
        assertThat(CombatType.ICEDAMAGE.value).isEqualTo(9)
        assertThat(CombatType.HOLYDAMAGE.value).isEqualTo(10)
        assertThat(CombatType.DEATHDAMAGE.value).isEqualTo(11)
        assertThat(CombatType.AGONYDAMAGE.value).isEqualTo(12)
        assertThat(CombatType.NEUTRALDAMAGE.value).isEqualTo(13)
        assertThat(CombatType.COUNT.value).isEqualTo(14)
        assertThat(CombatType.NONE.value).isEqualTo(255)
    }

    @Test
    fun `CombatOrigin and BlockType mirror the exact C++ ordinals`() {
        // src/creatures/creatures_definitions.hpp:313-322
        assertThat(CombatOrigin.NONE.value).isEqualTo(0)
        assertThat(CombatOrigin.CONDITION.value).isEqualTo(1)
        assertThat(CombatOrigin.SPELL.value).isEqualTo(2)
        assertThat(CombatOrigin.MELEE.value).isEqualTo(3)
        assertThat(CombatOrigin.RANGED.value).isEqualTo(4)
        assertThat(CombatOrigin.HARMONY.value).isEqualTo(5)
        assertThat(CombatOrigin.FIST.value).isEqualTo(6)
        assertThat(CombatOrigin.WEAPON_PROFICIENCY.value).isEqualTo(7)

        // src/creatures/creatures_definitions.hpp:516-522
        assertThat(BlockType.NONE.value).isEqualTo(0)
        assertThat(BlockType.DEFENSE.value).isEqualTo(1)
        assertThat(BlockType.ARMOR.value).isEqualTo(2)
        assertThat(BlockType.IMMUNITY.value).isEqualTo(3)
        assertThat(BlockType.DODGE.value).isEqualTo(4)
    }

    @Test
    fun `CombatDamage starts empty and only borrows empty for the exact C++ field set`() {
        // src/creatures/creatures_definitions.hpp:1725-1758
        val empty = CombatDamage()
        assertThat(empty.primaryType).isEqualTo(CombatType.NONE)
        assertThat(empty.primaryValue).isEqualTo(0)
        assertThat(empty.affected).isEqualTo(1)
        assertThat(empty.isEmpty()).isTrue()

        assertThat(empty.copy(primaryValue = 1).isEmpty()).isFalse()
        assertThat(empty.copy(origin = CombatOrigin.SPELL).isEmpty()).isFalse()
        assertThat(empty.copy(critical = true).isEmpty()).isFalse()
        assertThat(empty.copy(primaryType = CombatType.FIREDAMAGE).isEmpty()).isFalse()
    }

    @Test
    fun `Weapons getMaxWeaponDamage matches the Canary melee and distance formulas`() {
        // src/items/weapons/weapons.cpp:94-100
        // melee: round(0.085 * factor * attackValue * attackSkill + level / 5)
        assertThat(Weapons.getMaxWeaponDamage(100, 100, 50, 1.0f, isMelee = true)).isEqualTo(445)
        // distance: round(0.09 * factor * attackValue * attackSkill + level / 5)
        assertThat(Weapons.getMaxWeaponDamage(100, 100, 50, 1.0f, isMelee = false)).isEqualTo(470)
        // zero attack value melee is explicitly 0
        assertThat(Weapons.getMaxWeaponDamage(100, 100, 0, 1.0f, isMelee = true)).isEqualTo(0)
        // distance with zero attack still returns round(level / 5)
        assertThat(Weapons.getMaxWeaponDamage(100, 100, 0, 1.0f, isMelee = false)).isEqualTo(20)
    }

    @Test
    fun `Weapons getMaxMeleeDamage matches the monster formula`() {
        // src/items/weapons/weapons.cpp:88-91
        // ceil(attackSkill * (attackValue * 0.05) + attackValue * 0.5)
        assertThat(Weapons.getMaxMeleeDamage(100, 50)).isEqualTo(275)
        assertThat(Weapons.getMaxMeleeDamage(0, 50)).isEqualTo(25)
    }

    @Test
    fun `Weapons getWeaponDamage returns the negated Canary damage roll`() {
        // src/items/weapons/weapons.cpp:645-664
        assertThat(
            Weapons.getWeaponDamage(
                level = 100,
                attackSkill = 100,
                physicalAttack = 50,
                elementalAttack = 0,
                attackFactor = 1.0f,
                meleeDamageMultiplier = 1.0,
                random = Random(42),
                maxDamage = true,
            )
        ).isEqualTo(-445)

        val rolled = Weapons.getWeaponDamage(
            level = 100,
            attackSkill = 100,
            physicalAttack = 50,
            elementalAttack = 0,
            attackFactor = 1.0f,
            meleeDamageMultiplier = 1.0,
            random = Random(42),
        )
        // minValue = level / 5 = 20, maxValue = 445 -> normal_random(20, 445) negated
        assertThat(rolled).isBetween(-445, -20)
    }

    @Test
    fun `Combat blockHit applies the exact armor reduction range`() {
        // src/creatures/creature.cpp:976-988
        // armor > 3: damage -= uniform_random(armor / 2, armor - (armor % 2 + 1))
        val evenArmor = Combat.blockHit(damage = 100, checkArmor = true, armor = 20, random = Random(1))
        assertThat(evenArmor.first).isBetween(81, 90)
        assertThat(evenArmor.second).isEqualTo(BlockType.NONE)

        val oddArmor = Combat.blockHit(damage = 100, checkArmor = true, armor = 21, random = Random(1))
        // uniform_random(10, 21 - 2) = uniform_random(10, 19)
        assertThat(oddArmor.first).isBetween(81, 90)

        // armor in (0, 3]: --damage exactly once
        val smallArmor = Combat.blockHit(damage = 100, checkArmor = true, armor = 3, random = Random(1))
        assertThat(smallArmor.first).isEqualTo(99)
        assertThat(smallArmor.second).isEqualTo(BlockType.NONE)

        // armor <= 0 leaves damage untouched
        assertThat(Combat.blockHit(damage = 100, checkArmor = true, armor = 0, random = Random(1)).first)
            .isEqualTo(100)

        // fully blocked armor reports BLOCK_ARMOR and clamps to 0
        val blocked = Combat.blockHit(damage = 1, checkArmor = true, armor = 3, random = Random(1))
        assertThat(blocked.first).isEqualTo(0)
        assertThat(blocked.second).isEqualTo(BlockType.ARMOR)
    }

    @Test
    fun `Combat blockHit applies defense before armor and honors immunity`() {
        // src/creatures/creature.cpp:958-993
        val defended = Combat.blockHit(
            damage = 100,
            checkDefense = true,
            defense = 20,
            random = Random(1),
        )
        // defense > 3 path: uniform_random(10, 20) -> damage in [80, 90]
        assertThat(defended.first).isBetween(80, 90)

        // defense fully absorbs -> BLOCK_DEFENSE, armor is skipped
        val defenseBlock = Combat.blockHit(
            damage = 5,
            checkDefense = true,
            checkArmor = true,
            defense = 40,
            armor = 100,
            random = Random(1),
        )
        assertThat(defenseBlock.first).isEqualTo(0)
        assertThat(defenseBlock.second).isEqualTo(BlockType.DEFENSE)

        // immunity zeroes the damage regardless of armor
        val immune = Combat.blockHit(damage = 500, checkArmor = true, armor = 50, immune = true, random = Random(1))
        assertThat(immune.first).isEqualTo(0)
        assertThat(immune.second).isEqualTo(BlockType.IMMUNITY)
    }

    @Test
    fun `Combat mitigateDamage matches the exact mitigation formula`() {
        // src/creatures/creature.cpp:910-922
        // damage -= (damage * mitigation) / 100.
        assertThat(Combat.mitigateDamage(CombatType.PHYSICALDAMAGE, 100, 20.0).first).isEqualTo(80)
        // truncation toward zero: 100 - 12.5 = 87.5 -> 87
        assertThat(Combat.mitigateDamage(CombatType.FIREDAMAGE, 100, 12.5).first).isEqualTo(87)
        // reduction to zero clamps and reports BLOCK_ARMOR
        val zeroed = Combat.mitigateDamage(CombatType.ENERGYDAMAGE, 1, 100.0)
        assertThat(zeroed.first).isEqualTo(0)
        assertThat(zeroed.second).isEqualTo(BlockType.ARMOR)

        // mana/life drain and agony damage are explicitly not mitigated
        assertThat(Combat.mitigateDamage(CombatType.MANADRAIN, 100, 50.0).first).isEqualTo(100)
        assertThat(Combat.mitigateDamage(CombatType.LIFEDRAIN, 100, 50.0).first).isEqualTo(100)
        assertThat(Combat.mitigateDamage(CombatType.AGONYDAMAGE, 100, 50.0).first).isEqualTo(100)
    }

    @Test
    fun `Combat applyExtensions applies the critical multiplier and fatal bonus`() {
        // src/creatures/combat/combat.cpp:2744-2756
        // targetMultiplier = 1.0 + bonus / 10000.0
        val crit = Combat.applyExtensions(
            damage = CombatDamage(primaryValue = 100, secondaryValue = 50),
            baseChance = 10000,
            baseBonus = 2000,
            random = Random(1),
        )
        assertThat(crit.critical).isTrue()
        assertThat(crit.primaryValue).isEqualTo(120)
        assertThat(crit.secondaryValue).isEqualTo(60)

        val noCrit = Combat.applyExtensions(
            damage = CombatDamage(primaryValue = 100, secondaryValue = 50),
            baseChance = 0,
            baseBonus = 2000,
            random = Random(1),
        )
        assertThat(noCrit.critical).isFalse()
        assertThat(noCrit.primaryValue).isEqualTo(100)

        // fatal adds round(value * 0.6)
        val fatal = Combat.applyExtensions(
            damage = CombatDamage(primaryValue = 100, secondaryValue = 0),
            baseChance = 0,
            baseBonus = 0,
            canApplyFatal = true,
            random = Random(1),
        )
        assertThat(fatal.fatal).isTrue()
        assertThat(fatal.primaryValue).isEqualTo(160)
    }

    @Test
    fun `Combat getLevelFormula matches the Canary level plus magic formula`() {
        // src/creatures/combat/combat.cpp:150-151
        // level * 2 + (magicLevel + specializedMagicLevel) * 3
        assertThat(Combat.getLevelFormula(level = 50, magicLevel = 10, specializedMagicLevel = 5)).isEqualTo(145)
        assertThat(Combat.getLevelFormula(level = 8, magicLevel = 0, specializedMagicLevel = 0)).isEqualTo(16)
    }

    @Test
    fun `Combat getCombatDamage reproduces the formula branches`() {
        // src/creatures/combat/combat.cpp:181-222

        // COMBAT_FORMULA_DAMAGE: primary = normal_random(mina, maxa)
        val fixed = Combat.getCombatDamage(
            formulaType = FormulaType.DAMAGE,
            mina = 10.0,
            maxa = 20.0,
            combatType = CombatType.PHYSICALDAMAGE,
            origin = CombatOrigin.MELEE,
            random = Random(7),
        )
        assertThat(fixed.primaryType).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(fixed.origin).isEqualTo(CombatOrigin.MELEE)
        assertThat(fixed.primaryValue).isBetween(10, 20)

        // COMBAT_FORMULA_LEVELMAGIC: normal_random(levelFormula * mina + minb, levelFormula * maxa + maxb)
        val levelMagic = Combat.getCombatDamage(
            formulaType = FormulaType.LEVELMAGIC,
            mina = 0.5,
            maxa = 1.0,
            levelFormula = 145,
            random = Random(7),
        )
        assertThat(levelMagic.primaryValue).isBetween(72, 145)

        // COMBAT_FORMULA_SKILL without a weapon: normal_random(minb, maxb)
        val skillNoWeapon = Combat.getCombatDamage(
            formulaType = FormulaType.SKILL,
            minb = 5.0,
            maxb = 15.0,
            random = Random(7),
        )
        assertThat(skillNoWeapon.primaryValue).isBetween(5, 15)

        // COMBAT_FORMULA_SKILL with a weapon: normal_random(minb, weaponDamage * maxa + maxb)
        val skillWeapon = Combat.getCombatDamage(
            formulaType = FormulaType.SKILL,
            minb = 0.0,
            maxa = 0.5,
            weaponDamage = 100,
            hasWeapon = true,
            elementType = CombatType.FIREDAMAGE,
            elementValue = 7,
            random = Random(7),
        )
        assertThat(skillWeapon.primaryValue).isBetween(0, 50)
        assertThat(skillWeapon.secondaryType).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(skillWeapon.secondaryValue).isEqualTo(7)
    }
}
