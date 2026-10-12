package org.opentibia.canary.network.protocol.rewardchest

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position

/**
 * Self-contained command produced by the client opcode `0xFF`
 * (`ProtocolGame::parseRewardChestCollect`).
 *
 * Field order mirrors Canary exactly
 * (src/server/network/protocol/protocolgame.cpp:4491):
 *
 *     const auto position = msg.getPosition();
 *     auto itemId = msg.get<uint16_t>();
 *     auto stackPosition = msg.getByte();
 *
 * `NetworkMessage::getPosition` reads `x` (u16), `y` (u16) and `z` (u8)
 * (src/server/network/message/networkmessage.cpp:114).
 *
 * Canary then gates the operation on `REWARD_CHEST_COLLECT_ENABLED` and supplies
 * `REWARD_CHEST_MAX_COLLECT_ITEMS` when calling
 * `Game::playerRewardChestCollect`; those are engine/config concerns intentionally
 * outside this pure wire decoder.
 */
data class RewardChestCollectCommand(
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
)

/**
 * Decoder for the reward-chest collect request (client opcode `0xFF`).
 *
 * The payload is eight bytes: two Little-Endian `uint16_t` coordinates, one
 * unsigned byte `z`, one Little-Endian `uint16_t` item id and one unsigned byte
 * stack position.
 */
object RewardChestCollectPacketParser {

    /** u16 x (2) + u16 y (2) + u8 z (1) + u16 itemId (2) + u8 stackPosition (1). */
    private const val BYTES = 8

    /**
     * Decodes [payload] into a [RewardChestCollectCommand].
     *
     * @return the decoded command, or `null` when the buffer holds fewer than
     *   [BYTES] readable bytes.
     */
    fun parse(payload: ByteBuf): RewardChestCollectCommand? {
        if (payload.readableBytes() < BYTES) {
            return null
        }

        val x = payload.readUnsignedShortLE()
        val y = payload.readUnsignedShortLE()
        val z = payload.readUnsignedByte().toInt()
        val itemId = payload.readUnsignedShortLE()
        val stackPosition = payload.readUnsignedByte().toInt()

        return RewardChestCollectCommand(
            position = Position(x, y, z),
            itemId = itemId,
            stackPosition = stackPosition,
        )
    }
}
