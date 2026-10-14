package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.TradeState_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.house.House
import org.opentibia.canary.network.protocol.npcshop.ShopBlock

/**
 * Transcribes the Player trade/shop/write/edit-house interaction state
 * (src/creatures/players/player.cpp:1329, 2963-2981, 3092-3120, 3160-3166,
 * 6037-6076, 6105-6125, 12465-12566, 12568-12576):
 *
 * - `Player::checkTradeState` (player.cpp:2963-2981)
 * - `Player::getWriteItem` / `Player::setWriteItem` (player.cpp:3092-3108)
 * - `Player::getEditHouse` / `Player::setEditHouse` (player.cpp:3110-3120)
 * - `Player::setScheduledSaleUpdate` / `getScheduledSaleUpdate` (player.cpp:3160-3166)
 * - `Player::updateSaleShopList` (both overloads, player.cpp:6037-6054)
 * - `Player::hasShopItemForSale` (player.cpp:6066-6076)
 * - `Player::setTradeState` / `getTradeState` / `getTradeItem` (player.cpp:6105-6117)
 * - `Player::setShopOwner` / `getShopOwner` (player.cpp:6119-6125)
 * - `Player::setItemCustomPrice` (player.cpp:1826-1828)
 * - `Player::getItemCustomPrice` (player.cpp:12568-12576)
 * - `Player::onAddContainerItem` (player.cpp:12465-12467)
 * - `Player::onUpdateContainerItem` (player.cpp:12469-12477)
 * - `Player::onRemoveContainerItem` (player.cpp:12479-12489)
 * - `Player::onUpdateInventoryItem` (player.cpp:12543-12551)
 * - `Player::onRemoveInventoryItem` (player.cpp:12553-12566)
 *
 * `Game::internalCloseTrade`, the dispatcher's deferred `Game::updatePlayerSaleItems`,
 * `Npc::getShopItemVector`, the item-type `isFluidContainer` probe and
 * `Game::getItemMarketPrice` are constructor seams per AGENTS.md 1.1. The C++
 * out-parameters of `getWriteItem`/`getEditHouse` are modelled as property
 * reads on the state.
 */
class PlayerInteractionStateTest {

    private val closedTrades = mutableListOf<Player>()
    private val scheduledUpdates = mutableListOf<Unit>()
    private val lootChecks = mutableListOf<Container?>()
    private var marketCalls = mutableListOf<Pair<Map<Int, Long>, Boolean>>()

    private fun player(): Player =
        Player(id = 21, name = "Trader", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun newState(
        player: Player = player(),
        shopItems: (Npc, Long) -> List<ShopBlock> = { _, _ -> emptyList() },
        fluidContainers: Set<Int> = emptySet(),
        marketPrice: Long = 0L,
    ) = PlayerInteractionState(
        player = player,
        internalCloseTrade = { closedTrades.add(it) },
        scheduleSaleUpdate = { scheduledUpdates.add(Unit) },
        shopItemVector = shopItems,
        isFluidContainer = { itemId -> itemId in fluidContainers },
        getItemMarketPrice = { itemMap, buyPrice ->
            marketCalls.add(itemMap to buyPrice)
            marketPrice
        },
        checkLootContainers = { lootChecks.add(it) },
    )

    private fun container(id: Int, capacity: Int = 10): Container = Container(id, capacity)

    // --- trade state (player.cpp:6105-6117) --------------------------------------

    @Test
    fun `trade state defaults to TRADE_NONE and stores every transition`() {
        val state = newState()

        assertThat(state.getTradeState()).isEqualTo(TradeState_t.TRADE_NONE)
        assertThat(state.getTradeItem()).isNull()

        state.setTradeState(TradeState_t.TRADE_INITIATED)
        assertThat(state.getTradeState()).isEqualTo(TradeState_t.TRADE_INITIATED)
    }

    @Test
    fun `checkTradeState does nothing without a trade item or while transferring`() {
        val state = newState()
        state.checkTradeState(Item(100))
        assertThat(closedTrades).isEmpty()

        state.tradeItem = Item(100)
        state.setTradeState(TradeState_t.TRADE_TRANSFER)
        state.checkTradeState(Item(100))
        assertThat(closedTrades).isEmpty()
    }

    @Test
    fun `checkTradeState closes the trade for the traded item itself`() {
        val state = newState()
        val item = Item(100)
        state.tradeItem = item

        state.checkTradeState(item)

        assertThat(closedTrades).containsExactly(state.player)
    }

    @Test
    fun `checkTradeState closes when the item is inside the traded container`() {
        val state = newState()
        val tradedContainer = container(1)
        val nested = container(2)
        val deep = Item(100)
        nested.addItem(deep)
        deep.setParent(nested)
        tradedContainer.addItem(nested)
        nested.setParent(tradedContainer)
        state.tradeItem = tradedContainer

        state.checkTradeState(deep)

        assertThat(closedTrades).containsExactly(state.player)
    }

    @Test
    fun `checkTradeState keeps the trade for unrelated items`() {
        val state = newState()
        state.tradeItem = container(1)

        state.checkTradeState(Item(100))

        assertThat(closedTrades).isEmpty()
    }

    // --- shop owner (player.cpp:6119-6125) ---------------------------------------

    @Test
    fun `shop owner round-trips`() {
        val state = newState()
        assertThat(state.getShopOwner()).isNull()

        val npc = Npc(id = 5, name = "Merchant", position = Position(101, 100, 7))
        state.setShopOwner(npc)
        assertThat(state.getShopOwner()).isSameAs(npc)

        state.setShopOwner(null)
        assertThat(state.getShopOwner()).isNull()
    }

    // --- write item (player.cpp:3092-3108) ---------------------------------------

    @Test
    fun `setWriteItem bumps the window text id and clears on null`() {
        val state = newState()
        val item = Item(100)

        state.setWriteItem(item, 255)

        assertThat(state.windowTextId).isEqualTo(1)
        assertThat(state.getWriteItem()).isSameAs(item)
        assertThat(state.maxWriteLen).isEqualTo(255)

        state.setWriteItem(null, 0)

        assertThat(state.windowTextId).isEqualTo(2)
        assertThat(state.getWriteItem()).isNull()
        assertThat(state.maxWriteLen).isZero()
    }

    // --- edit house (player.cpp:3110-3120) ---------------------------------------

    @Test
    fun `setEditHouse bumps the window text id and stores the list id`() {
        val state = newState()
        val house = House(id = 3)

        state.setEditHouse(house, 7)

        assertThat(state.windowTextId).isEqualTo(1)
        assertThat(state.getEditHouse()).isSameAs(house)
        assertThat(state.editListId).isEqualTo(7)

        state.setEditHouse(null, 0)
        assertThat(state.getEditHouse()).isNull()
    }

    // --- custom prices (player.cpp:1826-1828, 12568-12576) -----------------------

    @Test
    fun `getItemCustomPrice prefers the stored price`() {
        val state = newState(marketPrice = 999)

        state.setItemCustomPrice(2160, 42)
        assertThat(state.getItemCustomPrice(2160, buyPrice = true)).isEqualTo(42)
        assertThat(marketCalls).isEmpty()
    }

    @Test
    fun `getItemCustomPrice falls back to the market price`() {
        val state = newState(marketPrice = 77)

        assertThat(state.getItemCustomPrice(2160, buyPrice = false)).isEqualTo(77)
        assertThat(marketCalls).containsExactly(mapOf(2160 to 1L) to false)
    }

    // --- sale list (player.cpp:6037-6076, 3160-3166) -----------------------------

    @Test
    fun `updateSaleShopList with an item schedules one player sale update`() {
        val state = newState()

        assertThat(state.updateSaleShopList(Item(2160))).isTrue()
        assertThat(scheduledUpdates).hasSize(1)
        assertThat(state.getScheduledSaleUpdate()).isTrue()
    }

    @Test
    fun `updateSaleShopList skips null and id zero items`() {
        val state = newState()

        assertThat(state.updateSaleShopList(null)).isTrue()
        assertThat(state.updateSaleShopList(Item(0))).isTrue()
        assertThat(scheduledUpdates).isEmpty()
        assertThat(state.getScheduledSaleUpdate()).isFalse()
    }

    @Test
    fun `updateSaleShopList without an item schedules the update`() {
        val state = newState()

        state.updateSaleShopList()

        assertThat(scheduledUpdates).hasSize(1)
        assertThat(state.getScheduledSaleUpdate()).isTrue()

        state.setScheduledSaleUpdate(false)
        assertThat(state.getScheduledSaleUpdate()).isFalse()
    }

    @Test
    fun `hasShopItemForSale requires an owner and a positive buy price`() {
        val npc = Npc(id = 5, name = "Merchant", position = Position(101, 100, 7))
        val noOwner = newState()
        assertThat(noOwner.hasShopItemForSale(2160, 0)).isFalse()

        val npcBlocks = listOf(
            ShopBlock(itemId = 2160, itemBuyPrice = 0, itemSellPrice = 5),
            ShopBlock(itemId = 2160, itemBuyPrice = 10, itemSubType = 0),
            ShopBlock(itemId = 2161, itemBuyPrice = 10, itemSubType = 0),
        )
        val state = newState(shopItems = { _, _ -> npcBlocks })
        state.setShopOwner(npc)

        assertThat(state.hasShopItemForSale(2160, 0)).isTrue()
        assertThat(state.hasShopItemForSale(2161, 0)).isTrue()
        assertThat(state.hasShopItemForSale(9999, 0)).isFalse()
    }

    @Test
    fun `hasShopItemForSale compares the subtype for fluid containers`() {
        val npc = Npc(id = 5, name = "Merchant", position = Position(101, 100, 7))
        val blocks = listOf(
            ShopBlock(itemId = 2006, itemBuyPrice = 10, itemSubType = 7),
        )
        val state = newState(shopItems = { _, _ -> blocks }, fluidContainers = setOf(2006))
        state.setShopOwner(npc)

        assertThat(state.hasShopItemForSale(2006, 7)).isTrue()
        assertThat(state.hasShopItemForSale(2006, 2)).isFalse()
    }

    // --- container/inventory trade hooks (player.cpp:12465-12566) ----------------

    @Test
    fun `onAddContainerItem checks the trade state of the added item`() {
        val state = newState()
        val item = Item(100)
        state.tradeItem = item

        state.onAddContainerItem(item)

        assertThat(closedTrades).containsExactly(state.player)
    }

    @Test
    fun `onUpdateContainerItem removes the old item before checking it`() {
        val state = newState()
        val oldItem = Item(100)
        val newItem = Item(101)
        val holder = container(1)
        oldItem.setParent(holder)
        holder.addItem(oldItem)
        state.tradeItem = oldItem

        state.onUpdateContainerItem(holder, oldItem, newItem)

        // C++ runs onRemoveContainerItem and then checkTradeState(oldItem)
        // again, so the close request fires twice (player.cpp:12470-12476).
        assertThat(closedTrades).containsExactly(state.player, state.player)
    }

    @Test
    fun `onUpdateContainerItem skips the trade check while transferring`() {
        val state = newState()
        state.setTradeState(TradeState_t.TRADE_TRANSFER)
        state.tradeItem = Item(100)

        state.onUpdateContainerItem(container(1), Item(100), Item(101))

        assertThat(closedTrades).isEmpty()
    }

    @Test
    fun `onRemoveContainerItem closes when the container holds the traded item`() {
        val state = newState()
        val traded = Item(100)
        val inner = container(2)
        inner.addItem(traded)
        traded.setParent(inner)
        val outer = container(1)
        outer.addItem(inner)
        inner.setParent(outer)
        state.tradeItem = traded

        state.onRemoveContainerItem(outer, inner)

        assertThat(closedTrades).containsExactly(state.player)
    }

    @Test
    fun `onRemoveInventoryItem closes for a held trade item and loot checks the container`() {
        val state = newState()
        val traded = Item(100)
        val holder = container(1)
        holder.addItem(traded)
        traded.setParent(holder)
        state.tradeItem = traded

        state.onRemoveInventoryItem(holder)

        assertThat(closedTrades).containsExactly(state.player)
        assertThat(lootChecks).containsExactly(holder)
    }

    @Test
    fun `onUpdateInventoryItem removes the old item first`() {
        val state = newState()
        val oldItem = Item(100)
        val holder = container(1)
        holder.addItem(oldItem)
        oldItem.setParent(holder)
        state.tradeItem = oldItem

        state.onUpdateInventoryItem(oldItem, Item(101))

        // C++ runs onRemoveInventoryItem and then checkTradeState(oldItem)
        // again, so the close request fires twice (player.cpp:12544-12550).
        assertThat(closedTrades).containsExactly(state.player, state.player)
    }
}
