package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Commands decoded from the private-channel client opcodes, mirroring the
 * Canary chat entry points:
 *
 *  - `0xAA` `Game::playerCreatePrivateChannel` (src/game/game.cpp:4324-4336)
 *  - `0xAB` `Game::playerChannelInvite`        (src/game/game.cpp:4338-4359)
 *  - `0xAC` `Game::playerChannelExclude`       (src/game/game.cpp:4361-4382)
 */
sealed interface ChannelCommand {
    val opcode: Int
}

/** `0xAA`: creates the sender's `CHANNEL_PRIVATE` channel; Canary reads no payload. */
object CreatePrivateChannelCommand : ChannelCommand {
    override val opcode: Int get() = ChannelPacketHandler.OPCODE_CREATE_PRIVATE_CHANNEL
}

/** `0xAB`: invites the named player to the sender's private channel. */
data class ChannelInviteCommand(
    val name: String,
) : ChannelCommand {
    override val opcode: Int get() = ChannelPacketHandler.OPCODE_CHANNEL_INVITE
}

/** `0xAC`: excludes the named player from the sender's private channel. */
data class ChannelExcludeCommand(
    val name: String,
) : ChannelCommand {
    override val opcode: Int get() = ChannelPacketHandler.OPCODE_CHANNEL_EXCLUDE
}

/**
 * Decoder for the private-channel client opcodes on the current protocol
 * profile. `parseChannelInvite`/`parseChannelExclude`
 * (src/server/network/protocol/protocolgame.cpp:2418-2432) read the
 * length-prefixed name and then consume a trailing `uint16_t` only when the
 * current payload leaves at least two unread bytes; every other profile is not
 * modeled (PARITY GAP). `Game::playerCreatePrivateChannel` reads nothing.
 */
object ChannelPacketHandler {

    /** protocolgame.cpp:1938-1940 dispatches this opcode to `playerCreatePrivateChannel`. */
    const val OPCODE_CREATE_PRIVATE_CHANNEL: Int = 0xAA

    /** protocolgame.cpp:1941-1943 dispatches this opcode to `parseChannelInvite`. */
    const val OPCODE_CHANNEL_INVITE: Int = 0xAB

    /** protocolgame.cpp:1944-1946 dispatches this opcode to `parseChannelExclude`. */
    const val OPCODE_CHANNEL_EXCLUDE: Int = 0xAC

    fun parse(opcode: Int, buffer: ByteBuf): ChannelCommand? = when (opcode) {
        OPCODE_CREATE_PRIVATE_CHANNEL -> CreatePrivateChannelCommand
        OPCODE_CHANNEL_INVITE -> readTargetName(buffer)?.let { ChannelInviteCommand(it) }
        OPCODE_CHANNEL_EXCLUDE -> readTargetName(buffer)?.let { ChannelExcludeCommand(it) }
        else -> null
    }

    /**
     * Reads `msg.getString()` plus the optional current-payload trailing
     * `uint16_t` (protocolgame.cpp:2419-2422).
     *
     * @return the decoded name, or `null` when the length prefix or the declared
     *   payload is not fully readable.
     */
    private fun readTargetName(buffer: ByteBuf): String? {
        val name = readString(buffer) ?: return null
        if (buffer.readableBytes() >= 2) {
            buffer.skipBytes(2)
        }
        return name
    }

    /** `NetworkMessage::getString` (src/server/network/message/networkmessage.cpp:91). */
    private fun readString(buffer: ByteBuf): String? {
        if (buffer.readableBytes() < 2) return null
        val length = buffer.readUnsignedShortLE()
        if (buffer.readableBytes() < length) return null

        val bytes = ByteArray(length)
        buffer.readBytes(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
