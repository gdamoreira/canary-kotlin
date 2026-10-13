package org.opentibia.canary.network.protocol.guild

import io.netty.buffer.ByteBuf

/**
 * Self-contained command DTO for the guild client opcode `0x9C`
 * (`ProtocolGame::parseEditGuildMessage`, src/server/network/protocol/protocolgame.cpp:3303).
 *
 * Canary reads the length-prefixed message and discards it; the decoded value is
 * kept here so the caller can route on it.
 */
data class EditGuildMessageCommand(
    val message: String,
) : GuildCommand {
    override val opcode: Int get() = GuildPacketParser.OPCODE_EDIT_GUILD_MESSAGE
}

/** Commands decoded from the guild client opcodes; [opcode] identifies the frame. */
sealed interface GuildCommand {
    val opcode: Int
}

/**
 * Decoder for the guild client opcodes. Every multi-byte field is Little-Endian,
 * matching `NetworkMessage::get<T>()` on the native wire order.
 *
 * Canary v3.6.0 only implements the guild message edit request below; it has no
 * guild-window server packets (guild membership is served outside the game
 * protocol), so there is no serializer counterpart.
 */
object GuildPacketParser {

    /** protocolgame.cpp:1899-1901 dispatches this opcode to `parseEditGuildMessage`. */
    const val OPCODE_EDIT_GUILD_MESSAGE: Int = 0x9C

    fun parse(opcode: Int, buffer: ByteBuf): GuildCommand? = when (opcode) {
        OPCODE_EDIT_GUILD_MESSAGE -> parseEditGuildMessage(buffer)
        else -> null
    }

    /**
     * 0x9C: reads a `uint16_t`-length-prefixed UTF-8 message
     * (`NetworkMessage::getString`, src/server/network/message/networkmessage.cpp:91).
     *
     * @return the decoded command, or `null` when the length prefix or the
     *   declared payload is not fully readable.
     */
    fun parseEditGuildMessage(buffer: ByteBuf): EditGuildMessageCommand? {
        if (buffer.readableBytes() < 2) return null
        val length = buffer.readUnsignedShortLE()
        if (buffer.readableBytes() < length) return null

        val bytes = ByteArray(length)
        buffer.readBytes(bytes)
        return EditGuildMessageCommand(String(bytes, Charsets.UTF_8))
    }
}
