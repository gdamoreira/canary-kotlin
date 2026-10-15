package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators

/**
 * Transcript tests for the `Game::internalCreatureSay` spectator fanout
 * (src/game/game.cpp:7618-7657):
 *
 * - a normal say/yell reaches a live monster's `onSay`
 *   (`Monster::onCreatureSay`, src/creatures/monsters/monster.cpp:568-598)
 *   through the default `Game.onCreatureSayEvent` and `MonsterPipelinesHost`;
 * - yells span the wide multifloor range while says stay on the client
 *   viewport floor;
 * - the explicit-spectators call site (`Game::playerNpcGreet`, game.cpp:10259)
 *   keeps working;
 * - `Game::playerWhisper` (game.cpp:7497-7515) keeps its 8x6 client-viewport
 *   player-only delivery and never reaches monsters.
 */
class GameSayFanoutTest {

    private val center = Position(1000, 1000, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterPipelinesHost
    private lateinit var registry: Monsters
    private lateinit var env: LuaEnvironment
    private val map = GameMap()
    private lateinit var game: Game

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        registry = Monsters()
        env = LuaEnvironment().apply { initState() }
        env.worldOperations!!.attachMonsters(registry)
        host = MonsterPipelinesHost(service = service, mapAccess = map)
        game = Game(
            map,
            GameDispatcher(),
            monsterHost = host,
            monsterTypeProvider = { name -> registry.getMonsterType(name) },
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun loadSayCallback() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            ratType = mType
            sayCount = 0
            sayCreature = ""
            sayType = -1
            sayMessage = ""
            mType.onSay = function(monster, creature, type, message)
                sayCount = sayCount + 1
                sayCreature = creature:getName()
                sayType = type
                sayMessage = message
            end
            """.trimIndent(),
        )
    }

    private fun place(creature: Creature, position: Position): Boolean {
        map.getOrCreateTile(position).also { if (it.ground == null) it.ground = Item(id = 100) }
        return game.internalPlaceCreature(creature, position)
    }

    private fun player(id: Long, name: String, position: Position): Player {
        val player = Player(id = id, name = name, maxHealth = 150, health = 150, position = position, level = 20)
        game.addPlayer(player)
        return player
    }

    private fun placePlayer(id: Long, name: String, position: Position): Player {
        val player = player(id, name, position)
        assertThat(place(player, position)).isTrue()
        return player
    }

    private fun monster(id: Long = 7, position: Position = center): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = position)

    @Test
    fun `a normal say reaches a live monster onSay through the map spectator fanout`() {
        loadSayCallback()
        val inViewport = Position(1003, 1000, 7)
        assertThat(place(monster(position = inViewport), inViewport)).isTrue()
        player(1, "Hero", center)

        game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "hello rat")

        assertThat(env.getGlobal("sayCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("sayCreature").tojstring()).isEqualTo("Hero")
        assertThat(env.getGlobal("sayType").toint()).isEqualTo(SpeakClasses.TALKTYPE_SAY.value)
        assertThat(env.getGlobal("sayMessage").tojstring()).isEqualTo("hello rat")
    }

    @Test
    fun `a yell reaches a monster on another floor while a say stays floor bound`() {
        loadSayCallback()
        val upstairs = Position(1003, 1003, 6)
        assertThat(place(monster(position = upstairs), upstairs)).isTrue()
        player(1, "Hero", center)

        game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "hello")
        assertThat(env.getGlobal("sayCount").toint()).isZero()

        game.playerSay(1, 0, SpeakClasses.TALKTYPE_YELL, "", "hello")
        assertThat(env.getGlobal("sayCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("sayMessage").tojstring()).isEqualTo("HELLO")
        assertThat(env.getGlobal("sayType").toint()).isEqualTo(SpeakClasses.TALKTYPE_YELL.value)
    }

    @Test
    fun `empty speech is never dispatched`() {
        loadSayCallback()
        assertThat(place(monster(), center)).isTrue()
        player(1, "Hero", center)

        game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "")

        assertThat(env.getGlobal("sayCount").toint()).isZero()
    }

    @Test
    fun `internalCreatureSay honours an explicit spectator list`() {
        loadSayCallback()
        val rat = monster()
        assertThat(place(rat, center)).isTrue()
        val hero = player(1, "Hero", center)

        game.internalCreatureSay(hero, SpeakClasses.TALKTYPE_SAY, "hi", false, listOf(rat))

        assertThat(env.getGlobal("sayCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("sayMessage").tojstring()).isEqualTo("hi")
    }

    @Test
    fun `whisper keeps the eight by six viewport player delivery and never reaches monsters`() {
        loadSayCallback()
        val nearMonster = Position(1002, 1000, 7)
        assertThat(place(monster(position = nearMonster), nearMonster)).isTrue()
        val hero = placePlayer(1, "Hero", center)
        val near = placePlayer(2, "Near", Position(1001, 1000, 7))
        val far = placePlayer(3, "Far", Position(1003, 1000, 7))
        val outsideViewport = placePlayer(4, "Out", Position(1010, 1000, 7))

        val sends = mutableListOf<Pair<Long, String>>()
        val events = mutableListOf<Pair<Long, SpeakClasses>>()
        game.sendCreatureSayTo = { recipient, _, _, text -> sends.add(recipient.id to text) }
        game.onCreatureSayEvent = { target, _, type, _ -> events.add(target.id to type) }

        game.playerWhisper(hero, "psst")

        assertThat(sends).containsExactlyInAnyOrder(1L to "psst", 2L to "psst", 3L to "pspsps")
        assertThat(events).containsExactlyInAnyOrder(
            1L to SpeakClasses.TALKTYPE_WHISPER,
            2L to SpeakClasses.TALKTYPE_WHISPER,
            3L to SpeakClasses.TALKTYPE_WHISPER,
        )
        assertThat(sends.map { it.first }).doesNotContain(4L)
        assertThat(events.map { it.first }).doesNotContain(7L)
        assertThat(env.getGlobal("sayCount").toint()).isZero()
    }
}
