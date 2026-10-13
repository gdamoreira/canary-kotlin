package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * `ProtocolGame::parseBossDifficultySelection` request (client opcode `0xC2`,
 * src/server/network/protocol/protocolgame.cpp:3317-3323). Canary reads the
 * `uint32_t` boss id, the action byte and, for actions 0 and 2, the `uint16_t`
 * difficulty, then discards them.
 */
data class BossDifficultySelectionCommand(
    val bossId: Long,
    val action: Int,
    val difficulty: Int?,
)

/** Decoder for the boss-difficulty selection opcode `0xC2`. */
object BossDifficultyPacketHandler {

    /** protocolgame.cpp:1983-1985 dispatches this opcode to `parseBossDifficultySelection`. */
    const val OPCODE_BOSS_DIFFICULTY_SELECTION: Int = 0xC2

    /** The actions whose payload includes the difficulty `uint16_t` (protocolgame.cpp:3320). */
    private val ACTIONS_WITH_DIFFICULTY = intArrayOf(0, 2)

    /**
     * @return the decoded command, or `null` when a field the action implies is
     *   truncated.
     */
    fun parse(buffer: ByteBuf): BossDifficultySelectionCommand? {
        if (buffer.readableBytes() < 4) return null
        val bossId = buffer.readUnsignedIntLE()

        if (buffer.readableBytes() < 1) return null
        val action = buffer.readUnsignedByte().toInt()

        var difficulty: Int? = null
        if (action in ACTIONS_WITH_DIFFICULTY) {
            if (buffer.readableBytes() < 2) return null
            difficulty = buffer.readUnsignedShortLE()
        }

        return BossDifficultySelectionCommand(bossId = bossId, action = action, difficulty = difficulty)
    }
}
