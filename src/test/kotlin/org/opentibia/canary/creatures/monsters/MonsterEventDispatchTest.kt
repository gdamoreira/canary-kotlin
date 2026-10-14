package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators

/**
 * Transcript tests for the monster creature-event dispatch:
 *
 * - `MonsterTypeFunctions::luaMonsterTypeRegisterEvent` pushes a late
 *   registration onto every live monster of the type
 *   (monster_type_functions.cpp:1224-1246, `Monster::registerCreatureEvent`
 *   creature.cpp:1674-1693);
 * - `Monster::onCreatureAppear`/`onRemoveCreature`/`onCreatureMove`/
 *   `onCreatureSay`/`onAttackedByPlayer` run the captured type callbacks
 *   (monster.cpp:348-625).
 */
class MonsterEventDispatchTest {

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
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun monster(id: Long = 1): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = origin)

    private fun player(id: Long = 2, position: Position = Position(101, 100, 7)): Player =
        Player(id = id, name = "Hero", maxHealth = 100, health = 100, position = position)

    @Test
    fun `registerEvent pushes the event onto every live monster of the type`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            ratType = mType
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val first = monster(id = 1)
        val second = monster(id = 2)
        host.register(first, rat)
        host.register(second, rat)

        env.loadChunk("ratType:registerEvent('MonsterOnSpawn')")

        assertThat(host.typeStateOf(1)!!.hasCreatureEvent("MonsterOnSpawn")).isTrue()
        assertThat(host.typeStateOf(2)!!.hasCreatureEvent("MonsterOnSpawn")).isTrue()
        assertThat(rat.info.scripts).contains("MonsterOnSpawn")
    }

    @Test
    fun `registerEvent during startup only records the script on the type`() {
        startup = true
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            ratType = mType
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val live = monster()
        host.register(live, rat)

        env.loadChunk("ratType:registerEvent('MonsterOnSpawn')")

        assertThat(rat.info.scripts).contains("MonsterOnSpawn")
        assertThat(host.typeStateOf(live.id)!!.hasCreatureEvent("MonsterOnSpawn")).isFalse()
    }

    @Test
    fun `registerEvent does not reach a live monster of another type`() {
        env.loadChunk(
            """
            local rat = Game.createMonsterType("Rat")
            Game.createMonsterType("Demon")
            ratType = rat
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val demon = registry.getMonsterType("demon")!!
        val ratMonster = monster(id = 1)
        val demonMonster = monster(id = 2)
        host.register(ratMonster, rat)
        host.register(demonMonster, demon)

        env.loadChunk("ratType:registerEvent('MonsterOnSpawn')")

        assertThat(host.typeStateOf(1)!!.hasCreatureEvent("MonsterOnSpawn")).isTrue()
        assertThat(host.typeStateOf(2)!!.hasCreatureEvent("MonsterOnSpawn")).isFalse()
    }

    @Test
    fun `captured onAppear fires when a creature appears`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            appearCount = 0
            mType.onAppear = function(monster, creature)
                appearCount = appearCount + 1
                appearMonster = monster:getName()
                appearCreature = creature:getName()
            end
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        val spectator = player()

        host.onCreatureAppear(ratMonster.id, spectator, false)

        assertThat(env.getGlobal("appearCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("appearMonster").tojstring()).isEqualTo("Rat")
        assertThat(env.getGlobal("appearCreature").tojstring()).isEqualTo("Hero")
    }

    @Test
    fun `an onAppear callback returning true stops the appear tail but not onSpawn`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            spawnCount = 0
            mType.onAppear = function(monster, creature)
                return true
            end
            mType.onSpawn = function(monster, spawnPosition)
                spawnCount = spawnCount + 1
            end
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)

        host.onCreatureAppear(ratMonster.id, ratMonster, false)

        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(1)
    }

    @Test
    fun `captured onDisappear fires when a creature is removed`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            disappearCount = 0
            mType.onDisappear = function(monster, creature)
                disappearCount = disappearCount + 1
                disappearCreature = creature:getName()
            end
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        val spectator = player()

        host.onRemoveCreature(ratMonster.id, spectator, false)

        assertThat(env.getGlobal("disappearCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("disappearCreature").tojstring()).isEqualTo("Hero")
    }

    @Test
    fun `captured onMove fires with both positions`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            moveCount = 0
            mType.onMove = function(monster, creature, oldPosition, newPosition)
                moveCount = moveCount + 1
                moveOldX = oldPosition.x
                moveNewX = newPosition.x
            end
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        val spectator = player()
        ratMonster.scheduleAsyncTaskBatch = {}

        host.onCreatureMove(ratMonster.id, spectator, Position(101, 100, 7), Position(102, 100, 7))

        assertThat(env.getGlobal("moveCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("moveOldX").toint()).isEqualTo(101)
        assertThat(env.getGlobal("moveNewX").toint()).isEqualTo(102)
    }

    @Test
    fun `captured onSay fires with the speaker, type and message`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            sayCount = 0
            mType.onSay = function(monster, creature, type, message)
                sayCount = sayCount + 1
                sayCreature = creature:getName()
                sayType = type
                sayMessage = message
            end
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        val speaker = player()

        host.onCreatureSay(ratMonster, speaker, SpeakClasses.TALKTYPE_SAY, "hello")

        assertThat(env.getGlobal("sayCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("sayCreature").tojstring()).isEqualTo("Hero")
        assertThat(env.getGlobal("sayType").toint()).isEqualTo(SpeakClasses.TALKTYPE_SAY.value)
        assertThat(env.getGlobal("sayMessage").tojstring()).isEqualTo("hello")
    }

    @Test
    fun `captured onPlayerAttack fires with the attacking player`() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            attackCount = 0
            mType.onPlayerAttack = function(monster, attacker)
                attackCount = attackCount + 1
                attackAttacker = attacker:getName()
            end
            """.trimIndent(),
        )
        val rat = registry.getMonsterType("rat")!!
        val ratMonster = monster()
        host.register(ratMonster, rat)
        val attacker = player()

        host.onAttackedByPlayer(ratMonster, attacker)

        assertThat(env.getGlobal("attackCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("attackAttacker").tojstring()).isEqualTo("Hero")
    }

    @Test
    fun `registerCreatureEventForType walks only the live monsters of that type`() {
        val rat = MonsterType("Rat")
        val demon = MonsterType("Demon")
        val ratMonster = monster(id = 1)
        val demonMonster = monster(id = 2)
        host.register(ratMonster, rat)
        host.register(demonMonster, demon)

        host.registerCreatureEventForType(rat, "MonsterOnSpawn")

        assertThat(host.typeStateOf(1)!!.hasCreatureEvent("MonsterOnSpawn")).isTrue()
        assertThat(host.typeStateOf(2)!!.hasCreatureEvent("MonsterOnSpawn")).isFalse()
    }

    @Test
    fun `a removed monster stops receiving pushed events`() {
        val rat = MonsterType("Rat")
        val live = monster()
        host.register(live, rat)
        host.unregister(live.id)

        host.registerCreatureEventForType(rat, "MonsterOnSpawn")

        assertThat(host.typeStateOf(live.id)).isNull()
    }

    @Test
    fun `untyped live monsters never receive pushed events`() {
        val rat = MonsterType("Rat")
        val live = monster()
        host.register(live)

        host.registerCreatureEventForType(rat, "MonsterOnSpawn")

        assertThat(host.typeStateOf(live.id)!!.hasCreatureEvent("MonsterOnSpawn")).isFalse()
    }

    @Test
    fun `the MonsterTypeState constructor attaches the type scripts`() {
        val rat = MonsterType("Rat").apply { info.scripts += "MonsterOnSpawn" }
        val live = monster()
        host.register(live, rat)

        assertThat(host.typeStateOf(live.id)!!.hasCreatureEvent("MonsterOnSpawn")).isTrue()
    }
}
