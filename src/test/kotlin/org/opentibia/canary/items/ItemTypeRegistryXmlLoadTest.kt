package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.appearances.Appearances
import java.io.File

/**
 * Transcript tests for `Items::loadFromXml` (items.cpp:281-317), `Items::parseItemNode`
 * (items.cpp:330-396) and `Items::reload` (items.cpp:131-140), including the
 * `id`/`fromid`/`toid` expansion, the reserved-sprite guard, duplicate detection,
 * name-map maintenance and the `ItemParse::initParse` dispatch seam.
 */
class ItemTypeRegistryXmlLoadTest {

    @Test
    fun `loads synthetic xml with id ranges names and duplicates`() {
        val registry = Items()
        // The protobuf stage defines the ids (and leaves the names empty), like Canary
        // where items.xml only augments the appearances table.
        registry.loadFromProtobuf(Appearances.load(appearancesMessage(
            appearance(id = 201, flags = cumulativeFlag()),
            appearance(id = 300, flags = cumulativeFlag()),
            appearance(id = 301, flags = cumulativeFlag()),
            appearance(id = 302, flags = cumulativeFlag()),
            appearance(id = 400, flags = cumulativeFlag()),
            appearance(id = 500, flags = cumulativeFlag()),
        )))

        val parsedKeys = mutableListOf<String>()
        val parser = ItemAttributeParse { key, _, _, _ ->
            parsedKeys += key
            key == "weight"
        }

        val xml = """
            <items>
                <item id="201" article="a" name="Test Sword" plural="Test Swords">
                    <attribute key="Weight" value="42"/>
                    <attribute key="unknownkey" value="1"/>
                    <attribute key="noValueAttribute"/>
                </item>
                <item fromid="300" toid="302" name="Range Item"/>
                <item fromid="400"/>
                <item id="500" name="First Name"/>
                <item id="500" name="Second Name"/>
            </items>
        """.trimIndent()

        assertThat(registry.loadFromXml(xml, parser)).isTrue()

        val sword = registry.getItemType(201)
        assertThat(sword.loaded).isTrue()
        assertThat(sword.name).isEqualTo("Test Sword")
        assertThat(sword.article).isEqualTo("a")
        assertThat(sword.pluralName).isEqualTo("Test Swords")
        assertThat(parsedKeys).containsExactly("weight", "unknownkey")

        for (id in 300..302) {
            assertThat(registry.getItemType(id).name).isEqualTo("Range Item")
        }

        assertThat(registry.getItemType(400).loaded).isFalse()

        assertThat(registry.getItemType(500).name).isEqualTo("First Name")
        assertThat(registry.getItemIdByName("first name")).isEqualTo(500)
        assertThat(registry.getItemIdByName("second name")).isEqualTo(0)
    }

    @Test
    fun `reserved sprite guard only applies below the first hundred ids without a proto item`() {
        val registry = Items()
        registry.loadFromXml(
            """
            <items>
                <item id="99" name="reserved sprite"/>
                <item id="202" name="Reserved Sprite"/>
            </items>
            """.trimIndent()
        )

        assertThat(registry.getItemType(99).name).isEqualTo("reserved sprite")
        assertThat(registry.getItemType(202).name).isEmpty()
        assertThat(registry.getItemType(202).loaded).isFalse()
    }

    @Test
    fun `parse item node replaces the previous name mapping`() {
        val registry = Items()
        registry.loadFromProtobuf(Appearances.load(appearancesMessage(
            appearance(id = 600, flags = cumulativeFlag(), name = "Old Name")
        )))
        assertThat(registry.getItemIdByName("old name")).isEqualTo(600)

        assertThat(registry.loadFromXml("<items><item id=\"600\" name=\"New Name\"/></items>")).isTrue()

        assertThat(registry.getItemIdByName("old name")).isEqualTo(0)
        assertThat(registry.getItemIdByName("new name")).isEqualTo(600)
        assertThat(registry.getItemType(600).name).isEqualTo("New Name")
    }

    @Test
    fun `load from xml tolerates a document without an items root and rejects malformed xml`() {
        val registry = Items()

        assertThat(registry.loadFromXml("<other/>")).isTrue()
        assertThat(registry.loadFromXml("not xml")).isFalse()
        assertThat(registry.loadFromXml(null)).isFalse()
    }

    @Test
    fun `loads the real items xml over the merged appearances`() {
        val registry = Items()
        registry.loadFromProtobuf(appearances)

        val xml = File("data/items/items.xml").readText()
        assertThat(registry.loadFromXml(xml, ItemAttributeParse { _, _, _, _ -> true })).isTrue()

        assertThat(registry.getItemType(1).name).isEqualTo("water")
        assertThat(registry.getItemType(3031).article).isEqualTo("a")
        assertThat(registry.getItemType(1988).name).isEqualTo("buried coffin")
        assertThat(registry.getItemType(1989).name).isEqualTo("buried coffin")
        assertThat(registry.getItemIdByName("buried coffin")).isEqualTo(1988)
        assertThat(registry.getItemType(2400).name).isEqualTo("round purple pillow")
    }

    @Test
    fun `reload clears and re-loads the protobuf and xml sources`() {
        val registry = Items()
        registry.register(ItemType(id = 65000, name = "sentinel"))

        val result = registry.reload(
            appearances,
            File("data/items/items.xml").readText(),
            attributeParse = ItemAttributeParse { _, _, _, _ -> true },
        )

        assertThat(result).isTrue()
        assertThat(registry.get(65000)).isNull()
        assertThat(registry.getItemType(3031).name).isEqualTo("gold coin")
        assertThat(registry.getItemType(1988).name).isEqualTo("buried coffin")
    }

    @Test
    fun `reload returns false when the xml content is missing but keeps the proto items`() {
        val registry = Items()

        assertThat(registry.reload(appearances, null)).isFalse()
        assertThat(registry.getItemType(3031).name).isEqualTo("gold coin")
    }

    @Test
    fun `reload accepts only the xml source`() {
        val registry = Items()

        // Ids 0-100 are fluid ids and bypass the reserved-sprite guard.
        assertThat(registry.reload(null, "<items><item id=\"1\" name=\"Only Xml\"/></items>")).isTrue()
        assertThat(registry.getItemType(1).name).isEqualTo("Only Xml")
        assertThat(registry.getItemType(3031).id).isEqualTo(0)
    }

    companion object {
        private val appearances: Appearances by lazy {
            Appearances.load(File("data/items/appearances.dat"))
        }
    }
}

private fun varint(value: Int): ByteArray {
    var remaining = value
    val bytes = ArrayList<Byte>()
    while (true) {
        val chunk = remaining and 0x7F
        remaining = remaining ushr 7
        if (remaining == 0) {
            bytes.add(chunk.toByte())
            return bytes.toByteArray()
        }
        bytes.add((chunk or 0x80).toByte())
    }
}

private fun varintField(field: Int, value: Int): ByteArray =
    varint((field shl 3) or 0) + varint(value)

private fun lengthDelimitedField(field: Int, payload: ByteArray): ByteArray =
    varint((field shl 3) or 2) + varint(payload.size) + payload

private fun appearancesMessage(vararg objects: ByteArray): ByteArray =
    objects.fold(ByteArray(0)) { message, objectAppearance ->
        message + lengthDelimitedField(1, objectAppearance)
    }

private fun appearance(id: Int, flags: ByteArray, name: String? = null): ByteArray {
    var message = varintField(1, id) + lengthDelimitedField(3, flags)
    if (name != null) {
        message += lengthDelimitedField(4, name.toByteArray(Charsets.UTF_8))
    }
    return message
}

private fun cumulativeFlag(): ByteArray = varintField(6, 1)
