package org.opentibia.canary.systems.wheel

import org.opentibia.canary.spells.Spells

/**
 * Mirror of the wheel spell-table registration in Canary `src/io/io_wheel.cpp`:
 *
 *  - `InternalPlayerWheel::registerWheelSpellTable` (io_wheel.cpp:51-102)
 *  - `IOWheel::initializeGlobalData` (io_wheel.cpp:123-177)
 *
 * The C++ helpers reach the process-wide `g_spells()` singleton; per AGENTS.md
 * 1.1 the [Spells] registry is injected. `m_focusSpells` lives in
 * [WheelSpellTables.focusSpells] and the per-vocation tables in
 * [WheelSpellTables].
 *
 * PARITY NOTE: `g_logger().trace/warn/debug` are injected callbacks instead of
 * the global logger; the messages keep the C++ text.
 */
object WheelSpellRegistration {

    const val ANY_FOCUS_MAGE_SPELL = "Any_Focus_Mage_Spell"

    /**
     * `InternalPlayerWheel::registerWheelSpellTable` (io_wheel.cpp:51-102):
     * `Any_Focus_Mage_Spell` expands into every `m_focusSpells` name; a spell
     * missing from the registry is warned and skipped; cooldown boosts are
     * scaled by 1000 and leech boosts by 100; a registered spell always ends up
     * flagged as wheel-upgraded.
     */
    fun registerWheelSpellTable(
        spells: Spells,
        spellData: WheelSpellBonus,
        name: String,
        gradeType: WheelSpellGrade,
        logTrace: (String) -> Unit = {},
        logWarn: (String) -> Unit = {},
    ) {
        if (name == ANY_FOCUS_MAGE_SPELL) {
            for (focusSpellName in WheelSpellTables.focusSpells) {
                logTrace("[registerWheelSpellTable] registered any spell: $focusSpellName")
                registerWheelSpellTable(spells, spellData, focusSpellName, gradeType, logTrace, logWarn)
            }
            return
        }

        val spell = spells.getInstantSpellByName(name)
        if (spell == null) {
            logWarn("[registerWheelSpellTable] Spell with name $name could not be found and was ignored")
            return
        }

        logTrace("[registerWheelSpellTable] registering instant spell with name ${spell.name}")

        val increaseData = spellData.increase
        if (increaseData.damage > 0) {
            spell.setWheelOfDestinyBoost(WheelSpellBoost.DAMAGE, gradeType, increaseData.damage)
        }
        if (increaseData.heal > 0) {
            spell.setWheelOfDestinyBoost(WheelSpellBoost.HEAL, gradeType, increaseData.heal)
        }
        if (increaseData.criticalDamage > 0) {
            spell.setWheelOfDestinyBoost(WheelSpellBoost.CRITICAL_DAMAGE, gradeType, increaseData.criticalDamage)
        }
        if (increaseData.criticalChance > 0) {
            spell.setWheelOfDestinyBoost(WheelSpellBoost.CRITICAL_CHANCE, gradeType, increaseData.criticalChance)
        }

        val decreaseData = spellData.decrease
        if (decreaseData.cooldown > 0) {
            spell.setWheelOfDestinyBoost(WheelSpellBoost.COOLDOWN, gradeType, decreaseData.cooldown * 1000)
        }
        if (decreaseData.manaCost > 0) {
            spell.setWheelOfDestinyBoost(WheelSpellBoost.MANA, gradeType, decreaseData.manaCost)
        }
        if (decreaseData.secondaryGroupCooldown > 0) {
            spell.setWheelOfDestinyBoost(
                WheelSpellBoost.SECONDARY_GROUP_COOLDOWN,
                gradeType,
                decreaseData.secondaryGroupCooldown * 1000,
            )
        }

        val leechData = spellData.leech
        if (leechData.mana > 0) {
            spell.setWheelOfDestinyBoost(WheelSpellBoost.MANA_LEECH, gradeType, leechData.mana * 100)
        }
        if (leechData.life > 0) {
            spell.setWheelOfDestinyBoost(WheelSpellBoost.LIFE_LEECH, gradeType, leechData.life * 100)
        }

        spell.setWheelOfDestinyUpgraded(true)
    }

    /**
     * `IOWheel::initializeGlobalData` (io_wheel.cpp:123-177): every vocation
     * table registers grades 1 (`REGULAR`) and 2 (`UPGRADED`).
     */
    fun initializeGlobalData(
        spells: Spells,
        reload: Boolean = false,
        logTrace: (String) -> Unit = {},
        logWarn: (String) -> Unit = {},
        logDebug: (String) -> Unit = {},
    ): Boolean {
        for (data in WheelSpellTables.druid) {
            registerTableGrades(spells, data, logTrace, logWarn)
        }
        for (data in WheelSpellTables.knight) {
            registerTableGrades(spells, data, logTrace, logWarn)
        }
        for (data in WheelSpellTables.paladin) {
            registerTableGrades(spells, data, logTrace, logWarn)
        }
        for (data in WheelSpellTables.sorcerer) {
            registerTableGrades(spells, data, logTrace, logWarn)
        }
        for (data in WheelSpellTables.monk) {
            registerTableGrades(spells, data, logTrace, logWarn)
        }

        logDebug(
            if (!reload) {
                "Loading wheel of destiny... [Success]"
            } else {
                "Reloading wheel of destiny... [Success]"
            },
        )
        return true
    }

    /** `for (size_t i = 1; i < 3; ++i)` (io_wheel.cpp:129-132). */
    private fun registerTableGrades(
        spells: Spells,
        data: WheelSpellData,
        logTrace: (String) -> Unit,
        logWarn: (String) -> Unit,
    ) {
        for (i in 1 until 3) {
            registerWheelSpellTable(spells, data.grade[i], data.name, gradeFromValue(i), logTrace, logWarn)
        }
    }

    /** `static_cast<WheelSpellGrade_t>(i)` for the `[1, 2]` loop range. */
    private fun gradeFromValue(value: Int): WheelSpellGrade =
        if (value == WheelSpellGrade.UPGRADED.value) WheelSpellGrade.UPGRADED else WheelSpellGrade.REGULAR
}
