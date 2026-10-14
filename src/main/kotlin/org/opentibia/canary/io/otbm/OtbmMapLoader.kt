package org.opentibia.canary.io.otbm

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.io.IOMapException
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
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
        val rootNode = OtbmNodeReader.read(buffer)
        val map = GameMap()
        map.path = path

        val mapDataNode = rootNode.children.find { it.type == OtbmConstants.OTBM_MAP_DATA } ?: return map
        parseMapDataAttributes(mapDataNode.props, map)

        for (areaNode in mapDataNode.children.filter { it.type == OtbmConstants.OTBM_TILE_AREA }) {
            val areaProps = areaNode.props
            val baseX = areaProps.readUnsignedShortLE()
            val baseY = areaProps.readUnsignedShortLE()
            val baseZ = areaProps.readUnsignedByte().toInt()

            for (tileNode in areaNode.children.filter {
                it.type == OtbmConstants.OTBM_TILE || it.type == OtbmConstants.OTBM_HOUSETILE
            }) {
                val tileProps = tileNode.props
                val offsetX = tileProps.readUnsignedByte().toInt()
                val offsetY = tileProps.readUnsignedByte().toInt()
                val tilePos = Position(baseX + offsetX, baseY + offsetY, baseZ)
                val tile = map.getOrCreateTile(tilePos)

                if (tileNode.type == OtbmConstants.OTBM_HOUSETILE) {
                    val houseId = tileProps.readIntLE()
                    tile.houseId = houseId
                    val house = map.houses.addHouse(houseId)
                    house.addTile(tilePos)
                }

                while (tileProps.isReadable) {
                    when (tileProps.readByte()) {
                        OtbmConstants.OTBM_ATTR_TILE_FLAGS -> {
                            val flags = tileProps.readIntLE()
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
                            val id = tileProps.readUnsignedShortLE()
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

                for (childNode in tileNode.children) {
                    when (childNode.type) {
                        OtbmConstants.OTBM_ITEM -> {
                            val item = parseItem(childNode.props, itemTypes, imbuements)
                            val itemType = itemTypes?.get(item.id)
                            if (tile.isHouse() && (itemType?.isBed == true || itemType?.isTrashHolder == true)) {
                                // House beds and trash holders keep their map position
                            } else if (itemType?.isGroundTile == true) {
                                tile.ground = item
                            } else {
                                tile.addItem(item)
                            }
                        }

                        OtbmConstants.OTBM_TILE_ZONE -> {
                            val zoneProps = childNode.props
                            val zoneCount = zoneProps.readUnsignedShortLE()
                            for (index in 0 until zoneCount) {
                                val zoneId = zoneProps.readUnsignedShortLE()
                                if (zoneId == 0) {
                                    throw IOMapException(
                                        "[x:${tilePos.x}, y:${tilePos.y}, z:${tilePos.z}] Invalid zone id.",
                                    )
                                }
                                map.zones.getZone(zoneId)?.addPosition(tilePos)
                            }
                        }

                        else -> throw IOMapException(
                            "[x:${tilePos.x}, y:${tilePos.y}, z:${tilePos.z}] Could not read item/zone node.",
                        )
                    }
                }
            }
        }

        return map
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
