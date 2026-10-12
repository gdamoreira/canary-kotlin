package org.opentibia.canary.systems.proficiency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.FeatureFlags
import java.io.File

/**
 * Transcript tests for the weapon proficiency JSON loader.
 *
 * Expectations are transcribed from Canary's src/utils/tools.cpp
 * (getCombatFromCipbiaElement / getSkillsFromCipbiaSkill / undoShift),
 * src/creatures/players/components/weapon_proficiency.cpp
 * (registerPerks / registerLevels / loadFromJson) and the real
 * data/items/proficiencies.json shipped with the port.
 */
class WeaponProficiencyLoaderTest {

    private val realJson: String = File("data/items/proficiencies.json").readText()

    @Test
    fun `loads every proficiency from the real file`() {
        val proficiencies = WeaponProficiencyLoader.loadFromJson(realJson)
        assertThat(proficiencies).hasSize(420)
        assertThat(proficiencies.keys).contains(6)
        assertThat(proficiencies[6]).isNotNull()
    }

    @Test
    fun `first proficiency matches canary id levels and perk assignment`() {
        val proficiency = WeaponProficiencyLoader.loadFromJson(realJson).getValue(6)

        assertThat(proficiency.id).isEqualTo(6)
        assertThat(proficiency.maxLevel).isEqualTo(7)
        assertThat(proficiency.level).hasSize(7)

        val first = proficiency.level[0].perks.single()
        assertThat(first.index).isEqualTo(0)
        assertThat(first.level).isEqualTo(0)
        assertThat(first.type).isEqualTo(WeaponProficiencyBonus.SKILL_BONUS)
        assertThat(first.value).isEqualTo(1.0)
        assertThat(first.skillId).isEqualTo(SKILL_SWORD)

        val augment = proficiency.level[2].perks[1]
        assertThat(augment.index).isEqualTo(1)
        assertThat(augment.level).isEqualTo(2)
        assertThat(augment.type).isEqualTo(WeaponProficiencyBonus.SPELL_AUGMENT)
        assertThat(augment.value).isEqualTo(0.1)
        assertThat(augment.augmentType).isEqualTo(16)
        assertThat(augment.spellId).isEqualTo(105)
    }

    @Test
    fun `element id is unshifted then mapped through the cipbia table`() {
        val proficiency = WeaponProficiencyLoader.loadFromJson(realJson).getValue(15)

        val energy = proficiency.level[1].perks[0]
        assertThat(energy.type).isEqualTo(WeaponProficiencyBonus.ELEMENTAL_CRITICAL_EXTRA_DAMAGE)
        assertThat(energy.value).isEqualTo(0.1)
        assertThat(energy.element).isEqualTo(COMBAT_ENERGYDAMAGE)

        val fire = proficiency.level[1].perks[1]
        assertThat(fire.element).isEqualTo(COMBAT_FIREDAMAGE)
    }

    @Test
    fun `specialized magic level reads the damage type as its element`() {
        val specialized = WeaponProficiencyLoader.loadFromJson(realJson).getValue(15).level[4].perks[0]

        assertThat(specialized.type).isEqualTo(WeaponProficiencyBonus.SPECIALIZED_MAGIC_LEVEL)
        assertThat(specialized.value).isEqualTo(2.0)
        assertThat(specialized.element).isEqualTo(COMBAT_ENERGYDAMAGE)
    }

    @Test
    fun `bestiary perk keeps id and name`() {
        val bestiary = WeaponProficiencyLoader.loadFromJson(realJson).getValue(27).level[2].perks[0]

        assertThat(bestiary.type).isEqualTo(WeaponProficiencyBonus.WEAPON_PROFICIENCY_BESTIARY)
        assertThat(bestiary.bestiaryId).isEqualTo(21)
        assertThat(bestiary.bestiaryName).isEqualTo("Inkborn")
        assertThat(bestiary.value).isEqualTo(0.03)
    }

    @Test
    fun `range perk is parsed`() {
        val ranged = WeaponProficiencyLoader.loadFromJson(realJson).getValue(13).level[1].perks[0]

        assertThat(ranged.type).isEqualTo(WeaponProficiencyBonus.PERFECT_SHOT_DAMAGE)
        assertThat(ranged.range).isEqualTo(3)
        assertThat(ranged.value).isEqualTo(10.0)
    }

    @Test
    fun `absent optional fields keep the canary defaults`() {
        val perk = WeaponProficiencyLoader.loadFromJson(realJson).getValue(6).level[1].perks[0]

        assertThat(perk.type).isEqualTo(WeaponProficiencyBonus.AUTO_ATTACK_CRITICAL_EXTRA_DAMAGE)
        assertThat(perk.skillId).isEqualTo(SKILL_NONE)
        assertThat(perk.element).isEqualTo(COMBAT_NONE)
        assertThat(perk.spellId).isEqualTo(0)
        assertThat(perk.range).isEqualTo(0)
        assertThat(perk.bestiaryId).isEqualTo(0)
        assertThat(perk.bestiaryName).isEmpty()
        assertThat(perk.augmentType).isEqualTo(0)
    }

    @Test
    fun `max levels and max perks per level truncate like the canary config`() {
        val limited = WeaponProficiencyLoader.loadFromJson(
            realJson,
            maxLevels = 3,
            maxPerksPerLevel = 1,
        ).getValue(6)

        assertThat(limited.maxLevel).isEqualTo(3)
        assertThat(limited.level).hasSize(3)
        assertThat(limited.level[1].perks).hasSize(1)
    }

    @Test
    fun `feature flags cap proficiency levels and perks`() {
        val limited = WeaponProficiencyLoader.loadFromJson(
            realJson,
            FeatureFlags(weaponProficiencyMaxLevels = 3, weaponProficiencyMaxPerksPerLevel = 1),
        ).getValue(6)

        assertThat(limited.maxLevel).isEqualTo(3)
        assertThat(limited.level).hasSize(3)
        assertThat(limited.level[1].perks).hasSize(1)
    }

    @Test
    fun `reload reads the real file from disk`() {
        val proficiencies = WeaponProficiencyLoader.reload(File("data/items/proficiencies.json").toPath())

        assertThat(proficiencies).hasSize(420)
        assertThat(proficiencies.getValue(6).level[0].perks.single().skillId).isEqualTo(SKILL_SWORD)
    }
}
