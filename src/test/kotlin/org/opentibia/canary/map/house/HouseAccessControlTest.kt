package org.opentibia.canary.map.house

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Transcript tests for the access-list/door half of `House`
 * (src/map/house/house.cpp:183-264, 483-563) and `Door`
 * (src/map/house/house.hpp:42-82, src/map/house/house.cpp:791-854).
 */
class HouseAccessControlTest {

    private fun player(id: Long, name: String, position: Position = Position(100, 100, 7)): Player =
        Player(id, name, 100, 100, position = position)

    @Test
    fun `isInvited accepts owner, subowner and guest and rejects a stranger`() {
        val house = House(id = 1, owner = 10)
        house.setAccessList(SUBOWNER_LIST, "Alice")
        house.setAccessList(GUEST_LIST, "Bob")

        assertThat(house.isInvited(player(10, "Owner"))).isTrue()
        assertThat(house.isInvited(player(30, "Alice"))).isTrue()
        assertThat(house.isInvited(player(20, "Bob"))).isTrue()
        assertThat(house.isInvited(player(40, "Eve"))).isFalse()
    }

    @Test
    fun `isInvited treats a missing player as the owner like Canary's null check`() {
        val house = House(id = 1, owner = 10)

        assertThat(house.isInvited(null)).isTrue()
    }

    @Test
    fun `canEditAccessList allows the owner every list and the subowner only the guest list`() {
        val house = House(id = 1, owner = 10)
        house.setAccessList(SUBOWNER_LIST, "Alice")
        house.setAccessList(GUEST_LIST, "Bob")
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)

        val owner = player(10, "Owner")
        val subOwner = player(30, "Alice")
        val guest = player(20, "Bob")

        assertThat(house.canEditAccessList(GUEST_LIST, owner)).isTrue()
        assertThat(house.canEditAccessList(SUBOWNER_LIST, owner)).isTrue()
        assertThat(house.canEditAccessList(7, owner)).isTrue()

        assertThat(house.canEditAccessList(GUEST_LIST, subOwner)).isTrue()
        assertThat(house.canEditAccessList(SUBOWNER_LIST, subOwner)).isFalse()
        assertThat(house.canEditAccessList(7, subOwner)).isFalse()

        assertThat(house.canEditAccessList(GUEST_LIST, guest)).isFalse()
        assertThat(house.canEditAccessList(7, guest)).isFalse()
    }

    @Test
    fun `canEditAccessList with no player resolves to HOUSE_OWNER`() {
        val house = House(id = 1, owner = 10)

        assertThat(house.canEditAccessList(SUBOWNER_LIST, null)).isTrue()
    }

    @Test
    fun `Door canUse is open from subowner level up`() {
        val house = House(id = 1, owner = 10)
        house.setAccessList(SUBOWNER_LIST, "Alice")
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)

        assertThat(door.canUse(player(10, "Owner"))).isTrue()
        assertThat(door.canUse(player(30, "Alice"))).isTrue()
        assertThat(door.canUse(player(40, "Eve"))).isFalse()
    }

    @Test
    fun `Door canUse falls back to its own access list`() {
        val house = House(id = 1, owner = 10)
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)
        door.setAccessList("Carol")

        assertThat(door.canUse(player(50, "Carol"))).isTrue()
        assertThat(door.canUse(player(40, "Eve"))).isFalse()
    }

    @Test
    fun `Door without a house can be used by anyone`() {
        val door = Door(1210)

        assertThat(door.canUse(null)).isTrue()
        assertThat(door.canUse(player(40, "Eve"))).isTrue()
        assertThat(door.getAccessList()).isNull()
    }

    @Test
    fun `removeDoor detaches it and getDoorByPosition finds the door on its tile`() {
        val house = House(id = 1, owner = 10)
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)
        val tile = Tile(Position(100, 100, 7))
        tile.addThing(door)

        assertThat(house.getDoorByPosition(Position(100, 100, 7))).isSameAs(door)
        assertThat(house.getDoorByPosition(Position(101, 100, 7))).isNull()

        house.removeDoor(door)

        assertThat(house.getDoors()).isEmpty()
        assertThat(house.getDoorByPosition(Position(100, 100, 7))).isNull()
    }

    @Test
    fun `Door onRemoved unregisters the door from its house`() {
        val house = House(id = 1, owner = 10)
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)

        door.onRemoved()

        assertThat(house.getDoors()).isEmpty()
    }

    @Test
    fun `addDoor refreshes the door description`() {
        val world = FakeHouseWorld()
        val house = House(id = 1, name = "Central Flat 1", owner = 10, world = world)
        house.ownerName = "Alice"
        val door = Door(1210)
        door.setDoorId(7)

        house.addDoor(door)

        assertThat(door.getAttribute(ItemAttribute.DESCRIPTION))
            .isEqualTo("It belongs to house 'Central Flat 1'. Alice owns this house.")
    }

    @Test
    fun `addTile stamps the protection zone, marks navigation and refreshes descriptions`() {
        val world = FakeHouseWorld()
        val house = House(id = 1, name = "Central Flat 1", world = world)
        val door = Door(1210)
        door.setDoorId(1)
        house.addDoor(door)
        val position = Position(100, 100, 7)
        world.tiles[position] = Tile(position)

        val houseTile = house.addTile(position)

        assertThat(house.tiles).containsExactly(houseTile)
        assertThat(houseTile.houseId).isEqualTo(1)
        assertThat(world.tiles.getValue(position).hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)).isTrue()
        assertThat(world.navigationChanges).containsExactly(position)
        assertThat(door.getAttribute(ItemAttribute.DESCRIPTION))
            .isEqualTo("It belongs to house 'Central Flat 1'. Nobody owns this house.")
    }

    @Test
    fun `addBed removeBed and getBedCount track the bed list`() {
        val house = House(id = 1, owner = 10)
        val bed1 = org.opentibia.canary.items.BedItem(1728)
        val bed2 = org.opentibia.canary.items.BedItem(1729)
        val bed3 = org.opentibia.canary.items.BedItem(1730)

        house.addBed(bed1)
        house.addBed(bed2)
        house.addBed(bed3)

        assertThat(house.getBeds()).containsExactly(bed1, bed2, bed3)
        assertThat(house.getBedCount()).isEqualTo(1L)

        house.removeBed(bed1)

        assertThat(house.getBeds()).containsExactly(bed2, bed3)
        assertThat(house.getBedCount()).isEqualTo(1L)
    }

    @Test
    fun `clearHouseInfo wakes every sleeper bed`() {
        val world = FakeHouseWorld()
        val house = House(id = 1, owner = 10, world = world)
        val sleeping = org.opentibia.canary.items.BedItem(1728).apply { sleeperGUID = 77 }
        val empty = org.opentibia.canary.items.BedItem(1729)
        house.addBed(sleeping)
        house.addBed(empty)

        house.clearHouseInfo(false)

        assertThat(world.wokenBeds).containsExactly(sleeping)
    }

    @Test
    fun `hasItemOnTile detects pickupable and wrapable items`() {
        val world = FakeHouseWorld()
        val house = House(id = 1, owner = 10, world = world)
        val position = Position(100, 100, 7)
        val tile = world.registerTile(house, position)

        assertThat(house.hasItemOnTile()).isFalse()

        tile.addThing(Item(2160, type = ItemType(2160, "crystal coin", isPickupable = true)))
        assertThat(house.hasItemOnTile()).isTrue()

        val wrapable = Item(12345, type = ItemType(12345, "wrapped item", isPickupable = false))
        world.wrapableItems.add(12345)
        val other = world.registerTile(house, Position(101, 100, 7))
        other.addThing(wrapable)
        assertThat(house.hasItemOnTile()).isTrue()
    }
}
