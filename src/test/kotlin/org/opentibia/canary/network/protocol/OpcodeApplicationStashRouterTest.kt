package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the stash opcode `0x28`
 * (`ProtocolGame::parseStashWithdraw`, protocolgame.cpp:10874) over the ported
 * `Game::playerStowItem`/`playerStashWithdraw` state transitions
 * (game.cpp:5665, 5706) and the `Player::stashItems` supply stash
 * (`Player::addItemFromStash`, `stowItem`, `stashContainer`, player.cpp:9364,
 * 10311, 5238).
 */
class OpcodeApplicationStashRouterTest {

    private lateinit var dispatcher: GameDispatcher
    private lateinit var map: GameMap
    private lateinit var game: Game

    private fun start() {
        dispatcher = GameDispatcher()
        dispatcher.start()
        map = GameMap()
        game = Game(map, dispatcher)
    }

    private fun stop() {
        dispatcher.shutdown()
    }

    private fun context(itemTypes: Items? = null): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
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

    private fun storableType(itemId: Int, name: String = "item $itemId"): ItemType =
        ItemType(id = itemId, name = name, wareId = itemId, isStackable = true, weight = 1.0)

    private fun itemTypes(vararg types: ItemType): Items =
        Items().also { registry -> types.forEach { registry.register(it) } }

    private fun writeWithdraw(frame: ByteBuf, itemId: Int, count: Long) {
        frame.writeByte(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW)
        frame.writeByte(StashAction.WITHDRAW.value)
        frame.writeShortLE(itemId)
        frame.writeIntLE(count.toInt())
        frame.writeByte(0)
    }

    private fun writeStowItem(frame: ByteBuf, action: StashAction, position: Position, itemId: Int, count: Int) {
        frame.writeByte(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW)
        frame.writeByte(action.value)
        frame.writeShortLE(position.x)
        frame.writeShortLE(position.y)
        frame.writeByte(position.z)
        frame.writeShortLE(itemId)
        frame.writeByte(0)
        if (action == StashAction.STOW_ITEM) {
            frame.writeByte(count)
        }
    }

    @Test
    fun `stash withdraw opcode 0x28 moves the stash items to the player`() {
        start()
        try {
            val player = hero()
            player.stashMenu = true
            player.stash.addItemOnStash(2160, 10)
            val registry = itemTypes(storableType(2160))

            val frame = Unpooled.buffer()
            writeWithdraw(frame, itemId = 2160, count = 4)

            assertThat(route(frame, player, registry)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isEqualTo(6)
            assertThat(player.getItemCount(2160)).isEqualTo(4L)
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `stash withdraw opcode 0x28 leaves the stash untouched when it holds fewer items`() {
        start()
        try {
            val player = hero()
            player.stashMenu = true
            player.stash.addItemOnStash(2160, 2)
            val registry = itemTypes(storableType(2160))

            val frame = Unpooled.buffer()
            writeWithdraw(frame, itemId = 2160, count = 5)

            assertThat(route(frame, player, registry)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isEqualTo(2)
            assertThat(player.getItemCount(2160)).isZero()
        } finally {
            stop()
        }
    }

    @Test
    fun `stash withdraw opcode 0x28 is inert without the stash menu`() {
        start()
        try {
            val player = hero()
            player.stash.addItemOnStash(2160, 10)
            val registry = itemTypes(storableType(2160))

            val frame = Unpooled.buffer()
            writeWithdraw(frame, itemId = 2160, count = 1)

            assertThat(route(frame, player, registry)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isEqualTo(10)
            assertThat(player.getItemCount(2160)).isZero()
            assertThat(player.isUIExhausted(500)).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `stash withdraw opcode 0x28 is inert while the UI is exhausted`() {
        start()
        try {
            val player = hero()
            player.stashMenu = true
            player.stash.addItemOnStash(2160, 10)
            player.updateUIExhausted()
            val registry = itemTypes(storableType(2160))

            val frame = Unpooled.buffer()
            writeWithdraw(frame, itemId = 2160, count = 1)

            assertThat(route(frame, player, registry)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isEqualTo(10)
        } finally {
            stop()
        }
    }

    @Test
    fun `stash stow item opcode 0x28 moves a tile item into the stash`() {
        start()
        try {
            val player = hero()
            player.premium = true
            player.stashMenu = true
            val position = Position(1000, 1000, 7)
            val item = Item(id = 2160, count = 3, type = storableType(2160))
            val tile = map.getOrCreateTile(position)
            tile.internalAddThing(item)

            val frame = Unpooled.buffer()
            writeStowItem(frame, StashAction.STOW_ITEM, position, itemId = 2160, count = 3)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isEqualTo(3)
            assertThat(tile.items).isEmpty()
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `stash stow item opcode 0x28 requires a premium account`() {
        start()
        try {
            val player = hero()
            player.stashMenu = true
            val position = Position(1000, 1000, 7)
            val item = Item(id = 2160, count = 3, type = storableType(2160))
            val tile = map.getOrCreateTile(position)
            tile.internalAddThing(item)

            val frame = Unpooled.buffer()
            writeStowItem(frame, StashAction.STOW_ITEM, position, itemId = 2160, count = 3)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isZero()
            assertThat(tile.items).containsExactly(item)
        } finally {
            stop()
        }
    }

    @Test
    fun `stash stow item opcode 0x28 ignores a mismatched item id`() {
        start()
        try {
            val player = hero()
            player.premium = true
            player.stashMenu = true
            val position = Position(1000, 1000, 7)
            val item = Item(id = 2160, count = 1, type = storableType(2160))
            val tile = map.getOrCreateTile(position)
            tile.internalAddThing(item)

            val frame = Unpooled.buffer()
            writeStowItem(frame, StashAction.STOW_ITEM, position, itemId = 2161, count = 1)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isZero()
            assertThat(tile.items).containsExactly(item)
        } finally {
            stop()
        }
    }

    @Test
    fun `stash stow container opcode 0x28 moves the container contents into the stash`() {
        start()
        try {
            val player = hero()
            player.premium = true
            player.stashMenu = true
            val position = Position(1000, 1000, 7)
            val containerType = ItemType(id = 2000, name = "bag", isContainer = true)
            val container = Container(id = 2000, capacity = 20, type = containerType)
            container.addItem(Item(id = 2160, count = 2, type = storableType(2160)))
            container.addItem(Item(id = 2161, count = 1, type = storableType(2161)))
            val tile = map.getOrCreateTile(position)
            tile.internalAddThing(container)

            val frame = Unpooled.buffer()
            writeStowItem(frame, StashAction.STOW_CONTAINER, position, itemId = 2000, count = 0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isEqualTo(2)
            assertThat(player.stash.getStashItemCount(2161)).isEqualTo(1)
            assertThat(container.items).isEmpty()
            assertThat(tile.items).containsExactly(container)
        } finally {
            stop()
        }
    }

    @Test
    fun `stash stow stack opcode 0x28 moves the backpack stack of the item id`() {
        start()
        try {
            val player = hero()
            player.premium = true
            player.stashMenu = true
            val backpackType = ItemType(id = 2001, name = "backpack", isContainer = true)
            val backpack = Container(id = 2001, capacity = 20, type = backpackType)
            val stack = Item(id = 2160, count = 5, type = storableType(2160))
            backpack.addItem(stack)
            assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(
                org.opentibia.canary.systems.rewardchest.ReturnValue.NOERROR,
            )

            val position = Position(1000, 1000, 7)
            val loose = Item(id = 2160, count = 2, type = storableType(2160))
            val tile = map.getOrCreateTile(position)
            tile.internalAddThing(loose)

            val frame = Unpooled.buffer()
            writeStowItem(frame, StashAction.STOW_STACK, position, itemId = 2160, count = 0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.stash.getStashItemCount(2160)).isEqualTo(5)
            assertThat(backpack.items).isEmpty()
            assertThat(tile.items).containsExactly(loose)
        } finally {
            stop()
        }
    }
}
