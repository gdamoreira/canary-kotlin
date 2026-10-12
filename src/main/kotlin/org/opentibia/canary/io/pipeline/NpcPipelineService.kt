package org.opentibia.canary.io.pipeline

import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.map.Position
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class NpcPipelineService {

    fun parse(xmlContent: String, spawnPos: Position): Npc? {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlContent.toByteArray()))
        val npcElem = doc.getElementsByTagName("npc").item(0) as? Element ?: return null

        val name = npcElem.getAttribute("name")
        val npc = Npc(
            id = System.currentTimeMillis(),
            name = name,
            position = spawnPos
        )

        val parameters = npcElem.getElementsByTagName("parameter")
        for (i in 0 until parameters.length) {
            val paramElem = parameters.item(i) as Element
            val key = paramElem.getAttribute("key")
            val value = paramElem.getAttribute("value")

            if (key == "shop_buyable" || key == "shop_sellable") {
                parseShopItems(npc, value)
            }
        }

        return npc
    }

    private fun parseShopItems(npc: Npc, shopString: String) {
        val items = shopString.split(";")
        for (raw in items) {
            val parts = raw.split(",")
            if (parts.size >= 3) {
                val itemName = parts[0].trim()
                val itemId = parts[1].trim().toIntOrNull() ?: continue
                val price = parts[2].trim().toIntOrNull() ?: continue
                npc.addShopItem(itemId, itemName, price)
            }
        }
    }
}
