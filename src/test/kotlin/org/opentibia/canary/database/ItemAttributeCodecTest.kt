package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute

/**
 * Transcript tests for the shared `Item::serializeAttr` / `readAttr` codec
 * (src/items/item.cpp:912-1349, 1368-1539) focused on the `ATTR_CUSTOM` custom
 * attribute block (`AttrTypes_t::ATTR_CUSTOM = 41`,
 * src/items/items_definitions.hpp:202-252; `CustomAttribute` variants from
 * src/items/functions/item/custom_attribute.cpp:88-153).
 *
 * The custom attribute store is carried on [ItemAttribute.CUSTOM] as a
 * `Map<String, Any>` whose values are the four `CustomAttribute` variants
 * (`Long`, `String`, `Double`, `Boolean`), matching the
 * `Item::getCustomAttributeMap()` shape (src/items/functions/item/attribute.cpp:105).
 */
class ItemAttributeCodecTest {

    @Test
    fun `serializeItemAttributes writes the ATTR_CUSTOM block in canary wire format`() {
        val blob = serializeItemAttributes(
            count = 1,
            attributes = mapOf(
                ItemAttribute.CUSTOM to linkedMapOf<String, Any>(
                    "a" to 0x0102030405060708L,
                    "b" to "hi",
                    "c" to 1.5,
                    "d" to true,
                ),
            ),
        )

        assertThat(blob).containsExactly(
            41,
            4, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 'a'.code,
            2,
            8, 7, 6, 5, 4, 3, 2, 1,
            1, 0, 'b'.code,
            1,
            2, 0, 'h'.code, 'i'.code,
            1, 0, 'c'.code,
            3,
            0, 0, 0, 0, 0, 0, 0xF8, 0x3F,
            1, 0, 'd'.code,
            4,
            1,
        )
    }

    @Test
    fun `serializeItemAttributes writes no ATTR_CUSTOM block for an empty store`() {
        assertThat(serializeItemAttributes(1, emptyMap())).isEmpty()
        assertThat(
            serializeItemAttributes(1, mapOf(ItemAttribute.CUSTOM to emptyMap<String, Any>())),
        ).isEmpty()
    }

    @Test
    fun `serializeItemAttributes writes ATTR_CUSTOM after OWNER and before OBTAINCONTAINER`() {
        val blob = serializeItemAttributes(
            count = 1,
            attributes = mapOf(
                ItemAttribute.OWNER to 7L,
                ItemAttribute.CUSTOM to mapOf("a" to 1L),
                ItemAttribute.OBTAINCONTAINER to 2L,
            ),
        )

        assertThat(blob).containsExactly(
            43, 7, 0, 0, 0,
            41,
            1, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 'a'.code,
            2,
            1, 0, 0, 0, 0, 0, 0, 0,
            44, 2, 0, 0, 0,
        )
    }

    @Test
    fun `deserializeItemAttributes decodes every custom attribute variant`() {
        val blob = serializeItemAttributes(
            count = 1,
            attributes = mapOf(
                ItemAttribute.CUSTOM to linkedMapOf<String, Any>(
                    "int" to -42L,
                    "string" to "hello",
                    "double" to 2.5,
                    "bool" to false,
                ),
            ),
        )

        val attributes = deserializeItemAttributes(blob)

        assertThat(attributes[ItemAttribute.CUSTOM]).isEqualTo(
            mapOf(
                "int" to -42L,
                "string" to "hello",
                "double" to 2.5,
                "bool" to false,
            ),
        )
    }

    @Test
    fun `custom attributes round-trip next to regular item attributes`() {
        val attributes = mapOf<ItemAttribute, Any>(
            ItemAttribute.ACTIONID to 1234L,
            ItemAttribute.TEXT to "Excalibur",
            ItemAttribute.CUSTOM to mapOf(
                "500" to ((72000L shl 8) or 300L),
                "flag" to true,
            ),
        )

        assertThat(deserializeItemAttributes(serializeItemAttributes(1, attributes))).isEqualTo(attributes)
    }

    @Test
    fun `materializes a packed imbuement through Item unserializeAttr`() {
        val item = Item(id = 2400)
        val packed = (72000L shl 8) or 300L
        val blob = serializeItemAttributes(1, mapOf(ItemAttribute.CUSTOM to mapOf("500" to packed)))

        assertThat(item.unserializeAttr(PropStream(blob))).isTrue()

        assertThat(item.getImbuementAttribute(0)).isEqualTo(packed)
        assertThat(item.getCustomAttribute("500")?.getInteger()).isEqualTo(packed)
    }

    @Test
    fun `deserializeItemAttributes decodes the deprecated ATTR_CUSTOM_ATTRIBUTES wire code`() {
        // 37, size=1, key="unWrapId", int64 variant=4242
        val blob = byteArrayOf(
            37,
            1, 0, 0, 0, 0, 0, 0, 0,
            8, 0, 'u'.code.toByte(), 'n'.code.toByte(), 'W'.code.toByte(), 'r'.code.toByte(),
            'a'.code.toByte(), 'p'.code.toByte(), 'I'.code.toByte(), 'd'.code.toByte(),
            2, 0x92.toByte(), 0x10, 0, 0, 0, 0, 0, 0,
        )

        assertThat(deserializeItemAttributes(blob)[ItemAttribute.CUSTOM])
            .isEqualTo(mapOf("unwrapid" to 4242L))
    }

    @Test
    fun `deserializeItemAttributes rejects an unknown custom attribute variant`() {
        val blob = byteArrayOf(
            41,
            1, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 'a'.code.toByte(),
            9,
        )

        assertThatIllegalArgumentException().isThrownBy { deserializeItemAttributes(blob) }
    }

    @Test
    fun `deserializeItemAttributes rejects a truncated custom attribute payload`() {
        val blob = byteArrayOf(
            41,
            1, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 'a'.code.toByte(),
            2,
            1, 0, 0, 0,
        )

        assertThatIllegalArgumentException().isThrownBy { deserializeItemAttributes(blob) }
    }
}
