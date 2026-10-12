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
    fun `spell constructor captures level magicLevel mana cooldown group and range`() {
        val (env, spells) = envWithSpells()
        env.loadChunk(
            """
            local spell = Spell("instant")
            spell:name("Test")
            spell:words("adori test")
            spell:level(20)
            spell:magicLevel(5)
            spell:mana(80)
            spell:cooldown(2000)
            spell:group(1)
            spell:groupCooldown(1000, 2000)
            spell:range(3)
            function spell.onCastSpell(creature, variant) return true end
            spell:register()
            """.trimIndent()
        )

        val spell = spells.getInstant("adori test")
        assertThat(spell).isNotNull
        assertThat(spell!!.level).isEqualTo(20)
        assertThat(spell.magicLevel).isEqualTo(5)
        assertThat(spell.mana).isEqualTo(80)
        assertThat(spell.cooldown).isEqualTo(2000)
        assertThat(spell.group).isEqualTo(1)
        assertThat(spell.groupCooldown).isEqualTo(1000)
        assertThat(spell.secondaryGroupCooldown).isEqualTo(2000)
        assertThat(spell.range).isEqualTo(3)
    }

    @Test
    fun `spell constructor captures vocations and supports the zero-argument getters`() {
        val (env, spells) = envWithSpells()
        env.loadChunk(
            """
            local spell = Spell("instant")
            spell:words("exura test")
            spell:level(8)
            spell:vocation("sorcerer", "druid")
            getLevel = spell:level()
            getWords = spell:words()
            function spell.onCastSpell(creature, variant) return true end
            spell:register()
            """.trimIndent()
        )

        val spell = spells.getInstant("exura test")
        assertThat(spell).isNotNull
        assertThat(spell!!.vocations).containsExactly("sorcerer", "druid")
        assertThat(env.getGlobal("getLevel").toint()).isEqualTo(8)
        assertThat(env.getGlobal("getWords").tojstring()).isEqualTo("exura test")
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
