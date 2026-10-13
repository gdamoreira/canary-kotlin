package org.opentibia.canary.items.functions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.items.ItemAttribute as ItemAttribute_t
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transcripts Canary's `ItemAttribute`/`Attributes`/`CustomAttribute` behaviour.
 *
 * Reference:
 * - src/items/functions/item/attribute.hpp/.cpp
 * - src/items/functions/item/custom_attribute.hpp/.cpp
 */
class ItemAttributesTest {

    @Test
    fun `Attributes classifies integer and string defaults`() {
        val integer = Attributes(ItemAttribute_t.ACTIONID)
        assertThat(integer.getDefaultValueForType(ItemAttribute_t.ACTIONID)).isEqualTo(0L)
        assertThat(integer.getInteger()).isEqualTo(0L)
        assertThat(integer.getString()).isEmpty()

        val string = Attributes(ItemAttribute_t.DESCRIPTION)
        assertThat(string.getDefaultValueForType(ItemAttribute_t.DESCRIPTION)).isEqualTo(0L)
        assertThat(string.getInteger()).isEqualTo(0L)
        assertThat(string.getString()).isEmpty()
    }

    @Test
    fun `Attributes stores and reads the matching variant`() {
        val integer = Attributes(ItemAttribute_t.ACTIONID)
        integer.setValue(1234L)
        assertThat(integer.getInteger()).isEqualTo(1234L)
        assertThat(integer.getString()).isEmpty()

        val string = Attributes(ItemAttribute_t.DESCRIPTION)
        string.setValue("a crystal coin")
        assertThat(string.getString()).isEqualTo("a crystal coin")
        assertThat(string.getInteger()).isEqualTo(0L)
    }

    @Test
    fun `ItemAttribute setAttribute ignores wrong classification and empty strings`() {
        val attributes = ItemAttribute()

        attributes.setAttribute(ItemAttribute_t.ACTIONID, "not a number")
        assertThat(attributes.hasAttribute(ItemAttribute_t.ACTIONID)).isFalse()

        attributes.setAttribute(ItemAttribute_t.DESCRIPTION, 42L)
        assertThat(attributes.hasAttribute(ItemAttribute_t.DESCRIPTION)).isFalse()

        attributes.setAttribute(ItemAttribute_t.DESCRIPTION, "")
        assertThat(attributes.hasAttribute(ItemAttribute_t.DESCRIPTION)).isFalse()
    }

    @Test
    fun `ItemAttribute setAttribute stores integer zero and string values`() {
        val attributes = ItemAttribute()

        attributes.setAttribute(ItemAttribute_t.ACTIONID, 0L)
        assertThat(attributes.hasAttribute(ItemAttribute_t.ACTIONID)).isTrue()
        assertThat(attributes.getAttributeValue(ItemAttribute_t.ACTIONID)).isEqualTo(0L)

        attributes.setAttribute(ItemAttribute_t.DESCRIPTION, "a crystal coin")
        assertThat(attributes.getAttributeString(ItemAttribute_t.DESCRIPTION)).isEqualTo("a crystal coin")
    }

    @Test
    fun `getAttributeString and getAttributeValue guard the classification`() {
        val attributes = ItemAttribute()
        attributes.setAttribute(ItemAttribute_t.ACTIONID, 7L)
        attributes.setAttribute(ItemAttribute_t.DESCRIPTION, "text")

        assertThat(attributes.getAttributeValue(ItemAttribute_t.DESCRIPTION)).isEqualTo(0L)
        assertThat(attributes.getAttributeString(ItemAttribute_t.ACTIONID)).isEmpty()
        assertThat(attributes.getAttributeValue(ItemAttribute_t.UNIQUEID)).isEqualTo(0L)
        assertThat(attributes.getAttributeString(ItemAttribute_t.TEXT)).isEmpty()
    }

    @Test
    fun `getAttribute returns null for absent attributes`() {
        val attributes = ItemAttribute()

        assertThat(attributes.getAttribute(ItemAttribute_t.ACTIONID)).isNull()
        assertThat(attributes.getAttributesByType(ItemAttribute_t.ACTIONID).getAttributeType())
            .isEqualTo(ItemAttribute_t.ACTIONID)
        assertThat(attributes.getAttribute(ItemAttribute_t.ACTIONID)?.getInteger()).isEqualTo(0L)
    }

    @Test
    fun `removeAttribute swaps the removed entry with the last`() {
        val attributes = ItemAttribute()
        attributes.setAttribute(ItemAttribute_t.ACTIONID, 1L)
        attributes.setAttribute(ItemAttribute_t.UNIQUEID, 2L)
        attributes.setAttribute(ItemAttribute_t.CHARGES, 3L)

        assertThat(attributes.removeAttribute(ItemAttribute_t.ACTIONID)).isTrue()
        assertThat(attributes.removeAttribute(ItemAttribute_t.ACTIONID)).isFalse()

        assertThat(attributes.getAttributeVector().map { it.getAttributeType() })
            .containsExactly(ItemAttribute_t.CHARGES, ItemAttribute_t.UNIQUEID)
        assertThat(attributes.getAttributeValue(ItemAttribute_t.CHARGES)).isEqualTo(3L)
        assertThat(attributes.getAttributeValue(ItemAttribute_t.UNIQUEID)).isEqualTo(2L)
    }

    @Test
    fun `hasAttribute reflects the vector contents`() {
        val attributes = ItemAttribute()
        assertThat(attributes.hasAttribute(ItemAttribute_t.ACTIONID)).isFalse()

        attributes.setAttribute(ItemAttribute_t.ACTIONID, 1L)
        assertThat(attributes.hasAttribute(ItemAttribute_t.ACTIONID)).isTrue()
    }

    @Test
    fun `setCustomAttribute keys are lowercased and keep map order`() {
        val attributes = ItemAttribute()

        attributes.setCustomAttribute("Zeta", 1L)
        attributes.setCustomAttribute("Alpha", 2L)
        attributes.setCustomAttribute("Beta", 3L)

        assertThat(attributes.getCustomAttributeMap().keys).containsExactly("alpha", "beta", "zeta")
        assertThat(attributes.getCustomAttribute("ZETA")?.getInteger()).isEqualTo(1L)
        assertThat(attributes.getCustomAttribute("missing")).isNull()
    }

    @Test
    fun `addCustomAttribute overwrites and removeCustomAttribute lowercases`() {
        val attributes = ItemAttribute()
        attributes.addCustomAttribute("Strength", CustomAttribute("Strength", 10L))
        assertThat(attributes.getCustomAttribute("strength")?.getInteger()).isEqualTo(10L)

        attributes.addCustomAttribute("Strength", CustomAttribute("Strength", 20L))
        assertThat(attributes.getCustomAttribute("strength")?.getInteger()).isEqualTo(20L)

        assertThat(attributes.removeCustomAttribute("STRENGTH")).isTrue()
        assertThat(attributes.removeCustomAttribute("Strength")).isFalse()
    }

    @Test
    fun `CustomAttribute accessors match the variant alternative`() {
        val integer = CustomAttribute("k", 8L)
        assertThat(integer.getStringKey()).isEqualTo("k")
        assertThat(integer.getInteger()).isEqualTo(8L)
        assertThat(integer.getString()).isEmpty()
        assertThat(integer.getDouble()).isEqualTo(0.0)
        assertThat(integer.getBool()).isFalse()
        assertThat(integer.hasValue<Long>()).isTrue()
        assertThat(integer.hasValue<String>()).isFalse()

        val string = CustomAttribute("k", "value")
        assertThat(string.getString()).isEqualTo("value")
        assertThat(string.getInteger()).isEqualTo(0L)

        val double = CustomAttribute("k", 1.5)
        assertThat(double.getDouble()).isEqualTo(1.5)
        assertThat(double.getInteger()).isEqualTo(0L)

        val bool = CustomAttribute("k", true)
        assertThat(bool.getBool()).isTrue()
        assertThat(bool.getDouble()).isEqualTo(0.0)

        assertThat(CustomAttribute().getInteger()).isEqualTo(0L)
    }

    @Test
    fun `CustomAttribute setValue only replaces the matching alternative`() {
        val integer = CustomAttribute("k", 1L)
        integer.setValue("ignored")
        integer.setValue(2.0)
        integer.setValue(true)
        assertThat(integer.getInteger()).isEqualTo(1L)

        integer.setValue(9L)
        assertThat(integer.getInteger()).isEqualTo(9L)

        val string = CustomAttribute("k", "a")
        string.setValue(5L)
        assertThat(string.getString()).isEqualTo("a")
        string.setValue("b")
        assertThat(string.getString()).isEqualTo("b")
    }

    @Test
    fun `getAttribute template clamps the integer like Canary`() {
        val attribute = CustomAttribute("k", 70_000L)
        assertThat(attribute.getAttribute<UShort>()).isEqualTo(4464.toUShort())
        assertThat(attribute.getAttribute<Short>()).isEqualTo(4464.toShort())
        assertThat(attribute.getAttribute<Int>()).isEqualTo(70_000)
        assertThat(attribute.getAttribute<Long>()).isEqualTo(70_000L)

        val huge = CustomAttribute("k", 5_000_000_000L)
        assertThat(huge.getAttribute<Int>()).isEqualTo(705_032_704)

        val string = CustomAttribute("k", "value")
        assertThat(string.getAttribute<String>()).isEqualTo("value")
        assertThat(string.getAttribute<Double>()).isEqualTo(0.0)
        assertThat(string.getAttribute<Boolean>()).isFalse()
    }

    @Test
    fun `serialize writes the variant tag and payload`() {
        val stream = PropWriteStream()

        CustomAttribute("s", "value").serialize(stream)
        assertThat(stream.getStream()).isEqualTo(byteArrayOf(1, 5, 0, 'v'.code.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'u'.code.toByte(), 'e'.code.toByte()))

        stream.clear()
        CustomAttribute("i", 0x0102030405060708L).serialize(stream)
        assertThat(stream.getStream()).isEqualTo(byteArrayOf(2, 8, 7, 6, 5, 4, 3, 2, 1))

        stream.clear()
        CustomAttribute("d", 1.0).serialize(stream)
        assertThat(stream.getStream()).isEqualTo(byteArrayOf(3) + ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(1.0).array())

        stream.clear()
        CustomAttribute("b", true).serialize(stream)
        assertThat(stream.getStream()).isEqualTo(byteArrayOf(4, 1))
    }

    @Test
    fun `unserialize reads every variant and fails on unknown tags`() {
        val cases = listOf(
            CustomAttribute("s", "value"),
            CustomAttribute("i", 123_456_789L),
            CustomAttribute("d", 2.5),
            CustomAttribute("b", false),
        )
        for (original in cases) {
            val writeStream = PropWriteStream()
            original.serialize(writeStream)

            val restored = CustomAttribute()
            assertThat(restored.unserialize(PropStream(writeStream.getStream()), "test")).isTrue()
            when {
                original.hasValue<String>() -> assertThat(restored.getString()).isEqualTo(original.getString())
                original.hasValue<Long>() -> assertThat(restored.getInteger()).isEqualTo(original.getInteger())
                original.hasValue<Double>() -> assertThat(restored.getDouble()).isEqualTo(original.getDouble())
                else -> assertThat(restored.getBool()).isEqualTo(original.getBool())
            }
        }

        assertThat(CustomAttribute().unserialize(PropStream(byteArrayOf(9)), "test")).isFalse()
        assertThat(CustomAttribute().unserialize(PropStream(byteArrayOf()), "test")).isFalse()
        assertThat(CustomAttribute().unserialize(PropStream(byteArrayOf(1, 4, 0, 'a'.code.toByte())), "test")).isFalse()
    }
}
