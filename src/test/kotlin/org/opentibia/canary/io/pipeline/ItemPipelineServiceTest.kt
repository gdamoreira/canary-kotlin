package org.opentibia.canary.io.pipeline

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.otb.OtbItemsLoader
import org.opentibia.canary.io.otbm.OtbmConstants
import java.io.File

class ItemPipelineServiceTest {

    @Test
    fun `merges binary OTB items with XML item attributes`() {
        val tempDir = File.createTempFile("canary_item_pipeline_", "").apply {
            delete()
            mkdirs()
        }

        try {
            // 1. Generate OTB buffer: Item 2400 (Magic Sword), sprite ID 3280
            val otbFile = File(tempDir, "items.otb")
            val buf = Unpooled.buffer()
            buf.writeInt(0) // identifier
            buf.writeByte(OtbmConstants.NODE_START.toInt())
            buf.writeByte(0) // root
            buf.writeIntLE(1) // version

            buf.writeByte(OtbmConstants.NODE_START.toInt())
            buf.writeByte(1) // item
            buf.writeIntLE(0) // flags
            buf.writeByte(OtbItemsLoader.ATTR_SERVER_ID.toInt())
            buf.writeShortLE(2)
            buf.writeShortLE(2400) // Server ID 2400
            buf.writeByte(OtbItemsLoader.ATTR_CLIENT_ID.toInt())
            buf.writeShortLE(2)
            buf.writeShortLE(3280) // Client Sprite ID 3280
            buf.writeByte(OtbmConstants.NODE_END.toInt()) // item
            buf.writeByte(OtbmConstants.NODE_END.toInt()) // root

            val bytes = ByteArray(buf.readableBytes())
            buf.readBytes(bytes)
            otbFile.writeBytes(bytes)

            // 2. Generate items.xml: Attack 48, Defense 35, Weight 42.0
            val xmlFile = File(tempDir, "items.xml").apply {
                writeText(
                    """
                    <items>
                        <item id="2400" name="Magic Sword">
                            <attribute key="weight" value="42.0"/>
                            <attribute key="attack" value="48"/>
                            <attribute key="defense" value="35"/>
                        </item>
                    </items>
                    """.trimIndent()
                )
            }

            val service = ItemPipelineService()
            val registry = service.load(otbFile, xmlFile)

            val sword = registry.get(2400)
            assertThat(sword).isNotNull
            assertThat(sword?.name).isEqualTo("Magic Sword")
            assertThat(sword?.weight).isEqualTo(42.0)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
