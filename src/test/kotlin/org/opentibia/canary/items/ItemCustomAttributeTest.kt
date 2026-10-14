package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * Transcript tests for the `Item` custom-attribute store, mirroring
 * `Item::getCustomAttribute` / `setCustomAttribute` / `addCustomAttribute` /
 * `hasCustomAttribute` / `removeCustomAttribute` (src/items/functions/item/attribute.cpp:102-153),
 * the `CustomAttribute` variant helpers (src/items/functions/item/custom_attribute.cpp:34-153)
 * and the `ATTR_CUSTOM` / deprecated `ATTR_CUSTOM_ATTRIBUTES` serialization paths
 * (src/items/item.cpp:1240-1270, 1521-1536; AttrTypes_t from
 * src/items/items_definitions.hpp:202-252).
 */
class ItemCustomAttributeTest {

    private fun serialize(item: Item): ByteArray {
        val stream = PropWriteStream()
        item.serializeAttr(stream)
        return stream.getStream()
    }

    @Test
    fun `setCustomAttribute stores each variant and the typed getters read it back`() {
        val item = Item(id = 2400)

        item.setCustomAttribute("int", 42L)
        item.setCustomAttribute("string", "hello")
        item.setCustomAttribute("double", 2.5)
        item.setCustomAttribute("bool", true)

        assertThat(item.getCustomAttribute("int")?.getInteger()).isEqualTo(42L)
        assertThat(item.getCustomAttribute("string")?.getString()).isEqualTo("hello")
        assertThat(item.getCustomAttribute("double")?.getDouble()).isEqualTo(2.5)
        assertThat(item.getCustomAttribute("bool")?.getBool()).isTrue()
    }

    @Test
    fun `typed getters of a mismatched variant return the cpp default`() {
        val item = Item(id = 2400)
        item.setCustomAttribute("string", "hello")

        val attribute = item.getCustomAttribute("string")!!

        assertThat(attribute.getInteger()).isZero()
        assertThat(attribute.getDouble()).isZero()
        assertThat(attribute.getBool()).isFalse()
        assertThat(attribute.getStringKey()).isEqualTo("string")
        assertThat(attribute.hasValue<String>()).isTrue()
        assertThat(attribute.hasValue<Long>()).isFalse()
        assertThat(attribute.hasValue<Double>()).isFalse()
        assertThat(attribute.hasValue<Boolean>()).isFalse()
    }

    @Test
    fun `custom attribute lookup is case insensitive and stores lowercase keys`() {
        val item = Item(id = 2400)

        item.setCustomAttribute("UnWrapId", 7L)

        assertThat(item.getCustomAttribute("unWrapId")?.getInteger()).isEqualTo(7L)
        assertThat(item.getCustomAttribute("UNWRAPID")?.getInteger()).isEqualTo(7L)
        assertThat(item.getCustomAttributeMap().keys).containsExactly("unwrapid")
        assertThat(item.getCustomAttribute("unwrapid")?.getStringKey()).isEqualTo("UnWrapId")
    }

    @Test
    fun `getCustomAttribute returns null for an unknown key`() {
        val item = Item(id = 2400)

        assertThat(item.getCustomAttribute("missing")).isNull()
        assertThat(item.hasCustomAttribute()).isFalse()
    }

    @Test
    fun `addCustomAttribute stores a prebuilt attribute under the lowercased key`() {
        val item = Item(id = 2400)

        item.addCustomAttribute("Hireling", CustomAttribute("Hireling", 88L))

        assertThat(item.hasCustomAttribute()).isTrue()
        assertThat(item.getCustomAttribute("hireling")?.getInteger()).isEqualTo(88L)
    }

    @Test
    fun `removeCustomAttribute erases the entry and reports the cpp result`() {
        val item = Item(id = 2400)
        item.setCustomAttribute("shader", "rain")

        assertThat(item.removeCustomAttribute("SHADER")).isTrue()
        assertThat(item.getCustomAttribute("shader")).isNull()
        assertThat(item.removeCustomAttribute("shader")).isFalse()
        assertThat(item.hasCustomAttribute()).isFalse()
    }

    @Test
    fun `getCustomAttributeMap iterates in ascending key order like std map`() {
        val item = Item(id = 2400)
        item.setCustomAttribute("zeta", 1L)
        item.setCustomAttribute("alpha", 2L)
        item.setCustomAttribute("beta", 3L)

        assertThat(item.getCustomAttributeMap().keys).containsExactly("alpha", "beta", "zeta")
    }

    @Test
    fun `clone copies the custom attribute store`() {
        val item = Item(id = 2400)
        item.setCustomAttribute("shader", "snow")

        val copy = item.clone()

        assertThat(copy.getCustomAttribute("shader")?.getString()).isEqualTo("snow")
        assertThat(item.removeCustomAttribute("shader")).isTrue()
        assertThat(copy.getCustomAttribute("shader")?.getString()).isEqualTo("snow")
    }

    @Test
    fun `serializeAttr writes the ATTR_CUSTOM block with the canary wire format`() {
        val item = Item(id = 2400)
        item.setCustomAttribute("a", 5L)
        item.setCustomAttribute("b", "hi")

        assertThat(serialize(item)).containsExactly(
            41, // ATTR_CUSTOM
            2, 0, 0, 0, 0, 0, 0, 0, // size
            1, 0, 'a'.code.toByte(),
            2, // int64 variant
            5, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 'b'.code.toByte(),
            1, // string variant
            2, 0, 'h'.code.toByte(), 'i'.code.toByte(),
        )
    }

    @Test
    fun `serializeAttr writes no ATTR_CUSTOM block for an empty store`() {
        val item = Item(id = 2400)

        assertThat(serialize(item)).isEmpty()
    }

    @Test
    fun `custom attributes round-trip through serializeAttr and unserializeAttr`() {
        val item = Item(id = 2400)
        item.setCustomAttribute("int", 0x0102030405060708L)
        item.setCustomAttribute("double", 1.5)
        item.setCustomAttribute("bool", false)
        item.setCustomAttribute("string", "round trip")

        val bytes = serialize(item)
        val restored = Item(id = 2400)
        assertThat(restored.unserializeAttr(PropStream(bytes))).isTrue()

        assertThat(restored.getCustomAttribute("int")?.getInteger()).isEqualTo(0x0102030405060708L)
        assertThat(restored.getCustomAttribute("double")?.getDouble()).isEqualTo(1.5)
        assertThat(restored.getCustomAttribute("bool")?.getBool()).isFalse()
        assertThat(restored.getCustomAttribute("string")?.getString()).isEqualTo("round trip")
    }

    @Test
    fun `deprecated ATTR_CUSTOM_ATTRIBUTES wire code 37 migrates into the store`() {
        // 37, size=1, key="unWrapId", int64 variant=4242
        val bytes = byteArrayOf(
            37,
            1, 0, 0, 0, 0, 0, 0, 0,
            8, 0, 'u'.code.toByte(), 'n'.code.toByte(), 'W'.code.toByte(), 'r'.code.toByte(),
            'a'.code.toByte(), 'p'.code.toByte(), 'I'.code.toByte(), 'd'.code.toByte(),
            2, 0x92.toByte(), 0x10, 0, 0, 0, 0, 0, 0,
        )
        val item = Item(id = 2400)

        assertThat(item.unserializeAttr(PropStream(bytes))).isTrue()
        assertThat(item.getCustomAttribute("unwrapid")?.getInteger()).isEqualTo(4242L)
    }

    @Test
    fun `setImbuement packs the duration and id into the custom attribute store`() {
        val item = Item(id = 2400, type = ItemType(id = 2400, name = "Sword", imbuementSlot = 2))

        item.setImbuement(slot = 1, imbuementId = 300, duration = 72000)

        assertThat(item.hasImbuementAttribute("${ITEM_IMBUEMENT_SLOT + 1}")).isTrue()
        assertThat(item.getCustomAttribute("501")?.getInteger()).isEqualTo((72000L shl 8) or 300L)
        assertThat(item.getImbuementAttribute(1)).isEqualTo((72000L shl 8) or 300L)
        assertThat(item.getImbuementAttribute(0)).isNull()
    }

    @Test
    fun `clearImbuement and decayImbuementTime follow the setImbuement packing`() {
        val item = Item(id = 2400, type = ItemType(id = 2400, name = "Sword", imbuementSlot = 1))
        item.setImbuement(0, 42, 1000)

        item.decayImbuementTime(0, 42, 500)
        assertThat(item.getImbuementAttribute(0)).isEqualTo((500L shl 8) or 42L)

        item.clearImbuement(0, 42)
        assertThat(item.getImbuementAttribute(0)).isZero()
        assertThat(item.hasImbuements()).isFalse()
    }

    @Test
    fun `hasImbuements ignores absent and zeroed slots`() {
        val item = Item(id = 2400, type = ItemType(id = 2400, name = "Sword", imbuementSlot = 2))
        item.setImbuement(0, 42, 0)
        item.setImbuement(1, 43, 1000)

        assertThat(item.hasImbuements()).isTrue()

        item.clearImbuement(1, 43)
        assertThat(item.hasImbuements()).isFalse()
    }

    @Test
    fun `hasMarketAttributes rejects an imbued item`() {
        val item = Item(id = 2400, type = ItemType(id = 2400, name = "Sword", imbuementSlot = 1))
        assertThat(item.hasMarketAttributes()).isTrue()

        item.setImbuement(0, 42, 1000)

        assertThat(item.hasMarketAttributes()).isFalse()
    }
}
