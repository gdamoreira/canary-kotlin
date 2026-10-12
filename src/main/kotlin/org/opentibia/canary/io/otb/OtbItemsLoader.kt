package org.opentibia.canary.io.otb

import io.netty.buffer.ByteBuf
import org.opentibia.canary.io.otbm.OtbmConstants
import org.opentibia.canary.io.otbm.OtbmNodeReader
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry

object OtbItemsLoader {

    const val ATTR_SERVER_ID: Byte = 0x10
    const val ATTR_CLIENT_ID: Byte = 0x11
    const val FLAG_STACKABLE: Int = 1 shl 3

    fun load(buffer: ByteBuf): ItemTypeRegistry {
        val rootNode = OtbmNodeReader.read(buffer)
        val registry = ItemTypeRegistry()

        for (itemNode in rootNode.children) {
            val props = itemNode.props
            val flags = if (props.readableBytes() >= 4) props.readIntLE() else 0
            val isStackable = (flags and FLAG_STACKABLE) != 0

            var serverId = 0
            var clientId = 0

            while (props.isReadable) {
                val attr = props.readByte()
                val dataLen = props.readUnsignedShortLE()
                when (attr) {
                    ATTR_SERVER_ID -> {
                        serverId = props.readUnsignedShortLE()
                    }
                    ATTR_CLIENT_ID -> {
                        clientId = props.readUnsignedShortLE()
                    }
                    else -> {
                        props.skipBytes(dataLen)
                    }
                }
            }

            if (serverId > 0) {
                val itemType = ItemType(
                    id = serverId,
                    name = "Item $serverId",
                    isStackable = isStackable
                )
                registry.register(itemType)
            }
        }

        return registry
    }
}
