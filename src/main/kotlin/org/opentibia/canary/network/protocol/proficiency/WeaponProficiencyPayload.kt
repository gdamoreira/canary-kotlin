package org.opentibia.canary.network.protocol.proficiency

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.protocol.ProtocolFeature
import org.opentibia.canary.network.protocol.ProtocolProfile
import org.opentibia.canary.systems.proficiency.ProficiencyPerk

private val logger = KotlinLogging.logger {}

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

    /**
     * The 15.25 build prefixes whose 0xC4 payload carries the trailing
     * detail-list section (`supportsWeaponProficiencyDetailList`,
     * protocolgame.cpp:109-118).
     */
    private val DETAIL_LIST_BUILD_PREFIXES = listOf("15.25.794c2e", "15.25.d96c64")

    /** The recognized 15.25 family prefix used for the unrecognized-build debug log. */
    private const val FAMILY_15_25_PREFIX = "15.25."

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

    /**
     * `supportsWeaponProficiencyDetailList` (protocolgame.cpp:109-118): only the
     * known 15.25 builds carry the trailing list; every other build keeps the
     * shorter shape until a capture proves the list is required. An
     * unrecognized 15.25 build logs the same debug line as Canary.
     */
    fun supportsWeaponProficiencyDetailList(clientVersionString: String): Boolean {
        val known = DETAIL_LIST_BUILD_PREFIXES.any { clientVersionString.startsWith(it) }
        if (!known && clientVersionString.startsWith(FAMILY_15_25_PREFIX)) {
            logger.debug {
                "[WeaponProficiency] unrecognized 15.25 build '$clientVersionString'; detail list will not be sent."
            }
        }
        return known
    }

    /**
     * `shouldSendWeaponProficiencyDetailList` (protocolgame.cpp:128-132): the
     * runtime profile feature gates the current weapon-proficiency packet
     * family; the login build string gates the trailing list inside that packet.
     */
    fun shouldSendWeaponProficiencyDetailList(
        protocolProfile: ProtocolProfile?,
        clientVersionString: String,
    ): Boolean =
        protocolProfile?.hasFeature(ProtocolFeature.OfficialWeaponProficiencyPayload) == true &&
            supportsWeaponProficiencyDetailList(clientVersionString)
}
