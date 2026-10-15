package org.opentibia.canary.creatures.spawns

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import java.nio.file.Path

/**
 * Transcript tests for the production monster spawn path over a real `Game`
 * world: [MonsterSpawnPath.load] ports the `IOMap::loadMonsters` half
 * (src/io/iomap.hpp:32-46) and [MonsterSpawnPath.startup] the
 * `map.spawnsMonster.startup()` half of `Game::setGameState(GAME_STATE_INIT)`
 * (src/game/game.cpp:970). Every placed monster must enter the world through
 * `Game::internalPlaceCreature` (src/game/game.cpp:1495) and be registered with
 * `Game::addMonster` (`Creature::addList`, game.cpp:1513).
 *
 * The respawn transcript follows `Monster::onRemoveCreature`
 * (src/creatures/monsters/monster.cpp:414-416): removing a spawned monster arms
 * `SpawnMonster::startSpawnMonsterCheck` (spawn_monster.cpp:165), and the block
 * respawns after its interval once the check observes the removal through
 * `SpawnMonster::cleanup` (spawn_monster.cpp:345).
 */
class MonsterSpawnPathTest {

    @TempDir
    lateinit var tempDir: Path

    private var now = 0L
    private val scheduler = FakeSpawnScheduler { now }
    private lateinit var map: GameMap
    private lateinit var game: Game
    private lateinit var monsters: Monsters
    private lateinit var path: MonsterSpawnPath

    private val spawnPosition = Position(100, 100, 7)

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        map = GameMap()
        map.getOrCreateTile(spawnPosition).ground = Item(id = 100)
        map.monsterfile = spawnXml(centerX = 100, centerY = 100)
        game = Game(map, GameDispatcher())
        monsters = Monsters().apply {
            tryAddMonsterType(
                "Rat",
                MonsterType("Rat").apply {
                    info.isBlockable = true
                    info.health = 50
                    info.healthMax = 100
                },
            )
        }
        path = MonsterSpawnPath(
            game = game,
            map = map,
            monsterTypes = monsters,
            spawnScheduler = scheduler,
            config = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
            now = { now },
        )
    }

    private fun spawnXml(centerX: Int, centerY: Int): String =
        tempDir.resolve("canary-monster-$centerX-$centerY.xml").toFile().apply {
            writeText(
                """
                <monsters>
                    <monster centerx="$centerX" centery="$centerY" centerz="7" radius="3">
                        <monster name="Rat" x="0" y="0" spawntime="60"/>
                    </monster>
                </monsters>
                """.trimIndent(),
            )
        }.path

    @Test
    fun `load parses the map spawn file into the composed registry`() {
        assertThat(path.load()).isTrue

        assertThat(map.spawnsMonster.isLoaded()).isTrue
        assertThat(map.spawnsMonster.getspawnMonsterList()).hasSize(1)
        assertThat(map.spawnsMonster.getspawnMonsterList().first().monsterSpawnMap).hasSize(1)
    }

    @Test
    fun `load keeps an already loaded spawn registry untouched`() {
        assertThat(path.load()).isTrue
        val loaded = map.spawnsMonster

        assertThat(path.load()).isTrue

        assertThat(map.spawnsMonster).isSameAs(loaded)
    }

    @Test
    fun `startup places the spawned monster through Game internalPlaceCreature`() {
        assertThat(path.load()).isTrue

        path.startup()

        val monster = game.getMonsterByID(MONSTER_AUTO_ID_START)
        assertThat(monster).isNotNull
        assertThat(monster!!.name).isEqualTo("Rat")
        assertThat(monster.maxHealth).isEqualTo(100)
        assertThat(monster.health).isEqualTo(50)
        assertThat(monster.position).isEqualTo(spawnPosition)
        assertThat(monster.getTile()).isNotNull
        assertThat(monster.direction).isEqualTo(Direction.North)
        assertThat(monster.isRemoved()).isFalse
        assertThat(map.spawnsMonster.isStarted()).isTrue
    }

    @Test
    fun `the composition threads the onSpawn hook into the engine with the block position`() {
        // `Monster::onSpawn(position)` (monster.cpp:627-648) is dispatched by
        // `SpawnMonster::spawnMonster` (spawn_monster.cpp:220) after placement;
        // the composition root hands the engine the world host entry.
        val dispatched = mutableListOf<Pair<Monster, Position>>()
        path = MonsterSpawnPath(
            game = game,
            map = map,
            monsterTypes = monsters,
            spawnScheduler = scheduler,
            config = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
            now = { now },
            onMonsterSpawn = { monster, position -> dispatched.add(monster to position) },
        )
        assertThat(path.load()).isTrue

        path.startup()

        val monster = game.getMonsterByID(MONSTER_AUTO_ID_START)!!
        assertThat(dispatched).containsExactly(monster to spawnPosition)
    }

    @Test
    fun `a monster whose placement fails is not recorded as spawned`() {
        map.monsterfile = spawnXml(centerX = 500, centerY = 500)
        assertThat(path.load()).isTrue

        path.startup()

        assertThat(game.getMonsterByID(MONSTER_AUTO_ID_START)).isNull()
        assertThat(map.spawnsMonster.getspawnMonsterList().first().spawnedMonsterMap).isEmpty()
    }

    @Test
    fun `removing a spawned monster arms the respawn check and the interval replaces it`() {
        assertThat(path.load()).isTrue
        path.startup()
        val first = game.getMonsterByID(MONSTER_AUTO_ID_START)!!

        now = 1_000
        assertThat(game.removeCreature(first)).isTrue

        assertThat(scheduler.pendingCount).isEqualTo(1)

        now = 31_000
        scheduler.runDue()
        assertThat(first.isRemoved()).isTrue
        assertThat(map.spawnsMonster.getspawnMonsterList().first().spawnedMonsterMap).isEmpty()

        now = 91_000
        scheduler.runDue()

        val second = game.getMonsterByID(MONSTER_AUTO_ID_START + 1)
        assertThat(second).isNotNull
        assertThat(second!!.position).isEqualTo(spawnPosition)
        assertThat(second.isRemoved()).isFalse
        assertThat(second).isNotSameAs(first)
        assertThat(scheduler.pendingCount).isZero()
    }
}
