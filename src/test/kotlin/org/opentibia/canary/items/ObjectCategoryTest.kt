package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Numeric parity of `ObjectCategory_t` (`src/enums/object_category.hpp:12-45`).
 * The category byte is wire-visible: `ProtocolGame` reads it from the merged
 * quick-loot/managed-container opcodes (protocolgame.cpp:2628-2712).
 */
class ObjectCategoryTest {

    @Test
    fun `object category values mirror the canary enum`() {
        // object_category.hpp:13-41; values 26, 28 and 29 are unassigned.
        val expected = mapOf(
            ObjectCategory_t.OBJECTCATEGORY_NONE to 0,
            ObjectCategory_t.OBJECTCATEGORY_ARMORS to 1,
            ObjectCategory_t.OBJECTCATEGORY_NECKLACES to 2,
            ObjectCategory_t.OBJECTCATEGORY_BOOTS to 3,
            ObjectCategory_t.OBJECTCATEGORY_CONTAINERS to 4,
            ObjectCategory_t.OBJECTCATEGORY_DECORATION to 5,
            ObjectCategory_t.OBJECTCATEGORY_FOOD to 6,
            ObjectCategory_t.OBJECTCATEGORY_HELMETS to 7,
            ObjectCategory_t.OBJECTCATEGORY_LEGS to 8,
            ObjectCategory_t.OBJECTCATEGORY_OTHERS to 9,
            ObjectCategory_t.OBJECTCATEGORY_POTIONS to 10,
            ObjectCategory_t.OBJECTCATEGORY_RINGS to 11,
            ObjectCategory_t.OBJECTCATEGORY_RUNES to 12,
            ObjectCategory_t.OBJECTCATEGORY_SHIELDS to 13,
            ObjectCategory_t.OBJECTCATEGORY_TOOLS to 14,
            ObjectCategory_t.OBJECTCATEGORY_VALUABLES to 15,
            ObjectCategory_t.OBJECTCATEGORY_AMMO to 16,
            ObjectCategory_t.OBJECTCATEGORY_AXES to 17,
            ObjectCategory_t.OBJECTCATEGORY_CLUBS to 18,
            ObjectCategory_t.OBJECTCATEGORY_DISTANCEWEAPONS to 19,
            ObjectCategory_t.OBJECTCATEGORY_SWORDS to 20,
            ObjectCategory_t.OBJECTCATEGORY_WANDS to 21,
            ObjectCategory_t.OBJECTCATEGORY_PREMIUMSCROLLS to 22,
            ObjectCategory_t.OBJECTCATEGORY_TIBIACOINS to 23,
            ObjectCategory_t.OBJECTCATEGORY_CREATUREPRODUCTS to 24,
            ObjectCategory_t.OBJECTCATEGORY_QUIVERS to 25,
            ObjectCategory_t.OBJECTCATEGORY_FISTWEAPONS to 27,
            ObjectCategory_t.OBJECTCATEGORY_GOLD to 30,
            ObjectCategory_t.OBJECTCATEGORY_DEFAULT to 31,
        )

        for ((category, value) in expected) {
            assertThat(category.value).`as`("%s value", category.name).isEqualTo(value)
        }
        assertThat(ObjectCategory_t.entries).hasSize(expected.size)
    }

    @Test
    fun `first and last aliases mirror the canary bounds`() {
        // object_category.hpp:43-44: FIRST = ARMORS and LAST = DEFAULT.
        assertThat(ObjectCategory_t.OBJECTCATEGORY_FIRST).isSameAs(ObjectCategory_t.OBJECTCATEGORY_ARMORS)
        assertThat(ObjectCategory_t.OBJECTCATEGORY_LAST).isSameAs(ObjectCategory_t.OBJECTCATEGORY_DEFAULT)
        assertThat(ObjectCategory_t.OBJECTCATEGORY_FIRST.value).isEqualTo(1)
        assertThat(ObjectCategory_t.OBJECTCATEGORY_LAST.value).isEqualTo(31)
    }
}
