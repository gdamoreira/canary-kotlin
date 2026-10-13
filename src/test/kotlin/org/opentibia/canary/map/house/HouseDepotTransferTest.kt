package org.opentibia.canary.map.house

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `House::transferToDepot` and `House::hasItemOnTile`
 * (src/map/house/house.cpp:266-481).
 */
class HouseDepotTransferTest {

    private val position = Position(100, 100, 7)

    private fun player(id: Long, name: String): Player =
        Player(id, name, 100, 100, position = position)

    private fun pickupable(id: Int = 2160): Item =
        Item(id, type = ItemType(id, "item", isPickupable = true))

    private fun newHouse(world: FakeHouseWorld): House =
        House(id = 1, owner = 10, townId = 1, world = world)

    @Test
    fun `transferToDepot fails when the house has no town`() {
        val world = FakeHouseWorld()
        val house = House(id = 1, owner = 10, world = world)
        world.registerTile(house, position).addThing(pickupable())

        assertThat(house.transferToDepot(player(10, "Owner"))).isFalse()
        assertThat(world.movedItems).isEmpty()
    }

    @Test
    fun `transferToDepot moves pickupable tile items to the depot inbox`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        val loot = pickupable().apply { setItemCount(5) }
        tile.addThing(loot)
        val owner = player(10, "Owner")

        assertThat(house.transferToDepot(owner)).isTrue()

        val moved = world.movedItems.single()
        assertThat(moved.item).isSameAs(loot)
        assertThat(moved.to).isSameAs(world.inbox)
        assertThat(moved.index).isEqualTo(INDEX_WHEREEVER)
        assertThat(moved.count).isEqualTo(5)
        assertThat(moved.actor).isSameAs(owner)
        assertThat(moved.flags and CylinderFlags.FLAG_NOLIMIT).isEqualTo(CylinderFlags.FLAG_NOLIMIT)
        assertThat(world.savedPlayers).containsExactly(owner)
    }

    @Test
    fun `transferToDepot refuses a tile that does not belong to this house`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val otherHouse = House(id = 2, owner = 20, world = world)
        val foreignTile = world.registerTile(otherHouse, position)
        foreignTile.addThing(pickupable())

        val foreign = HouseTile(position, houseId = 2)
        assertThat(house.transferToDepot(player(10, "Owner"), foreign)).isFalse()
        assertThat(world.movedItems).isEmpty()
    }

    @Test
    fun `transferToDepot collects inside a non-pickupable container`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        val crate = Container(1990, capacity = 8, type = ItemType(1990, "crate", isPickupable = false))
        val coin = pickupable()
        crate.addThing(coin)
        tile.addThing(crate)

        assertThat(house.transferToDepot(player(10, "Owner"))).isTrue()

        assertThat(world.movedItems.map { it.item }).containsExactly(coin)
    }

    @Test
    fun `transferToDepot moves a pickupable nested container as a whole`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        val crate = Container(1990, capacity = 8, type = ItemType(1990, "crate", isPickupable = false))
        val bag = Container(1987, capacity = 8, type = ItemType(1987, "bag", isPickupable = true))
        val coin = pickupable()
        bag.addThing(coin)
        crate.addThing(bag)
        tile.addThing(crate)

        assertThat(house.transferToDepot(player(10, "Owner"))).isTrue()

        assertThat(world.movedItems.map { it.item }).containsExactly(bag)
    }

    @Test
    fun `transferToDepot wraps wrapable items through the world seam`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        val wrapable = Item(12345, type = ItemType(12345, "wrapped item", isPickupable = false))
        world.wrapableItems.add(12345)
        val wrapped = Item(2834)
        world.wrappedItems[12345] = wrapped
        tile.addThing(wrapable)

        assertThat(house.transferToDepot(player(10, "Owner"))).isTrue()

        assertThat(world.movedItems.map { it.item }).containsExactly(wrapped)
    }

    @Test
    fun `transferToDepot routes owned items to the item owner and saves both players`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        val owned = pickupable().apply { setAttribute(ItemAttribute.OWNER, 30L) }
        tile.addThing(owned)
        val owner = player(10, "Owner")
        val itemOwner = player(30, "Guest")
        world.offlinePlayers[30] = itemOwner

        assertThat(house.transferToDepot(owner)).isTrue()

        assertThat(world.movedItems.single().to).isSameAs(world.inbox)
        assertThat(world.savedPlayers).containsExactlyInAnyOrder(owner, itemOwner)
    }

    @Test
    fun `transferToDepot skips an owned item whose owner cannot be resolved`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        tile.addThing(pickupable().apply { setAttribute(ItemAttribute.OWNER, 30L) })
        val owner = player(10, "Owner")

        assertThat(house.transferToDepot(owner)).isTrue()

        assertThat(world.movedItems).isEmpty()
        assertThat(world.savedPlayers).containsExactly(owner)
    }

    @Test
    fun `transferToDepot keeps going and reports success after a failed move`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        tile.addThing(pickupable())
        world.moveItemResult = ReturnValue.NOTPOSSIBLE

        assertThat(house.transferToDepot(player(10, "Owner"))).isTrue()
        assertThat(world.movedItems).isEmpty()
        assertThat(world.savedPlayers.map { it.getGUID() }).containsExactly(10L)
    }

    @Test
    fun `transferToDepot with no player resolves the owner online or from storage`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        tile.addThing(pickupable())
        val owner = player(10, "Owner")
        world.onlinePlayers[10] = owner

        assertThat(house.transferToDepot()).isTrue()

        assertThat(world.movedItems).hasSize(1)
        assertThat(world.savedPlayers).containsExactly(owner)
    }

    @Test
    fun `transferToDepot with no player returns false when the owner cannot be loaded`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)

        assertThat(house.transferToDepot()).isFalse()
    }

    @Test
    fun `transferToDepot walks the wrapable container contents through the seam`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, position)
        val inner = pickupable()
        val wrapper = Container(
            12345,
            capacity = 8,
            type = ItemType(12345, "wrapped container", isPickupable = false),
        )
        wrapper.addThing(inner)
        tile.addThing(wrapper)
        world.wrapableItems.add(12345)
        world.wrapContainerItems.add(12345)
        val wrapped = Item(2834)
        world.wrappedItems[12345] = wrapped

        assertThat(house.transferToDepot(player(10, "Owner"))).isTrue()

        assertThat(world.movedItems.map { it.item }).containsExactly(inner, wrapped)
    }
}
