package org.opentibia.canary.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.spawns.MONSTER_AUTO_ID_START
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import java.nio.file.Path

/**
 * Production-composition tests for the booted monster spawn path: [CanaryServer]
 * loads the map's spawn XML (`IOMap::loadMonsters`, src/io/iomap.hpp:32), starts
 * the engine on the WorldCommit lane (`Game::setGameState(GAME_STATE_INIT)` ->
 * `map.spawnsMonster.startup()`, src/game/game.cpp:970) and drains it on
 * [CanaryServer.stop] (`Game::shutdown`, src/game/game.cpp:9502). Every block
 * must place through `Game::internalPlaceCreature` (src/game/game.cpp:1495).
 */
class CanaryServerSpawnPathTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var dispatcher: GameDispatcher
    private lateinit var gameMap: GameMap
    private lateinit var server: CanaryServer

    private val spawnPosition = Position(200, 200, 7)

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        dispatcher = GameDispatcher().apply { start() }
        gameMap = GameMap()
        gameMap.getOrCreateTile(spawnPosition).ground = Item(id = 100)
        gameMap.monsterfile = tempDir.resolve("canary-monster.xml").toFile().apply {
            writeText(
                """
                <monsters>
                    <monster centerx="200" centery="200" centerz="7" radius="4">
                        <monster name="Rat" x="0" y="0" spawntime="60"/>
                    </monster>
                </monsters>
                """.trimIndent(),
            )
        }.path
        val monsters = Monsters().apply {
            tryAddMonsterType(
                "Rat",
                MonsterType("Rat").apply {
                    info.isBlockable = true
                    info.health = 100
                    info.healthMax = 100
                },
            )
        }
        server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            gameMap = gameMap,
            dispatcher = dispatcher,
            configManager = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
            monsters = monsters,
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        dispatcher.shutdown()
    }

    @Test
    fun `server boot loads the spawn XML and places the monsters on the world lane`() {
        server.start()
        awaitLane()

        val monster = server.game.getMonsterByID(MONSTER_AUTO_ID_START)
        assertThat(monster).isNotNull
        assertThat(monster!!.position).isEqualTo(spawnPosition)
        assertThat(monster.getTile()).isNotNull
        assertThat(gameMap.spawnsMonster.isLoaded()).isTrue
        assertThat(gameMap.spawnsMonster.isStarted()).isTrue
        assertThat(server.monsterSpawnPath).isNotNull
    }

    @Test
    fun `stop cancels the spawn checks and releases the engine state`() {
        server.start()
        awaitLane()

        server.stop()

        assertThat(gameMap.spawnsMonster.isStarted()).isFalse
        assertThat(gameMap.spawnsMonster.getspawnMonsterList()).isEmpty()
    }

    @Test
    fun `a server without a composed monster registry keeps the spawn path uncomposed`() {
        val plain = CanaryServer(loginPort = 0, gamePort = 0, gameMap = GameMap(), dispatcher = dispatcher)

        assertThat(plain.monsterSpawnPath).isNull()
    }

    @Test
    fun `the booted spawn path dispatches the captured onSpawn through the world host`() {
        // `Monster::onSpawn(position)` (monster.cpp:627-648) is dispatched by
        // `SpawnMonster::spawnMonster` (spawn_monster.cpp:220) through the
        // composition root's `monsterPipelinesHost.onSpawn` binding, at the
        // block position and after the placement/direction/spawn-owner wiring.
        val environment = LuaEnvironment().apply { initState() }
        val registered = Monsters()
        environment.worldOperations!!.attachMonsters(registered)
        environment.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            spawnCount = 0
            spawnX = 0
            mType.onSpawn = function(monster, spawnPosition)
                spawnCount = spawnCount + 1
                spawnX = spawnPosition.x
            end
            """.trimIndent(),
        )
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        var composed: CanaryServer? = null
        try {
            composed = CanaryServer(
                loginPort = 0,
                gamePort = 0,
                gameMap = gameMap,
                dispatcher = dispatcher,
                configManager = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
                luaEnvironment = environment,
                monsters = registered,
                monsterComputeService = service,
            )
            composed.start()
            awaitLane()

            assertThat(environment.getGlobal("spawnCount").toint()).isEqualTo(1)
            assertThat(environment.getGlobal("spawnX").toint()).isEqualTo(spawnPosition.x)
        } finally {
            composed?.stop()
            service.shutdown()
        }
    }

    @Test
    fun `boot injects the creature event registry lookup into the monster host`() {
        // `Creature::registerCreatureEvent` (creature.cpp:1674-1693) validates
        // the name through `g_creatureEvents().getEventByName` (creatureevent.cpp:43-52);
        // the composition root injects the booted registry so `Monster::Monster`
        // (monster.cpp:107-114) and `luaMonsterTypeRegisterEvent` reject unknown
        // names in production.
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local known = CreatureEvent("BootKnown")
            function known.onThink(creature, interval) end
            known:register()
            """.trimIndent(),
        )
        val registered = Monsters().apply {
            tryAddMonsterType(
                "Rat",
                MonsterType("Rat").apply {
                    info.isBlockable = true
                    info.scripts += "BootGhost"
                    info.scripts += "BootKnown"
                },
            )
        }
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        try {
            val composed = CanaryServer(
                loginPort = 0,
                gamePort = 0,
                gameMap = gameMap,
                dispatcher = dispatcher,
                luaEnvironment = environment,
                monsters = registered,
                monsterComputeService = service,
            )
            val host = composed.monsterPipelinesHost!!
            assertThat(host.creatureEventRegistered("BootKnown")).isTrue()
            assertThat(host.creatureEventRegistered("BootGhost")).isFalse()

            val livePosition = Position(300, 300, 7)
            gameMap.getOrCreateTile(livePosition).ground = Item(id = 100)
            val monster = Monster(id = 777, name = "Rat", maxHealth = 100, health = 100, position = livePosition)
            assertThat(composed.game.internalPlaceCreature(monster, livePosition)).isTrue()

            val state = host.typeStateOf(777)!!
            assertThat(state.hasCreatureEvent("BootKnown")).isTrue()
            assertThat(state.hasCreatureEvent("BootGhost")).isFalse()
        } finally {
            service.shutdown()
        }
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
