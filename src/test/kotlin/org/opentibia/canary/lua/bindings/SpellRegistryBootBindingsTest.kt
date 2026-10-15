package org.opentibia.canary.lua.bindings

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.loader.Scripts
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.spells.InstantSpell

/**
 * Wave 65 B3: the production boot wiring for the engine spell registry. The
 * datapack spell scripts stay Lua (AGENTS.md 1.4); `defaultScriptingBoot`
 * loads them through the existing registration path and
 * `SpellBindings.recordRegisteredSpells` records the registered actors into the
 * engine `Spells` registry, so the monster `sb.spell =
 * g_spells().getSpellByName(name)` look-up (monsters.cpp:77-80) resolves
 * against a populated compiled registry.
 */
class SpellRegistryBootBindingsTest {

    private fun bootDatapackSpells(): LuaEnvironment {
        val environment = LuaEnvironment().apply { initState() }
        Scripts(environment).apply {
            setDirectories(Path.of("data-canary"), Path.of("data"))
            loadLibs()
            loadScripts(Path.of("data-canary", "scripts"))
        }
        return environment
    }

    @Test
    fun `boot records the datapack spell scripts into the engine registry`() {
        val environment = bootDatapackSpells()

        val recorded = SpellBindings.recordRegisteredSpells()
        assertThat(recorded).isGreaterThanOrEqualTo(2)
        assertThat(environment.spells.instantWords()).contains("###1", "###2")

        val registry = SpellBindings.spellRegistry()
        val furyReducer = registry.getSpellByName("fury skill reducer")
        assertThat(furyReducer).isInstanceOf(InstantSpell::class.java)
        assertThat(furyReducer!!.words).isEqualTo("###2")
        assertThat(furyReducer.isLearnable()).isTrue()
        assertThat(furyReducer.enabled).isTrue()
        assertThat(furyReducer.aggressive).isTrue()

        val summonChallenge = registry.getSpellByName("summon challenge")
        assertThat(summonChallenge).isInstanceOf(InstantSpell::class.java)
        assertThat(summonChallenge!!.words).isEqualTo("###1")
        assertThat(summonChallenge.isLearnable()).isTrue()
    }

    @Test
    fun `getSpellByName resolves through the compiled registry for the monster path`() {
        val environment = bootDatapackSpells()

        val state = SpellBindings.getSpellByName("summon challenge")
        assertThat(state).isSameAs(environment.spells.getInstant("###1"))
        assertThat(SpellBindings.spellRegistry().getSpellByName("summon challenge")).isNotNull
        assertThat(SpellBindings.getSpellByName("missing spell")).isNull()
    }

    @Test
    fun `monster sb spell resolves to the registry hit actor`() {
        bootDatapackSpells()

        val block = MonsterSpellFunctions.deserializeSpell(
            MonsterSpellData().apply {
                name = "fury skill reducer"
                scriptName = "fury skill reducer"
                chance = 5
                interval = 2000
            },
        )

        assertThat(block).isNotNull
        assertThat(block!!.spell).isInstanceOf(MonsterRegisteredSpell::class.java)
        assertThat(block.combatSpell).isFalse()
        assertThat(block.chance).isEqualTo(5)
        assertThat(block.speed).isEqualTo(2000)
        assertThat((block.spell as MonsterRegisteredSpell).spell.name).isEqualTo("fury skill reducer")
    }
}
