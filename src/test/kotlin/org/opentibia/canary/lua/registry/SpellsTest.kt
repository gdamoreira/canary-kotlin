package org.opentibia.canary.lua.registry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.bindings.SpellBindings
import org.opentibia.canary.lua.runtime.LuaEnvironment

class SpellsTest {
    private fun envWithSpells(): Pair<LuaEnvironment, Spells> {
        val env = LuaEnvironment().apply { initState() }
        val spells = SpellBindings.install(env)
        return env to spells
    }

    @Test
    fun `a Lua instant spell registers by words and captures onCastSpell`() {
        val (env, spells) = envWithSpells()
        env.loadChunk(
            """
            local spell = Spell("instant")
            spell:name("Test")
            spell:id(221)
            spell:words("adori mas test")
            function spell.onCastSpell(creature, variant) return true end
            spell:register()
            """.trimIndent()
        )

        val spell = spells.getInstant("adori mas test")
        assertThat(spell).isNotNull
        assertThat(spell!!.name).isEqualTo("Test")
        assertThat(spell.spellType).isEqualTo(SpellType.INSTANT)
        assertThat(spell.onCastSpell.isfunction()).isTrue()
    }

    @Test
    fun `a Lua rune spell registers by runeId and captures onCastSpell`() {
        val (env, spells) = envWithSpells()
        env.loadChunk(
            """
            local rune = Spell("rune")
            rune:name("explosion rune")
            rune:id(18)
            rune:runeId(3200)
            function rune.onCastSpell(creature, var, isHotkey) return true end
            rune:register()
            """.trimIndent()
        )

        val rune = spells.getRune(3200)
        assertThat(rune).isNotNull
        assertThat(rune!!.name).isEqualTo("explosion rune")
        assertThat(rune.spellType).isEqualTo(SpellType.RUNE)
        assertThat(rune.onCastSpell.isfunction()).isTrue()
    }

    @Test
    fun `Spell with the SPELL_RUNE enum value creates a rune spell`() {
        val (env, spells) = envWithSpells()
        env.loadChunk(
            """
            local rune = Spell(SPELL_RUNE)
            function rune.onCastSpell(creature, var) return true end
            rune:runeId(3162)
            rune:register()
            """.trimIndent()
        )
        assertThat(spells.runeIds()).containsExactly(3162)
    }

    @Test
    fun `an instant spell without words does not register`() {
        val (env, spells) = envWithSpells()
        env.loadChunk(
            """
            local spell = Spell("instant")
            spell:id(1)
            function spell.onCastSpell(creature, variant) return true end
            result = spell:register()
            """.trimIndent()
        )
        assertThat(spells.instantWords()).isEmpty()
        assertThat(env.getGlobal("result").toboolean()).isFalse()
    }

    @Test
    fun `a spell without onCastSpell does not register`() {
        val (env, spells) = envWithSpells()
        env.loadChunk(
            """
            local spell = Spell("instant")
            spell:words("exura")
            result = spell:register()
            """.trimIndent()
        )
        assertThat(spells.instantWords()).isEmpty()
        assertThat(env.getGlobal("result").toboolean()).isFalse()
    }

    @Test
    fun `duplicate instant words is rejected`() {
        val (env, spells) = envWithSpells()
        env.loadChunk(
            """
            local first = Spell("instant")
            first:words("exura")
            function first.onCastSpell(creature, variant) return true end
            first:register()

            local second = Spell("instant")
            second:words("exura")
            function second.onCastSpell(creature, variant) return false end
            result = second:register()
            """.trimIndent()
        )
        assertThat(spells.instantWords()).containsExactly("exura")
        assertThat(env.getGlobal("result").toboolean()).isFalse()
    }
}
