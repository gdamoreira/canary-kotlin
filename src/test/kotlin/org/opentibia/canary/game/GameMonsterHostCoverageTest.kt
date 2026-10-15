package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.raid.RaidMonster
import org.opentibia.canary.systems.raid.RaidSpawner
import org.opentibia.canary.systems.raid.RaidTile
import org.opentibia.canary.systems.raid.SingleSpawnEvent
import java.util.concurrent.atomic.AtomicLong
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Wave 69 F2 placement coverage for the creature-event hosts:
 *
 * `Game::internalPlaceCreature` (src/game/game.cpp:1495) is the single world
 * entry of every path that is not the engine spawner — summons
 * (`Monster::createSummon` -> `Game::placeCreature`), the legacy raid engine
 * (`SingleSpawnEvent::executeEvent` -> `g_game().placeCreature`,
 * src/lua/creature/raids.cpp:395) and the Lua `Game.createMonster` binding
 * (`game_functions.cpp:589` -> `Game::placeCreature`, game.cpp:1524). The
 * `Creature::onThink`/`Creature::dropCorpse` scripting loops
 * (src/creatures/creature.cpp:157-163, 819-824) only ran for monsters bound by
 * the spawn path (`MonsterSpawnPath.onMonsterSpawn`), so these creatures kept
 * an inert think/death host. The world binder now runs for every monster the
 * map accepts and stays untouched for a rejected placement or a player.
 */
class GameMonsterHostCoverageTest {

    private val center = Position(1000, 1000, 7)

    private lateinit var game: Game
    private val bound = mutableListOf<Creature>()

    @BeforeEach
    fun setUp() {
        game = Game(GameMap(), GameDispatcher())
        game.creatureEventHostBinder = { creature -> bound.add(creature) }
    }

    private fun Game.groundTile(position: Position = center): Tile =
        map.getOrCreateTile(position).also { it.ground = Item(id = 100) }

    private fun monster(id: Long = 7, position: Position = center): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = position)

    @Test
    fun `a placed monster receives the world creature event hosts`() {
        game.groundTile()
        val monster = monster()

        assertThat(game.internalPlaceCreature(monster, center)).isTrue()

        assertThat(bound).containsExactly(monster)
    }

    @Test
    fun `a rejected placement leaves the creature event hosts unbound`() {
        game.groundTile()
        val occupant = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)
        assertThat(game.internalPlaceCreature(occupant, center)).isTrue()

        assertThat(game.internalPlaceCreature(monster(), center)).isFalse()

        assertThat(bound).isEmpty()
    }

    @Test
    fun `placing a player does not bind the monster creature event hosts`() {
        game.groundTile()
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)

        assertThat(game.internalPlaceCreature(player, center)).isTrue()

        assertThat(bound).isEmpty()
    }

    @Test
    fun `a raid spawn places the monster through the game and binds its hosts`() {
        game.groundTile()
        val spawner = GameRaidSpawner(game)
        val event = SingleSpawnEvent(spawner)
        assertThat(event.configureRaidEvent(raidSpawnElement("Rat"))).isTrue()

        assertThat(event.executeEvent()).isTrue()

        assertThat(bound).containsExactly(spawner.placed)
        assertThat(spawner.placed.position).isEqualTo(center)
    }

    /** A `singlespawn` element of the raid XML (raids.cpp:351-393). */
    private fun raidSpawnElement(name: String): org.w3c.dom.Element {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        return document.createElement("singlespawn").apply {
            setAttribute("delay", "1000")
            setAttribute("name", name)
            setAttribute("x", center.x.toString())
            setAttribute("y", center.y.toString())
            setAttribute("z", center.z.toString())
        }
    }

    /** The real `Monster` behind the raid `RaidMonster` view. */
    private class HostedRaidMonster(val monster: Monster) : RaidMonster(monster.name)

    /**
     * `RaidSpawner` over the composed world, mirroring the production bridge the
     * raid engine expects (`g_game().placeCreature`, raids.cpp:395).
     */
    private class GameRaidSpawner(private val game: Game) : RaidSpawner {
        private val nextId = AtomicLong(500)
        lateinit var placed: Monster

        override fun createMonster(name: String): RaidMonster {
            placed = Monster(
                id = nextId.getAndIncrement(),
                name = name,
                maxHealth = 100,
                health = 100,
                position = Position(0, 0, 0),
            )
            return HostedRaidMonster(placed)
        }

        override fun placeCreature(
            monster: RaidMonster,
            position: Position,
            extendedPos: Boolean,
            force: Boolean,
        ): Boolean = game.placeCreature((monster as HostedRaidMonster).monster, position, extendedPos, force)

        override fun getTile(x: Int, y: Int, z: Int): RaidTile? = null
    }
}
