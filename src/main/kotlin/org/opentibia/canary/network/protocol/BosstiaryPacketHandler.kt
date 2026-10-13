package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Commands decoded from the bosstiary client opcodes
 * (src/server/network/protocol/protocolgame.cpp):
 *
 *  - `0xAE` `ProtocolGame::parseSendBosstiary`      (protocolgame.cpp:11206)
 *  - `0xAF` `ProtocolGame::parseSendBosstiarySlots` (protocolgame.cpp:11248)
 *  - `0xB0` `ProtocolGame::parseBosstiarySlot`      (protocolgame.cpp:11383)
 */
sealed interface BosstiaryCommand {
    val opcode: Int
}

/** `0xAE`: the window send is outbound only; Canary reads no payload. */
object SendBosstiaryCommand : BosstiaryCommand {
    override val opcode: Int get() = BosstiaryPacketHandler.OPCODE_SEND_BOSSTIARY
}

/** `0xAF`: the slots send is outbound only; Canary reads no payload. */
object SendBosstiarySlotsCommand : BosstiaryCommand {
    override val opcode: Int get() = BosstiaryPacketHandler.OPCODE_SEND_BOSSTIARY_SLOTS
}

/** `0xB0`: `Game::playerBosstiarySlot` (src/game/game.cpp:11256). */
data class BosstiarySlotCommand(
    val slot: Int,
    val selectedBossId: Long,
) : BosstiaryCommand {
    override val opcode: Int get() = BosstiaryPacketHandler.OPCODE_BOSSTIARY_SLOT
}

/**
 * Decoder for the bosstiary client opcodes. Only `0xB0` carries a payload: the
 * slot byte followed by the `uint32_t` selected boss id, Little-Endian like
 * `NetworkMessage::get<T>()`.
 */
object BosstiaryPacketHandler {

    /** protocolgame.cpp:1950-1952 dispatches this opcode to `parseSendBosstiary`. */
    const val OPCODE_SEND_BOSSTIARY: Int = 0xAE

    /** protocolgame.cpp:1953-1955 dispatches this opcode to `parseSendBosstiarySlots`. */
    const val OPCODE_SEND_BOSSTIARY_SLOTS: Int = 0xAF

    /** protocolgame.cpp:1956-1958 dispatches this opcode to `parseBosstiarySlot`. */
    const val OPCODE_BOSSTIARY_SLOT: Int = 0xB0

    fun parse(opcode: Int, buffer: ByteBuf): BosstiaryCommand? = when (opcode) {
        OPCODE_SEND_BOSSTIARY -> SendBosstiaryCommand
        OPCODE_SEND_BOSSTIARY_SLOTS -> SendBosstiarySlotsCommand
        OPCODE_BOSSTIARY_SLOT -> parseBosstiarySlot(buffer)
        else -> null
    }

    /**
     * 0xB0: reads `msg.getByte()` then `msg.get<uint32_t>()`.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    private fun parseBosstiarySlot(buffer: ByteBuf): BosstiarySlotCommand? {
        if (buffer.readableBytes() < 1) return null
        val slot = buffer.readUnsignedByte().toInt()
        if (buffer.readableBytes() < 4) return null

        return BosstiarySlotCommand(slot = slot, selectedBossId = buffer.readUnsignedIntLE())
    }
}
