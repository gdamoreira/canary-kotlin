package org.opentibia.canary.items.containers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `Inbox` (src/items/containers/inbox/inbox.cpp:16-77,
 * inbox.hpp:14-56).
 */
class InboxTest {

    private fun item(id: Int, pickupable: Boolean = true): Item =
        Item(id, type = ItemType(id, "item $id", isPickupable = pickupable))

    @Test
    fun `inbox only accepts adds with the NOLIMIT flag`() {
        // Inbox::queryAdd (inbox.cpp:25-27).
        val inbox = Inbox(Inbox.ITEM_INBOX)

        assertThat(inbox.queryAdd(INDEX_WHEREEVER, item(2376), 1, 0)).isEqualTo(ReturnValue.CONTAINERNOTENOUGHROOM)
        assertThat(inbox.queryAdd(INDEX_WHEREEVER, item(2376), 1, CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `inbox rejects itself and non pickupable items`() {
        val inbox = Inbox(Inbox.ITEM_INBOX)

        assertThat(inbox.queryAdd(INDEX_WHEREEVER, inbox, 1, CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(ReturnValue.THISISIMPOSSIBLE)
        assertThat(inbox.queryAdd(INDEX_WHEREEVER, item(2376, pickupable = false), 1, CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(ReturnValue.CANNOTPICKUP)
    }

    @Test
    fun `inbox caps items at maxInboxItems`() {
        // Inbox::queryAdd (inbox.cpp:42-53): `getItemHoldingCount() + addCount > maxInboxItems`.
        val inbox = Inbox(Inbox.ITEM_INBOX, maxInboxItemsConfig = 1)
        inbox.addItem(item(2376))

        assertThat(inbox.queryAdd(INDEX_WHEREEVER, item(2389), 1, CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(ReturnValue.DEPOTISFULL)
    }

    @Test
    fun `inbox counts a moved container with all of its contents`() {
        // inbox.cpp:42-48: a container addCount is `getItemHoldingCount() + 1`.
        val inbox = Inbox(Inbox.ITEM_INBOX)
        inbox.setMaxInboxItems(1)
        val bag = Container(1987, capacity = 8, type = ItemType(1987, "Bag", isContainer = true))
        bag.addItem(item(2376))

        assertThat(inbox.queryAdd(INDEX_WHEREEVER, bag, 1, CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(ReturnValue.DEPOTISFULL)
    }

    @Test
    fun `inbox remaining capacity is the configured limit minus the held items`() {
        // Inbox::getRemainingItemCapacity (inbox.hpp:26-29).
        val inbox = Inbox(Inbox.ITEM_INBOX, maxInboxItemsConfig = 3)

        assertThat(inbox.getMaxInboxItems()).isEqualTo(3)
        assertThat(inbox.getRemainingItemCapacity()).isEqualTo(3)

        inbox.setMaxInboxItems(1)
        inbox.addItem(item(2376))
        assertThat(inbox.getRemainingItemCapacity()).isEqualTo(0)
    }

    @Test
    fun `inbox defaults to the uint32 maximum when the config is zero`() {
        // inbox.cpp:18-19: `maxConfigInboxItem ? maxConfigInboxItem : uint32 max`.
        val inbox = Inbox(Inbox.ITEM_INBOX, maxInboxItemsConfig = 0)

        assertThat(inbox.getMaxInboxItems()).isEqualTo(Inbox.UINT32_MAX)
    }

    @Test
    fun `inbox is locked, paginated and flagged as an inbox`() {
        // Inbox constructor (inbox.cpp:16-17) and inbox.hpp:37-39.
        val inbox = Inbox(Inbox.ITEM_INBOX)

        assertThat(inbox.isInbox()).isTrue()
        assertThat(inbox.isUnlocked()).isFalse()
        assertThat(inbox.hasPagination()).isTrue()
        assertThat(inbox.capacity).isEqualTo(30)
        assertThat(inbox.canRemove()).isFalse()
        assertThat(inbox.isRemoved()).isFalse()
    }

    @Test
    fun `inbox parent skips the locker to the locker parent`() {
        // Inbox::getParent (inbox.cpp:71-77): `m_parent.lock()->getParent()`.
        val inbox = Inbox(Inbox.ITEM_INBOX)
        val locker = Container(3497, capacity = 4)
        val above = Container(3502, capacity = 4)
        locker.setParent(above)
        inbox.setParent(locker)

        assertThat(inbox.getRealParent()).isSameAs(locker)
        assertThat(inbox.getParent()).isSameAs(above)
    }
}
