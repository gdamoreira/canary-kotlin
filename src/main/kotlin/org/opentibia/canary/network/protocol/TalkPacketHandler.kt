package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.network.message.readTibiaString
import java.nio.charset.StandardCharsets

/**
 * Self-contained command produced by the client opcode `0x96`
 * (`ProtocolGame::parseSay`, src/server/network/protocol/protocolgame.cpp:2711).
 *
 * [type] is Canary's `SpeakClasses` byte, [receiver] is only populated for the
 * `TALKTYPE_PRIVATE_TO` / `TALKTYPE_PRIVATE_RED_TO` variants and [channelId] only for the
 * `TALKTYPE_CHANNEL_Y` / `TALKTYPE_CHANNEL_R1` variants (0 otherwise).
 */
data class SayCommand(
    val type: Int,
    val receiver: String,
    val channelId: Int,
    val text: String,
)

/**
 * Decoder for the say request (client opcode `0x96`).
 *
 * Mirrors the exact read order of `ProtocolGame::parseSay`:
 *
 *   1. one byte `SpeakClasses` type;
 *   2. for `TALKTYPE_PRIVATE_TO` (5) / `TALKTYPE_PRIVATE_RED_TO` (16): a string receiver;
 *      for `TALKTYPE_CHANNEL_Y` (7) / `TALKTYPE_CHANNEL_R1` (14): a `uint16_t` channel id;
 *      every other type reads neither (channel id 0);
 *   3. the length-prefixed text string.
 *
 * Canary aborts without dispatching when the text is longer than 255 bytes, so such a
 * payload decodes to `null` as well (src/server/network/protocol/protocolgame.cpp:2733).
 */
object TalkPacketParser {

    private const val TALKTYPE_PRIVATE_TO = 5
    private const val TALKTYPE_CHANNEL_Y = 7
    private const val TALKTYPE_CHANNEL_R1 = 14
    private const val TALKTYPE_PRIVATE_RED_TO = 16
    private const val MAX_TEXT_LENGTH = 255

    /**
     * Decodes [payload] into a [SayCommand].
     *
     * @return the decoded command, or `null` when the buffer is truncated or the text
     *   exceeds [MAX_TEXT_LENGTH] bytes.
     */
    fun parse(payload: ByteBuf): SayCommand? {
        if (payload.readableBytes() < 1) {
            return null
        }

        val type = payload.readUnsignedByte().toInt()
        var receiver = ""
        var channelId = 0

        when (type) {
            TALKTYPE_PRIVATE_TO, TALKTYPE_PRIVATE_RED_TO -> {
                receiver = readString(payload) ?: return null
            }
            TALKTYPE_CHANNEL_Y, TALKTYPE_CHANNEL_R1 -> {
                if (payload.readableBytes() < 2) {
                    return null
                }
                channelId = payload.readUnsignedShortLE()
            }
        }

        val text = readString(payload) ?: return null
        if (text.toByteArray(StandardCharsets.UTF_8).size > MAX_TEXT_LENGTH) {
            return null
        }

        return SayCommand(type = type, receiver = receiver, channelId = channelId, text = text)
    }

    /**
     * Mirrors `NetworkMessage::getString()`: a `uint16_t` Little-Endian byte length followed
     * by that many bytes, with an explicit bounds check instead of trusting the frame.
     */
    private fun readString(payload: ByteBuf): String? {
        if (payload.readableBytes() < 2) {
            return null
        }
        val length = payload.getUnsignedShortLE(payload.readerIndex())
        if (payload.readableBytes() < 2 + length) {
            return null
        }
        return payload.readTibiaString()
    }
}

/**
 * Handles the talkaction branch of the say packet (client opcode `0x96`).
 *
 * After [TalkPacketParser] decodes the packet, the first word of the text is the talkaction
 * command and the remainder (leading spaces trimmed, as `TalkActions::checkWord` does in
 * src/lua/creature/talkaction.cpp:71-77) is the parameter. Lookup and invocation are
 * delegated to [ScriptingDispatch.talkActionOnSay], which confines the `onSay` callback to
 * the `WorldCommit` lane (AGENTS.md 1.2) and returns `false` when no talkaction is
 * registered for the command.
 */
object TalkPacketHandler {

    /** Client opcode for `ProtocolGame::parseSay`. */
    const val OPCODE_SAY = 0x96

    fun handleSay(
        player: Player,
        payload: ByteBuf,
        dispatcher: GameDispatcher,
        luaEnvironment: LuaEnvironment,
    ): Boolean {
        val command = TalkPacketParser.parse(payload) ?: return false

        val separatorIndex = command.text.indexOfFirst { it.isWhitespace() }
        val words = if (separatorIndex == -1) command.text else command.text.substring(0, separatorIndex)
        val param = if (separatorIndex == -1) "" else command.text.substring(separatorIndex + 1).trimStart(' ')

        return ScriptingDispatch(luaEnvironment, dispatcher).talkActionOnSay(words, player, param)
    }
}
