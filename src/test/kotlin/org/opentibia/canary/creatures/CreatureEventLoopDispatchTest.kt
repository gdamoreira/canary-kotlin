package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Production owner-loop dispatch for the attached named creature events:
 *
 * - `Creature::onThink` (src/creatures/creature.cpp:157-163) executes the
 *   attached `think` events; the port reaches it from `Game::checkCreatures`
 *   (src/game/game.cpp:7719-7741), which calls `Creature::onThink` for every
 *   alive flagged creature (players and monsters alike).
 * - `Creature::dropCorpse` (src/creatures/creature.cpp:819-824) executes the
 *   attached `death` events; the port reaches it from the `Creature.onDeath`
 *   tail (`Creature::onDeath`, creature.cpp:577-720) before
 *   `Creature::death` runs the revival/penalty tail.
 *
 * Both loops resolve the attached names through `Creature::getCreatureEvents`
 * (the [Creature.creatureEvents] set) exactly like Canary's typed event list;
 * the [ScriptingDispatch] confinement is the network composition root's job
 * (AGENTS.md 1.2), and the seam bindings here mirror the binding the root
 * installs (`CanaryServer.scriptingDispatch`).
 */
class CreatureEventLoopDispatchTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun attach(player: Player, environment: LuaEnvironment, name: String) {
        player.creatureEventRegistered = { candidate -> environment.creatureEvents.get(candidate) != null }
        assertThat(player.registerCreatureEvent(name)).isTrue()
    }

    private fun awaitLane(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `the check creatures loop dispatches a player attached think event`() {
        val environment = env()
        environment.loadChunk(
            """
            local attached = CreatureEvent("PlayerThinkLoop")
            function attached.onThink(creature, interval)
                seen_loop_think = creature:getName()
                seen_loop_interval = interval
            end
            attached:register()

            local unattached = CreatureEvent("OtherThinkLoop")
            function unattached.onThink(creature, interval)
                seen_loop_unattached = creature:getName()
            end
            unattached:register()
            """.trimIndent(),
        )
        val dispatcher = GameDispatcher()
        val game = Game(GameMap(), dispatcher)
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(1000, 1000, 7))
        attach(player, environment, "PlayerThinkLoop")
        val scripting = ScriptingDispatch(environment, dispatcher, creatureEventsOf = { it.getCreatureEvents() })
        player.onThinkEventsHost = { creature, interval -> scripting.creatureOnThink(creature, interval.toLong()) }

        // `Game::addCreatureCheck` commits inline while the dispatcher is stopped
        // (game.cpp:7676-7684 embedded branch), so the flagged player is in a
        // bucket before the world lane starts.
        game.addCreatureCheck(player)
        dispatcher.start()
        try {
            repeat(10) { game.checkCreatures() }
            awaitLane(dispatcher)

            assertThat(environment.getGlobal("seen_loop_think").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_loop_interval").toint()).isEqualTo(1000)
            assertThat(environment.getGlobal("seen_loop_unattached").isnil()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the death tail dispatches a player attached death event before the revival`() {
        val environment = env()
        environment.loadChunk(
            """
            local attached = CreatureEvent("PlayerDeathLoop")
            function attached.onDeath(creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified)
                seen_loop_death = creature:getName()
            end
            attached:register()
            """.trimIndent(),
        )
        val dispatcher = GameDispatcher().apply { start() }
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(1000, 1000, 7))
        attach(player, environment, "PlayerDeathLoop")
        val scripting = ScriptingDispatch(environment, dispatcher, creatureEventsOf = { it.getCreatureEvents() })
        player.onDeathEventsHost = { creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified ->
            scripting.creatureOnDeath(
                creature = creature,
                corpse = corpse,
                killer = killer,
                mostDamageKiller = mostDamageKiller,
                lastHitUnjustified = lastHitUnjustified,
                mostDamageUnjustified = mostDamageUnjustified,
            )
        }
        try {
            player.changeHealth(-player.health)
            awaitLane(dispatcher)

            assertThat(environment.getGlobal("seen_loop_death").tojstring()).isEqualTo("Hero")
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `an unbound think loop keeps the creature tick inert`() {
        val game = Game(GameMap(), GameDispatcher())
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(1000, 1000, 7))
        game.addCreatureCheck(player)

        repeat(10) { game.checkCreatures() }

        assertThat(player.health).isEqualTo(150)
    }
}
