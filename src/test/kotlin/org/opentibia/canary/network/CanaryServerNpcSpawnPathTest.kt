package org.opentibia.canary.network

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.npcs.Npcs
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Production-composition tests for the booted NPC spawn path: [CanaryServer]
 * loads the map's npc spawn XML (`IOMap::loadNpcs`, src/io/iomap.hpp:64-73),
 * starts the engine on the WorldCommit lane with the monster start
 * (`Game::setGameState(GAME_STATE_INIT)` -> `map.spawnsNpc.startup()`,
 * src/game/game.cpp:971) and drains it on [CanaryServer.stop]
 * (`Game::shutdown`, src/game/game.cpp:9502). Every block must place through
 * `Game::internalPlaceCreature` (src/game/game.cpp:1495) and register through
 * `Npc::addList` -> `Game::addNpc` (npc.cpp:1225).
 */
class CanaryServerNpcSpawnPathTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var dispatcher: GameDispatcher
    private lateinit var gameMap: GameMap

    private val spawnPosition = Position(200, 200, 7)

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
        gameMap = GameMap()
        gameMap.getOrCreateTile(spawnPosition).ground = Item(id = 100)
        gameMap.npcfile = tempDir.resolve("canary-npc.xml").toFile().apply {
            writeText(
                """
                <npcs>
                    <npc centerx="200" centery="200" centerz="7" radius="4">
                        <npc name="Shopkeeper" x="0" y="0" spawntime="60"/>
                    </npc>
                </npcs>
                """.trimIndent(),
            )
        }.path
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    private fun server(npcs: Npcs? = Npcs().apply { getNpcType("Shopkeeper", create = true) }): CanaryServer =
        CanaryServer(
            loginPort = 0,
            gamePort = 0,
            gameMap = gameMap,
            dispatcher = dispatcher,
            configManager = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
            npcs = npcs,
        )

    @Test
    fun `server boot loads the npc spawn XML and places the npcs on the world lane`() {
        val server = server()
        try {
            server.start()
            awaitLane()

            val npc = server.game.getNpcByName("Shopkeeper")
            assertThat(npc).isNotNull
            assertThat(npc!!.position).isEqualTo(spawnPosition)
            assertThat(npc.getTile()).isNotNull
            assertThat(server.game.getNpcs()).hasSize(1)
            assertThat(gameMap.spawnsNpc.isLoaded()).isTrue
            assertThat(gameMap.spawnsNpc.isStarted()).isTrue
            assertThat(server.npcSpawnPath).isNotNull
        } finally {
            server.stop()
        }
    }

    @Test
    fun `stop cancels the npc spawn checks and releases the engine state`() {
        val server = server()
        try {
            server.start()
            awaitLane()

            server.stop()

            assertThat(gameMap.spawnsNpc.isStarted()).isFalse
            assertThat(gameMap.spawnsNpc.getSpawnNpcList()).isEmpty()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a server without a composed npc registry keeps the npc spawn path uncomposed`() {
        val plain = server(npcs = null)

        assertThat(plain.npcSpawnPath).isNull()
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
