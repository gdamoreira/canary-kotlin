package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeathPenaltyTest {

    @Test
    fun `calculates experience loss reduction based on active blessings`() {
        val playerWithoutBlessings = Player(1, "Alice", 100, 0, position = Position(100, 100, 7), experience = 1000000, level = 50)

        // Without blessings: base loss is 10% (100,000 exp)
        val lostUnblessed = DeathPenaltyEngine.calculateExperienceLoss(playerWithoutBlessings, blessingsCount = 0)
        assertThat(lostUnblessed).isEqualTo(100000L)

        // With all 5 blessings: loss reduced by 8% per blessing -> 10% * (1 - 0.40) = 6% loss or 2% loss depending on promotion
        val lostBlessed = DeathPenaltyEngine.calculateExperienceLoss(playerWithoutBlessings, blessingsCount = 5)
        assertThat(lostBlessed).isLessThan(lostUnblessed)
        assertThat(lostBlessed).isEqualTo(20000L) // 2% with full blessings
    }

    @Test
    fun `handles player death and teleports to temple`() {
        val player = Player(1, "Alice", 100, 0, position = Position(500, 500, 7), experience = 100000, level = 20)
        val templePos = Position(1000, 1000, 7)

        val result = DeathPenaltyEngine.processDeath(player, templePosition = templePos, blessingsCount = 0)

        assertThat(player.health).isEqualTo(player.maxHealth) // restored on temple revive
        assertThat(player.position).isEqualTo(templePos)
        assertThat(result.corpseItemId).isGreaterThan(0)
    }

    @Test
    fun `processDeath fires the registered creature onDeath Lua handler`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local death = CreatureEvent("PlayerDeathCaller")
            function death.onDeath(creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified)
                seen_death_player = creature:getName()
                seen_death_corpse = corpse:getId()
                seen_death_killer_nil = (killer == nil)
                seen_death_most_nil = (mostDamageKiller == nil)
            end
            death:register()
            """.trimIndent()
        )

        val player = Player(1, "Alice", 100, 0, position = Position(500, 500, 7), experience = 100000, level = 20)
        val dispatcher = GameDispatcher().apply { start() }
        try {
            DeathPenaltyEngine.processDeath(
                player,
                templePosition = Position(1000, 1000, 7),
                blessingsCount = 0,
                scripting = ScriptingDispatch(environment, dispatcher),
            )

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(environment.getGlobal("seen_death_player").tojstring()).isEqualTo("Alice")
            assertThat(environment.getGlobal("seen_death_corpse").toint()).isEqualTo(3058)
            assertThat(environment.getGlobal("seen_death_killer_nil").toboolean()).isTrue()
            assertThat(environment.getGlobal("seen_death_most_nil").toboolean()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }
}
