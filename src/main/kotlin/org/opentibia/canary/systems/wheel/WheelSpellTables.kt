package org.opentibia.canary.systems.wheel

/**
 * Mirror of `WheelSpells::Bonus` (src/creatures/players/components/wheel/wheel_spells.hpp).
 * Field names and defaults match the C++ structs exactly.
 */
data class WheelSpellBonus(
    val leech: Leech = Leech(),
    val increase: Increase = Increase(),
    val decrease: Decrease = Decrease(),
) {
    data class Increase(
        val area: Boolean = false,
        val damage: Int = 0,
        val heal: Int = 0,
        val additionalTarget: Int = 0,
        val damageReduction: Int = 0,
        val duration: Int = 0,
        val criticalDamage: Int = 0,
        val criticalChance: Int = 0,
    )

    data class Decrease(
        val cooldown: Int = 0,
        val manaCost: Int = 0,
        val secondaryGroupCooldown: Int = 0,
    )

    data class Leech(
        val mana: Int = 0,
        val life: Int = 0,
    )
}

/**
 * Mirror of `IOWheelBonusData::DataArray::Spells::<Vocation>`
 * (src/io/io_wheel.hpp). `grade[0]` is unused, `grade[1]` and `grade[2]` hold
 * the regular and upgraded bonuses, matching `std::array<WheelSpells::Bonus, 3>`.
 */
data class WheelSpellData(
    val name: String,
    val grade: List<WheelSpellBonus> = listOf(WheelSpellBonus(), WheelSpellBonus(), WheelSpellBonus()),
)

/**
 * Per-vocation wheel spell augmentation data, ported verbatim from
 * `IOWheel::initializeDruidSpells`, `initializeKnightSpells`,
 * `initializePaladinSpells`, `initializeSorcererSpells`, `initializeMonkSpells`
 * and `InternalPlayerWheel::m_focusSpells` (src/io/io_wheel.cpp).
 *
 * Note: values written as `static_cast<int>(std::round(x))` in Canary are
 * stored here as the rounded literals (round half away from zero).
 */
object WheelSpellTables {
    val focusSpells: List<String> = IOWheel.FOCUS_SPELLS

    val druid: List<WheelSpellData> = listOf(
        spell(
            "Strong Ice Wave",
            WheelSpellBonus(leech = WheelSpellBonus.Leech(mana = 3)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 10)),
        ),
        spell(
            "Mass Healing",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(heal = 4)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(area = true)),
        ),
        spell(
            "Nature's Embrace",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(heal = 11)),
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 10)),
        ),
        spell(
            "Terra Wave",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 7)),
            WheelSpellBonus(leech = WheelSpellBonus.Leech(life = 5)),
        ),
        spell(
            "Heal Friend",
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(manaCost = 10)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(heal = 6)),
        ),
    )

    val knight: List<WheelSpellData> = listOf(
        spell(
            "Front Sweep",
            WheelSpellBonus(leech = WheelSpellBonus.Leech(life = 5)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 14)),
        ),
        spell(
            "Groundshaker",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 13)),
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 2)),
        ),
        spell(
            "Chivalrous Challenge",
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(manaCost = 20)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(additionalTarget = 1)),
        ),
        spell(
            "Intense Wound Cleansing",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(heal = 125)),
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 300)),
        ),
        spell(
            "Fierce Berserk",
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(manaCost = 30)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 10)),
        ),
    )

    val paladin: List<WheelSpellData> = listOf(
        spell(
            "Sharpshooter",
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(secondaryGroupCooldown = 8)),
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 6)),
        ),
        spell(
            "Strong Ethereal Spear",
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 2)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 380)),
        ),
        spell(
            "Divine Dazzle",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(additionalTarget = 1)),
            WheelSpellBonus(
                increase = WheelSpellBonus.Increase(duration = 4),
                decrease = WheelSpellBonus.Decrease(cooldown = 4),
            ),
        ),
        spell(
            "Swift Foot",
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(secondaryGroupCooldown = 8)),
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 6)),
        ),
        spell(
            "Divine Caldera",
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(manaCost = 20)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 9)),
        ),
    )

    val sorcerer: List<WheelSpellData> = listOf(
        spell(
            "Magic Shield",
            grade2 = WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 6)),
        ),
        spell(
            "Sap Strength",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(area = true)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damageReduction = 1)),
        ),
        spell(
            "Energy Wave",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 5)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(area = true)),
        ),
        spell(
            "Great Fire Wave",
            WheelSpellBonus(
                increase = WheelSpellBonus.Increase(criticalDamage = 15, criticalChance = 10),
            ),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 5)),
        ),
        spell(
            "Any_Focus_Mage_Spell",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 5)),
            WheelSpellBonus(
                decrease = WheelSpellBonus.Decrease(cooldown = 4, secondaryGroupCooldown = 4),
            ),
        ),
    )

    val monk: List<WheelSpellData> = listOf(
        spell(
            "Mass Spirit Mend",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(heal = 8)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(area = true)),
        ),
        spell(
            "Mystic Repulse",
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 4)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 40)),
        ),
        spell(
            "Chained Penance",
            WheelSpellBonus(increase = WheelSpellBonus.Increase(additionalTarget = 1)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(additionalTarget = 2)),
        ),
        spell(
            "Flurry of Blows",
            WheelSpellBonus(leech = WheelSpellBonus.Leech(life = 5)),
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 12)),
        ),
        spell(
            "Sweeping Takedown",
            WheelSpellBonus(leech = WheelSpellBonus.Leech(mana = 3)),
            WheelSpellBonus(
                increase = WheelSpellBonus.Increase(criticalDamage = 25, criticalChance = 10),
            ),
        ),
    )

    fun forVocation(vocationCipId: Int): List<WheelSpellData> = when (vocationCipId) {
        IOWheel.VOCATION_KNIGHT_CIP -> knight
        IOWheel.VOCATION_PALADIN_CIP -> paladin
        IOWheel.VOCATION_SORCERER_CIP -> sorcerer
        IOWheel.VOCATION_DRUID_CIP -> druid
        IOWheel.VOCATION_MONK_CIP -> monk
        else -> emptyList()
    }

    private fun spell(
        name: String,
        grade1: WheelSpellBonus = WheelSpellBonus(),
        grade2: WheelSpellBonus = WheelSpellBonus(),
    ): WheelSpellData = WheelSpellData(name, listOf(WheelSpellBonus(), grade1, grade2))
}
