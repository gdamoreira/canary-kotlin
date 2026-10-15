package org.opentibia.canary.systems.raid

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.Creature
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
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Transcript tests for the production [RaidSpawner] bridge over the composed
 * world: the raid events call `Monster::createMonster`
 * (src/creatures/monsters/monster.cpp:82), `g_game().placeCreature`
 * (src/game/game.cpp:1524), `g_game().map.getTile` (map.cpp) and
 * `Monster::onSpawn(position)` (monster.cpp:627-648) exactly like
 * `SingleSpawnEvent::executeEvent` (src/lua/creature/raids.cpp:395) and
 * `AreaSpawnEvent::executeEvent` (raids.cpp:556).
 */
class GameRaidSpawnerTest {

    private val spawnPosition = Position(100, 100, 7)

    private lateinit var service: MonsterComputeService
    private lateinit var map: GameMap
    private lateinit var game: Game
    private lateinit var registry: Monsters
    private lateinit var host: MonsterPipelinesHost
    private lateinit var spawnPath: MonsterSpawnPath
    private lateinit var spawner: GameRaidSpawner
    private lateinit var env: LuaEnvironment

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        map = GameMap()
        map.getOrCreateTile(spawnPosition).ground = Item(id = 100)
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
        spawner = GameRaidSpawner(spawnPath, map, host)
        env = LuaEnvironment().apply { initState() }
        env.worldOperations!!.attachMonsters(registry)
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
    fun `singlespawn spawns a real monster through the production bridge and fires onSpawn`() {
        loadRatType()
        registry.getMonsterType("rat")!!.info.isForgeCreature = true
        val event = SingleSpawnEvent(spawner)
        assertThat(event.configureRaidEvent(raidSpawnElement("Rat"))).isTrue

        assertThat(event.executeEvent()).isTrue

        val monster = game.getMonsterByID(MONSTER_AUTO_ID_START)
        assertThat(monster).isNotNull
        assertThat(monster!!.position).isEqualTo(spawnPosition)
        assertThat(monster.health).isEqualTo(75)
        assertThat(map.getTile(spawnPosition)!!.getTopCreature()).isSameAs(monster)
        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(1)
        assertThat(env.getGlobal("spawnX").toint()).isEqualTo(spawnPosition.x)
        // `SingleSpawnEvent::executeEvent` calls `monster->setForgeMonster(false)`
        // (raids.cpp:404) after the placement.
        assertThat(registry.getMonsterType("rat")!!.info.isForgeCreature).isFalse
    }

    @Test
    fun `singlespawn fails when the monster type is unknown`() {
        val event = SingleSpawnEvent(spawner)
        assertThat(event.configureRaidEvent(raidSpawnElement("Ghost"))).isTrue

        assertThat(event.executeEvent()).isFalse

        assertThat(game.getMonsterByID(MONSTER_AUTO_ID_START)).isNull()
    }

    @Test
    fun `areaspawn places through the production bridge`() {
        loadRatType()
        val event = AreaSpawnEvent(spawner, RaidRandom { min, _ -> min })
        assertThat(event.configureRaidEvent(raidAreaElement("Rat", amount = 1))).isTrue

        assertThat(event.executeEvent()).isTrue

        val monster = game.getMonsterByID(MONSTER_AUTO_ID_START)
        assertThat(monster).isNotNull
        assertThat(monster!!.position).isEqualTo(spawnPosition)
        // `AreaSpawnEvent::executeEvent` never dispatches `Monster::onSpawn`
        // (raids.cpp:556-580): only `SingleSpawnEvent` fires it (raids.cpp:408).
        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(0)
    }

    @Test
    fun `areaspawn skips tiles whose top creature is not a monster view`() {
        loadRatType()
        val occupant = Monster(
            id = 99,
            name = "Rat",
            maxHealth = 100,
            health = 100,
            position = spawnPosition,
        )
        assertThat(game.internalPlaceCreature(occupant, spawnPosition)).isTrue
        val event = AreaSpawnEvent(spawner, RaidRandom { min, _ -> min })
        assertThat(event.configureRaidEvent(raidAreaElement("Rat", amount = 1))).isTrue

        assertThat(event.executeEvent()).isTrue

        assertThat(game.getMonsterByID(MONSTER_AUTO_ID_START)).isNull()
        assertThat(env.getGlobal("spawnCount").toint()).isEqualTo(0)
        assertThat(map.getTile(spawnPosition)!!.getTopCreature()).isSameAs(occupant)
    }

    @Test
    fun `areaspawn skips protection-zone tiles`() {
        loadRatType()
        val tile = map.getOrCreateTile(spawnPosition)
        tile.flags = tile.flags or TILESTATE_PROTECTIONZONE
        val event = AreaSpawnEvent(spawner, RaidRandom { min, _ -> min })
        assertThat(event.configureRaidEvent(raidAreaElement("Rat", amount = 1))).isTrue

        assertThat(event.executeEvent()).isTrue

        assertThat(game.getMonsterByID(MONSTER_AUTO_ID_START)).isNull()
    }

    @Test
    fun `the production tile view reports position flags and occupancy`() {
        val view = spawner.getTile(100, 100, 7)

        assertThat(view).isNotNull
        assertThat(view!!.position).isEqualTo(spawnPosition)
        assertThat(view.hasTopCreature).isFalse
        assertThat(view.hasFlag(TILESTATE_PROTECTIONZONE)).isFalse
        assertThat(view.isMovableBlocking()).isFalse
        assertThat(spawner.getTile(5000, 5000, 7)).isNull()

        val occupant = Monster(
            id = 99,
            name = "Rat",
            maxHealth = 100,
            health = 100,
            position = spawnPosition,
        )
        assertThat(game.internalPlaceCreature(occupant, spawnPosition)).isTrue
        assertThat(spawner.getTile(100, 100, 7)!!.hasTopCreature).isTrue
    }

    @Test
    fun `the bridge rejects a raid monster it did not create`() {
        assertThat(spawner.createMonster("Ghost")).isNull()
        assertThat(spawner.placeCreature(RaidMonster("Rat"), spawnPosition, false, true)).isFalse
    }

    private fun raidSpawnElement(name: String): Element {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        return document.createElement("singlespawn").apply {
            setAttribute("delay", "1000")
            setAttribute("name", name)
            setAttribute("x", spawnPosition.x.toString())
            setAttribute("y", spawnPosition.y.toString())
            setAttribute("z", spawnPosition.z.toString())
        }
    }

    private fun raidAreaElement(name: String, amount: Int): Element {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        return document.createElement("areaspawn").apply {
            setAttribute("delay", "1000")
            setAttribute("fromx", spawnPosition.x.toString())
            setAttribute("fromy", spawnPosition.y.toString())
            setAttribute("fromz", spawnPosition.z.toString())
            setAttribute("tox", spawnPosition.x.toString())
            setAttribute("toy", spawnPosition.y.toString())
            setAttribute("toz", spawnPosition.z.toString())
            appendChild(
                document.createElement("monster").apply {
                    setAttribute("name", name)
                    setAttribute("amount", amount.toString())
                },
            )
        }
    }
}
