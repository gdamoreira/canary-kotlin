package org.opentibia.canary.network.protocol.cyclopedia

import io.netty.buffer.ByteBuf
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.GameWindowWriter

/**
 * Outbound cyclopedia character-info serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendCyclopediaCharacterNoData`          (0xDA, protocolgame.cpp:4776-4786)
 *  - `ProtocolGame::sendCyclopediaCharacterBaseInformation` (0xDA, protocolgame.cpp:4788-4805)
 *
 * The client request opcodes `0xE5` (`parseCyclopediaCharacterInfo`) and `0xDB`
 * (`parseCyclopediaMapAction`) are inbound and already decoded elsewhere; every
 * response frame shares the `0xDA` opcode plus the
 * `CyclopediaCharacterInfoType_t` byte (`src/game/game_definitions.hpp:64-80`).
 * The remaining response types (general stats, recent deaths, achievements,
 * item summary, outfits/mounts, store summary, inspection, badges, titles,
 * offence/defence/misc stats, house list) are ported in
 * `CyclopediaCharacterWriter`; their `addCyclopedia*`, `handleImbuementDamage`,
 * `calculateAbsorbValues`, `getForgeSkillStat` and `getCipbia*` computations
 * live in `CyclopediaComputation`. Every multi-byte field is Little-Endian and
 * strings are `uint16` length plus UTF-8; the `!player`/`oldProtocol` guard
 * stays with the caller.
 */
object CyclopediaWindowWriter {

    const val PACKET_CYCLOPEDIA_CHARACTER_INFO: Int = 0xDA

    /** `CyclopediaCharacterInfoType_t` (`src/game/game_definitions.hpp:64-80`). */
    const val CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION: Int = 0
    const val CYCLOPEDIA_CHARACTERINFO_GENERALSTATS: Int = 1
    const val CYCLOPEDIA_CHARACTERINFO_COMBATSTATS: Int = 2
    const val CYCLOPEDIA_CHARACTERINFO_RECENTDEATHS: Int = 3
    const val CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS: Int = 4
    const val CYCLOPEDIA_CHARACTERINFO_ACHIEVEMENTS: Int = 5
    const val CYCLOPEDIA_CHARACTERINFO_ITEMSUMMARY: Int = 6
    const val CYCLOPEDIA_CHARACTERINFO_OUTFITSMOUNTS: Int = 7
    const val CYCLOPEDIA_CHARACTERINFO_STORESUMMARY: Int = 8
    const val CYCLOPEDIA_CHARACTERINFO_INSPECTION: Int = 9
    const val CYCLOPEDIA_CHARACTERINFO_BADGES: Int = 10
    const val CYCLOPEDIA_CHARACTERINFO_TITLES: Int = 11
    const val CYCLOPEDIA_CHARACTERINFO_WHEEL: Int = 12
    const val CYCLOPEDIA_CHARACTERINFO_OFFENCESTATS: Int = 13
    const val CYCLOPEDIA_CHARACTERINFO_DEFENCESTATS: Int = 14
    const val CYCLOPEDIA_CHARACTERINFO_MISCSTATS: Int = 15

    /** `sendCyclopediaCharacterNoData` (protocolgame.cpp:4776-4786). */
    fun writeCyclopediaCharacterNoData(out: ByteBuf, characterInfoType: Int, errorCode: Int) {
        out.writeByte(PACKET_CYCLOPEDIA_CHARACTER_INFO)
        out.writeByte(characterInfoType)
        out.writeByte(errorCode)
    }

    /**
     * `sendCyclopediaCharacterBaseInformation` (protocolgame.cpp:4788-4805).
     * [defaultOutfit] is `Player::getDefaultOutfit()` and [currentTitleName]
     * `Player::title().getCurrentTitleName()`; `AddOutfit(..., false)` never
     * writes a mount.
     */
    fun writeCyclopediaCharacterBaseInformation(
        out: ByteBuf,
        name: String,
        vocationName: String,
        level: Int,
        defaultOutfit: Outfit,
        currentTitleName: String,
    ) {
        out.writeByte(PACKET_CYCLOPEDIA_CHARACTER_INFO)
        out.writeByte(CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION)
        out.writeByte(0x00)
        out.writeTibiaString(name)
        out.writeTibiaString(vocationName)
        out.writeShortLE(level)
        GameWindowWriter.writeOutfit(out, defaultOutfit, addMount = false)
        out.writeByte(0x01)
        out.writeTibiaString(currentTitleName)
    }
}
