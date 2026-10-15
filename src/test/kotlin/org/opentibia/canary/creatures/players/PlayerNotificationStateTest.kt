package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.CylinderLink
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player inventory-change notification producers
 * (src/creatures/players/player.cpp:8664-8760):
 *
 * - `Player::postAddNotification` (player.cpp:8664-8703)
 * - `Player::postRemoveNotification` (player.cpp:8705-8760)
 *
 * The `g_moveEvents().onPlayerEquip`/`onPlayerDeEquip` dispatch, the
 * `Player::updateInventoryWeight`/`updateItemsLight`/`sendInventoryIds`/
 * `sendStats` refresh block, `Player::onSendContainer`, the
 * `Container::getHoldingPlayer` lookup, the loot/auto-close container helpers
 * and the shop-owner/sale-schedule reads are call-site seams per AGENTS.md 1.1.
 */
class PlayerNotificationStateTest {

    private fun player(id: Long = 1L, name: String = "Hero"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun container(id: Int = 1987, capacity: Int = 8): Container = Container(id, capacity)

    // --- postAddNotification (player.cpp:8664-8703) ------------------------------

    @Test
    fun `postAddNotification runs the equip hook and the state update for the owner link`() {
        val player = player()
        val item = Item(2160)
        val equipped = mutableListOf<Triple<Item, Slots_t, Boolean>>()
        val calls = mutableListOf<String>()

        player.postAddNotification(
            thing = item,
            oldParent = null,
            index = Slots_t.CONST_SLOT_HEAD.value,
            link = CylinderLink.LINK_OWNER,
            onPlayerEquip = { _, hookedItem, slot, isCheck ->
                equipped.add(Triple(hookedItem, slot, isCheck))
            },
            updateInventoryWeight = { calls.add("weight") },
            updateItemsLight = { calls.add("light") },
            sendInventoryIds = { calls.add("ids") },
            sendStats = { calls.add("stats") },
            hasShopOwner = { true },
            isSaleUpdateScheduled = { false },
            updateSaleShopList = { saleItem -> calls.add("sale:${saleItem.id}") },
        )

        assertThat(equipped).containsExactly(Triple(item, Slots_t.CONST_SLOT_HEAD, false))
        assertThat(calls).containsExactly("weight", "light", "ids", "stats", "sale:2160")
    }

    @Test
    fun `postAddNotification refreshes state for the top-parent link without the equip hook`() {
        val player = player()
        val calls = mutableListOf<String>()

        player.postAddNotification(
            thing = Item(2160),
            oldParent = null,
            index = 0,
            link = CylinderLink.LINK_TOPPARENT,
            onPlayerEquip = { _, _, _, _ -> calls.add("equip") },
            updateInventoryWeight = { calls.add("weight") },
            updateItemsLight = { calls.add("light") },
            sendInventoryIds = { calls.add("ids") },
            sendStats = { calls.add("stats") },
            hasShopOwner = { true },
            isSaleUpdateScheduled = { false },
            updateSaleShopList = { saleItem -> calls.add("sale:${saleItem.id}") },
        )

        assertThat(calls).containsExactly("weight", "light", "ids", "stats", "sale:2160")
    }

    @Test
    fun `postAddNotification keeps the parent link to the sale tail only`() {
        val player = player()
        val calls = mutableListOf<String>()

        player.postAddNotification(
            thing = Item(2160),
            oldParent = null,
            index = 0,
            link = CylinderLink.LINK_PARENT,
            updateInventoryWeight = { calls.add("weight") },
            updateItemsLight = { calls.add("light") },
            sendInventoryIds = { calls.add("ids") },
            sendStats = { calls.add("stats") },
            hasShopOwner = { true },
            isSaleUpdateScheduled = { false },
            updateSaleShopList = { calls.add("sale") },
        )

        // `requireListUpdate` starts true (player.cpp:8673), so the shop tail
        // still runs for LINK_PARENT even though the refresh block is skipped.
        assertThat(calls).containsExactly("sale")
    }

    @Test
    fun `postAddNotification returns after the equip hook while batching`() {
        val player = player()
        player.beginBatchUpdate()
        val calls = mutableListOf<String>()

        player.postAddNotification(
            thing = Item(2160),
            oldParent = null,
            index = Slots_t.CONST_SLOT_HEAD.value,
            link = CylinderLink.LINK_OWNER,
            onPlayerEquip = { _, _, _, _ -> calls.add("equip") },
            updateInventoryWeight = { calls.add("weight") },
            sendInventoryIds = { calls.add("ids") },
            sendStats = { calls.add("stats") },
            hasShopOwner = { true },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(calls).containsExactly("equip")
    }

    @Test
    fun `postAddNotification sends the added container`() {
        val player = player()
        val bag = container()
        val sent = mutableListOf<Container>()

        player.postAddNotification(
            thing = bag,
            oldParent = null,
            index = Slots_t.CONST_SLOT_BACKPACK.value,
            link = CylinderLink.LINK_OWNER,
            onSendContainer = { sent.add(it) },
        )

        assertThat(sent).containsExactly(bag)
    }

    @Test
    fun `postAddNotification skips the sale refresh when the old parent is held by the player`() {
        val player = player()
        val oldParent = container()
        val calls = mutableListOf<String>()

        player.postAddNotification(
            thing = Item(2160),
            oldParent = oldParent,
            index = 0,
            link = CylinderLink.LINK_TOPPARENT,
            getHoldingPlayer = { holder -> if (holder === oldParent) player else null },
            hasShopOwner = { true },
            isSaleUpdateScheduled = { false },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(calls).isEmpty()
    }

    @Test
    fun `postAddNotification refreshes the sale list for a foreign old parent`() {
        val player = player()
        val oldParent = container()
        val calls = mutableListOf<String>()

        player.postAddNotification(
            thing = Item(2160),
            oldParent = oldParent,
            index = 0,
            link = CylinderLink.LINK_TOPPARENT,
            getHoldingPlayer = { null },
            hasShopOwner = { true },
            isSaleUpdateScheduled = { false },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(calls).containsExactly("sale")
    }

    @Test
    fun `postAddNotification skips the sale refresh while one is scheduled`() {
        val player = player()
        val calls = mutableListOf<String>()

        player.postAddNotification(
            thing = Item(2160),
            oldParent = null,
            index = 0,
            hasShopOwner = { true },
            isSaleUpdateScheduled = { true },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(calls).isEmpty()
    }

    @Test
    fun `postAddNotification closes containers out of range for the own creature`() {
        val player = player()
        var closed = 0

        player.postAddNotification(
            thing = player,
            oldParent = null,
            index = 0,
            closeContainersOutOfRange = { closed++ },
        )

        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `postAddNotification ignores another creature`() {
        val player = player()
        val other = player(id = 2L, name = "Mate")
        var closed = 0

        player.postAddNotification(
            thing = other,
            oldParent = null,
            index = 0,
            closeContainersOutOfRange = { closed++ },
        )

        assertThat(closed).isZero()
    }

    // --- postRemoveNotification (player.cpp:8705-8760) ---------------------------

    @Test
    fun `postRemoveNotification returns early for a null thing`() {
        val player = player()
        val calls = mutableListOf<String>()

        player.postRemoveNotification(
            thing = null,
            newParent = null,
            index = 0,
            onPlayerDeEquip = { _, _, _ -> calls.add("deequip"); 1 },
            updateInventoryWeight = { calls.add("weight") },
            hasShopOwner = { true },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(calls).isEmpty()
    }

    @Test
    fun `postRemoveNotification stops when the de-equip hook vetoes`() {
        val player = player()
        val item = Item(2160)
        val deEquipped = mutableListOf<Triple<Item, Slots_t, Int>>()
        val calls = mutableListOf<String>()

        player.postRemoveNotification(
            thing = item,
            newParent = null,
            index = Slots_t.CONST_SLOT_HEAD.value,
            link = CylinderLink.LINK_OWNER,
            onPlayerDeEquip = { _, hookedItem, slot ->
                deEquipped.add(Triple(hookedItem, slot, 0))
                0
            },
            updateInventoryWeight = { calls.add("weight") },
            sendInventoryIds = { calls.add("ids") },
            hasShopOwner = { true },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(deEquipped).containsExactly(Triple(item, Slots_t.CONST_SLOT_HEAD, 0))
        assertThat(calls).isEmpty()
    }

    @Test
    fun `postRemoveNotification refreshes state and the shop tail when the hook allows it`() {
        val player = player()
        val deEquipped = mutableListOf<Slots_t>()
        val calls = mutableListOf<String>()

        player.postRemoveNotification(
            thing = Item(2160),
            newParent = null,
            index = Slots_t.CONST_SLOT_HEAD.value,
            link = CylinderLink.LINK_OWNER,
            onPlayerDeEquip = { _, _, slot -> deEquipped.add(slot); 1 },
            updateInventoryWeight = { calls.add("weight") },
            updateItemsLight = { calls.add("light") },
            sendInventoryIds = { calls.add("ids") },
            sendStats = { calls.add("stats") },
            hasShopOwner = { true },
            isSaleUpdateScheduled = { false },
            updateSaleShopList = { saleItem -> calls.add("sale:${saleItem.id}") },
        )

        assertThat(deEquipped).containsExactly(Slots_t.CONST_SLOT_HEAD)
        assertThat(calls).containsExactly("weight", "light", "ids", "stats", "sale:2160")
    }

    @Test
    fun `postRemoveNotification returns after the de-equip hook while batching`() {
        val player = player()
        player.beginBatchUpdate()
        val calls = mutableListOf<String>()

        player.postRemoveNotification(
            thing = Item(2160),
            newParent = null,
            index = Slots_t.CONST_SLOT_HEAD.value,
            link = CylinderLink.LINK_OWNER,
            onPlayerDeEquip = { _, _, _ -> calls.add("deequip"); 1 },
            updateInventoryWeight = { calls.add("weight") },
            sendInventoryIds = { calls.add("ids") },
        )

        assertThat(calls).containsExactly("deequip")
    }

    @Test
    fun `postRemoveNotification loot-checks and auto-closes an out-of-range container`() {
        val player = player()
        val bag = container()
        val lootChecked = mutableListOf<Container>()
        val autoClosed = mutableListOf<Container>()
        val sent = mutableListOf<Container>()

        player.postRemoveNotification(
            thing = bag,
            newParent = null,
            index = 0,
            link = CylinderLink.LINK_PARENT,
            checkLootContainers = { lootChecked.add(it) },
            shouldCloseContainer = { true },
            autoCloseContainers = { autoClosed.add(it) },
            onSendContainer = { sent.add(it) },
        )

        assertThat(lootChecked).containsExactly(bag)
        assertThat(autoClosed).containsExactly(bag)
        assertThat(sent).isEmpty()
    }

    @Test
    fun `postRemoveNotification sends a container that stays open`() {
        val player = player()
        val bag = container()
        val lootChecked = mutableListOf<Container>()
        val autoClosed = mutableListOf<Container>()
        val sent = mutableListOf<Container>()

        player.postRemoveNotification(
            thing = bag,
            newParent = null,
            index = 0,
            link = CylinderLink.LINK_PARENT,
            checkLootContainers = { lootChecked.add(it) },
            shouldCloseContainer = { false },
            autoCloseContainers = { autoClosed.add(it) },
            onSendContainer = { sent.add(it) },
        )

        assertThat(lootChecked).containsExactly(bag)
        assertThat(autoClosed).isEmpty()
        assertThat(sent).containsExactly(bag)
    }

    @Test
    fun `postRemoveNotification forces the sale refresh for a tiered item`() {
        val player = player()
        val newParent = container()
        val item = Item(2160, 1, ItemType(id = 2160, name = "magic sword", upgradeClassification = 1)).apply {
            setTier(1)
        }
        val calls = mutableListOf<String>()

        player.postRemoveNotification(
            thing = item,
            newParent = newParent,
            index = 0,
            link = CylinderLink.LINK_TOPPARENT,
            getHoldingPlayer = { holder -> if (holder === newParent) player else null },
            hasShopOwner = { true },
            isSaleUpdateScheduled = { false },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(item.getTier()).isPositive()
        assertThat(calls).containsExactly("sale")
    }

    @Test
    fun `postRemoveNotification keeps a plain item out of the sale refresh for an own parent`() {
        val player = player()
        val newParent = container()
        val calls = mutableListOf<String>()

        player.postRemoveNotification(
            thing = Item(2160),
            newParent = newParent,
            index = 0,
            link = CylinderLink.LINK_TOPPARENT,
            getHoldingPlayer = { holder -> if (holder === newParent) player else null },
            hasShopOwner = { true },
            isSaleUpdateScheduled = { false },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(calls).isEmpty()
    }
}
