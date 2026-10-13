package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for Canary `class Town` and `class Towns`
 * (src/map/town.hpp:15-93): id/temple/name storage, insertion-order-independent
 * `std::map` id registry, the id-0 skip and the case-insensitive name lookup of
 * `Towns::getTown(const std::string &)` (town.hpp:58-69) and the
 * create-on-miss behavior of `Towns::getOrCreateTown` (town.hpp:79-85).
 */
class TownTest {

    @Test
    fun `constructs a town with the given id empty name and zero temple position`() {
        val town = Town(5)

        assertThat(town.getID()).isEqualTo(5)
        assertThat(town.getName()).isEmpty()
        assertThat(town.getTemplePosition()).isEqualTo(Position(0, 0, 0))
    }

    @Test
    fun `setName and setTemplePos store the values`() {
        val town = Town(7)

        town.setName("Thais")
        town.setTemplePos(Position(100, 200, 7))

        assertThat(town.getName()).isEqualTo("Thais")
        assertThat(town.getTemplePosition()).isEqualTo(Position(100, 200, 7))
        assertThat(town.getID()).isEqualTo(7)
    }

    @Test
    fun `addTown returns true only for a new id and never replaces an existing town`() {
        val towns = Towns()
        val first = Town(1)
        val second = Town(1)

        assertThat(towns.addTown(1, first)).isTrue()
        assertThat(towns.addTown(1, second)).isFalse()
        assertThat(towns.getTown(1)).isSameAs(first)
    }

    @Test
    fun `getTown by id returns the registered town or null`() {
        val towns = Towns()
        val town = Town(3)
        val zero = Town(0)
        towns.addTown(3, town)
        towns.addTown(0, zero)

        assertThat(towns.getTown(3)).isSameAs(town)
        assertThat(towns.getTown(0)).isSameAs(zero)
        assertThat(towns.getTown(4)).isNull()
    }

    @Test
    fun `getTown by name is case-insensitive and requires the full name`() {
        val towns = Towns()
        val thais = Town(1)
        thais.setName("Thais")
        towns.addTown(1, thais)

        assertThat(towns.getTown("Thais")).isSameAs(thais)
        assertThat(towns.getTown("thais")).isSameAs(thais)
        assertThat(towns.getTown("THAIS")).isSameAs(thais)
        assertThat(towns.getTown("tHaIs")).isSameAs(thais)
        assertThat(towns.getTown("Thai")).isNull()
        assertThat(towns.getTown("Thais ")).isNull()
    }

    @Test
    fun `getTown by name skips the reserved id zero`() {
        val towns = Towns()
        val zero = Town(0)
        zero.setName("Thais")
        towns.addTown(0, zero)

        assertThat(towns.getTown("Thais")).isNull()

        val real = Town(1)
        real.setName("thais")
        towns.addTown(1, real)

        assertThat(towns.getTown("Thais")).isSameAs(real)
    }

    @Test
    fun `getTown by name scans ids in ascending order`() {
        val towns = Towns()
        val high = Town(7)
        high.setName("Same")
        val low = Town(2)
        low.setName("Same")
        towns.addTown(7, high)
        towns.addTown(2, low)

        assertThat(towns.getTown("Same")).isSameAs(low)
    }

    @Test
    fun `getOrCreateTown returns an existing town without creating a new one`() {
        val towns = Towns()
        val existing = Town(4)
        towns.addTown(4, existing)

        assertThat(towns.getOrCreateTown(4)).isSameAs(existing)
        assertThat(towns.getTowns()).hasSize(1)
    }

    @Test
    fun `getOrCreateTown creates and registers a town carrying the requested id`() {
        val towns = Towns()

        val created = towns.getOrCreateTown(9)

        assertThat(created.getID()).isEqualTo(9)
        assertThat(created.getName()).isEmpty()
        assertThat(towns.getTown(9)).isSameAs(created)
        assertThat(towns.getOrCreateTown(9)).isSameAs(created)
    }

    @Test
    fun `getTowns exposes every registered town ordered by id`() {
        val towns = Towns()
        val first = Town(9)
        val second = Town(1)
        val third = Town(5)
        towns.addTown(9, first)
        towns.addTown(1, second)
        towns.addTown(5, third)

        assertThat(towns.getTowns().keys).containsExactly(1, 5, 9)
    }
}
