package org.opentibia.canary.io.xml

import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

object XmlItemLoader {

    fun load(xmlContent: String, registry: ItemTypeRegistry) {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(xmlContent.toByteArray()))

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val itemElem = items.item(i) as Element
            val id = itemElem.getAttribute("id").toIntOrNull() ?: continue
            val name = itemElem.getAttribute("name")

            var weight = 0.0

            val attributes = itemElem.getElementsByTagName("attribute")
            for (j in 0 until attributes.length) {
                val attrElem = attributes.item(j) as Element
                val key = attrElem.getAttribute("key")
                val value = attrElem.getAttribute("value")

                when (key.lowercase()) {
                    "weight" -> weight = value.toDoubleOrNull() ?: 0.0
                }
            }

            val existing = registry.get(id)
            val updated = ItemType(
                id = id,
                name = if (name.isNotEmpty()) name else (existing?.name ?: "Item $id"),
                isStackable = existing?.isStackable ?: false,
                weight = if (weight > 0.0) weight else (existing?.weight ?: 0.0)
            )
            registry.register(updated)
        }
    }
}

data class Vocation(
    val id: Int,
    val name: String,
    val gainHp: Int,
    val gainMana: Int,
    val gainCap: Int
)

object XmlVocationLoader {

    fun load(xmlContent: String): List<Vocation> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(xmlContent.toByteArray()))

        val result = mutableListOf<Vocation>()
        val vocations = doc.getElementsByTagName("vocation")

        for (i in 0 until vocations.length) {
            val elem = vocations.item(i) as Element
            val id = elem.getAttribute("id").toIntOrNull() ?: continue
            val name = elem.getAttribute("name")
            val gainHp = elem.getAttribute("gainhp").toIntOrNull() ?: 5
            val gainMana = elem.getAttribute("gainmana").toIntOrNull() ?: 5
            val gainCap = elem.getAttribute("gaincap").toIntOrNull() ?: 10

            result.add(Vocation(id, name, gainHp, gainMana, gainCap))
        }

        return result
    }
}
