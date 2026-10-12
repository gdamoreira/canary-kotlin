package org.opentibia.canary.io.pipeline

import io.netty.buffer.Unpooled
import org.opentibia.canary.creatures.Spawner
import org.opentibia.canary.io.otbm.OtbmMapLoader
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.house.House
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

data class MapPipelineResult(
    val map: GameMap,
    val houses: List<House>,
    val spawns: List<Spawner>
)

class MapPipelineService {

    fun load(
        otbmFile: File,
        houseXmlFile: File? = null,
        spawnXmlFile: File? = null
    ): MapPipelineResult {
        val mapBytes = otbmFile.readBytes()
        val buffer = Unpooled.wrappedBuffer(mapBytes)
        val gameMap = OtbmMapLoader.load(buffer)

        val houses = mutableListOf<House>()
        if (houseXmlFile != null && houseXmlFile.exists()) {
            val factory = DocumentBuilderFactory.newInstance()
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(houseXmlFile.readBytes()))
            val houseNodes = doc.getElementsByTagName("house")
            for (i in 0 until houseNodes.length) {
                val elem = houseNodes.item(i) as Element
                val id = elem.getAttribute("id").toIntOrNull() ?: continue
                val name = elem.getAttribute("name")
                val rent = elem.getAttribute("rent").toLongOrNull() ?: 0L
                val townId = elem.getAttribute("townid").toIntOrNull() ?: 0

                houses.add(House(id = id, name = name, rent = rent, townId = townId))
            }
        }

        val spawns = mutableListOf<Spawner>()
        if (spawnXmlFile != null && spawnXmlFile.exists()) {
            val factory = DocumentBuilderFactory.newInstance()
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(spawnXmlFile.readBytes()))
            val spawnNodes = doc.getElementsByTagName("spawn")
            for (i in 0 until spawnNodes.length) {
                val spawnElem = spawnNodes.item(i) as Element
                val cx = spawnElem.getAttribute("centerx").toIntOrNull() ?: 0
                val cy = spawnElem.getAttribute("centery").toIntOrNull() ?: 0
                val cz = spawnElem.getAttribute("centerz").toIntOrNull() ?: 7
                val radius = spawnElem.getAttribute("radius").toIntOrNull() ?: 5

                val monsters = spawnElem.getElementsByTagName("monster")
                for (j in 0 until monsters.length) {
                    val mElem = monsters.item(j) as Element
                    val monsterName = mElem.getAttribute("name")
                    val spawntime = mElem.getAttribute("spawntime").toIntOrNull() ?: 60

                    val spawner = Spawner(
                        monsterName = monsterName,
                        centerPos = Position(cx, cy, cz),
                        radius = radius,
                        respawnSeconds = spawntime,
                        map = gameMap
                    )
                    spawns.add(spawner)
                }
            }
        }

        return MapPipelineResult(map = gameMap, houses = houses, spawns = spawns)
    }
}
