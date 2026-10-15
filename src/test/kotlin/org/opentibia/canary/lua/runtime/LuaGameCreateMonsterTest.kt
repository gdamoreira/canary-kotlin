package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.spawns.FakeSpawnScheduler
import org.opentibia.canary.creatures.spawns.MONSTER_AUTO_ID_START
import org.opentibia.canary.creatures.spawns.MonsterSpawnPath
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators

/**
 * Transcript tests for the production binding of `Game.createMonster`
 * (`GameFunctions::luaGameCreateMonster`, game_functions.cpp:589) and
 * `Game.createSoulPitMonster` (game_functions.cpp:641) over the composed world:
 * `Monster::createMonster` (src/creatures/monsters/monster.cpp:82) constructs
 * the monster, `Game::placeCreature` (src/game/game.cpp:1524) places it through
 * `Game::internalPlaceCreature` (game.cpp:1495) and `Monster::onSpawn(position)`
 * (monster.cpp:627-648) dispatches through the world host.
 */
class LuaGameCreateMonsterTest {

    private val spawnPosition = Position(100, 100, 7)
    private val northPosition = Position(101, 101, 7)

    private lateinit var service: MonsterComputeService
    private lateinit var map: GameMap
    private lateinit var game: Game
    private lateinit var registry: Monsters
    private lateinit var host: MonsterPipelinesHost
    private lateinit var spawnPath: MonsterSpawnPath
    private lateinit var env: LuaEnvironment

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        map = GameMap()
        map.getOrCreateTile(spawnPosition).ground = Item(id = 100)
        map.getOrCreateTile(northPosition).ground = Item(id = 100)
        registry = Monsters()
        host = MonsterPipelinesHost(service = service, mapAccess = map)
        game = Game(
            map,
            GameDispatcher(),
            monsterHost = host,
            monsterTypeProvider = { name -> registry.getMonsterType(name) },
        )
        spawnPath = MonsterSpawnPath(
            game = game,
            map = map,
            monsterTypes = registry,
            spawnScheduler = FakeSpawnScheduler { 0L },
            config = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
            onMonsterSpawn = { monster, position -> host.onSpawn(monster.id, position) },
        )
        env = LuaEnvironment().apply { initState() }
        env.worldOperations!!.attachMonsters(registry)
        env.worldOperations!!.attachMonsterSpawn(spawnPath, host)
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun loadRatType() {
        env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType:health(75)
            mType:maxHealth(100)
            spawnCount = 0
            spawnX = 0
            mType.onSpawn = function(monster, spawnPosition)
                spawnCount = spawnCount + 1
                spawnX = spawnPosition.x
            end
            """.trimIndent(),
        )
    }

    @Test
    fun `Lua createMonster places a real monster and fires onSpawn`() {
        loadRatType()

        env.loadChunk("monster = Game.createMonster('Rat', { x = 100, y = 100, z = 7 })")

        assertThat(env.getGlobal("monster").isnil()).isFalse
        val placed = game.getMonsterByID(MONSTER_AUTO_ID_START)
        assertThat(placed).isNotNull
        assertThat(placed!!.name).isEqualTo("Rat")
        assertThat(placed.health).isEqualTo(75)
        assertThat(placed.maxHealth).isEqualTo(100)
        assertThat(placed.position).isEqualTo(spawnPosition)
        assertThat(map.getTile(spawnPosition)!!.getTopCreature()).isSameAs(placed)
        assertThat(host.entryOf(placed.id)).isNotNull
        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("spawnX").toint()).isEqualTo(spawnPosition.x)
    }

    @Test
    fun `Lua createMonster returns nil for an unknown monster name`() {
        loadRatType()

        env.loadChunk("monster = Game.createMonster('Ghost', { x = 100, y = 100, z = 7 })")

        assertThat(env.getGlobal("monster").isnil()).isTrue
        assertThat(game.getMonsterByID(MONSTER_AUTO_ID_START)).isNull()
        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(0)
    }

    @Test
    fun `Lua createMonster returns nil when placement fails and clears the summon master`() {
        loadRatType()
        env.loadChunk("master = Game.createMonster('Rat', { x = 101, y = 101, z = 7 })")
        val master = game.getMonsterByID(MONSTER_AUTO_ID_START)
        assertThat(master).isNotNull
        val occupant = Monster(
            id = 99,
            name = "Rat",
            maxHealth = 100,
            health = 100,
            position = spawnPosition,
        )
        assertThat(game.internalPlaceCreature(occupant, spawnPosition)).isTrue

        env.loadChunk(
            "blocked = Game.createMonster('Rat', { x = 100, y = 100, z = 7 }, false, false, master)",
        )

        assertThat(env.getGlobal("blocked").isnil()).isTrue
        assertThat(game.getMonsterByID(MONSTER_AUTO_ID_START + 1)).isNull()
        assertThat(master!!.getSummonCount()).isZero
    }

    @Test
    fun `Lua createMonster binds the summon master before placement`() {
        loadRatType()
        env.loadChunk("master = Game.createMonster('Rat', { x = 101, y = 101, z = 7 })")
        val master = game.getMonsterByID(MONSTER_AUTO_ID_START)!!

        env.loadChunk(
            "summon = Game.createMonster('Rat', { x = 100, y = 100, z = 7 }, false, false, master)",
        )

        val summon = game.getMonsterByID(MONSTER_AUTO_ID_START + 1)
        assertThat(summon).isNotNull
        assertThat(summon!!.getMaster()).isSameAs(master)
        assertThat(summon.isSummon()).isTrue
        assertThat(master.getSummonCount()).isEqualTo(1)
        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(2)
    }

    @Test
    fun `Lua createSoulPitMonster places the monster and latches the soul pit stack`() {
        loadRatType()

        env.loadChunk("monster = Game.createSoulPitMonster('Rat', { x = 100, y = 100, z = 7 }, 3)")

        assertThat(env.getGlobal("monster").isnil()).isFalse
        val placed = game.getMonsterByID(MONSTER_AUTO_ID_START)
        assertThat(placed).isNotNull
        assertThat(placed!!.position).isEqualTo(spawnPosition)
        // `Monster::setSoulPitStack(3)` scales 100 HP by 1 + (15 * 3 + 35) / 100 = 1.8
        // (monster.cpp:3201-3208, `MonsterForge.applyStacks`).
        assertThat(placed.maxHealth).isEqualTo(180)
        assertThat(placed.getSoulPit()).isTrue
        val state = host.typeStateOf(placed.id)
        assertThat(state).isNotNull
        assertThat(state!!.forgeStack).isEqualTo(3)
        assertThat(state.soulPit).isTrue
        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(1)
    }
}
