package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * One aim-at-target spell update of the `0xC8` request, mirroring the
 * `spellId`/`state` pair `ProtocolGame::parseAimAtTarget`
 * (protocolgame.cpp:12196-12209) hands to `Player::updateAimAtTargetSpells`
 * (player.cpp:13361-13368).
 */
data class AimAtTargetSpell(
    val spellId: Int,
    val state: Int,
)

/**
 * Aim-at-target update request (client opcode `0xC8`), mirroring
 * `ProtocolGame::parseAimAtTarget` (protocolgame.cpp:12196-12209): a count byte
 * followed by that many `uint16_t` spell ids and state bytes.
 */
data class AimAtTargetCommand(
    val spells: List<AimAtTargetSpell>,
)

/**
 * Decoder for the aim-at-target client opcode.
 *
 *  - `0xC8` `ProtocolGame::parseAimAtTarget` (protocolgame.cpp:12196)
 */
object AimAtTargetPacketHandler {

    const val OPCODE_AIM_AT_TARGET: Int = 0xC8

    fun parse(opcode: Int, buffer: ByteBuf): AimAtTargetCommand? {
        if (opcode != OPCODE_AIM_AT_TARGET) {
            return null
        }
        if (buffer.readableBytes() < 1) {
            return null
        }

        val amount = buffer.readUnsignedByte().toInt()
        val spells = ArrayList<AimAtTargetSpell>(amount)
        for (index in 0 until amount) {
            if (buffer.readableBytes() < 3) {
                return null
            }
            val spellId = buffer.readUnsignedShortLE()
            val state = buffer.readUnsignedByte().toInt()
            spells.add(AimAtTargetSpell(spellId = spellId, state = state))
        }
        return AimAtTargetCommand(spells = spells)
    }
}
