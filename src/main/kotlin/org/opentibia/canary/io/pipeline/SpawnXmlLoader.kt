package org.opentibia.canary.io.pipeline

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.spawns.NPC_MAXSPAWN_INTERVAL
import org.opentibia.canary.creatures.spawns.NPC_MINSPAWN_INTERVAL
import org.opentibia.canary.creatures.spawns.SpawnMonster
import org.opentibia.canary.creatures.spawns.SpawnNpc
import org.opentibia.canary.creatures.spawns.SpawnsMonster
import org.opentibia.canary.creatures.spawns.SpawnsNpc
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/**
 * Port of the spawn-file parsing half of `SpawnsMonster::loadFromXML`
 * (src/creatures/monsters/spawns/spawn_monster.cpp:29) and
 * `SpawnsNpc::loadFromXml` (src/creatures/npcs/spawns/spawn_npc.cpp:25).
 *
 * The `<monsters>`/`<npcs>` roots, per-block `centerx/centery/centerz/radius`,
 * per-creature `x/y/direction/spawntime/weight` attributes, the `radius = -1`
 * default and the 1s-24h spawn interval window follow the C++ loader exactly.
 */
object SpawnXmlLoader {

    /** `DEFAULT_RESPAWN_TIME` config default (configmanager.cpp:246), in seconds. */
    const val DEFAULT_RESPAWN_TIME_SECONDS = 60

    fun loadMonsters(
        file: File,
        defaultRespawnTimeSeconds: Int = DEFAULT_RESPAWN_TIME_SECONDS,
        spawnFactory: (Position, Int) -> SpawnMonster,
    ): SpawnsMonster? {
        val doc = parse(file) ?: return null
        val spawns = SpawnsMonster()

        for (spawnMonsterNode in rootChildren(doc, "monsters")) {
            val centerPos = centerPosOf(spawnMonsterNode)
            val radius = radiusOf(spawnMonsterNode)

            val blockChildren = elementChildren(spawnMonsterNode)
            if (blockChildren.isEmpty()) {
                logger.warn { "[SpawnsMonster::loadFromXml] - Empty spawn at position: ${centerPos.toString()} with radius: $radius" }
                continue
            }

            val spawnMonster = spawnFactory(centerPos, radius)
            for (childMonsterNode in blockChildren) {
                if (!childMonsterNode.tagName.equals("monster", ignoreCase = true)) {
                    continue
                }
                if (!childMonsterNode.hasAttribute("name")) {
                    continue
                }

                val name = childMonsterNode.getAttribute("name")
                val direction = directionOf(childMonsterNode)
                val pos = childPosition(centerPos, childMonsterNode)
                val weight = childMonsterNode.getAttribute("weight").toIntOrNull() ?: 1

                var scheduleInterval = defaultRespawnTimeSeconds.toLong()
                if (childMonsterNode.hasAttribute("spawntime")) {
                    val xmlSpawnTime = childMonsterNode.getAttribute("spawntime").toLongOrNull() ?: 0L
                    if (xmlSpawnTime > 0) {
                        scheduleInterval = xmlSpawnTime
                    } else {
                        logger.warn {
                            "Invalid spawntime value '${childMonsterNode.getAttribute("spawntime")}' for monster '$name'. Setting to default respawn time: $scheduleInterval"
                        }
                    }
                } else {
                    logger.warn { "Missing spawntime attribute for monster '$name'. Setting to default respawn time: $scheduleInterval" }
                }

                spawnMonster.addMonster(name, pos, direction, scheduleInterval * 1000, weight)
            }

            spawns.addSpawnMonster(spawnMonster)
        }

        spawns.loaded = true
        return spawns
    }

    fun loadNpcs(
        file: File,
        spawnFactory: (Position, Int) -> SpawnNpc,
    ): SpawnsNpc? {
        val doc = parse(file) ?: return null
        val spawns = SpawnsNpc()

        spawns.setFileName(file.path)
        spawns.setLoaded(true)

        for (spawnNpcNode in rootChildren(doc, "npcs")) {
            val centerPos = centerPosOf(spawnNpcNode)
            val radius = radiusOf(spawnNpcNode)

            val blockChildren = elementChildren(spawnNpcNode)
            if (blockChildren.isEmpty()) {
                logger.warn { "[SpawnsNpc::loadFromXml] - Empty spawn at position: ${centerPos.toString()} with radius: $radius" }
                continue
            }

            val spawnNpc = spawnFactory(centerPos, radius)
            for (childNode in blockChildren) {
                if (!childNode.tagName.equals("npc", ignoreCase = true)) {
                    continue
                }
                if (!childNode.hasAttribute("name")) {
                    continue
                }

                val name = childNode.getAttribute("name")
                val direction = directionOf(childNode)
                val pos = childPosition(centerPos, childNode)
                val interval = (childNode.getAttribute("spawntime").toLongOrNull() ?: 0L) * 1000

                if (interval >= NPC_MINSPAWN_INTERVAL && interval <= NPC_MAXSPAWN_INTERVAL) {
                    spawnNpc.addNpc(name, pos, direction, interval)
                } else if (interval <= NPC_MINSPAWN_INTERVAL) {
                    logger.warn { "[SpawnsNpc::loadFromXml] - $name ${pos.toString()} spawntime can not be less than ${NPC_MINSPAWN_INTERVAL / 1000} seconds" }
                } else {
                    logger.warn { "[SpawnsNpc::loadFromXml] - $name ${pos.toString()} spawntime can not be more than ${NPC_MAXSPAWN_INTERVAL / 1000} seconds" }
                }
            }

            spawns.addSpawnNpc(spawnNpc)
        }

        return spawns
    }

    private fun parse(file: File): Document? {
        return try {
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(file.readBytes()))
        } catch (e: Exception) {
            logger.error(e) { "Failed to load spawn file ${file.path}" }
            null
        }
    }

    private fun rootChildren(doc: Document, rootTag: String): List<Element> {
        val root = doc.documentElement ?: return emptyList()
        if (!root.tagName.equals(rootTag, ignoreCase = true)) {
            return emptyList()
        }
        return elementChildren(root)
    }

    private fun elementChildren(node: Element): List<Element> {
        val children = mutableListOf<Element>()
        val childNodes = node.childNodes
        for (index in 0 until childNodes.length) {
            val child = childNodes.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
        }
        return children
    }

    private fun centerPosOf(element: Element): Position {
        return Position(
            element.getAttribute("centerx").toIntOrNull() ?: 0,
            element.getAttribute("centery").toIntOrNull() ?: 0,
            element.getAttribute("centerz").toIntOrNull() ?: 0,
        )
    }

    private fun childPosition(centerPos: Position, element: Element): Position {
        val xOffset = element.getAttribute("x").toIntOrNull() ?: 0
        val yOffset = element.getAttribute("y").toIntOrNull() ?: 0
        return Position(centerPos.x + xOffset, centerPos.y + yOffset, centerPos.z)
    }

    private fun radiusOf(element: Element): Int {
        if (!element.hasAttribute("radius")) {
            return -1
        }
        return element.getAttribute("radius").toIntOrNull() ?: 0
    }

    private fun directionOf(element: Element): Direction {
        if (!element.hasAttribute("direction")) {
            return Direction.North
        }
        val value = element.getAttribute("direction").toIntOrNull() ?: return Direction.North
        return Direction.entries.getOrElse(value) { Direction.North }
    }
}
