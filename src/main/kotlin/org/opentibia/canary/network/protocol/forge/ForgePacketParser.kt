package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import org.opentibia.canary.systems.forge.ForgeAction_t

/**
 * Self-contained command produced by the client opcode `0xBF`
 * (`ProtocolGame::parseForgeEnter`, src/server/network/protocol/protocolgame.cpp:7398).
 *
 * Canary reads the `ForgeAction_t` byte, then only for `FUSION`/`TRANSFER` reads
 * the convergence byte, both item ids, and the tier. `FUSION` additionally reads
 * `usedCore` and `reduceTierLoss` unless convergence short-circuits both to
 * `false`. Resource conversions (`DUSTTOSLIVERS`, `SLIVERSTOCORES`,
 * `INCREASELIMIT`) carry only the action byte, so the trailing optional fields
 * keep their defaults.
 */
data class ForgeEnterCommand(
    val actionType: ForgeAction_t,
    val convergence: Boolean = false,
    val firstItem: Int = 0,
    val tier: Int = 0,
    val secondItem: Int = 0,
    val usedCore: Boolean = false,
    val reduceTierLoss: Boolean = false,
)

/**
 * Decoder for the forge entry request (client opcode `0xBF`). Multi-byte fields
 * are Little-Endian, matching `NetworkMessage::get<T>()`.
 */
object ForgePacketParser {

    const val OPCODE_FORGE_ENTER: Int = 0xBF

    /**
     * Decodes [buffer] into a [ForgeEnterCommand].
     *
     * @return the decoded command, or `null` when the action byte is not a
     *   [ForgeAction_t] member or the buffer is too short for the fields the
     *   action implies.
     */
    fun parseForgeEnter(buffer: ByteBuf): ForgeEnterCommand? {
        val actionByte = readByte(buffer) ?: return null
        val actionType = ForgeAction_t.entries.getOrNull(actionByte) ?: return null

        if (actionType != ForgeAction_t.FUSION && actionType != ForgeAction_t.TRANSFER) {
            return ForgeEnterCommand(actionType = actionType)
        }

        val convergence = (readByte(buffer) ?: return null) != 0
        val firstItem = readShort(buffer) ?: return null
        val tier = readByte(buffer) ?: return null
        val secondItem = readShort(buffer) ?: return null

        if (actionType == ForgeAction_t.TRANSFER) {
            return ForgeEnterCommand(
                actionType = actionType,
                convergence = convergence,
                firstItem = firstItem,
                tier = tier,
                secondItem = secondItem,
            )
        }

        // FUSION: convergence short-circuits usedCore/reduceTierLoss to false.
        val usedCore = if (convergence) false else (readByte(buffer) ?: return null) != 0
        val reduceTierLoss = if (convergence) false else (readByte(buffer) ?: return null) != 0

        return ForgeEnterCommand(
            actionType = actionType,
            convergence = convergence,
            firstItem = firstItem,
            tier = tier,
            secondItem = secondItem,
            usedCore = usedCore,
            reduceTierLoss = reduceTierLoss,
        )
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()
}
