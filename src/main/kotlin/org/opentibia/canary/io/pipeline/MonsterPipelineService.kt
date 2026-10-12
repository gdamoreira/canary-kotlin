package org.opentibia.canary.io.pipeline

import org.opentibia.canary.items.Item
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

data class LootEntry(
    val itemId: Int,
    val countMax: Int = 1,
    val chanceBasisPoints: Int // 1..100,000 where 100,000 = 100%
)

data class MonsterTypeDefinition(
    val name: String,
    val experience: Long,
    val speed: Int,
    val maxHealth: Int,
    val lootTable: List<LootEntry>
)

object LootEngine {
    fun rollLoot(table: List<LootEntry>, random: Random = Random): List<Item> {
        val result = mutableListOf<Item>()
        for (entry in table) {
            val roll = random.nextInt(1, 100001)
            if (roll <= entry.chanceBasisPoints) {
                val count = if (entry.countMax > 1) random.nextInt(1, entry.countMax + 1) else 1
                result.add(Item(id = entry.itemId, count = count))
            }
        }
        return result
    }
}

class MonsterPipelineService {

    fun parse(xmlContent: String): MonsterTypeDefinition? {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlContent.toByteArray()))
        val monsterElem = doc.getElementsByTagName("monster").item(0) as? Element ?: return null

        val name = monsterElem.getAttribute("name")
        val exp = monsterElem.getAttribute("experience").toLongOrNull() ?: 0L
        val speed = monsterElem.getAttribute("speed").toIntOrNull() ?: 200

        var maxHp = 100
        val healthNodes = monsterElem.getElementsByTagName("health")
        if (healthNodes.length > 0) {
            val hElem = healthNodes.item(0) as Element
            maxHp = hElem.getAttribute("max").toIntOrNull() ?: 100
        }

        val lootList = mutableListOf<LootEntry>()
        val lootNodes = monsterElem.getElementsByTagName("loot")
        if (lootNodes.length > 0) {
            val lElem = lootNodes.item(0) as Element
            val itemNodes = lElem.getElementsByTagName("item")
            for (i in 0 until itemNodes.length) {
                val itElem = itemNodes.item(i) as Element
                val id = itElem.getAttribute("id").toIntOrNull() ?: continue
                val countmax = itElem.getAttribute("countmax").toIntOrNull() ?: 1
                val chance = itElem.getAttribute("chance").toIntOrNull() ?: 100000

                lootList.add(LootEntry(itemId = id, countMax = countmax, chanceBasisPoints = chance))
            }
        }

        return MonsterTypeDefinition(name, exp, speed, maxHp, lootList)
    }
}
