package org.opentibia.canary.network

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wave 69 F2 production-root binding tests for monsters that enter the world
 * outside the engine spawner:
 *
 *  - `Game::internalPlaceCreature` (src/game/game.cpp:1495) is the placement
 *    entry of the Lua `Game.createMonster` binding and the summon path; it must
 *    hand every accepted monster to the world creature-event binder, so the
 *    `Creature::onThink`/`Creature::dropCorpse` loops
 *    (src/creatures/creature.cpp:157-163, 819-824) run through the world
 *    `ScriptingDispatch` exactly like the `MonsterSpawnPath` spawns.
 *  - `Game::placeCreature` (game.cpp:1524) is the summon/raid placement call;
 *    the same binding must hold for a forced placement.
 *  - the death binder forwards the full C++ payload (corpse, last-hit killer,
 *    most-damage killer, both unjustified flags; creature.cpp:819-824) instead
 *    of dropping it.
 *
 * The harness composes the production [CanaryServer] with the monster compute
 * service, exactly like [CanaryServerEventModalBindingsTest], and places the
 * monsters through the real `Game` facade instead of hand-binding the hosts.
 */
class CanaryServerMonsterHostBindingTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? =
            if (name == "1" && password == "1") {
                Account(
                    id = 1,
                    name = "1",
                    passwordHash = "1",
                    characters = listOf(CharacterEntry("Knight Hero")),
                )
            } else {
                null
            }
    }

    private val center = Position(1000, 1000, 7)

    private lateinit var dispatcher: GameDispatcher
    private lateinit var environment: LuaEnvironment
    private lateinit var service: MonsterComputeService
    private lateinit var server: CanaryServer

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        dispatcher = GameDispatcher().apply { start() }
        val gameMap = GameMap()
        for (y in 998..1002) {
            for (x in 998..1002) {
                gameMap.getOrCreateTile(Position(x, y, 7)).ground = Item(id = 106)
            }
        }
        environment = LuaEnvironment().apply { initState() }
        service = MonsterComputeService().apply {
            start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        }
        server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            accountRepository = MockAccountRepo(),
            gameMap = gameMap,
            dispatcher = dispatcher,
            luaEnvironment = environment,
            monsterComputeService = service,
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
        dispatcher.shutdown()
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun monster(id: Long = 7, position: Position = center): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = position)

    /** Attaches [name] to the placed monster's host entry, like the type registry does. */
    private fun attachMonsterEvent(monsterId: Long, name: String) {
        val host = server.monsterPipelinesHost
        assertThat(host).isNotNull
        assertThat(host!!.typeStateOf(monsterId)!!.registerCreatureEvent(name)).isTrue()
    }

    @Test
    fun `a monster placed through internalPlaceCreature fires its attached think event`() {
        environment.loadChunk(
            """
            local attached = CreatureEvent("F2PlacementThink")
            function attached.onThink(creature, interval)
                seen_f2_placement_think = creature:getName()
            end
            attached:register()
            """.trimIndent(),
        )
        val monster = monster()
        assertThat(server.game.internalPlaceCreature(monster, center)).isTrue()
        attachMonsterEvent(monster.id, "F2PlacementThink")

        monster.onThink(1000)
        awaitLane()

        assertThat(environment.getGlobal("seen_f2_placement_think").tojstring()).isEqualTo("Rat")
    }

    @Test
    fun `a summon style placeCreature spawn fires its attached think event`() {
        environment.loadChunk(
            """
            local attached = CreatureEvent("F2SummonThink")
            function attached.onThink(creature, interval)
                seen_f2_summon_think = creature:getName()
            end
            attached:register()
            """.trimIndent(),
        )
        val monster = monster()
        assertThat(server.game.placeCreature(monster, center, extendedPos = false, forced = true)).isTrue()
        attachMonsterEvent(monster.id, "F2SummonThink")

        monster.onThink(1000)
        awaitLane()

        assertThat(environment.getGlobal("seen_f2_summon_think").tojstring()).isEqualTo("Rat")
    }

    @Test
    fun `a monster placed through internalPlaceCreature fires its attached death event`() {
        environment.loadChunk(
            """
            local attached = CreatureEvent("F2PlacementDeath")
            function attached.onDeath(creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified)
                seen_f2_placement_death = creature:getName()
            end
            attached:register()
            """.trimIndent(),
        )
        val monster = monster()
        assertThat(server.game.internalPlaceCreature(monster, center)).isTrue()
        attachMonsterEvent(monster.id, "F2PlacementDeath")

        monster.changeHealth(-monster.health)
        awaitLane()

        assertThat(environment.getGlobal("seen_f2_placement_death").tojstring()).isEqualTo("Rat")
    }

    @Test
    fun `the root death binder forwards the corpse killer and unjustified payload to Lua`() {
        environment.loadChunk(
            """
            local attached = CreatureEvent("F2PayloadDeath")
            function attached.onDeath(creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified)
                seen_f2_payload_creature = creature:getName()
                seen_f2_payload_corpse = corpse:getId()
                seen_f2_payload_killer = killer:getName()
                seen_f2_payload_most_damage = mostDamageKiller:getName()
                seen_f2_payload_last_unjustified = lastHitUnjustified
                seen_f2_payload_most_unjustified = mostDamageUnjustified
            end
            attached:register()
            """.trimIndent(),
        )
        val monster = monster()
        assertThat(server.game.internalPlaceCreature(monster, center)).isTrue()
        attachMonsterEvent(monster.id, "F2PayloadDeath")

        val killer = Monster(
            id = 9,
            name = "Wolf",
            maxHealth = 100,
            health = 100,
            position = Position(1001, 1000, 7),
        )
        assertThat(server.game.internalPlaceCreature(killer, killer.position)).isTrue()

        // The C++ `CreatureEvent::executeOnDeath` argument set (creature.cpp:819-824).
        monster.onDeathEventsHost(
            monster,
            Item(3058),
            killer,
            killer,
            true,
            false,
        )
        awaitLane()

        assertThat(environment.getGlobal("seen_f2_payload_creature").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("seen_f2_payload_corpse").toint()).isEqualTo(3058)
        assertThat(environment.getGlobal("seen_f2_payload_killer").tojstring()).isEqualTo("Wolf")
        assertThat(environment.getGlobal("seen_f2_payload_most_damage").tojstring()).isEqualTo("Wolf")
        assertThat(environment.getGlobal("seen_f2_payload_last_unjustified").toboolean()).isTrue()
        assertThat(environment.getGlobal("seen_f2_payload_most_unjustified").toboolean()).isFalse()
    }
}
