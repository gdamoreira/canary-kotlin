package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the canonical registry look-up [SpellBindings]
 * exposes to the engine (`Spells::getSpellByName`, spells.cpp:255-261); the
 * `Spell` binding surface itself is covered by [SpellFunctionsBindingsTest].
 */
class SpellBindingsTest {

    @Test
    fun `getSpellByName searches runes before instants`() {
        val env = LuaEnvironment().apply { initState() }

        env.loadChunk(
            """
            local instant = Spell("instant")
            instant:name("Shared Name")
            instant:id(2)
            instant:words("exura shared")
            function instant.onCastSpell(creature, variant) return true end
            instant:register()

            local rune = Spell("rune")
            rune:name("Shared Name")
            rune:id(3174)
            rune:runeId(3174)
            function rune.onCastSpell(creature, variant, isHotkey) return true end
            rune:register()
            """.trimIndent(),
        )

        assertThat(SpellBindings.getSpellByName("shared name")).isSameAs(env.spells.getRune(3174))
        assertThat(SpellBindings.getSpellByName("missing spell")).isNull()
    }
}
