package org.opentibia.canary.io.otbm

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

object OtbmConstants {
    const val NODE_START: Byte = 0xFE.toByte()
    const val NODE_END: Byte = 0xFF.toByte()
    const val ESCAPE: Byte = 0xFD.toByte()

    const val OTBM_ROOT: Byte = 0x00
    const val OTBM_MAP_DATA: Byte = 0x02
    const val OTBM_TILE_AREA: Byte = 0x04
    const val OTBM_TILE: Byte = 0x05

    const val OTBM_ATTR_DESCRIPTION: Byte = 0x01
    const val OTBM_ATTR_ITEM: Byte = 0x09
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

    fun load(buffer: ByteBuf): GameMap {
        val rootNode = OtbmNodeReader.read(buffer)
        val map = GameMap()

        val mapDataNode = rootNode.children.find { it.type == OtbmConstants.OTBM_MAP_DATA } ?: return map

        for (areaNode in mapDataNode.children.filter { it.type == OtbmConstants.OTBM_TILE_AREA }) {
            val areaProps = areaNode.props
            val baseX = areaProps.readUnsignedShortLE()
            val baseY = areaProps.readUnsignedShortLE()
            val baseZ = areaProps.readUnsignedByte().toInt()

            for (tileNode in areaNode.children.filter { it.type == OtbmConstants.OTBM_TILE }) {
                val tileProps = tileNode.props
                val offsetX = tileProps.readUnsignedByte().toInt()
                val offsetY = tileProps.readUnsignedByte().toInt()
                val tilePos = Position(baseX + offsetX, baseY + offsetY, baseZ)
                val tile = map.getOrCreateTile(tilePos)

                while (tileProps.isReadable) {
                    val attr = tileProps.readByte()
                    when (attr) {
                        OtbmConstants.OTBM_ATTR_ITEM -> {
                            val itemId = tileProps.readUnsignedShortLE()
                            tile.ground = Item(itemId)
                        }
                        else -> {
                            // unknown attribute
                        }
                    }
                }
            }
        }

        return map
    }
}
