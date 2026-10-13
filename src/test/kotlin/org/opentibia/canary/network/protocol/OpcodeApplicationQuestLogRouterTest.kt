package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the quest-log opcodes (`0xF0`/`0xF1`):
 * `Game::playerShowQuestLog` / `playerShowQuestLine` (game.cpp:7391,7401) fire
 * the registered `playerOnRequestQuestLog` / `playerOnRequestQuestLine`
 * event callbacks; the quest data itself stays in the Lua scripts
 * (`data/scripts/eventcallbacks/player/on_request_quest_log.lua`) per
 * AGENTS.md 1.4.
 */
class OpcodeApplicationQuestLogRouterTest {

    private lateinit var dispatcher: GameDispatcher
    private lateinit var map: GameMap
    private lateinit var game: Game
    private lateinit var environment: LuaEnvironment

    private fun start() {
        dispatcher = GameDispatcher()
        dispatcher.start()
        map = GameMap()
        game = Game(map, dispatcher)
        environment = LuaEnvironment().apply { initState() }
    }

    private fun stop() {
        dispatcher.shutdown()
    }

    private fun context(): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = environment,
        game = game,
    )

    private fun hero(): Player = Player(1, "Hero", 150, 150, position = Position(1000, 1000, 7))

    private fun route(frame: ByteBuf, player: Player): Int {
        val handled = GamePacketRouter.route(player, frame, context())
        awaitWorldCommit()
        return handled
    }

    private fun awaitWorldCommit() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `show quest log opcode 0xF0 fires the registered Lua callback`() {
        start()
        try {
            environment.loadChunk(
                """
                local callback = EventCallback("QuestLogWatcher")
                callback.playerOnRequestQuestLog = function(player) questLogFired = true end
                callback:register()
                """.trimIndent(),
            )

            val frame = Unpooled.buffer()
            frame.writeByte(QuestLogPacketHandler.OPCODE_SHOW_QUEST_LOG)

            assertThat(route(frame, hero())).isEqualTo(1)
            assertThat(environment.getGlobal("questLogFired").toboolean()).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `quest line opcode 0xF1 fires the registered Lua callback with the quest id`() {
        start()
        try {
            environment.loadChunk(
                """
                local callback = EventCallback("QuestLineWatcher")
                callback.playerOnRequestQuestLine = function(player, questId) requestedQuestId = questId end
                callback:register()
                """.trimIndent(),
            )

            val frame = Unpooled.buffer()
            frame.writeByte(QuestLogPacketHandler.OPCODE_QUEST_LINE)
            frame.writeShortLE(1234)

            assertThat(route(frame, hero())).isEqualTo(1)
            assertThat(environment.getGlobal("requestedQuestId").toint()).isEqualTo(1234)
        } finally {
            stop()
        }
    }

    @Test
    fun `show quest log opcode 0xF0 without a callback is inert`() {
        start()
        try {
            val frame = Unpooled.buffer()
            frame.writeByte(QuestLogPacketHandler.OPCODE_SHOW_QUEST_LOG)

            assertThat(route(frame, hero())).isEqualTo(1)
            assertThat(environment.eventCallbacks.names()).isEmpty()
        } finally {
            stop()
        }
    }
}
