package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the decay-related `Item` core (src/items/item.cpp):
 * the constructor subtype/charges/duration defaults (item.cpp:685-707),
 * `getDefaultDuration`/`setDefaultDuration` (item.hpp:632-640),
 * `setDefaultSubtype` (item.cpp:770-783), `canDecay` (item.cpp:3438-3454)
 * and `isRemoved` (item.cpp:859-865).
 */
class ItemDecayTest {

    @Test
    fun `fluid container clamps the subtype to the Fluids_t enum count`() {
        val vial = ItemType(id = 2006, name = "vial", isFluidContainer = true)

        val full = Item(2006, count = 200, type = vial)
        assertThat(full.getSubType()).isEqualTo(FLUIDS_COUNT)
        assertThat(full.count).isEqualTo(1)

        val wine = Item(2006, count = 2, type = vial)
        assertThat(wine.getSubType()).isEqualTo(2)
    }

    @Test
    fun `splash clamps the subtype through the same fluid rule`() {
        val splash = ItemType(id = 2016, name = "splash", isSplash = true)

        assertThat(Item(2016, count = 300, type = splash).getSubType()).isEqualTo(FLUIDS_COUNT)
    }

    @Test
    fun `stackable constructor falls back to charges and then to one`() {
        val runes = ItemType(id = 2260, name = "rune", isStackable = true, charges = 3)
        val coins = ItemType(id = 2148, name = "coin", isStackable = true)

        assertThat(Item(2260, type = runes).count).isEqualTo(3)
        assertThat(Item(2260, count = 10, type = runes).count).isEqualTo(10)
        assertThat(Item(2148, type = coins).count).isEqualTo(1)
        assertThat(Item(2148, count = 0, type = coins).count).isEqualTo(1)
    }

    @Test
    fun `charged non-stackable constructor stores charges and keeps count at one`() {
        val wand = ItemType(id = 2190, name = "wand", charges = 5)

        val withCount = Item(2190, count = 7, type = wand)
        assertThat(withCount.getSubType()).isEqualTo(7)
        assertThat(withCount.count).isEqualTo(1)

        val withoutCount = Item(2190, type = wand)
        assertThat(withoutCount.getSubType()).isEqualTo(5)
        assertThat(withoutCount.count).isEqualTo(1)
    }

    @Test
    fun `constructor applies the default duration from decayTime`() {
        val torch = ItemType(id = 2050, name = "torch", decayTime = 300)
        val item = Item(2050, type = torch)

        assertThat(item.getDefaultDuration()).isEqualTo(300_000)
        assertThat(item.getDuration()).isEqualTo(300_000)
        assertThat(item.hasAttribute(ItemAttribute.DURATION)).isTrue()

        val plain = Item(2051, type = ItemType(2051, "plain"))
        assertThat(plain.getDefaultDuration()).isZero()
        assertThat(plain.hasAttribute(ItemAttribute.DURATION)).isFalse()
    }

    @Test
    fun `setDefaultDuration ignores zero decay time`() {
        val plain = Item(2051, type = ItemType(2051, "plain"))

        plain.setDuration(1234)
        plain.setDefaultDuration()

        assertThat(plain.getDuration()).isEqualTo(1234)
    }

    @Test
    fun `setDefaultSubtype restores the count and charges from the type`() {
        val runes = ItemType(id = 2260, name = "rune", isStackable = true, charges = 3)
        val rune = Item(2260, count = 10, type = runes)
        rune.setDefaultSubtype()
        assertThat(rune.count).isEqualTo(3)

        val wand = ItemType(id = 2190, name = "wand", charges = 5)
        val charged = Item(2190, count = 7, type = wand)
        charged.setDefaultSubtype()
        assertThat(charged.count).isEqualTo(1)
        assertThat(charged.getAttribute(ItemAttribute.CHARGES)).isEqualTo(5L)
    }

    @Test
    fun `canDecay requires a target, a time, no unique id, no disable flag and a live parent`() {
        val torch = ItemType(id = 2050, name = "torch", decayTime = 5, decayTo = 2051)
        val item = Item(2050, type = torch)

        assertThat(item.canDecay()).isFalse()
        item.setParent(liveCylinder())
        assertThat(item.canDecay()).isTrue()

        item.setUniqueId(10)
        assertThat(item.canDecay()).isFalse()
        item.removeAttribute(ItemAttribute.UNIQUEID)
        assertThat(item.canDecay()).isTrue()

        item.decayDisabled = true
        assertThat(item.canDecay()).isFalse()
        item.decayDisabled = false

        item.resetParent()
        assertThat(item.canDecay()).isFalse()
    }

    @Test
    fun `canDecay rejects missing decay target or missing decay time`() {
        val live = liveCylinder()
        val noTarget = Item(2050, type = ItemType(2050, "torch", decayTime = 5))
        val noTime = Item(2050, type = ItemType(2050, "torch", decayTo = 2051))
        noTarget.setParent(live)
        noTime.setParent(live)

        assertThat(noTarget.canDecay()).isFalse()
        assertThat(noTime.canDecay()).isFalse()
    }

    @Test
    fun `canDecay allows a zero decayTo removal target`() {
        val live = liveCylinder()
        val ashes = Item(2050, type = ItemType(2050, "torch", decayTime = 5, decayTo = 0))
        ashes.setParent(live)

        assertThat(ashes.canDecay()).isTrue()
    }

    @Test
    fun `isRemoved follows the parent chain`() {
        val item = Item(1)
        assertThat(item.isRemoved()).isTrue()

        item.setParent(liveCylinder())
        assertThat(item.isRemoved()).isFalse()
    }

    private fun liveCylinder(): Cylinder = object : Cylinder {
        override fun internalAddThing(item: Item) = Unit
    }
}
