package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Modal window answer (client opcode `0xF9`), mirroring
 * `ProtocolGame::parseModalWindowAnswer` (protocolgame.cpp:4484-4489): a
 * `uint32_t` window id, the button byte and the choice byte handed to
 * `Game::playerAnswerModalWindow`.
 */
data class ModalWindowAnswerCommand(
    val windowId: Long,
    val button: Int,
    val choice: Int,
)

/**
 * Decoder for the modal window answer client opcode.
 *
 *  - `0xF9` `ProtocolGame::parseModalWindowAnswer` (protocolgame.cpp:4484)
 */
object ModalAnswerPacketHandler {

    const val OPCODE_MODAL_WINDOW_ANSWER: Int = 0xF9

    fun parse(opcode: Int, buffer: ByteBuf): ModalWindowAnswerCommand? {
        if (opcode != OPCODE_MODAL_WINDOW_ANSWER) {
            return null
        }
        if (buffer.readableBytes() < 6) {
            return null
        }

        val windowId = buffer.readUnsignedIntLE()
        val button = buffer.readUnsignedByte().toInt()
        val choice = buffer.readUnsignedByte().toInt()
        return ModalWindowAnswerCommand(windowId = windowId, button = button, choice = choice)
    }
}
