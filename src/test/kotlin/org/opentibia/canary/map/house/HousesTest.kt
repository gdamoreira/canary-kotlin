package org.opentibia.canary.map.house

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.map.Position
import java.io.File

/**
 * Transcript tests for `Houses` (src/map/house/house.hpp:391-438,
 * src/map/house/house.cpp:860-938) and `AccessList`
 * (src/map/house/house.hpp:23-40, src/map/house/house.cpp:673-796).
 */
class HousesTest {

    @Test
    fun `addHouse reuses an existing house and getHouse hides unknown ids`() {
        val houses = Houses()

        val first = houses.addHouse(42)
        val second = houses.addHouse(42)

        assertThat(second).isSameAs(first)
        assertThat(houses.getHouse(42)).isSameAs(first)
        assertThat(houses.getHouse(7)).isNull()
        assertThat(houses.getHouses()).containsExactly(first)
    }

    @Test
    fun `getHouses returns the house map ordered by id`() {
        val houses = Houses()
        houses.addHouse(9)
        houses.addHouse(1)
        houses.addHouse(5)

        assertThat(houses.getHouses().map { it.id }).containsExactly(1, 5, 9)
    }

    @Test
    fun `addHouseClientId keeps the first house registered for a client id`() {
        val houses = Houses()
        val first = houses.addHouse(1)
        val second = houses.addHouse(2)

        houses.addHouseClientId(100, first)
        houses.addHouseClientId(100, second)

        assertThat(houses.getHouseByClientId(100)).isSameAs(first)
        assertThat(houses.getHouseByClientId(101)).isNull()
    }

    @Test
    fun `getHouseByPlayerId returns the house owned by the player`() {
        val houses = Houses(ownerInfoLookup = { HouseOwnerInfo("Knight Hero", 12L) })
        val owned = houses.addHouse(3)
        owned.setOwner(77)

        assertThat(houses.getHouseByPlayerId(77)).isSameAs(owned)
        assertThat(houses.getHouseByPlayerId(78)).isNull()
    }

    @Test
    fun `loadHousesXML transcribes every house attribute and clears the owner`(
        @TempDir tempDir: File,
    ) {
        val houses = Houses()
        val mapped = houses.addHouse(1)

        val xml = File(tempDir, "world-house.xml").apply {
            writeText(
                """
                <?xml version="1.0"?>
                <houses>
                    <house name="Central Flat 1" houseid="1" entryx="1000" entryy="1001" entryz="7" rent="500" townid="2" size="17" clientid="33" guildhall="1" beds="4" />
                </houses>
                """.trimIndent(),
            )
        }

        assertThat(houses.loadHousesXML(xml.path)).isTrue()

        assertThat(mapped.name).isEqualTo("Central Flat 1")
        assertThat(mapped.entryPos).isEqualTo(Position(1000, 1001, 7))
        assertThat(mapped.rent).isEqualTo(500L)
        assertThat(mapped.townId).isEqualTo(2)
        assertThat(mapped.size).isEqualTo(17)
        assertThat(mapped.clientId).isEqualTo(33)
        assertThat(mapped.isGuildhall()).isTrue()
        assertThat(mapped.maxBeds).isEqualTo(4)
        assertThat(mapped.owner).isEqualTo(0L)
        assertThat(houses.getHouseByClientId(33)).isSameAs(mapped)
    }

    @Test
    fun `loadHousesXML defaults beds to minus one and fails on unknown or missing house ids`(
        @TempDir tempDir: File,
    ) {
        val houses = Houses()
        houses.addHouse(1)

        val missingId = File(tempDir, "missing.xml").apply {
            writeText("<houses><house name=\"x\" /></houses>")
        }
        assertThat(houses.loadHousesXML(missingId.path)).isFalse()

        val unknownId = File(tempDir, "unknown.xml").apply {
            writeText("<houses><house houseid=\"99\" name=\"x\" /></houses>")
        }
        assertThat(houses.loadHousesXML(unknownId.path)).isFalse()

        val noBeds = File(tempDir, "nobeds.xml").apply {
            writeText("<houses><house houseid=\"1\" name=\"x\" /></houses>")
        }
        assertThat(houses.loadHousesXML(noBeds.path)).isTrue()
        assertThat(houses.getHouse(1)?.maxBeds).isEqualTo(-1)
    }

    @Test
    fun `AccessList parseList keeps the validated raw text and resolves names`() {
        val accessList = AccessList()

        accessList.parseList("  Alice  \nBob\n#comment\n\nAlice\n")

        assertThat(accessList.getList()).isEqualTo("  Alice  \nBob\n#comment\nAlice\n")
        assertThat(accessList.isInList("alice")).isTrue()
        assertThat(accessList.isInList("bob")).isTrue()
        assertThat(accessList.isInList("Eve")).isFalse()
    }

    @Test
    fun `AccessList parseList accepts everyone and skips unsupported regex lines`() {
        val accessList = AccessList()

        accessList.parseList("Gu!ld\n*")

        assertThat(accessList.getList()).isEqualTo("Gu!ld\n*\n")
        assertThat(accessList.isInList("anyone")).isTrue()
    }

    @Test
    fun `House setAccessList stores guest, subowner, and door lists via list ids`() {
        val house = House(id = 1)
        val door = Door(id = 1210)
        door.setDoorId(7)
        house.addDoor(door)

        house.setAccessList(GUEST_LIST, "Alice")
        house.setAccessList(SUBOWNER_LIST, "Bob")
        house.setAccessList(7, "Carol")

        assertThat(house.getAccessList(GUEST_LIST)).isEqualTo("Alice\n")
        assertThat(house.getAccessList(SUBOWNER_LIST)).isEqualTo("Bob\n")
        assertThat(door.getAccessList()).isEqualTo("Carol\n")
        assertThat(house.getAccessList(8)).isNull()
    }

    @Test
    fun `House caches a door list until the door is registered`() {
        val house = House(id = 2)
        val door = Door(id = 1210)
        door.setDoorId(5)

        house.setAccessList(5, "Dave")
        house.addDoor(door)

        assertThat(door.getAccessList()).isEmpty()
        assertThat(house.getAccessList(5)).isEqualTo("Dave\n")
        assertThat(door.getAccessList()).isEqualTo("Dave\n")
    }

    @Test
    fun `clearHouseInfo resets owner, access lists, and door lists`() {
        val house = House(id = 3, owner = 10)
        val door = Door(id = 1210)
        door.setDoorId(1)
        house.addDoor(door)
        house.setAccessList(GUEST_LIST, "Alice")
        house.setAccessList(SUBOWNER_LIST, "Bob")
        door.setAccessList("Carol")

        house.clearHouseInfo(false)

        assertThat(house.owner).isEqualTo(0L)
        assertThat(house.ownerAccountId).isEqualTo(0L)
        assertThat(house.getAccessList(GUEST_LIST)).isEmpty()
        assertThat(house.getAccessList(SUBOWNER_LIST)).isEmpty()
        assertThat(door.getAccessList()).isEmpty()
    }

    @Test
    fun `clearHouseInfo can keep the owner while still clearing lists`() {
        val house = House(id = 4, owner = 11)
        house.setAccessList(GUEST_LIST, "Alice")

        house.clearHouseInfo(true)

        assertThat(house.owner).isEqualTo(11L)
        assertThat(house.getAccessList(GUEST_LIST)).isEmpty()
    }
}
