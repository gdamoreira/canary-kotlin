package org.opentibia.canary.io.pipeline

import io.netty.buffer.Unpooled
import org.opentibia.canary.io.otb.OtbItemsLoader
import org.opentibia.canary.io.xml.XmlItemLoader
import org.opentibia.canary.items.ItemTypeRegistry
import java.io.File

class ItemPipelineService {

    fun load(otbFile: File, xmlFile: File? = null): ItemTypeRegistry {
        val otbBytes = otbFile.readBytes()
        val buffer = Unpooled.wrappedBuffer(otbBytes)
        val registry = OtbItemsLoader.load(buffer)

        if (xmlFile != null && xmlFile.exists()) {
            XmlItemLoader.load(xmlFile.readText(), registry)
        }

        return registry
    }
}
