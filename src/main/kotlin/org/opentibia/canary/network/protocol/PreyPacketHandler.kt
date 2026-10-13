package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Prey action request (client opcode `0xEB`), mirroring
 * `ProtocolGame::parsePreyAction` (protocolgame.cpp:4263-4282): the slot and
 * action bytes plus the action-specific field handed to
 * `Game::playerPreyAction` (game.cpp:10218) and then to
 * `IOPrey::parsePreyAction` (ioprey.cpp:299).
 *
 * [index] stays `-1` and [raceId] `0` for the actions that do not read them,
 * exactly like the C++ locals; [option] stays `0` for the actions that do not
 * read it.
 */
data class PreyActionCommand(
    val slot: Int,
    val action: Int,
    val option: Int,
    val index: Int,
    val raceId: Int,
)

/**
 * Decoder for the prey action client opcode.
 *
 *  - `0xEB` `ProtocolGame::parsePreyAction` (protocolgame.cpp:4263)
 */
object PreyPacketHandler {

    const val OPCODE_PREY_ACTION: Int = 0xEB

    /** `PreyAction_t` values that carry an extra field (src/io/ioprey.hpp:57-64). */
    const val PREY_ACTION_MONSTER_SELECTION: Int = 2
    const val PREY_ACTION_LIST_ALL_SELECTION: Int = 4
    const val PREY_ACTION_OPTION: Int = 5

    fun parse(opcode: Int, buffer: ByteBuf): PreyActionCommand? {
        if (opcode != OPCODE_PREY_ACTION) {
            return null
        }
        if (buffer.readableBytes() < 2) {
            return null
        }

        val slot = buffer.readUnsignedByte().toInt()
        val action = buffer.readUnsignedByte().toInt()

        var option = 0
        var index = -1
        var raceId = 0
        when (action) {
            PREY_ACTION_MONSTER_SELECTION -> {
                if (buffer.readableBytes() < 1) {
                    return null
                }
                index = buffer.readUnsignedByte().toInt()
            }

            PREY_ACTION_OPTION -> {
                if (buffer.readableBytes() < 1) {
                    return null
                }
                option = buffer.readUnsignedByte().toInt()
            }

            PREY_ACTION_LIST_ALL_SELECTION -> {
                if (buffer.readableBytes() < 2) {
                    return null
                }
                raceId = buffer.readUnsignedShortLE()
            }

            else -> Unit
        }

        return PreyActionCommand(
            slot = slot,
            action = action,
            option = option,
            index = index,
            raceId = raceId,
        )
    }
}
