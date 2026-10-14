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
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.containers.Inbox
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the depot-search state opcodes: `0x93`
 * (`Game::playerRequestCloseDepotSearch`) resets `Player::depotSearchOnItem`
 * through the engine, `0x92` (`Game::playerRequestDepotItems`) opens the
 * search, `0x94` (`Game::playerRequestDepotSearchItem`) records the requested
 * item, `0x29` (`Game::playerRequestDepotSearchRetrieve`) moves the searched
 * depot/inbox items to the player and `0x95`
 * (`Game::playerRequestOpenContainerFromDepotSearch`) dispatches the searched
 * container through the Lua action layer.
 */
class OpcodeApplicationDepotSearchRouterTest {

    private lateinit var dispatcher: GameDispatcher
    private lateinit var map: GameMap
    private lateinit var game: Game
    private lateinit var luaEnvironment: LuaEnvironment

    private fun start() {
        dispatcher = GameDispatcher()
        dispatcher.start()
        map = GameMap()
        game = Game(map, dispatcher)
        luaEnvironment = LuaEnvironment().apply { initState() }
    }

    private fun stop() {
        dispatcher.shutdown()
    }

    private fun context(itemTypes: Items? = null): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = luaEnvironment,
        itemTypes = itemTypes,
        game = game,
    )

    private fun hero(): Player =
        Player(1, "Hero", 150, 150, position = Position(1000, 1000, 7)).also { game.addPlayer(it) }

    private fun route(frame: ByteBuf, player: Player, itemTypes: Items? = null): Int {
        val handled = GamePacketRouter.route(player, frame, context(itemTypes))
        awaitWorldCommit()
        return handled
    }

    private fun awaitWorldCommit() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `close depot search opcode 0x93 resets the open item state`() {
        start()
        try {
            val player = hero()
            player.setDepotSearchIsOpen(3031, 0)

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_CLOSE_DEPOT_SEARCH)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isDepotSearchOpen()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `close depot search opcode 0x93 is inert when no search is open`() {
        start()
        try {
            val player = hero()

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_CLOSE_DEPOT_SEARCH)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isDepotSearchOpen()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `open depot search opcode 0x92 sets the open state when the menu is available`() {
        start()
        try {
            val player = hero()
            player.depotSearch = true
            player.setLastDepotId(1)

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_OPEN_DEPOT_SEARCH)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isDepotSearchOpen()).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `open depot search opcode 0x92 is inert when the menu is unavailable`() {
        start()
        try {
            val player = hero()
            player.depotSearch = false
            player.setLastDepotId(1)

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_OPEN_DEPOT_SEARCH)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isDepotSearchOpen()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `depot search item request opcode 0x94 records the requested item when open`() {
        start()
        try {
            val player = hero()
            player.depotSearch = true
            player.setLastDepotId(1)
            player.setDepotSearchIsOpen(1, 0)

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST)
            frame.writeShortLE(3031)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isDepotSearchOpenOnItem(3031)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `depot search item request opcode 0x94 is inert when no search is open`() {
        start()
        try {
            val player = hero()
            player.depotSearch = true
            player.setLastDepotId(1)

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST)
            frame.writeShortLE(3031)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isDepotSearchOpenOnItem(3031)).isFalse()
        } finally {
            stop()
        }
    }

    private fun storableType(itemId: Int): ItemType =
        ItemType(id = itemId, name = "item $itemId", wareId = itemId, isStackable = true, weight = 1.0)

    @Test
    fun `retrieve depot search opcode 0x29 moves the matching inbox items to the player`() {
        start()
        try {
            val player = hero()
            player.depotSearch = true
            player.setLastDepotId(1)
            player.setDepotSearchIsOpen(2160, 0)
            val inbox = player.inbox
            inbox.addItem(Item(id = 2160, count = 3, type = storableType(2160)))
            val registry = Items().also { it.register(storableType(2160)) }

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_RETRIEVE_DEPOT_SEARCH)
            frame.writeShortLE(2160)
            frame.writeByte(0)

            assertThat(route(frame, player, registry)).isEqualTo(1)
            assertThat(inbox.items).isEmpty()
            assertThat(player.getItemCount(2160)).isEqualTo(3L)
            assertThat(player.isDepotSearchOpenOnItem(2160)).isTrue()
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `retrieve depot search opcode 0x29 is inert when the search is not open on the item`() {
        start()
        try {
            val player = hero()
            player.depotSearch = true
            player.setLastDepotId(1)
            player.setDepotSearchIsOpen(2161, 0)
            val inbox = player.inbox
            inbox.addItem(Item(id = 2160, count = 3, type = storableType(2160)))
            val registry = Items().also { it.register(storableType(2160)) }

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_RETRIEVE_DEPOT_SEARCH)
            frame.writeShortLE(2160)
            frame.writeByte(0)

            assertThat(route(frame, player, registry)).isEqualTo(1)
            assertThat(inbox.items).hasSize(1)
            assertThat(player.getItemCount(2160)).isZero()
        } finally {
            stop()
        }
    }

    @Test
    fun `open parent container opcode 0x95 dispatches the searched container action`() {
        start()
        try {
            val player = hero()
            player.depotSearch = true
            player.setLastDepotId(1)
            player.setDepotSearchIsOpen(2160, 0)
            player.inbox.addItem(Item(id = 2160, count = 1, type = storableType(2160)))
            luaEnvironment.loadChunk(
                """
                local a = Action()
                a:id(${Inbox.ITEM_INBOX})
                function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                    depot_container_used = true
                    return true
                end
                a:register()
                """.trimIndent(),
            )

            val frame = Unpooled.buffer()
            frame.writeByte(TradeAndContainerPacketHandler.OPCODE_OPEN_PARENT_CONTAINER)
            frame.writeShortLE(0)
            frame.writeShortLE(0x21)
            frame.writeByte(0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(luaEnvironment.getGlobal("depot_container_used").toboolean()).isTrue()
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `open parent container opcode 0x95 is inert when no search is open`() {
        start()
        try {
            val player = hero()
            player.depotSearch = true
            player.setLastDepotId(1)
            player.inbox.addItem(Item(id = 2160, count = 1, type = storableType(2160)))
            luaEnvironment.loadChunk(
                """
                local a = Action()
                a:id(${Inbox.ITEM_INBOX})
                function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                    depot_container_used = true
                    return true
                end
                a:register()
                """.trimIndent(),
            )

            val frame = Unpooled.buffer()
            frame.writeByte(TradeAndContainerPacketHandler.OPCODE_OPEN_PARENT_CONTAINER)
            frame.writeShortLE(0)
            frame.writeShortLE(0x21)
            frame.writeByte(0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(luaEnvironment.getGlobal("depot_container_used").toboolean()).isFalse()
        } finally {
            stop()
        }
    }
}
