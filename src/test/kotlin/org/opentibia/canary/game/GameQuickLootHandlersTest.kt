package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ITEM_GOLD_COIN
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the quick-loot/managed-container `Game` handlers:
 *
 *  - `Game::getObjectCategory` (game.cpp:3946/3962)
 *  - `Game::playerSetManagedContainer` (game.cpp:6822),
 *    `Game::playerClearManagedContainer` (game.cpp:6867),
 *    `Game::playerOpenManagedContainer` (game.cpp:6884)
 *  - `Game::findManagedContainer` (game.cpp:3757),
 *    `Game::findNextAvailableContainer` (game.cpp:3774),
 *    `Game::handleFallbackLogic` (game.cpp:3798)
 *  - `Game::processMoveOrAddItemToLootContainer` (game.cpp:3814),
 *    `Game::processLootItems` (game.cpp:3828),
 *    `Game::internalCollectManagedItems` (game.cpp:3850)
 *  - `Game::playerQuickLootCorpse` (game.cpp:3596),
 *    `Game::playerQuickLoot` (game.cpp:6428),
 *    `Game::playerLootAllCorpses` (game.cpp:6575),
 *    `Game::playerLootNearby` (game.cpp:6766)
 */
class GameQuickLootHandlersTest {

    private val center = Position(1000, 1000, 7)

    private class LootHarness(
        val game: Game,
        val player: Player,
        val texts: MutableList<Pair<MessageClass, String>>,
        val lootStats: MutableList<Pair<Int, Int>>,
        val bank: LongArray,
    )

    private fun newGame(): LootHarness {
        val game = Game(GameMap(), GameDispatcher())
        val player = Player(id = 1, name = "Looter", maxHealth = 150, health = 150, position = center)
        val texts = mutableListOf<Pair<MessageClass, String>>()
        val lootStats = mutableListOf<Pair<Int, Int>>()
        val bank = longArrayOf(0L)
        player.sendTextMessageTyped = { type, text -> texts.add(type to text) }
        game.sendLootStats = { _, item, count -> lootStats.add(item.id to count) }
        game.getBankBalance = { bank[0] }
        game.setBankBalance = { _, value -> bank[0] = value }
        game.sendCancelMessageText = { _, text -> texts.add(MessageClass.MESSAGE_FAILURE to text) }
        game.addPlayer(player)
        return LootHarness(game, player, texts, lootStats, bank)
    }

    private fun container(id: Int = 5000, capacity: Int = 20, corpse: Boolean = true): Container =
        Container(
            id = id,
            capacity = capacity,
            type = ItemType(id = id, name = "corpse-$id", isCorpse = corpse, isContainer = true),
        )

    private fun item(id: Int, count: Int = 1, type: ItemType? = ItemType(id = id, name = "item-$id", isMovable = true)): Item =
        Item(id = id, count = count, type = type)

    private fun lootContainer(harness: LootHarness, category: ObjectCategory_t = ObjectCategory_t.OBJECTCATEGORY_DEFAULT): Container {
        val loot = container(id = 6000, capacity = 20, corpse = false)
        harness.player.refreshManagedContainer(category, loot, true)
        return loot
    }

    // ----- getObjectCategory -----

    @Test
    fun `getObjectCategory classifies coins weapons equipment and item types`() {
        val harness = newGame()

        assertThat(harness.game.getObjectCategory(item(ITEM_GOLD_COIN))).isEqualTo(ObjectCategory_t.OBJECTCATEGORY_GOLD)

        val sword = item(2000, type = ItemType(id = 2000, name = "sword", weaponType = WeaponType_t.WEAPON_SWORD))
        assertThat(harness.game.getObjectCategory(sword)).isEqualTo(ObjectCategory_t.OBJECTCATEGORY_SWORDS)

        val helmet = item(2001, type = ItemType(id = 2001, name = "helmet", slotPosition = SLOTP_HEAD))
        assertThat(harness.game.getObjectCategory(helmet)).isEqualTo(ObjectCategory_t.OBJECTCATEGORY_HELMETS)

        val food = item(2002, type = ItemType(id = 2002, name = "food", type = ItemTypes_t.ITEM_TYPE_FOOD))
        assertThat(harness.game.getObjectCategory(food)).isEqualTo(ObjectCategory_t.OBJECTCATEGORY_FOOD)

        assertThat(harness.game.getObjectCategory(null)).isEqualTo(ObjectCategory_t.OBJECTCATEGORY_NONE)
    }

    // ----- managed container handlers -----

    @Test
    fun `playerSetManagedContainer rejects a gold pouch registered for another category`() {
        val harness = newGame()
        val pouch = container(id = Container.ITEM_GOLD_POUCH, corpse = false)
        harness.player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_GOLD, pouch, true)

        // The lookup resolves through STACKPOS_USEITEM, which reads the inventory.
        harness.player.addItem(pouch, org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK)

        harness.game.playerSetManagedContainer(
            playerId = 1,
            category = ObjectCategory_t.OBJECTCATEGORY_SWORDS,
            pos = Position(0xFFFF, org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK.value, 0),
            stackPosition = 0,
            itemId = Container.ITEM_GOLD_POUCH,
            isLootContainer = true,
        )

        assertThat(harness.player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_SWORDS, true)).isNull()
    }

    @Test
    fun `playerSetManagedContainer registers the inventory container`() {
        val harness = newGame()
        val backpack = container(id = 7000, corpse = false)
        harness.player.addItem(backpack, org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK)

        harness.game.playerSetManagedContainer(
            playerId = 1,
            category = ObjectCategory_t.OBJECTCATEGORY_DEFAULT,
            pos = Position(0xFFFF, org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK.value, 0),
            stackPosition = 0,
            itemId = 7000,
            isLootContainer = true,
        )

        assertThat(harness.player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)).isSameAs(backpack)
    }

    @Test
    fun `playerClearManagedContainer drops the registered container`() {
        val harness = newGame()
        val registered = lootContainer(harness, ObjectCategory_t.OBJECTCATEGORY_DEFAULT)

        harness.game.playerClearManagedContainer(1, ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)

        assertThat(harness.player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)).isNull()
        assertThat(registered.hasAttribute(ItemAttribute.QUICKLOOTCONTAINER)).isFalse()
    }

    @Test
    fun `playerOpenManagedContainer sends the registered container`() {
        val harness = newGame()
        val registered = lootContainer(harness)
        val sent = mutableListOf<Int>()
        harness.player.sendContainer = { cid, _, _, _ -> sent.add(cid) }

        harness.game.playerOpenManagedContainer(1, ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)

        assertThat(sent).containsExactly(registered.id and 0xFF)
    }

    // ----- findManagedContainer / fallback -----

    @Test
    fun `findManagedContainer falls back to the main backpack once`() {
        val harness = newGame()
        val backpack = container(id = 7000, corpse = false)
        harness.player.addItem(backpack, org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK)
        harness.player.quickLootFallbackToMainContainer = true
        harness.player.premium = true

        val first = harness.game.findManagedContainer(harness.player, false, ObjectCategory_t.OBJECTCATEGORY_SWORDS, true)
        assertThat(first.first).isSameAs(backpack)
        assertThat(first.second).isTrue()

        val other = Player(id = 2, name = "Other", maxHealth = 150, health = 150, position = center)
        val otherBackpack = container(id = 7001, corpse = false)
        other.addItem(otherBackpack, org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK)
        other.quickLootFallbackToMainContainer = true
        other.premium = true
        val consumed = harness.game.findManagedContainer(other, true, ObjectCategory_t.OBJECTCATEGORY_SWORDS, true)

        assertThat(consumed.first).isNull()
        assertThat(consumed.second).isTrue()
    }

    @Test
    fun `handleFallbackLogic rewinds the iterator to the main backpack`() {
        val harness = newGame()
        val backpack = container(id = 7000, corpse = false)
        harness.player.addItem(backpack, org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK)
        harness.player.quickLootFallbackToMainContainer = true
        val empty = container(id = 8000, corpse = false)

        val result = harness.game.handleFallbackLogic(harness.player, empty.iterator(), false)

        assertThat(result).isNotNull
        assertThat(result!!.first).isSameAs(backpack)
        assertThat(result.second.hasNext()).isFalse()
        assertThat(harness.game.handleFallbackLogic(harness.player, empty.iterator(), true)).isNull()
    }

    // ----- internalCollectManagedItems / processLootItems -----

    @Test
    fun `internalCollectManagedItems deposits autobanked coins`() {
        val harness = newGame()
        harness.game.autobank = true
        val corpse = container()
        val coins = item(ITEM_GOLD_COIN, count = 5, type = ItemType(id = ITEM_GOLD_COIN, name = "gold coin", isStackable = true, isMovable = true))
        corpse.addItem(coins)

        val ret = harness.game.internalCollectManagedItems(harness.player, coins, ObjectCategory_t.OBJECTCATEGORY_GOLD)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(corpse.items).doesNotContain(coins)
        assertThat(harness.bank[0]).isEqualTo(5L)
    }

    @Test
    fun `internalCollectManagedItems rejects items outside the accepted list`() {
        val harness = newGame()
        lootContainer(harness)
        harness.player.quickLootFilter = QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT
        harness.player.quickLootListItemIds = listOf(1234)
        val corpse = container()
        val other = item(9999)
        corpse.addItem(other)

        val ret = harness.game.internalCollectManagedItems(harness.player, other, ObjectCategory_t.OBJECTCATEGORY_OTHERS)

        assertThat(ret).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(corpse.items).contains(other)
    }

    @Test
    fun `internalCollectManagedItems moves the item into the managed container`() {
        val harness = newGame()
        val loot = lootContainer(harness)
        val corpse = container()
        val sword = item(2000)
        corpse.addItem(sword)

        val ret = harness.game.internalCollectManagedItems(harness.player, sword, ObjectCategory_t.OBJECTCATEGORY_SWORDS)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(loot.items).contains(sword)
        assertThat(corpse.items).doesNotContain(sword)
    }

    @Test
    fun `internalCollectManagedItems reports a missing managed container`() {
        val harness = newGame()
        val corpse = container()
        val sword = item(2000)
        corpse.addItem(sword)

        assertThat(harness.game.internalCollectManagedItems(harness.player, sword, ObjectCategory_t.OBJECTCATEGORY_SWORDS))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `processLootItems descends into a nested container when the target is full`() {
        val harness = newGame()
        val full = container(id = 6000, capacity = 1, corpse = false)
        full.addItem(item(1))
        val nested = container(id = 6001, capacity = 20, corpse = false)
        full.addItem(nested)
        val corpse = container()
        val sword = item(2000)
        corpse.addItem(sword)

        val ret = harness.game.processLootItems(harness.player, full, sword)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(nested.items).contains(sword)
    }

    @Test
    fun `processLootItems returns container-not-enough-room when no nested room exists`() {
        val harness = newGame()
        val full = container(id = 6000, capacity = 1, corpse = false)
        full.addItem(item(1))
        val corpse = container()
        val sword = item(2000)
        corpse.addItem(sword)

        assertThat(harness.game.processLootItems(harness.player, full, sword))
            .isEqualTo(ReturnValue.CONTAINERNOTENOUGHROOM)
        assertThat(corpse.items).contains(sword)
    }

    // ----- playerQuickLootCorpse -----

    @Test
    fun `playerQuickLootCorpse loots only the items selected by the accepted list`() {
        val harness = newGame()
        val loot = lootContainer(harness)
        harness.player.quickLootFilter = QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT
        harness.player.quickLootListItemIds = listOf(2000)
        val corpse = container()
        val listed = item(2000)
        val skipped = item(2001)
        corpse.addItem(listed)
        corpse.addItem(skipped)

        harness.game.playerQuickLootCorpse(harness.player, corpse, corpse.getTile()?.position ?: center)

        assertThat(loot.items).contains(listed)
        assertThat(corpse.items).contains(skipped)
        assertThat(harness.texts).anyMatch { it.second == "You looted some of the dropped items." }
    }

    @Test
    fun `playerQuickLootCorpse reports no loot for a fully skipped corpse`() {
        val harness = newGame()
        lootContainer(harness)
        harness.player.quickLootFilter = QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT
        harness.player.quickLootListItemIds = listOf(2000)
        val corpse = container()
        corpse.addItem(item(2001))

        harness.game.playerQuickLootCorpse(harness.player, corpse, center)

        assertThat(harness.texts).anyMatch { it.second == "You looted none of the dropped items." }
    }

    // ----- playerLootAllCorpses / playerLootNearby -----

    @Test
    fun `playerLootAllCorpses walks the tile corpses up to the cap`() {
        val harness = newGame()
        val loot = lootContainer(harness)
        val tile = harness.game.map.getOrCreateTile(center).also { it.ground = item(100) }
        val first = container(id = 3058)
        val second = container(id = 3058)
        first.addItem(item(2000))
        second.addItem(item(2001))
        tile.addItem(first)
        tile.addItem(second)

        harness.game.playerLootAllCorpses(harness.player, center, true)

        assertThat(loot.items.map { it.id }).contains(2000, 2001)
        assertThat(harness.texts).anyMatch { it.second == "You looted 2 corpses." }
    }

    @Test
    fun `playerLootNearby loots corpses on the eight neighbours`() {
        val harness = newGame()
        val loot = lootContainer(harness)
        val neighbour = Position(1001, 1000, 7)
        val tile = harness.game.map.getOrCreateTile(neighbour).also { it.ground = item(100) }
        val corpse = container(id = 3058)
        corpse.addItem(item(2000))
        tile.addItem(corpse)

        harness.game.playerLootNearby(1)

        assertThat(loot.items.map { it.id }).contains(2000)
    }

    @Test
    fun `playerLootNearby cancels with no lootable corpses nearby`() {
        val harness = newGame()

        harness.game.playerLootNearby(1)

        assertThat(harness.texts).anyMatch { it.second == "No lootable corpses nearby." }
    }
}
