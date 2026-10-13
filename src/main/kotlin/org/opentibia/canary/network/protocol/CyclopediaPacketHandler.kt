package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Self-contained commands produced by the cyclopedia client opcodes, mirroring
 * `ProtocolGame::parseCyclopediaMapAction` / `parseCyclopediaCharacterInfo`
 * (src/server/network/protocol/protocolgame.cpp).
 */
sealed interface CyclopediaCommand {
    val opcode: Int
}

/**
 * Cyclopedia map action (client opcode `0xDB`), mirroring
 * `ProtocolGame::parseCyclopediaMapAction` (protocolgame.cpp:3332-3348). Canary
 * reads the type-specific fields and discards them, so [mapId] and [extra] are
 * only populated for the actions that read them.
 */
data class CyclopediaMapActionCommand(
    val action: Int,
    val mapId: Int?,
    val extra: Long?,
) : CyclopediaCommand {
    override val opcode: Int get() = CyclopediaPacketHandler.OPCODE_CYCLOPEDIA_MAP_ACTION
}

/**
 * Cyclopedia character info request (client opcode `0xE5`), mirroring
 * `ProtocolGame::parseCyclopediaCharacterInfo` (protocolgame.cpp:3026-3043):
 * the `uint32_t` character id, the info type byte and, for recent deaths / PvP
 * kills, the clamped `entriesPerPage` and `page` values handed to
 * `Game::playerCyclopediaCharacterInfo` (game.cpp:9823).
 */
data class CyclopediaCharacterInfoCommand(
    val characterId: Long,
    val infoType: Int,
    val entriesPerPage: Int,
    val page: Int,
) : CyclopediaCommand {
    override val opcode: Int get() = CyclopediaPacketHandler.OPCODE_CYCLOPEDIA_CHARACTER_INFO
}

/**
 * Decoder for the cyclopedia client opcodes.
 *
 *  - `0xDB` `ProtocolGame::parseCyclopediaMapAction` (protocolgame.cpp:3332)
 *  - `0xE5` `ProtocolGame::parseCyclopediaCharacterInfo` (protocolgame.cpp:3026)
 */
object CyclopediaPacketHandler {

    const val OPCODE_CYCLOPEDIA_MAP_ACTION: Int = 0xDB
    const val OPCODE_CYCLOPEDIA_CHARACTER_INFO: Int = 0xE5

    /**
     * `CyclopediaCharacterInfoType_t` values that read the pagination fields
     * (`src/game/game_definitions.hpp:64-79`).
     */
    const val CYCLOPEDIA_CHARACTERINFO_RECENT_DEATHS: Int = 3
    const val CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS: Int = 4

    /** `std::min<uint16_t>(30, std::max<uint16_t>(5, ...))` (protocolgame.cpp:3034). */
    private const val MIN_ENTRIES_PER_PAGE: Int = 5
    private const val MAX_ENTRIES_PER_PAGE: Int = 30

    fun parse(opcode: Int, buffer: ByteBuf): CyclopediaCommand? = when (opcode) {
        OPCODE_CYCLOPEDIA_MAP_ACTION -> parseMapAction(buffer)
        OPCODE_CYCLOPEDIA_CHARACTER_INFO -> parseCharacterInfo(buffer)
        else -> null
    }

    /**
     * 0xDB: reads the action byte and the fields Canary reads for actions
     * `0`/`1`/`2`.
     *
     * @return the decoded command, or `null` when a read field is truncated.
     */
    fun parseMapAction(buffer: ByteBuf): CyclopediaMapActionCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }

        return when (val action = buffer.readUnsignedByte().toInt()) {
            0, 2 -> {
                if (buffer.readableBytes() < 2) {
                    return null
                }
                CyclopediaMapActionCommand(action = action, mapId = buffer.readUnsignedShortLE(), extra = null)
            }

            1 -> {
                if (buffer.readableBytes() < 6) {
                    return null
                }
                val mapId = buffer.readUnsignedShortLE()
                val extra = buffer.readUnsignedIntLE()
                CyclopediaMapActionCommand(action = action, mapId = mapId, extra = extra)
            }

            else -> CyclopediaMapActionCommand(action = action, mapId = null, extra = null)
        }
    }

    /**
     * 0xE5: reads the character id, info type and, for the recent deaths / PvP
     * kills types, the clamped pagination pair.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseCharacterInfo(buffer: ByteBuf): CyclopediaCharacterInfoCommand? {
        if (buffer.readableBytes() < 5) {
            return null
        }
        val characterId = buffer.readUnsignedIntLE()
        val infoType = buffer.readUnsignedByte().toInt()

        var entriesPerPage = 0
        var page = 0
        if (infoType == CYCLOPEDIA_CHARACTERINFO_RECENT_DEATHS ||
            infoType == CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS
        ) {
            if (buffer.readableBytes() < 4) {
                return null
            }
            entriesPerPage = buffer.readUnsignedShortLE().coerceIn(MIN_ENTRIES_PER_PAGE, MAX_ENTRIES_PER_PAGE)
            page = maxOf(1, buffer.readUnsignedShortLE())
        }

        return CyclopediaCharacterInfoCommand(
            characterId = characterId,
            infoType = infoType,
            entriesPerPage = entriesPerPage,
            page = page,
        )
    }
}
