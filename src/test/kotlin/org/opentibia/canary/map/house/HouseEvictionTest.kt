package org.opentibia.canary.map.house

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `House::kickPlayer`, `House::tryTransferOwnership` and
 * the `House::setAccessList`/`House::setOwner` eviction side effects
 * (src/map/house/house.cpp:72-92, 94-154, 213-264).
 */
class HouseEvictionTest {

    private val entry = Position(50, 50, 7)
    private val tilePosition = Position(100, 100, 7)

    private fun player(id: Long, name: String, position: Position = tilePosition): Player =
        Player(id, name, 100, 100, position = position)

    private fun newHouse(world: FakeHouseWorld): House =
        House(
            id = 1,
            owner = 10,
            townId = 1,
            world = world,
            ownerInfoLookup = { HouseOwnerInfo("Player $it", it * 10) },
        ).apply { entryPos = entry }

    @Test
    fun `kickPlayer teleports a stranger standing on a house tile to the entry and plays both effects`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, tilePosition)
        val target = player(40, "Eve")
        target.setParent(tile)

        assertThat(house.kickPlayer(null, target)).isTrue()

        assertThat(world.teleports).containsExactly(target to entry)
        assertThat(world.magicEffects).containsExactly(
            tilePosition to CONST_ME_POFF,
            entry to CONST_ME_TELEPORT,
        )
    }

    @Test
    fun `kickPlayer refuses a target that is not on this house tile`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val otherHouse = House(id = 2, owner = 20, world = world)
        val foreignTile = world.registerTile(otherHouse, tilePosition)
        val target = player(40, "Eve")
        target.setParent(foreignTile)

        assertThat(house.kickPlayer(null, target)).isFalse()
        assertThat(world.teleports).isEmpty()

        val detached = player(41, "Nobody")
        assertThat(house.kickPlayer(null, detached)).isFalse()
    }

    @Test
    fun `kickPlayer refuses when the target has an equal or higher access level`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        world.registerTile(house, tilePosition)
        house.setAccessList(GUEST_LIST, "Bob")
        house.setAccessList(SUBOWNER_LIST, "Alice")
        val guest = player(20, "Bob")
        val subOwner = player(30, "Alice")
        guest.setParent(world.tiles.getValue(tilePosition))
        subOwner.setParent(world.tiles.getValue(tilePosition))

        assertThat(house.kickPlayer(guest, subOwner)).isFalse()
        assertThat(world.teleports).isEmpty()

        assertThat(house.kickPlayer(subOwner, guest)).isTrue()
        assertThat(world.teleports).containsExactly(guest to entry)
    }

    @Test
    fun `kickPlayer refuses targets with the CanEditHouses flag`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        world.registerTile(house, tilePosition)
        val staff = player(40, "GM").apply { setFlag(PlayerFlags_t.CanEditHouses) }
        staff.setParent(world.tiles.getValue(tilePosition))

        assertThat(house.kickPlayer(null, staff)).isFalse()
        assertThat(world.teleports).isEmpty()
    }

    @Test
    fun `setAccessList kicks players that are no longer invited`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        world.registerTile(house, tilePosition)
        val bob = player(20, "Bob")
        world.tiles.getValue(tilePosition).addThing(bob)

        house.setAccessList(GUEST_LIST, "Bob")
        assertThat(world.teleports).isEmpty()

        house.setAccessList(GUEST_LIST, "")

        assertThat(world.teleports).containsExactly(bob to entry)
    }

    @Test
    fun `setAccessList for a door list never kicks anyone`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        world.registerTile(house, tilePosition)
        val stranger = player(40, "Eve")
        world.tiles.getValue(tilePosition).addThing(stranger)

        house.setAccessList(7, "Carol")

        assertThat(world.teleports).isEmpty()
    }

    @Test
    fun `tryTransferOwnership moves the depot items, kicks everyone and clears the lists`() {
        val world = FakeHouseWorld()
        world.towns.add(1)
        val house = newHouse(world)
        val tile = world.registerTile(house, tilePosition)
        val loot = Item(2160, type = ItemType(2160, "crystal coin", isPickupable = true))
        tile.addThing(loot)
        val bob = player(20, "Bob")
        tile.addThing(bob)
        house.setAccessList(GUEST_LIST, "Bob")
        val owner = player(10, "Owner")
        world.offlinePlayers[10] = owner

        val success = house.tryTransferOwnership(null, false)

        assertThat(success).isTrue()
        assertThat(world.movedItems.map { it.item }).containsExactly(loot)
        assertThat(world.movedItems.single().to).isSameAs(world.inbox)
        assertThat(world.teleports).containsExactly(bob to entry)
        assertThat(world.savedPlayers).containsExactly(owner)
        assertThat(house.owner).isEqualTo(0L)
        assertThat(house.getAccessList(GUEST_LIST)).isEmpty()
    }

    @Test
    fun `tryTransferOwnership with serverStartup keeps the owner but still clears lists`() {
        val world = FakeHouseWorld()
        world.towns.add(1)
        val house = newHouse(world)
        world.registerTile(house, tilePosition)
        world.offlinePlayers[10] = player(10, "Owner")

        assertThat(house.tryTransferOwnership(null, true)).isTrue()

        assertThat(house.owner).isEqualTo(10L)
        assertThat(house.getAccessList(GUEST_LIST)).isEmpty()
    }

    @Test
    fun `tryTransferOwnership reports a failed depot transfer`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)

        assertThat(house.tryTransferOwnership(null, false)).isFalse()
    }

    @Test
    fun `setOwner evicts the previous owner and updates the door description`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        val tile = world.registerTile(house, tilePosition)
        val oldOwner = player(10, "Old")
        tile.addThing(oldOwner)
        world.offlinePlayers[10] = oldOwner
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)

        house.setOwner(20)

        assertThat(world.teleports).containsExactly(oldOwner to entry)
        assertThat(world.ownerRows).containsEntry(1, 20L)
        assertThat(house.owner).isEqualTo(20L)
        assertThat(door.getAttribute(org.opentibia.canary.items.ItemAttribute.DESCRIPTION))
            .isEqualTo("It belongs to house ''. Player 20 owns this house.")
    }

    @Test
    fun `setOwner skips the database update when the owner is unchanged and already loaded`() {
        val world = FakeHouseWorld()
        val house = newHouse(world)
        house.setOwner(10)
        world.ownerRows.clear()

        house.setOwner(10)

        assertThat(world.ownerRows).isEmpty()
    }
}
