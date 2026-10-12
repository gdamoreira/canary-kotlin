package org.opentibia.canary.systems.wheel

/**
 * Wheel of Destiny enums. Ported verbatim from Canary:
 *  - src/enums/player_wheel.hpp
 *  - src/creatures/players/components/wheel/wheel_definitions.hpp
 *
 * Enum constant names and numeric values mirror the C++ exactly (including
 * non-contiguous values such as Vocation_Mana_FireResistance = 33).
 */

enum class WheelGemAction(val value: Int) {
    Destroy(0),
    Reveal(1),
    SwitchDomain(2),
    ToggleLock(3),
    ImproveGrade(4),
}

enum class WheelImproveGemGrade(val value: Int) {
    Grade1(0),
    Grade2(1),
    Grade3(2),
    Grade4(3),
}

enum class WheelFragmentType(val value: Int) {
    Greater(0),
    Lesser(1),
}

enum class WheelGemAffinity(val value: Int) {
    Green(0),
    Red(1),
    Blue(2),
    Purple(3),
}

enum class WheelGemQuality(val value: Int) {
    Lesser(0),
    Regular(1),
    Greater(2),
}

enum class WheelGemBasicModifier(val value: Int) {
    General_PhysicalResistance(0),
    General_HolyResistance(1),
    General_DeathResistance(2),
    General_FireResistance(3),
    General_EarthResistance(4),
    General_IceResistance(5),
    General_EnergyResistance(6),

    General_HolyResistance_DeathWeakness(7),
    General_DeathResistance_HolyWeakness(8),
    General_FireResistance_EarthResistance(9),
    General_FireResistance_IceResistance(10),
    General_FireResistance_EnergyResistance(11),
    General_EarthResistance_IceResistance(12),
    General_EarthResistance_EnergyResistance(13),
    General_IceResistance_EnergyResistance(14),

    General_FireResistance_EarthWeakness(15),
    General_FireResistance_IceWeakness(16),
    General_FireResistance_EnergyWeakness(17),
    General_EarthResistance_FireWeakness(18),
    General_EarthResistance_IceWeakness(19),
    General_EarthResistance_EnergyWeakness(20),
    General_IceResistance_EarthWeakness(21),
    General_IceResistance_FireWeakness(22),
    General_IceResistance_EnergyWeakness(23),
    General_EnergyResistance_EarthWeakness(24),
    General_EnergyResistance_IceWeakness(25),
    General_EnergyResistance_FireWeakness(26),
    General_ManaDrainResistance(27),
    General_LifeDrainResistance(28),
    General_ManaDrainResistance_LifeDrainResistance(29),
    General_MitigationMultiplier(30),

    Vocation_Health(31),
    // 32 is INVALID in Canary (Vocation_Mana_Capacity): displayed as unknown.
    Vocation_Mana_FireResistance(33),
    Vocation_Mana_EnergyResistance(34),
    Vocation_Mana_Earth_Resistance(35),
    Vocation_Mana_Ice_Resistance(36),
    Vocation_Mana(37),
    Vocation_Health_FireResistance(38),
    Vocation_Health_EnergyResistance(39),
    Vocation_Health_EarthResistance(40),
    Vocation_Health_IceResistance(41),
    Vocation_Mixed(42),
    Vocation_Mixed2(43),
    Vocation_Capacity_FireResistance(44),
    Vocation_Capacity_EnergyResistance(45),
    Vocation_Capacity_EarthResistance(46),
    Vocation_Capacity_IceResistance(47),
    Vocation_Capacity(48),
}

enum class WheelGemSupremeModifier(val value: Int) {
    General_Dodge(0),
    General_CriticalDamage(1),
    General_LifeLeech(2),
    General_ManaLeech(3),
    SorcererDruid_UltimateHealing(4),
    General_RevelationMastery_GiftOfLife(5),

    Knight_AvatarOfSteel_Cooldown(6),
    Knight_ExecutionersThrow_Cooldown(7),
    Knight_ExecutionersThrow_DamageIncrease(8),
    Knight_ExecutionersThrow_CriticalExtraDamage(9),
    Knight_Fierce_Berserk_DamageIncrease(10),
    Knight_Fierce_Berserk_CriticalExtraDamage(11),
    Knight_Berserk_DamageIncrease(12),
    Knight_Berserk_CriticalExtraDamage(13),
    Knight_Front_Sweep_DamageIncrease(14),
    Knight_Front_Sweep_CriticalExtraDamage(15),
    Knight_Groundshaker_DamageIncrease(16),
    Knight_Groundshaker_CriticalExtraDamage(17),
    Knight_Annihilation_DamageIncrease(18),
    Knight_Annihilation_CriticalExtraDamage(19),
    Knight_FairWoundCleansing_HealingIncrease(20),
    Knight_RevelationMastery_AvatarOfSteel(21),
    Knight_RevelationMastery_ExecutionersThrow(22),
    Knight_RevelationMastery_CombatMastery(23),

    Paladin_AvatarOfLight_Cooldown(24),
    Paladin_DivineDazzle_Cooldown(25),
    Paladin_DivineGrenade_DamageIncrease(26),
    Paladin_DivineGrenade_CriticalExtraDamage(27),
    Paladin_DivineCaldera_DamageIncrease(28),
    Paladin_DivineCaldera_CriticalExtraDamage(29),
    Paladin_DivineMissile_DamageIncrease(30),
    Paladin_DivineMissile_CriticalExtraDamage(31),
    Paladin_EtherealSpear_DamageIncrease(32),
    Paladin_EtherealSpear_CriticalExtraDamage(33),
    Paladin_StrongEtherealSpear_DamageIncrease(34),
    Paladin_StrongEtherealSpear_CriticalExtraDamage(35),
    Paladin_DivineEmpowerment_Cooldown(36),
    Paladin_DivineGrenade_Cooldown(37),
    Paladin_Salvation_HealingIncrease(38),
    Paladin_RevelationMastery_AvatarOfLight(39),
    Paladin_RevelationMastery_DivineGrenade(40),
    Paladin_RevelationMastery_DivineEmpowerment(41),

    Sorcerer_AvatarOfStorm_Cooldown(42),
    Sorcerer_EnergyWave_Cooldown(43),
    Sorcerer_GreatDeathBeam_DamageIncrease(44),
    Sorcerer_GreatDeathBeam_CriticalExtraDamage(45),
    Sorcerer_HellsCore_DamageIncrease(46),
    Sorcerer_HellsCore_CriticalExtraDamage(47),
    Sorcerer_EnergyWave_DamageIncrease(48),
    Sorcerer_EnergyWave_CriticalExtraDamage(49),
    Sorcerer_GreatFireWave_DamageIncrease(50),
    Sorcerer_GreatFireWave_CriticalExtraDamage(51),
    Sorcerer_RageOfTheSkies_DamageIncrease(52),
    Sorcerer_RageOfTheSkies_CriticalExtraDamage(53),
    Sorcerer_GreatEnergyBeam_DamageIncrease(54),
    Sorcerer_GreatEnergyBeam_CriticalExtraDamage(55),
    Sorcerer_RevelationMastery_AvatarOfStorm(56),
    Sorcerer_RevelationMastery_BeamMastery(57),
    Sorcerer_RevelationMastery_DrainBody(58),

    Druid_AvatarOfNature_Cooldown(59),
    Druid_NaturesEmbrace_Cooldown(60),
    Druid_TerraBurst_DamageIncrease(61),
    Druid_TerraBurst_CriticalExtraDamage(62),
    Druid_IceBurst_DamageIncrease(63),
    Druid_IceBurst_CriticalExtraDamage(64),
    Druid_EternalWinter_DamageIncrease(65),
    Druid_EternalWinter_CriticalExtraDamage(66),
    Druid_TerraWave_DamageIncrease(67),
    Druid_TerraWave_CriticalExtraDamage(68),
    Druid_StrongIceWave_DamageIncrease(69),
    Druid_StrongIceWave_CriticalExtraDamage(70),
    Druid_HealFriend_HealingIncrease(71),
    Druid_MassHealing_HealingIncrease(72),
    Druid_RevelationMastery_AvatarOfNature(73),
    Druid_RevelationMastery_BlessingOfTheGrove(74),
    Druid_RevelationMastery_TwinBursts(75),

    Monk_AvatarOfBalance_Cooldown(76),
    Monk_SpiritMend_HealingIncreased(77),
    Monk_SpiritualOutburst_DamageIncrease(78),
    Monk_SpiritualOutburst_CriticalExtraDamage(79),
    Monk_ForcefulUppercut_DamageIncrease(80),
    Monk_ForcefulUppercut_CriticalExtraDamage(81),
    Monk_FlurryOfBlows_DamageIncrease(82),
    Monk_FlurryOfBlows_CriticalExtraDamage(83),
    Monk_GreaterFlurryOfBlows_DamageIncrease(84),
    Monk_GreaterFlurryOfBlows_CriticalExtraDamage(85),
    Monk_SweepingTakedown_DamageIncrease(86),
    Monk_SweepingTakedown_CriticalExtraDamage(87),
    Monk_FocusSerenity_Cooldown(88),
    Monk_FocusHarmony_Cooldown(89),
    Monk_MassSpiritMend_HealingIncrease(90),
    Monk_RevelationMastery_AvatarOfBalance(91),
    Monk_RevelationMastery_SpiritualBurst(92),
    Monk_RevelationMastery_Ascetic(93),
}

enum class WheelSlots(val value: Int) {
    SLOT_GREEN_200(1),
    SLOT_GREEN_TOP_150(2),
    SLOT_GREEN_TOP_100(3),

    SLOT_RED_TOP_100(4),
    SLOT_RED_TOP_150(5),
    SLOT_RED_200(6),

    SLOT_GREEN_BOTTOM_150(7),
    SLOT_GREEN_MIDDLE_100(8),
    SLOT_GREEN_TOP_75(9),

    SLOT_RED_TOP_75(10),
    SLOT_RED_MIDDLE_100(11),
    SLOT_RED_BOTTOM_150(12),

    SLOT_GREEN_BOTTOM_100(13),
    SLOT_GREEN_BOTTOM_75(14),
    SLOT_GREEN_50(15),

    SLOT_RED_50(16),
    SLOT_RED_BOTTOM_75(17),
    SLOT_RED_BOTTOM_100(18),

    SLOT_BLUE_TOP_100(19),
    SLOT_BLUE_TOP_75(20),
    SLOT_BLUE_50(21),

    SLOT_PURPLE_50(22),
    SLOT_PURPLE_TOP_75(23),
    SLOT_PURPLE_TOP_100(24),

    SLOT_BLUE_TOP_150(25),
    SLOT_BLUE_MIDDLE_100(26),
    SLOT_BLUE_BOTTOM_75(27),

    SLOT_PURPLE_BOTTOM_75(28),
    SLOT_PURPLE_MIDDLE_100(29),
    SLOT_PURPLE_TOP_150(30),

    SLOT_BLUE_200(31),
    SLOT_BLUE_BOTTOM_150(32),
    SLOT_BLUE_BOTTOM_100(33),

    SLOT_PURPLE_BOTTOM_100(34),
    SLOT_PURPLE_BOTTOM_150(35),
    SLOT_PURPLE_200(36);

    companion object {
        private val byValue = entries.associateBy { it.value }
        fun fromValue(value: Int): WheelSlots? = byValue[value]
    }
}

enum class WheelStageEnum(val value: Int) {
    NONE(0),
    ONE(1),
    TWO(2),
    THREE(3),
}

enum class WheelStagePoints(val value: Int) {
    ONE(250),
    TWO(500),
    THREE(1000),
}

enum class WheelStage(val value: Int) {
    GIFT_OF_LIFE(0),
    COMBAT_MASTERY(1),
    BLESSING_OF_THE_GROVE(2),
    DRAIN_BODY(3),
    BEAM_MASTERY(4),
    DIVINE_EMPOWERMENT(5),
    TWIN_BURST(6),
    EXECUTIONERS_THROW(7),
    AVATAR_OF_LIGHT(8),
    AVATAR_OF_NATURE(9),
    AVATAR_OF_STEEL(10),
    AVATAR_OF_STORM(11),
    AVATAR_OF_BALANCE(12),
    DIVINE_GRENADE(13),
    SPIRITUAL_OUTBURST(14),
    ASCETIC(15),
}

enum class WheelOnThink(val value: Int) {
    BATTLE_INSTINCT(0),
    POSITIONAL_TACTICS(1),
    BALLISTIC_MASTERY(2),
    COMBAT_MASTERY(3),
    FOCUS_MASTERY(4),
    GIFT_OF_LIFE(5),
    DIVINE_EMPOWERMENT(6),
    AVATAR_SPELL(7),
    AVATAR_FORGE(8),
}

enum class WheelStat(val value: Int) {
    HEALTH(0),
    MANA(1),
    CAPACITY(2),
    MITIGATION(3),
    FIST(4),
    MELEE(5),
    DISTANCE(6),
    MAGIC(7),
    LIFE_LEECH(8),
    MANA_LEECH(9),
    HEALING(10),
    DAMAGE(11),
    LIFE_LEECH_CHANCE(12),
    MANA_LEECH_CHANCE(13),
    DODGE(14),
    CRITICAL_DAMAGE(15),
    CRITICAL_CHANCE(16),
}

enum class WheelMajor(val value: Int) {
    MELEE(0),
    DISTANCE(1),
    SHIELD(2),
    MAGIC(3),
    HOLY_RESISTANCE(4),
    CRITICAL_DMG(5),
    PHYSICAL_DMG(6),
    HOLY_DMG(7),
    CRITICAL_DMG_2(8),
    DEFENSE(9),
    DAMAGE(10),
}

enum class WheelInstant(val value: Int) {
    BATTLE_INSTINCT(0),
    BATTLE_HEALING(1),
    POSITIONAL_TACTICS(2),
    BALLISTIC_MASTERY(3),
    HEALING_LINK(4),
    RUNIC_MASTERY(5),
    FOCUS_MASTERY(6),
    SANCTUARY(7),
    GUIDING_PRESENCE(8),
}

enum class WheelAvatarSkill(val value: Int) {
    NONE(0),
    DAMAGE_REDUCTION(1),
    CRITICAL_CHANCE(2),
    CRITICAL_DAMAGE(3),
}

enum class WheelSpellGrade(val value: Int) {
    NONE(0),
    REGULAR(1),
    UPGRADED(2),
    MAX(3),
}

enum class WheelSpellBoost(val value: Int) {
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
