package org.opentibia.canary.creatures.vocations

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.wheel.WheelGemSupremeModifier
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transcript tests for the remaining `Vocation` runtime, transcribed from
 * `Vocation::getManaGainTicks` / `getManaGainAmount` / `getHealthGainTicks` /
 * `getHealthGainAmount` / `getSoulGainTicks` / `getAttackSpeed`
 * (src/creatures/players/vocations/vocation.cpp:337-367),
 * `Vocation::getSupremeGemModifiers` (vocation.cpp:385-403) and
 * `Vocation::getWheelGemId` (vocation.cpp:405-411).
 *
 * The `g_configManager().getFloat(RATE_*)` reads are replaced by the injected
 * [VocationRates], per AGENTS.md 1.1.
 */
class VocationTest {

    private fun realXml(): String {
        val candidates = listOf(
            Path.of("data/XML/vocations.xml"),
            Path.of("../data/XML/vocations.xml"),
            Path.of("../../data/XML/vocations.xml"),
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("data/XML/vocations.xml not found from ${System.getProperty("user.dir")}")
        return Files.readString(path)
    }

    private fun loaded(
        rates: VocationRates = VocationRates(),
        itemIdByName: (String) -> Int = { 0 },
    ): Vocations = Vocations(rates, itemIdByName).apply {
        assertThat(loadFromXml(realXml())).isTrue()
    }

    @Test
    fun `gain getters return the xml values at the default rates`() {
        val knight = loaded().getVocation(4)!!

        assertThat(knight.getManaGainTicks()).isEqualTo(6000)
        assertThat(knight.getManaGainAmount()).isEqualTo(2)
        assertThat(knight.getHealthGainTicks()).isEqualTo(6000)
        assertThat(knight.getHealthGainAmount()).isEqualTo(1)
        assertThat(knight.getSoulGainTicks()).isEqualTo(120000)
        assertThat(knight.getAttackSpeed()).isEqualTo(2000)
    }

    @Test
    fun `gain ticks divide by the regen speed rates`() {
        val rates = VocationRates(
            rateManaRegenSpeed = 2.0f,
            rateHealthRegenSpeed = 2.5f,
            rateSoulRegenSpeed = 0.5f,
        )
        val knight = loaded(rates).getVocation(4)!!

        assertThat(knight.getManaGainTicks()).isEqualTo(3000)
        assertThat(knight.getHealthGainTicks()).isEqualTo(2400)
        assertThat(knight.getSoulGainTicks()).isEqualTo(240000)
    }

    @Test
    fun `gain ticks truncate the float division like the cpp cast`() {
        val rates = VocationRates(rateManaRegenSpeed = 1.3f)
        val knight = loaded(rates).getVocation(4)!!

        assertThat(knight.getManaGainTicks()).isEqualTo(4615)
    }

    @Test
    fun `gain amounts multiply by the regen rates and truncate`() {
        val rates = VocationRates(rateManaRegen = 1.25f, rateHealthRegen = 2.5f)
        val knight = loaded(rates).getVocation(4)!!

        assertThat(knight.getManaGainAmount()).isEqualTo(2)
        assertThat(knight.getHealthGainAmount()).isEqualTo(2)
    }

    @Test
    fun `attack speed divides by the attack speed rate`() {
        val rates = VocationRates(rateAttackSpeed = 2.0f)
        val knight = loaded(rates).getVocation(4)!!

        assertThat(knight.getAttackSpeed()).isEqualTo(1000)
    }

    @Test
    fun `getSupremeGemModifiers returns general and base vocation modifiers`() {
        val knight = loaded().getVocation(4)!!.getSupremeGemModifiers()

        assertThat(knight).hasSize(23)
        assertThat(knight.take(2)).containsExactly(
            WheelGemSupremeModifier.General_Dodge,
            WheelGemSupremeModifier.General_CriticalDamage,
        )
        assertThat(knight[5]).isEqualTo(WheelGemSupremeModifier.Knight_AvatarOfSteel_Cooldown)
        assertThat(knight).contains(
            WheelGemSupremeModifier.General_LifeLeech,
            WheelGemSupremeModifier.General_RevelationMastery_GiftOfLife,
            WheelGemSupremeModifier.Knight_Annihilation_DamageIncrease,
            WheelGemSupremeModifier.Knight_RevelationMastery_CombatMastery,
        )
        assertThat(knight).doesNotContain(
            WheelGemSupremeModifier.SorcererDruid_UltimateHealing,
            WheelGemSupremeModifier.Paladin_AvatarOfLight_Cooldown,
            WheelGemSupremeModifier.Monk_AvatarOfBalance_Cooldown,
        )
    }

    @Test
    fun `getSupremeGemModifiers resolves the base vocation of a promoted vocation`() {
        val vocations = loaded()

        assertThat(vocations.getVocation(5)!!.getSupremeGemModifiers())
            .isEqualTo(vocations.getVocation(1)!!.getSupremeGemModifiers())
        assertThat(vocations.getVocation(8)!!.getSupremeGemModifiers())
            .isEqualTo(vocations.getVocation(4)!!.getSupremeGemModifiers())
        assertThat(vocations.getVocation(10)!!.getSupremeGemModifiers())
            .isEqualTo(vocations.getVocation(9)!!.getSupremeGemModifiers())
    }

    @Test
    fun `getSupremeGemModifiers for the none vocation only matches the general prefix`() {
        val none = loaded().getVocation(0)!!.getSupremeGemModifiers()

        assertThat(none).hasSize(5)
        assertThat(none).allMatch { it.name.startsWith("General_") }
    }

    @Test
    fun `getWheelGemId resolves gem names through the injected item lookup`() {
        val gems = mapOf(
            "lesser sage gem" to 35001,
            "sage gem" to 35002,
            "greater sage gem" to 35003,
        )
        val masterSorcerer = loaded(itemIdByName = { gems[it] ?: 0 }).getVocation(5)!!

        assertThat(masterSorcerer.getWheelGemId(0)).isEqualTo(35001)
        assertThat(masterSorcerer.getWheelGemId(1)).isEqualTo(35002)
        assertThat(masterSorcerer.getWheelGemId(2)).isEqualTo(35003)
    }

    @Test
    fun `getWheelGemId returns zero without a gem for the quality`() {
        var lookups = 0
        val vocations = loaded(itemIdByName = { lookups++; 1 })

        val knight = vocations.getVocation(4)!!
        assertThat(knight.getWheelGemId(0)).isZero()

        val masterSorcerer = vocations.getVocation(5)!!
        assertThat(masterSorcerer.getWheelGemId(9)).isZero()

        assertThat(lookups).isZero()
    }
}
