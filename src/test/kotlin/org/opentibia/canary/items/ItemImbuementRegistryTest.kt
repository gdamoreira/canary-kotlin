package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * Tests for the `Item` imbuement-registry validation seam: when a registry is
 * assigned, `Item::hasImbuements` (src/items/item.hpp:728-737) must resolve the
 * packed id/duration through `Item::getImbuementInfo`'s
 * `g_imbuements().getImbuement(value & 0xFF)` (src/items/item.cpp:472-483)
 * instead of only checking that the custom attribute is non-zero.
 */
class ItemImbuementRegistryTest {

    private val imbuements = Imbuements().apply {
        assertThat(loadFromXml(TEST_XML)).isTrue()
    }
    private val knownId: Int = imbuements.imbuementMap.keys.single()
    private val itemTypes = ItemTypeRegistry().apply {
        register(ItemType(id = 2400, name = "Sword", imbuementSlot = 2))
    }

    private fun sword(): Item =
        Item(id = 2400, type = ItemType(id = 2400, name = "Sword", imbuementSlot = 2))

    @Test
    fun `without a registry hasImbuements keeps the attribute-presence fallback`() {
        val item = sword()
        item.setImbuement(0, 4242, 1000)

        assertThat(item.hasImbuements()).isTrue()
    }

    @Test
    fun `hasImbuements validates the packed id against the assigned registry`() {
        val item = sword()
        item.imbuementRegistry = imbuements

        item.setImbuement(0, 4242, 1000)
        assertThat(item.hasImbuements()).isFalse()

        item.setImbuement(0, knownId, 1000)
        assertThat(item.hasImbuements()).isTrue()
    }

    @Test
    fun `hasImbuements treats a known id with zero duration as empty`() {
        val item = sword()
        item.imbuementRegistry = imbuements
        item.setCustomAttribute("${ITEM_IMBUEMENT_SLOT}", knownId.toLong())

        assertThat(item.getImbuementAttribute(0)).isEqualTo(knownId.toLong())
        assertThat(item.hasImbuements()).isFalse()
    }

    @Test
    fun `hasMarketAttributes treats an unknown imbuement id as no imbuement once the registry is assigned`() {
        val item = sword()
        item.imbuementRegistry = imbuements
        item.setImbuement(0, 4242, 1000)

        assertThat(item.hasMarketAttributes()).isTrue()
    }

    @Test
    fun `clone preserves the assigned registry seam`() {
        val item = sword()
        item.imbuementRegistry = imbuements
        item.setImbuement(0, 4242, 1000)

        assertThat(item.clone().hasImbuements()).isFalse()
    }

    @Test
    fun `Item create assigns the imbuement registry creation hub`() {
        val item = Item.create(id = 2400, registry = itemTypes, imbuements = imbuements)

        assertThat(item.imbuementRegistry).isSameAs(imbuements)
    }

    @Test
    fun `Item create without imbuements keeps the presence fallback`() {
        val item = Item.create(id = 2400, registry = itemTypes)
        item.setImbuement(0, 4242, 1000)

        assertThat(item.imbuementRegistry).isNull()
        assertThat(item.hasImbuements()).isTrue()
    }

    @Test
    fun `Item create validates packed imbuement ids through the assigned registry`() {
        val item = Item.create(id = 2400, registry = itemTypes, imbuements = imbuements)

        item.setImbuement(0, 200, 1000)
        assertThat(item.hasImbuements()).isFalse()

        item.setImbuement(0, knownId, 1000)
        assertThat(item.hasImbuements()).isTrue()
    }
}

private const val TEST_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="1" category="3" iconid="1">
        <attribute key="effect" type="skill" value="critical" bonus="1500" chance="1000"/>
    </imbuement>
</imbuements>
"""
