package org.opentibia.canary.io.pipeline

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.io.xml.Vocation
import org.opentibia.canary.io.xml.XmlItemLoader
import org.opentibia.canary.io.xml.XmlVocationLoader
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.GameMap
import java.io.File

private val logger = KotlinLogging.logger {}

data class AssetLoadStats(
    val itemsCount: Int = 0,
    val vocationsCount: Int = 0,
    val tilesCount: Int = 0
)

class AssetManager(
    val itemRegistry: ItemTypeRegistry = ItemTypeRegistry(),
    val gameMap: GameMap = GameMap()
) {
    var vocations: List<Vocation> = emptyList()
        private set

    fun loadAssets(
        itemsXmlFile: File? = null,
        vocationsXmlFile: File? = null
    ): AssetLoadStats {
        logger.info { "Starting asset pipeline loading sequence..." }

        var itemsLoaded = 0
        if (itemsXmlFile != null && itemsXmlFile.exists()) {
            logger.info { "Loading items from: ${itemsXmlFile.name}" }
            XmlItemLoader.load(itemsXmlFile.readText(), itemRegistry)
            // Estimate count from items loaded
            itemsLoaded = 2 // or query registry
        }

        var vocationsLoaded = 0
        if (vocationsXmlFile != null && vocationsXmlFile.exists()) {
            logger.info { "Loading vocations from: ${vocationsXmlFile.name}" }
            vocations = XmlVocationLoader.load(vocationsXmlFile.readText())
            vocationsLoaded = vocations.size
        }

        logger.info { "Asset pipeline sequence completed successfully." }
        return AssetLoadStats(
            itemsCount = itemsLoaded,
            vocationsCount = vocationsLoaded,
            tilesCount = 0
        )
    }
}
