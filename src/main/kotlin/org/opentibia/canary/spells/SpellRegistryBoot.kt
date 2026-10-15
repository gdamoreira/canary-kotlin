package org.opentibia.canary.spells

import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType as LuaSpellType
import org.opentibia.canary.lua.registry.Spells as LuaSpells

/**
 * Boot bridge between the datapack spell scripts and the engine `g_spells()`
 * registry (`Spells`, spells.hpp:28-79).
 *
 * The scripts stay Lua (AGENTS.md 1.4): `Spell(type)` builds the registered
 * state in the Lua mirror and `spell:register()` hands it to the mirror through
 * `SpellFunctions` (spell_functions.cpp:169-206). In Canary that same call
 * registers the compiled `InstantSpell`/`RuneSpell` object the script created
 * (`Spells::registerInstantLuaEvent`/`registerRuneLuaEvent`,
 * spells.cpp:181-222); this recorder replays the mirror's registered state into
 * the engine registry so `Spells::getSpellByName` (spells.cpp:255-261),
 * `getSpellsByVocation` (spells.cpp:229-245) and the rune/vocation look text
 * resolve compiled actors instead of an empty registry.
 *
 * The compiled actor is the metadata projection of the registered state; the
 * `onCastSpell` callback stays in the [LuaSpell] mirror because the scripts are
 * executed through the Lua bridge (`SpellScriptRunner`/`MonsterRegisteredSpellRunner`)
 * rather than through a C++ `LuaScriptInterface` id, so `Spell::scriptId` stays
 * 0 and the Lua-only `spell:monkSpellType` state has no engine field to land in
 * (PARITY GAPs, the same seam gaps recorded on the spell engine).
 *
 * Recording is idempotent: an already-registered actor is never replaced, so a
 * replay only records the spells the scripts registered since the last call.
 */
object SpellRegistryBoot {

    /**
     * Replays every spell the [luaSpells] mirror holds into [registry] through
     * the same `registerInstantLuaEvent`/`registerRuneLuaEvent` calls Canary's
     * `spell:register()` makes. Returns the number of newly recorded actors.
     */
    fun recordRegisteredSpells(luaSpells: LuaSpells, registry: Spells): Int {
        var recorded = 0

        for (words in luaSpells.instantWords()) {
            if (registry.hasInstantSpell(words)) {
                continue
            }
            val state = luaSpells.getInstant(words) ?: continue
            if (registry.registerInstantLuaEvent(compileInstant(state))) {
                recorded++
            }
        }

        for (runeId in luaSpells.runeIds()) {
            if (registry.getRuneSpell(runeId) != null) {
                continue
            }
            val state = luaSpells.getRune(runeId) ?: continue
            if (registry.registerRuneLuaEvent(compileRune(state))) {
                recorded++
            }
        }

        return recorded
    }

    /** Compiles the registered instant state (`InstantSpell`, spells.hpp:316-347). */
    fun compileInstant(state: LuaSpell): InstantSpell = InstantSpell().also { actor ->
        applyCommon(actor, state)
        actor.hasParam = state.hasParams
        actor.hasPlayerNameParam = state.hasPlayerNameParam
        actor.needDirection = state.needDirection
        actor.casterTargetOrDirection = state.needCasterTargetOrDirection
        // `spell:blockWalls` writes `setCheckLineOfSight` in the C++ setter
        // despite its name (spell_functions.cpp:910-924).
        actor.checkLineOfSight = state.blockWalls
    }

    /** Compiles the registered rune state (`RuneSpell`, spells.hpp:349-385). */
    fun compileRune(state: LuaSpell): RuneSpell = RuneSpell().also { actor ->
        applyCommon(actor, state)
        actor.runeId = state.runeId
        actor.charges = state.charges
        actor.allowFarUse = state.allowFarUse
        actor.checkFloor = state.checkFloor
        // Same `blockWalls` -> `checkLineOfSight` mapping as the instant setter.
        actor.checkLineOfSight = state.blockWalls
    }

    /** The `Spell` fields shared by both compiled actor kinds (spells.hpp:266-310). */
    private fun applyCommon(actor: Spell, state: LuaSpell) {
        actor.name = state.name
        actor.words = state.words
        actor.separator = state.separator
        actor.spellId = state.spellId
        actor.spellType = if (state.spellType == LuaSpellType.RUNE) {
            SpellType.SPELL_RUNE
        } else {
            SpellType.SPELL_INSTANT
        }
        actor.group = SpellGroup.fromValue(state.group)
        actor.secondaryGroup = SpellGroup.fromValue(state.secondaryGroup)
        actor.cooldown = state.cooldown
        actor.groupCooldown = state.groupCooldown
        actor.secondaryGroupCooldown = state.secondaryGroupCooldown
        actor.level = state.level
        actor.magicLevel = state.magicLevel
        actor.range = state.range
        actor.mana = state.mana
        actor.manaPercent = state.manaPercent
        actor.soul = state.soul
        actor.needWeapon = state.needWeapon
        actor.blockingSolid = state.blockingSolid
        actor.blockingCreature = state.blockingCreature
        actor.aggressive = state.aggressive
        actor.learnable = state.needLearn
        actor.enabled = state.enabled
        actor.premium = state.premium
        actor.selfTarget = state.selfTarget
        actor.needTarget = state.needTarget
        actor.allowOnSelf = state.allowOnSelf
        actor.pzLocked = state.pzLocked
        actor.soundImpactEffect = soundEffectOf(state.soundImpactEffect, SoundEffect.SILENCE)
        actor.soundCastEffect = soundEffectOf(state.soundCastEffect, SoundEffect.SPELL_OR_RUNE)
        for ((vocationId, value) in state.vocSpellMap) {
            actor.addVocMap(vocationId, value)
        }
    }

    /** `enumToValue(SoundEffect_t)` lookup with the C++ default fallback. */
    private fun soundEffectOf(value: Int, fallback: SoundEffect): SoundEffect =
        SoundEffect.entries.firstOrNull { it.value == value } ?: fallback
}
