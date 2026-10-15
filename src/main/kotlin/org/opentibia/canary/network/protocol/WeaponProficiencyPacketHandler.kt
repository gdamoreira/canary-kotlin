package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Commands decoded from the weapon-proficiency opcode `0xB3`
 * (`ProtocolGame::parseWeaponProficiency`,
 * src/server/network/protocol/protocolgame.cpp:3130-3202).
 */
sealed interface WeaponProficiencyCommand

/**
 * Action `0x01`: Canary sends the window for every tracked weapon
 * (`Player::weaponProficiency().getTrackedWeaponIds()`, protocolgame.cpp:3137-3141)
 * and reads no further payload.
 */
object WeaponProficiencyWindowsCommand : WeaponProficiencyCommand

/** One `(level, perkIndex)` pair of the action `0x03` selection list. */
data class SelectedPerkInput(
    val level: Int,
    val perkIndex: Int,
)

/**
 * Any action other than `0x01`: the weapon id is always read, and only action
 * `0x03` carries the selected-perk list. Actions `0x04`-`0x07`/`0x09` and `0x08`
 * additionally consume two or three official-payload bytes respectively.
 */
data class WeaponProficiencyActionCommand(
    val action: Int,
    val weaponId: Int,
    val selectedPerks: List<SelectedPerkInput> = emptyList(),
) : WeaponProficiencyCommand

/**
 * Decoder for `0xB3` on the current protocol profile: `CurrentPayload` plus
 * `OfficialWeaponProficiencyPayload`, the only profile this port targets
 * (PARITY GAP: the legacy payload variants are not modeled). Multi-byte fields
 * are Little-Endian, matching `NetworkMessage::get<T>()`.
 */
object WeaponProficiencyPacketHandler {

    /** protocolgame.cpp:1965-1967 dispatches this opcode to `parseWeaponProficiency`. */
    const val OPCODE_WEAPON_PROFICIENCY: Int = 0xB3

    /** The `action == 0x01` tracked-weapons window branch (protocolgame.cpp:3137). */
    const val ACTION_SEND_TRACKED_WEAPONS: Int = 0x01

    /** The `action == 0x02` clear-stats/selected-perks branch (protocolgame.cpp:3158-3165). */
    const val ACTION_CLEAR_SELECTED_PERKS: Int = 0x02

    /** The `action == 0x03` selected-perks branch (protocolgame.cpp:3167). */
    const val ACTION_SET_SELECTED_PERKS: Int = 0x03

    /** The `action == 0x08` branch consuming three official payload bytes (protocolgame.cpp:3193). */
    const val ACTION_OFFICIAL_PAYLOAD_3: Int = 0x08

    /** The actions consuming two official payload bytes (protocolgame.cpp:3186-3191). */
    private val OFFICIAL_PAYLOAD_2_ACTIONS = intArrayOf(0x04, 0x05, 0x06, 0x07, 0x09)

    fun parse(buffer: ByteBuf): WeaponProficiencyCommand? {
        val action = readByte(buffer) ?: return null
        if (action == ACTION_SEND_TRACKED_WEAPONS) {
            return WeaponProficiencyWindowsCommand
        }

        val weaponId = readShort(buffer) ?: return null
        return when (action) {
            ACTION_SET_SELECTED_PERKS -> {
                val slots = readByte(buffer) ?: return null
                val selectedPerks = ArrayList<SelectedPerkInput>(slots)
                for (slot in 0 until slots) {
                    val level = readByte(buffer) ?: return null
                    val perkIndex = readByte(buffer) ?: return null
                    selectedPerks.add(SelectedPerkInput(level = level, perkIndex = perkIndex))
                }
                WeaponProficiencyActionCommand(action = action, weaponId = weaponId, selectedPerks = selectedPerks)
            }

            in OFFICIAL_PAYLOAD_2_ACTIONS -> {
                consumeOfficialPayloadBytes(buffer, 2)
                WeaponProficiencyActionCommand(action = action, weaponId = weaponId)
            }

            ACTION_OFFICIAL_PAYLOAD_3 -> {
                consumeOfficialPayloadBytes(buffer, 3)
                WeaponProficiencyActionCommand(action = action, weaponId = weaponId)
            }

            else -> WeaponProficiencyActionCommand(action = action, weaponId = weaponId)
        }
    }

    /**
     * Mirrors the `consumeOfficialPayloadBytes` lambda (protocolgame.cpp:3147-3155):
     * reads up to [count] bytes, stopping at the end of the payload.
     */
    private fun consumeOfficialPayloadBytes(buffer: ByteBuf, count: Int) {
        for (i in 0 until count) {
            if (buffer.readableBytes() < 1) {
                return
            }
            buffer.skipBytes(1)
        }
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()
}
