package org.opentibia.canary.network.protocol.npcshop

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NpcShopPacketHandlerTest {

    @Test
    fun `parses a modern buy on shop request 0x7A`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(0x1234)
        payload.writeByte(5)
        payload.writeShortLE(0x0203)
        payload.writeByte(0x01)
        payload.writeByte(0x00)

        val command = NpcShopPacketHandler.parsePlayerBuyOnShop(payload)

        assertThat(command).isEqualTo(
            PlayerBuyOnShopCommand(
                itemId = 0x1234,
                count = 5,
                amount = 0x0203,
                ignoreCap = true,
                inBackpacks = false,
            )
        )
        assertThat(command?.opcode).isEqualTo(NpcShopPacketHandler.OPCODE_PLAYER_BUY_ON_SHOP)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses an old protocol buy on shop amount as a single byte`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(0x1234)
        payload.writeByte(2)
        payload.writeByte(0x07)
        payload.writeByte(0x00)
        payload.writeByte(0x01)

        val command = NpcShopPacketHandler.parsePlayerBuyOnShop(payload, oldProtocol = true)

        assertThat(command).isEqualTo(
            PlayerBuyOnShopCommand(
                itemId = 0x1234,
                count = 2,
                amount = 0x07,
                ignoreCap = false,
                inBackpacks = true,
            )
        )
    }

    @Test
    fun `parses a modern sell on shop request 0x7B`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(0x0ABC)
        payload.writeByte(3)
        payload.writeShortLE(0x0102)
        payload.writeByte(0x01)

        val command = NpcShopPacketHandler.parsePlayerSellOnShop(payload)

        assertThat(command).isEqualTo(
            PlayerSellOnShopCommand(
                itemId = 0x0ABC,
                count = 3,
                amount = 0x0102,
                ignoreEquipped = true,
            )
        )
        assertThat(command?.opcode).isEqualTo(NpcShopPacketHandler.OPCODE_PLAYER_SELL_ON_SHOP)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `clamps a zero sell count byte to one as Canary does`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(3031)
        payload.writeByte(0)
        payload.writeShortLE(1)
        payload.writeByte(0x00)

        val command = NpcShopPacketHandler.parsePlayerSellOnShop(payload)

        assertThat(command?.count).isEqualTo(1)
    }

    @Test
    fun `parses an old protocol sell on shop amount as a single byte`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(3031)
        payload.writeByte(4)
        payload.writeByte(9)
        payload.writeByte(0x00)

        val command = NpcShopPacketHandler.parsePlayerSellOnShop(payload, oldProtocol = true)

        assertThat(command).isEqualTo(
            PlayerSellOnShopCommand(
                itemId = 3031,
                count = 4,
                amount = 9,
                ignoreEquipped = false,
            )
        )
    }

    @Test
    fun `parses a close shop request 0x7C without consuming payload bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0xAA)

        val command = NpcShopPacketHandler.parsePlayerCloseShop()

        assertThat(command).isEqualTo(PlayerCloseShopCommand)
        assertThat(command.opcode).isEqualTo(NpcShopPacketHandler.OPCODE_PLAYER_CLOSE_SHOP)
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `dispatches shop opcodes through the opcode entry point`() {
        val buy = Unpooled.buffer()
        buy.writeShortLE(2160)
        buy.writeByte(1)
        buy.writeShortLE(1)
        buy.writeByte(0)
        buy.writeByte(0)

        assertThat(NpcShopPacketHandler.parse(NpcShopPacketHandler.OPCODE_PLAYER_BUY_ON_SHOP, buy))
            .isEqualTo(
                PlayerBuyOnShopCommand(2160, 1, 1, ignoreCap = false, inBackpacks = false)
            )

        val sell = Unpooled.buffer()
        sell.writeShortLE(2160)
        sell.writeByte(1)
        sell.writeShortLE(1)
        sell.writeByte(0)

        assertThat(NpcShopPacketHandler.parse(NpcShopPacketHandler.OPCODE_PLAYER_SELL_ON_SHOP, sell))
            .isEqualTo(PlayerSellOnShopCommand(2160, 1, 1, ignoreEquipped = false))

        assertThat(NpcShopPacketHandler.parse(NpcShopPacketHandler.OPCODE_PLAYER_CLOSE_SHOP, Unpooled.buffer()))
            .isEqualTo(PlayerCloseShopCommand)
    }

    @Test
    fun `returns null for an unhandled opcode`() {
        assertThat(NpcShopPacketHandler.parse(0x00, Unpooled.buffer())).isNull()
    }

    @Test
    fun `returns null when a buy on shop frame is truncated`() {
        val missingFlags = Unpooled.buffer()
        missingFlags.writeShortLE(2160)
        missingFlags.writeByte(1)
        missingFlags.writeShortLE(1)
        missingFlags.writeByte(0x01)

        assertThat(NpcShopPacketHandler.parsePlayerBuyOnShop(missingFlags)).isNull()

        val missingAmount = Unpooled.buffer()
        missingAmount.writeShortLE(2160)
        missingAmount.writeByte(1)
        missingAmount.writeByte(1)

        assertThat(NpcShopPacketHandler.parsePlayerBuyOnShop(missingAmount, oldProtocol = true)).isNull()

        assertThat(NpcShopPacketHandler.parsePlayerBuyOnShop(Unpooled.buffer())).isNull()
    }

    @Test
    fun `returns null when a sell on shop frame is truncated`() {
        val missingFlag = Unpooled.buffer()
        missingFlag.writeShortLE(2160)
        missingFlag.writeByte(1)
        missingFlag.writeShortLE(1)

        assertThat(NpcShopPacketHandler.parsePlayerSellOnShop(missingFlag)).isNull()
        assertThat(NpcShopPacketHandler.parsePlayerSellOnShop(Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes a buy on shop request through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val frame = Unpooled.buffer()
            frame.writeShortLE(3031)
            frame.writeByte(10)
            frame.writeShortLE(10)
            frame.writeByte(0x01)
            frame.writeByte(0x01)
            assertThat(channel.writeInbound(frame)).isTrue()

            val inbound = channel.readInbound<ByteBuf>()
            val command = NpcShopPacketHandler.parse(
                NpcShopPacketHandler.OPCODE_PLAYER_BUY_ON_SHOP,
                inbound,
            )

            assertThat(command).isEqualTo(
                PlayerBuyOnShopCommand(3031, 10, 10, ignoreCap = true, inBackpacks = true)
            )
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
