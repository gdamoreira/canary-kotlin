package org.opentibia.canary.map.house

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class HouseTest {

    @Test
    fun `evaluates house permissions for owner, guest, and unauthorized player`() {
        val house = House(
            id = 1,
            name = "Thais Central Flat 1",
            owner = 10,
            rent = 1500,
            townId = 1
        )

        house.setAccessList(GUEST_LIST, "Bob")
        house.setAccessList(SUBOWNER_LIST, "Alice")

        val owner = Player(10, "Owner", 100, 100, position = Position(100, 100, 7))
        val guest = Player(20, "Bob", 100, 100, position = Position(100, 100, 7))
        val subOwner = Player(30, "Alice", 100, 100, position = Position(100, 100, 7))
        val stranger = Player(40, "Eve", 100, 100, position = Position(100, 100, 7))

        assertThat(house.canEnter(owner)).isTrue()
        assertThat(house.canEnter(guest)).isTrue()
        assertThat(house.canEnter(subOwner)).isTrue()
        assertThat(house.canEnter(stranger)).isFalse()
    }

    @Test
    fun `manages house tiles and evicts non-authorized players`() {
        val house = House(id = 2, name = "Carlin Villa", owner = 5, rent = 5000, townId = 2)
        val pos1 = Position(32000, 32000, 7)
        val pos2 = Position(32001, 32000, 7)

        val tile1 = house.addTile(pos1)
        val tile2 = house.addTile(pos2)

        assertThat(house.tiles).containsExactly(tile1, tile2)
        assertThat(tile1.houseId).isEqualTo(2)
    }
}
