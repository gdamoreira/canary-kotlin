package org.opentibia.canary.map.house

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass

/**
 * Transcript tests for the auction/transfer half of `House`
 * (src/map/house/house.cpp:33-48, 432-438, 565-671) and `HouseTransferItem`
 * (src/map/house/house.hpp:87-101, src/map/house/house.cpp:619-652).
 */
class HouseAuctionTest {

    private fun player(id: Long, name: String): Player =
        Player(id, name, 100, 100, position = Position(100, 100, 7))

    private fun newHouse(world: FakeHouseWorld, owner: Long = 10): House =
        House(
            id = 1,
            name = "Central Flat 1",
            owner = owner,
            world = world,
            ownerInfoLookup = { HouseOwnerInfo("Player $it", it * 10) },
        )

    @Test
    fun `calculateBidEndDate lands on the server save time of the target day`() {
        val world = FakeHouseWorld().apply {
            now = 1_700_000_000L
            serverSaveTime = "06:00"
        }
        val house = newHouse(world)

        house.calculateBidEndDate(3)

        assertThat(house.bidEndDate).isEqualTo(1_700_200_800L)
    }

    @Test
    fun `calculateBidEndDate parses hours minutes and seconds`() {
        val world = FakeHouseWorld().apply {
            now = 1_700_000_000L
            serverSaveTime = "06:30:15"
        }
        val house = newHouse(world)

        house.calculateBidEndDate(3)

        assertThat(house.bidEndDate).isEqualTo(1_700_202_615L)
    }

    @Test
    fun `setNewOwnerGuid transfers immediately when the restart transfer toggle is off`() {
        val world = FakeHouseWorld().apply { toggleHouseTransferOnServerRestart = false }
        val house = newHouse(world)

        house.setNewOwnerGuid(20, false)

        assertThat(world.ownerRows).containsEntry(1, 20L)
        assertThat(world.newOwnerRows).isEmpty()
        assertThat(house.owner).isEqualTo(20L)
        assertThat(house.hasNewOwnership()).isFalse()
    }

    @Test
    fun `setNewOwnerGuid schedules the transfer when the restart toggle is on`() {
        val world = FakeHouseWorld().apply { toggleHouseTransferOnServerRestart = true }
        val house = newHouse(world)

        house.setNewOwnerGuid(20, false)

        assertThat(world.ownerRows).isEmpty()
        assertThat(world.newOwnerRows).containsEntry(1, 20)
        assertThat(house.owner).isEqualTo(10L)
        assertThat(house.hasNewOwnership()).isTrue()
    }

    @Test
    fun `setNewOwnerGuid during server startup does not flag a new ownership`() {
        val world = FakeHouseWorld().apply { toggleHouseTransferOnServerRestart = true }
        val house = newHouse(world)

        house.setNewOwnerGuid(20, true)

        assertThat(world.newOwnerRows).containsEntry(1, 20)
        assertThat(house.hasNewOwnership()).isFalse()
    }

    @Test
    fun `getTransferItem hands out one item until it is reset`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)

        val first = house.getTransferItem()

        assertThat(first).isNotNull()
        assertThat(first?.id).isEqualTo(ITEM_DOCUMENT_RO)
        assertThat(first?.getItemCount()).isEqualTo(1)
        assertThat(first?.getAttribute(ItemAttribute.DESCRIPTION))
            .isEqualTo("It is a house transfer document for 'Central Flat 1'.")
        assertThat(house.getTransferItem()).isNull()

        house.resetTransferItem()

        assertThat(house.getTransferItem()).isNotNull()
    }

    @Test
    fun `executeTransfer refuses an item that is not the pending transfer item`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        house.getTransferItem()
        val otherHouse = newHouse(world)
        val otherItem = HouseTransferItem.createHouseTransferItem(otherHouse)

        assertThat(house.executeTransfer(otherItem, player(20, "New"))).isFalse()
    }

    @Test
    fun `executeTransfer sells the house immediately when the restart toggle is off`() {
        val world = FakeHouseWorld().apply { toggleHouseTransferOnServerRestart = false }
        val house = newHouse(world)
        val transferItem = house.getTransferItem()!!

        assertThat(house.executeTransfer(transferItem, player(20, "New"))).isTrue()

        assertThat(house.owner).isEqualTo(20L)
        assertThat(world.ownerRows).containsEntry(1, 20L)
        assertThat(house.getTransferItem()).isNotNull()
    }

    @Test
    fun `executeTransfer schedules the new owner when the restart toggle is on`() {
        val world = FakeHouseWorld().apply { toggleHouseTransferOnServerRestart = true }
        val house = newHouse(world)
        val transferItem = house.getTransferItem()!!

        assertThat(house.executeTransfer(transferItem, player(20, "New"))).isTrue()

        assertThat(house.owner).isEqualTo(10L)
        assertThat(world.newOwnerRows).containsEntry(1, 20)
        assertThat(house.hasNewOwnership()).isTrue()
    }

    @Test
    fun `executeTransfer refuses a house that already has a pending owner at restart`() {
        val world = FakeHouseWorld().apply { toggleHouseTransferOnServerRestart = true }
        val house = newHouse(world)
        val transferItem = house.getTransferItem()!!
        house.setNewOwnership()

        assertThat(house.executeTransfer(transferItem, player(20, "New"))).isFalse()
        assertThat(world.newOwnerRows).isEmpty()
    }

    @Test
    fun `HouseTransferItem cannot be transformed`() {
        val item = HouseTransferItem.createHouseTransferItem(newHouse(FakeHouseWorld()))

        assertThat(item.canTransform()).isFalse()
    }

    @Test
    fun `onTradeEvent announces the sale, executes the transfer and removes the item`() {
        val world = FakeHouseWorld().apply { toggleHouseTransferOnServerRestart = false }
        val house = newHouse(world)
        val transferItem = house.getTransferItem()!!
        val newOwner = player(20, "New")
        val oldOwner = player(10, "Old")
        val newOwnerMessages = mutableListOf<String>()
        val oldOwnerMessages = mutableListOf<String>()
        newOwner.sendTextMessageTyped = { _, text -> newOwnerMessages.add(text) }
        oldOwner.sendTextMessageTyped = { _, text -> oldOwnerMessages.add(text) }
        world.onlinePlayers[10] = oldOwner

        transferItem.onTradeEvent(TradeEvents_t.ON_TRADE_TRANSFER, newOwner)

        assertThat(newOwnerMessages)
            .containsExactly("You have successfully bought the house.")
        assertThat(oldOwnerMessages)
            .containsExactly("You have successfully sold your house.")
        assertThat(house.owner).isEqualTo(20L)
        assertThat(world.removedItems).containsExactly(transferItem to 1)
    }

    @Test
    fun `onTradeEvent mentions the server restart transfer when the toggle is on`() {
        val world = FakeHouseWorld().apply { toggleHouseTransferOnServerRestart = true }
        val house = newHouse(world)
        val transferItem = house.getTransferItem()!!
        val newOwner = player(20, "New")
        val messages = mutableListOf<String>()
        newOwner.sendTextMessageTyped = { _, text -> messages.add(text) }

        transferItem.onTradeEvent(TradeEvents_t.ON_TRADE_TRANSFER, newOwner)

        assertThat(messages).containsExactly(
            "You have successfully bought the house. The ownership will be transferred upon server restart.",
        )
        assertThat(house.hasNewOwnership()).isTrue()
        assertThat(house.owner).isEqualTo(10L)
    }

    @Test
    fun `onTradeEvent cancel resets the pending transfer item`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val transferItem = house.getTransferItem()!!

        transferItem.onTradeEvent(TradeEvents_t.ON_TRADE_CANCEL, player(20, "New"))

        assertThat(world.removedItems).isEmpty()
        assertThat(house.getTransferItem()).isNotNull()
    }
}
