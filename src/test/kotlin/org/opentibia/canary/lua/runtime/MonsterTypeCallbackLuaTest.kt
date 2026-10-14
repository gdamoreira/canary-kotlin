package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators

/**
 * Transcript tests for the inline `MonsterType` event callbacks
 * (`data/libs/functions/revscriptsys.lua:272-304`, `MonsterType::loadCallback`,
 * src/creatures/monsters/monsters.cpp:285-322):
 *
 * - `mtype.onSpawn = function(...)` and `mtype:eventType(e)` + `mtype:onSpawn(fn)`
 *   capture the Lua function on the registered type;
 * - a live monster of that type fires the captured `onSpawn` when it enters the
 *   world and the captured `onThink` on its tick.
 */
class MonsterTypeCallbackLuaTest {

    private val origin = Position(100, 100, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterPipelinesHost
    private lateinit var registry: Monsters
    private lateinit var env: LuaEnvironment

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
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun monster(id: Long = 1): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = origin)

    @Test
    fun `inline onSpawn assignment captures the callback on the type`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType.onSpawn = function(monster, spawnPosition)
                spawnName = monster:getName()
                spawnX = spawnPosition.x
            end
            """.trimIndent(),
        )

        val rat = registry.getMonsterType("rat")!!
        assertThat(rat.info.spawnEvent).isNotEqualTo(-1)
        assertThat(rat.info.scriptInterface).isNotNull()
    }

    @Test
    fun `captured onSpawn fires for a spawned monster`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            spawnCount = 0
            mType.onSpawn = function(monster, spawnPosition)
                spawnCount = spawnCount + 1
                spawnName = monster:getName()
                spawnX = spawnPosition.x
            end
            """.trimIndent(),
        )

        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        host.onCreatureAppear(ratMonster.id, ratMonster, false)

        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("spawnName").tojstring()).isEqualTo("Rat")
        assertThat(env.getGlobal("spawnX").toint()).isEqualTo(origin.x)
    }

    @Test
    fun `captured onSpawn fires once per registered monster`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            spawnCount = 0
            mType.onSpawn = function(monster, spawnPosition)
                spawnCount = spawnCount + 1
            end
            """.trimIndent(),
        )

        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        host.onCreatureAppear(ratMonster.id, ratMonster, false)
        host.onCreatureAppear(ratMonster.id, ratMonster, false)

        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(1)
    }

    @Test
    fun `eventType plus the onThink method captures the callback`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType:eventType(1)
            mType:onThink(function(monster, interval)
                thinkName = monster:getName()
                thinkInterval = interval
                return true
            end)
            """.trimIndent(),
        )

        val rat = registry.getMonsterType("rat")!!
        assertThat(rat.info.thinkEvent).isNotEqualTo(-1)
        assertThat(rat.info.eventType.value).isEqualTo(1)
    }

    @Test
    fun `captured onThink consumes the tick when it returns true`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType:eventType(1)
            mType:onThink(function(monster, interval)
                thinkInterval = interval
                return true
            end)
            """.trimIndent(),
        )

        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        // Capture the async batch so setting the OnThink flag does not execute it
        // inline through the host's embedded-test bucket (same pattern as
        // MonsterPipelinesHostTest).
        ratMonster.scheduleAsyncTaskBatch = {}
        ratMonster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)

        assertThat(host.trySchedulePostThink(ratMonster)).isFalse()
        assertThat(env.getGlobal("thinkInterval").toint()).isEqualTo(1000)
        assertThat(ratMonster.hasAsyncTaskFlag(FlagAsyncClass_t.OnThink)).isTrue()
    }

    @Test
    fun `captured onThink keeps the default schedule when it returns false`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType:eventType(1)
            mType:onThink(function(monster, interval)
                thinkInterval = interval
                return false
            end)
            """.trimIndent(),
        )

        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        ratMonster.scheduleAsyncTaskBatch = {}
        ratMonster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)

        assertThat(host.trySchedulePostThink(ratMonster)).isTrue()
        assertThat(env.getGlobal("thinkInterval").toint()).isEqualTo(1000)
    }

    @Test
    fun `the method form without eventType reports the C++ unknown event failure`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            loaded = mType:onThink(function(monster, interval) return true end)
            """.trimIndent(),
        )

        val rat = registry.getMonsterType("rat")!!
        assertThat(rat.info.thinkEvent).isEqualTo(-1)
        assertThat(env.getGlobal("loaded").isboolean()).isTrue()
        assertThat(env.getGlobal("loaded").toboolean()).isFalse()
    }
}
