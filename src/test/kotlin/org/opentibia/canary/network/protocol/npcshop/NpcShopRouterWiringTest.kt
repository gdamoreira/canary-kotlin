package org.opentibia.canary.network.protocol.npcshop

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.GamePacketContext
import org.opentibia.canary.network.protocol.GamePacketRouter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for the NPC shop opcodes `0x7A`-`0x7C`
 * (`ProtocolGame::parsePlayerBuyOnShop` / `parsePlayerSellOnShop` /
 * `Game::playerCloseShop`). The decoded command is dispatched on the
 * WorldCommit lane (AGENTS.md 1.2); the trade application itself stays Lua.
 */
class NpcShopRouterWiringTest {

    @Test
    fun `recognizes the buy on shop opcode 0x7A and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(NpcShopPacketHandler.OPCODE_PLAYER_BUY_ON_SHOP)
        writeBuyPayload(frame)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes the sell on shop opcode 0x7B and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(NpcShopPacketHandler.OPCODE_PLAYER_SELL_ON_SHOP)
        writeSellPayload(frame)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `recognizes the close shop opcode 0x7C and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(NpcShopPacketHandler.OPCODE_PLAYER_CLOSE_SHOP)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `routes bundled buy sell and close shop packets in one frame`() {
        val frame = Unpooled.buffer()
        frame.writeByte(NpcShopPacketHandler.OPCODE_PLAYER_BUY_ON_SHOP)
        writeBuyPayload(frame)
        frame.writeByte(NpcShopPacketHandler.OPCODE_PLAYER_SELL_ON_SHOP)
        writeSellPayload(frame)
        frame.writeByte(NpcShopPacketHandler.OPCODE_PLAYER_CLOSE_SHOP)

        assertThat(route(frame)).isEqualTo(3)
    }

    @Test
    fun `rejects a malformed buy on shop payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(NpcShopPacketHandler.OPCODE_PLAYER_BUY_ON_SHOP)
        frame.writeShortLE(2160) // truncated before the amount and flags

        assertThat(route(frame)).isEqualTo(0)
    }

    private fun writeBuyPayload(frame: ByteBuf) {
        frame.writeShortLE(2160)
        frame.writeByte(1)
        frame.writeShortLE(1)
        frame.writeByte(0x00)
        frame.writeByte(0x00)
    }

    private fun writeSellPayload(frame: ByteBuf) {
        frame.writeShortLE(2160)
        frame.writeByte(1)
        frame.writeShortLE(1)
        frame.writeByte(0x00)
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
