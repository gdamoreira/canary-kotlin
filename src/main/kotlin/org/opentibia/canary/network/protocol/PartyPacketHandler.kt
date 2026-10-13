package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Self-contained commands produced by the party client opcodes `0xA3`-`0xA8`,
 * mirroring `ProtocolGame` / `Game` (src/server/network/protocol/protocolgame.cpp).
 */
sealed interface PartyCommand {
    val opcode: Int
}

/**
 * Invite a creature to the party (client opcode `0xA3`), mirroring
 * `ProtocolGame::parseInviteToParty` (protocolgame.cpp:4373-4376): a `uint32_t`
 * target creature id handed to `Game::playerInviteToParty`.
 */
data class InviteToPartyCommand(val targetId: Long) : PartyCommand {
    override val opcode: Int get() = PartyPacketHandler.OPCODE_INVITE_TO_PARTY
}

/**
 * Accept a party invitation (client opcode `0xA4`), mirroring
 * `ProtocolGame::parseJoinParty` (protocolgame.cpp:4378-4381): a `uint32_t`
 * target creature id handed to `Game::playerJoinParty`.
 */
data class JoinPartyCommand(val targetId: Long) : PartyCommand {
    override val opcode: Int get() = PartyPacketHandler.OPCODE_JOIN_PARTY
}

/**
 * Revoke a pending party invitation (client opcode `0xA5`), mirroring
 * `ProtocolGame::parseRevokePartyInvite` (protocolgame.cpp:4383-4386): a
 * `uint32_t` target creature id.
 */
data class RevokePartyInviteCommand(val targetId: Long) : PartyCommand {
    override val opcode: Int get() = PartyPacketHandler.OPCODE_REVOKE_PARTY_INVITE
}

/**
 * Pass party leadership (client opcode `0xA6`), mirroring
 * `ProtocolGame::parsePassPartyLeadership` (protocolgame.cpp:4388-4391): a
 * `uint32_t` target creature id.
 */
data class PassPartyLeadershipCommand(val targetId: Long) : PartyCommand {
    override val opcode: Int get() = PartyPacketHandler.OPCODE_PASS_PARTY_LEADERSHIP
}

/**
 * Leave the current party (client opcode `0xA7`). Canary reads no payload and
 * calls `Game::playerLeaveParty` (protocolgame.cpp:1933).
 */
object LeavePartyCommand : PartyCommand {
    override val opcode: Int get() = PartyPacketHandler.OPCODE_LEAVE_PARTY
}

/**
 * Toggle shared party experience (client opcode `0xA8`), mirroring
 * `ProtocolGame::parseEnableSharedPartyExperience` (protocolgame.cpp:4393-4396):
 * one byte, true only when it equals `0x01`.
 */
data class EnableSharedPartyExperienceCommand(
    val sharedExpActive: Boolean,
) : PartyCommand {
    override val opcode: Int get() = PartyPacketHandler.OPCODE_ENABLE_SHARED_PARTY_EXPERIENCE
}

/**
 * Decoder for the party client opcodes. Multi-byte fields are Little-Endian,
 * matching `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0xA3` `ProtocolGame::parseInviteToParty`               (protocolgame.cpp:4373)
 *  - `0xA4` `ProtocolGame::parseJoinParty`                   (protocolgame.cpp:4378)
 *  - `0xA5` `ProtocolGame::parseRevokePartyInvite`           (protocolgame.cpp:4383)
 *  - `0xA6` `ProtocolGame::parsePassPartyLeadership`         (protocolgame.cpp:4388)
 *  - `0xA7` `Game::playerLeaveParty`                         (protocolgame.cpp:1933)
 *  - `0xA8` `ProtocolGame::parseEnableSharedPartyExperience` (protocolgame.cpp:4393)
 */
object PartyPacketHandler {

    const val OPCODE_INVITE_TO_PARTY: Int = 0xA3
    const val OPCODE_JOIN_PARTY: Int = 0xA4
    const val OPCODE_REVOKE_PARTY_INVITE: Int = 0xA5
    const val OPCODE_PASS_PARTY_LEADERSHIP: Int = 0xA6
    const val OPCODE_LEAVE_PARTY: Int = 0xA7
    const val OPCODE_ENABLE_SHARED_PARTY_EXPERIENCE: Int = 0xA8

    fun parse(opcode: Int, buffer: ByteBuf): PartyCommand? = when (opcode) {
        OPCODE_INVITE_TO_PARTY -> readTargetId(buffer)?.let(::InviteToPartyCommand)
        OPCODE_JOIN_PARTY -> readTargetId(buffer)?.let(::JoinPartyCommand)
        OPCODE_REVOKE_PARTY_INVITE -> readTargetId(buffer)?.let(::RevokePartyInviteCommand)
        OPCODE_PASS_PARTY_LEADERSHIP -> readTargetId(buffer)?.let(::PassPartyLeadershipCommand)
        OPCODE_LEAVE_PARTY -> LeavePartyCommand
        OPCODE_ENABLE_SHARED_PARTY_EXPERIENCE -> readByte(buffer)?.let {
            EnableSharedPartyExperienceCommand(sharedExpActive = it == 0x01)
        }
        else -> null
    }

    private fun readTargetId(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 4) null else buffer.readUnsignedIntLE()

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()
}
