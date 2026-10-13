package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the extended `Game` global binding and the Canary global
 * time/timer functions. Each test installs the seam defaults and runs real Lua chunks
 * with the exact Canary names (Game.getSpectators, Game.getPlayers, Game.getGameState,
 * getWorldTime, addEvent, stopEvent, ...), pinning the safe no-op contract for state the
 * Kotlin domain does not model yet. The real-domain delegation is covered by
 * [GameItemWorldBindingsTest] and the fake-seam argument plumbing by
 * [GameBindingsDelegationTest].
 */
class GameBindingsExtendedTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Game exposes the extended Canary method names`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk(
            "checks = (Game.getSpectators ~= nil) and (Game.getPlayers ~= nil) and " +
                "(Game.getGameState ~= nil) and (Game.getMonsterCount ~= nil) and " +
                "(Game.getPlayerCount ~= nil) and (Game.getNpcCount ~= nil) and " +
                "(Game.getExperienceForLevel ~= nil) and (Game.createMonster ~= nil) and " +
                "(Game.createNpc ~= nil) and (Game.getOfflinePlayer ~= nil) and " +
                "(Game.getNormalizedPlayerName ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `Game spectator and player queries return tables under the default seam`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk("spectators = Game.getSpectators({ x = 100, y = 200, z = 7 })")
        environment.loadChunk("spectatorCount = #spectators")
        environment.loadChunk("players = Game.getPlayers()")
        environment.loadChunk("playerCount = #players")

        assertThat(environment.getGlobal("spectatorCount").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("playerCount").toint()).isEqualTo(0)
    }

    @Test
    fun `Game state and count queries return the default numbers`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk("state = Game.getGameState()")
        environment.loadChunk("monsters = Game.getMonsterCount()")
        environment.loadChunk("players = Game.getPlayerCount()")
        environment.loadChunk("npcs = Game.getNpcCount()")

        assertThat(environment.getGlobal("state").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("monsters").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("players").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("npcs").toint()).isEqualTo(0)
    }

    @Test
    fun `Game creation and lookup methods return the default seam values`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk("monster = Game.createMonster('Rat', { x = 100, y = 200, z = 7 })")
        environment.loadChunk("npc = Game.createNpc('Sam', { x = 100, y = 200, z = 7 })")
        environment.loadChunk("offline = Game.getOfflinePlayer('Hero')")
        environment.loadChunk("normalized = Game.getNormalizedPlayerName('hero')")
        environment.loadChunk("experience = Game.getExperienceForLevel(8)")

        assertThat(environment.getGlobal("monster").isnil()).isTrue()
        assertThat(environment.getGlobal("npc").isnil()).isTrue()
        assertThat(environment.getGlobal("offline").isnil()).isTrue()
        assertThat(environment.getGlobal("normalized").isnil()).isTrue()
        assertThat(environment.getGlobal("experience").toint()).isEqualTo(0)
    }

    @Test
    fun `Game getExperienceForLevel rejects level zero like Canary`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk("zero = Game.getExperienceForLevel(0)")

        assertThat(environment.getGlobal("zero").isnil()).isTrue()
    }

    @Test
    fun `global getWorldTime addEvent and stopEvent use the Canary global names`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk("time = getWorldTime()")
        environment.loadChunk("eventId = addEvent(function() end, 100)")
        environment.loadChunk("stopped = stopEvent(1)")

        assertThat(environment.getGlobal("time").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("eventId").isboolean()).isTrue()
        assertThat(environment.getGlobal("eventId").toboolean()).isFalse()
        assertThat(environment.getGlobal("stopped").toboolean()).isFalse()
    }

    @Test
    fun `addEvent rejects a non-function callback like Canary`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk("eventId = addEvent('not a function', 100)")

        assertThat(environment.getGlobal("eventId").toboolean()).isFalse()
    }
}
