package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.readTibiaString

/**
 * Commands produced by `ProtocolGame::parseFriendSystemAction` (client opcode
 * `0x81`, protocolgame.cpp:2975-3024) on the current payload
 * (`ProtocolFeature::CurrentPayload`). Every command keeps the raw state byte;
 * only state `0x0E` reaches a game method (`Game::playerFriendSystemAction`,
 * game.cpp:9814-9821).
 */
sealed interface FriendSystemCommand {
    val state: Int
}

/** States `0x03`/`0x05`/`0x06`/`0x07`/`0x08`/`0x09`: a `uint32_t` target id. */
data class FriendSystemTargetCommand(
    override val state: Int,
    val targetId: Long,
) : FriendSystemCommand

/** States `0x04`/`0x0A`/`0x0D`: a `uint32_t` target id and one extra byte. */
data class FriendSystemTargetValueCommand(
    override val state: Int,
    val targetId: Long,
    val value: Int,
) : FriendSystemCommand

/** State `0x0B`: a length-prefixed string. */
data class FriendSystemTextCommand(
    override val state: Int,
    val text: String,
) : FriendSystemCommand

/** State `0x0E`: the new title id byte handed to `Player::title().setCurrentTitle`. */
data class FriendSystemTitleCommand(
    override val state: Int = 0x0E,
    val titleId: Int,
) : FriendSystemCommand

/** State `0x10`: four raw bytes. */
data class FriendSystemFourBytesCommand(
    override val state: Int,
    val first: Int,
    val second: Int,
    val third: Int,
    val fourth: Int,
) : FriendSystemCommand

/** Any other state: no trailing operands are read. */
data class FriendSystemStateCommand(
    override val state: Int,
) : FriendSystemCommand

/**
 * Decoder for `ProtocolGame::parseFriendSystemAction` (protocolgame.cpp:2975).
 *
 * The C++ parser only reads the trailing operands when the profile has
 * `ProtocolFeature::CurrentPayload`; this port targets that profile.
 *
 * PARITY GAP: state `0x0E` calls `Game::playerFriendSystemAction`, which needs
 * `Player::title()` (the [org.opentibia.canary.creatures.players.components.PlayerTitle]
 * component is not reachable from the router context) and the cyclopedia
 * base-information/title senders; neither is wired, so the decoded command is
 * only the confinement boundary.
 */
object FriendSystemPacketHandler {

    /** Client opcode for `ProtocolGame::parseFriendSystemAction`. */
    const val OPCODE_FRIEND_SYSTEM_ACTION: Int = 0x81

    /** State whose target id is a `uint32_t` alone. */
    private val TARGET_ID_STATES = setOf(0x03, 0x05, 0x06, 0x07, 0x08, 0x09)

    /** State whose target id is followed by one byte. */
    private val TARGET_ID_AND_BYTE_STATES = setOf(0x04, 0x0A, 0x0D)

    private const val STATE_TEXT: Int = 0x0B
    private const val STATE_TITLE: Int = 0x0E
    private const val STATE_FOUR_BYTES: Int = 0x10

    /**
     * 0x81: reads the state byte and the operands the current payload assigns
     * to it.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(buffer: ByteBuf): FriendSystemCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        val state = buffer.readUnsignedByte().toInt()
        return when {
            state in TARGET_ID_STATES -> {
                if (buffer.readableBytes() < 4) {
                    return null
                }
                FriendSystemTargetCommand(state = state, targetId = buffer.readUnsignedIntLE())
            }

            state in TARGET_ID_AND_BYTE_STATES -> {
                if (buffer.readableBytes() < 5) {
                    return null
                }
                FriendSystemTargetValueCommand(
                    state = state,
                    targetId = buffer.readUnsignedIntLE(),
                    value = buffer.readUnsignedByte().toInt(),
                )
            }

            state == STATE_TEXT -> readText(buffer, state)
            state == STATE_TITLE -> {
                if (buffer.readableBytes() < 1) {
                    return null
                }
                FriendSystemTitleCommand(state = state, titleId = buffer.readUnsignedByte().toInt())
            }

            state == STATE_FOUR_BYTES -> {
                if (buffer.readableBytes() < 4) {
                    return null
                }
                FriendSystemFourBytesCommand(
                    state = state,
                    first = buffer.readUnsignedByte().toInt(),
                    second = buffer.readUnsignedByte().toInt(),
                    third = buffer.readUnsignedByte().toInt(),
                    fourth = buffer.readUnsignedByte().toInt(),
                )
            }

            else -> FriendSystemStateCommand(state = state)
        }
    }

    /** `msg.getString()`: a `uint16_t` byte length followed by that many bytes. */
    private fun readText(buffer: ByteBuf, state: Int): FriendSystemTextCommand? {
        if (buffer.readableBytes() < 2) {
            return null
        }
        val length = buffer.getUnsignedShortLE(buffer.readerIndex())
        if (buffer.readableBytes() < 2 + length) {
            return null
        }
        return FriendSystemTextCommand(state = state, text = buffer.readTibiaString())
    }
}
