package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.cyclopedia.CyclopediaCharacterWriter.ItemSummaryEntry
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcribes the player-side item enumeration methods
 * (src/creatures/players/player.cpp:5488-5511, 5803-5859, 5972-6011,
 * 13214-13238):
 *
 * - `Player::getInventoryItemsId` (player.cpp:5488-5511)
 * - `Player::getStoreInboxItemsId` (player.cpp:5803-5819)
 * - `Player::getDepotChestItemsId` (player.cpp:5821-5841)
 * - `Player::getDepotInboxItemsId` (player.cpp:5843-5859)
 * - `Player::getAllItemTypeCount` (player.cpp:5972-5979)
 * - `Player::getAllSaleItemIdAndCount` (player.cpp:5982-6000)
 * - `Player::getAllItemTypeCountAndSubtype` (player.cpp:6002-6011)
 * - `Player::getSkillsEquipment` (player.cpp:13214-13238)
 * - `Player::getItemImbuementSkillEquipment` (player.cpp:73-85)
 *
 * The C++ `ItemsTierCountList` (`std::map<std::pair<uint16_t, uint8_t>,
 * uint32_t>`) is surfaced as the port's protocol-ready [ItemSummaryEntry]
 * list; the `tier` column is null exactly when
 * `Item::items[itemId].upgradeClassification == 0`, the condition under which
 * `ProtocolGame::sendCyclopediaCharacterItemSummary` (protocolgame.cpp:4967)
 * skips the tier byte.
 */
class PlayerItemSummaryStateTest {

    private fun newPlayer(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun newContainer(id: Int, name: String): Container =
        Container(id, 8, ItemType(id = id, name = name, isContainer = true))

    private fun newItem(
        id: Int,
        name: String = "Item",
        count: Int = 1,
        tier: Int = 0,
        classification: Int = 0,
    ): Item {
        val item = Item(
            id,
            count,
            ItemType(id = id, name = name, isStackable = true, upgradeClassification = classification),
        )
        if (tier > 0) {
            item.setTier(tier)
        }
        return item
    }

    private fun entries(entries: List<ItemSummaryEntry>): List<Triple<Int, Int?, Long>> =
        entries.map { Triple(it.itemId, it.tier, it.count) }

    // --- Player::getInventoryItemsId ---------------------------------------------

    @Test
    fun `getInventoryItemsId is empty without items`() {
        // player.cpp:5488-5511.
        assertThat(newPlayer().getInventoryItemsId()).isEmpty()
    }

    @Test
    fun `getInventoryItemsId counts top-level items by id and tier`() {
        // inventoryCache[{ item->getID(), item->getTier() }] += item->getItemAmount() (5501).
        val player = newPlayer()
        assertThat(player.addItem(newItem(2160, "Crystal Coin", count = 100), Slots_t.CONST_SLOT_BACKPACK))
            .isEqualTo(ReturnValue.NOERROR)
        assertThat(
            player.addItem(
                newItem(8924, "Sword", tier = 3, classification = 10),
                Slots_t.CONST_SLOT_LEFT,
            ),
        ).isEqualTo(ReturnValue.NOERROR)

        assertThat(entries(player.getInventoryItemsId())).containsExactly(
            Triple(2160, null, 100L),
            Triple(8924, 3, 1L),
        )
    }

    @Test
    fun `getInventoryItemsId merges the same id and tier and counts nested containers`() {
        // The `container->getItems(true)` walk (5504-5510).
        val player = newPlayer()
        val backpack = newContainer(2854, "Backpack")
        backpack.addItem(newItem(2160, "Crystal Coin", count = 25))
        val bag = newContainer(2865, "Bag")
        bag.addItem(newItem(2160, "Crystal Coin", count = 5))
        backpack.addItem(bag)
        backpack.addItem(newItem(3031, "Gold Coin", count = 42))
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)

        assertThat(entries(player.getInventoryItemsId())).containsExactly(
            Triple(2160, null, 30L),
            Triple(2854, null, 1L),
            Triple(2865, null, 1L),
            Triple(3031, null, 42L),
        )
    }

    @Test
    fun `getInventoryItemsId skips the store inbox contents when ignored`() {
        // `isStoreInbox` (5497-5510): the inbox item itself is never counted.
        val player = newPlayer()
        val storeInbox = newContainer(Container.ITEM_STORE_INBOX, "Store Inbox")
        storeInbox.addItem(newItem(2160, "Crystal Coin", count = 7))
        assertThat(player.addItem(storeInbox, Slots_t.CONST_SLOT_STORE_INBOX)).isEqualTo(ReturnValue.NOERROR)

        assertThat(entries(player.getInventoryItemsId(ignoreStoreInbox = true))).isEmpty()
        assertThat(entries(player.getInventoryItemsId(ignoreStoreInbox = false))).containsExactly(
            Triple(2160, null, 7L),
        )
    }

    // --- Player::getStoreInboxItemsId --------------------------------------------

    @Test
    fun `getStoreInboxItemsId walks the store inbox recursively`() {
        // player.cpp:5803-5819.
        val player = newPlayer()
        val storeInbox = newContainer(Container.ITEM_STORE_INBOX, "Store Inbox")
        val bag = newContainer(2865, "Bag")
        bag.addItem(newItem(2160, "Crystal Coin", count = 3, tier = 2, classification = 4))
        storeInbox.addItem(bag)
        storeInbox.addItem(newItem(7590, "Potion", count = 10))
        assertThat(player.addItem(storeInbox, Slots_t.CONST_SLOT_STORE_INBOX)).isEqualTo(ReturnValue.NOERROR)

        assertThat(entries(player.getStoreInboxItemsId())).containsExactly(
            Triple(2160, 2, 3L),
            Triple(2865, null, 1L),
            Triple(7590, null, 10L),
        )
    }

    @Test
    fun `getStoreInboxItemsId is empty without a store inbox`() {
        // `if (container)` guard (5807).
        assertThat(newPlayer().getStoreInboxItemsId()).isEmpty()
    }

    // --- Player::getDepotChestItemsId --------------------------------------------

    @Test
    fun `getDepotChestItemsId counts every depot chest container`() {
        // player.cpp:5821-5841 over `depotChests`.
        val player = newPlayer()
        val chest = newContainer(2594, "Depot Chest")
        val inner = newContainer(2854, "Backpack")
        inner.addItem(newItem(2160, "Crystal Coin", count = 11))
        chest.addItem(inner)

        assertThat(entries(player.getDepotChestItemsId(depotChests = { listOf(chest) }))).containsExactly(
            Triple(2160, null, 11L),
            Triple(2854, null, 1L),
        )
    }

    @Test
    fun `getDepotChestItemsId skips depot entries without a container`() {
        // `depot->getContainer()` null (5826-5828).
        val player = newPlayer()
        assertThat(entries(player.getDepotChestItemsId(depotChests = { listOf(newItem(2594)) }))).isEmpty()
    }

    // --- Player::getDepotInboxItemsId --------------------------------------------

    @Test
    fun `getDepotInboxItemsId walks the inbox recursively`() {
        // player.cpp:5843-5859 (`getInbox()`).
        val player = newPlayer()
        player.inbox.addItem(newItem(2160, "Crystal Coin", count = 9))
        player.inbox.addItem(newItem(2854, "Backpack"))

        assertThat(entries(player.getDepotInboxItemsId())).containsExactly(
            Triple(2160, null, 9L),
            Triple(2854, null, 1L),
        )
    }

    // --- Player::getAllItemTypeCount ---------------------------------------------

    @Test
    fun `getAllItemTypeCount folds countByType over the inventory`() {
        // player.cpp:5972-5979: countMap[item->getID()] += Item::countByType(item, -1).
        val player = newPlayer()
        val backpack = newContainer(2854, "Backpack")
        backpack.addItem(newItem(2160, "Crystal Coin", count = 100))
        backpack.addItem(newItem(2160, "Crystal Coin", count = 50))
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)

        val countMap = mutableMapOf<Int, Int>()
        val result = player.getAllItemTypeCount(countMap)

        assertThat(result[2160]).isEqualTo(150)
        assertThat(result[2854]).isEqualTo(1)
    }

    // --- Player::getAllSaleItemIdAndCount ----------------------------------------

    @Test
    fun `getAllSaleItemIdAndCount skips non-marketable container carriers`() {
        // player.cpp:5982-6000: a non-empty container without marketable
        // attributes is skipped, a gold pouch is always counted.
        val player = newPlayer()
        val saleable = newItem(3556, "Frozen Plate")
        val containerWithItem = newContainer(2854, "Backpack")
        containerWithItem.addItem(newItem(3031, "Gold Coin"))
        val fakePouch = newContainer(Container.ITEM_GOLD_POUCH, "Gold Pouch")
        fakePouch.addItem(newItem(3031, "Gold Coin", count = 2))

        assertThat(player.addItem(saleable, Slots_t.CONST_SLOT_ARMOR)).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(containerWithItem, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(fakePouch, Slots_t.CONST_SLOT_AMMO)).isEqualTo(ReturnValue.NOERROR)

        val countMap = mutableMapOf<Int, Int>()
        player.getAllSaleItemIdAndCount(countMap)

        assertThat(countMap).containsEntry(3556, 1)
        assertThat(countMap).containsEntry(Container.ITEM_GOLD_POUCH, 1)
        // The non-empty backpack itself fails the market check (5993-5997), but
        // `getAllInventoryItems(false, true)` still enumerated its nested tier-0
        // coins: 1 inside the backpack, 2 inside the gold pouch.
        assertThat(countMap).containsEntry(3031, 3)
        assertThat(countMap.keys).doesNotContain(2854)
    }

    @Test
    fun `getAllSaleItemIdAndCount counts normal top-level items`() {
        // `hasMarketAttributes()` true for an attribute-free item (5987).
        val player = newPlayer()
        assertThat(player.addItem(newItem(2160, "Crystal Coin", count = 30), Slots_t.CONST_SLOT_BACKPACK))
            .isEqualTo(ReturnValue.NOERROR)

        val countMap = mutableMapOf<Int, Int>()
        player.getAllSaleItemIdAndCount(countMap)

        assertThat(countMap).containsEntry(2160, 30)
    }

    // --- Player::getAllItemTypeCountAndSubtype -----------------------------------

    @Test
    fun `getAllItemTypeCountAndSubtype keys fluid containers by fluid type`() {
        // player.cpp:6002-6011: itemId | (FLUIDTYPE << 16).
        val player = newPlayer()
        val fluid = Item(2006, 1, ItemType(id = 2006, name = "Flask", isFluidContainer = true))
        fluid.setAttribute(ItemAttribute.FLUIDTYPE, 5L)
        assertThat(player.addItem(fluid, Slots_t.CONST_SLOT_LEFT)).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(newItem(2160, "Crystal Coin", count = 12), Slots_t.CONST_SLOT_BACKPACK))
            .isEqualTo(ReturnValue.NOERROR)

        val countMap = mutableMapOf<Int, Int>()
        player.getAllItemTypeCountAndSubtype(countMap)

        assertThat(countMap).containsEntry(2006 or (5 shl 16), 1)
        assertThat(countMap).containsEntry(2160, 12)
    }

    // --- Player::getSkillsEquipment ----------------------------------------------

    @Test
    fun `getSkillsEquipment sums item abilities per skill`() {
        // player.cpp:13214-13238: `itemType.abilities->skills[skill] / 10000.0`.
        val player = newPlayer()
        val sword = Item(
            8924,
            1,
            ItemType(id = 8924, name = "Sword").also { type ->
                type.abilities = Abilities().also { it.skills[skills_t.SKILL_SWORD.value] = 15000 }
            },
        )
        assertThat(player.addItem(sword, Slots_t.CONST_SLOT_LEFT)).isEqualTo(ReturnValue.NOERROR)

        val equipment = player.getSkillsEquipment()

        assertThat(equipment).hasSize(skills_t.LAST + 1)
        assertThat(equipment[skills_t.SKILL_SWORD.value].equipment).isEqualTo(1.5)
        assertThat(equipment[skills_t.SKILL_AXE.value].equipment).isEqualTo(0.0)
    }

    @Test
    fun `getSkillsEquipment folds imbuement skill bonuses`() {
        // `getItemImbuementSkillEquipment` (player.cpp:73-85):
        // imbuementInfo.imbuement->skills[skill] / 10000.0.
        val imbuements = Imbuements()
        assertThat(
            imbuements.loadFromXml(
                """
                <imbuements>
                  <base id="1" name="Basic" price="0" protectionPrice="0" removecost="0" duration="3600" percent="100"/>
                  <category id="1" name="Sword" agressive="true"/>
                  <imbuement base="1" iconid="1" category="1" name="Sword Power">
                    <attribute key="effect" type="skill" value="sword" bonus="8000"/>
                  </imbuement>
                </imbuements>
                """.trimIndent(),
            ),
        ).isTrue()

        val registered = imbuements.getImbuement(1)
        assertThat(registered).isNotNull()
        assertThat(registered!!.skills[skills_t.SKILL_SWORD.value]).isEqualTo(8000)

        val player = newPlayer()
        val sword = Item(8924, 1, ItemType(id = 8924, name = "Sword").also { it.imbuementSlot = 2 })
        sword.imbuementRegistry = imbuements
        sword.setImbuement(slot = 0, imbuementId = 1, duration = 3600)
        assertThat(sword.getImbuementSlot()).isEqualTo(2)
        assertThat(getItemImbuementSkillEquipment(sword, skills_t.SKILL_SWORD.value)).isEqualTo(0.8)
        assertThat(player.addItem(sword, Slots_t.CONST_SLOT_LEFT)).isEqualTo(ReturnValue.NOERROR)

        val equipment = player.getSkillsEquipment()

        assertThat(equipment[skills_t.SKILL_SWORD.value].equipment).isEqualTo(0.0)
        assertThat(equipment[skills_t.SKILL_SWORD.value].imbuement).isEqualTo(0.8)
    }

    @Test
    fun `getSkillsEquipment ignores slots past ammo`() {
        // `slot <= CONST_SLOT_AMMO` (13219).
        val player = newPlayer()
        val storeItem = Item(
            8924,
            1,
            ItemType(id = 8924, name = "Sword").also { type ->
                type.abilities = Abilities().also { it.skills[skills_t.SKILL_SWORD.value] = 9999 }
            },
        )
        assertThat(player.addItem(storeItem, Slots_t.CONST_SLOT_STORE_INBOX)).isEqualTo(ReturnValue.NOERROR)

        val equipment = player.getSkillsEquipment()

        assertThat(equipment[skills_t.SKILL_SWORD.value].equipment).isEqualTo(0.0)
    }
}
