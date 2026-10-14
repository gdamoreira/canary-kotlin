package org.opentibia.canary.network.protocol.party

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.GameWindowWriter

/**
 * Outbound party creature serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendPartyCreatureUpdate`      (0x8B, protocolgame.cpp:8333-8349)
 *  - `ProtocolGame::sendPartyCreatureShield`      (0x91, protocolgame.cpp:8351-8363)
 *  - `ProtocolGame::sendPartyCreatureSkull`       (0x90, protocolgame.cpp:8365-8381)
 *  - `ProtocolGame::sendPartyCreatureHealth`      (0x8C, protocolgame.cpp:8383-8395)
 *  - `ProtocolGame::sendPartyPlayerMana`          (0x8B, protocolgame.cpp:8397-8413)
 *  - `ProtocolGame::sendPartyCreatureShowStatus`  (0x8B, protocolgame.cpp:8415-8431)
 *  - `ProtocolGame::sendPartyPlayerVocation`      (0x8B, protocolgame.cpp:8433-8454)
 *
 * The session-owned `checkCreatureAsKnown` state arrives through
 * [CreatureAppearanceFrame.known] (`knownCreatureSet.contains(cid)`) and
 * [CreatureAppearanceFrame.removedKnown] (the eviction result when the creature
 * is not known yet). When a sender finds the creature unknown it delegates to
 * `sendPartyCreatureUpdate` exactly like the C++ early branches: shield, skull,
 * health and vocation stop after the update, while mana and show status keep
 * going with their sub-code frame when the protocol is not the old one.
 * The `player`/`target` null guards and `Game::getWorldType()` PVP gate stay
 * with the caller ([pvpWorld]). Every multi-byte field is Little-Endian and the
 * `AddCreature` payload is the shared [GameWindowWriter.writeCreature] body.
 */
object PartyWindowWriter {

    const val PACKET_PARTY_CREATURE_UPDATE: Int = 0x8B
    const val PACKET_PARTY_CREATURE_SKULL: Int = 0x90
    const val PACKET_PARTY_CREATURE_SHIELD: Int = 0x91
    const val PACKET_PARTY_CREATURE_HEALTH: Int = 0x8C

    /** `0x8B` creature-update sub-code (protocolgame.cpp:8346). */
    private const val UPDATE_CREATURE_UPDATE: Int = 0

    /** `0x8B` mana-percent sub-code (protocolgame.cpp:8410). */
    private const val UPDATE_MANA_PERCENT: Int = 11

    /** `0x8B` show-status sub-code (protocolgame.cpp:8428). */
    private const val UPDATE_SHOW_STATUS: Int = 12

    /** `0x8B` vocation sub-code (protocolgame.cpp:8451). */
    private const val UPDATE_VOCATION: Int = 13

    /** `sendPartyCreatureUpdate` (protocolgame.cpp:8333-8349). */
    fun writePartyCreatureUpdate(
        out: ByteBuf,
        cid: Long,
        appearance: CreatureAppearanceFrame,
        oldProtocol: Boolean = false,
    ) {
        if (oldProtocol) {
            return
        }

        out.writeByte(PACKET_PARTY_CREATURE_UPDATE)
        out.writeIntLE(cid.toInt())
        out.writeByte(UPDATE_CREATURE_UPDATE)
        GameWindowWriter.writeCreature(out, appearance)
    }

    /** `sendPartyCreatureShield` (protocolgame.cpp:8351-8363). */
    fun writePartyCreatureShield(
        out: ByteBuf,
        cid: Long,
        appearance: CreatureAppearanceFrame,
        partyShield: Int,
        oldProtocol: Boolean = false,
    ) {
        if (!appearance.known) {
            writePartyCreatureUpdate(out, cid, appearance, oldProtocol)
            return
        }

        out.writeByte(PACKET_PARTY_CREATURE_SHIELD)
        out.writeIntLE(cid.toInt())
        out.writeByte(partyShield)
    }

    /** `sendPartyCreatureSkull` (protocolgame.cpp:8365-8381). */
    fun writePartyCreatureSkull(
        out: ByteBuf,
        cid: Long,
        appearance: CreatureAppearanceFrame,
        skullClient: Int,
        pvpWorld: Boolean,
        oldProtocol: Boolean = false,
    ) {
        if (!pvpWorld) {
            return
        }

        if (!appearance.known) {
            writePartyCreatureUpdate(out, cid, appearance, oldProtocol)
            return
        }

        out.writeByte(PACKET_PARTY_CREATURE_SKULL)
        out.writeIntLE(cid.toInt())
        out.writeByte(skullClient)
    }

    /** `sendPartyCreatureHealth` (protocolgame.cpp:8383-8395). */
    fun writePartyCreatureHealth(
        out: ByteBuf,
        cid: Long,
        appearance: CreatureAppearanceFrame,
        healthPercent: Int,
        oldProtocol: Boolean = false,
    ) {
        if (!appearance.known) {
            writePartyCreatureUpdate(out, cid, appearance, oldProtocol)
            return
        }

        out.writeByte(PACKET_PARTY_CREATURE_HEALTH)
        out.writeIntLE(cid.toInt())
        out.writeByte(minOf(100, healthPercent))
    }

    /** `sendPartyPlayerMana` (protocolgame.cpp:8397-8413). */
    fun writePartyPlayerMana(
        out: ByteBuf,
        cid: Long,
        appearance: CreatureAppearanceFrame,
        manaPercent: Int,
        oldProtocol: Boolean = false,
    ) {
        if (!appearance.known) {
            writePartyCreatureUpdate(out, cid, appearance, oldProtocol)
        }

        if (oldProtocol) {
            return
        }

        out.writeByte(PACKET_PARTY_CREATURE_UPDATE)
        out.writeIntLE(cid.toInt())
        out.writeByte(UPDATE_MANA_PERCENT)
        out.writeByte(minOf(100, manaPercent))
    }

    /** `sendPartyCreatureShowStatus` (protocolgame.cpp:8415-8431). */
    fun writePartyCreatureShowStatus(
        out: ByteBuf,
        cid: Long,
        appearance: CreatureAppearanceFrame,
        showStatus: Boolean,
        oldProtocol: Boolean = false,
    ) {
        if (!appearance.known) {
            writePartyCreatureUpdate(out, cid, appearance, oldProtocol)
        }

        if (oldProtocol) {
            return
        }

        out.writeByte(PACKET_PARTY_CREATURE_UPDATE)
        out.writeIntLE(cid.toInt())
        out.writeByte(UPDATE_SHOW_STATUS)
        out.writeByte(if (showStatus) 0x01 else 0x00)
    }

    /** `sendPartyPlayerVocation` (protocolgame.cpp:8433-8454). */
    fun writePartyPlayerVocation(
        out: ByteBuf,
        cid: Long,
        appearance: CreatureAppearanceFrame,
        vocationClientId: Int,
        oldProtocol: Boolean = false,
    ) {
        if (!appearance.known) {
            writePartyCreatureUpdate(out, cid, appearance, oldProtocol)
            return
        }

        if (oldProtocol) {
            return
        }

        out.writeByte(PACKET_PARTY_CREATURE_UPDATE)
        out.writeIntLE(cid.toInt())
        out.writeByte(UPDATE_VOCATION)
        out.writeByte(vocationClientId)
    }
}
