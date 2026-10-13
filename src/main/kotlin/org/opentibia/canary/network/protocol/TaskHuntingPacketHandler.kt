package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.systems.taskhunting.PreyTaskAction

/**
 * `ProtocolGame::parseTaskHuntingAction` request (client opcode `0xBA`,
 * src/server/network/protocol/protocolgame.cpp:3204-3219), handed to
 * `Game::playerTaskHuntingAction` (src/game/game.cpp:10227).
 *
 * [action] is the wire byte resolved against the existing
 * [PreyTaskAction] table; an unknown byte yields `null` (Canary's
 * `static_cast` would be undefined, and its `switch` has no default).
 */
data class TaskHuntingActionCommand(
    val slot: Int,
    val action: PreyTaskAction?,
    val upgrade: Boolean,
    val raceId: Int,
)

/**
 * Decoder for `0xBA`: the `PreySlot_t` slot byte, the action byte, the upgrade
 * flag byte and the `uint16_t` race id, Little-Endian like
 * `NetworkMessage::get<T>()`.
 */
object TaskHuntingPacketHandler {

    /** protocolgame.cpp:1968-1970 dispatches this opcode to `parseTaskHuntingAction`. */
    const val OPCODE_TASK_HUNTING_ACTION: Int = 0xBA

    /**
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(buffer: ByteBuf): TaskHuntingActionCommand? {
        if (buffer.readableBytes() < 1) return null
        val slot = buffer.readUnsignedByte().toInt()

        if (buffer.readableBytes() < 1) return null
        val action = buffer.readUnsignedByte().toInt()

        if (buffer.readableBytes() < 1) return null
        val upgrade = buffer.readUnsignedByte().toInt() != 0

        if (buffer.readableBytes() < 2) return null
        val raceId = buffer.readUnsignedShortLE()

        return TaskHuntingActionCommand(
            slot = slot,
            action = PreyTaskAction.entries.firstOrNull { it.value == action },
            upgrade = upgrade,
            raceId = raceId,
        )
    }
}
