package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.forge.ForgeAction_t

class ForgePacketParserTest {

    @Test
    fun `parses fusion 0xBF non convergence including used core and reduce tier loss`() {
        val payload = Unpooled.buffer()
        payload.writeByte(ForgeAction_t.FUSION.value) // 0
        payload.writeByte(0) // convergence = false
        payload.writeShortLE(0x1234) // firstItem
        payload.writeByte(5) // tier
        payload.writeShortLE(0x5678) // secondItem
        payload.writeByte(1) // usedCore
        payload.writeByte(0) // reduceTierLoss

        val command = ForgePacketParser.parseForgeEnter(payload)

        assertThat(command).isEqualTo(
            ForgeEnterCommand(
                actionType = ForgeAction_t.FUSION,
                convergence = false,
                firstItem = 0x1234,
                tier = 5,
                secondItem = 0x5678,
                usedCore = true,
                reduceTierLoss = false,
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses fusion 0xBF convergence without reading used core or reduce tier loss`() {
        val payload = Unpooled.buffer()
        payload.writeByte(ForgeAction_t.FUSION.value)
        payload.writeByte(1) // convergence = true
        payload.writeShortLE(0x1234)
        payload.writeByte(5)
        payload.writeShortLE(0x5678)

        val command = ForgePacketParser.parseForgeEnter(payload)

        assertThat(command).isEqualTo(
            ForgeEnterCommand(
                actionType = ForgeAction_t.FUSION,
                convergence = true,
                firstItem = 0x1234,
                tier = 5,
                secondItem = 0x5678,
                usedCore = false,
                reduceTierLoss = false,
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses transfer 0xBF without reading used core or reduce tier loss`() {
        val payload = Unpooled.buffer()
        payload.writeByte(ForgeAction_t.TRANSFER.value) // 1
        payload.writeByte(0) // convergence = false
        payload.writeShortLE(0x0102)
        payload.writeByte(3)
        payload.writeShortLE(0x0304)

        val command = ForgePacketParser.parseForgeEnter(payload)

        assertThat(command).isEqualTo(
            ForgeEnterCommand(
                actionType = ForgeAction_t.TRANSFER,
                convergence = false,
                firstItem = 0x0102,
                tier = 3,
                secondItem = 0x0304,
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses resource conversion actions 0xBF with a single byte`() {
        for (action in listOf(
            ForgeAction_t.DUSTTOSLIVERS,
            ForgeAction_t.SLIVERSTOCORES,
            ForgeAction_t.INCREASELIMIT,
        )) {
            val payload = Unpooled.buffer()
            payload.writeByte(action.value)

            val command = ForgePacketParser.parseForgeEnter(payload)

            assertThat(command).isEqualTo(ForgeEnterCommand(actionType = action))
            assertThat(payload.readableBytes()).isZero()
        }
    }

    @Test
    fun `rejects an unknown forge action byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)

        assertThat(ForgePacketParser.parseForgeEnter(payload)).isNull()
    }

    @Test
    fun `returns null when the payload is truncated`() {
        val fusionPayload = Unpooled.buffer()
        fusionPayload.writeByte(ForgeAction_t.FUSION.value)
        fusionPayload.writeByte(0)
        fusionPayload.writeShortLE(0x1234)
        // missing tier, second item, used core and reduce tier loss
        assertThat(ForgePacketParser.parseForgeEnter(fusionPayload)).isNull()

        val emptyPayload = Unpooled.buffer()
        assertThat(ForgePacketParser.parseForgeEnter(emptyPayload)).isNull()
    }

    @Test
    fun `decodes a forge enter frame delivered through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val frame = Unpooled.buffer()
            frame.writeByte(ForgeAction_t.DUSTTOSLIVERS.value)
            assertThat(channel.writeInbound(frame)).isTrue()

            val inbound = channel.readInbound<ByteBuf>()
            assertThat(ForgePacketParser.parseForgeEnter(inbound))
                .isEqualTo(ForgeEnterCommand(actionType = ForgeAction_t.DUSTTOSLIVERS))
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
