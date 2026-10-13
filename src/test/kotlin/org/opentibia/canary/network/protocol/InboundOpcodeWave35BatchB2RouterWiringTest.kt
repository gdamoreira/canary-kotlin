package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
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
 * Router wiring tests for wave 35 batch B2: item interaction (`0x84`/`0x85`/
 * `0x86`/`0x8B`/`0x9F`), text windows (`0x89`/`0x8A`), channels (`0x97`-`0x9A`/
 * `0x9E`) and the read-and-discard queries (`0x8E`/`0x9D`). Each decoded command
 * crosses the WorldCommit confinement boundary (AGENTS.md 1.2).
 */
class InboundOpcodeWave35BatchB2RouterWiringTest {

    @Test
    fun `routes every batch B2 opcode and counts them handled`() {
        val frame = Unpooled.buffer()

        frame.writeByte(ItemInteractionPacketHandler.OPCODE_USE_WITH_CREATURE)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2160)
        frame.writeByte(1)
        frame.writeIntLE(77)

        frame.writeByte(ItemInteractionPacketHandler.OPCODE_ROTATE_ITEM)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2160)
        frame.writeByte(2)

        frame.writeByte(ItemInteractionPacketHandler.OPCODE_CONFIGURE_SHOW_OFF_SOCKET)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2578)
        frame.writeByte(1)

        frame.writeByte(TextWindowPacketHandler.OPCODE_TEXT_WINDOW)
        frame.writeIntLE(1)
        frame.writeTibiaString("Hero")

        frame.writeByte(TextWindowPacketHandler.OPCODE_HOUSE_WINDOW)
        frame.writeByte(0)
        frame.writeIntLE(1)
        frame.writeTibiaString("Guest")

        frame.writeByte(ItemInteractionPacketHandler.OPCODE_WRAPABLE_ITEM)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2348)
        frame.writeByte(4)

        frame.writeByte(AggressionAndReportPacketHandler.OPCODE_JOIN_AGGRESSION)
        frame.writeIntLE(7)

        frame.writeByte(ChannelPacketHandler.OPCODE_REQUEST_CHANNELS)

        frame.writeByte(ChannelPacketHandler.OPCODE_OPEN_CHANNEL)
        frame.writeShortLE(8)

        frame.writeByte(ChannelPacketHandler.OPCODE_CLOSE_CHANNEL)
        frame.writeShortLE(8)

        frame.writeByte(ChannelPacketHandler.OPCODE_OPEN_PRIVATE_CHANNEL)
        frame.writeTibiaString("Bubble")

        frame.writeByte(AggressionAndReportPacketHandler.OPCODE_GET_TEXT_FOR_REPORT)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2160)
        frame.writeByte(2)

        frame.writeByte(ChannelPacketHandler.OPCODE_CLOSE_NPC_CHANNEL)

        frame.writeByte(ItemInteractionPacketHandler.OPCODE_SET_MONSTER_PODIUM)
        frame.writeIntLE(0x0000ABCD)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2578)
        frame.writeByte(2)
        frame.writeByte(3)
        frame.writeByte(1)
        frame.writeByte(0)

        assertThat(route(frame)).isEqualTo(14)
    }

    @Test
    fun `rejects a malformed use with creature payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ItemInteractionPacketHandler.OPCODE_USE_WITH_CREATURE)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2160)
        frame.writeByte(1)
        frame.writeShortLE(1) // creature id needs 4 bytes

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `rejects a malformed text window payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TextWindowPacketHandler.OPCODE_TEXT_WINDOW)
        frame.writeIntLE(1)
        frame.writeShortLE(10)
        frame.writeByte('A'.code)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `rejects a malformed open private channel payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ChannelPacketHandler.OPCODE_OPEN_PRIVATE_CHANNEL)
        frame.writeShortLE(9)
        frame.writeByte('B'.code)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `rejects a malformed set monster podium payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ItemInteractionPacketHandler.OPCODE_SET_MONSTER_PODIUM)
        frame.writeIntLE(0x0000ABCD)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2578)
        frame.writeByte(2)
        frame.writeByte(3) // visibility bytes missing

        assertThat(route(frame)).isEqualTo(0)
    }

    private fun writePosition(frame: ByteBuf, position: Position) {
        frame.writeShortLE(position.x)
        frame.writeShortLE(position.y)
        frame.writeByte(position.z)
    }

    private fun route(frame: ByteBuf): Int {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val handled = GamePacketRouter.route(
                hero(),
                frame,
                GamePacketContext(
                    map = GameMap(),
                    dispatcher = dispatcher,
                    luaEnvironment = LuaEnvironment().apply { initState() },
                ),
            )
            awaitWorldCommit(dispatcher)
            return handled
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
