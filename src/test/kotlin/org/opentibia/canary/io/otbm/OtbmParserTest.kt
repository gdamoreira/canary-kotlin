package org.opentibia.canary.io.otbm

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

class OtbmParserTest {

    @Test
    fun `parses binary OTBM node tree with escape bytes`() {
        val buf = Unpooled.buffer()
        // Format signature: 4 bytes identifier (0x00 0x00 0x00 0x00)
        buf.writeInt(0)

        // Root Node Start (0xFE)
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(OtbmConstants.OTBM_ROOT.toInt())
        buf.writeIntLE(2) // OTBM version 2

        // Child Node: OTBM_MAP_DATA (0xFE)
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(OtbmConstants.OTBM_MAP_DATA.toInt())

        // Attribute: Description (0x01) with escaped byte
        buf.writeByte(0x01) // ATTR_DESCRIPTION
        buf.writeShortLE(4)
        buf.writeByte(0x41) // 'A'
        // Escaped byte: ESCAPE (0xFD) followed by NODE_START (0xFE)
        buf.writeByte(OtbmConstants.ESCAPE.toInt())
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(0x42) // 'B'
        buf.writeByte(0x43) // 'C'

        // Close MAP_DATA Node (0xFF)
        buf.writeByte(OtbmConstants.NODE_END.toInt())

        // Close Root Node (0xFF)
        buf.writeByte(OtbmConstants.NODE_END.toInt())

        val root = OtbmNodeReader.read(buf)
        assertThat(root).isNotNull
        assertThat(root.type).isEqualTo(OtbmConstants.OTBM_ROOT)
        assertThat(root.children).hasSize(1)

        val mapDataNode = root.children.first()
        assertThat(mapDataNode.type).isEqualTo(OtbmConstants.OTBM_MAP_DATA)
    }

    @Test
    fun `loads tile areas and tiles into GameMap`() {
        val buf = Unpooled.buffer()
        buf.writeInt(0)

        // Root Node
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(OtbmConstants.OTBM_ROOT.toInt())
        buf.writeIntLE(2)

        // MAP DATA
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(OtbmConstants.OTBM_MAP_DATA.toInt())
        // Map dimensions attribute
        buf.writeByte(OtbmConstants.OTBM_ATTR_DESCRIPTION.toInt())
        buf.writeShortLE(4)
        buf.writeBytes("Test".toByteArray())

        // TILE AREA Node
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(OtbmConstants.OTBM_TILE_AREA.toInt())
        buf.writeShortLE(100) // Area X
        buf.writeShortLE(100) // Area Y
        buf.writeByte(7)     // Area Z

        // TILE Node
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(OtbmConstants.OTBM_TILE.toInt())
        buf.writeByte(2) // Offset X (100 + 2 = 102)
        buf.writeByte(3) // Offset Y (100 + 3 = 103)
        // Ground Item attribute
        buf.writeByte(OtbmConstants.OTBM_ATTR_ITEM.toInt())
        buf.writeShortLE(100) // Ground Item ID 100

        buf.writeByte(OtbmConstants.NODE_END.toInt()) // Close TILE
        buf.writeByte(OtbmConstants.NODE_END.toInt()) // Close TILE AREA
        buf.writeByte(OtbmConstants.NODE_END.toInt()) // Close MAP DATA
        buf.writeByte(OtbmConstants.NODE_END.toInt()) // Close ROOT

        val gameMap = OtbmMapLoader.load(buf)
        val loadedTile = gameMap.getTile(Position(102, 103, 7))

        assertThat(loadedTile).isNotNull
        assertThat(loadedTile?.ground?.id).isEqualTo(100)
    }
}
