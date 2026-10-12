package org.opentibia.canary.systems.proficiency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatType

/**
 * Transcript tests for the weapon-proficiency combat-application methods, ported
 * from `src/creatures/players/components/weapon_proficiency.cpp`:
 * `applyAutoAttackCritical`, `applyRunesCritical`, `applyElementCritical`,
 * `applyBestiaryDamage`, `applyPowerfulFoeDamage`,
 * `applySkillAutoAttackPercentage`, `applySkillSpellPercentage`,
 * `applySpellAugment`.
 */
class WeaponProficiencyCombatTest {

    private val player = FakePlayer()
    private val proficiency = WeaponProficiency(player)

    // ---- applyAutoAttackCritical ---------------------------------------------

    @Test
    fun `auto attack critical only applies to fist melee and ranged`() {
        proficiency.addAutoAttackCritical(WeaponProficiencyCriticalBonus(chance = 0.5, damage = 1.0))

        val melee = CombatDamage(origin = CombatOrigin.MELEE, criticalChance = 200, criticalDamage = 3000)
        proficiency.applyAutoAttackCritical(melee)
        assertThat(melee.criticalChance).isEqualTo(5200)
        assertThat(melee.criticalDamage).isEqualTo(13000)

        val spell = CombatDamage(origin = CombatOrigin.SPELL, criticalChance = 200, criticalDamage = 3000)
        proficiency.applyAutoAttackCritical(spell)
        assertThat(spell.criticalChance).isEqualTo(200)
        assertThat(spell.criticalDamage).isEqualTo(3000)
    }

    // ---- applyRunesCritical ---------------------------------------------------

    @Test
    fun `runes critical requires a rune name and an aggressive cast`() {
        proficiency.addRunesCritical(WeaponProficiencyCriticalBonus(chance = 0.25, damage = 0.5))

        val rune = CombatDamage(runeSpellName = "adori")
        proficiency.applyRunesCritical(rune, aggressive = true)
        assertThat(rune.criticalChance).isEqualTo(2500)
        assertThat(rune.criticalDamage).isEqualTo(5000)

        val passive = CombatDamage(runeSpellName = "adori")
        proficiency.applyRunesCritical(passive, aggressive = false)
        assertThat(passive.criticalChance).isEqualTo(0)
        assertThat(passive.criticalDamage).isEqualTo(0)

        val spell = CombatDamage()
        proficiency.applyRunesCritical(spell, aggressive = true)
        assertThat(spell.criticalChance).isEqualTo(0)
    }

    // ---- applyElementCritical -------------------------------------------------

    @Test
    fun `element critical only applies to the matching element`() {
        proficiency.addElementCritical(
            CombatType.FIREDAMAGE,
            WeaponProficiencyCriticalBonus(chance = 0.2, damage = 0.5),
        )

        val fire = CombatDamage(primaryType = CombatType.FIREDAMAGE)
        proficiency.applyElementCritical(fire)
        assertThat(fire.criticalChance).isEqualTo(2000)
        assertThat(fire.criticalDamage).isEqualTo(5000)

        val energy = CombatDamage(primaryType = CombatType.ENERGYDAMAGE)
        proficiency.applyElementCritical(energy)
        assertThat(energy.criticalChance).isEqualTo(0)

        val none = CombatDamage(primaryType = CombatType.NONE)
        proficiency.applyElementCritical(none)
        assertThat(none.criticalChance).isEqualTo(0)

        val neutral = CombatDamage(primaryType = CombatType.NEUTRALDAMAGE)
        proficiency.applyElementCritical(neutral)
        assertThat(neutral.criticalChance).isEqualTo(0)
    }

    @Test
    fun `element critical skips the count sentinel`() {
        proficiency.addElementCritical(
            CombatType.COUNT,
            WeaponProficiencyCriticalBonus(chance = 0.2, damage = 0.5),
        )

        val count = CombatDamage(primaryType = CombatType.COUNT)
        proficiency.applyElementCritical(count)
        assertThat(count.criticalChance).isEqualTo(0)
    }

    // ---- applyBestiaryDamage --------------------------------------------------

    @Test
    fun `bestiary damage multiplies primary and secondary by the race bonus`() {
        proficiency.addBestiaryDamage(14, 0.1)
        val monster = FakeMonster(FakeMonsterType(bestiaryRace = 14, isBoss = false), forgeStack = 0)

        val damage = CombatDamage(
            primaryType = CombatType.PHYSICALDAMAGE,
            primaryValue = 100,
            secondaryType = CombatType.FIREDAMAGE,
            secondaryValue = 50,
        )
        proficiency.applyBestiaryDamage(damage, monster)

        assertThat(damage.primaryValue).isEqualTo(110)
        assertThat(damage.secondaryValue).isEqualTo(55)
    }

    @Test
    fun `bestiary damage is skipped for a zero race or a missing monster`() {
        proficiency.addBestiaryDamage(14, 0.1)
        val noRace = FakeMonster(FakeMonsterType(bestiaryRace = 0, isBoss = false), forgeStack = 0)

        val damage = CombatDamage(primaryValue = 100, secondaryValue = 50)
        proficiency.applyBestiaryDamage(damage, noRace)
        assertThat(damage.primaryValue).isEqualTo(100)
        assertThat(damage.secondaryValue).isEqualTo(50)

        proficiency.applyBestiaryDamage(damage, null)
        assertThat(damage.primaryValue).isEqualTo(100)

        val noType = FakeMonster(null, forgeStack = 0)
        proficiency.applyBestiaryDamage(damage, noType)
        assertThat(damage.primaryValue).isEqualTo(100)
    }

    // ---- applyPowerfulFoeDamage ----------------------------------------------

    @Test
    fun `powerful foe damage applies to bosses and forged monsters`() {
        proficiency.addPowerfulFoeDamage(0.05)

        val damage = CombatDamage(primaryValue = 100, secondaryValue = 50)
        proficiency.applyPowerfulFoeDamage(damage, FakeMonster(FakeMonsterType(1, false), forgeStack = 1))
        assertThat(damage.primaryValue).isEqualTo(105)
        assertThat(damage.secondaryValue).isEqualTo(52)

        val boss = CombatDamage(primaryValue = 100)
        proficiency.applyPowerfulFoeDamage(boss, FakeMonster(FakeMonsterType(1, isBoss = true), forgeStack = 0))
        assertThat(boss.primaryValue).isEqualTo(105)
    }

    @Test
    fun `powerful foe damage is skipped for a plain monster`() {
        proficiency.addPowerfulFoeDamage(0.05)

        val damage = CombatDamage(primaryValue = 100)
        proficiency.applyPowerfulFoeDamage(damage, FakeMonster(FakeMonsterType(1, isBoss = false), forgeStack = 0))

        assertThat(damage.primaryValue).isEqualTo(100)
        proficiency.applyPowerfulFoeDamage(damage, null)
        assertThat(damage.primaryValue).isEqualTo(100)
    }

    // ---- applySkillAutoAttackPercentage --------------------------------------

    @Test
    fun `skill auto attack percentage subtracts the skill bonus`() {
        val swordPlayer = FakePlayer(skills = mapOf(SKILL_SWORD to 100))
        val prof = WeaponProficiency(swordPlayer)
        prof.addSkillPercentage(SKILL_SWORD, SkillPercentageType.AutoAttack, 0.5)

        val damage = CombatDamage(
            origin = CombatOrigin.MELEE,
            primaryType = CombatType.PHYSICALDAMAGE,
            primaryValue = 500,
            secondaryType = CombatType.FIREDAMAGE,
            secondaryValue = 200,
        )
        prof.applySkillAutoAttackPercentage(damage)

        assertThat(damage.primaryValue).isEqualTo(450)
        assertThat(damage.secondaryValue).isEqualTo(150)
    }

    @Test
    fun `skill auto attack percentage ignores non auto attack origins and missing weapons`() {
        val prof = WeaponProficiency(FakePlayer(skills = mapOf(SKILL_SWORD to 100)))
        prof.addSkillPercentage(SKILL_SWORD, SkillPercentageType.AutoAttack, 0.5)

        val spell = CombatDamage(origin = CombatOrigin.SPELL, primaryType = CombatType.PHYSICALDAMAGE, primaryValue = 500)
        prof.applySkillAutoAttackPercentage(spell)
        assertThat(spell.primaryValue).isEqualTo(500)

        val unarmed = WeaponProficiency(FakePlayer(skills = mapOf(SKILL_SWORD to 100), hasWeapon = false))
        unarmed.addSkillPercentage(SKILL_SWORD, SkillPercentageType.AutoAttack, 0.5)
        val melee = CombatDamage(origin = CombatOrigin.MELEE, primaryType = CombatType.PHYSICALDAMAGE, primaryValue = 500)
        unarmed.applySkillAutoAttackPercentage(melee)
        assertThat(melee.primaryValue).isEqualTo(500)
    }

    // ---- applySkillSpellPercentage -------------------------------------------

    @Test
    fun `skill spell percentage adds bonus damage to spells`() {
        val prof = WeaponProficiency(FakePlayer(skills = mapOf(SKILL_MAGLEVEL to 80)))
        prof.addSkillPercentage(SKILL_MAGLEVEL, SkillPercentageType.SpellDamage, 0.5)

        val damage = CombatDamage(
            primaryType = CombatType.PHYSICALDAMAGE,
            primaryValue = 500,
            instantSpellName = "exori",
        )
        prof.applySkillSpellPercentage(damage)

        assertThat(damage.primaryValue).isEqualTo(540)
    }

    @Test
    fun `skill spell percentage adds bonus healing to healing spells`() {
        val prof = WeaponProficiency(FakePlayer(skills = mapOf(SKILL_MAGLEVEL to 80)))
        prof.addSkillPercentage(SKILL_MAGLEVEL, SkillPercentageType.SpellHealing, 0.5)

        val damage = CombatDamage(
            primaryType = CombatType.HEALING,
            primaryValue = 500,
            instantSpellName = "exura",
        )
        prof.applySkillSpellPercentage(damage, healing = true)

        assertThat(damage.primaryValue).isEqualTo(540)
    }

    @Test
    fun `skill spell percentage preserves the damage sign`() {
        val prof = WeaponProficiency(FakePlayer(skills = mapOf(SKILL_MAGLEVEL to 80)))
        prof.addSkillPercentage(SKILL_MAGLEVEL, SkillPercentageType.SpellDamage, 0.5)

        val drain = CombatDamage(
            primaryType = CombatType.LIFEDRAIN,
            primaryValue = -500,
            instantSpellName = "exura gran",
        )
        prof.applySkillSpellPercentage(drain)

        assertThat(drain.primaryValue).isEqualTo(-540)
    }

    @Test
    fun `skill spell percentage ignores healing on non healing damage`() {
        val prof = WeaponProficiency(FakePlayer(skills = mapOf(SKILL_MAGLEVEL to 80)))
        prof.addSkillPercentage(SKILL_MAGLEVEL, SkillPercentageType.SpellHealing, 0.5)

        val damage = CombatDamage(
            primaryType = CombatType.PHYSICALDAMAGE,
            primaryValue = 500,
            instantSpellName = "exori",
        )
        prof.applySkillSpellPercentage(damage, healing = true)

        assertThat(damage.primaryValue).isEqualTo(500)
    }

    @Test
    fun `skill spell percentage requires an instant spell name`() {
        val prof = WeaponProficiency(FakePlayer(skills = mapOf(SKILL_MAGLEVEL to 80)))
        prof.addSkillPercentage(SKILL_MAGLEVEL, SkillPercentageType.SpellDamage, 0.5)

        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = 500)
        prof.applySkillSpellPercentage(damage)

        assertThat(damage.primaryValue).isEqualTo(500)
    }

    // ---- applySpellAugment ---------------------------------------------------

    @Test
    fun `spell augment applies every increase and leech component`() {
        proficiency.addSpellBonus(
            SPELL_ID,
            spellBonus(
                damage = 0.5,
                heal = 0.25,
                criticalChance = 0.02,
                criticalDamage = 0.1,
                lifeLeech = 0.03,
                manaLeech = 0.01,
            ),
        )

        val damage = CombatDamage()
        proficiency.applySpellAugment(damage, SPELL_ID)

        assertThat(damage.damageMultiplier).isEqualTo(50)
        assertThat(damage.healingMultiplier).isEqualTo(25)
        assertThat(damage.criticalChance).isEqualTo(200)
        assertThat(damage.criticalDamage).isEqualTo(1000)
        assertThat(damage.lifeLeech).isEqualTo(300)
        assertThat(damage.manaLeech).isEqualTo(100)
    }

    @Test
    fun `spell augment accumulates repeated bonuses`() {
        proficiency.addSpellBonus(SPELL_ID, spellBonus(damage = 0.1))
        proficiency.addSpellBonus(SPELL_ID, spellBonus(damage = 0.1))

        val damage = CombatDamage()
        proficiency.applySpellAugment(damage, SPELL_ID)

        assertThat(damage.damageMultiplier).isEqualTo(20)
    }

    @Test
    fun `spell augment ignores unknown spells`() {
        val damage = CombatDamage()
        proficiency.applySpellAugment(damage, 999)

        assertThat(damage.isEmpty()).isTrue()
    }

    // ---- fakes ---------------------------------------------------------------

    private class FakePlayer(
        private val skills: Map<Int, Int> = emptyMap(),
        private val hasWeapon: Boolean = true,
    ) : WeaponProficiencyPlayer {
        override fun getSkillLevel(skill: Int): Int = skills[skill] ?: 0
        override fun getWeapon(ignoreWeaponProficiency: Boolean): Boolean = hasWeapon
    }

    private class FakeMonsterType(
        override val bestiaryRace: Int,
        override val isBoss: Boolean,
    ) : ProficiencyMonsterType

    private class FakeMonster(
        override val monsterType: ProficiencyMonsterType?,
        override val forgeStack: Int,
    ) : ProficiencyMonster

    private companion object {
        const val SPELL_ID = 123

        fun spellBonus(
            damage: Double = 0.0,
            heal: Double = 0.0,
            criticalChance: Double = 0.0,
            criticalDamage: Double = 0.0,
            lifeLeech: Double = 0.0,
            manaLeech: Double = 0.0,
        ): WeaponProficiencySpellBonus = WeaponProficiencySpellBonus(
            increase = WeaponProficiencySpellIncrease(
                damage = damage,
                heal = heal,
                criticalChance = criticalChance,
                criticalDamage = criticalDamage,
            ),
            leech = WeaponProficiencySpellLeech(life = lifeLeech, mana = manaLeech),
        )
    }
}
