package org.opentibia.canary.io.pipeline

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.otbm.OtbmConstants
import org.opentibia.canary.map.Position
import java.io.File

class MapPipelineServiceTest {

    @Test
    fun `loads otbm binary map, links houses from xml, and registers spawns`() {
        val tempDir = File.createTempFile("canary_map_pipeline_", "").apply {
            delete()
            mkdirs()
        }

        try {
            // 1. Generate OTBM Binary Map
            val otbmFile = File(tempDir, "world.otbm")
            val buf = Unpooled.buffer()
            buf.writeInt(0) // signature
            buf.writeByte(OtbmConstants.NODE_START.toInt())
            buf.writeByte(OtbmConstants.OTBM_ROOT.toInt())
            buf.writeIntLE(2)

            buf.writeByte(OtbmConstants.NODE_START.toInt())
            buf.writeByte(OtbmConstants.OTBM_MAP_DATA.toInt())
            buf.writeByte(OtbmConstants.OTBM_ATTR_DESCRIPTION.toInt())
            buf.writeShortLE(4)
            buf.writeBytes("Test".toByteArray())

            // Tile Area
            buf.writeByte(OtbmConstants.NODE_START.toInt())
            buf.writeByte(OtbmConstants.OTBM_TILE_AREA.toInt())
            buf.writeShortLE(1000)
            buf.writeShortLE(1000)
            buf.writeByte(7)

            // Tile
            buf.writeByte(OtbmConstants.NODE_START.toInt())
            buf.writeByte(OtbmConstants.OTBM_TILE.toInt())
            buf.writeByte(0)
            buf.writeByte(0)
            buf.writeByte(OtbmConstants.OTBM_ATTR_ITEM.toInt())
            buf.writeShortLE(106) // grass
            buf.writeByte(OtbmConstants.NODE_END.toInt())

            buf.writeByte(OtbmConstants.NODE_END.toInt()) // tile area
            buf.writeByte(OtbmConstants.NODE_END.toInt()) // map data
            buf.writeByte(OtbmConstants.NODE_END.toInt()) // root

            val otbmBytes = ByteArray(buf.readableBytes())
            buf.readBytes(otbmBytes)
            otbmFile.writeBytes(otbmBytes)

            // 2. House XML
            val houseXml = File(tempDir, "world-house.xml").apply {
                writeText(
                    """
                    <houses>
                        <house id="1" name="Central Flat 1" entryx="1000" entryy="1000" entryz="7" rent="500" townid="1" size="16"/>
                    </houses>
                    """.trimIndent()
                )
            }

            // 3. Spawn XML
            val spawnXml = File(tempDir, "world-spawn.xml").apply {
                writeText(
                    """
                    <spawns>
                        <spawn centerx="1000" centery="1000" centerz="7" radius="5">
                            <monster name="Dragon" x="0" y="0" z="7" spawntime="60"/>
                        </spawn>
                    </spawns>
                    """.trimIndent()
                )
            }

            val service = MapPipelineService()
            val result = service.load(
                otbmFile = otbmFile,
                houseXmlFile = houseXml,
                spawnXmlFile = spawnXml
            )

            assertThat(result.map.getTile(Position(1000, 1000, 7))).isNotNull
            assertThat(result.houses).hasSize(1)
            assertThat(result.houses.first().name).isEqualTo("Central Flat 1")
            assertThat(result.spawns).hasSize(1)
            assertThat(result.spawns.first().monsterName).isEqualTo("Dragon")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
