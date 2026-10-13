package org.opentibia.canary.systems.wheel

/**
 * Mirror of Canary's `uniform_random` (src/utils/tools.hpp:90): inclusive on
 * both bounds.
 */
fun interface WheelRandom {
    fun uniformRandom(minNumber: Int, maxNumber: Int): Int
}

/**
 * Mirror of Canary's `normal_random` (src/utils/tools.hpp:91): the distribution
 * is centred on the range midpoint, so rolls cluster there rather than being
 * uniform.
 */
fun interface WheelNormalRandom {
    fun normalRandom(minNumber: Int, maxNumber: Int): Int
}

/**
 * Mirror of `enum Vocation_t` (src/creatures/creatures_definitions.hpp:790),
 * the internal vocation ids used by the wheel gem value tables.
 */
enum class WheelVocation(val value: Int) {
    None(0),
    Sorcerer(1),
    Druid(2),
    Paladin(3),
    Knight(4),
    MasterSorcerer(5),
    ElderDruid(6),
    RoyalPaladin(7),
    EliteKnight(8),
    Monk(9),
    ExaltedMonk(10),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }
        fun fromValue(value: Int): WheelVocation? = byValue[value]
    }
}

/**
 * Wheel gem modifier pools, ported verbatim from the static arrays in
 * src/creatures/players/components/wheel/player_wheel.cpp (lines 33-299).
 * `selectBasicModifier2` mirrors `PlayerWheel::selectBasicModifier2`.
 */
object WheelGems {
    val basicSlot1Allowed: List<WheelGemBasicModifier> = listOf(
        WheelGemBasicModifier.General_FireResistance,
        WheelGemBasicModifier.General_IceResistance,
        WheelGemBasicModifier.General_EnergyResistance,
        WheelGemBasicModifier.General_EarthResistance,
        WheelGemBasicModifier.General_MitigationMultiplier,
        WheelGemBasicModifier.Vocation_Health,
        WheelGemBasicModifier.Vocation_Mana,
        WheelGemBasicModifier.Vocation_Capacity,
        WheelGemBasicModifier.Vocation_Health_FireResistance,
        WheelGemBasicModifier.Vocation_Health_IceResistance,
        WheelGemBasicModifier.Vocation_Health_EnergyResistance,
        WheelGemBasicModifier.Vocation_Health_EarthResistance,
        WheelGemBasicModifier.Vocation_Mana_FireResistance,
        WheelGemBasicModifier.Vocation_Mana_EnergyResistance,
        WheelGemBasicModifier.Vocation_Mana_Earth_Resistance,
        WheelGemBasicModifier.Vocation_Mana_Ice_Resistance,
        WheelGemBasicModifier.Vocation_Capacity_FireResistance,
        WheelGemBasicModifier.Vocation_Capacity_EnergyResistance,
        WheelGemBasicModifier.Vocation_Capacity_EarthResistance,
        WheelGemBasicModifier.Vocation_Capacity_IceResistance,
    )

    val basicSlot2Allowed: List<WheelGemBasicModifier> = listOf(
        WheelGemBasicModifier.General_FireResistance,
        WheelGemBasicModifier.General_IceResistance,
        WheelGemBasicModifier.General_EnergyResistance,
        WheelGemBasicModifier.General_EarthResistance,
        WheelGemBasicModifier.General_PhysicalResistance,
        WheelGemBasicModifier.General_HolyResistance,
        WheelGemBasicModifier.General_HolyResistance_DeathWeakness,
        WheelGemBasicModifier.General_DeathResistance_HolyWeakness,
        WheelGemBasicModifier.General_FireResistance_EarthResistance,
        WheelGemBasicModifier.General_FireResistance_IceResistance,
        WheelGemBasicModifier.General_FireResistance_EnergyResistance,
        WheelGemBasicModifier.General_EarthResistance_IceResistance,
        WheelGemBasicModifier.General_EarthResistance_EnergyResistance,
        WheelGemBasicModifier.General_IceResistance_EnergyResistance,
        WheelGemBasicModifier.General_FireResistance_EarthWeakness,
        WheelGemBasicModifier.General_FireResistance_IceWeakness,
        WheelGemBasicModifier.General_FireResistance_EnergyWeakness,
        WheelGemBasicModifier.General_EarthResistance_FireWeakness,
        WheelGemBasicModifier.General_EarthResistance_IceWeakness,
        WheelGemBasicModifier.General_EarthResistance_EnergyWeakness,
        WheelGemBasicModifier.General_IceResistance_EarthWeakness,
        WheelGemBasicModifier.General_IceResistance_FireWeakness,
        WheelGemBasicModifier.General_IceResistance_EnergyWeakness,
        WheelGemBasicModifier.General_EnergyResistance_EarthWeakness,
        WheelGemBasicModifier.General_EnergyResistance_IceWeakness,
        WheelGemBasicModifier.General_EnergyResistance_FireWeakness,
        WheelGemBasicModifier.General_ManaDrainResistance,
        WheelGemBasicModifier.General_LifeDrainResistance,
        WheelGemBasicModifier.General_ManaDrainResistance_LifeDrainResistance,
        WheelGemBasicModifier.General_MitigationMultiplier,
    )

    val modsBasicPosition: List<WheelGemBasicModifier> = listOf(
        WheelGemBasicModifier.General_PhysicalResistance,
        WheelGemBasicModifier.General_HolyResistance,
        WheelGemBasicModifier.General_DeathResistance,
        WheelGemBasicModifier.General_FireResistance,
        WheelGemBasicModifier.General_EarthResistance,
        WheelGemBasicModifier.General_IceResistance,
        WheelGemBasicModifier.General_EnergyResistance,

        WheelGemBasicModifier.General_HolyResistance_DeathWeakness,
        WheelGemBasicModifier.General_DeathResistance_HolyWeakness,
        WheelGemBasicModifier.General_FireResistance_EarthResistance,
        WheelGemBasicModifier.General_FireResistance_IceResistance,
        WheelGemBasicModifier.General_FireResistance_EnergyResistance,
        WheelGemBasicModifier.General_EarthResistance_IceResistance,
        WheelGemBasicModifier.General_EarthResistance_EnergyResistance,
        WheelGemBasicModifier.General_IceResistance_EnergyResistance,

        WheelGemBasicModifier.General_FireResistance_EarthWeakness,
        WheelGemBasicModifier.General_FireResistance_IceWeakness,
        WheelGemBasicModifier.General_FireResistance_EnergyWeakness,
        WheelGemBasicModifier.General_EarthResistance_FireWeakness,
        WheelGemBasicModifier.General_EarthResistance_IceWeakness,
        WheelGemBasicModifier.General_EarthResistance_EnergyWeakness,
        WheelGemBasicModifier.General_IceResistance_EarthWeakness,
        WheelGemBasicModifier.General_IceResistance_FireWeakness,
        WheelGemBasicModifier.General_IceResistance_EnergyWeakness,
        WheelGemBasicModifier.General_EnergyResistance_EarthWeakness,
        WheelGemBasicModifier.General_EnergyResistance_IceWeakness,
        WheelGemBasicModifier.General_EnergyResistance_FireWeakness,
        WheelGemBasicModifier.General_ManaDrainResistance,
        WheelGemBasicModifier.General_LifeDrainResistance,
        WheelGemBasicModifier.General_ManaDrainResistance_LifeDrainResistance,
        WheelGemBasicModifier.General_MitigationMultiplier,

        WheelGemBasicModifier.Vocation_Health,
        WheelGemBasicModifier.Vocation_Mana_FireResistance,
        WheelGemBasicModifier.Vocation_Mana_EnergyResistance,
        WheelGemBasicModifier.Vocation_Mana_Earth_Resistance,
        WheelGemBasicModifier.Vocation_Mana_Ice_Resistance,
        WheelGemBasicModifier.Vocation_Mana,
        WheelGemBasicModifier.Vocation_Health_FireResistance,
        WheelGemBasicModifier.Vocation_Health_EnergyResistance,
        WheelGemBasicModifier.Vocation_Health_EarthResistance,
        WheelGemBasicModifier.Vocation_Health_IceResistance,

        WheelGemBasicModifier.Vocation_Capacity_FireResistance,
        WheelGemBasicModifier.Vocation_Capacity_EnergyResistance,
        WheelGemBasicModifier.Vocation_Capacity_EarthResistance,
        WheelGemBasicModifier.Vocation_Capacity_IceResistance,
        WheelGemBasicModifier.Vocation_Capacity,
    )

    val supremeKnightPosition: List<WheelGemSupremeModifier> = listOf(
        WheelGemSupremeModifier.General_Dodge,
        WheelGemSupremeModifier.General_CriticalDamage,
        WheelGemSupremeModifier.General_LifeLeech,
        WheelGemSupremeModifier.General_ManaLeech,
        WheelGemSupremeModifier.General_RevelationMastery_GiftOfLife,

        WheelGemSupremeModifier.Knight_AvatarOfSteel_Cooldown,
        WheelGemSupremeModifier.Knight_ExecutionersThrow_Cooldown,
        WheelGemSupremeModifier.Knight_ExecutionersThrow_DamageIncrease,
        WheelGemSupremeModifier.Knight_ExecutionersThrow_CriticalExtraDamage,
        WheelGemSupremeModifier.Knight_Fierce_Berserk_DamageIncrease,
        WheelGemSupremeModifier.Knight_Fierce_Berserk_CriticalExtraDamage,
        WheelGemSupremeModifier.Knight_Berserk_DamageIncrease,
        WheelGemSupremeModifier.Knight_Berserk_CriticalExtraDamage,
        WheelGemSupremeModifier.Knight_Front_Sweep_CriticalExtraDamage,
        WheelGemSupremeModifier.Knight_Front_Sweep_DamageIncrease,
        WheelGemSupremeModifier.Knight_Groundshaker_DamageIncrease,
        WheelGemSupremeModifier.Knight_Groundshaker_CriticalExtraDamage,
        WheelGemSupremeModifier.Knight_Annihilation_CriticalExtraDamage,
        WheelGemSupremeModifier.Knight_Annihilation_DamageIncrease,
        WheelGemSupremeModifier.Knight_FairWoundCleansing_HealingIncrease,
        WheelGemSupremeModifier.Knight_RevelationMastery_AvatarOfSteel,
        WheelGemSupremeModifier.Knight_RevelationMastery_ExecutionersThrow,
        WheelGemSupremeModifier.Knight_RevelationMastery_CombatMastery,
    )

    val supremePaladinPosition: List<WheelGemSupremeModifier> = listOf(
        WheelGemSupremeModifier.General_Dodge,
        WheelGemSupremeModifier.General_CriticalDamage,
        WheelGemSupremeModifier.General_LifeLeech,
        WheelGemSupremeModifier.General_ManaLeech,
        WheelGemSupremeModifier.General_RevelationMastery_GiftOfLife,

        WheelGemSupremeModifier.Paladin_AvatarOfLight_Cooldown,
        WheelGemSupremeModifier.Paladin_DivineDazzle_Cooldown,
        WheelGemSupremeModifier.Paladin_DivineGrenade_DamageIncrease,
        WheelGemSupremeModifier.Paladin_DivineGrenade_CriticalExtraDamage,
        WheelGemSupremeModifier.Paladin_DivineCaldera_DamageIncrease,
        WheelGemSupremeModifier.Paladin_DivineCaldera_CriticalExtraDamage,
        WheelGemSupremeModifier.Paladin_DivineMissile_DamageIncrease,
        WheelGemSupremeModifier.Paladin_DivineMissile_CriticalExtraDamage,
        WheelGemSupremeModifier.Paladin_EtherealSpear_DamageIncrease,
        WheelGemSupremeModifier.Paladin_EtherealSpear_CriticalExtraDamage,
        WheelGemSupremeModifier.Paladin_StrongEtherealSpear_DamageIncrease,
        WheelGemSupremeModifier.Paladin_StrongEtherealSpear_CriticalExtraDamage,
        WheelGemSupremeModifier.Paladin_DivineEmpowerment_Cooldown,
        WheelGemSupremeModifier.Paladin_DivineGrenade_Cooldown,
        WheelGemSupremeModifier.Paladin_Salvation_HealingIncrease,
        WheelGemSupremeModifier.Paladin_RevelationMastery_AvatarOfLight,
        WheelGemSupremeModifier.Paladin_RevelationMastery_DivineGrenade,
        WheelGemSupremeModifier.Paladin_RevelationMastery_DivineEmpowerment,
    )

    val supremeSorcererPosition: List<WheelGemSupremeModifier> = listOf(
        WheelGemSupremeModifier.General_Dodge,
        WheelGemSupremeModifier.General_CriticalDamage,
        WheelGemSupremeModifier.General_LifeLeech,
        WheelGemSupremeModifier.General_ManaLeech,
        WheelGemSupremeModifier.SorcererDruid_UltimateHealing,
        WheelGemSupremeModifier.General_RevelationMastery_GiftOfLife,

        WheelGemSupremeModifier.Sorcerer_AvatarOfStorm_Cooldown,
        WheelGemSupremeModifier.Sorcerer_EnergyWave_Cooldown,
        WheelGemSupremeModifier.Sorcerer_GreatDeathBeam_DamageIncrease,
        WheelGemSupremeModifier.Sorcerer_GreatDeathBeam_CriticalExtraDamage,
        WheelGemSupremeModifier.Sorcerer_HellsCore_DamageIncrease,
        WheelGemSupremeModifier.Sorcerer_HellsCore_CriticalExtraDamage,
        WheelGemSupremeModifier.Sorcerer_EnergyWave_DamageIncrease,
        WheelGemSupremeModifier.Sorcerer_EnergyWave_CriticalExtraDamage,
        WheelGemSupremeModifier.Sorcerer_GreatFireWave_DamageIncrease,
        WheelGemSupremeModifier.Sorcerer_GreatFireWave_CriticalExtraDamage,
        WheelGemSupremeModifier.Sorcerer_RageOfTheSkies_DamageIncrease,
        WheelGemSupremeModifier.Sorcerer_RageOfTheSkies_CriticalExtraDamage,
        WheelGemSupremeModifier.Sorcerer_GreatEnergyBeam_DamageIncrease,
        WheelGemSupremeModifier.Sorcerer_GreatEnergyBeam_CriticalExtraDamage,
        WheelGemSupremeModifier.Sorcerer_RevelationMastery_AvatarOfStorm,
        WheelGemSupremeModifier.Sorcerer_RevelationMastery_BeamMastery,
        WheelGemSupremeModifier.Sorcerer_RevelationMastery_DrainBody,
    )

    val supremeDruidPosition: List<WheelGemSupremeModifier> = listOf(
        WheelGemSupremeModifier.General_Dodge,
        WheelGemSupremeModifier.General_CriticalDamage,
        WheelGemSupremeModifier.General_LifeLeech,
        WheelGemSupremeModifier.General_ManaLeech,
        WheelGemSupremeModifier.SorcererDruid_UltimateHealing,
        WheelGemSupremeModifier.General_RevelationMastery_GiftOfLife,

        WheelGemSupremeModifier.Druid_AvatarOfNature_Cooldown,
        WheelGemSupremeModifier.Druid_NaturesEmbrace_Cooldown,
        WheelGemSupremeModifier.Druid_TerraBurst_DamageIncrease,
        WheelGemSupremeModifier.Druid_TerraBurst_CriticalExtraDamage,
        WheelGemSupremeModifier.Druid_IceBurst_DamageIncrease,
        WheelGemSupremeModifier.Druid_IceBurst_CriticalExtraDamage,
        WheelGemSupremeModifier.Druid_EternalWinter_CriticalExtraDamage,
        WheelGemSupremeModifier.Druid_EternalWinter_DamageIncrease,
        WheelGemSupremeModifier.Druid_TerraWave_DamageIncrease,
        WheelGemSupremeModifier.Druid_TerraWave_CriticalExtraDamage,
        WheelGemSupremeModifier.Druid_StrongIceWave_DamageIncrease,
        WheelGemSupremeModifier.Druid_StrongIceWave_CriticalExtraDamage,
        WheelGemSupremeModifier.Druid_HealFriend_HealingIncrease,
        WheelGemSupremeModifier.Druid_MassHealing_HealingIncrease,
        WheelGemSupremeModifier.Druid_RevelationMastery_AvatarOfNature,
        WheelGemSupremeModifier.Druid_RevelationMastery_BlessingOfTheGrove,
        WheelGemSupremeModifier.Druid_RevelationMastery_TwinBursts,
    )

    val supremeMonkPosition: List<WheelGemSupremeModifier> = listOf(
        WheelGemSupremeModifier.General_Dodge,
        WheelGemSupremeModifier.General_CriticalDamage,
        WheelGemSupremeModifier.General_LifeLeech,
        WheelGemSupremeModifier.General_ManaLeech,
        WheelGemSupremeModifier.General_RevelationMastery_GiftOfLife,

        WheelGemSupremeModifier.Monk_AvatarOfBalance_Cooldown,
        WheelGemSupremeModifier.Monk_SpiritMend_HealingIncreased,
        WheelGemSupremeModifier.Monk_SpiritualOutburst_DamageIncrease,
        WheelGemSupremeModifier.Monk_SpiritualOutburst_CriticalExtraDamage,
        WheelGemSupremeModifier.Monk_ForcefulUppercut_DamageIncrease,
        WheelGemSupremeModifier.Monk_ForcefulUppercut_CriticalExtraDamage,
        WheelGemSupremeModifier.Monk_FlurryOfBlows_DamageIncrease,
        WheelGemSupremeModifier.Monk_FlurryOfBlows_CriticalExtraDamage,
        WheelGemSupremeModifier.Monk_GreaterFlurryOfBlows_DamageIncrease,
        WheelGemSupremeModifier.Monk_GreaterFlurryOfBlows_CriticalExtraDamage,
        WheelGemSupremeModifier.Monk_SweepingTakedown_DamageIncrease,
        WheelGemSupremeModifier.Monk_SweepingTakedown_CriticalExtraDamage,
        WheelGemSupremeModifier.Monk_FocusSerenity_Cooldown,
        WheelGemSupremeModifier.Monk_FocusHarmony_Cooldown,
        WheelGemSupremeModifier.Monk_MassSpiritMend_HealingIncrease,
        WheelGemSupremeModifier.Monk_RevelationMastery_AvatarOfBalance,
        WheelGemSupremeModifier.Monk_RevelationMastery_SpiritualBurst,
        WheelGemSupremeModifier.Monk_RevelationMastery_Ascetic,
    )

    val supremePositionByVocation: Map<Int, List<WheelGemSupremeModifier>> = mapOf(
        1 to supremeSorcererPosition,
        2 to supremeDruidPosition,
        3 to supremePaladinPosition,
        4 to supremeKnightPosition,
        9 to supremeMonkPosition,
    )

    fun selectBasicModifier2(modifier1: WheelGemBasicModifier, random: WheelRandom): WheelGemBasicModifier {
        var modifier = modifier1
        while (modifier == modifier1) {
            modifier = basicSlot2Allowed[random.uniformRandom(0, basicSlot2Allowed.size - 1)]
        }
        return modifier
    }
}

/**
 * Mirror of `WheelGemUtils` (src/creatures/players/components/wheel/wheel_gems.cpp:710-948).
 * Returns 0 when the modifier/vocation pair is absent, matching the C++ fallthrough.
 */
object WheelGemUtils {
    private val healthValues: Map<WheelGemBasicModifier, Map<WheelVocation, Int>> = mapOf(
        WheelGemBasicModifier.Vocation_Health to mapOf(
            WheelVocation.Knight to 300,
            WheelVocation.Paladin to 200,
            WheelVocation.Sorcerer to 100,
            WheelVocation.Druid to 100,
            WheelVocation.Monk to 200,
        ),
        WheelGemBasicModifier.Vocation_Health_FireResistance to mapOf(
            WheelVocation.Knight to 150,
            WheelVocation.Paladin to 100,
            WheelVocation.Sorcerer to 50,
            WheelVocation.Druid to 50,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Health_EnergyResistance to mapOf(
            WheelVocation.Knight to 150,
            WheelVocation.Paladin to 100,
            WheelVocation.Sorcerer to 50,
            WheelVocation.Druid to 50,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Health_EarthResistance to mapOf(
            WheelVocation.Knight to 150,
            WheelVocation.Paladin to 100,
            WheelVocation.Sorcerer to 50,
            WheelVocation.Druid to 50,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Health_IceResistance to mapOf(
            WheelVocation.Knight to 150,
            WheelVocation.Paladin to 100,
            WheelVocation.Sorcerer to 50,
            WheelVocation.Druid to 50,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Mixed to mapOf(
            WheelVocation.Knight to 150,
            WheelVocation.Paladin to 100,
            WheelVocation.Sorcerer to 50,
            WheelVocation.Druid to 50,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Mixed2 to mapOf(
            WheelVocation.Knight to 150,
            WheelVocation.Paladin to 100,
            WheelVocation.Sorcerer to 50,
            WheelVocation.Druid to 50,
            WheelVocation.Monk to 100,
        ),
    )

    private val manaValues: Map<WheelGemBasicModifier, Map<WheelVocation, Int>> = mapOf(
        WheelGemBasicModifier.Vocation_Mana_FireResistance to mapOf(
            WheelVocation.Knight to 50,
            WheelVocation.Paladin to 150,
            WheelVocation.Sorcerer to 300,
            WheelVocation.Druid to 300,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Mana_EnergyResistance to mapOf(
            WheelVocation.Knight to 50,
            WheelVocation.Paladin to 150,
            WheelVocation.Sorcerer to 300,
            WheelVocation.Druid to 300,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Mana_Earth_Resistance to mapOf(
            WheelVocation.Knight to 50,
            WheelVocation.Paladin to 150,
            WheelVocation.Sorcerer to 300,
            WheelVocation.Druid to 300,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Mana_Ice_Resistance to mapOf(
            WheelVocation.Knight to 50,
            WheelVocation.Paladin to 150,
            WheelVocation.Sorcerer to 300,
            WheelVocation.Druid to 300,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Mana to mapOf(
            WheelVocation.Knight to 100,
            WheelVocation.Paladin to 300,
            WheelVocation.Sorcerer to 600,
            WheelVocation.Druid to 600,
            WheelVocation.Monk to 200,
        ),
        WheelGemBasicModifier.Vocation_Mixed to mapOf(
            WheelVocation.Paladin to 100,
            WheelVocation.Sorcerer to 150,
            WheelVocation.Druid to 150,
            WheelVocation.Monk to 100,
        ),
        WheelGemBasicModifier.Vocation_Capacity to mapOf(
            WheelVocation.Knight to 50,
            WheelVocation.Paladin to 150,
            WheelVocation.Sorcerer to 300,
            WheelVocation.Druid to 300,
        ),
    )

    private val capacityValues: Map<WheelGemBasicModifier, Map<WheelVocation, Int>> = mapOf(
        WheelGemBasicModifier.Vocation_Capacity_FireResistance to mapOf(
            WheelVocation.Knight to 250,
            WheelVocation.Paladin to 200,
            WheelVocation.Sorcerer to 100,
            WheelVocation.Druid to 100,
        ),
        WheelGemBasicModifier.Vocation_Capacity_EnergyResistance to mapOf(
            WheelVocation.Knight to 250,
            WheelVocation.Paladin to 200,
            WheelVocation.Sorcerer to 100,
            WheelVocation.Druid to 100,
        ),
        WheelGemBasicModifier.Vocation_Capacity_EarthResistance to mapOf(
            WheelVocation.Knight to 250,
            WheelVocation.Paladin to 200,
            WheelVocation.Sorcerer to 100,
            WheelVocation.Druid to 100,
        ),
        WheelGemBasicModifier.Vocation_Capacity_IceResistance to mapOf(
            WheelVocation.Knight to 250,
            WheelVocation.Paladin to 200,
            WheelVocation.Sorcerer to 100,
            WheelVocation.Druid to 100,
        ),
        WheelGemBasicModifier.Vocation_Capacity to mapOf(
            WheelVocation.Knight to 500,
            WheelVocation.Paladin to 400,
            WheelVocation.Sorcerer to 200,
            WheelVocation.Druid to 200,
        ),
        WheelGemBasicModifier.Vocation_Mixed to mapOf(
            WheelVocation.Knight to 125,
        ),
        WheelGemBasicModifier.Vocation_Mixed2 to mapOf(
            WheelVocation.Knight to 250,
            WheelVocation.Paladin to 200,
            WheelVocation.Sorcerer to 100,
            WheelVocation.Druid to 100,
        ),
    )

    fun getHealthValue(vocation: WheelVocation, modifier: WheelGemBasicModifier): Int =
        healthValues[modifier]?.get(vocation) ?: 0

    fun getManaValue(vocation: WheelVocation, modifier: WheelGemBasicModifier): Int =
        manaValues[modifier]?.get(vocation) ?: 0

    fun getCapacityValue(vocation: WheelVocation, modifier: WheelGemBasicModifier): Int =
        capacityValues[modifier]?.get(vocation) ?: 0
}
