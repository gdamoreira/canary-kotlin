package org.opentibia.canary.systems.wheel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WheelGemsTest {

    @Test
    fun `basic slot 1 allowed list matches canary wheelGemBasicSlot1Allowed`() {
        val allowed = WheelGems.basicSlot1Allowed
        assertThat(allowed).hasSize(20)
        assertThat(allowed.first()).isEqualTo(WheelGemBasicModifier.General_FireResistance)
        assertThat(allowed.last()).isEqualTo(WheelGemBasicModifier.Vocation_Capacity_IceResistance)
        assertThat(allowed).doesNotContain(
            WheelGemBasicModifier.General_PhysicalResistance,
            WheelGemBasicModifier.General_HolyResistance,
            WheelGemBasicModifier.Vocation_Mixed,
        )
    }

    @Test
    fun `basic slot 2 allowed list matches canary wheelGemBasicSlot2Allowed`() {
        val allowed = WheelGems.basicSlot2Allowed
        assertThat(allowed).hasSize(30)
        assertThat(allowed).contains(
            WheelGemBasicModifier.General_PhysicalResistance,
            WheelGemBasicModifier.General_HolyResistance,
            WheelGemBasicModifier.General_HolyResistance_DeathWeakness,
            WheelGemBasicModifier.General_MitigationMultiplier,
        )
        assertThat(allowed.last()).isEqualTo(WheelGemBasicModifier.General_MitigationMultiplier)
        assertThat(allowed).doesNotContain(WheelGemBasicModifier.Vocation_Mixed)
    }

    @Test
    fun `basic position list matches canary modsBasicPosition`() {
        val position = WheelGems.modsBasicPosition
        assertThat(position).hasSize(46)
        assertThat(position.first()).isEqualTo(WheelGemBasicModifier.General_PhysicalResistance)
        assertThat(position.last()).isEqualTo(WheelGemBasicModifier.Vocation_Capacity)
        assertThat(position).doesNotContain(WheelGemBasicModifier.Vocation_Mixed)
    }

    @Test
    fun `supreme position lists match canary per-vocation vectors`() {
        assertThat(WheelGems.supremeKnightPosition).hasSize(23)
        assertThat(WheelGems.supremeKnightPosition.first()).isEqualTo(WheelGemSupremeModifier.General_Dodge)
        assertThat(WheelGems.supremeKnightPosition.last()).isEqualTo(WheelGemSupremeModifier.Knight_RevelationMastery_CombatMastery)
        assertThat(WheelGems.supremePaladinPosition).hasSize(23)
        assertThat(WheelGems.supremePaladinPosition.last()).isEqualTo(WheelGemSupremeModifier.Paladin_RevelationMastery_DivineEmpowerment)
        assertThat(WheelGems.supremeSorcererPosition).hasSize(23)
        assertThat(WheelGems.supremeSorcererPosition).contains(WheelGemSupremeModifier.SorcererDruid_UltimateHealing)
        assertThat(WheelGems.supremeDruidPosition).hasSize(23)
        assertThat(WheelGems.supremeDruidPosition).contains(WheelGemSupremeModifier.SorcererDruid_UltimateHealing)
        assertThat(WheelGems.supremeMonkPosition).hasSize(23)
        assertThat(WheelGems.supremeMonkPosition.last()).isEqualTo(WheelGemSupremeModifier.Monk_RevelationMastery_Ascetic)
    }

    @Test
    fun `supreme position map keys match canary vocation values`() {
        val byVocation = WheelGems.supremePositionByVocation
        assertThat(byVocation).containsOnlyKeys(1, 2, 3, 4, 9)
        assertThat(byVocation[1]).isSameAs(WheelGems.supremeSorcererPosition)
        assertThat(byVocation[2]).isSameAs(WheelGems.supremeDruidPosition)
        assertThat(byVocation[3]).isSameAs(WheelGems.supremePaladinPosition)
        assertThat(byVocation[4]).isSameAs(WheelGems.supremeKnightPosition)
        assertThat(byVocation[9]).isSameAs(WheelGems.supremeMonkPosition)
    }

    @Test
    fun `selectBasicModifier2 never returns the first modifier`() {
        val first = WheelGems.basicSlot2Allowed.first()

        var calls = 0
        val sequence = intArrayOf(0, 1)
        val random = WheelRandom { _, _ -> sequence[calls++] }

        val selected = WheelGems.selectBasicModifier2(first, random)

        assertThat(selected).isEqualTo(WheelGemBasicModifier.General_IceResistance)
        assertThat(selected).isNotEqualTo(first)
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `selectBasicModifier2 returns a member of the slot 2 allowed list`() {
        val modifier1 = WheelGemBasicModifier.Vocation_Health
        for (seed in 0 until 50) {
            val random = WheelRandom { min, max -> min + (seed * 7) % (max - min + 1) }
            val selected = WheelGems.selectBasicModifier2(modifier1, random)
            assertThat(selected).isIn(WheelGems.basicSlot2Allowed)
            assertThat(selected).isNotEqualTo(modifier1)
        }
    }

    @Test
    fun `health values match canary WheelGemUtils`() {
        assertThat(WheelGemUtils.getHealthValue(WheelVocation.Knight, WheelGemBasicModifier.Vocation_Health)).isEqualTo(300)
        assertThat(WheelGemUtils.getHealthValue(WheelVocation.Sorcerer, WheelGemBasicModifier.Vocation_Health)).isEqualTo(100)
        assertThat(WheelGemUtils.getHealthValue(WheelVocation.Knight, WheelGemBasicModifier.Vocation_Mixed)).isEqualTo(150)
        assertThat(WheelGemUtils.getHealthValue(WheelVocation.Knight, WheelGemBasicModifier.Vocation_Mana)).isEqualTo(0)
    }

    @Test
    fun `mana values match canary WheelGemUtils`() {
        assertThat(WheelGemUtils.getManaValue(WheelVocation.Sorcerer, WheelGemBasicModifier.Vocation_Mana)).isEqualTo(600)
        assertThat(WheelGemUtils.getManaValue(WheelVocation.Knight, WheelGemBasicModifier.Vocation_Mana)).isEqualTo(100)
        assertThat(WheelGemUtils.getManaValue(WheelVocation.Knight, WheelGemBasicModifier.Vocation_Mixed)).isEqualTo(0)
        assertThat(WheelGemUtils.getManaValue(WheelVocation.Paladin, WheelGemBasicModifier.Vocation_Mixed)).isEqualTo(100)
    }

    @Test
    fun `capacity values match canary WheelGemUtils`() {
        assertThat(WheelGemUtils.getCapacityValue(WheelVocation.Knight, WheelGemBasicModifier.Vocation_Capacity)).isEqualTo(500)
        assertThat(WheelGemUtils.getCapacityValue(WheelVocation.Paladin, WheelGemBasicModifier.Vocation_Capacity)).isEqualTo(400)
        assertThat(WheelGemUtils.getCapacityValue(WheelVocation.Knight, WheelGemBasicModifier.Vocation_Mixed)).isEqualTo(125)
        assertThat(WheelGemUtils.getCapacityValue(WheelVocation.Monk, WheelGemBasicModifier.Vocation_Capacity)).isEqualTo(0)
    }
}
