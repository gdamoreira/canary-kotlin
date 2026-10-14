package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcribes the auto-loot/loot-pouch and managed-container hooks
 * (src/creatures/players/player.cpp:1713-1732, 5692-5708, 12864-12885):
 *
 * - `Player::checkAutoLoot` (player.cpp:5692-5708)
 * - `Player::getLootPouch` (player.cpp:12864-12885)
 * - `Player::setMainBackpackUnassigned` (player.cpp:1713-1732)
 *
 * `g_configManager()` booleans, the VIP check, the `kv()` feature lookup and
 * the `ProtocolGame::send*` calls are call-site seams per AGENTS.md 1.1.
 */
class PlayerQuickLootStateTest {

    private fun newPlayer(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun newContainer(id: Int, name: String): Container =
        Container(id, 1, ItemType(id = id, name = name, isContainer = true))

    // --- Player::checkAutoLoot ---------------------------------------------------

    @Test
    fun `checkAutoLoot is disabled when the config toggle is off`() {
        // `if (!g_configManager().getBoolean(AUTOLOOT)) return false;` (5693-5695).
        val player = newPlayer()
        assertThat(
            player.checkAutoLoot(isBoss = false, autoLootEnabled = false, autolootFeature = { 2 }),
        ).isFalse()
    }

    @Test
    fun `checkAutoLoot honors the vip-only gate`() {
        // player.cpp:5696-5698.
        val player = newPlayer()
        assertThat(
            player.checkAutoLoot(
                isBoss = false,
                autoLootEnabled = true,
                vipSystemEnabled = true,
                vipAutoLootVipOnly = true,
                isVip = { false },
                autolootFeature = { 2 },
            ),
        ).isFalse()

        assertThat(
            player.checkAutoLoot(
                isBoss = false,
                autoLootEnabled = true,
                vipSystemEnabled = true,
                vipAutoLootVipOnly = true,
                isVip = { true },
                autolootFeature = { 2 },
            ),
        ).isTrue()
    }

    @Test
    fun `checkAutoLoot decodes the feature value`() {
        // player.cpp:5700-5706: 2 -> all, 1 -> non-boss, otherwise off.
        val player = newPlayer()
        fun autoLoot(value: Long?, isBoss: Boolean) = player.checkAutoLoot(
            isBoss = isBoss,
            autoLootEnabled = true,
            autolootFeature = { value },
        )

        assertThat(autoLoot(null, false)).isFalse()
        assertThat(autoLoot(0, false)).isFalse()
        assertThat(autoLoot(3, false)).isFalse()
        assertThat(autoLoot(2, isBoss = true)).isTrue()
        assertThat(autoLoot(1, isBoss = false)).isTrue()
        assertThat(autoLoot(1, isBoss = true)).isFalse()
    }

    // --- Player::getLootPouch ----------------------------------------------------

    @Test
    fun `getLootPouch finds the gold pouch inside the backpack`() {
        // player.cpp:12864-12885.
        val player = newPlayer()
        val backpack = newContainer(1, "Backpack")
        val pouch = newContainer(Container.ITEM_GOLD_POUCH, "Gold Pouch")
        backpack.addItem(pouch)
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)

        assertThat(player.getLootPouch()).isSameAs(pouch)
    }

    @Test
    fun `getLootPouch returns null without a gold pouch`() {
        // `inventoryItems.empty()` (12872-12874).
        val player = newPlayer()
        assertThat(player.getLootPouch()).isNull()
    }

    @Test
    fun `getLootPouch ignores a pouch item without a container`() {
        // `containerItem->getContainer()` null (12879-12882).
        val player = newPlayer()
        val backpack = newContainer(1, "Backpack")
        backpack.addItem(Item(Container.ITEM_GOLD_POUCH, 1, ItemType(id = Container.ITEM_GOLD_POUCH, name = "Gold Pouch")))
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getLootPouch()).isNull()
    }

    @Test
    fun `getLootPouch rejects a player-group character inside a parent item`() {
        // player.cpp:12866-12869: player group + non-inbox parent item.
        val player = newPlayer()
        val backpack = newContainer(1, "Backpack")
        val pouch = newContainer(Container.ITEM_GOLD_POUCH, "Gold Pouch")
        backpack.addItem(pouch)
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(
            player.getLootPouch(
                isPlayerGroup = { true },
                parentItem = { Item(2, 1, ItemType(id = 2, name = "Crate")) },
            ),
        ).isNull()
    }

    @Test
    fun `getLootPouch allows an access character inside the store inbox`() {
        // The `parentItem->getID() != ITEM_STORE_INBOX` exception (12867).
        val player = newPlayer()
        val backpack = newContainer(1, "Backpack")
        val pouch = newContainer(Container.ITEM_GOLD_POUCH, "Gold Pouch")
        backpack.addItem(pouch)
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(
            player.getLootPouch(
                isPlayerGroup = { true },
                parentItem = { Item(Container.ITEM_STORE_INBOX, 1, ItemType(id = Container.ITEM_STORE_INBOX, name = "Store Inbox")) },
            ),
        ).isSameAs(pouch)
    }

    // --- Player::setMainBackpackUnassigned --------------------------------------

    @Test
    fun `setMainBackpackUnassigned assigns an unmanaged container to both slots`() {
        // player.cpp:1713-1732.
        val player = newPlayer()
        val backpack = newContainer(1, "Backpack")
        val inventoryUpdates = mutableListOf<Container>()
        var lootSends = 0

        player.setMainBackpackUnassigned(
            backpack,
            sendInventoryItem = { slot, container ->
                assertThat(slot).isEqualTo(Slots_t.CONST_SLOT_BACKPACK)
                inventoryUpdates.add(container)
            },
            sendLootContainers = { lootSends++ },
        )

        assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)).isSameAs(backpack)
        assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, false)).isSameAs(backpack)
        assertThat(inventoryUpdates).containsExactly(backpack)
        assertThat(lootSends).isEqualTo(1)
    }

    @Test
    fun `setMainBackpackUnassigned only fills the missing managed slot`() {
        // The loop registers the absent loot/obtain container (1720-1727).
        val player = newPlayer()
        val lootContainer = newContainer(1, "Loot Backpack")
        player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, lootContainer, true)
        val backpack = newContainer(2, "Backpack")
        var inventorySends = 0
        var lootSends = 0

        player.setMainBackpackUnassigned(
            backpack,
            sendInventoryItem = { _, _ -> inventorySends++ },
            sendLootContainers = { lootSends++ },
        )

        assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)).isSameAs(lootContainer)
        assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, false)).isSameAs(backpack)
        assertThat(inventorySends).isEqualTo(1)
        assertThat(lootSends).isEqualTo(1)
    }

    @Test
    fun `setMainBackpackUnassigned with both slots managed sends nothing`() {
        // `toSendInventoryUpdate` stays false (1722-1732).
        val player = newPlayer()
        val lootContainer = newContainer(1, "Loot Backpack")
        val obtainContainer = newContainer(2, "Obtain Backpack")
        player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, lootContainer, true)
        player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, obtainContainer, false)
        var sends = 0

        player.setMainBackpackUnassigned(
            obtainContainer,
            sendInventoryItem = { _, _ -> sends++ },
            sendLootContainers = { sends++ },
        )

        assertThat(sends).isZero()
        assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, false)).isSameAs(obtainContainer)
    }

    @Test
    fun `setMainBackpackUnassigned with a null container is a no-op`() {
        // `if (!container) return;` (1714-1716).
        val player = newPlayer()
        var sends = 0

        player.setMainBackpackUnassigned(null, sendInventoryItem = { _, _ -> sends++ }, sendLootContainers = { sends++ })

        assertThat(sends).isZero()
        assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, true)).isNull()
    }
}
