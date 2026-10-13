package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.creatures.vocations.Vocation
import java.util.Random

/**
 * Transcript tests for the remaining `Combat::getCombatDamage` branches and the
 * absorb/increase damage modifications.
 *
 * Every expectation is derived from the exact C++ control flow in the cited
 * source lines:
 *  - `Combat::getCombatDamage` (src/creatures/combat/combat.cpp:154-231)
 *  - `Creature::applyAbsorbDamageModifications` (src/creatures/creature.cpp:924-942)
 *  - `Creature::blockHit` (src/creatures/creature.cpp:944-1009)
 *  - `Combat::applyMantraAbsorb` (src/creatures/combat/combat.cpp:739-759)
 *  - `Game::combatBlockHit` (src/game/game.cpp:7874-8061)
 */
class CombatMatrixTest {

    @Test
    fun `getCombatDamage lets the monster combat values win over the player formulas`() {
        // combat.cpp:186-190 `if (creature->getCombatValues(min, max))`
        // runs before the player LEVELMAGIC/SKILL branches.
        val levelMagic = Combat.getCombatDamage(
            formulaType = FormulaType.LEVELMAGIC,
            mina = 0.5,
            maxa = 1.0,
            levelFormula = 1000,
            combatValues = 30 to 40,
            random = Random(7),
        )
        assertThat(levelMagic.primaryValue).isBetween(30, 40)

        val skill = Combat.getCombatDamage(
            formulaType = FormulaType.SKILL,
            minb = 500.0,
            maxb = 900.0,
            combatValues = 5 to 9,
            random = Random(7),
        )
        assertThat(skill.primaryValue).isBetween(5, 9)
    }

    @Test
    fun `getCombatDamage still applies the DAMAGE formula before the combat values`() {
        // combat.cpp:181-185 `COMBAT_FORMULA_DAMAGE` is checked first.
        val fixed = Combat.getCombatDamage(
            formulaType = FormulaType.DAMAGE,
            mina = 10.0,
            maxa = 20.0,
            combatValues = 500 to 600,
            random = Random(7),
        )
        assertThat(fixed.primaryValue).isBetween(10, 20)
    }

    @Test
    fun `getCombatDamage applies the monk elemental bond to a spell cast`() {
        // combat.cpp:161-174: Monk CIP + non-empty instantSpellName +
        // params.combatType != COMBAT_HEALING + equipped weapon with a bond.
        val bonded = Combat.getCombatDamage(
            formulaType = FormulaType.DAMAGE,
            mina = 10.0,
            maxa = 20.0,
            combatType = CombatType.PHYSICALDAMAGE,
            hasWeapon = true,
            weaponElementalBond = CombatType.FIREDAMAGE,
            instantSpellName = "flurry of blows",
            casterVocation = Vocation.VOCATION_MONK_CIP,
            random = Random(7),
        )
        assertThat(bonded.primaryType).isEqualTo(CombatType.FIREDAMAGE)
    }

    @Test
    fun `getCombatDamage keeps the params type when the elemental bond is none`() {
        // combat.cpp:168-173 `if (bond != COMBAT_NONE) ... else params.combatType`
        val noBond = Combat.getCombatDamage(
            formulaType = FormulaType.DAMAGE,
            mina = 10.0,
            maxa = 20.0,
            combatType = CombatType.PHYSICALDAMAGE,
            hasWeapon = true,
            weaponElementalBond = CombatType.NONE,
            instantSpellName = "flurry of blows",
            casterVocation = Vocation.VOCATION_MONK_CIP,
            random = Random(7),
        )
        assertThat(noBond.primaryType).isEqualTo(CombatType.PHYSICALDAMAGE)
    }

    @Test
    fun `getCombatDamage skips the elemental bond without a weapon`() {
        // combat.cpp:163-174 the bond is only read when `weapon` is valid.
        val noWeapon = Combat.getCombatDamage(
            formulaType = FormulaType.DAMAGE,
            mina = 10.0,
            maxa = 20.0,
            combatType = CombatType.PHYSICALDAMAGE,
            hasWeapon = false,
            weaponElementalBond = CombatType.FIREDAMAGE,
            instantSpellName = "flurry of blows",
            casterVocation = Vocation.VOCATION_MONK_CIP,
            random = Random(7),
        )
        assertThat(noWeapon.primaryType).isEqualTo(CombatType.PHYSICALDAMAGE)
    }

    @Test
    fun `getCombatDamage skips the elemental bond for non-monk casters runes and heals`() {
        // combat.cpp:162 `getPlayerVocationEnum() == VOCATION_MONK_CIP`
        val knight = Combat.getCombatDamage(
            formulaType = FormulaType.DAMAGE,
            mina = 10.0,
            maxa = 20.0,
            combatType = CombatType.PHYSICALDAMAGE,
            hasWeapon = true,
            weaponElementalBond = CombatType.FIREDAMAGE,
            instantSpellName = "flurry of blows",
            casterVocation = Vocation.VOCATION_KNIGHT_CIP,
            random = Random(7),
        )
        assertThat(knight.primaryType).isEqualTo(CombatType.PHYSICALDAMAGE)

        // `!instantSpellName.empty()`: a rune-only cast has no bond.
        val runeOnly = Combat.getCombatDamage(
            formulaType = FormulaType.DAMAGE,
            mina = 10.0,
            maxa = 20.0,
            combatType = CombatType.PHYSICALDAMAGE,
            hasWeapon = true,
            weaponElementalBond = CombatType.FIREDAMAGE,
            runeSpellName = "adori",
            casterVocation = Vocation.VOCATION_MONK_CIP,
            random = Random(7),
        )
        assertThat(runeOnly.primaryType).isEqualTo(CombatType.PHYSICALDAMAGE)

        // `params.combatType != COMBAT_HEALING`: healing never binds.
        val heal = Combat.getCombatDamage(
            formulaType = FormulaType.DAMAGE,
            mina = 10.0,
            maxa = 20.0,
            combatType = CombatType.HEALING,
            hasWeapon = true,
            weaponElementalBond = CombatType.FIREDAMAGE,
            instantSpellName = "heal friend",
            casterVocation = Vocation.VOCATION_MONK_CIP,
            random = Random(7),
        )
        assertThat(heal.primaryType).isEqualTo(CombatType.HEALING)
    }

    @Test
    fun `applyAbsorbDamageModifications applies percent then flat then attacker increase`() {
        // creature.cpp:924-942
        assertThat(
            Combat.applyAbsorbDamageModifications(
                combatType = CombatType.FIREDAMAGE,
                damage = 100,
                absorbPercent = 20,
            )
        ).isEqualTo(80)

        assertThat(
            Combat.applyAbsorbDamageModifications(
                combatType = CombatType.FIREDAMAGE,
                damage = 100,
                absorbFlat = -30,
            )
        ).isEqualTo(70)

        assertThat(
            Combat.applyAbsorbDamageModifications(
                combatType = CombatType.FIREDAMAGE,
                damage = 50,
                absorbPercent = 15,
            )
        ).isEqualTo(42)

        assertThat(
            Combat.applyAbsorbDamageModifications(
                combatType = CombatType.FIREDAMAGE,
                damage = 100,
                increasePercent = 50,
            )
        ).isEqualTo(150)

        // percent (100 -> 80), flat (80 -> 70), increase (70 -> 105)
        assertThat(
            Combat.applyAbsorbDamageModifications(
                combatType = CombatType.FIREDAMAGE,
                damage = 100,
                absorbPercent = 20,
                absorbFlat = -10,
                increasePercent = 50,
            )
        ).isEqualTo(105)
    }

    @Test
    fun `applyAbsorbDamageModifications skips healing and zero damage`() {
        // creature.cpp:925 `combatType != COMBAT_HEALING && damage != 0`
        assertThat(
            Combat.applyAbsorbDamageModifications(
                combatType = CombatType.HEALING,
                damage = 100,
                absorbPercent = 50,
                absorbFlat = -30,
                increasePercent = 100,
            )
        ).isEqualTo(100)

        assertThat(
            Combat.applyAbsorbDamageModifications(
                combatType = CombatType.FIREDAMAGE,
                damage = 0,
                absorbPercent = 50,
                increasePercent = 100,
            )
        ).isEqualTo(0)
    }

    @Test
    fun `applyMantraAbsorb reduces elemental damage by the combined mantra`() {
        // combat.cpp:739-759. Individual mantra + (BUFF_MANTRA - 100).
        val target = FakeCombatTarget()
        target.mantraValue = 30

        assertThat(
            Combat.applyMantraAbsorb(
                target = target,
                combatType = CombatType.FIREDAMAGE,
                value = -100,
            )
        ).isEqualTo(-70)

        // shared mantra: buff 130 - 100 = 30, plus individual 10 = 40.
        target.mantraValue = 10
        target.buffValues[Buff.BUFF_MANTRA] = 130
        assertThat(
            Combat.applyMantraAbsorb(
                target = target,
                combatType = CombatType.ICEDAMAGE,
                value = -100,
            )
        ).isEqualTo(-60)

        // value is clamped at zero: -20 with 40 mantra stays -0.
        assertThat(
            Combat.applyMantraAbsorb(
                target = target,
                combatType = CombatType.ENERGYDAMAGE,
                value = -20,
            )
        ).isEqualTo(0)
    }

    @Test
    fun `applyMantraAbsorb only touches elemental damage below zero`() {
        // combat.cpp:753-757
        val target = FakeCombatTarget()
        target.mantraValue = 30

        assertThat(
            Combat.applyMantraAbsorb(target, CombatType.PHYSICALDAMAGE, -100)
        ).isEqualTo(-100)
        assertThat(
            Combat.applyMantraAbsorb(target, CombatType.FIREDAMAGE, 100)
        ).isEqualTo(100)

        val noMantra = FakeCombatTarget()
        assertThat(
            Combat.applyMantraAbsorb(noMantra, CombatType.EARTHDAMAGE, -100)
        ).isEqualTo(-100)
    }

    @Test
    fun `blockHit applies absorb and increase before immunity defense and armor`() {
        // creature.cpp:947-948 `applyAbsorbDamageModifications` runs first.
        val absorbed = Combat.blockHit(
            damage = 100,
            random = Random(1),
            combatType = CombatType.FIREDAMAGE,
            absorbPercent = 20,
        )
        assertThat(absorbed.first).isEqualTo(80)
        assertThat(absorbed.second).isEqualTo(BlockType.NONE)

        val increased = Combat.blockHit(
            damage = 100,
            random = Random(1),
            combatType = CombatType.FIREDAMAGE,
            increasePercent = 25,
        )
        assertThat(increased.first).isEqualTo(125)

        // absorb 20% (100 -> 80) then increase 25% (80 -> 100)
        val both = Combat.blockHit(
            damage = 100,
            random = Random(1),
            combatType = CombatType.FIREDAMAGE,
            absorbPercent = 20,
            increasePercent = 25,
        )
        assertThat(both.first).isEqualTo(100)

        // armor reduction runs on the post-absorb damage:
        // 100 -> absorb 50% -> 50, armor 20 -> uniform_random(10, 19).
        val armored = Combat.blockHit(
            damage = 100,
            random = Random(1),
            checkArmor = true,
            armor = 20,
            combatType = CombatType.FIREDAMAGE,
            absorbPercent = 50,
        )
        assertThat(armored.first).isBetween(31, 40)
    }

    @Test
    fun `doCombatHealth applies the target absorb the attacker increase and mantra`() {
        // game.cpp:7932/8007 applyMantraAbsorb then blockHit -> absorb/increase.
        val absorbed = FakeCombatTarget(health = 1000)
        absorbed.absorbPercentByType[CombatType.FIREDAMAGE] = 50
        Combat.doCombatHealth(
            caster = null,
            target = absorbed,
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.FIREDAMAGE),
            random = Random(1),
        )
        assertThat(absorbed.getHealth()).isEqualTo(950)

        val increased = FakeCombatTarget(health = 1000)
        val caster = FakeCombatTarget()
        caster.increasePercentByType[CombatType.FIREDAMAGE] = 50
        Combat.doCombatHealth(
            caster = caster,
            target = increased,
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.FIREDAMAGE, aggressive = false),
            random = Random(1),
        )
        assertThat(increased.getHealth()).isEqualTo(850)

        // Canary negates the damage to a positive value at game.cpp:7911 before
        // calling applyMantraAbsorb at game.cpp:7932, and the function only
        // reduces `value < 0` (combat.cpp:755). The call site is therefore
        // inert: a mantra never changes doCombatHealth damage. Transcribed
        // exactly (even though it looks like a Canary oversight).
        val mantra = FakeCombatTarget(health = 1000)
        mantra.mantraValue = 30
        Combat.doCombatHealth(
            caster = null,
            target = mantra,
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.FIREDAMAGE),
            random = Random(1),
        )
        assertThat(mantra.getHealth()).isEqualTo(900)

        // flat absorb + percentage absorb compose exactly like the C++ order.
        val flat = FakeCombatTarget(health = 1000)
        flat.absorbPercentByType[CombatType.FIREDAMAGE] = 20
        flat.absorbFlatByType[CombatType.FIREDAMAGE] = -10
        Combat.doCombatHealth(
            caster = null,
            target = flat,
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.FIREDAMAGE),
            random = Random(1),
        )
        assertThat(flat.getHealth()).isEqualTo(930)
    }
}
