package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.readTibiaString

/**
 * Self-contained commands produced by the channel client opcodes, mirroring the
 * Canary `ProtocolGame` / `Game` handlers in
 * src/server/network/protocol/protocolgame.cpp.
 */
sealed interface ChannelCommand {
    val opcode: Int
}

/**
 * Request the channel list (client opcode `0x97`). Canary reads no payload and
 * calls `Game::playerRequestChannels` (protocolgame.cpp:1887-1889), which sends
 * `Player::sendChannelsDialog`.
 */
object RequestChannelsCommand : ChannelCommand {
    override val opcode: Int get() = ChannelPacketHandler.OPCODE_REQUEST_CHANNELS
}

/**
 * Join a chat channel (client opcode `0x98`), mirroring
 * `ProtocolGame::parseOpenChannel` (protocolgame.cpp:2434-2437): a `uint16_t`
 * channel id handed to `Game::playerOpenChannel` (game.cpp:4393).
 */
data class OpenChannelCommand(
    val channelId: Int,
) : ChannelCommand {
    override val opcode: Int get() = ChannelPacketHandler.OPCODE_OPEN_CHANNEL
}

/**
 * Leave a chat channel (client opcode `0x99`), mirroring
 * `ProtocolGame::parseCloseChannel` (protocolgame.cpp:2439-2442): a `uint16_t`
 * channel id handed to `Game::playerCloseChannel` (game.cpp:4415).
 */
data class CloseChannelCommand(
    val channelId: Int,
) : ChannelCommand {
    override val opcode: Int get() = ChannelPacketHandler.OPCODE_CLOSE_CHANNEL
}

/**
 * Open a private-message channel (client opcode `0x9A`), mirroring
 * `ProtocolGame::parseOpenPrivateChannel` (protocolgame.cpp:2444-2447): the
 * receiver name handed to `Game::playerOpenPrivateChannel` (game.cpp:4424).
 */
data class OpenPrivateChannelCommand(
    val receiver: String,
) : ChannelCommand {
    override val opcode: Int get() = ChannelPacketHandler.OPCODE_OPEN_PRIVATE_CHANNEL
}

/**
 * Close the NPC interaction channel (client opcode `0x9E`). Canary reads no
 * payload and calls `Game::playerCloseNpcChannel` (protocolgame.cpp:1905-1907),
 * which forwards `Npc::onPlayerCloseChannel` to the NPCs in view
 * (game.cpp:4443-4452).
 */
object CloseNpcChannelCommand : ChannelCommand {
    override val opcode: Int get() = ChannelPacketHandler.OPCODE_CLOSE_NPC_CHANNEL
}

/**
 * Decoder for the channel client opcodes. Multi-byte fields are Little-Endian,
 * matching `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0x97` `Game::playerRequestChannels`          (protocolgame.cpp:1887)
 *  - `0x98` `ProtocolGame::parseOpenChannel`       (protocolgame.cpp:2434)
 *  - `0x99` `ProtocolGame::parseCloseChannel`      (protocolgame.cpp:2439)
 *  - `0x9A` `ProtocolGame::parseOpenPrivateChannel` (protocolgame.cpp:2444)
 *  - `0x9E` `Game::playerCloseNpcChannel`          (protocolgame.cpp:1905)
 */
object ChannelPacketHandler {

    const val OPCODE_REQUEST_CHANNELS: Int = 0x97
    const val OPCODE_OPEN_CHANNEL: Int = 0x98
    const val OPCODE_CLOSE_CHANNEL: Int = 0x99
    const val OPCODE_OPEN_PRIVATE_CHANNEL: Int = 0x9A
    const val OPCODE_CLOSE_NPC_CHANNEL: Int = 0x9E

    fun parse(opcode: Int, buffer: ByteBuf): ChannelCommand? = when (opcode) {
        OPCODE_REQUEST_CHANNELS -> RequestChannelsCommand
        OPCODE_OPEN_CHANNEL -> parseOpenChannel(buffer)
        OPCODE_CLOSE_CHANNEL -> parseCloseChannel(buffer)
        OPCODE_OPEN_PRIVATE_CHANNEL -> parseOpenPrivateChannel(buffer)
        OPCODE_CLOSE_NPC_CHANNEL -> CloseNpcChannelCommand
        else -> null
    }

    /**
     * 0x98: reads the `uint16_t` channel id.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseOpenChannel(buffer: ByteBuf): OpenChannelCommand? {
        val channelId = readShort(buffer) ?: return null
        return OpenChannelCommand(channelId = channelId)
    }

    /**
     * 0x99: reads the `uint16_t` channel id.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseCloseChannel(buffer: ByteBuf): CloseChannelCommand? {
        val channelId = readShort(buffer) ?: return null
        return CloseChannelCommand(channelId = channelId)
    }

    /**
     * 0x9A: reads the length-prefixed receiver name.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseOpenPrivateChannel(buffer: ByteBuf): OpenPrivateChannelCommand? {
        val receiver = readString(buffer) ?: return null
        return OpenPrivateChannelCommand(receiver = receiver)
    }

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()

    /**
     * Mirrors `NetworkMessage::getString()`: a `uint16_t` Little-Endian byte
     * length followed by that many bytes, with an explicit bounds check instead
     * of trusting the frame.
     */
    private fun readString(buffer: ByteBuf): String? {
        if (buffer.readableBytes() < 2) return null
        val length = buffer.getUnsignedShortLE(buffer.readerIndex())
        if (buffer.readableBytes() < 2 + length) return null
        return buffer.readTibiaString()
    }
}
