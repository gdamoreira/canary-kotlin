package org.opentibia.canary.network.protocol.wheel

import io.netty.buffer.ByteBuf
import org.opentibia.canary.systems.wheel.WheelFragmentType
import org.opentibia.canary.systems.wheel.WheelGemAction
import org.opentibia.canary.systems.wheel.WheelGemAffinity
import org.opentibia.canary.systems.wheel.WheelGemQuality
import org.opentibia.canary.systems.wheel.WheelSlots

/**
 * Self-contained command DTOs for the Wheel of Destiny client opcodes. The opcode
 * is stored on every command so a caller can route on the decoded value without
 * re-reading the frame.
 *
 * Ported from Canary (Little-Endian wire order, `NetworkMessage::get<T>()`):
 *  - 0x61 `ProtocolGame::parseOpenWheel`           (src/server/network/protocol/protocolgame.cpp)
 *  - 0x62 `ProtocolGame::parseSaveWheel`           -> `PlayerWheel::saveSlotPointsOnPressSaveButton`
 *  - 0xE7 `ProtocolGame::parseWheelGemAction`      (CurrentPayload + legacy `Game::playerWheelGemAction`)
 */
sealed interface WheelCommand {
    val opcode: Int
}

data class OpenWheelCommand(val ownerId: Long) : WheelCommand {
    override val opcode: Int get() = WheelPacketParser.OPCODE_OPEN_WHEEL
}

data class WheelSlotPoints(val slot: WheelSlots, val points: Int)

data class WheelGemVessel(val affinity: WheelGemAffinity, val gemIndex: Int?)

data class SaveWheelCommand(
    val slotPoints: List<WheelSlotPoints>,
    val gemVessels: List<WheelGemVessel>
) : WheelCommand {
    override val opcode: Int get() = WheelPacketParser.OPCODE_SAVE_WHEEL
}

sealed interface WheelGemActionCommand : WheelCommand {
    val action: WheelGemAction
}

data class DestroyGemCommand(val gemIndex: Int) : WheelGemActionCommand {
    override val action: WheelGemAction get() = WheelGemAction.Destroy
    override val opcode: Int get() = WheelPacketParser.OPCODE_WHEEL_GEM_ACTION
}

data class RevealGemCommand(val quality: WheelGemQuality) : WheelGemActionCommand {
    override val action: WheelGemAction get() = WheelGemAction.Reveal
    override val opcode: Int get() = WheelPacketParser.OPCODE_WHEEL_GEM_ACTION
}

data class SwitchGemDomainCommand(val gemIndex: Int) : WheelGemActionCommand {
    override val action: WheelGemAction get() = WheelGemAction.SwitchDomain
    override val opcode: Int get() = WheelPacketParser.OPCODE_WHEEL_GEM_ACTION
}

data class ToggleGemLockCommand(val gemIndex: Int) : WheelGemActionCommand {
    override val action: WheelGemAction get() = WheelGemAction.ToggleLock
    override val opcode: Int get() = WheelPacketParser.OPCODE_WHEEL_GEM_ACTION
}

data class ImproveGemGradeCommand(
    val fragmentType: WheelFragmentType,
    val position: Int
) : WheelGemActionCommand {
    override val action: WheelGemAction get() = WheelGemAction.ImproveGrade
    override val opcode: Int get() = WheelPacketParser.OPCODE_WHEEL_GEM_ACTION
}

object WheelPacketParser {

    const val OPCODE_OPEN_WHEEL: Int = 0x61
    const val OPCODE_SAVE_WHEEL: Int = 0x62
    const val OPCODE_WHEEL_GEM_ACTION: Int = 0xE7

    fun parse(opcode: Int, buffer: ByteBuf, currentPayload: Boolean = true): WheelCommand? = when (opcode) {
        OPCODE_OPEN_WHEEL -> parseOpenWheel(buffer)
        OPCODE_SAVE_WHEEL -> parseSaveWheel(buffer)
        OPCODE_WHEEL_GEM_ACTION -> parseWheelGemAction(buffer, currentPayload)
        else -> null
    }

    /** 0x61: reads a single `uint32_t` owner id. */
    fun parseOpenWheel(buffer: ByteBuf): OpenWheelCommand? {
        if (buffer.readableBytes() < 4) return null
        return OpenWheelCommand(buffer.readUnsignedIntLE())
    }

    /**
     * 0x62: `saveSlotPointsOnPressSaveButton` reads one `uint16_t` per
     * `WheelSlots_t` (declaration order, 36 slots) and then one vessel per
     * `WheelGemAffinity_t` (Green, Red, Blue, Purple): a trailing-gem byte and,
     * only when set, a `uint16_t` gem index.
     */
    fun parseSaveWheel(buffer: ByteBuf): SaveWheelCommand? {
        val slotPoints = ArrayList<WheelSlotPoints>(WheelSlots.entries.size)
        for (slot in WheelSlots.entries) {
            val points = readShort(buffer) ?: return null
            slotPoints += WheelSlotPoints(slot, points)
        }

        val gemVessels = ArrayList<WheelGemVessel>(WheelGemAffinity.entries.size)
        for (affinity in WheelGemAffinity.entries) {
            val hasGem = readByte(buffer) ?: return null
            val gemIndex = if (hasGem == 0) null else (readShort(buffer) ?: return null)
            gemVessels += WheelGemVessel(affinity, gemIndex)
        }
        return SaveWheelCommand(slotPoints, gemVessels)
    }

    /**
     * 0xE7: the action byte selects the trailing layout. The `CurrentPayload`
     * profile (modern client) uses `uint16_t` indexes for Destroy/SwitchDomain/
     * ToggleLock and a byte quality for Reveal; the legacy layout uses a single
     * byte parameter for every action plus a byte position for ImproveGrade.
     */
    fun parseWheelGemAction(buffer: ByteBuf, currentPayload: Boolean = true): WheelGemActionCommand? {
        val actionByte = readByte(buffer) ?: return null
        val action = WheelGemAction.entries.getOrNull(actionByte) ?: return null
        return if (currentPayload) parseCurrentPayloadGems(buffer, action) else parseLegacyGems(buffer, action)
    }

    private fun parseCurrentPayloadGems(buffer: ByteBuf, action: WheelGemAction): WheelGemActionCommand? = when (action) {
        WheelGemAction.Destroy -> readShort(buffer)?.let { DestroyGemCommand(it) }
        WheelGemAction.Reveal -> readByte(buffer)?.let { byte ->
            WheelGemQuality.entries.getOrNull(byte)?.let { RevealGemCommand(it) }
        }
        WheelGemAction.SwitchDomain -> readShort(buffer)?.let { SwitchGemDomainCommand(it) }
        WheelGemAction.ToggleLock -> readShort(buffer)?.let { ToggleGemLockCommand(it) }
        WheelGemAction.ImproveGrade -> {
            val rawFragment = readByte(buffer) ?: return null
            val position = readByte(buffer) ?: return null
            val fragmentType = WheelFragmentType.entries.getOrNull(if (rawFragment != 0) 1 else 0) ?: return null
            ImproveGemGradeCommand(fragmentType, position)
        }
    }

    private fun parseLegacyGems(buffer: ByteBuf, action: WheelGemAction): WheelGemActionCommand? {
        val param = readByte(buffer) ?: return null
        return when (action) {
            WheelGemAction.Destroy -> DestroyGemCommand(param)
            WheelGemAction.Reveal -> WheelGemQuality.entries.getOrNull(param)?.let { RevealGemCommand(it) }
            WheelGemAction.SwitchDomain -> SwitchGemDomainCommand(param)
            WheelGemAction.ToggleLock -> ToggleGemLockCommand(param)
            WheelGemAction.ImproveGrade -> {
                val fragmentType = WheelFragmentType.entries.getOrNull(param) ?: return null
                val position = readByte(buffer) ?: return null
                ImproveGemGradeCommand(fragmentType, position)
            }
        }
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()
}
