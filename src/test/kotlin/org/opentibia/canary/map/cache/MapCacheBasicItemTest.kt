package org.opentibia.canary.map.cache

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.IOMapException
import org.opentibia.canary.map.cache.TestMapItemStream.Companion.bytes
import org.opentibia.canary.map.cache.TestMapItemStream.Companion.string

/**
 * Transcript tests for `struct BasicItem`
 * (src/map/mapcache.hpp:26-76, src/map/mapcache.cpp:474-585): the simple-item
 * predicate, the `stdext::hash_combine` hash and the `FileStream` attribute /
 * child-node parser.
 */
class MapCacheBasicItemTest {

    // Wire codes of `AttrTypes_t` (src/items/items_definitions.hpp:202-252).
    private val attrActionId = 4
    private val attrUniqueId = 5
    private val attrText = 6
    private val attrDesc = 7
    private val attrTeleDest = 8
    private val attrDepotId = 10
    private val attrHouseDoorId = 14
    private val attrCount = 15
    private val attrCharges = 22
    private val otbmItem = 0x06
    private val nodeStart = 0xFE
    private val nodeEnd = 0xFF

    @BeforeEach
    fun resetMapCacheStatics() {
        MapCache().flush()
    }

    @Test
    fun `defaults to a simple item`() {
        assertThat(BasicItem().isSimple()).isTrue()
    }

    @Test
    fun `is simple only when every attribute and child slot is empty`() {
        assertThat(BasicItem().apply { charges = 1 }.isSimple()).isFalse()
        assertThat(BasicItem().apply { actionId = 1 }.isSimple()).isFalse()
        assertThat(BasicItem().apply { uniqueId = 1 }.isSimple()).isFalse()
        assertThat(BasicItem().apply { destX = 1 }.isSimple()).isFalse()
        assertThat(BasicItem().apply { destY = 1 }.isSimple()).isFalse()
        assertThat(BasicItem().apply { destZ = 1 }.isSimple()).isFalse()
        assertThat(BasicItem().apply { doorOrDepotId = 1 }.isSimple()).isFalse()
        assertThat(BasicItem().apply { text = "a" }.isSimple()).isFalse()
        assertThat(BasicItem().apply { items.add(BasicItem()) }.isSimple()).isFalse()
    }

    @Test
    fun `hashes equal item trees equally`() {
        val first = BasicItem().apply {
            id = 100
            charges = 5
            items.add(BasicItem().apply { id = 200 })
        }
        val second = BasicItem().apply {
            id = 100
            charges = 5
            items.add(BasicItem().apply { id = 200 })
        }

        assertThat(first.hash()).isEqualTo(second.hash())
    }

    @Test
    fun `hashes different item trees differently`() {
        val baseline = BasicItem().apply { id = 100 }
        assertThat(BasicItem().apply { id = 101 }.hash()).isNotEqualTo(baseline.hash())
        assertThat(BasicItem().apply { id = 100; charges = 1 }.hash()).isNotEqualTo(baseline.hash())
        assertThat(BasicItem().apply { id = 100; text = "a" }.hash()).isNotEqualTo(baseline.hash())
        assertThat(
            BasicItem().apply {
                id = 100
                items.add(BasicItem().apply { id = 200 })
            }.hash(),
        ).isNotEqualTo(baseline.hash())
    }

    @Test
    fun `reads count and charges attributes`() {
        val count = BasicItem()
        count.readAttr(TestMapItemStream(bytes(attrCount, 7, 0)))
        assertThat(count.charges).isEqualTo(7)

        val charges = BasicItem()
        charges.readAttr(TestMapItemStream(bytes(attrCharges, 0x34, 0x12, 0)))
        assertThat(charges.charges).isEqualTo(0x1234)
    }

    @Test
    fun `reads action unique and destination attributes`() {
        val item = BasicItem()
        item.readAttr(
            TestMapItemStream(
                bytes(
                    attrActionId, 0x01, 0x02,
                    attrUniqueId, 0x03, 0x04,
                    attrTeleDest, 5, 0, 6, 0, 7,
                    0,
                ),
            ),
        )

        assertThat(item.actionId).isEqualTo(0x0201)
        assertThat(item.uniqueId).isEqualTo(0x0403)
        assertThat(item.destX).isEqualTo(5)
        assertThat(item.destY).isEqualTo(6)
        assertThat(item.destZ).isEqualTo(7)
    }

    @Test
    fun `reads depot and house door ids`() {
        val depot = BasicItem()
        depot.readAttr(TestMapItemStream(bytes(attrDepotId, 9, 8, 0)))
        assertThat(depot.doorOrDepotId).isEqualTo(0x0809)

        val door = BasicItem()
        door.readAttr(TestMapItemStream(bytes(attrHouseDoorId, 0x2A, 0)))
        assertThat(door.doorOrDepotId).isEqualTo(42)
    }

    @Test
    fun `reads text and ignores the description attribute`() {
        val item = BasicItem()
        item.readAttr(TestMapItemStream(bytes(attrDesc) + string("desc") + bytes(attrText) + string("hi") + bytes(0)))

        assertThat(item.text).isEqualTo("hi")
    }

    @Test
    fun `stops at the first unknown attribute and puts it back`() {
        val stream = TestMapItemStream(bytes(attrCount, 5, 0x42))
        val item = BasicItem()

        item.readAttr(stream)

        assertThat(item.charges).isEqualTo(5)
        assertThat(stream.getU8()).isEqualTo(0x42)
    }

    @Test
    fun `returns immediately when the item node already ended`() {
        val stream = TestMapItemStream(bytes(nodeEnd))
        val item = BasicItem()

        assertThat(item.unserializeItemNode(stream, 100, 100, 7)).isTrue()
        assertThat(item.items).isEmpty()
        assertThat(stream.getU8()).isEqualTo(nodeEnd)
    }

    @Test
    fun `parses simple child items through the map item cache`() {
        val stream = TestMapItemStream(
            bytes(attrCount, 3, nodeStart, otbmItem, 0x64, 0x00, nodeEnd, nodeEnd),
        )
        val item = BasicItem()

        assertThat(item.unserializeItemNode(stream, 100, 100, 7)).isTrue()

        assertThat(item.charges).isEqualTo(3)
        assertThat(item.items).hasSize(1)
        assertThat(item.items[0].id).isEqualTo(100)
        assertThat(item.items[0]).isSameAs(MapCache().getBasicItemFromCache(100))
    }

    @Test
    fun `reuses one cached instance for equal simple children`() {
        val stream = TestMapItemStream(
            bytes(
                nodeStart, otbmItem, 0x64, 0x00, nodeEnd,
                nodeStart, otbmItem, 0x64, 0x00, nodeEnd,
                nodeEnd,
            ),
        )
        val item = BasicItem()

        item.unserializeItemNode(stream, 100, 100, 7)

        assertThat(item.items).hasSize(2)
        assertThat(item.items[0]).isSameAs(item.items[1])
    }

    @Test
    fun `deduplicates non simple child items by hash`() {
        val stream = TestMapItemStream(
            bytes(
                nodeStart, otbmItem, 0x64, 0x00, attrCharges, 7, 0, nodeEnd,
                nodeStart, otbmItem, 0x64, 0x00, attrCharges, 7, 0, nodeEnd,
                nodeEnd,
            ),
        )
        val item = BasicItem()

        item.unserializeItemNode(stream, 100, 100, 7)

        assertThat(item.items).hasSize(2)
        assertThat(item.items[0].charges).isEqualTo(7)
        assertThat(item.items[0]).isSameAs(item.items[1])
    }

    @Test
    fun `rejects a child node that is not an item node`() {
        val stream = TestMapItemStream(bytes(nodeStart, 0x05, nodeEnd, nodeEnd))
        val item = BasicItem()

        assertThatThrownBy { item.unserializeItemNode(stream, 100, 100, 7) }
            .isInstanceOf(IOMapException::class.java)
    }

    @Test
    fun `parses nested container children recursively`() {
        val stream = TestMapItemStream(
            bytes(
                attrCount, 1,
                nodeStart, otbmItem, 0x64, 0x00,
                attrCount, 2,
                nodeStart, otbmItem, 0xC8, 0x00, nodeEnd,
                nodeEnd,
                nodeEnd,
            ),
        )
        val item = BasicItem()

        assertThat(item.unserializeItemNode(stream, 100, 100, 7)).isTrue()

        assertThat(item.items[0].id).isEqualTo(100)
        assertThat(item.items[0].items[0].id).isEqualTo(200)
        assertThat(item.items[0].charges).isEqualTo(2)
    }
}
