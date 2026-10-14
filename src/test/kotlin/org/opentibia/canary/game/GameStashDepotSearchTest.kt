package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ITEM_GOLD_COIN
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.items.containers.depot.DepotLocker
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the stash/depot-search `Game` handlers:
 *
 *  - `Game::isTryingToStow` (game.cpp:2297)
 *  - `Game::playerRequestDepotItems` (game.cpp:6920)
 *  - `Game::playerRequestDepotSearchItem` (game.cpp:6945)
 *  - `Game::playerRequestDepotSearchRetrieve` (game.cpp:6960)
 *  - `Game::playerRequestOpenContainerFromDepotSearch` (game.cpp:6975)
 *  - `Game::playerStowItem` (game.cpp:5665)
 *  - `Game::playerStashWithdraw` (game.cpp:5706)
 *  - `Game::tryRetrieveStashItems` (game.cpp:12342)
 *  - `Game::addItemStoreInbox` (game.cpp:12231)
 */
class GameStashDepotSearchTest {

    private val center = Position(1000, 1000, 7)

    /** `ITEM_STASH_INDEX` (`src/utils/utils_definitions.hpp:552`). */
    private val itemStashIndex = 1

    private class Harness(
        val game: Game,
        val cancels: MutableList<ReturnValue>,
        val texts: MutableList<Pair<MessageClass, String>>,
    )

    private fun newGame(itemTypes: Items? = null): Harness {
        val game = Game(GameMap(), GameDispatcher(), itemTypes = itemTypes)
        val cancels = mutableListOf<ReturnValue>()
        val texts = mutableListOf<Pair<MessageClass, String>>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        game.sendTextMessage = { _, type, text -> texts.add(type to text) }
        return Harness(game, cancels, texts)
    }

    private fun Game.player(id: Long = 1, name: String = "Stasher"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also { addPlayer(it) }

    private fun container(id: Int, capacity: Int = 8): Container =
        Container(id = id, capacity = capacity, type = ItemType(id = id, name = "container-$id", isContainer = true))

    private fun item(id: Int, count: Int = 1, stackable: Boolean = false, type: ItemType? = null): Item =
        Item(
            id = id,
            count = count,
            type = type ?: ItemType(id = id, name = "item-$id", isMovable = true, isStackable = stackable),
        )

    private fun Game.putOnTile(position: Position, item: Item) {
        map.getOrCreateTile(position).addItem(item)
    }

    // ----- isTryingToStow -----

    @Test
    fun `isTryingToStow is true only for a locker container at the stash index`() {
        val harness = newGame()
        val locker = container(DepotLocker.ITEM_LOCKER)
        val chest = container(5000)

        assertThat(harness.game.isTryingToStow(Position(0, 0, itemStashIndex), locker)).isTrue()
        assertThat(harness.game.isTryingToStow(Position(0, 0, 7), locker)).isFalse()
        assertThat(harness.game.isTryingToStow(Position(0, 0, itemStashIndex), chest)).isFalse()
    }

    // ----- playerRequestDepotItems -----

    @Test
    fun `playerRequestDepotItems ignores an unknown player`() {
        val harness = newGame()
        harness.game.playerRequestDepotItems(404)
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerRequestDepotItems ignores a player without depot search access`() {
        val harness = newGame()
        val player = harness.game.player()
        player.depotSearch = false

        harness.game.playerRequestDepotItems(player.id)

        assertThat(harness.cancels).isEmpty()
        assertThat(player.isDepotSearchOpen()).isFalse()
    }

    @Test
    fun `playerRequestDepotItems cancels an exhausted player`() {
        val harness = newGame()
        val player = harness.game.player()
        player.depotSearch = true
        harness.game.isUIExhausted = { true }

        harness.game.playerRequestDepotItems(player.id)

        assertThat(harness.cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(player.isDepotSearchOpen()).isFalse()
    }

    @Test
    fun `playerRequestDepotItems opens the search and refreshes the UI exhaustion`() {
        val harness = newGame()
        val player = harness.game.player()
        player.depotSearch = true
        var refreshed = false
        harness.game.updateUIExhausted = { refreshed = true }

        harness.game.playerRequestDepotItems(player.id)

        assertThat(player.isDepotSearchOpen()).isTrue()
        assertThat(refreshed).isTrue()
        assertThat(harness.cancels).isEmpty()
    }

    // ----- playerRequestDepotSearchItem -----

    @Test
    fun `playerRequestDepotSearchItem ignores a player whose search is closed`() {
        val harness = newGame()
        val player = harness.game.player()

        harness.game.playerRequestDepotSearchItem(player.id, 2160, 0)

        assertThat(player.isDepotSearchOpen()).isFalse()
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerRequestDepotSearchItem cancels an exhausted player`() {
        val harness = newGame()
        val player = harness.game.player()
        player.setDepotSearchIsOpen(1, 0)
        harness.game.isUIExhausted = { true }

        harness.game.playerRequestDepotSearchItem(player.id, 2160, 2)

        assertThat(harness.cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(player.getDepotSearchItemId()).isEqualTo(1)
    }

    @Test
    fun `playerRequestDepotSearchItem records the requested item and tier`() {
        val harness = newGame()
        val player = harness.game.player()
        player.setDepotSearchIsOpen(1, 0)
        var refreshed = false
        harness.game.updateUIExhausted = { refreshed = true }

        harness.game.playerRequestDepotSearchItem(player.id, 2160, 2)

        assertThat(player.getDepotSearchItemId()).isEqualTo(2160)
        assertThat(player.getDepotSearchItemTier()).isEqualTo(2)
        assertThat(refreshed).isTrue()
    }

    // ----- playerRequestDepotSearchRetrieve -----

    @Test
    fun `playerRequestDepotSearchRetrieve ignores a search not open on the item`() {
        val harness = newGame()
        val player = harness.game.player()
        player.setDepotSearchIsOpen(2160, 0)
        var retrieved: Triple<Int, Int, Boolean>? = null
        harness.game.retrieveAllItemsFromDepotSearch = { _, itemId, tier, isDepot ->
            retrieved = Triple(itemId, tier, isDepot)
        }

        harness.game.playerRequestDepotSearchRetrieve(player.id, 9999, 0, 0)

        assertThat(retrieved).isNull()
    }

    @Test
    fun `playerRequestDepotSearchRetrieve cancels an exhausted player`() {
        val harness = newGame()
        val player = harness.game.player()
        player.setDepotSearchIsOpen(2160, 0)
        harness.game.isUIExhausted = { true }
        var retrieved = false
        harness.game.retrieveAllItemsFromDepotSearch = { _, _, _, _ -> retrieved = true }

        harness.game.playerRequestDepotSearchRetrieve(player.id, 2160, 0, 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(retrieved).isFalse()
    }

    @Test
    fun `playerRequestDepotSearchRetrieve retrieves all with the depot flag from the type byte`() {
        val harness = newGame()
        val player = harness.game.player()
        player.setDepotSearchIsOpen(2160, 3)
        var retrieved: Triple<Int, Int, Boolean>? = null
        harness.game.retrieveAllItemsFromDepotSearch = { _, itemId, tier, isDepot ->
            retrieved = Triple(itemId, tier, isDepot)
        }
        var refreshed = false
        harness.game.updateUIExhausted = { refreshed = true }

        harness.game.playerRequestDepotSearchRetrieve(player.id, 2160, 3, 1)

        assertThat(retrieved).isEqualTo(Triple(2160, 3, true))
        assertThat(refreshed).isTrue()
    }

    // ----- playerRequestOpenContainerFromDepotSearch -----

    @Test
    fun `playerRequestOpenContainerFromDepotSearch ignores a closed search`() {
        val harness = newGame()
        val player = harness.game.player()
        var opened: Position? = null
        harness.game.openContainerFromDepotSearch = { _, pos -> opened = pos }

        harness.game.playerRequestOpenContainerFromDepotSearch(player.id, center)

        assertThat(opened).isNull()
    }

    @Test
    fun `playerRequestOpenContainerFromDepotSearch cancels an exhausted player`() {
        val harness = newGame()
        val player = harness.game.player()
        player.setDepotSearchIsOpen(1, 0)
        harness.game.isUIExhausted = { true }
        var opened = false
        harness.game.openContainerFromDepotSearch = { _, _ -> opened = true }

        harness.game.playerRequestOpenContainerFromDepotSearch(player.id, center)

        assertThat(harness.cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(opened).isFalse()
    }

    @Test
    fun `playerRequestOpenContainerFromDepotSearch opens the position and refreshes exhaustion`() {
        val harness = newGame()
        val player = harness.game.player()
        player.setDepotSearchIsOpen(1, 0)
        var opened: Position? = null
        harness.game.openContainerFromDepotSearch = { _, pos -> opened = pos }
        var refreshed = false
        harness.game.updateUIExhausted = { refreshed = true }

        harness.game.playerRequestOpenContainerFromDepotSearch(player.id, center)

        assertThat(opened).isEqualTo(center)
        assertThat(refreshed).isTrue()
    }

    // ----- playerStowItem -----

    @Test
    fun `playerStowItem ignores an unknown player`() {
        val harness = newGame()
        var stowed = false
        harness.game.stowItem = { _, _, _, _ -> stowed = true }

        harness.game.playerStowItem(404, center, 2160, 0, 1, false)

        assertThat(stowed).isFalse()
    }

    @Test
    fun `playerStowItem cancels a non-premium player`() {
        val harness = newGame()
        val player = harness.game.player()

        harness.game.playerStowItem(player.id, center, 2160, 0, 1, false)

        assertThat(harness.cancels).containsExactly(ReturnValue.YOUNEEDPREMIUMACCOUNT)
    }

    @Test
    fun `playerStowItem ignores a missing thing`() {
        val harness = newGame()
        val player = harness.game.player()
        player.premium = true
        var stowed = false
        harness.game.stowItem = { _, _, _, _ -> stowed = true }

        harness.game.playerStowItem(player.id, Position(2000, 2000, 7), 2160, 0, 1, false)

        assertThat(stowed).isFalse()
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerStowItem cancels when the item does not match the requested id`() {
        val harness = newGame()
        val player = harness.game.player()
        player.premium = true
        harness.game.putOnTile(center, item(2160))

        harness.game.playerStowItem(player.id, center, 2161, 0, 1, false)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerStowItem cancels when the count exceeds the item count`() {
        val harness = newGame()
        val player = harness.game.player()
        player.premium = true
        harness.game.putOnTile(center, item(2160, count = 5, stackable = true))

        harness.game.playerStowItem(player.id, center, 2160, 0, 6, false)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerStowItem cancels a store item`() {
        val harness = newGame()
        val player = harness.game.player()
        player.premium = true
        val storeItem = item(2160).also { it.setAttribute(ItemAttribute.STORE, 1) }
        harness.game.putOnTile(center, storeItem)

        harness.game.playerStowItem(player.id, center, 2160, 0, 1, false)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerStowItem cancels an item owned by someone else`() {
        val harness = newGame()
        val player = harness.game.player()
        player.premium = true
        val owned = item(2160).also { it.setOwner(999) }
        harness.game.putOnTile(center, owned)

        harness.game.playerStowItem(player.id, center, 2160, 0, 1, false)

        assertThat(harness.cancels).containsExactly(ReturnValue.ITEMISNOTYOURS)
    }

    @Test
    fun `playerStowItem cancels a position out of range`() {
        val harness = newGame()
        val player = harness.game.player()
        player.premium = true
        val far = Position(1000, 1002, 7)
        harness.game.putOnTile(far, item(2160))

        harness.game.playerStowItem(player.id, far, 2160, 0, 1, false)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerStowItem stows and refreshes an open depot search`() {
        val harness = newGame()
        val player = harness.game.player()
        player.premium = true
        player.setDepotSearchIsOpen(2160, 0)
        val carried = item(2160, count = 5, stackable = true)
        harness.game.putOnTile(center, carried)
        val stowed = mutableListOf<Triple<Item, Int, Boolean>>()
        harness.game.stowItem = { _, stowItem, count, allItems -> stowed.add(Triple(stowItem, count, allItems)) }

        harness.game.playerStowItem(player.id, center, 2160, 0, 3, true)

        assertThat(stowed).containsExactly(Triple(carried, 3, true))
        assertThat(player.getDepotSearchItemId()).isEqualTo(2160)
        assertThat(harness.cancels).isEmpty()
    }

    // ----- playerStashWithdraw -----

    @Test
    fun `playerStashWithdraw ignores an unknown player`() {
        val harness = newGame()
        harness.game.playerStashWithdraw(404, 2160, 1, 0)
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerStashWithdraw ignores a player that cannot pick up items`() {
        val harness = newGame()
        val player = harness.game.player()
        player.setFlag(PlayerFlags_t.CannotPickupItem)
        var withdrawn = false
        harness.game.addItemFromStash = { _, _, _ -> withdrawn = true; ReturnValue.NOERROR }

        harness.game.playerStashWithdraw(player.id, 2160, 1, 0)

        assertThat(withdrawn).isFalse()
    }

    @Test
    fun `playerStashWithdraw ignores unknown items and zero counts`() {
        val items = Items().also { it.register(ItemType(id = 2160, name = "item-2160")) }
        val harness = newGame(items)
        val player = harness.game.player()
        var withdrawn = false
        harness.game.addItemFromStash = { _, _, _ -> withdrawn = true; ReturnValue.NOERROR }

        harness.game.playerStashWithdraw(player.id, 9999, 1, 0)
        harness.game.playerStashWithdraw(player.id, 2160, 0, 0)

        assertThat(withdrawn).isFalse()
    }

    @Test
    fun `playerStashWithdraw cancels without enough free capacity`() {
        val items = Items().also { it.register(ItemType(id = 2160, name = "item-2160")) }
        val harness = newGame(items)
        val player = harness.game.player()
        player.capacity = 0.0

        harness.game.playerStashWithdraw(player.id, 2160, 1, 0)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTENOUGHCAPACITY)
    }

    @Test
    fun `playerStashWithdraw clamps the count to the configured limit`() {
        val items = Items().also { it.register(ItemType(id = 2160, name = "item-2160")) }
        val harness = newGame(items)
        val player = harness.game.player()
        harness.game.stashManageAmount = 10
        var withdrawn = 0L
        harness.game.addItemFromStash = { _, _, count -> withdrawn = count; ReturnValue.NOERROR }

        harness.game.playerStashWithdraw(player.id, 2160, 25, 0)

        assertThat(withdrawn).isEqualTo(10)
        assertThat(harness.texts).containsExactly(
            MessageClass.MESSAGE_EVENT_ADVANCE to "You can only withdraw up to 10 items at a time from the stash.",
        )
    }

    @Test
    fun `playerStashWithdraw corrects a partial failed withdrawal`() {
        val items = Items().also { it.register(ItemType(id = 2160, name = "item-2160")) }
        val harness = newGame(items)
        val player = harness.game.player()
        player.stash.addItemOnStash(2160, 5)
        harness.game.getStashItemCount = { _, _ -> player.stash.getStashItemCount(2160) }
        harness.game.addItemFromStash = { p, _, _ ->
            p.stash.withdrawItem(2160, 2)
            ReturnValue.NOTENOUGHCAPACITY
        }

        harness.game.playerStashWithdraw(player.id, 2160, 5, 0)

        assertThat(player.stash.getStashItemCount(2160)).isEqualTo(5)
        assertThat(harness.cancels).containsExactly(ReturnValue.NOTENOUGHCAPACITY)
    }

    @Test
    fun `playerStashWithdraw refreshes the depot search and reopens the stash`() {
        val items = Items().also { it.register(ItemType(id = 2160, name = "item-2160")) }
        val harness = newGame(items)
        val player = harness.game.player()
        player.setDepotSearchIsOpen(2160, 0)
        harness.game.addItemFromStash = { _, _, _ -> ReturnValue.NOERROR }
        var stashOpen = false
        harness.game.sendOpenStash = { _, isNpc -> stashOpen = isNpc }

        harness.game.playerStashWithdraw(player.id, 2160, 1, 0)

        assertThat(stashOpen).isTrue()
        assertThat(player.getDepotSearchItemId()).isEqualTo(2160)
        assertThat(harness.cancels).isEmpty()
    }

    // ----- tryRetrieveStashItems -----

    @Test
    fun `tryRetrieveStashItems reports failure without a managed container`() {
        val harness = newGame()
        val player = harness.game.player()
        val gold = item(ITEM_GOLD_COIN, count = 1, type = ItemType(id = ITEM_GOLD_COIN, name = "gold coin", isStackable = true))

        assertThat(harness.game.tryRetrieveStashItems(player, gold)).isFalse()
    }

    @Test
    fun `tryRetrieveStashItems places a coin with autobank and reports success`() {
        val harness = newGame()
        val player = harness.game.player()
        harness.game.autobank = true
        var bank = 0L
        harness.game.getBankBalance = { bank }
        harness.game.setBankBalance = { _, value -> bank = value }
        val coin = item(ITEM_GOLD_COIN, count = 5, type = ItemType(id = ITEM_GOLD_COIN, name = "gold coin", isStackable = true))
        val source = container(5000)
        source.addThing(coin)

        assertThat(harness.game.tryRetrieveStashItems(player, coin)).isTrue()
        assertThat(bank).isEqualTo(5)
    }

    // ----- addItemStoreInbox -----

    @Test
    fun `addItemStoreInbox fails without a store inbox item`() {
        val items = Items().also { it.register(ItemType(id = 23398, name = "decoration kit")) }
        val harness = newGame(items)
        val player = harness.game.player()

        assertThat(harness.game.addItemStoreInbox(player, 2160)).isFalse()
    }

    @Test
    fun `addItemStoreInbox fails when the store inbox slot holds no container`() {
        val items = Items().also { it.register(ItemType(id = 23398, name = "decoration kit")) }
        val harness = newGame(items)
        val player = harness.game.player()
        player.addItem(item(23396, type = ItemType(id = 23396, name = "store inbox")), Slots_t.CONST_SLOT_STORE_INBOX)

        assertThat(harness.game.addItemStoreInbox(player, 2160)).isFalse()
    }

    @Test
    fun `addItemStoreInbox adds the stamped decoration kit to the store inbox`() {
        val items = Items().also {
            it.register(ItemType(id = 23398, name = "decoration kit", isContainer = false))
            it.register(ItemType(id = 2160, name = "magic sword"))
        }
        val harness = newGame(items)
        val player = harness.game.player()
        val inbox = container(23396, capacity = 20)
        player.addItem(inbox, Slots_t.CONST_SLOT_STORE_INBOX)

        assertThat(harness.game.addItemStoreInbox(player, 2160)).isTrue()

        val kit = inbox.items.last()
        assertThat(kit.id).isEqualTo(23398)
        assertThat(kit.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo(
            "You bought this item in the Store.\nUnwrap it in your own house to create a <magic sword>.",
        )
        assertThat(harness.game.getItemCustomAttribute(kit, "unWrapId")).isEqualTo(2160L)
    }
}
