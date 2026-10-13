package org.opentibia.canary.spells

/**
 * Test double for the Lua `onCastSpell` bridge (`InstantSpell::executeCastSpell`,
 * spells.cpp:1351-1373; `RuneSpell::executeCastSpell`, spells.cpp:1587-1611).
 * Spell scripts stay Lua; the engine must hand them the exact caster/variant.
 */
class FakeSpellScriptRunner : SpellScriptRunner {

    var instantResult: Boolean = true
    var runeResult: Boolean = true

    val instantCalls: MutableList<Triple<SpellCaster, InstantSpell, LuaVariant>> = mutableListOf()
    val runeCalls: MutableList<RuneCall> = mutableListOf()

    data class RuneCall(
        val caster: SpellCaster,
        val rune: RuneSpell,
        val variant: LuaVariant,
        val isHotkey: Boolean,
    )

    override fun executeCastSpell(caster: SpellCaster, spell: InstantSpell, variant: LuaVariant): Boolean {
        instantCalls.add(Triple(caster, spell, variant))
        return instantResult
    }

    override fun executeCastSpell(
        caster: SpellCaster,
        rune: RuneSpell,
        variant: LuaVariant,
        isHotkey: Boolean,
    ): Boolean {
        runeCalls.add(RuneCall(caster, rune, variant, isHotkey))
        return runeResult
    }
}
