package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.bindings.MonsterRegisteredSpell
import org.opentibia.canary.lua.bindings.SpellBindings
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.spells.InstantSpell

/**
 * Wave 65 B3: the production [ServerBootstrap] boot must compose the engine
 * `org.opentibia.canary.spells.Spells` registry and populate it from the
 * datapack spell scripts before the datapack monster scripts run, so the
 * `sb.spell = g_spells().getSpellByName(spell->name)` look-up
 * (monsters.cpp:77-80) resolves the registry-hit actor for a monster attack
 * block like fury's `fury skill reducer` (data-canary/scripts/spells).
 *
 * The assertion uses the default scripting and monster boots, exactly like
 * `CanaryServer::loadModules` (src/canary_server.cpp:568-601): datapack
 * `/scripts` load first, `{DATA_DIRECTORY}/monster` afterwards.
 */
class SpellRegistryBootCompositionTest {

    private class RecordingListener : ListenerBoot {
        override fun start() = Unit
        override fun stop() = Unit
        override fun closeConnections() = Unit
    }

    @Test
    fun `boot populates the engine registry and resolves the monster registry hit`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("spells_boot_composition")
        var composition: GameComposition? = null
        val bootstrap = ServerBootstrap(
            configBoot = ConfigBoot { _, _ -> ServerConfig() },
            databaseBoot = JdbcDatabaseBoot,
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener()
            },
            dispatcher = GameDispatcher(),
        )

        try {
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        val registry = SpellBindings.spellRegistry()
        val compiled = registry.getSpellByName("fury skill reducer")
        assertThat(compiled).isInstanceOf(InstantSpell::class.java)
        assertThat(compiled!!.words).isEqualTo("###2")
        assertThat(registry.getSpellByName("summon challenge")).isNotNull

        val booted = composition
        assertThat(booted).isNotNull
        val fury = booted!!.monsters.getMonsterType("fury", silent = true)
        assertThat(fury).isNotNull
        val registryHit = fury!!.info.attackSpells.firstOrNull { block ->
            (block.spell as? MonsterRegisteredSpell)?.spell?.name == "fury skill reducer"
        }
        assertThat(registryHit).isNotNull
        assertThat(registryHit!!.combatSpell).isFalse()
    }
}
