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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for the wave-20 inbound opcode batch: trade
 * (`0x7D`-`0x80`), containers (`0x75`/`0x87`/`0x88`/`0x95`), follow (`0xA2`),
 * outfit/mount (`0xD3`/`0xD4`) and use-item-ex (`0x83`). Each decoded command
 * crosses the WorldCommit confinement boundary (AGENTS.md 1.2).
 */
class InboundOpcodeRouterWiringTest {

    @Test
    fun `recognizes request trade opcode 0x7D and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE)
        writeRequestTrade(frame)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes look in trade opcode 0x7E and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_LOOK_IN_TRADE)
        frame.writeByte(0x01)
        frame.writeByte(0)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes accept trade opcode 0x7F and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_ACCEPT_TRADE)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes close trade opcode 0x80 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_CLOSE_TRADE)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes container action opcode 0x75 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_CONTAINER_ACTION)
        frame.writeByte(1)
        frame.writeByte(0)
        frame.writeByte(2)
        frame.writeByte(3)
        frame.writeByte(4)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes close container opcode 0x87 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_CLOSE_CONTAINER)
        frame.writeByte(2)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes up arrow container opcode 0x88 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_UP_ARROW_CONTAINER)
        frame.writeByte(2)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes open parent container opcode 0x95 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_OPEN_PARENT_CONTAINER)
        writePosition(frame, Position(100, 100, 7))

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `routes bundled trade and container packets in one frame`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE)
        writeRequestTrade(frame)
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_LOOK_IN_TRADE)
        frame.writeByte(0x01)
        frame.writeByte(0)
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_ACCEPT_TRADE)
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_CLOSE_TRADE)
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_CLOSE_CONTAINER)
        frame.writeByte(2)

        assertThat(route(frame)).isEqualTo(5)
    }

    @Test
    fun `routes bundled follow outfit and use item ex packets in one frame`() {
        val frame = Unpooled.buffer()
        frame.writeByte(FollowPacketHandler.OPCODE_FOLLOW)
        frame.writeIntLE(7)
        frame.writeByte(OutfitPacketHandler.OPCODE_SET_OUTFIT)
        writeSetOutfit(frame)
        frame.writeByte(OutfitPacketHandler.OPCODE_TOGGLE_MOUNT)
        frame.writeByte(0x01)
        frame.writeByte(ItemPacketHandler.OPCODE_USE_ITEM_EX)
        writeUseItemEx(frame)

        assertThat(route(frame)).isEqualTo(4)
    }

    @Test
    fun `rejects a malformed request trade payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE)
        frame.writeShortLE(100) // truncated before the item and partner fields

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `recognizes follow opcode 0xA2 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(FollowPacketHandler.OPCODE_FOLLOW)
        frame.writeIntLE(7)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed follow payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(FollowPacketHandler.OPCODE_FOLLOW)
        frame.writeShortLE(7) // truncated uint32_t creature id

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `recognizes set outfit opcode 0xD3 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(OutfitPacketHandler.OPCODE_SET_OUTFIT)
        writeSetOutfit(frame)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes toggle mount opcode 0xD4 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(OutfitPacketHandler.OPCODE_TOGGLE_MOUNT)
        frame.writeByte(0x01)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed set outfit payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(OutfitPacketHandler.OPCODE_SET_OUTFIT)
        frame.writeShortLE(136) // missing the outfit type and color bytes

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `recognizes use item ex opcode 0x83 and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ItemPacketHandler.OPCODE_USE_ITEM_EX)
        writeUseItemEx(frame)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed use item ex payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ItemPacketHandler.OPCODE_USE_ITEM_EX)
        frame.writeShortLE(100) // truncated before the source item fields

        assertThat(route(frame)).isEqualTo(0)
    }

    private fun writeRequestTrade(frame: ByteBuf) {
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2160)
        frame.writeByte(0)
        frame.writeIntLE(2)
    }

    private fun writeSetOutfit(frame: ByteBuf) {
        frame.writeByte(0) // outfit type 0: change outfit
        frame.writeShortLE(136)
        frame.writeByte(1)
        frame.writeByte(2)
        frame.writeByte(3)
        frame.writeByte(4)
        frame.writeByte(0)
        frame.writeShortLE(0)
        frame.writeByte(0)
        frame.writeByte(0)
        frame.writeByte(0)
        frame.writeByte(0)
        frame.writeByte(0)
        frame.writeShortLE(0)
        frame.writeByte(0)
    }

    private fun writeUseItemEx(frame: ByteBuf) {
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(1945)
        frame.writeByte(2)
        writePosition(frame, Position(101, 100, 7))
        frame.writeShortLE(2160)
        frame.writeByte(3)
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
