package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.components.Achievement
import org.opentibia.canary.creatures.players.components.InMemoryAchievementRegistry
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Wave 55 A3: the achievement/animus Lua bindings must read and write the
 * composed `PlayerFeatures.achievement`/`animusMastery` components instead of
 * keeping their own per-player maps. The Lua surface stays exactly the Canary
 * `player_functions.cpp` one; only the backing state moves. Component reads are
 * interleaved with the Lua chunks so the "Lua set + component read agree"
 * contract is asserted before the script removes the state again.
 */
class PlayerAchievementAnimusDelegationTest {

    private val registry = InMemoryAchievementRegistry(
        listOf(
            Achievement(id = 1, name = "First Steps", description = "", points = 5),
            Achievement(id = 2, name = "Pitfighter", description = "", points = 3),
        ),
    )

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun newPlayer(id: Long = 7L, name: String = "Delegated"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(0, 0, 7))

    @Test
    fun `lua achievement operations read and write the composed component`() {
        val environment = env()
        PlayerFunctions.install(
            environment,
            batch3 = WorldPlayerBatch3Operations(achievementRegistry = registry),
        )
        val player = newPlayer()
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            addedByName = p:addAchievement("First Steps", false)
            unlockedByName = p:hasAchievement(1)
            pointsAfterAdd = p:getAchievementPoints()
            addedAgain = p:addAchievement(1, false)
            p:addAchievementPoints(7)
            pointsAfterAddPoints = p:getAchievementPoints()
            p:removeAchievementPoints(2)
            pointsAfterRemovePoints = p:getAchievementPoints()
            """.trimIndent(),
        )

        val component = player.features.achievement
        assertThat(environment.getGlobal("addedByName").toboolean()).isTrue()
        assertThat(component.isUnlocked(1)).isTrue()
        assertThat(environment.getGlobal("unlockedByName").toboolean()).isTrue()
        assertThat(environment.getGlobal("pointsAfterAdd").toint()).isEqualTo(5)
        assertThat(component.getPoints()).isEqualTo(10)
        assertThat(environment.getGlobal("addedAgain").toboolean()).isFalse()
        assertThat(environment.getGlobal("pointsAfterAddPoints").toint()).isEqualTo(12)
        assertThat(environment.getGlobal("pointsAfterRemovePoints").toint()).isEqualTo(10)

        environment.loadChunk(
            """
            removed = p:removeAchievement("First Steps")
            unlockedAfterRemove = p:hasAchievement(1)
            unknown = p:hasAchievement("Not Registered")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(component.isUnlocked(1)).isFalse()
        assertThat(component.getPoints()).isEqualTo(5)
        assertThat(environment.getGlobal("unlockedAfterRemove").toboolean()).isFalse()
        assertThat(environment.getGlobal("unknown").toboolean()).isFalse()
    }

    @Test
    fun `lua animus mastery operations read and write the composed component`() {
        val environment = env()
        PlayerFunctions.install(environment, batch3 = WorldPlayerBatch3Operations())
        PlayerFunctionsExtra.install(environment, WorldPlayerExtraOperations())
        val player = newPlayer()
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            hasBefore = p:hasAnimusMastery("Dragon")
            added = p:addAnimusMastery("Dragon")
            hasExact = p:hasAnimusMastery("Dragon")
            hasLowercase = p:hasAnimusMastery("dragon")
            """.trimIndent(),
        )

        val component = player.features.animusMastery
        assertThat(environment.getGlobal("hasBefore").toboolean()).isFalse()
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(component.has("dragon")).isTrue()
        assertThat(component.getPoints()).isEqualTo(1)
        assertThat(environment.getGlobal("hasExact").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasLowercase").toboolean()).isTrue()

        environment.loadChunk(
            """
            removed = p:removeAnimusMastery("DRAGON")
            hasRemoved = p:hasAnimusMastery("Dragon")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(component.has("Dragon")).isFalse()
        assertThat(component.getPoints()).isZero()
        assertThat(environment.getGlobal("hasRemoved").toboolean()).isFalse()
    }

    @Test
    fun `default install wires the real extra operations so animus delegates`() {
        val environment = env()
        PlayerFunctions.install(environment, batch3 = WorldPlayerBatch3Operations())
        PlayerFunctionsExtra.install(environment)
        val player = newPlayer()
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            added = p:addAnimusMastery("Demon")
            has = p:hasAnimusMastery("demon")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("has").toboolean()).isTrue()
        assertThat(player.features.animusMastery.has("Demon")).isTrue()
    }
}
