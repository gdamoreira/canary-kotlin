package org.opentibia.canary.network.protocol.rewardchest

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

class RewardChestPacketTest {

    @Test
    fun `decodes opcode 0xFF position itemId and stackPosition in Canary order`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(0x0102) // x = 258
        payload.writeShortLE(0x0304) // y = 772
        payload.writeByte(0x07) // z = 7
        payload.writeShortLE(2148) // itemId = gold coin
        payload.writeByte(0x05) // stackPosition = 5

        val command = RewardChestCollectPacketParser.parse(payload)

        assertThat(command).isEqualTo(
            RewardChestCollectCommand(
                position = Position(258, 772, 7),
                itemId = 2148,
                stackPosition = 5,
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `interprets position fields as Little-Endian shorts`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(1000) // 0x03E8 -> bytes E8 03
        payload.writeShortLE(1000)
        payload.writeByte(7)
        payload.writeShortLE(1)
        payload.writeByte(0)

        val command = RewardChestCollectPacketParser.parse(payload)

        assertThat(command!!.position).isEqualTo(Position(1000, 1000, 7))
        assertThat(command.itemId).isEqualTo(1)
        assertThat(command.stackPosition).isZero()
    }

    @Test
    fun `reads exactly eight bytes and leaves trailing payload untouched`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(100)
        payload.writeShortLE(200)
        payload.writeByte(7)
        payload.writeShortLE(2160)
        payload.writeByte(3)
        payload.writeByte(0xCC)

        RewardChestCollectPacketParser.parse(payload)

        assertThat(payload.readableBytes()).isEqualTo(1)
        assertThat(payload.readUnsignedByte().toInt()).isEqualTo(0xCC)
    }

    @Test
    fun `returns null when fewer than eight bytes are available`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(100)
        payload.writeShortLE(200)
        payload.writeByte(7)
        payload.writeShortLE(2160) // seven bytes only, missing stackPosition

        assertThat(RewardChestCollectPacketParser.parse(payload)).isNull()
    }

    @Test
    fun `decodes bounded unsigned itemId and stackPosition`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(65535)
        payload.writeShortLE(65535)
        payload.writeByte(15)
        payload.writeShortLE(65535)
        payload.writeByte(255)

        val command = RewardChestCollectPacketParser.parse(payload)

        assertThat(command).isEqualTo(
            RewardChestCollectCommand(
                position = Position(65535, 65535, 15),
                itemId = 65535,
                stackPosition = 255,
            )
        )
    }

    @Test
    fun `decodes via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
                .writeShortLE(321)
                .writeShortLE(654)
                .writeByte(9)
                .writeShortLE(3031)
                .writeByte(4)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = RewardChestCollectPacketParser.parse(payload)

            assertThat(command).isEqualTo(
                RewardChestCollectCommand(
                    position = Position(321, 654, 9),
                    itemId = 3031,
                    stackPosition = 4,
                )
            )
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
