package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.writeTibiaString

/**
 * The `sendHighscores` payload (protocolgame.cpp:3367-3443). Every value Canary
 * resolves from the live registries is supplied by the caller:
 *
 *  - [serverName] is `g_configManager().getString(SERVER_NAME)`; Canary writes
 *    it as the first world, the selected world and every character's world.
 *  - [vocations] are the registered `Vocation` records; only entries whose
 *    `fromVocation` equals their own id are emitted (protocolgame.cpp:3397),
 *    exactly like the C++ loop.
 *  - [categories] are `Game::getHighscoreCategories()`.
 *  - [characters] are the ranked `HighscoreCharacter` rows; [playerId] is
 *    `Player::getGUID` used for the character indicator byte.
 */
data class HighscoresFrame(
    val serverName: String,
    val vocations: List<HighscoresWindowSerializer.HighscoreVocation> = emptyList(),
    val vocationId: Long = HighscoresWindowSerializer.ALL_VOCATIONS_CLIENT_ID,
    val categories: List<HighscoresWindowSerializer.HighscoreCategory> = emptyList(),
    val categoryId: Int = -1,
    val page: Int = 0,
    val pages: Int = 0,
    val characters: List<HighscoresWindowSerializer.HighscoreCharacterEntry> = emptyList(),
    val playerId: Long = 0,
    val updateTimer: Long = 0,
)

/**
 * Outbound highscore-window serialization for the Canary
 * `ProtocolProfileId::Current` profile, ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendHighscoresNoData` (0xB1, protocolgame.cpp:3356-3365)
 *  - `ProtocolGame::sendHighscores`       (0xB1, protocolgame.cpp:3367-3443)
 *
 * Canary backpatches the vocation count after iterating the registry; the
 * count is computed up front here because the eligible entries are known, which
 * is byte-identical. All multi-byte fields are Little-Endian.
 */
object HighscoresWindowSerializer {

    const val PACKET_HIGHSCORES: Int = 0xB1

    /** `0xFFFFFFFF` — the hardcoded "All Vocations" client id (protocolgame.cpp:3388). */
    const val ALL_VOCATIONS_CLIENT_ID: Long = 0xFFFFFFFFL

    /** `Vocation` fields the vocation list needs (`getId`/`getFromVocation`/`getClientId`/`getVocName`). */
    data class HighscoreVocation(
        val id: Int,
        val fromVocation: Int,
        val clientId: Long,
        val name: String,
    )

    /** One `HighscoreCategory` (`m_id`/`m_name`, server_definitions.hpp:174). */
    data class HighscoreCategory(
        val id: Int,
        val name: String,
    )

    /**
     * One `HighscoreCharacter` row (`src/server/server_definitions.hpp:130-145`).
     * Canary writes [name] and [loyaltyTitle], and uses [id] only for the
     * player indicator comparison.
     */
    data class HighscoreCharacterEntry(
        val rank: Long,
        val name: String,
        val loyaltyTitle: String,
        val vocation: Int,
        val level: Int,
        val id: Long,
        val points: Long,
    )

    /** `sendHighscoresNoData` (protocolgame.cpp:3356-3365): opcode plus the no-data marker. */
    fun writeHighscoresNoData(out: ByteBuf) {
        out.writeByte(PACKET_HIGHSCORES)
        out.writeByte(0x01)
    }

    /** `sendHighscores` (protocolgame.cpp:3367-3443). */
    fun writeHighscores(out: ByteBuf, frame: HighscoresFrame) {
        out.writeByte(PACKET_HIGHSCORES)
        out.writeByte(0x00)

        out.writeByte(1)
        out.writeTibiaString(frame.serverName)
        out.writeTibiaString(frame.serverName)

        out.writeByte(0)
        out.writeByte(0)

        val eligibleVocations = frame.vocations.filter { it.fromVocation == it.id }
        out.writeByte(1 + eligibleVocations.size)
        out.writeIntLE(ALL_VOCATIONS_CLIENT_ID.toInt())
        out.writeTibiaString("(all)")

        var selectedVocation = ALL_VOCATIONS_CLIENT_ID
        for (vocation in eligibleVocations) {
            out.writeIntLE(vocation.clientId.toInt())
            out.writeTibiaString(vocation.name)
            if (vocation.clientId == frame.vocationId) {
                selectedVocation = vocation.clientId
            }
        }
        out.writeIntLE(selectedVocation.toInt())

        var selectedCategory = 0
        out.writeByte(frame.categories.size)
        for (category in frame.categories) {
            out.writeByte(category.id)
            out.writeTibiaString(category.name)
            if (category.id == frame.categoryId) {
                selectedCategory = frame.categoryId
            }
        }
        out.writeByte(selectedCategory)

        out.writeShortLE(frame.page)
        out.writeShortLE(frame.pages)

        out.writeByte(frame.characters.size)
        for (character in frame.characters) {
            out.writeIntLE(character.rank.toInt())
            out.writeTibiaString(character.name)
            out.writeTibiaString(character.loyaltyTitle)
            out.writeByte(character.vocation)
            out.writeTibiaString(frame.serverName)
            out.writeShortLE(character.level)
            out.writeByte(if (frame.playerId == character.id) 0x01 else 0x00)
            out.writeLongLE(character.points)
        }

        out.writeByte(0xFF)
        out.writeByte(0)
        out.writeByte(1)
        out.writeIntLE(frame.updateTimer.toInt())
    }
}
