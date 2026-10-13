package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * `ProtocolGame::parseHighscores` request (client opcode `0xB1`,
 * src/server/network/protocol/protocolgame.cpp:3046-3063), handed to
 * `Game::playerHighscores` (src/game/game.cpp:10124).
 *
 * The C++ function clamps [page] to a minimum of 1 for
 * [HighscoresPacketHandler.HIGHSCORE_GETENTRIES] and [entriesPerPage] to the
 * closed range 5..30 before the call, so the decoded command carries the
 * clamped values.
 */
data class HighscoresCommand(
    val type: Int,
    val category: Int,
    val vocation: Long,
    val worldName: String,
    val gameWorldCategory: Int,
    val battlEyeWorldType: Int,
    val page: Int,
    val entriesPerPage: Int,
)

/**
 * Decoder for the highscores client opcode. Multi-byte fields are Little-Endian,
 * matching `NetworkMessage::get<T>()`.
 */
object HighscoresPacketHandler {

    /** protocolgame.cpp:1959-1961 dispatches this opcode to `parseHighscores`. */
    const val OPCODE_HIGHSCORES: Int = 0xB1

    /** `HIGHSCORE_GETENTRIES` (src/game/game_definitions.hpp:110). */
    const val HIGHSCORE_GETENTRIES: Int = 0

    /** `HIGHSCORE_OURRANK` (src/game/game_definitions.hpp:111). */
    const val HIGHSCORE_OURRANK: Int = 1

    /**
     * Reads the `HighscoreType_t` byte, category byte, `uint32_t` vocation, the
     * world-name string, the game-world-category and BattlEye bytes, the page
     * (get-entries only) and the entries-per-page byte.
     *
     * @return the decoded command, or `null` when any field is truncated.
     */
    fun parse(buffer: ByteBuf): HighscoresCommand? {
        val type = readByte(buffer) ?: return null
        val category = readByte(buffer) ?: return null
        val vocation = readInt(buffer) ?: return null
        val worldName = readString(buffer) ?: return null
        val gameWorldCategory = readByte(buffer) ?: return null
        val battlEyeWorldType = readByte(buffer) ?: return null

        var page = 1
        if (type == HIGHSCORE_GETENTRIES) {
            page = maxOf(1, readShort(buffer) ?: return null)
        }

        val entriesPerPage = (readByte(buffer) ?: return null).coerceIn(5, 30)
        return HighscoresCommand(
            type = type,
            category = category,
            vocation = vocation,
            worldName = worldName,
            gameWorldCategory = gameWorldCategory,
            battlEyeWorldType = battlEyeWorldType,
            page = page,
            entriesPerPage = entriesPerPage,
        )
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()

    private fun readInt(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 4) null else buffer.readUnsignedIntLE()

    private fun readString(buffer: ByteBuf): String? {
        if (buffer.readableBytes() < 2) return null
        val length = buffer.readUnsignedShortLE()
        if (buffer.readableBytes() < length) return null

        val bytes = ByteArray(length)
        buffer.readBytes(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
