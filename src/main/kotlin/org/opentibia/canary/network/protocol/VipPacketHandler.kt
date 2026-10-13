package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.readTibiaString

/**
 * Self-contained commands produced by the VIP client opcodes, mirroring
 * `ProtocolGame::parseAddVip` / `parseRemoveVip` / `parseEditVip` /
 * `parseVipGroupActions` (src/server/network/protocol/protocolgame.cpp).
 *
 * The `PlayerVip` engine component is ported
 * (`creatures/players/vip/PlayerVip.kt`), but Canary reaches it through
 * `Player::vip()` (`player.hpp`), which this port does not compose into
 * `Player` yet, so the router cannot apply the decoded VIP commands (PARITY
 * GAP).
 */
sealed interface VipCommand {
    val opcode: Int
}

/**
 * Add-VIP request (client opcode `0xDC`), mirroring
 * `ProtocolGame::parseAddVip` (protocolgame.cpp:2844-2847): the buddy name
 * string handed to `Game::playerRequestAddVip` (game.cpp:7058).
 */
data class AddVipCommand(
    val name: String,
) : VipCommand {
    override val opcode: Int get() = VipPacketHandler.OPCODE_ADD_VIP
}

/**
 * Remove-VIP request (client opcode `0xDD`), mirroring
 * `ProtocolGame::parseRemoveVip` (protocolgame.cpp:2849-2852): the `uint32_t`
 * guid handed to `Game::playerRequestRemoveVip` (game.cpp:7098).
 */
data class RemoveVipCommand(
    val guid: Long,
) : VipCommand {
    override val opcode: Int get() = VipPacketHandler.OPCODE_REMOVE_VIP
}

/**
 * Edit-VIP request (client opcode `0xDE`), mirroring
 * `ProtocolGame::parseEditVip` (protocolgame.cpp:2854-2866): the `uint32_t`
 * guid, description string, icon `uint32_t` clamped to 10, notify byte and the
 * group id byte list handed to `Game::playerRequestEditVip` (game.cpp:7107).
 */
data class EditVipCommand(
    val guid: Long,
    val description: String,
    val icon: Int,
    val notify: Boolean,
    val groupsId: List<Int>,
) : VipCommand {
    override val opcode: Int get() = VipPacketHandler.OPCODE_EDIT_VIP
}

/**
 * VIP group action (client opcode `0xDF`), mirroring
 * `ProtocolGame::parseVipGroupActions` (protocolgame.cpp:2868-2891), which
 * calls `PlayerVIP::addGroup` / `editGroup` / `removeGroup` directly.
 */
sealed interface VipGroupActionCommand : VipCommand {
    override val opcode: Int get() = VipPacketHandler.OPCODE_VIP_GROUP_ACTIONS

    /** Action `0x01`: `PlayerVIP::addGroup(groupName)` (player_vip.cpp:185). */
    data class Add(val groupName: String) : VipGroupActionCommand

    /** Action `0x02`: `PlayerVIP::editGroup(groupId, newGroupName)` (player_vip.cpp:209). */
    data class Edit(val groupId: Int, val newGroupName: String) : VipGroupActionCommand

    /** Action `0x03`: `PlayerVIP::removeGroup(groupId)` (player_vip.cpp:165). */
    data class Remove(val groupId: Int) : VipGroupActionCommand

    /** Any other action: Canary consumes no further payload and does nothing. */
    data class Unknown(val action: Int) : VipGroupActionCommand
}

/**
 * Decoder for the VIP client opcodes.
 *
 *  - `0xDC` `ProtocolGame::parseAddVip` (protocolgame.cpp:2844)
 *  - `0xDD` `ProtocolGame::parseRemoveVip` (protocolgame.cpp:2849)
 *  - `0xDE` `ProtocolGame::parseEditVip` (protocolgame.cpp:2854)
 *  - `0xDF` `ProtocolGame::parseVipGroupActions` (protocolgame.cpp:2868)
 */
object VipPacketHandler {

    const val OPCODE_ADD_VIP: Int = 0xDC
    const val OPCODE_REMOVE_VIP: Int = 0xDD
    const val OPCODE_EDIT_VIP: Int = 0xDE
    const val OPCODE_VIP_GROUP_ACTIONS: Int = 0xDF

    /** `std::min<uint32_t>(10, ...)` icon cap (protocolgame.cpp:2860). */
    private const val MAX_VIP_ICON: Int = 10

    private const val VIP_GROUP_ACTION_ADD: Int = 0x01
    private const val VIP_GROUP_ACTION_EDIT: Int = 0x02
    private const val VIP_GROUP_ACTION_REMOVE: Int = 0x03

    fun parse(opcode: Int, buffer: ByteBuf): VipCommand? = when (opcode) {
        OPCODE_ADD_VIP -> parseAddVip(buffer)
        OPCODE_REMOVE_VIP -> parseRemoveVip(buffer)
        OPCODE_EDIT_VIP -> parseEditVip(buffer)
        OPCODE_VIP_GROUP_ACTIONS -> parseVipGroupActions(buffer)
        else -> null
    }

    /** 0xDC: reads the name string. */
    fun parseAddVip(buffer: ByteBuf): AddVipCommand? {
        val name = readString(buffer) ?: return null
        return AddVipCommand(name = name)
    }

    /** 0xDD: reads the `uint32_t` guid. */
    fun parseRemoveVip(buffer: ByteBuf): RemoveVipCommand? {
        if (buffer.readableBytes() < 4) {
            return null
        }
        return RemoveVipCommand(guid = buffer.readUnsignedIntLE())
    }

    /** 0xDE: reads the guid, description, clamped icon, notify flag and group id list. */
    fun parseEditVip(buffer: ByteBuf): EditVipCommand? {
        if (buffer.readableBytes() < 4) {
            return null
        }
        val guid = buffer.readUnsignedIntLE()

        val description = readString(buffer) ?: return null

        if (buffer.readableBytes() < 4) {
            return null
        }
        val icon = minOf(MAX_VIP_ICON.toLong(), buffer.readUnsignedIntLE()).toInt()

        if (buffer.readableBytes() < 1) {
            return null
        }
        val notify = buffer.readUnsignedByte().toInt() != 0

        if (buffer.readableBytes() < 1) {
            return null
        }
        val groupsAmount = buffer.readUnsignedByte().toInt()
        if (buffer.readableBytes() < groupsAmount) {
            return null
        }

        val groupsId = ArrayList<Int>(groupsAmount)
        for (index in 0 until groupsAmount) {
            groupsId.add(buffer.readUnsignedByte().toInt())
        }
        return EditVipCommand(
            guid = guid,
            description = description,
            icon = icon,
            notify = notify,
            groupsId = groupsId,
        )
    }

    /** 0xDF: reads the action byte and its type-specific payload. */
    fun parseVipGroupActions(buffer: ByteBuf): VipGroupActionCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }

        return when (val action = buffer.readUnsignedByte().toInt()) {
            VIP_GROUP_ACTION_ADD -> {
                val groupName = readString(buffer) ?: return null
                VipGroupActionCommand.Add(groupName = groupName)
            }

            VIP_GROUP_ACTION_EDIT -> {
                if (buffer.readableBytes() < 1) {
                    return null
                }
                val groupId = buffer.readUnsignedByte().toInt()
                val newGroupName = readString(buffer) ?: return null
                VipGroupActionCommand.Edit(groupId = groupId, newGroupName = newGroupName)
            }

            VIP_GROUP_ACTION_REMOVE -> {
                if (buffer.readableBytes() < 1) {
                    return null
                }
                VipGroupActionCommand.Remove(groupId = buffer.readUnsignedByte().toInt())
            }

            else -> VipGroupActionCommand.Unknown(action = action)
        }
    }

    /**
     * Mirrors `NetworkMessage::getString()`: a `uint16_t` Little-Endian byte
     * length followed by that many bytes, with an explicit bounds check instead
     * of trusting the frame.
     */
    private fun readString(buffer: ByteBuf): String? {
        if (buffer.readableBytes() < 2) {
            return null
        }
        val length = buffer.getUnsignedShortLE(buffer.readerIndex())
        if (buffer.readableBytes() < 2 + length) {
            return null
        }
        return buffer.readTibiaString()
    }
}
