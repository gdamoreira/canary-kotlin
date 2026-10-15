package org.opentibia.canary.creatures.spawns

import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.npcs.Npcs
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Production-composition tests for the NPC spawn path: [NpcSpawnPath] loads the
 * map's npc spawn XML (`IOMap::loadNpcs`, src/io/iomap.hpp:64-73) and starts the
 * engine (`Game::setGameState(GAME_STATE_INIT)` -> `map.spawnsNpc.startup()`,
 * src/game/game.cpp:971), which places every block through
 * `Game::internalPlaceCreature` (spawn_npc.cpp:194-205, game.cpp:1495) and
 * registers it through `Npc::addList` -> `Game::addNpc` (npc.cpp:1225).
 */
class NpcSpawnPathTest {

    @TempDir
    lateinit var tempDir: Path

    private val dispatcher = GameDispatcher()
    private val config = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier })

    private val spawnPosition = Position(200, 200, 7)

    private fun writeNpcFile(map: GameMap) {
        map.npcfile = File(tempDir.toFile(), "canary-npc.xml").apply {
            writeText(
                """
                <npcs>
                    <npc centerx="200" centery="200" centerz="7" radius="2">
                        <npc name="Shopkeeper" x="0" y="0" direction="1" spawntime="60"/>
                    </npc>
                </npcs>
                """.trimIndent(),
            )
        }.path
    }

    private fun newMap(): GameMap {
        val map = GameMap()
        map.getOrCreateTile(spawnPosition).ground = Item(id = 100)
        writeNpcFile(map)
        return map
    }

    private fun path(
        map: GameMap,
        game: Game,
        registry: Npcs = Npcs().apply { getNpcType("Shopkeeper", create = true) },
    ): NpcSpawnPath = NpcSpawnPath(
        game = game,
        map = map,
        npcTypes = registry,
        spawnScheduler = FakeSpawnScheduler { 0L },
        config = config,
    )

    @Test
    fun `load parses the npc spawn file and startup places the npc through the world`() {
        val map = newMap()
        val game = Game(map, dispatcher)
        val path = path(map, game)

        assertThat(path.load()).isTrue()
        assertThat(map.spawnsNpc.isLoaded()).isTrue()

        path.startup()

        val npc = game.getNpcByName("Shopkeeper")
        assertThat(npc).isNotNull
        assertThat(npc!!.position).isEqualTo(spawnPosition)
        assertThat(npc.direction).isEqualTo(Direction.East)
        assertThat(npc.getMasterPos()).isEqualTo(spawnPosition)
        assertThat(npc.npcType?.name).isEqualTo("Shopkeeper")
        assertThat(npc.id).isEqualTo(NPC_AUTO_ID_START)
        assertThat(game.getNpcs()).hasSize(1)
        assertThat(map.spawnsNpc.isStarted()).isTrue()
    }

    @Test
    fun `shutdown clears the spawn engine through the game shutdown call site`() {
        val map = newMap()
        val path = path(map, Game(map, dispatcher))
        path.load()
        path.startup()

        path.shutdown()

        assertThat(map.spawnsNpc.isStarted()).isFalse()
        assertThat(map.spawnsNpc.getSpawnNpcList()).isEmpty()
    }

    @Test
    fun `an npc block whose type is not registered stays unspawned`() {
        val map = newMap()
        val game = Game(map, dispatcher)
        val path = path(map, game, registry = Npcs())

        assertThat(path.load()).isTrue()
        path.startup()

        assertThat(game.getNpcByName("Shopkeeper")).isNull()
        assertThat(game.getNpcs()).isEmpty()
    }

    @Test
    fun `a rejected map placement leaves the block unspawned`() {
        val map = GameMap()
        writeNpcFile(map)
        val game = Game(map, dispatcher)
        val path = path(map, game)

        path.load()
        path.startup()

        assertThat(game.getNpcs()).isEmpty()
    }

    @Test
    fun `load is idempotent through the spawns loaded guard`() {
        val map = newMap()
        val game = Game(map, dispatcher)
        val path = path(map, game)

        assertThat(path.load()).isTrue()
        val loaded = map.spawnsNpc

        assertThat(path.load()).isTrue()
        assertThat(map.spawnsNpc).isSameAs(loaded)
    }
}
