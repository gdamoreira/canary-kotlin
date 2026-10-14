package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.TradeState_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.house.House
import org.opentibia.canary.network.protocol.npcshop.ShopBlock

/**
 * Self-contained mirror of the Player trade/shop/write/edit-house interaction
 * state (src/creatures/players/player.cpp:1329, 2963-2981, 3092-3120,
 * 3160-3166, 6037-6076, 6105-6125, 12465-12566, 12568-12576).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps `tradeState`/`tradeItem`/`shopOwner`/`writeItem`/`windowTextId`/
 * `maxWriteLen`/`editHouse`/`editListId`/`itemPriceMap`/`scheduledSaleUpdate`
 * directly on `Player` (player.hpp:1676-1926); the state is exposed as an
 * injectable seam so the host player can delegate without a global (AGENTS.md
 * 1.1). Every external dependency is a constructor seam:
 * `Game::internalCloseTrade` (game.cpp:6187), the dispatcher's deferred
 * `Game::updatePlayerSaleItems` (player.cpp:6046), `Npc::getShopItemVector`
 * (npc.cpp:512), `ItemType::isFluidContainer` (player.cpp:6073),
 * `Game::getItemMarketPrice` (player.cpp:12574) and
 * `Player::checkLootContainers` (player.cpp:12564).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe. The C++ out-parameters of `getWriteItem`/`getEditHouse` become
 * reads of [windowTextId]/[maxWriteLen]/[editListId].
 */
class PlayerInteractionState(
    /** The host `Player` whose id and state the trade/UI hooks act on. */
    val player: Player,
    /** `Game::internalCloseTrade(player)` (player.cpp:2971, 12561). */
    private val internalCloseTrade: (Player) -> Unit = {},
    /** The dispatcher-deferred `Game::updatePlayerSaleItems(creatureId)` (player.cpp:6046). */
    private val scheduleSaleUpdate: () -> Unit = {},
    /** `Npc::getShopItemVector(playerGuid)` (player.cpp:6074). */
    private val shopItemVector: (Npc, Long) -> List<ShopBlock> = { _, _ -> emptyList() },
    /** `ItemType::isFluidContainer()` for the sold item id (player.cpp:6073). */
    private val isFluidContainer: (Int) -> Boolean = { false },
    /** `Game::getItemMarketPrice(itemMap, buyPrice)` (player.cpp:12574). */
    private val getItemMarketPrice: (Map<Int, Long>, Boolean) -> Long = { _, _ -> 0L },
    /** `Player::checkLootContainers(item->getContainer())` (player.cpp:12564). */
    private val checkLootContainers: (Container?) -> Unit = {},
) {

    /** `Player::tradeState` (player.hpp:1914). */
    private var tradeState: TradeState_t = TradeState_t.TRADE_NONE

    /** `Player::tradeItem` (player.hpp:1915); written by the Game trade pipeline. */
    @get:JvmName("getTradeItemValue")
    var tradeItem: Item? = null

    /** `Player::shopOwner` (player.hpp:1893). */
    private var shopOwner: Npc? = null

    /** `Player::writeItem` (player.hpp:1887). */
    private var writeItem: Item? = null

    /** `Player::windowTextId` (player.hpp:1886); `uint32_t`. */
    var windowTextId: Long = 0

    /** `Player::maxWriteLen` (player.hpp:1888); `uint16_t`. */
    var maxWriteLen: Int = 0

    /** `Player::editHouse` (player.hpp:1890). */
    private var editHouse: House? = null

    /** `Player::editListId` (player.hpp:1891); `uint32_t`. */
    var editListId: Long = 0

    /** `Player::itemPriceMap` (player.hpp:1686). */
    private val itemPriceMap = mutableMapOf<Int, Long>()

    /** `Player::scheduledSaleUpdate` (player.hpp:1917). */
    private var scheduledSaleUpdate = false

    /** `Player::setTradeState` (player.cpp:6105-6107). */
    fun setTradeState(state: TradeState_t) {
        tradeState = state
    }

    /** `Player::getTradeState` (player.cpp:6109-6111). */
    fun getTradeState(): TradeState_t = tradeState

    /** `Player::getTradeItem` (player.cpp:6113-6115). */
    fun getTradeItem(): Item? = tradeItem

    /**
     * `Player::checkTradeState(item)` (player.cpp:2963-2981): a transfer in
     * progress is ignored; offering/within the traded item's container chain
     * closes the trade.
     */
    fun checkTradeState(item: Item?) {
        val tradeItem = this.tradeItem ?: return
        if (tradeState == TradeState_t.TRADE_TRANSFER || item == null) {
            return
        }

        if (tradeItem === item) {
            internalCloseTrade(player)
            return
        }

        var container = item.getParent() as? Container
        while (container != null) {
            if (container === tradeItem) {
                internalCloseTrade(player)
                break
            }

            container = container.getParent() as? Container
        }
    }

    /** `Player::setShopOwner` (player.cpp:6119-6121). */
    fun setShopOwner(owner: Npc?) {
        shopOwner = owner
    }

    /** `Player::getShopOwner` (player.cpp:6123-6125). */
    fun getShopOwner(): Npc? = shopOwner

    /**
     * `Player::getWriteItem(uint32_t &retWindowTextId, uint16_t &retMaxWriteLen)`
     * (player.cpp:3092-3096); the out-parameters are [windowTextId] and
     * [maxWriteLen].
     */
    fun getWriteItem(): Item? = writeItem

    /** `Player::setWriteItem(item, maxWriteLength)` (player.cpp:3098-3108). */
    fun setWriteItem(item: Item?, maxWriteLength: Int) {
        windowTextId++

        if (item != null) {
            writeItem = item
            maxWriteLen = maxWriteLength
        } else {
            writeItem = null
            maxWriteLen = 0
        }
    }

    /**
     * `Player::getEditHouse(uint32_t &retWindowTextId, uint32_t &retListId)`
     * (player.cpp:3110-3116); the out-parameters are [windowTextId] and
     * [editListId].
     */
    fun getEditHouse(): House? = editHouse

    /** `Player::setEditHouse(house, listId)` (player.cpp:3116-3120). */
    fun setEditHouse(house: House?, listId: Int) {
        windowTextId++
        editHouse = house
        editListId = listId.toLong() and 0xFFFF_FFFFL
    }

    /** `Player::setItemCustomPrice(itemId, price)` (player.cpp:1826-1828). */
    fun setItemCustomPrice(itemId: Int, price: Long) {
        itemPriceMap[itemId and 0xFFFF] = price
    }

    /**
     * `Player::getItemCustomPrice(itemId, buyPrice)` (player.cpp:12568-12576):
     * the stored custom price wins, otherwise the Game market price for a
     * single item evaluates the fallback.
     */
    fun getItemCustomPrice(itemId: Int, buyPrice: Boolean): Long {
        val id = itemId and 0xFFFF
        itemPriceMap[id]?.let { return it }

        return getItemMarketPrice(mapOf(id to 1L), buyPrice)
    }

    /**
     * `Player::updateSaleShopList(item)` (player.cpp:6037-6049): null or
     * id-less items are ignored; otherwise a deferred sale-list update is
     * scheduled and flagged.
     */
    fun updateSaleShopList(item: Item?): Boolean {
        if (item == null) {
            return true
        }

        val itemId = item.id
        if (itemId == 0) {
            return true
        }

        scheduleSaleUpdate()
        scheduledSaleUpdate = true
        return true
    }

    /** `Player::updateSaleShopList()` (player.cpp:6051-6054). */
    fun updateSaleShopList() {
        scheduleSaleUpdate()
        scheduledSaleUpdate = true
    }

    /**
     * `Player::hasShopItemForSale(itemId, subType)` (player.cpp:6066-6076): the
     * owner's vector must carry the id at a non-zero buy price and, for fluid
     * containers, the matching subtype.
     */
    fun hasShopItemForSale(itemId: Int, subType: Int): Boolean {
        val owner = shopOwner ?: return false
        val id = itemId and 0xFFFF
        val wantedSubType = subType and 0xFF

        return shopItemVector(owner, player.id).any { shopBlock ->
            shopBlock.itemId == id &&
                shopBlock.itemBuyPrice != 0L &&
                (!isFluidContainer(id) || shopBlock.itemSubType == wantedSubType)
        }
    }

    /** `Player::setScheduledSaleUpdate` (player.cpp:3160-3162). */
    fun setScheduledSaleUpdate(scheduled: Boolean) {
        scheduledSaleUpdate = scheduled
    }

    /** `Player::getScheduledSaleUpdate` (player.cpp:3164-3166). */
    fun getScheduledSaleUpdate(): Boolean = scheduledSaleUpdate

    /** `Player::onAddContainerItem(item)` (player.cpp:12465-12467). */
    fun onAddContainerItem(item: Item?) {
        checkTradeState(item)
    }

    /**
     * `Player::onUpdateContainerItem(container, oldItem, newItem)`
     * (player.cpp:12469-12477): a replaced item runs the remove hook first, then
     * the old item is re-checked unless a transfer is in progress.
     */
    fun onUpdateContainerItem(container: Container, oldItem: Item, newItem: Item) {
        if (oldItem !== newItem) {
            onRemoveContainerItem(container, oldItem)
        }

        if (tradeState != TradeState_t.TRADE_TRANSFER) {
            checkTradeState(oldItem)
        }
    }

    /**
     * `Player::onRemoveContainerItem(container, item)` (player.cpp:12479-12489):
     * the removed item is trade-checked, and a traded item still held by the
     * container (through nesting) closes the trade.
     */
    fun onRemoveContainerItem(container: Container, item: Item) {
        if (tradeState == TradeState_t.TRADE_TRANSFER) {
            return
        }

        checkTradeState(item)

        val tradeItem = this.tradeItem
        if (tradeItem != null) {
            if (tradeItem.getParent() !== container && container.isHoldingItem(tradeItem)) {
                internalCloseTrade(player)
            }
        }
    }

    /**
     * `Player::onUpdateInventoryItem(oldItem, newItem)` (player.cpp:12543-12551):
     * a replaced item runs the inventory remove hook first, then the old item is
     * re-checked unless a transfer is in progress.
     */
    fun onUpdateInventoryItem(oldItem: Item, newItem: Item) {
        if (oldItem !== newItem) {
            onRemoveInventoryItem(oldItem)
        }

        if (tradeState != TradeState_t.TRADE_TRANSFER) {
            checkTradeState(oldItem)
        }
    }

    /**
     * `Player::onRemoveInventoryItem(item)` (player.cpp:12553-12566): the
     * removed item is trade-checked, a held trade item closes the trade, and the
     * item's container is loot-checked.
     */
    fun onRemoveInventoryItem(item: Item) {
        if (tradeState != TradeState_t.TRADE_TRANSFER) {
            checkTradeState(item)

            val tradeItem = this.tradeItem
            if (tradeItem != null) {
                val container = item.getContainer()
                if (container != null && container.isHoldingItem(tradeItem)) {
                    internalCloseTrade(player)
                }
            }
        }

        checkLootContainers(item.getContainer())
    }
}
