package org.opentibia.canary.spells

import org.opentibia.canary.map.Position

/**
 * Injected view of the `Combat` instance a [CombatSpell] drives.
 *
 * Transcribes the exact `Combat` calls of `CombatSpell::castSpell`
 * (src/creatures/combat/spells.cpp:378-466): `hasArea()`, the
 * `COMBAT_PARAM_CASTSOUND`/`COMBAT_PARAM_IMPACTSOUND` params and the two
 * `doCombat` overloads. That state is created by the spell's Lua `combat()`
 * object, so it stays behind a seam instead of the stateless [Combat] core.
 */
interface CombatSpellCombat {
    /** `Combat::hasArea()` (`spells.cpp:428`, `456`). */
    fun hasArea(): Boolean

    /** `Combat::setParam(param, value)` (`spells.cpp:409`, `413`, `449`, `453`). */
    fun setParam(param: Int, value: Int): Boolean

    /** `Combat::doCombat(creature, position)` (`spells.cpp:416`, `458`). */
    fun doCombat(creature: SpellCaster?, position: Position)

    /** `Combat::doCombat(creature, target)` (`spells.cpp:463`). */
    fun doCombat(creature: SpellCaster?, target: SpellTarget)
}

/**
 * Lua bridge seam for `CombatSpell::executeCastSpell`
 * (src/creatures/combat/spells.cpp:468-490).
 *
 * Canary pushes the creature and [LuaVariant] into the spell script; the
 * scripts stay Lua (AGENTS.md 1.4), so the engine hands them to this seam.
 */
fun interface CombatSpellScriptRunner {
    /** `CombatSpell::executeCastSpell(creature, var)`. */
    fun executeCastSpell(creature: SpellCaster?, variant: LuaVariant): Boolean
}

/**
 * `CombatSpell` (`src/creatures/combat/spells.cpp:368-490`,
 * `spells.hpp:106-128`).
 *
 * The scripted branch is selected by `BaseSpell::isLoadedScriptId()`
 * (`m_spellScriptId != 0`); otherwise the spell sets the cast/impact sound
 * params on the combat and dispatches it. The `std::enable_shared_from_this`
 * base and the Lua userdata push are not modelled (AGENTS.md 1.4: scripts stay
 * Lua through [CombatSpellScriptRunner]).
 */
class CombatSpell(
    private val combat: CombatSpellCombat?,
    private val needTarget: Boolean,
    private val needDirection: Boolean,
    private val scripts: CombatSpellScriptRunner,
) {

    /** `BaseSpell::m_spellScriptId` (`spells.hpp:103`). */
    var scriptId: Int = 0

    /** `BaseSpell::soundImpactEffect` (`spells.hpp:99`). */
    var soundImpactEffect: SoundEffect = SoundEffect.SILENCE

    /** `BaseSpell::soundCastEffect` (`spells.hpp:100`). */
    var soundCastEffect: SoundEffect = SoundEffect.SPELL_OR_RUNE

    /** `CombatSpell::getCombat` (`spells.cpp:374-376`). */
    fun getCombat(): CombatSpellCombat? = combat

    /** `BaseSpell::isLoadedScriptId` (`spells.cpp:364-366`). */
    fun isLoadedScriptId(): Boolean = scriptId != 0

    /** `CombatSpell::castSpell(creature)` (`spells.cpp:378-418`). */
    fun castSpell(creature: SpellCaster?): Boolean {
        if (isLoadedScriptId()) {
            val variant = LuaVariant()
            variant.type = LuaVariantType.VARIANT_POSITION

            if (creature != null) {
                variant.pos = if (needDirection) {
                    Spells.getCasterPosition(creature, creature.getDirection())
                } else {
                    creature.getPosition()
                }
            }

            return executeCastSpell(creature, variant)
        }

        val position = if (creature != null) {
            if (needDirection) {
                Spells.getCasterPosition(creature, creature.getDirection())
            } else {
                creature.getPosition()
            }
        } else {
            Position(0, 0, 0)
        }

        val targetCombat = getCombat() ?: return false

        if (soundCastEffect != SoundEffect.SILENCE) {
            targetCombat.setParam(COMBAT_PARAM_CASTSOUND, soundCastEffect.value)
        }

        if (soundImpactEffect != SoundEffect.SILENCE) {
            targetCombat.setParam(COMBAT_PARAM_IMPACTSOUND, soundImpactEffect.value)
        }

        targetCombat.doCombat(creature, position)
        return true
    }

    /** `CombatSpell::castSpell(creature, target)` (`spells.cpp:420-466`). */
    fun castSpell(creature: SpellCaster?, target: SpellTarget): Boolean {
        val targetCombat = getCombat() ?: return false

        if (isLoadedScriptId()) {
            val variant = LuaVariant()
            if (targetCombat.hasArea()) {
                variant.type = LuaVariantType.VARIANT_POSITION

                if (needTarget) {
                    variant.pos = target.getPosition()
                } else if (needDirection && creature != null) {
                    variant.pos = Spells.getCasterPosition(creature, creature.getDirection())
                } else if (creature != null) {
                    variant.pos = creature.getPosition()
                }
            } else {
                variant.type = LuaVariantType.VARIANT_NUMBER
                variant.number = target.getID()
            }

            return executeCastSpell(creature, variant)
        }

        if (soundCastEffect != SoundEffect.SILENCE) {
            targetCombat.setParam(COMBAT_PARAM_CASTSOUND, soundCastEffect.value)
        }

        if (soundImpactEffect != SoundEffect.SILENCE) {
            targetCombat.setParam(COMBAT_PARAM_IMPACTSOUND, soundImpactEffect.value)
        }

        if (targetCombat.hasArea()) {
            if (needTarget) {
                targetCombat.doCombat(creature, target.getPosition())
            } else {
                return castSpell(creature)
            }
        } else {
            targetCombat.doCombat(creature, target)
        }
        return true
    }

    /** `CombatSpell::executeCastSpell(creature, var)` (`spells.cpp:468-490`). */
    fun executeCastSpell(creature: SpellCaster?, variant: LuaVariant): Boolean =
        scripts.executeCastSpell(creature, variant)

    companion object {
        /** `COMBAT_PARAM_CASTSOUND` (`src/creatures/creatures_definitions.hpp:308`). */
        const val COMBAT_PARAM_CASTSOUND: Int = 10

        /** `COMBAT_PARAM_IMPACTSOUND` (`src/creatures/creatures_definitions.hpp:309`). */
        const val COMBAT_PARAM_IMPACTSOUND: Int = 11
    }
}
