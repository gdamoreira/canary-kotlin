package org.opentibia.canary.network.protocol.bestiary

import io.netty.buffer.ByteBuf

/**
 * Self-contained command DTOs for the Bestiary/Charm client opcodes. Each
 * command carries the opcode so a caller can route on the decoded value without
 * re-reading the frame.
 *
 * Ported from Canary (Little-Endian wire order via `NetworkMessage::get<T>()`,
 * src/server/network/message/networkmessage.cpp):
 *  - 0xE1 `ProtocolGame::parseBestiarySendRaces`
 *  - 0xE2 `ProtocolGame::parseBestiarySendCreatures`
 *  - 0xE3 `ProtocolGame::parseBestiarysendMonsterData`
 *  - 0xE4 `ProtocolGame::parseSendBuyCharmRune`
 * (src/server/network/protocol/protocolgame.cpp)
 */
sealed interface BestiaryCommand {
    val opcode: Int
}

/**
 * 0xE1: the client requests the bestiary race list. Canary reads no payload
 * (protocolgame.cpp:3472), so this command has no fields.
 */
object BestiarySendRacesCommand : BestiaryCommand {
    override val opcode: Int get() = BestiaryPacketParser.OPCODE_SEND_RACES
}

/**
 * 0xE2: the client searches bestiary creatures either by the `uint16_t` race-id
 * list (`search == 1`) or by a length-prefixed race name (protocolgame.cpp:4164).
 */
sealed interface BestiarySendCreaturesCommand : BestiaryCommand {
    override val opcode: Int get() = BestiaryPacketParser.OPCODE_SEND_CREATURES
}

/** `search == 1` branch: a `uint16_t` count followed by that many `uint16_t` race ids. */
data class SearchRaceIdsCommand(
    val raceIds: List<Int>,
) : BestiarySendCreaturesCommand

/** Fallback branch: a `uint16_t`-length-prefixed race name. */
data class SearchRaceByNameCommand(
    val raceName: String,
) : BestiarySendCreaturesCommand

/** 0xE3: a single `uint16_t` race id (protocolgame.cpp:3515). */
data class BestiaryMonsterDataCommand(
    val raceId: Int,
) : BestiaryCommand {
    override val opcode: Int get() = BestiaryPacketParser.OPCODE_MONSTER_DATA
}

/**
 * 0xE4: an action byte, a charm byte and a `uint16_t` race id
 * (protocolgame.cpp:4028).
 *
 * Canary casts the charm byte through `static_cast<charmRune_t>(msg.getByte())`,
 * i.e. an `int8_t`; [charmId] reproduces that signed interpretation, so an
 * `0xFF` byte decodes to `-1` (`CHARM_NONE`).
 */
data class BuyCharmRuneCommand(
    val action: Int,
    val charmId: Int,
    val raceId: Int,
) : BestiaryCommand {
    override val opcode: Int get() = BestiaryPacketParser.OPCODE_BUY_CHARM_RUNE
}

/**
 * Decoder for the Bestiary/Charm client opcodes. Every multi-byte field is
 * Little-Endian, matching `NetworkMessage::get<T>()` on the native wire order.
 */
object BestiaryPacketParser {

    const val OPCODE_SEND_RACES: Int = 0xE1
    const val OPCODE_SEND_CREATURES: Int = 0xE2
    const val OPCODE_MONSTER_DATA: Int = 0xE3
    const val OPCODE_BUY_CHARM_RUNE: Int = 0xE4

    fun parse(opcode: Int, buffer: ByteBuf): BestiaryCommand? = when (opcode) {
        OPCODE_SEND_RACES -> parseBestiarySendRaces()
        OPCODE_SEND_CREATURES -> parseBestiarySendCreatures(buffer)
        OPCODE_MONSTER_DATA -> parseBestiarysendMonsterData(buffer)
        OPCODE_BUY_CHARM_RUNE -> parseSendBuyCharmRune(buffer)
        else -> null
    }

    /** 0xE1: no payload is read. */
    fun parseBestiarySendRaces(): BestiarySendRacesCommand = BestiarySendRacesCommand

    /**
     * 0xE2: reads the `search` byte. `1` selects the race-id list; any other
     * value falls through to the length-prefixed race name, mirroring Canary's
     * `if (search == 1) ... else ...` (protocolgame.cpp:4172-4197).
     */
    fun parseBestiarySendCreatures(buffer: ByteBuf): BestiarySendCreaturesCommand? {
        val search = readByte(buffer) ?: return null
        return if (search == 1) {
            val amount = readShort(buffer) ?: return null
            val raceIds = ArrayList<Int>(amount)
            repeat(amount) {
                raceIds += readShort(buffer) ?: return null
            }
            SearchRaceIdsCommand(raceIds)
        } else {
            val length = readShort(buffer) ?: return null
            if (buffer.readableBytes() < length) return null
            val bytes = ByteArray(length)
            buffer.readBytes(bytes)
            SearchRaceByNameCommand(String(bytes, Charsets.UTF_8))
        }
    }

    /** 0xE3: reads a single `uint16_t` race id. */
    fun parseBestiarysendMonsterData(buffer: ByteBuf): BestiaryMonsterDataCommand? {
        val raceId = readShort(buffer) ?: return null
        return BestiaryMonsterDataCommand(raceId)
    }

    /** 0xE4: reads the action byte, the signed charm byte and a `uint16_t` race id. */
    fun parseSendBuyCharmRune(buffer: ByteBuf): BuyCharmRuneCommand? {
        val action = readByte(buffer) ?: return null
        val charmByte = readByte(buffer) ?: return null
        val raceId = readShort(buffer) ?: return null
        return BuyCharmRuneCommand(
            action = action,
            charmId = charmByte.toByte().toInt(),
            raceId = raceId,
        )
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()
}
