package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators

/**
 * Transcript tests for the named `CreatureEvent` attachment of the monster
 * event list:
 *
 * - `Creature::registerCreatureEvent` (src/creatures/creature.cpp:1674-1693)
 *   rejects names unknown to `g_creatureEvents()`
 *   (`CreatureEvents::getEventByName`, src/lua/creature/creatureevent.cpp:43-52);
 * - `MonsterTypeFunctions::luaMonsterTypeRegisterEvent` pushes a late
 *   registration only onto the live monsters of the type
 *   (monster_type_functions.cpp:1235-1242);
 * - `Monster::Monster` filters the `mType->info.scripts` copy through the same
 *   registry (monster.cpp:107-114).
 */
class MonsterCreatureEventRegistryTest {

    private val origin = Position(100, 100, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterPipelinesHost
    private lateinit var registry: Monsters
    private lateinit var env: LuaEnvironment
    private var startup = false

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        host = MonsterPipelinesHost(
            service = service,
            mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
        )
        registry = Monsters()
        env = LuaEnvironment().apply { initState() }
        env.worldOperations!!.attachMonsters(registry)
        env.worldOperations!!.attachMonsterEvents(
            liveMonsterEvents = { monsterType, name -> host.registerCreatureEventForType(monsterType, name) },
            isStartup = { startup },
        )
        host.creatureEventRegistered = { name -> env.creatureEvents.get(name) != null }
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun monster(id: Long = 1): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = origin)

    private fun defineTypes(): Pair<MonsterType, MonsterType> {
        env.loadChunk(
            """
            local rat = Game.createMonsterType("Rat")
            local demon = Game.createMonsterType("Demon")
            ratType = rat
            """.trimIndent(),
        )
        return registry.getMonsterType("rat")!! to registry.getMonsterType("demon")!!
    }

    private fun registerCreatureEvent(name: String) {
        env.loadChunk(
            """
            local ev = CreatureEvent("$name")
            function ev.onThink(creature, interval) return true end
            ev:register()
            """.trimIndent(),
        )
    }

    @Test
    fun `an unknown name is rejected and never attached to a live monster`() {
        val (rat, _) = defineTypes()
        val live = monster()
        host.register(live, rat)

        env.loadChunk("ratType:registerEvent('NoSuchEvent')")

        assertThat(host.typeStateOf(live.id)!!.hasCreatureEvent("NoSuchEvent")).isFalse()
    }

    @Test
    fun `a late registration attaches per creature only when the registry knows the name`() {
        val (rat, demon) = defineTypes()
        val ratMonster = monster(id = 1)
        val demonMonster = monster(id = 2)
        host.register(ratMonster, rat)
        host.register(demonMonster, demon)
        registerCreatureEvent("MonsterOnThink")

        env.loadChunk("ratType:registerEvent('MonsterOnThink')")

        assertThat(env.creatureEvents.get("MonsterOnThink")).isNotNull
        assertThat(host.typeStateOf(1)!!.hasCreatureEvent("MonsterOnThink")).isTrue()
        assertThat(host.typeStateOf(2)!!.hasCreatureEvent("MonsterOnThink")).isFalse()
    }

    @Test
    fun `the constructor filters the type scripts through the registry`() {
        val rat = MonsterType("Rat").apply {
            info.scripts += "KnownEvent"
            info.scripts += "UnknownEvent"
        }
        registerCreatureEvent("KnownEvent")
        val live = monster()
        host.register(live, rat)

        assertThat(host.typeStateOf(live.id)!!.hasCreatureEvent("KnownEvent")).isTrue()
        assertThat(host.typeStateOf(live.id)!!.hasCreatureEvent("UnknownEvent")).isFalse()
    }

    @Test
    fun `an unwired registry seam keeps the permissive attachment`() {
        host.creatureEventRegistered = { true }
        val rat = MonsterType("Rat")
        val live = monster()
        host.register(live, rat)

        assertThat(host.typeStateOf(live.id)!!.registerCreatureEvent("Anything")).isTrue()
        assertThat(host.typeStateOf(live.id)!!.hasCreatureEvent("Anything")).isTrue()
    }
}
