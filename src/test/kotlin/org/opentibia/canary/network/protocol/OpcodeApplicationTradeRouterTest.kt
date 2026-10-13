package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the trade opcodes (`0x7D`-`0x80`): the decoded
 * command must reach `Game::playerRequestTrade` / `playerLookInTrade` /
 * `playerAcceptTrade` / `playerCloseTrade` (src/game/game.cpp:5797-6187) on the
 * WorldCommit lane instead of stopping at the confinement boundary.
 */
class OpcodeApplicationTradeRouterTest {

    private val groundId = 100
    private val goldCoinId = 3031
    private val platinumCoinId = 3035

    private val center = Position(1000, 1000, 7)
    private val nextToCenter = Position(1001, 1000, 7)

    private data class ItemRequest(val player: Player, val traderName: String, val item: Item, val ack: Boolean)

    private class Harness {
        val itemRequests = mutableListOf<ItemRequest>()
        val tradeCloses = mutableListOf<Player>()
        val lookInTrade = mutableListOf<Triple<Player, Player, Item>>()
    }

    private lateinit var dispatcher: GameDispatcher
    private lateinit var map: GameMap
    private lateinit var game: Game
    private lateinit var harness: Harness

    private fun start() {
        dispatcher = GameDispatcher()
        dispatcher.start()
        map = GameMap()
        harness = Harness()
        game = Game(map, dispatcher).also {
            it.sendTradeItemRequest = { player, name, item, ack ->
                harness.itemRequests.add(ItemRequest(player, name, item, ack))
            }
            it.sendTradeClose = { player -> harness.tradeCloses.add(player) }
            it.eventPlayerOnLookInTrade = { player, partner, item, _ ->
                harness.lookInTrade.add(Triple(player, partner, item))
            }
        }
    }

    private fun stop() {
        dispatcher.shutdown()
    }

    private fun context(): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
    )

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also {
            game.addPlayer(it)
        }

    private fun groundTile(position: Position): Tile =
        map.getOrCreateTile(position).also { if (it.ground == null) it.ground = Item(id = groundId) }

    private fun gold(count: Int = 1): Item =
        Item(
            id = goldCoinId,
            count = count,
            type = ItemType(id = goldCoinId, name = "gold coin", isStackable = true, weight = 0.1, isPickupable = true),
        )

    private fun platinum(count: Int = 1): Item =
        Item(
            id = platinumCoinId,
            count = count,
            type = ItemType(
                id = platinumCoinId,
                name = "platinum coin",
                isStackable = true,
                weight = 0.1,
                isPickupable = true,
            ),
        )

    private fun itemOnTile(item: Item, position: Position = center) {
        val tile = groundTile(position)
        check(game.internalAddItem(tile, item) == ReturnValue.NOERROR) { "item placement failed" }
    }

    private fun messagesOf(player: Player): MutableList<Pair<MessageClass, String>> {
        val messages = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text -> messages.add(type to text) }
        return messages
    }

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

    private fun writePosition(frame: ByteBuf, position: Position) {
        frame.writeShortLE(position.x)
        frame.writeShortLE(position.y)
        frame.writeByte(position.z)
    }

    private fun requestTradeFrame(item: Item, partnerId: Long, position: Position = center): ByteBuf {
        val frame = Unpooled.buffer()
        frame.writeByte(TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE)
        writePosition(frame, position)
        frame.writeShortLE(item.id)
        frame.writeByte(0)
        frame.writeIntLE(partnerId.toInt())
        return frame
    }

    @Test
    fun `request trade opcode 0x7D starts the trade engine session`() {
        start()
        try {
            val first = player(1, "Knight")
            val second = player(2, "Druid", nextToCenter)
            val item = gold(3)
            itemOnTile(item)
            val secondMessages = messagesOf(second)

            assertThat(route(requestTradeFrame(item, 2), first)).isEqualTo(1)

            assertThat(harness.itemRequests).containsExactly(ItemRequest(first, "Knight", item, true))
            assertThat(secondMessages).containsExactly(MessageClass.MESSAGE_TRANSACTION to "Knight wants to trade with you.")
        } finally {
            stop()
        }
    }

    @Test
    fun `accept trade opcode 0x7F swaps both offers through the engine`() {
        start()
        try {
            val first = player(1, "Knight")
            val second = player(2, "Druid", nextToCenter)
            val firstItem = gold(3)
            val secondItem = platinum(2)
            itemOnTile(firstItem)
            itemOnTile(secondItem, nextToCenter)

            assertThat(route(requestTradeFrame(firstItem, 2), first)).isEqualTo(1)
            assertThat(route(requestTradeFrame(secondItem, 1, nextToCenter), second)).isEqualTo(1)

            val accept = Unpooled.buffer()
            accept.writeByte(TradeAndContainerPacketHandler.OPCODE_ACCEPT_TRADE)
            assertThat(route(accept, first)).isEqualTo(1)

            val partnerAccept = Unpooled.buffer()
            partnerAccept.writeByte(TradeAndContainerPacketHandler.OPCODE_ACCEPT_TRADE)
            assertThat(route(partnerAccept, second)).isEqualTo(1)

            assertThat(second.getItemCount(goldCoinId)).isEqualTo(3)
            assertThat(first.getItemCount(platinumCoinId)).isEqualTo(2)
            assertThat(harness.tradeCloses).containsExactlyInAnyOrder(first, second)
        } finally {
            stop()
        }
    }

    @Test
    fun `close trade opcode 0x80 cancels the session through the engine`() {
        start()
        try {
            val first = player(1, "Knight")
            val second = player(2, "Druid", nextToCenter)
            val item = gold(3)
            itemOnTile(item)
            route(requestTradeFrame(item, 2), first)
            val firstMessages = messagesOf(first)

            val close = Unpooled.buffer()
            close.writeByte(TradeAndContainerPacketHandler.OPCODE_CLOSE_TRADE)

            assertThat(route(close, first)).isEqualTo(1)

            assertThat(harness.tradeCloses).contains(first)
            assertThat(firstMessages).contains(MessageClass.MESSAGE_FAILURE to "Trade cancelled.")
        } finally {
            stop()
        }
    }

    @Test
    fun `look in trade opcode 0x7E reports the offered item through the engine`() {
        start()
        try {
            val first = player(1, "Knight")
            val second = player(2, "Druid", nextToCenter)
            val item = gold(3)
            itemOnTile(item)
            route(requestTradeFrame(item, 2), first)

            val look = Unpooled.buffer()
            look.writeByte(TradeAndContainerPacketHandler.OPCODE_LOOK_IN_TRADE)
            look.writeByte(0x01) // counter offer
            look.writeByte(0)

            assertThat(route(look, second)).isEqualTo(1)

            assertThat(harness.lookInTrade).hasSize(1)
            assertThat(harness.lookInTrade.single().third).isSameAs(item)
        } finally {
            stop()
        }
    }
}
