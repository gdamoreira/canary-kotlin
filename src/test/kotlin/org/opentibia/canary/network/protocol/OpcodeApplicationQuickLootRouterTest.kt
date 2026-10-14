package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.QuickLootFilter_t
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the quick-loot state opcodes: `0x90` action `3`
 * (`Game::playerSetQuickLootFallback`, game.cpp:6898), `0x90` actions
 * `0`/`1`/`2` (`Game::playerSetManagedContainer`/`playerClearManagedContainer`/
 * `playerOpenManagedContainer`, game.cpp:6822-6894), `0x91`
 * (`Game::playerQuickLootBlackWhitelist`, game.cpp:6907) and the corpse/nearby
 * loot paths of `0x8F` (`Game::playerQuickLoot`/`playerLootNearby`,
 * game.cpp:6428/6766) through the managed-container loot engine
 * (`Game::internalCollectManagedItems`/`processLootItems`, game.cpp:3850/3820).
 */
class OpcodeApplicationQuickLootRouterTest {

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

    private fun context(): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
    )

    private fun hero(): Player =
        Player(1, "Hero", 150, 150, position = Position(1000, 1000, 7)).also { game.addPlayer(it) }

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
    fun `loot container action 3 toggles the quick loot fallback`() {
        start()
        try {
            val player = hero()

            val enable = Unpooled.buffer()
            enable.writeByte(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER)
            enable.writeByte(3)
            enable.writeByte(0x01)
            assertThat(route(enable, player)).isEqualTo(1)
            assertThat(player.quickLootFallbackToMainContainer).isTrue()

            val disable = Unpooled.buffer()
            disable.writeByte(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER)
            disable.writeByte(3)
            disable.writeByte(0x00)
            assertThat(route(disable, player)).isEqualTo(1)
            assertThat(player.quickLootFallbackToMainContainer).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `black whitelist opcode 0x91 stores the filter and item ids`() {
        start()
        try {
            val player = hero()

            val frame = Unpooled.buffer()
            frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST)
            frame.writeByte(QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT.value)
            frame.writeShortLE(2)
            frame.writeShortLE(3031)
            frame.writeShortLE(3035)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.quickLootFilter).isEqualTo(QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT)
            assertThat(player.quickLootListItemIds).containsExactly(3031, 3035)
        } finally {
            stop()
        }
    }

    @Test
    fun `black whitelist opcode 0x91 replaces a previous list`() {
        start()
        try {
            val player = hero()
            player.quickLootListItemIds = listOf(1, 2, 3)

            val frame = Unpooled.buffer()
            frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST)
            frame.writeByte(QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT.value)
            frame.writeShortLE(1)
            frame.writeShortLE(2148)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.quickLootListItemIds).containsExactly(2148)
        } finally {
            stop()
        }
    }

    private fun backpack(id: Int, capacity: Int = 20): Container =
        Container(id = id, capacity = capacity, type = ItemType(id = id, name = "backpack", isContainer = true))

    private fun writeManagedContainer(frame: ByteBuf, action: Int, category: Int, position: Position, itemId: Int) {
        frame.writeByte(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER)
        frame.writeByte(action)
        frame.writeByte(category)
        if (action == 0 || action == 4) {
            frame.writeShortLE(position.x)
            frame.writeShortLE(position.y)
            frame.writeByte(position.z)
            frame.writeShortLE(itemId)
            frame.writeByte(0)
        }
    }

    @Test
    fun `set managed container action 0 registers the loot container`() {
        start()
        try {
            val player = hero()
            val openContainer = backpack(2000)
            val managed = backpack(2001, capacity = 10)
            openContainer.addItem(managed)
            player.addContainer(2, openContainer)

            val frame = Unpooled.buffer()
            writeManagedContainer(
                frame,
                action = 0,
                category = ObjectCategory_t.OBJECTCATEGORY_DEFAULT.value,
                position = Position(0xFFFF, 0x42, 0),
                itemId = 2001,
            )

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)).isSameAs(managed)
            assertThat(managed.getAttribute(ItemAttribute.QUICKLOOTCONTAINER))
                .isEqualTo(1L shl ObjectCategory_t.OBJECTCATEGORY_DEFAULT.value)
        } finally {
            stop()
        }
    }

    @Test
    fun `clear managed container action 1 drops the loot container`() {
        start()
        try {
            val player = hero()
            val managed = backpack(2001, capacity = 10)
            player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, managed, true)

            val frame = Unpooled.buffer()
            writeManagedContainer(
                frame,
                action = 1,
                category = ObjectCategory_t.OBJECTCATEGORY_DEFAULT.value,
                position = Position(0, 0, 0),
                itemId = 0,
            )

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)).isNull()
            assertThat(managed.hasAttribute(ItemAttribute.QUICKLOOTCONTAINER)).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `open managed container action 2 sends the registered container`() {
        start()
        try {
            val player = hero()
            val managed = backpack(2001, capacity = 10)
            player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, managed, true)
            val sent = mutableListOf<Triple<Int, Container, Boolean>>()
            player.sendContainer = { cid, container, hasParent, _ ->
                sent.add(Triple(cid, container, hasParent))
            }

            val frame = Unpooled.buffer()
            writeManagedContainer(
                frame,
                action = 2,
                category = ObjectCategory_t.OBJECTCATEGORY_DEFAULT.value,
                position = Position(0, 0, 0),
                itemId = 0,
            )

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(sent).containsExactly(Triple(managed.id and 0xFF, managed, false))
        } finally {
            stop()
        }
    }

    private fun corpse(id: Int, contents: List<Item>): Container =
        Container(
            id = id,
            capacity = 10,
            type = ItemType(id = id, name = "corpse", isContainer = true, isCorpse = true),
        ).also { container -> contents.forEach { container.addItem(it) } }

    private fun writeQuickLootCorpse(frame: ByteBuf, variant: Int, position: Position, itemId: Int) {
        frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT)
        frame.writeByte(variant)
        frame.writeShortLE(position.x)
        frame.writeShortLE(position.y)
        frame.writeByte(position.z)
        frame.writeShortLE(itemId)
        frame.writeByte(0)
    }

    private fun writeQuickLootNearby(frame: ByteBuf) {
        frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT)
        frame.writeByte(2)
    }

    @Test
    fun `quick loot corpse opcode 0x8F moves the corpse items into the managed loot container`() {
        start()
        try {
            val player = hero()
            val managed = backpack(2001, capacity = 10)
            player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, managed, true)
            val position = Position(1000, 1000, 7)
            val loot = Item(id = 2160, count = 1, type = ItemType(id = 2160, name = "loot"))
            val corpse = corpse(3058, listOf(loot))
            map.getOrCreateTile(position).internalAddThing(corpse)

            val frame = Unpooled.buffer()
            writeQuickLootCorpse(frame, variant = 0, position = position, itemId = 3058)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(corpse.items).isEmpty()
            assertThat(managed.items).containsExactly(loot)
        } finally {
            stop()
        }
    }

    @Test
    fun `quick loot corpse opcode 0x8F leaves the corpse untouched without a managed container`() {
        start()
        try {
            val player = hero()
            val position = Position(1000, 1000, 7)
            val loot = Item(id = 2160, count = 1, type = ItemType(id = 2160, name = "loot"))
            val corpse = corpse(3058, listOf(loot))
            map.getOrCreateTile(position).internalAddThing(corpse)

            val frame = Unpooled.buffer()
            writeQuickLootCorpse(frame, variant = 0, position = position, itemId = 3058)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(corpse.items).containsExactly(loot)
        } finally {
            stop()
        }
    }

    @Test
    fun `quick loot corpse opcode 0x8F sends gold to the gold managed container`() {
        start()
        try {
            val player = hero()
            player.premium = true
            val goldContainer = backpack(2002, capacity = 10)
            player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_GOLD, goldContainer, true)
            val position = Position(1000, 1000, 7)
            val coins = Item(
                id = org.opentibia.canary.items.ITEM_GOLD_COIN,
                count = 75,
                type = ItemType(id = org.opentibia.canary.items.ITEM_GOLD_COIN, name = "gold coin", isStackable = true),
            )
            val corpse = corpse(3058, listOf(coins))
            map.getOrCreateTile(position).internalAddThing(corpse)

            val frame = Unpooled.buffer()
            writeQuickLootCorpse(frame, variant = 0, position = position, itemId = 3058)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(corpse.items).isEmpty()
            assertThat(goldContainer.items).containsExactly(coins)
        } finally {
            stop()
        }
    }

    @Test
    fun `loot nearby opcode 0x8F loots the adjacent corpses`() {
        start()
        try {
            val player = hero()
            val managed = backpack(2001, capacity = 10)
            player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, managed, true)
            val westPosition = Position(999, 1000, 7)
            val eastPosition = Position(1001, 1000, 7)
            val westLoot = Item(id = 2160, count = 1, type = ItemType(id = 2160, name = "loot"))
            val eastLoot = Item(id = 2161, count = 1, type = ItemType(id = 2161, name = "loot"))
            val westCorpse = corpse(3058, listOf(westLoot))
            val eastCorpse = corpse(3058, listOf(eastLoot))
            map.getOrCreateTile(westPosition).internalAddThing(westCorpse)
            map.getOrCreateTile(eastPosition).internalAddThing(eastCorpse)

            val frame = Unpooled.buffer()
            writeQuickLootNearby(frame)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(westCorpse.items).isEmpty()
            assertThat(eastCorpse.items).isEmpty()
            assertThat(managed.items).containsExactlyInAnyOrder(westLoot, eastLoot)
        } finally {
            stop()
        }
    }
}
