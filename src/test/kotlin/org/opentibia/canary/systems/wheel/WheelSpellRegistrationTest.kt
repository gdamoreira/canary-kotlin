package org.opentibia.canary.systems.wheel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.spells.Spells

/**
 * Transcript tests for the wheel spell-table registration, ported from Canary
 * `src/io/io_wheel.cpp`:
 *
 *  - `InternalPlayerWheel::registerWheelSpellTable` (io_wheel.cpp:51-102)
 *  - `IOWheel::initializeGlobalData` (io_wheel.cpp:123-177)
 *  - `Spell::getWheelOfDestinyUpgraded` / `getWheelOfDestinyBoost` /
 *    `setWheelOfDestinyUpgraded` / `setWheelOfDestinyBoost`
 *    (src/creatures/combat/spells.cpp:730-762)
 *
 * The `g_spells()` singleton is injected as a [Spells] instance (AGENTS.md 1.1).
 */
class WheelSpellRegistrationTest {

    @Test
    fun `registerWheelSpellTable scales cooldown and leech by 1000 and 100`() {
        val spells = Spells()
        val naturesEmbrace = instantSpell(spells, "Nature's Embrace")
        val terraWave = instantSpell(spells, "Terra Wave")

        WheelSpellRegistration.registerWheelSpellTable(
            spells,
            WheelSpellBonus(decrease = WheelSpellBonus.Decrease(cooldown = 10)),
            "Nature's Embrace",
            WheelSpellGrade.UPGRADED,
        )
        WheelSpellRegistration.registerWheelSpellTable(
            spells,
            WheelSpellBonus(leech = WheelSpellBonus.Leech(life = 5)),
            "Terra Wave",
            WheelSpellGrade.UPGRADED,
        )

        assertThat(naturesEmbrace.getWheelOfDestinyBoost(WheelSpellBoost.COOLDOWN, WheelSpellGrade.UPGRADED))
            .isEqualTo(10_000)
        assertThat(terraWave.getWheelOfDestinyBoost(WheelSpellBoost.LIFE_LEECH, WheelSpellGrade.UPGRADED))
            .isEqualTo(500)
    }

    @Test
    fun `registerWheelSpellTable registers only positive values and marks the spell upgraded`() {
        val spells = Spells()
        val strongIceWave = instantSpell(spells, "Strong Ice Wave")

        WheelSpellRegistration.registerWheelSpellTable(
            spells,
            WheelSpellBonus(
                leech = WheelSpellBonus.Leech(mana = 3, life = 0),
                increase = WheelSpellBonus.Increase(damage = 0, heal = 4, criticalDamage = 15, criticalChance = 10),
                decrease = WheelSpellBonus.Decrease(cooldown = 0, manaCost = 10, secondaryGroupCooldown = 8),
            ),
            "Strong Ice Wave",
            WheelSpellGrade.REGULAR,
        )

        assertThat(strongIceWave.getWheelOfDestinyBoost(WheelSpellBoost.MANA_LEECH, WheelSpellGrade.REGULAR))
            .isEqualTo(300)
        assertThat(strongIceWave.getWheelOfDestinyBoost(WheelSpellBoost.LIFE_LEECH, WheelSpellGrade.REGULAR))
            .isEqualTo(0)
        assertThat(strongIceWave.getWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.REGULAR))
            .isEqualTo(0)
        assertThat(strongIceWave.getWheelOfDestinyBoost(WheelSpellBoost.HEAL, WheelSpellGrade.REGULAR))
            .isEqualTo(4)
        assertThat(strongIceWave.getWheelOfDestinyBoost(WheelSpellBoost.CRITICAL_DAMAGE, WheelSpellGrade.REGULAR))
            .isEqualTo(15)
        assertThat(strongIceWave.getWheelOfDestinyBoost(WheelSpellBoost.CRITICAL_CHANCE, WheelSpellGrade.REGULAR))
            .isEqualTo(10)
        assertThat(strongIceWave.getWheelOfDestinyBoost(WheelSpellBoost.MANA, WheelSpellGrade.REGULAR))
            .isEqualTo(10)
        assertThat(
            strongIceWave.getWheelOfDestinyBoost(WheelSpellBoost.SECONDARY_GROUP_COOLDOWN, WheelSpellGrade.REGULAR),
        ).isEqualTo(8_000)
        assertThat(strongIceWave.getWheelOfDestinyUpgraded()).isTrue()
    }

    @Test
    fun `registerWheelSpellTable expands Any_Focus_Mage_Spell into the four focus spells`() {
        val spells = Spells()
        val focusSpells = IOWheel.FOCUS_SPELLS.map { instantSpell(spells, it) }

        WheelSpellRegistration.registerWheelSpellTable(
            spells,
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 5)),
            "Any_Focus_Mage_Spell",
            WheelSpellGrade.REGULAR,
        )
        WheelSpellRegistration.registerWheelSpellTable(
            spells,
            WheelSpellBonus(
                decrease = WheelSpellBonus.Decrease(cooldown = 4, secondaryGroupCooldown = 4),
            ),
            "Any_Focus_Mage_Spell",
            WheelSpellGrade.UPGRADED,
        )

        for (spell in focusSpells) {
            assertThat(spell.getWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.REGULAR)).isEqualTo(5)
            assertThat(spell.getWheelOfDestinyBoost(WheelSpellBoost.COOLDOWN, WheelSpellGrade.UPGRADED))
                .isEqualTo(4_000)
            assertThat(
                spell.getWheelOfDestinyBoost(WheelSpellBoost.SECONDARY_GROUP_COOLDOWN, WheelSpellGrade.UPGRADED),
            ).isEqualTo(4_000)
            assertThat(spell.getWheelOfDestinyUpgraded()).isTrue()
        }
    }

    @Test
    fun `registerWheelSpellTable warns and skips a missing spell`() {
        val spells = Spells()
        val warnings = mutableListOf<String>()

        WheelSpellRegistration.registerWheelSpellTable(
            spells,
            WheelSpellBonus(increase = WheelSpellBonus.Increase(damage = 5)),
            "Unknown Spell",
            WheelSpellGrade.REGULAR,
            logWarn = { warnings.add(it) },
        )

        assertThat(warnings)
            .containsExactly("[registerWheelSpellTable] Spell with name Unknown Spell could not be found and was ignored")
    }

    @Test
    fun `getWheelOfDestinyBoost adds the regular and upgraded boosts by grade`() {
        val spell = InstantSpell()

        spell.setWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.REGULAR, 10)
        spell.setWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.UPGRADED, 5)

        assertThat(spell.getWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.NONE)).isEqualTo(0)
        assertThat(spell.getWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.REGULAR)).isEqualTo(10)
        assertThat(spell.getWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.UPGRADED)).isEqualTo(15)
        assertThat(spell.getWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.MAX)).isEqualTo(15)
    }

    @Test
    fun `setWheelOfDestinyBoost ignores the NONE and MAX grades and overwrites per grade`() {
        val spell = InstantSpell()

        spell.setWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.NONE, 99)
        spell.setWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.MAX, 99)
        assertThat(spell.getWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.MAX)).isEqualTo(0)

        spell.setWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.REGULAR, 10)
        spell.setWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.REGULAR, 7)
        assertThat(spell.getWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, WheelSpellGrade.REGULAR)).isEqualTo(7)
    }

    @Test
    fun `getWheelOfDestinyUpgraded defaults to false and setter toggles it`() {
        val spell = InstantSpell()

        assertThat(spell.getWheelOfDestinyUpgraded()).isFalse()

        spell.setWheelOfDestinyUpgraded(true)
        assertThat(spell.getWheelOfDestinyUpgraded()).isTrue()
    }

    @Test
    fun `initializeGlobalData registers the grades of every vocation table`() {
        val spells = Spells()
        for (name in allTableSpellNames()) {
            instantSpell(spells, name)
        }

        val result = WheelSpellRegistration.initializeGlobalData(spells)

        assertThat(result).isTrue()
        assertThat(boostOf(spells, "Strong Ice Wave", WheelSpellBoost.MANA_LEECH, WheelSpellGrade.REGULAR))
            .isEqualTo(300)
        assertThat(boostOf(spells, "Strong Ice Wave", WheelSpellBoost.DAMAGE, WheelSpellGrade.UPGRADED))
            .isEqualTo(10)
        assertThat(boostOf(spells, "Front Sweep", WheelSpellBoost.LIFE_LEECH, WheelSpellGrade.REGULAR))
            .isEqualTo(500)
        assertThat(boostOf(spells, "Front Sweep", WheelSpellBoost.DAMAGE, WheelSpellGrade.UPGRADED))
            .isEqualTo(14)
        assertThat(boostOf(spells, "Magic Shield", WheelSpellBoost.COOLDOWN, WheelSpellGrade.UPGRADED))
            .isEqualTo(6_000)
        assertThat(boostOf(spells, "Mass Spirit Mend", WheelSpellBoost.HEAL, WheelSpellGrade.REGULAR))
            .isEqualTo(8)
        assertThat(boostOf(spells, "Divine Caldera", WheelSpellBoost.DAMAGE, WheelSpellGrade.UPGRADED))
            .isEqualTo(9)
        assertThat(spells.getInstantSpellByName("Eternal Winter")?.getWheelOfDestinyBoost(
            WheelSpellBoost.DAMAGE,
            WheelSpellGrade.REGULAR,
        )).isEqualTo(5)
    }

    @Test
    fun `initializeGlobalData logs the loading result`() {
        val spells = Spells()
        val debugLogs = mutableListOf<String>()

        WheelSpellRegistration.initializeGlobalData(spells, reload = true, logDebug = { debugLogs.add(it) })

        assertThat(debugLogs).containsExactly("Reloading wheel of destiny... [Success]")
    }

    private fun instantSpell(spells: Spells, name: String): InstantSpell {
        val spell = InstantSpell()
        spell.name = name
        spell.words = "exevo $name"
        spells.registerInstantLuaEvent(spell)
        return spell
    }

    private fun boostOf(
        spells: Spells,
        name: String,
        boost: WheelSpellBoost,
        grade: WheelSpellGrade,
    ): Int = spells.getInstantSpellByName(name)?.getWheelOfDestinyBoost(boost, grade) ?: -1

    private fun allTableSpellNames(): List<String> =
        (WheelSpellTables.druid + WheelSpellTables.knight + WheelSpellTables.paladin +
            WheelSpellTables.sorcerer + WheelSpellTables.monk)
            .map { it.name }
            .filter { it != "Any_Focus_Mage_Spell" } + IOWheel.FOCUS_SPELLS
}
