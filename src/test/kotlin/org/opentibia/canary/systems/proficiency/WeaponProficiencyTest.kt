package org.opentibia.canary.systems.proficiency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WeaponProficiencyTest {

    @Test
    fun `enum values preserve canary numbering`() {
        assertThat(WeaponProficiencyBonus.ATTACK_DAMAGE.value).isEqualTo(0)
        assertThat(WeaponProficiencyBonus.SKILL_BONUS.value).isEqualTo(3)
        assertThat(WeaponProficiencyBonus.ELEMENTAL_PIERCE.value).isEqualTo(31)
        assertThat(WeaponProficiencySpellBoost.CRITICAL_CHANCE.value).isEqualTo(12)
        assertThat(WeaponProficiencyExperienceTier.Hard.value).isEqualTo(3)
    }

    @Test
    fun `experience tables match canary`() {
        assertThat(WeaponProficiencyExperience.CROSSBOW_EXPERIENCE)
            .containsExactly(600, 8000, 30000, 150000, 650000, 2500000, 10000000, 20000000, 30000000)
        assertThat(WeaponProficiencyExperience.KNIGHT_EXPERIENCE)
            .containsExactly(1250, 20000, 80000, 300000, 1500000, 6000000, 20000000, 40000000, 60000000)
        assertThat(WeaponProficiencyExperience.STANDARD_EXPERIENCE)
            .containsExactly(1750, 25000, 100000, 400000, 2000000, 8000000, 30000000, 60000000, 90000000)
    }

    @Test
    fun `experience array is selected by weapon type`() {
        assertThat(WeaponProficiencyExperience.experienceArray(isBolt = true, isKnightWeapon = false))
            .isSameAs(WeaponProficiencyExperience.CROSSBOW_EXPERIENCE)
        assertThat(WeaponProficiencyExperience.experienceArray(isBolt = false, isKnightWeapon = true))
            .isSameAs(WeaponProficiencyExperience.KNIGHT_EXPERIENCE)
        assertThat(WeaponProficiencyExperience.experienceArray(isBolt = false, isKnightWeapon = false))
            .isSameAs(WeaponProficiencyExperience.STANDARD_EXPERIENCE)
    }

    @Test
    fun `mastery tier count applies the offset of two`() {
        assertThat(WeaponProficiencyExperience.masteryExperienceTierCount(5, WeaponProficiencyExperience.STANDARD_EXPERIENCE))
            .isEqualTo(7)
        assertThat(WeaponProficiencyExperience.masteryExperienceTierCount(0, WeaponProficiencyExperience.STANDARD_EXPERIENCE))
            .isEqualTo(0)
    }

    @Test
    fun `next level experience returns the remaining requirement`() {
        val array = WeaponProficiencyExperience.STANDARD_EXPERIENCE
        assertThat(WeaponProficiencyExperience.nextLevelExperience(0, 5, array)).isEqualTo(1750)
        assertThat(WeaponProficiencyExperience.nextLevelExperience(1750, 5, array)).isEqualTo(25000 - 1750)
        assertThat(WeaponProficiencyExperience.nextLevelExperience(90000000, 5, array)).isEqualTo(0)
    }

    @Test
    fun `max experience is the last mastery tier`() {
        assertThat(WeaponProficiencyExperience.maxExperience(5, WeaponProficiencyExperience.STANDARD_EXPERIENCE))
            .isEqualTo(30000000)
    }

    @Test
    fun `gain scaling rounds and clamps`() {
        assertThat(WeaponProficiencyExperience.scaleGain(100, 1.0)).isEqualTo(100)
        assertThat(WeaponProficiencyExperience.scaleGain(101, 0.5)).isEqualTo(51)
        assertThat(WeaponProficiencyExperience.scaleGain(100, -2.0)).isEqualTo(0)
    }

    @Test
    fun `bosstiary experience follows rarity`() {
        assertThat(WeaponProficiencyExperience.bosstiaryExperience(BosstiaryRarity.RARITY_BANE)).isEqualTo(500)
        assertThat(WeaponProficiencyExperience.bosstiaryExperience(BosstiaryRarity.RARITY_ARCHFOE)).isEqualTo(5000)
        assertThat(WeaponProficiencyExperience.bosstiaryExperience(BosstiaryRarity.RARITY_NEMESIS)).isEqualTo(15000)
    }

    @Test
    fun `bestiary experience uses the canary polynomial`() {
        assertThat(WeaponProficiencyExperience.bestiaryExperience(0)).isEqualTo(1)
        assertThat(WeaponProficiencyExperience.bestiaryExperience(5)).isEqualTo(240)
        assertThat(WeaponProficiencyExperience.bestiaryExperience(9)).isEqualTo(240)
    }
}
