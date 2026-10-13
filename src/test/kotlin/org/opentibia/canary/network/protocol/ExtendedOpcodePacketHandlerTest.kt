package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Decode and dispatch tests for `ProtocolGame::parseExtendedOpcode` (client
 * opcode `0x32`, protocolgame.cpp:10673-10679) and its execution path
 * `Game::parsePlayerExtendedOpcode` (game.cpp:11114-11123), which fires the
 * player's `CREATURE_EVENT_EXTENDED_OPCODE` creature events.
 */
class ExtendedOpcodePacketHandlerTest {

    @Test
    fun `parses the extended opcode and its string buffer`() {
        val payload = Unpooled.buffer()
        payload.writeByte(200)
        payload.writeTibiaString("data")

        val command = ExtendedOpcodePacketHandler.parse(payload)

        assertThat(command).isEqualTo(ExtendedOpcodeCommand(opcode = 200, buffer = "data"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses an empty extended opcode buffer`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeTibiaString("")

        val command = ExtendedOpcodePacketHandler.parse(payload)

        assertThat(command).isEqualTo(ExtendedOpcodeCommand(opcode = 0, buffer = ""))
    }

    @Test
    fun `rejects a truncated extended opcode header`() {
        assertThat(ExtendedOpcodePacketHandler.parse(Unpooled.buffer())).isNull()
    }

    @Test
    fun `rejects an extended opcode string missing its declared bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(200)
        payload.writeShortLE(8)
        payload.writeByte(1)

        assertThat(ExtendedOpcodePacketHandler.parse(payload)).isNull()
    }

    @Test
    fun `fires the registered onExtendedOpcode creature event`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local e = CreatureEvent("TestOpcode")
            function e.onExtendedOpcode(player, opcode, buffer)
                seen_opcode_player = player:getName()
                seen_opcode = opcode
                seen_buffer = buffer
            end
            e:register()
            """.trimIndent(),
        )
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val payload = Unpooled.buffer()
            payload.writeByte(200)
            payload.writeTibiaString("data")

            val handled = ExtendedOpcodePacketHandler.handle(
                hero(),
                payload,
                dispatcher,
                environment,
            )

            assertThat(handled).isTrue()
            awaitWorldCommit(dispatcher)
            assertThat(environment.getGlobal("seen_opcode_player").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_opcode").toint()).isEqualTo(200)
            assertThat(environment.getGlobal("seen_buffer").tojstring()).isEqualTo("data")
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `consumes a valid packet even without a registered event`() {
        val environment = LuaEnvironment().apply { initState() }
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val payload = Unpooled.buffer()
            payload.writeByte(1)
            payload.writeTibiaString("x")

            assertThat(ExtendedOpcodePacketHandler.handle(hero(), payload, dispatcher, environment)).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `does not consume a truncated packet`() {
        val environment = LuaEnvironment().apply { initState() }
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val payload = Unpooled.buffer()
            payload.writeByte(1)

            assertThat(ExtendedOpcodePacketHandler.handle(hero(), payload, dispatcher, environment)).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
