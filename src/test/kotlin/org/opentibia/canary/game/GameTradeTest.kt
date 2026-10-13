package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.npcs.NpcWorld
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.TradeEvents_t
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.npcshop.ShopBlock
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the `Game` trade handlers: `Game::playerRequestTrade`
 * (game.cpp:5797), `Game::internalStartTrade` (game.cpp:5951),
 * `Game::playerAcceptTrade` (game.cpp:5986), `Game::getTradeErrorDescription`
 * (game.cpp:6088), `Game::playerLookInTrade` (game.cpp:6119),
 * `Game::playerCloseTrade` (game.cpp:6178) and `Game::internalCloseTrade`
 * (game.cpp:6187); and the shop handlers `Game::playerBuyItem` (game.cpp:6228),
 * `Game::playerSellItem` (game.cpp:6284) and `Game::playerCloseShop`
 * (game.cpp:6319).
 *
 * The Kotlin port keeps `Player::tradeState`/`tradeItem`/`tradePartner` and the
 * `Game::tradeItems` registry inside the facade (documented PARITY GAP) and
 * reaches the Player protocol sends through injected seams.
 */
class GameTradeTest {

    private val groundId = 100
    private val goldCoinId = 3031
    private val platinumCoinId = 3035
    private val backpackId = 2000

    private val center = Position(1000, 1000, 7)
    private val nextToCenter = Position(1001, 1000, 7)

    private data class ItemRequest(val player: Player, val traderName: String, val item: Item, val ack: Boolean)

    private class TradeHarness {
        val cancelMessages = mutableListOf<Pair<Player, ReturnValue>>()
        val itemRequests = mutableListOf<ItemRequest>()
        val tradeCloses = mutableListOf<Player>()
        val tradeEvents = mutableListOf<Triple<Item, TradeEvents_t, Player>>()
    }

    /**
     * Minimal `NpcWorld` implementing the inventory/money calls the NPC shop
     * engine reads (`src/creatures/npcs/npc.cpp`), backed by [inventory].
     */
    private class TestNpcWorld(
        val inventory: MutableList<Item> = mutableListOf(),
        var money: Long = 1000,
    ) : NpcWorld {
        override fun getInventoryItemsFromId(player: Player, itemId: Int, ignore: Boolean): List<Item> =
            inventory.filter { it.id == itemId }

        override fun getFreeBackpackSlots(player: Player): Int = 20

        override fun getMoney(player: Player): Long = money

        override fun getBankBalance(player: Player): Long = 0

        override fun getItemTypeCount(player: Player, itemId: Int): Long =
            inventory.filter { it.id == itemId }.sumOf { it.count.toLong() }

        override fun removeItem(player: Player, item: Item, count: Int): ReturnValue {
            inventory.remove(item)
            return ReturnValue.NOERROR
        }
    }

    private lateinit var harness: TradeHarness
    private lateinit var currentGame: Game

    private fun newGame(): Game {
        harness = TradeHarness()
        currentGame = Game(GameMap(), GameDispatcher()).also { game ->
            game.sendCancelMessage = { player, ret -> harness.cancelMessages.add(player to ret) }
            game.sendTradeItemRequest = { player, name, item, ack ->
                harness.itemRequests.add(ItemRequest(player, name, item, ack))
            }
            game.sendTradeClose = { player -> harness.tradeCloses.add(player) }
            game.onTradeEvent = { item, event, player -> harness.tradeEvents.add(Triple(item, event, player)) }
        }
        return currentGame
    }

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also {
            if (::currentGame.isInitialized) {
                currentGame.addPlayer(it)
            }
        }

    private fun Game.groundTile(position: Position): Tile =
        map.getOrCreateTile(position).also { if (it.ground == null) it.ground = Item(id = groundId) }

    private fun gold(count: Int = 1): Item =
        Item(id = goldCoinId, count = count, type = ItemType(id = goldCoinId, name = "gold coin", isStackable = true, weight = 0.1))

    private fun platinum(count: Int = 1): Item =
        Item(id = platinumCoinId, count = count, type = ItemType(id = platinumCoinId, name = "platinum coin", isStackable = true, weight = 0.1))

    private fun Game.itemOnTile(item: Item, position: Position = center): Tile {
        val tile = groundTile(position)
        check(internalAddItem(tile, item) == ReturnValue.NOERROR) { "item placement failed" }
        return tile
    }

    private fun messagesOf(player: Player): MutableList<Pair<MessageClass, String>> {
        val messages = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text -> messages.add(type to text) }
        return messages
    }

    // ----- internalStartTrade -----

    @Test
    fun `internalStartTrade starts the initiator and acknowledges the partner`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        val secondMessages = messagesOf(second)

        assertThat(game.internalStartTrade(first, second, item)).isTrue()

        assertThat(harness.itemRequests).containsExactly(ItemRequest(first, "Knight", item, true))
        assertThat(secondMessages).containsExactly(MessageClass.MESSAGE_TRANSACTION to "Knight wants to trade with you.")
    }

    @Test
    fun `internalStartTrade refuses a player already trading with someone else`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val third = player(3, "Sorcerer", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        game.internalStartTrade(first, second, item)
        harness.cancelMessages.clear()

        assertThat(game.internalStartTrade(first, third, item)).isFalse()

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.YOUAREALREADYTRADING)
    }

    @Test
    fun `internalStartTrade refuses a partner already trading with someone else`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val third = player(3, "Sorcerer", nextToCenter)
        val item = gold(3)
        val anotherItem = gold(4)
        game.itemOnTile(item)
        game.internalStartTrade(third, second, item)

        assertThat(game.internalStartTrade(first, second, anotherItem)).isFalse()

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.THISPLAYERISALREADYTRADING)
    }

    @Test
    fun `internalStartTrade refuses an untradeable item`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val owned = gold(3)
        owned.setAttribute(ItemAttribute.OWNER, 2L)

        assertThat(game.internalStartTrade(first, second, owned)).isFalse()

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.ITEMUNTRADEABLE)
        assertThat(harness.itemRequests).isEmpty()
    }

    // ----- playerRequestTrade guards -----

    @Test
    fun `playerRequestTrade refuses trading with yourself`() {
        val game = newGame()
        val first = player(1, "Knight")
        val messages = messagesOf(first)

        game.playerRequestTrade(1, center, 0, 1, gold().id)

        assertThat(messages).containsExactly(MessageClass.MESSAGE_FAILURE to "Sorry, not possible.")
    }

    @Test
    fun `playerRequestTrade asks the partner to move closer beyond two tiles`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", Position(1003, 1000, 7))
        val item = gold()
        game.itemOnTile(item)
        val messages = messagesOf(first)

        game.playerRequestTrade(1, center, 0, 2, item.id)

        assertThat(messages).containsExactly(MessageClass.MESSAGE_TRADE to "Druid tells you to move closer.")
    }

    @Test
    fun `playerRequestTrade cancels when the sight line is blocked`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold()
        game.itemOnTile(item)
        game.canThrowObjectTo = { _, _ -> false }

        game.playerRequestTrade(1, center, 0, 2, item.id)

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.CREATUREISNOTREACHABLE)
    }

    @Test
    fun `playerRequestTrade cancels when there is no top down item`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        game.groundTile(center)

        game.playerRequestTrade(1, center, 0, 2, goldCoinId)

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRequestTrade cancels on an item id mismatch`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold()
        game.itemOnTile(item)

        game.playerRequestTrade(1, center, 0, 2, platinumCoinId)

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRequestTrade cancels for a non pickupable item`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = Item(id = goldCoinId, count = 1, type = ItemType(id = goldCoinId, name = "fixed", isPickupable = false))
        game.itemOnTile(item)

        game.playerRequestTrade(1, center, 0, 2, item.id)

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRequestTrade cancels for a unique item`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold()
        item.setAttribute(ItemAttribute.UNIQUEID, 7L)
        game.itemOnTile(item)

        game.playerRequestTrade(1, center, 0, 2, item.id)

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRequestTrade cancels for an untradeable item`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold()
        item.setAttribute(ItemAttribute.STORE, 1L)
        game.itemOnTile(item)

        game.playerRequestTrade(1, center, 0, 2, item.id)

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.ITEMUNTRADEABLE)
    }

    @Test
    fun `playerRequestTrade points upstairs when the player stands above the item`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold()
        val itemPosition = Position(1000, 1000, 6)
        game.itemOnTile(item, itemPosition)

        game.playerRequestTrade(1, itemPosition, 0, 2, item.id)

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.FIRSTGOUPSTAIRS)
    }

    @Test
    fun `playerRequestTrade cancels when the item is out of reach`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold()
        val itemPosition = Position(1003, 1000, 7)
        game.itemOnTile(item, itemPosition)

        game.playerRequestTrade(1, itemPosition, 0, 2, item.id)

        // The auto-walk retry arm (getPathTo/queuePlayerAutoWalk/setNextWalkActionTask)
        // is not ported, so the no-path cancel is the only reachable outcome (PARITY GAP).
        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerRequestTrade refuses an item that is already being traded`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        game.internalStartTrade(first, second, item)
        val messages = messagesOf(first)

        game.playerRequestTrade(1, center, 0, 2, item.id)

        assertThat(messages).containsExactly(MessageClass.MESSAGE_TRADE to "This item is already being traded.")
    }

    @Test
    fun `playerRequestTrade is vetoed by the trade request event`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        game.eventPlayerOnTradeRequest = { _, _, _ -> false }
        game.callbackPlayerOnTradeRequest = { _, _, _ -> true }

        game.playerRequestTrade(1, center, 0, 2, item.id)

        assertThat(harness.itemRequests).isEmpty()
    }

    @Test
    fun `playerRequestTrade starts the trade through the request path`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)

        game.playerRequestTrade(1, center, 0, 2, item.id)

        assertThat(harness.itemRequests).containsExactly(ItemRequest(first, "Knight", item, true))
    }

    // ----- playerAcceptTrade -----

    @Test
    fun `playerAcceptTrade records the first accept without closing`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        game.internalStartTrade(first, second, item)

        game.playerAcceptTrade(1)

        assertThat(harness.tradeCloses).isEmpty()

        game.playerAcceptTrade(2)

        assertThat(harness.tradeCloses).containsExactlyInAnyOrder(first, second)
    }

    @Test
    fun `playerAcceptTrade swaps the offered items when both accept`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val firstItem = gold(3)
        val secondItem = platinum(2)
        game.itemOnTile(firstItem)
        game.itemOnTile(secondItem, nextToCenter)
        game.internalStartTrade(first, second, firstItem)
        game.internalStartTrade(second, first, secondItem)

        game.playerAcceptTrade(1)
        game.playerAcceptTrade(2)

        assertThat(second.getItemCount(goldCoinId)).isEqualTo(3)
        assertThat(first.getItemCount(platinumCoinId)).isEqualTo(2)
        assertThat(harness.tradeEvents).containsExactlyInAnyOrder(
            Triple(firstItem, TradeEvents_t.ON_TRADE_TRANSFER, second),
            Triple(secondItem, TradeEvents_t.ON_TRADE_TRANSFER, first),
        )
    }

    @Test
    fun `playerAcceptTrade fails the swap without capacity and reports the error`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val firstItem = gold(3)
        val secondItem = platinum(2)
        game.itemOnTile(firstItem)
        game.itemOnTile(secondItem, nextToCenter)
        game.internalStartTrade(first, second, firstItem)
        game.internalStartTrade(second, first, secondItem)
        second.capacity = 0.0
        val firstMessages = messagesOf(first)
        val secondMessages = messagesOf(second)

        game.playerAcceptTrade(1)
        game.playerAcceptTrade(2)

        assertThat(second.getItemCount(goldCoinId)).isZero()
        assertThat(firstMessages).containsExactly(MessageClass.MESSAGE_TRANSACTION to "Trade could not be completed.")
        assertThat(secondMessages).containsExactly(
            MessageClass.MESSAGE_TRANSACTION to
                "You do not have enough capacity to carry these objects.\n They weigh 0.30 oz.",
        )
        assertThat(harness.tradeEvents).containsExactlyInAnyOrder(
            Triple(firstItem, TradeEvents_t.ON_TRADE_CANCEL, first),
            Triple(secondItem, TradeEvents_t.ON_TRADE_CANCEL, second),
        )
    }

    @Test
    fun `playerAcceptTrade ignores a player outside the acknowledge state`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)

        game.playerAcceptTrade(1)

        assertThat(harness.tradeCloses).isEmpty()
        assertThat(harness.tradeEvents).isEmpty()
    }

    @Test
    fun `playerAcceptTrade cancels when the sight line is blocked`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        game.internalStartTrade(first, second, item)
        game.canThrowObjectTo = { _, _ -> false }

        game.playerAcceptTrade(1)

        assertThat(harness.cancelMessages).containsExactly(first to ReturnValue.CREATUREISNOTREACHABLE)
    }

    // ----- playerLookInTrade -----

    @Test
    fun `playerLookInTrade dispatches the look event with the distance`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item, Position(1002, 1000, 7))
        game.internalStartTrade(first, second, item)
        val looks = mutableListOf<Triple<Player, Item, Int>>()
        game.eventPlayerOnLookInTrade = { viewer, _, looked, distance -> looks.add(Triple(viewer, looked, distance)) }

        game.playerLookInTrade(1, lookAtCounterOffer = false, index = 0)

        assertThat(looks).containsExactly(Triple(first, item, 2))
    }

    @Test
    fun `playerLookInTrade ignores a counter offer without a partner item`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        game.internalStartTrade(first, second, item)
        var looks = 0
        game.eventPlayerOnLookInTrade = { _, _, _, _ -> looks++ }

        game.playerLookInTrade(1, lookAtCounterOffer = true, index = 0)

        assertThat(looks).isZero()
    }

    @Test
    fun `playerLookInTrade ignores a container index on a non container item`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        game.internalStartTrade(first, second, item)
        var looks = 0
        game.eventPlayerOnLookInTrade = { _, _, _, _ -> looks++ }

        game.playerLookInTrade(1, lookAtCounterOffer = false, index = 1)

        assertThat(looks).isZero()
    }

    // ----- playerCloseTrade / internalCloseTrade -----

    @Test
    fun `playerCloseTrade cancels both sides and fires the cancel events`() {
        val game = newGame()
        val first = player(1, "Knight")
        val second = player(2, "Druid", nextToCenter)
        val item = gold(3)
        game.itemOnTile(item)
        game.internalStartTrade(first, second, item)
        val firstMessages = messagesOf(first)
        val secondMessages = messagesOf(second)

        game.playerCloseTrade(1)

        assertThat(firstMessages).containsExactly(MessageClass.MESSAGE_FAILURE to "Trade cancelled.")
        assertThat(secondMessages).containsExactly(MessageClass.MESSAGE_FAILURE to "Trade cancelled.")
        assertThat(harness.tradeCloses).containsExactlyInAnyOrder(first, second)
        assertThat(harness.tradeEvents).containsExactly(Triple(item, TradeEvents_t.ON_TRADE_CANCEL, first))
    }

    @Test
    fun `playerCloseTrade ignores an unknown player id`() {
        val game = newGame()

        game.playerCloseTrade(99)

        assertThat(harness.tradeCloses).isEmpty()
    }

    // ----- getTradeErrorDescription -----

    @Test
    fun `getTradeErrorDescription describes the capacity and room failures`() {
        val game = newGame()
        val single = gold(1)
        val stack = gold(3)

        assertThat(game.getTradeErrorDescription(ReturnValue.NOTENOUGHCAPACITY, single))
            .isEqualTo("You do not have enough capacity to carry this object.\n It weighs 0.10 oz.")
        assertThat(game.getTradeErrorDescription(ReturnValue.NOTENOUGHCAPACITY, stack))
            .isEqualTo("You do not have enough capacity to carry these objects.\n They weigh 0.30 oz.")
        assertThat(game.getTradeErrorDescription(ReturnValue.NOTENOUGHROOM, stack))
            .isEqualTo("You do not have enough room to carry these objects.")
        assertThat(game.getTradeErrorDescription(ReturnValue.CONTAINERNOTENOUGHROOM, single))
            .isEqualTo("You do not have enough room to carry this object.")
        assertThat(game.getTradeErrorDescription(ReturnValue.NOTPOSSIBLE, single))
            .isEqualTo("Trade could not be completed.")
        assertThat(game.getTradeErrorDescription(ReturnValue.NOTPOSSIBLE, null))
            .isEqualTo("Trade could not be completed.")
    }

    // ----- shop -----

    private fun shopItems(): Items = Items().also { registry ->
        registry.register(ItemType(id = goldCoinId, name = "gold coin", isStackable = true, weight = 0.1, isPickupable = true))
        registry.register(ItemType(id = platinumCoinId, name = "platinum coin", isStackable = true, weight = 0.1, isPickupable = true))
        registry.register(ItemType(id = backpackId, name = "backpack", isContainer = true, isPickupable = true))
        registry.register(ItemType(id = 2001, name = "fluid vial", isFluidContainer = true, isPickupable = true))
    }

    private fun newShopGame(): Game {
        harness = TradeHarness()
        currentGame = Game(GameMap(), GameDispatcher(), itemTypes = shopItems()).also { game ->
            game.sendCancelMessage = { player, ret -> harness.cancelMessages.add(player to ret) }
        }
        return currentGame
    }

    private fun Npc.stockFor(player: Player, vararg blocks: ShopBlock) {
        addShopPlayer(player.getGUID(), blocks.toList())
    }

    private fun shopNpc(id: Long = 10, name: String = "Merchant", position: Position = center, world: NpcWorld? = null): Npc =
        Npc(id = id, name = name, position = position, world = world)

    private fun buyGame(
        buyer: Player,
        merchant: Npc,
    ): Game {
        val game = newShopGame()
        game.addPlayer(buyer)
        game.getShopOwner = { merchant }
        return game
    }

    @Test
    fun `playerBuyItem delegates to the merchant and exhausts the ui`() {
        val buyer = player(1, "Knight")
        val world = TestNpcWorld(money = 1000)
        val merchant = shopNpc(world = world)
        merchant.stockFor(buyer, ShopBlock(itemId = goldCoinId, itemBuyPrice = 5))
        val game = buyGame(buyer, merchant)
        var exhausted = 0
        game.updateUIExhausted = { exhausted++ }
        var bought = 0
        merchant.registerOnPlayerBuyHandler { _, _, itemId, _, amount, _, _, _ ->
            bought++
            assertThat(itemId).isEqualTo(goldCoinId)
            assertThat(amount).isEqualTo(3)
        }

        game.playerBuyItem(1, goldCoinId, count = 1, amount = 3)

        assertThat(bought).isEqualTo(1)
        assertThat(exhausted).isEqualTo(1)
    }

    @Test
    fun `playerBuyItem refuses zero amount unknown item and over cap`() {
        val buyer = player(1, "Knight")
        val world = TestNpcWorld()
        val merchant = shopNpc(world = world)
        merchant.stockFor(buyer, ShopBlock(itemId = goldCoinId, itemBuyPrice = 5))
        val game = buyGame(buyer, merchant)
        var bought = 0
        merchant.registerOnPlayerBuyHandler { _, _, _, _, _, _, _, _ -> bought++ }

        game.playerBuyItem(1, goldCoinId, count = 1, amount = 0)
        game.playerBuyItem(1, 9999, count = 1, amount = 1)
        game.playerBuyItem(1, goldCoinId, count = 1, amount = 10001)
        game.playerBuyItem(1, backpackId, count = 1, amount = 101)

        assertThat(bought).isZero()
    }

    @Test
    fun `playerBuyItem cancels when the player is exhausted`() {
        val buyer = player(1, "Knight")
        val world = TestNpcWorld()
        val merchant = shopNpc(world = world)
        merchant.stockFor(buyer, ShopBlock(itemId = goldCoinId, itemBuyPrice = 5))
        val game = buyGame(buyer, merchant)
        game.isUIExhausted = { true }

        game.playerBuyItem(1, goldCoinId, count = 1, amount = 1)

        assertThat(harness.cancelMessages).containsExactly(buyer to ReturnValue.YOUAREEXHAUSTED)
    }

    @Test
    fun `playerBuyItem refuses an item the merchant does not sell for the player`() {
        val buyer = player(1, "Knight")
        val world = TestNpcWorld()
        val merchant = shopNpc(world = world)
        val game = buyGame(buyer, merchant)
        var bought = 0
        merchant.registerOnPlayerBuyHandler { _, _, _, _, _, _, _, _ -> bought++ }

        game.playerBuyItem(1, goldCoinId, count = 1, amount = 1)

        assertThat(bought).isZero()
    }

    @Test
    fun `playerBuyItem checks the backpack container limit`() {
        val buyer = player(1, "Knight")
        val world = TestNpcWorld()
        val merchant = shopNpc(world = world)
        merchant.stockFor(buyer, ShopBlock(itemId = backpackId, itemBuyPrice = 5))
        val game = buyGame(buyer, merchant)
        game.maxContainer = 0
        val backpack = Container(id = backpackId, capacity = 20, type = ItemType(id = backpackId, name = "backpack", isContainer = true))
        check(buyer.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK) == ReturnValue.NOERROR) { "backpack equip failed" }
        var bought = 0
        merchant.registerOnPlayerBuyHandler { _, _, _, _, _, _, _, _ -> bought++ }

        game.playerBuyItem(1, backpackId, count = 1, amount = 1, inBackpacks = true)

        assertThat(harness.cancelMessages).containsExactly(buyer to ReturnValue.CONTAINERISFULL)
        assertThat(bought).isZero()
    }

    @Test
    fun `playerSellItem delegates to the merchant`() {
        val seller = player(1, "Knight")
        val world = TestNpcWorld(inventory = mutableListOf(gold(2)))
        val merchant = shopNpc(world = world)
        merchant.stockFor(seller, ShopBlock(itemId = goldCoinId, itemSellPrice = 5))
        val game = buyGame(seller, merchant)
        var sold = 0
        merchant.registerOnPlayerSellHandler { _, _, itemId, _, amount, _, _, _ ->
            sold++
            assertThat(itemId).isEqualTo(goldCoinId)
            assertThat(amount).isEqualTo(2)
        }

        game.playerSellItem(1, goldCoinId, count = 1, amount = 2, ignoreEquipped = false)

        assertThat(sold).isEqualTo(1)
    }

    @Test
    fun `playerSellItem refuses zero amount over cap and unknown items`() {
        val seller = player(1, "Knight")
        val world = TestNpcWorld(inventory = mutableListOf(gold(2)))
        val merchant = shopNpc(world = world)
        merchant.stockFor(seller, ShopBlock(itemId = goldCoinId, itemSellPrice = 5))
        val game = buyGame(seller, merchant)
        var sold = 0
        merchant.registerOnPlayerSellHandler { _, _, _, _, _, _, _, _ -> sold++ }

        game.playerSellItem(1, goldCoinId, count = 1, amount = 0, ignoreEquipped = false)
        game.playerSellItem(1, 9999, count = 1, amount = 1, ignoreEquipped = false)
        game.playerSellItem(1, goldCoinId, count = 1, amount = 10001, ignoreEquipped = false)
        game.playerSellItem(1, backpackId, count = 1, amount = 101, ignoreEquipped = false)

        assertThat(sold).isZero()
    }

    @Test
    fun `playerCloseShop closes the shop window of the interacting merchant`() {
        val game = newShopGame()
        val buyer = player(1, "Knight")
        game.addPlayer(buyer)
        val closedFor = mutableListOf<Player>()
        game.closeShopWindow = { p -> closedFor.add(p) }

        game.playerCloseShop(1)

        assertThat(closedFor).containsExactly(buyer)
    }

    @Test
    fun `playerBuyItem closes the shop when the merchant is out of interaction range`() {
        val buyer = player(1, "Knight")
        val world = TestNpcWorld()
        val merchant = shopNpc(position = Position(2000, 2000, 7), world = world)
        merchant.stockFor(buyer, ShopBlock(itemId = goldCoinId, itemBuyPrice = 5))
        val game = buyGame(buyer, merchant)
        var closed = 0
        game.closeShopWindow = { closed++ }

        game.playerBuyItem(1, goldCoinId, count = 1, amount = 1)

        assertThat(closed).isEqualTo(1)
    }
}
