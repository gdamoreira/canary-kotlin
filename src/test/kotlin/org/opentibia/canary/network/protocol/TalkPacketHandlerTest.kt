package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Proves the opcode 0x96 path: `ProtocolGame::parseSay` field order decoded into a DTO and the
 * talkaction branch of `Game::playerSaySpell` dispatched through [org.opentibia.canary.lua.runtime.ScriptingDispatch].
 */
class TalkPacketHandlerTest {

    @Test
    fun `parses a say payload into a command dto`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1) // TALKTYPE_SAY
        payload.writeTibiaString("!hello world")

        val command = TalkPacketParser.parse(payload)

        assertThat(command).isEqualTo(
            SayCommand(type = 1, receiver = "", channelId = 0, text = "!hello world")
        )
    }

    @Test
    fun `parses a private-to payload reading the receiver before the text`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5) // TALKTYPE_PRIVATE_TO
        payload.writeTibiaString("Knight")
        payload.writeTibiaString("hello Knight")

        val command = TalkPacketParser.parse(payload)

        assertThat(command).isEqualTo(
            SayCommand(type = 5, receiver = "Knight", channelId = 0, text = "hello Knight")
        )
    }

    @Test
    fun `parses a channel payload reading the channel id after the type`() {
        val payload = Unpooled.buffer()
        payload.writeByte(7) // TALKTYPE_CHANNEL_Y
        payload.writeShortLE(0x0B)
        payload.writeTibiaString("hello channel")

        val command = TalkPacketParser.parse(payload)

        assertThat(command).isEqualTo(
            SayCommand(type = 7, receiver = "", channelId = 11, text = "hello channel")
        )
    }

    @Test
    fun `rejects a truncated payload that ends before the text string`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1) // TALKTYPE_SAY, missing string length + bytes

        assertThat(TalkPacketParser.parse(payload)).isNull()
    }

    @Test
    fun `router dispatches a registered talkaction through the world commit lane`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local t = TalkAction("!dispatch")
            function t.onSay(player, words, param)
                seen_talk_player = player:getName()
                seen_talk_words = words
                seen_talk_param = param
            end
            t:register()
            """.trimIndent()
        )

        val dispatcher = GameDispatcher().apply { start() }
        try {
            val frame = Unpooled.buffer()
            frame.writeByte(0x96)
            frame.writeByte(1) // TALKTYPE_SAY
            frame.writeTibiaString("!dispatch hello world")

            val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
            val handled = GamePacketRouter.route(
                player,
                frame,
                GamePacketContext(
                    map = GameMap(),
                    dispatcher = dispatcher,
                    luaEnvironment = environment,
                ),
            )
            assertThat(handled).isEqualTo(1)

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(environment.getGlobal("seen_talk_player").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_talk_words").tojstring()).isEqualTo("!dispatch")
            assertThat(environment.getGlobal("seen_talk_param").tojstring()).isEqualTo("hello world")
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `handleSay reports false and enqueues nothing for an unregistered command`() {
        val environment = LuaEnvironment().apply { initState() }
        val payload = Unpooled.buffer()
        payload.writeByte(1) // TALKTYPE_SAY
        payload.writeTibiaString("!missing")

        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
            assertThat(TalkPacketHandler.handleSay(player, payload, dispatcher, environment)).isFalse()

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(environment.getGlobal("seen_talk_player").isnil()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }
}
