package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Self-contained commands produced by the quest-log client opcodes `0xF0`/`0xF1`,
 * mirroring the Canary `Game` / `ProtocolGame` handlers
 * (src/server/network/protocol/protocolgame.cpp).
 */
sealed interface QuestLogCommand {
    val opcode: Int
}

/**
 * Open the quest log (client opcode `0xF0`). Canary reads no payload and calls
 * `Game::playerShowQuestLog` (protocolgame.cpp:2083-2085); the quest entries
 * themselves stay in the Lua scripts (`data/scripts`), per AGENTS.md 1.4.
 */
object ShowQuestLogCommand : QuestLogCommand {
    override val opcode: Int get() = QuestLogPacketHandler.OPCODE_SHOW_QUEST_LOG
}

/**
 * Open one quest line (client opcode `0xF1`), mirroring
 * `ProtocolGame::parseQuestLine` (protocolgame.cpp:4398-4401): a `uint16_t`
 * quest id handed to `Game::playerShowQuestLine`.
 */
data class ShowQuestLineCommand(
    val questId: Int,
) : QuestLogCommand {
    override val opcode: Int get() = QuestLogPacketHandler.OPCODE_QUEST_LINE
}

/**
 * Decoder for the quest-log client opcodes. Multi-byte fields are Little-Endian,
 * matching `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0xF0` `Game::playerShowQuestLog`              (protocolgame.cpp:2083)
 *  - `0xF1` `ProtocolGame::parseQuestLine`          (protocolgame.cpp:4398)
 */
object QuestLogPacketHandler {

    const val OPCODE_SHOW_QUEST_LOG: Int = 0xF0
    const val OPCODE_QUEST_LINE: Int = 0xF1

    fun parse(opcode: Int, buffer: ByteBuf): QuestLogCommand? = when (opcode) {
        OPCODE_SHOW_QUEST_LOG -> ShowQuestLogCommand
        OPCODE_QUEST_LINE -> parseQuestLine(buffer)
        else -> null
    }

    /**
     * 0xF1: reads the `uint16_t` quest id.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseQuestLine(buffer: ByteBuf): ShowQuestLineCommand? {
        if (buffer.readableBytes() < 2) {
            return null
        }
        return ShowQuestLineCommand(questId = buffer.readUnsignedShortLE())
    }
}
