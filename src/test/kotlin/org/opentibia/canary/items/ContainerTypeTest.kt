package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Numeric parity of `ContainerSpecial_t` (`src/enums/container_type.hpp:16-25`),
 * the container "special category" byte `ProtocolGame::AddItem` serializes
 * (protocolgame.cpp:816-836).
 */
class ContainerTypeTest {

    @Test
    fun `container special values mirror container type hpp`() {
        // container_type.hpp:17-23; values 3, 5-7 and 10 are unassigned.
        val expected = mapOf(
            ContainerSpecial_t.None to 0,
            ContainerSpecial_t.LootContainer to 1,
            ContainerSpecial_t.ContentCounter to 2,
            ContainerSpecial_t.LootHighlight to 4,
            ContainerSpecial_t.Obtain to 8,
            ContainerSpecial_t.Manager to 9,
            ContainerSpecial_t.QuiverLoot to 11,
        )

        for ((special, value) in expected) {
            assertThat(special.value).`as`("%s value", special.name).isEqualTo(value)
        }
        assertThat(ContainerSpecial_t.entries).hasSize(expected.size)
    }
}
