package org.opentibia.canary.io.otbm

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.io.IOMapException
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.imbuement.Imbuements

object OtbmConstants {
    const val NODE_START: Byte = 0xFE.toByte()
    const val NODE_END: Byte = 0xFF.toByte()
    const val ESCAPE: Byte = 0xFD.toByte()

    const val OTBM_ROOT: Byte = 0x00
    const val OTBM_MAP_DATA: Byte = 0x02
    const val OTBM_TILE_AREA: Byte = 0x04
    const val OTBM_TILE: Byte = 0x05
    const val OTBM_ITEM: Byte = 0x06
    const val OTBM_HOUSETILE: Byte = 0x0E
    const val OTBM_TILE_ZONE: Byte = 0x13
    const val OTBM_TOWNS: Byte = 0x0C
    const val OTBM_TOWN: Byte = 0x0D
    const val OTBM_WAYPOINTS: Byte = 0x0F

    const val OTBM_ATTR_DESCRIPTION: Byte = 0x01
    const val OTBM_ATTR_TILE_FLAGS: Byte = 0x03
    const val OTBM_ATTR_ITEM: Byte = 0x09
    const val OTBM_ATTR_EXT_SPAWN_MONSTER_FILE: Byte = 0x0B
    const val OTBM_ATTR_EXT_HOUSE_FILE: Byte = 0x0D
    const val OTBM_ATTR_EXT_SPAWN_NPC_FILE: Byte = 0x17
    const val OTBM_ATTR_EXT_ZONE_FILE: Byte = 0x18
}

/** `OTBM_TileFlag_t` (src/io/io_definitions.hpp:72-77). */
private object OtbmTileFlag {
    const val PROTECTIONZONE = 1 shl 0
    const val NOPVPZONE = 1 shl 2
    const val NOLOGOUT = 1 shl 3
    const val PVPZONE = 1 shl 4
}

data class OtbmNode(
    val type: Byte,
    val props: ByteBuf,
    val children: MutableList<OtbmNode> = mutableListOf()
)

/**
 * Depth-first visitor for [OtbmNodeReader.readStreaming]. [props] is only valid
 * for the duration of the call; returning `false` skips the node's subtree.
 */
interface OtbmVisitor {
    fun startNode(type: Byte, props: ByteBuf): Boolean

    fun endNode(type: Byte) {}
}

object OtbmNodeReader {

    fun read(buffer: ByteBuf): OtbmNode {
        // Skip header bytes (e.g. 4-byte 0x00 identifier)
        if (buffer.readByte() != OtbmConstants.NODE_START) {
            buffer.skipBytes(3) // skip remaining header bytes to find first NODE_START
            if (buffer.readByte() != OtbmConstants.NODE_START) {
                throw IllegalStateException("Missing root NODE_START in OTBM stream")
            }
        }

        return parseNode(buffer)
    }

    /**
     * Streaming variant of [read] for the map loader: visits each node
     * depth-first without materializing the tree, so the retained memory is the
     * [GameMap] alone (the released otservbr world does not fit the tree in a
     * container heap).
     */
    fun readStreaming(buffer: ByteBuf, visitor: OtbmVisitor) {
        if (buffer.readByte() != OtbmConstants.NODE_START) {
            buffer.skipBytes(3)
            if (buffer.readByte() != OtbmConstants.NODE_START) {
                throw IllegalStateException("Missing root NODE_START in OTBM stream")
            }
        }
        visitNodeStreaming(buffer, visitor)
    }

    private fun visitNodeStreaming(buffer: ByteBuf, visitor: OtbmVisitor) {
        val type = unescapeByte(buffer)
        val props = Unpooled.buffer()
        var started = false
        var descend = false

        while (buffer.isReadable) {
            when (val b = buffer.readByte()) {
                OtbmConstants.NODE_START -> {
                    if (!started) {
                        descend = visitor.startNode(type, props)
                        started = true
                    }
                    if (descend) {
                        visitNodeStreaming(buffer, visitor)
                    } else {
                        skipNode(buffer)
                    }
                }
                OtbmConstants.NODE_END -> {
                    if (!started) {
                        visitor.startNode(type, props)
                    }
                    visitor.endNode(type)
                    props.release()
                    return
                }
                OtbmConstants.ESCAPE -> props.writeByte(buffer.readByte().toInt())
                else -> props.writeByte(b.toInt())
            }
        }

        props.release()
    }

    /** Consumes one node subtree without materializing it. */
    private fun skipNode(buffer: ByteBuf) {
        var depth = 1
        while (buffer.isReadable && depth > 0) {
            when (buffer.readByte()) {
                OtbmConstants.NODE_START -> depth++
                OtbmConstants.NODE_END -> depth--
                OtbmConstants.ESCAPE -> buffer.skipBytes(1)
            }
        }
    }

    private fun parseNode(buffer: ByteBuf): OtbmNode {
        val type = unescapeByte(buffer)
        val props = Unpooled.buffer()
        val children = mutableListOf<OtbmNode>()

        while (buffer.isReadable) {
            val b = buffer.readByte()
            when (b) {
                OtbmConstants.NODE_START -> {
                    children.add(parseNode(buffer))
                }
                OtbmConstants.NODE_END -> {
                    return OtbmNode(type, props, children)
                }
                OtbmConstants.ESCAPE -> {
                    props.writeByte(buffer.readByte().toInt())
                }
                else -> {
                    props.writeByte(b.toInt())
                }
            }
        }

        return OtbmNode(type, props, children)
    }

    private fun unescapeByte(buffer: ByteBuf): Byte {
        val b = buffer.readByte()
        return if (b == OtbmConstants.ESCAPE) buffer.readByte() else b
    }
}

object OtbmMapLoader {

    /**
     * Port of `IOMap::loadMap` / `IOMap::parseMapDataAttributes` /
     * `IOMap::parseTileArea` (src/io/iomap.cpp:48-261) over the parsed node
     * tree. [path] is `Map::path`, used to resolve the external file
     * attributes; [itemTypes] enables the `isGroundTile`/house classification
     * of tile items. [imbuements] is the `g_imbuements()` composition seam:
     * when supplied, every item created by the loader carries the registry so
     * `hasImbuements` resolves packed ids like `Item::getImbuementInfo`
     * (src/items/item.cpp:472-483) instead of the attribute-presence fallback.
     */
    fun load(
        buffer: ByteBuf,
        path: String = "",
        itemTypes: ItemTypeRegistry? = null,
        imbuements: Imbuements? = null,
    ): GameMap {
        val map = GameMap()
        map.path = path

        // Streaming state mirroring the C++ recursion locals
        // (`IOMap::parseTileArea`, src/io/iomap.cpp:130-261): the current tile
        // area base, the current tile and the open container stack.
        var baseX = 0
        var baseY = 0
        var baseZ = 0
        var currentTile: Tile? = null
        val containerStack = ArrayDeque<Container>()
        val containerFlags = ArrayDeque<Boolean>()

        OtbmNodeReader.readStreaming(
            buffer,
            object : OtbmVisitor {
                override fun startNode(type: Byte, props: ByteBuf): Boolean = when (type) {
                    OtbmConstants.OTBM_ROOT -> true

                    OtbmConstants.OTBM_MAP_DATA -> {
                        parseMapDataAttributes(props, map)
                        true
                    }

                    OtbmConstants.OTBM_TOWNS -> true

                    OtbmConstants.OTBM_TOWN -> {
                        parseTown(props, map)
                        false
                    }

                    OtbmConstants.OTBM_TILE_AREA -> {
                        baseX = props.readUnsignedShortLE()
                        baseY = props.readUnsignedShortLE()
                        baseZ = props.readUnsignedByte().toInt()
                        true
                    }

                    OtbmConstants.OTBM_TILE, OtbmConstants.OTBM_HOUSETILE -> {
                        currentTile = parseTile(props, type, map, baseX, baseY, baseZ, itemTypes, imbuements)
                        true
                    }

                    OtbmConstants.OTBM_ITEM -> {
                        val item = parseItem(props, itemTypes, imbuements)
                        val parent = containerStack.lastOrNull()
                        if (parent != null) {
                            parent.addItem(item)
                        } else {
                            val tile = currentTile
                            val itemType = itemTypes?.get(item.id)
                            if (tile != null) {
                                if (tile.isHouse() && (itemType?.isBed == true || itemType?.isTrashHolder == true)) {
                                    // House beds and trash holders keep their map position
                                } else if (itemType?.isGroundTile == true) {
                                    tile.ground = item
                                } else {
                                    tile.addItem(item)
                                }
                            }
                        }

                        val isContainer = item is Container
                        containerFlags.addLast(isContainer)
                        if (isContainer) {
                            containerStack.addLast(item as Container)
                        }
                        true
                    }

                    OtbmConstants.OTBM_TILE_ZONE -> {
                        parseTileZone(props, currentTile, map)
                        false
                    }

                    else -> {
                        // The tile-level unknown node keeps the C++ exception;
                        // a root-level sibling (waypoints) is skipped.
                        if (currentTile != null && type != OtbmConstants.OTBM_WAYPOINTS) {
                            val tilePos = currentTile!!.position
                            throw IOMapException(
                                "[x:${tilePos.x}, y:${tilePos.y}, z:${tilePos.z}] Could not read item/zone node.",
                            )
                        }
                        false
                    }
                }

                override fun endNode(type: Byte) {
                    when (type) {
                        OtbmConstants.OTBM_TILE, OtbmConstants.OTBM_HOUSETILE -> currentTile = null

                        OtbmConstants.OTBM_ITEM -> {
                            if (containerFlags.removeLastOrNull() == true) {
                                containerStack.removeLastOrNull()
                            }
                        }

                        else -> Unit
                    }
                }
            },
        )

        return map
    }

    /** `IOMap::parseTileArea` tile branch (src/io/iomap.cpp:130-215). */
    private fun parseTile(
        props: ByteBuf,
        type: Byte,
        map: GameMap,
        baseX: Int,
        baseY: Int,
        baseZ: Int,
        itemTypes: ItemTypeRegistry?,
        imbuements: Imbuements?,
    ): Tile {
        val offsetX = props.readUnsignedByte().toInt()
        val offsetY = props.readUnsignedByte().toInt()
        val tilePos = Position(baseX + offsetX, baseY + offsetY, baseZ)
        val tile = map.getOrCreateTile(tilePos)

        if (type == OtbmConstants.OTBM_HOUSETILE) {
            val houseId = props.readIntLE()
            tile.houseId = houseId
            val house = map.houses.addHouse(houseId)
            house.addTile(tilePos)
        }

        while (props.isReadable) {
            when (props.readByte()) {
                OtbmConstants.OTBM_ATTR_TILE_FLAGS -> {
                    val flags = props.readIntLE()
                    if (flags and OtbmTileFlag.PROTECTIONZONE != 0) {
                        tile.flags = tile.flags or TileFlags.TILESTATE_PROTECTIONZONE.value
                    } else if (flags and OtbmTileFlag.NOPVPZONE != 0) {
                        tile.flags = tile.flags or TileFlags.TILESTATE_NOPVPZONE.value
                    } else if (flags and OtbmTileFlag.PVPZONE != 0) {
                        tile.flags = tile.flags or TileFlags.TILESTATE_PVPZONE.value
                    }

                    if (flags and OtbmTileFlag.NOLOGOUT != 0) {
                        tile.flags = tile.flags or TileFlags.TILESTATE_NOLOGOUT.value
                    }
                }

                OtbmConstants.OTBM_ATTR_ITEM -> {
                    val id = props.readUnsignedShortLE()
                    val itemType = itemTypes?.get(id)
                    val item = Item(id, type = itemType)
                    if (imbuements != null) {
                        item.imbuementRegistry = imbuements
                    }
                    if (itemTypes == null || itemType?.isGroundTile == true) {
                        tile.ground = item
                    } else {
                        tile.addItem(item)
                    }
                }

                else -> break
            }
        }

        return tile
    }

    /** `IOMap::parseTileArea` zone branch (src/io/iomap.cpp:216-246). */
    private fun parseTileZone(props: ByteBuf, tile: Tile?, map: GameMap) {
        val zoneCount = props.readUnsignedShortLE()
        for (index in 0 until zoneCount) {
            val zoneId = props.readUnsignedShortLE()
            if (zoneId == 0) {
                val tilePos = tile?.position ?: Position(0, 0, 0)
                throw IOMapException("[x:${tilePos.x}, y:${tilePos.y}, z:${tilePos.z}] Invalid zone id.")
            }
            tile?.let { map.zones.getZone(zoneId)?.addPosition(it.position) }
        }
    }

    /** `IOMap::parseTowns` town branch (src/io/iomap.cpp:263-287). */
    private fun parseTown(props: ByteBuf, map: GameMap) {
        val townId = props.readUnsignedIntLE().toInt()
        val townName = readString(props)
        val x = props.readUnsignedShortLE().toInt()
        val y = props.readUnsignedShortLE().toInt()
        val z = props.readUnsignedByte().toInt()

        val town = map.towns.getOrCreateTown(townId)
        town.setName(townName)
        town.setTemplePos(Position(x, y, z))
    }

    /** `IOMap::parseMapDataAttributes` (src/io/iomap.cpp:93-127). */
    private fun parseMapDataAttributes(props: ByteBuf, map: GameMap) {
        while (props.isReadable) {
            when (props.readByte()) {
                OtbmConstants.OTBM_ATTR_DESCRIPTION -> readString(props)

                OtbmConstants.OTBM_ATTR_EXT_SPAWN_MONSTER_FILE -> {
                    map.monsterfile = resolveExternalFile(map.path, readString(props))
                }

                OtbmConstants.OTBM_ATTR_EXT_SPAWN_NPC_FILE -> {
                    map.npcfile = resolveExternalFile(map.path, readString(props))
                }

                OtbmConstants.OTBM_ATTR_EXT_HOUSE_FILE -> {
                    map.housefile = resolveExternalFile(map.path, readString(props))
                }

                OtbmConstants.OTBM_ATTR_EXT_ZONE_FILE -> {
                    map.zonesfile = resolveExternalFile(map.path, readString(props))
                }

                else -> break
            }
        }
    }

    /** `map->path.string().substr(0, map->path.string().rfind('/') + 1) + string`. */
    private fun resolveExternalFile(path: String, file: String): String {
        val separator = path.lastIndexOf('/')
        return path.substring(0, separator + 1) + file
    }

    private fun parseItem(props: ByteBuf, itemTypes: ItemTypeRegistry?, imbuements: Imbuements?): Item {
        val id = props.readUnsignedShortLE()
        val item = Item.create(id, registry = itemTypes, imbuements = imbuements)
        val remaining = ByteArray(props.readableBytes())
        props.readBytes(remaining)
        item.unserializeAttr(PropStream(remaining))
        return item
    }

    private fun readString(props: ByteBuf): String {
        val length = props.readUnsignedShortLE()
        val bytes = ByteArray(length)
        props.readBytes(bytes)
        return String(bytes)
    }
}
