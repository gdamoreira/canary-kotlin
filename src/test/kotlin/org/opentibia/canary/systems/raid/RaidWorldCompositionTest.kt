package org.opentibia.canary.systems.raid

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.bindings.core.UnwiredGameOperations
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.webhook.Webhook
import org.opentibia.canary.network.webhook.WebhookResponse
import org.opentibia.canary.network.webhook.WebhookSender
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for the production raid composition seams composed by the
 * server root, each mirroring a Canary global:
 *
 *  - [WorldRaidDispatcher] -> `g_dispatcher().scheduleEvent`/`stopEvent` on
 *    `DispatcherLane::Maintenance` (src/lua/creature/raids.cpp:105, 144);
 *  - [WorldRaidAnnouncer] -> `g_game().broadcastMessage` and
 *    `g_webhook().sendMessage` (`AnnounceEvent::executeEvent`,
 *    src/lua/creature/raids.cpp:345-347);
 *  - [WorldRaidScriptRunner] -> `ScriptEvent`'s Lua `onRaid` load/execute
 *    (`Event::loadScript`, src/lua/global/baseevents.cpp:118-141,
 *    `ScriptEvent::executeEvent`, raids.cpp:612-627);
 *  - [RaidGameOperations] -> `GameFunctions::luaGameStartRaid`
 *    (src/lua/functions/core/game/game_functions.cpp:752-772).
 */
class RaidWorldCompositionTest {

    // --- WorldRaidDispatcher ------------------------------------------------

    @Test
    fun `the world dispatcher schedules on the maintenance lane and returns the event id`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val fired = CountDownLatch(1)
            val id = WorldRaidDispatcher(dispatcher).scheduleEvent(10, "Raids::checkRaids") { fired.countDown() }

            assertThat(id).isNotZero()
            assertThat(fired.await(2, TimeUnit.SECONDS)).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the world dispatcher reserves the maintenance lane and releases it through stopEvent`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val raidDispatcher = WorldRaidDispatcher(dispatcher)
            val id = raidDispatcher.scheduleEvent(500, "Raid::executeRaidEvent") {}

            assertThat(dispatcher.laneReservationCount(DispatcherLane.Maintenance)).isEqualTo(1)
            assertThat(dispatcher.laneReservationCount(DispatcherLane.WorldCommit)).isZero()

            raidDispatcher.stopEvent(id)

            assertThat(dispatcher.laneReservationCount(DispatcherLane.Maintenance)).isZero()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the world dispatcher returns zero when the dispatcher is not running`() {
        val dispatcher = GameDispatcher()

        assertThat(WorldRaidDispatcher(dispatcher).scheduleEvent(10, "Raids::checkRaids") {}).isZero()
    }

    // --- WorldRaidAnnouncer -------------------------------------------------

    @Test
    fun `the world announcer broadcasts the announce type as the canary message class`() {
        val game = Game(GameMap(), GameDispatcher())
        val player = Player(id = 1L, name = "Hero", maxHealth = 100, health = 100, position = Position(1, 1, 7))
        val received = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text -> received.add(type to text) }
        game.addPlayer(player)

        WorldRaidAnnouncer(game).broadcastMessage("Beware!", MessageType.MESSAGE_GAME_HIGHLIGHT)

        assertThat(received).containsExactly(MessageClass.MESSAGE_GAME_HIGHLIGHT to "Beware!")
    }

    @Test
    fun `the world announcer forwards the webhook through the composed sender`() {
        val sender = RecordingSender()
        val webhook = Webhook(
            sender = sender,
            serverName = "Canary",
            webhookUrl = "https://discord.example/hook",
        )
        val game = Game(GameMap(), GameDispatcher())

        WorldRaidAnnouncer(game, webhook).sendWebhook(":space_invader: Beware!")

        runBlocking<Unit> { webhook.sendWebhook() }
        assertThat(sender.calls).hasSize(1)
        assertThat(sender.calls.single().second).contains(":space_invader: Beware!")
    }

    @Test
    fun `the world announcer drops the webhook when no sender is composed`() {
        val game = Game(GameMap(), GameDispatcher())

        // No `g_webhook()` sink: the call degrades to a no-op instead of failing.
        WorldRaidAnnouncer(game).sendWebhook(":space_invader: Beware!")
    }

    // --- WorldRaidScriptRunner ----------------------------------------------

    @Test
    fun `the script runner loads and executes the raid onRaid function`(@TempDir dir: Path) {
        val script = writeScript(dir, "on_raid.lua", "raidFired = false\nfunction onRaid() raidFired = true end\n")
        val environment = LuaEnvironment().apply { initState() }
        val runner = WorldRaidScriptRunner(environment)

        assertThat(runner.loadScript(script, "on_raid")).isTrue()
        // `LuaScriptInterface::getEvent` moves the function into the event
        // table and resets the global (luascript.cpp:618-620).
        assertThat(environment.getGlobal("onRaid").isnil()).isTrue()

        assertThat(runner.executeOnRaid("on_raid")).isTrue()
        assertThat(environment.getGlobal("raidFired").toboolean()).isTrue()
        assertThat(runner.executeOnRaid("unknown")).isFalse()
    }

    @Test
    fun `the script runner rejects a missing file and a script without onRaid`(@TempDir dir: Path) {
        val withoutFunction = writeScript(dir, "plain.lua", "local answer = 42\n")
        val environment = LuaEnvironment().apply { initState() }
        val runner = WorldRaidScriptRunner(environment)

        assertThat(runner.loadScript(dir.resolve("missing.lua").toString(), "missing")).isFalse()
        assertThat(runner.loadScript(withoutFunction, "plain")).isFalse()
    }

    @Test
    fun `the script runner drops the loaded functions on reInitState`(@TempDir dir: Path) {
        val script = writeScript(dir, "on_raid.lua", "function onRaid() end\n")
        val environment = LuaEnvironment().apply { initState() }
        val runner = WorldRaidScriptRunner(environment)
        assertThat(runner.loadScript(script, "on_raid")).isTrue()

        runner.reInitState()

        assertThat(runner.executeOnRaid("on_raid")).isFalse()
    }

    // --- RaidGameOperations -------------------------------------------------

    @Test
    fun `the raid game operations mirror luaGameStartRaid`(@TempDir dir: Path) {
        writeRaid(dir, "Called", true)
        val dispatcher = StubRaidDispatcher()
        val raids = Raids(stubEnvironment(dir.toFile().path, dispatcher = dispatcher))
        assertThat(raids.loadFromXml()).isTrue()
        val operations = RaidGameOperations(UnwiredGameOperations, raids)

        assertThat(operations.startRaid("Ghost")).isEqualTo(ReturnValue.NOSUCHRAIDEXISTS.ordinal)
        assertThat(operations.startRaid("called")).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(raids.getRunning()?.getName()).isEqualTo("Called")
        assertThat(dispatcher.scheduled).hasSize(1)
        assertThat(operations.startRaid("Called")).isEqualTo(ReturnValue.ANOTHERRAIDISALREADYEXECUTING.ordinal)
        // The name is resolved before the running gate, like the C++ tail
        // (game_functions.cpp:756-766).
        assertThat(operations.startRaid("Ghost")).isEqualTo(ReturnValue.NOSUCHRAIDEXISTS.ordinal)

        // The remaining Game surface is delegated unchanged.
        assertThat(operations.getMonsterCount()).isZero()
    }

    private class RecordingSender : WebhookSender {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun sendRequest(url: String, payload: String): WebhookResponse {
            calls.add(url to payload)
            return WebhookResponse(204, """{"ok":true}""")
        }
    }

    private fun writeScript(dir: Path, name: String, source: String): String =
        dir.resolve(name).toFile().apply { writeText(source) }.path

    private fun writeRaid(dir: Path, name: String, loaded: Boolean) {
        val raidsDir = File(dir.toFile(), "raids").apply { mkdirs() }
        File(raidsDir, "$name.xml").writeText(
            if (loaded) "<raid><announce delay=\"1000\" message=\"go\"/></raid>" else "<raid>",
        )
        File(raidsDir, "raids.xml").writeText(
            """<raids><raid name="$name" file="$name.xml" interval2="1" margin="1"/></raids>""",
        )
    }

    private class StubRaidDispatcher : RaidDispatcher {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

        override fun scheduleEvent(delayMs: Long, taskName: String, callback: () -> Unit): Long {
            scheduled.add(delayMs to callback)
            return scheduled.size.toLong()
        }

        override fun stopEvent(eventId: Long) {}
    }

    private fun stubEnvironment(
        dataDirectory: String,
        dispatcher: StubRaidDispatcher = StubRaidDispatcher(),
    ): RaidEnvironment = object : RaidEnvironment {
        override val dataDirectory: String = dataDirectory
        override val dispatcher: RaidDispatcher = dispatcher

        override val announcer: RaidAnnouncer = object : RaidAnnouncer {
            override fun broadcastMessage(message: String, messageType: MessageType) {}

            override fun sendWebhook(message: String) {}
        }

        override val spawner: RaidSpawner = object : RaidSpawner {
            override fun createMonster(name: String): RaidMonster? = null

            override fun placeCreature(
                monster: RaidMonster,
                position: Position,
                extendedPos: Boolean,
                force: Boolean,
            ): Boolean = false

            override fun getTile(x: Int, y: Int, z: Int): RaidTile? = null
        }

        override val random: RaidRandom = RaidRandom { minNumber, _ -> minNumber }
        override val clock: RaidClock = RaidClock { 0L }
        override val scriptRunner: RaidScriptRunner? = null
    }
}
