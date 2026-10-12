package org.opentibia.canary.network.protocol.proficiency

import io.netty.buffer.ByteBuf
import org.opentibia.canary.systems.proficiency.ProficiencyPerk

/**
 * Outbound Weapon Proficiencies serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *  - ProtocolGame::sendWeaponProficiency (opcode 0x5C)
 *  - ProtocolGame::sendWeaponProficiencyWindow (opcode 0xC4)
 *
 * The 0xC4 window carries the OfficialWeaponProficiencyPayload family gated by
 * ProtocolFeature::OfficialWeaponProficiencyPayload; the trailing detail-list
 * boundary is only appended when shouldSendWeaponProficiencyDetailList(...) is true.
 */
object WeaponProficiencyPayload {

    const val PACKET_WEAPON_PROFICIENCY: Int = 0x5C
    const val PACKET_WEAPON_PROFICIENCY_WINDOW: Int = 0xC4

    private const val MAX_PERK_COUNT = 0xFF

    fun writeWeaponProficiency(
        out: ByteBuf,
        weaponId: Int,
        experience: Int,
        upgradeAvailable: Boolean,
    ) {
        out.writeByte(PACKET_WEAPON_PROFICIENCY)
        out.writeShortLE(weaponId)
        out.writeIntLE(if (weaponId > 0) experience else 0)
        out.writeByte(if (weaponId > 0 && upgradeAvailable) 0x01 else 0x00)
    }

    fun writeWeaponProficiencyWindow(
        out: ByteBuf,
        weaponId: Int,
        experience: Int,
        selectedPerks: List<ProficiencyPerk>,
        sendDetailList: Boolean,
    ) {
        out.writeByte(PACKET_WEAPON_PROFICIENCY_WINDOW)
        out.writeShortLE(weaponId)
        out.writeIntLE(experience)

        val perkCount = minOf(selectedPerks.size, MAX_PERK_COUNT)
        out.writeByte(perkCount)
        for (i in 0 until perkCount) {
            val perk = selectedPerks[i]
            out.writeByte(perk.level)
            out.writeByte(perk.index)
        }

        if (sendDetailList) {
            out.writeByte(0x00)
        }
    }
}
