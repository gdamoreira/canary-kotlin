package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Self-contained commands produced by the container-browse client opcodes,
 * mirroring the Canary `Game` / `ProtocolGame` handlers
 * (src/server/network/protocol/protocolgame.cpp).
 */
sealed interface ContainerBrowseCommand {
    val opcode: Int
}

/**
 * Livestream-viewer container resend (client opcode `0xCA`), mirroring
 * `ProtocolGame::resendLivestreamViewerContainer` (protocolgame.cpp:12448-12462):
 * a container id byte used to resend the viewer's open container.
 *
 * Canary only reaches this parse on a livestream viewer whose payload is
 * exactly `UPDATE_CONTAINER_PAYLOAD_SIZE` (1) bytes (protocolgame.cpp:81,
 * 1660-1664); a normal client's `0xCA` is `parseExivaRestrictions`, which is
 * not ported (PARITY GAP).
 */
data class LivestreamViewerContainerCommand(
    val containerId: Int,
) : ContainerBrowseCommand {
    override val opcode: Int get() = ContainerBrowsePacketHandler.OPCODE_LIVESTREAM_VIEWER_CONTAINER
}

/**
 * Browse-field request (client opcode `0xCB`), mirroring
 * `ProtocolGame::parseBrowseField` (protocolgame.cpp:4506-4509): the field
 * `Position` handed to `Game::playerBrowseField`.
 */
data class BrowseFieldCommand(
    val position: Position,
) : ContainerBrowseCommand {
    override val opcode: Int get() = ContainerBrowsePacketHandler.OPCODE_BROWSE_FIELD
}

/**
 * Seek-in-container request (client opcode `0xCC`), mirroring
 * `ProtocolGame::parseSeekInContainer` (protocolgame.cpp:4511-4516): the
 * container id byte, the `uint16_t` index and the primary category byte handed
 * to `Game::playerSeekInContainer`.
 */
data class SeekInContainerCommand(
    val containerId: Int,
    val index: Int,
    val primaryType: Int,
) : ContainerBrowseCommand {
    override val opcode: Int get() = ContainerBrowsePacketHandler.OPCODE_SEEK_IN_CONTAINER
}

/**
 * Decoder for the container-browse client opcodes.
 *
 *  - `0xCA` `ProtocolGame::resendLivestreamViewerContainer` (protocolgame.cpp:12448)
 *  - `0xCB` `ProtocolGame::parseBrowseField` (protocolgame.cpp:4506)
 *  - `0xCC` `ProtocolGame::parseSeekInContainer` (protocolgame.cpp:4511)
 */
object ContainerBrowsePacketHandler {

    const val OPCODE_LIVESTREAM_VIEWER_CONTAINER: Int = 0xCA
    const val OPCODE_BROWSE_FIELD: Int = 0xCB
    const val OPCODE_SEEK_IN_CONTAINER: Int = 0xCC

    fun parse(opcode: Int, buffer: ByteBuf): ContainerBrowseCommand? = when (opcode) {
        OPCODE_LIVESTREAM_VIEWER_CONTAINER -> parseLivestreamViewerContainer(buffer)
        OPCODE_BROWSE_FIELD -> parseBrowseField(buffer)
        OPCODE_SEEK_IN_CONTAINER -> parseSeekInContainer(buffer)
        else -> null
    }

    /** 0xCA: reads the `uint8_t` container id. */
    fun parseLivestreamViewerContainer(buffer: ByteBuf): LivestreamViewerContainerCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        return LivestreamViewerContainerCommand(containerId = buffer.readUnsignedByte().toInt())
    }

    /** 0xCB: reads the field `Position`. */
    fun parseBrowseField(buffer: ByteBuf): BrowseFieldCommand? {
        if (buffer.readableBytes() < 5) {
            return null
        }
        val x = buffer.readUnsignedShortLE()
        val y = buffer.readUnsignedShortLE()
        val z = buffer.readUnsignedByte().toInt()
        return BrowseFieldCommand(position = Position(x, y, z))
    }

    /** 0xCC: reads the container id byte, the `uint16_t` index and the primary category byte. */
    fun parseSeekInContainer(buffer: ByteBuf): SeekInContainerCommand? {
        if (buffer.readableBytes() < 4) {
            return null
        }
        val containerId = buffer.readUnsignedByte().toInt()
        val index = buffer.readUnsignedShortLE()
        val primaryType = buffer.readUnsignedByte().toInt()
        return SeekInContainerCommand(containerId = containerId, index = index, primaryType = primaryType)
    }

    /**
     * `ProtocolGame::resendLivestreamViewerContainer` (protocolgame.cpp:12448-12462):
     * the requested container id resolves one of the viewer's open containers
     * and re-sends it through the session sink, or closes the window when the
     * id is not open.
     */
    fun applyLivestreamViewerContainer(player: Player, command: LivestreamViewerContainerCommand) {
        val sink = player.protocolSink ?: return
        val open = player.getOpenContainers()[command.containerId]
        val container = open?.container
        if (container == null) {
            sink.sendCloseContainer(command.containerId)
            return
        }

        sink.sendContainer(command.containerId, container, container.hasParent(), open.index)
    }
}
