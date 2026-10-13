package org.opentibia.canary.items.containers.depot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.AttrReadValue
import org.opentibia.canary.items.containers.Inbox
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `DepotChest` and `DepotLocker`
 * (src/items/containers/depot/depotchest.cpp:14-78, depotlocker.cpp:12-49)
 * and the `Player::getDepotChest`/`getDepotLocker` town locker structure
 * (src/creatures/players/player.cpp:1998-2055).
 */
class DepotTest {

    private val goldType = ItemType(id = 2148, name = "Gold Coin", isStackable = true, weight = 0.1, stackSize = 100)

    private fun coin(count: Int = 1): Item = Item(2148, count = count, type = goldType)

    private fun item(id: Int): Item = Item(id, type = ItemType(id, "item $id", isMovable = true))

    private fun player(id: Long): Player = Player(
        id = id,
        name = "Player $id",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `depot chest caps items at maxDepotItems`() {
        // DepotChest::queryAdd (depotchest.cpp:30-52): the parentless chest checks
        // `getItemHoldingCount() + addCount > maxDepotItems`.
        val chest = DepotChest(DepotChest.depotChestItemId(1))
        chest.setMaxDepotItems(2)
        chest.addItem(item(2376))
        chest.addItem(item(2389))

        assertThat(chest.queryAdd(INDEX_WHEREEVER, item(2390), 1, 0)).isEqualTo(ReturnValue.DEPOTISFULL)
        assertThat(chest.queryAdd(INDEX_WHEREEVER, item(2390), 1, CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `depot chest accepts a third item below the depot limit`() {
        val chest = DepotChest(DepotChest.depotChestItemId(1))
        chest.addItem(item(2376))
        chest.addItem(item(2389))

        assertThat(chest.queryAdd(INDEX_WHEREEVER, item(2390), 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `depot chest rejects an item owned by another player`() {
        // DepotChest::queryAdd (depotchest.cpp:26-28): `item->isOwner(actor)`.
        val chest = DepotChest(DepotChest.depotChestItemId(1))
        val owned = coin().apply { setAttribute(ItemAttribute.OWNER, 42L) }

        assertThat(chest.queryAdd(INDEX_WHEREEVER, owned, 1, 0, player(43))).isEqualTo(ReturnValue.ITEMISNOTYOURS)
        assertThat(chest.queryAdd(INDEX_WHEREEVER, owned, 1, 0, player(42))).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `depot chest is paginated and cannot be removed`() {
        // depotchest.cpp:17-18 / depotchest.hpp:57-62.
        val chest = DepotChest(DepotChest.depotChestItemId(1))

        assertThat(chest.hasPagination()).isTrue()
        assertThat(chest.capacity).isEqualTo(DepotChest.MAX_SIZE)
        assertThat(chest.isDepotChest()).isTrue()
        assertThat(chest.canRemove()).isFalse()
        assertThat(chest.isRemoved()).isFalse()
        assertThat(chest.getDepotChest()).isSameAs(chest)
    }

    @Test
    fun `town depot ids map to the Canary depot chest items`() {
        // Player::getDepotChest (player.cpp:2007-2016).
        assertThat(DepotChest.depotChestItemId(1)).isEqualTo(DepotChest.ITEM_DEPOT_NULL + 1)
        assertThat(DepotChest.depotChestItemId(17)).isEqualTo(DepotChest.ITEM_DEPOT_NULL + 17)
        assertThat(DepotChest.depotChestItemId(18)).isEqualTo(DepotChest.ITEM_DEPOT_XVIII)
        assertThat(DepotChest.depotChestItemId(19)).isEqualTo(DepotChest.ITEM_DEPOT_XIX)
        assertThat(DepotChest.depotChestItemId(20)).isEqualTo(DepotChest.ITEM_DEPOT_XX)
        assertThat(DepotChest.depotChestItemId(0)).isEqualTo(DepotChest.ITEM_DEPOT_XX)
    }

    @Test
    fun `depot locker refuses every add`() {
        // DepotLocker::queryAdd (depotlocker.cpp:25-27).
        val locker = DepotLocker(DepotLocker.ITEM_LOCKER, 4)

        assertThat(locker.queryAdd(INDEX_WHEREEVER, coin(), 1, 0)).isEqualTo(ReturnValue.NOTENOUGHROOM)
        assertThat(locker.canRemove()).isFalse()
        assertThat(locker.isRemoved()).isFalse()
    }

    @Test
    fun `depot locker reads and writes the depot id attribute`() {
        // DepotLocker::readAttr (depotlocker.cpp:15-23): ATTR_DEPOT_ID is a uint16.
        val locker = DepotLocker(DepotLocker.ITEM_LOCKER, 4)
        val stream = PropWriteStream()
        stream.writeU16(7)

        assertThat(locker.readAttr(DepotLocker.ATTR_DEPOT_ID, PropStream(stream.getStream())))
            .isEqualTo(AttrReadValue.ATTR_READ_CONTINUE)
        assertThat(locker.getDepotId()).isEqualTo(7)

        locker.setDepotId(0x1_2345)
        assertThat(locker.getDepotId()).isEqualTo(0x2345)
    }

    @Test
    fun `depot locker removes the player inbox from its item list`() {
        // DepotLocker::removeInbox (depotlocker.cpp:43-49) and the Player
        // destructor loop (player.cpp:176-183).
        val inbox = Inbox(Inbox.ITEM_INBOX)
        val locker = DepotLocker.create(depotId = 1, inbox = inbox, depotBoxes = 2, createStash = false)
        assertThat(locker.items).contains(inbox)

        locker.removeInbox(inbox)

        assertThat(locker.items).doesNotContain(inbox)
    }

    @Test
    fun `getDepotLocker builds the market inbox stash and depot box structure`() {
        // Player::getDepotLocker (player.cpp:2038-2055).
        val inbox = Inbox(Inbox.ITEM_INBOX)
        val locker = DepotLocker.create(depotId = 3, inbox = inbox, depotBoxes = 3, createStash = true)

        assertThat(locker.getDepotId()).isEqualTo(3)
        assertThat(locker.items.map { it.id }).contains(DepotLocker.ITEM_MARKET, Inbox.ITEM_INBOX, DepotLocker.ITEM_STASH)

        val depotContainer = locker.items.first { it.id == DepotLocker.ITEM_DEPOT } as Container
        assertThat(depotContainer.items).hasSize(3)
        assertThat(depotContainer.items.map { it.id }).containsExactlyInAnyOrder(
            DepotChest.depotChestItemId(1),
            DepotChest.depotChestItemId(2),
            DepotChest.depotChestItemId(3),
        )
        assertThat(depotContainer.items).allMatch { it is DepotChest }
        assertThat((depotContainer.items[0] as DepotChest).getParent()).isNull()
    }

    @Test
    fun `getDepotLocker without stash uses the three slot locker`() {
        // player.cpp:2038: `createStash ? 4 : 3`.
        val inbox = Inbox(Inbox.ITEM_INBOX)
        val locker = DepotLocker.create(depotId = 1, inbox = inbox, depotBoxes = 2, createStash = false)

        assertThat(locker.capacity).isEqualTo(3)
        assertThat(locker.items.map { it.id }).doesNotContain(DepotLocker.ITEM_STASH)
    }
}
