package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
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

    /**
     * `Creature::dropCorpse` (src/creatures/creature.cpp:819-824) runs the
     * death events with the resolved corpse, last-hit killer, most-damage
     * killer and both unjustified flags. The engine routes its call through the
     * creature's bound `onDeathEventsHost` seam — the same seam the network
     * composition root binds to `ScriptingDispatch.creatureOnDeath` — instead
     * of carrying a second, payload-less dispatch.
     */
    @Test
    fun `processDeath routes the C++ death payload through the bound creature event host`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local death = CreatureEvent("PlayerDeathCaller")
            function death.onDeath(creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified)
                seen_death_player = creature:getName()
                seen_death_corpse = corpse:getId()
                seen_death_killer = killer:getName()
                seen_death_most = mostDamageKiller:getName()
                seen_death_last_unjustified = lastHitUnjustified
                seen_death_most_unjustified = mostDamageUnjustified
            end
            death:register()
            """.trimIndent()
        )

        val player = Player(1, "Alice", 100, 0, position = Position(500, 500, 7), experience = 100000, level = 20)
        val killer = Player(2, "Rival", 100, 0, position = Position(500, 501, 7))
        val mostDamage = Monster(id = 3, name = "Rat", maxHealth = 100, health = 100, position = Position(500, 502, 7))
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val scripting = ScriptingDispatch(environment, dispatcher, creatureEventsOf = { it.getCreatureEvents() })
            player.onDeathEventsHost = { creature, corpse, killerCreature, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified ->
                scripting.creatureOnDeath(
                    creature = creature,
                    corpse = corpse,
                    killer = killerCreature,
                    mostDamageKiller = mostDamageKiller,
                    lastHitUnjustified = lastHitUnjustified,
                    mostDamageUnjustified = mostDamageUnjustified,
                )
            }
            player.creatureEventRegistered = { candidate -> environment.creatureEvents.get(candidate) != null }
            assertThat(player.registerCreatureEvent("PlayerDeathCaller")).isTrue()

            DeathPenaltyEngine.processDeath(
                player,
                templePosition = Position(1000, 1000, 7),
                blessingsCount = 0,
                killer = killer,
                mostDamageKiller = mostDamage,
                lastHitUnjustified = true,
                mostDamageUnjustified = false,
            )

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(environment.getGlobal("seen_death_player").tojstring()).isEqualTo("Alice")
            assertThat(environment.getGlobal("seen_death_corpse").toint()).isEqualTo(3058)
            assertThat(environment.getGlobal("seen_death_killer").tojstring()).isEqualTo("Rival")
            assertThat(environment.getGlobal("seen_death_most").tojstring()).isEqualTo("Rat")
            assertThat(environment.getGlobal("seen_death_last_unjustified").toboolean()).isTrue()
            assertThat(environment.getGlobal("seen_death_most_unjustified").toboolean()).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }
}
