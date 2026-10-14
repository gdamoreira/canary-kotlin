package org.opentibia.canary.io.otbm

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.ITEM_IMBUEMENT_SLOT
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * `IOMap::loadMap` / `IOMap::parseTileArea` item-creation hub
 * (src/io/iomap.cpp:48-261): every item created by the map loader must carry
 * the `g_imbuements()` composition seam so `Item::hasImbuements`
 * (src/items/item.hpp:728-737) resolves the packed id through
 * `Item::getImbuementInfo` (src/items/item.cpp:472-483) instead of the
 * attribute-presence fallback.
 */
class OtbmMapLoaderImbuementRegistryTest {

    private val imbuements = Imbuements().apply {
        assertThat(loadFromXml(TEST_XML)).isTrue()
    }
    private val itemTypes = ItemTypeRegistry().apply {
        register(ItemType(106, "grass", isGroundTile = true))
        register(ItemType(2400, "sword", isMovable = true, imbuementSlot = 2))
    }

    private fun ByteBuf.node(type: Int, props: ByteBuf.() -> Unit) {
        writeByte(OtbmConstants.NODE_START.toInt())
        writeByte(type)
        props()
        writeByte(OtbmConstants.NODE_END.toInt())
    }

    private fun buildMap(mapData: ByteBuf.() -> Unit): ByteBuf {
        val buf = Unpooled.buffer()
        buf.writeInt(0)
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(OtbmConstants.OTBM_ROOT.toInt())
        buf.writeIntLE(2)
        buf.node(OtbmConstants.OTBM_MAP_DATA.toInt(), mapData)
        buf.writeByte(OtbmConstants.NODE_END.toInt())
        return buf
    }

    /** `Item::setImbuement` (src/items/item.cpp:485) wire encoding as `ATTR_CUSTOM` (41). */
    private fun ByteBuf.writeImbuementAttribute(slot: Int, imbuementId: Int, duration: Int) {
        val key = "${ITEM_IMBUEMENT_SLOT + slot}"
        writeByte(41)
        writeLongLE(1L)
        writeShortLE(key.length)
        writeBytes(key.toByteArray())
        writeByte(2)
        writeLongLE((duration.toLong() shl 8) or imbuementId.toLong())
    }

    private fun itemNodeMap(itemProps: ByteBuf.() -> Unit): ByteBuf = buildMap {
        node(OtbmConstants.OTBM_TILE_AREA.toInt()) {
            writeShortLE(50)
            writeShortLE(60)
            writeByte(7)
            node(OtbmConstants.OTBM_TILE.toInt()) {
                writeByte(0)
                writeByte(0)
                node(OtbmConstants.OTBM_ITEM.toInt(), itemProps)
            }
        }
    }

    @Test
    fun `OTBM item child nodes receive the assigned imbuement registry`() {
        val buffer = itemNodeMap {
            writeShortLE(2400)
            writeImbuementAttribute(slot = 0, imbuementId = 200, duration = 1000)
        }

        val map = OtbmMapLoader.load(buffer, itemTypes = itemTypes, imbuements = imbuements)
        val item = map.getTile(Position(50, 60, 7))?.items?.single()

        assertThat(item?.imbuementRegistry).isSameAs(imbuements)
        assertThat(item?.hasImbuements()).isFalse()
    }

    @Test
    fun `tile ground items receive the assigned imbuement registry`() {
        val buffer = buildMap {
            node(OtbmConstants.OTBM_TILE_AREA.toInt()) {
                writeShortLE(10)
                writeShortLE(20)
                writeByte(7)
                node(OtbmConstants.OTBM_TILE.toInt()) {
                    writeByte(0)
                    writeByte(0)
                    writeByte(OtbmConstants.OTBM_ATTR_ITEM.toInt())
                    writeShortLE(106)
                }
            }
        }

        val map = OtbmMapLoader.load(buffer, itemTypes = itemTypes, imbuements = imbuements)

        assertThat(map.getTile(Position(10, 20, 7))?.ground?.imbuementRegistry).isSameAs(imbuements)
    }

    @Test
    fun `without a registry the map-loaded item keeps the presence fallback`() {
        val buffer = itemNodeMap {
            writeShortLE(2400)
            writeImbuementAttribute(slot = 0, imbuementId = 200, duration = 1000)
        }

        val map = OtbmMapLoader.load(buffer, itemTypes = itemTypes)
        val item = map.getTile(Position(50, 60, 7))?.items?.single()

        assertThat(item?.imbuementRegistry).isNull()
        assertThat(item?.hasImbuements()).isTrue()
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
