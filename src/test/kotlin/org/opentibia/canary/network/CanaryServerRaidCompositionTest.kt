package org.opentibia.canary.network

import java.io.File
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
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.spawns.MONSTER_AUTO_ID_START
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Production-composition tests for the raid container the server root composes:
 * [CanaryServer.start] runs the `Game::setGameState(GAME_STATE_INIT)` half of
 * the boot (`raids.loadFromXml(); raids.startup()`,
 * src/game/game.cpp:979-980) over the configured datapack directory, the
 * [CanaryServer.stop] drain clears it (`Game::shutdown` -> `raids.clear()`,
 * game.cpp:9504) and the Lua `Game.startRaid` entry point
 * (`GameFunctions::luaGameStartRaid`, game_functions.cpp:752-772) starts a
 * loaded raid whose spawn events place through the wave-70
 * [org.opentibia.canary.systems.raid.GameRaidSpawner] bridge.
 */
class CanaryServerRaidCompositionTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var dispatcher: GameDispatcher
    private lateinit var gameMap: GameMap
    private val spawnPosition = Position(1200, 1200, 7)

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        dispatcher = GameDispatcher().apply { start() }
        gameMap = GameMap()
        gameMap.getOrCreateTile(spawnPosition).ground = Item(id = 100)
        gameMap.monsterfile = tempDir.resolve("canary-monster.xml").toFile().apply {
            writeText("<monsters/>")
        }.path
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    private fun server(
        monsters: Monsters = Monsters(),
        environment: LuaEnvironment = LuaEnvironment().apply { initState() },
    ): CanaryServer = CanaryServer(
        loginPort = 0,
        gamePort = 0,
        gameMap = gameMap,
        dispatcher = dispatcher,
        configManager = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
        luaEnvironment = environment,
        monsters = monsters,
        dataDirectory = tempDir.toFile().path,
    )

    private fun writeRaid(name: String, raidXml: String, interval2: Int = 1, margin: Int = 0) {
        val raidsDir = File(tempDir.toFile(), "raids").apply { mkdirs() }
        File(raidsDir, "$name.xml").writeText(raidXml)
        File(raidsDir, "raids.xml").writeText(
            """<raids><raid name="$name" file="$name.xml" interval2="$interval2" margin="$margin"/></raids>""",
        )
    }

    @Test
    fun `boot loads and starts the raids and stop clears them`() {
        writeRaid("BootRaid", """<raid><announce delay="1000" type="event" message="Boot!"/></raid>""")
        val server = server()
        val raids = requireNotNull(server.raids)
        server.start()

        assertThat(raids.isLoaded()).isTrue()
        assertThat(raids.isStarted()).isTrue()
        assertThat(raids.getRaidByName("BootRaid")).isNotNull

        server.stop()

        assertThat(raids.isStarted()).isFalse()
        assertThat(raids.getRaidByName("BootRaid")).isNull()
    }

    @Test
    fun `an eligible raid starts on the raid check clock and announces to online players`() {
        writeRaid("ClockRaid", """<raid><announce delay="1000" type="event" message="The Toad is coming!"/></raid>""")
        val server = server()
        server.start()
        val raids = requireNotNull(server.raids)

        val player = Player(id = 1L, name = "Hero", maxHealth = 100, health = 100, position = spawnPosition)
        val announced = CountDownLatch(1)
        val messages = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text ->
            messages.add(type to text)
            announced.countDown()
        }
        server.game.addPlayer(player)
        awaitLane()

        // `Raids::checkRaids` (raids.cpp:117): margin 0 and interval2 1 make the
        // raid eligible and the roll always pass; the real dispatcher then runs
        // the announce event after its configured delay.
        raids.checkRaids()

        assertThat(raids.getRunning()).isNotNull
        assertThat(announced.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(messages).contains(MessageClass.MESSAGE_EVENT_ADVANCE to "The Toad is coming!")
    }

    @Test
    fun `Game startRaid returns success for a loaded raid and spawns through the world bridge`() {
        writeRaid(
            "SpawnRaid",
            """
            <raid>
              <singlespawn delay="1000" name="Toad" x="${spawnPosition.x}" y="${spawnPosition.y}" z="${spawnPosition.z}"/>
            </raid>
            """.trimIndent(),
        )
        val monsters = Monsters().apply {
            tryAddMonsterType(
                "Toad",
                MonsterType("Toad").apply {
                    info.health = 100
                    info.healthMax = 100
                },
            )
        }
        val environment = LuaEnvironment().apply { initState() }
        val server = server(monsters = monsters, environment = environment)
        server.start()
        val raids = requireNotNull(server.raids)

        environment.loadChunk("raidResult = Game.startRaid('SpawnRaid')")
        assertThat(environment.getGlobal("raidResult").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(raids.getRunning()?.getName()).isEqualTo("SpawnRaid")

        environment.loadChunk("raidAgain = Game.startRaid('SpawnRaid')")
        assertThat(environment.getGlobal("raidAgain").toint())
            .isEqualTo(ReturnValue.ANOTHERRAIDISALREADYEXECUTING.ordinal)

        environment.loadChunk("raidMissing = Game.startRaid('Ghost')")
        assertThat(environment.getGlobal("raidMissing").toint()).isEqualTo(ReturnValue.NOSUCHRAIDEXISTS.ordinal)

        val monster = awaitMonster(server, MONSTER_AUTO_ID_START)
        assertThat(monster).isNotNull
        assertThat(monster!!.name).isEqualTo("Toad")
        assertThat(monster.position).isEqualTo(spawnPosition)
        assertThat(gameMap.getTile(spawnPosition)!!.getTopCreature()).isSameAs(monster)
    }

    @Test
    fun `a raid script event runs the datapack onRaid through the bound runner`() {
        writeRaid("ScriptRaid", """<raid><script delay="1000" script="script_raid.lua"/></raid>""")
        val scriptsDir = File(File(tempDir.toFile(), "raids"), "scripts").apply { mkdirs() }
        File(scriptsDir, "script_raid.lua").writeText(
            "raidScriptFired = false\nfunction onRaid() raidScriptFired = true end\n",
        )
        val environment = LuaEnvironment().apply { initState() }
        val server = server(environment = environment)
        server.start()

        environment.loadChunk("raidScriptResult = Game.startRaid('ScriptRaid')")
        assertThat(environment.getGlobal("raidScriptResult").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)

        assertThat(awaitGlobal(environment, "raidScriptFired")).isTrue()
    }

    @Test
    fun `a server without a data directory keeps the raids uncomposed`() {
        val plain = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            gameMap = gameMap,
            dispatcher = dispatcher,
            configManager = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
            monsters = Monsters(),
        )

        assertThat(plain.raids).isNull()
        // The default `Game.startRaid` seam keeps the Canary empty-registry
        // result (game_functions.cpp:757-759).
        plain.scriptingEnvironment.loadChunk("raidMissing = Game.startRaid('Example')")
        assertThat(plain.scriptingEnvironment.getGlobal("raidMissing").toint())
            .isEqualTo(ReturnValue.NOSUCHRAIDEXISTS.ordinal)
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun awaitMonster(server: CanaryServer, id: Long): Monster? {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            awaitLane()
            server.game.getMonsterByID(id)?.let { return it }
            Thread.sleep(25)
        }
        return server.game.getMonsterByID(id)
    }

    private fun awaitGlobal(environment: LuaEnvironment, name: String): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            awaitLane()
            if (environment.getGlobal(name).toboolean()) {
                return true
            }
            Thread.sleep(25)
        }
        return environment.getGlobal(name).toboolean()
    }
}
