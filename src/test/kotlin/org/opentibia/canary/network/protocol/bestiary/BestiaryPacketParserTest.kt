package org.opentibia.canary.network.protocol.bestiary

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BestiaryPacketParserTest {

    @Test
    fun `parses send races 0xE1 without reading any payload`() {
        val payload = Unpooled.buffer()

        val command = BestiaryPacketParser.parseBestiarySendRaces()

        assertThat(command).isSameAs(BestiarySendRacesCommand)
        assertThat(command.opcode).isEqualTo(0xE1)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses send creatures 0xE2 search ids branch as little endian uint16 list`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1) // search == 1 -> race id list
        payload.writeShortLE(2) // monsterAmount
        payload.writeShortLE(0x0102)
        payload.writeShortLE(0x0304)

        val command = BestiaryPacketParser.parseBestiarySendCreatures(payload)

        assertThat(command).isEqualTo(SearchRaceIdsCommand(listOf(0x0102, 0x0304)))
        assertThat(command?.opcode).isEqualTo(0xE2)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses send creatures 0xE2 name branch as length prefixed string`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0) // any search != 1 -> race name
        payload.writeShortLE(6)
        payload.writeBytes("dragon".toByteArray(Charsets.US_ASCII))

        val command = BestiaryPacketParser.parseBestiarySendCreatures(payload)

        assertThat(command).isEqualTo(SearchRaceByNameCommand("dragon"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses send creatures 0xE2 empty race id list`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeShortLE(0)

        val command = BestiaryPacketParser.parseBestiarySendCreatures(payload)

        assertThat(command).isEqualTo(SearchRaceIdsCommand(emptyList()))
    }

    @Test
    fun `parses monster data 0xE3 as little endian uint16 race id`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(0x1234)

        val command = BestiaryPacketParser.parseBestiarysendMonsterData(payload)

        assertThat(command).isEqualTo(BestiaryMonsterDataCommand(0x1234))
        assertThat(command?.opcode).isEqualTo(0xE3)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses buy charm rune 0xE4 action byte charm byte and little endian race id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(3) // action
        payload.writeByte(11) // charmId -> CHARM_CLEANSE
        payload.writeShortLE(0x0102)

        val command = BestiaryPacketParser.parseSendBuyCharmRune(payload)

        assertThat(command).isEqualTo(BuyCharmRuneCommand(action = 3, charmId = 11, raceId = 0x0102))
        assertThat(command?.opcode).isEqualTo(0xE4)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses buy charm rune 0xE4 charm byte as signed int8 matching canary cast`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0) // action
        payload.writeByte(0xFF) // -> charmRune_t CHARM_NONE (-1)
        payload.writeShortLE(7)

        val command = BestiaryPacketParser.parseSendBuyCharmRune(payload)

        assertThat(command).isEqualTo(BuyCharmRuneCommand(action = 0, charmId = -1, raceId = 7))
    }

    @Test
    fun `dispatch selects the handler by opcode and rejects unknown opcodes`() {
        val monsterPayload = Unpooled.buffer()
        monsterPayload.writeShortLE(42)
        assertThat(BestiaryPacketParser.parse(0xE3, monsterPayload))
            .isEqualTo(BestiaryMonsterDataCommand(42))

        assertThat(BestiaryPacketParser.parse(0x99, Unpooled.buffer())).isNull()
    }

    @Test
    fun `dispatch handles 0xE1 send races on an empty payload`() {
        assertThat(BestiaryPacketParser.parse(0xE1, Unpooled.buffer())).isSameAs(BestiarySendRacesCommand)
    }

    @Test
    fun `returns null for truncated payloads`() {
        val monsterPayload = Unpooled.buffer()
        monsterPayload.writeByte(0x01)
        assertThat(BestiaryPacketParser.parseBestiarysendMonsterData(monsterPayload)).isNull()

        val creaturesPayload = Unpooled.buffer()
        creaturesPayload.writeByte(1)
        creaturesPayload.writeShortLE(2) // claims two ids but provides one
        creaturesPayload.writeShortLE(5)
        assertThat(BestiaryPacketParser.parseBestiarySendCreatures(creaturesPayload)).isNull()

        val charmPayload = Unpooled.buffer()
        charmPayload.writeByte(1)
        charmPayload.writeByte(2)
        assertThat(BestiaryPacketParser.parseSendBuyCharmRune(charmPayload)).isNull()
    }

    @Test
    fun `decodes a bestiary frame delivered through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val frame = Unpooled.buffer()
            frame.writeShortLE(0x0BEE)
            assertThat(channel.writeInbound(frame)).isTrue()

            val inbound = channel.readInbound<ByteBuf>()
            assertThat(BestiaryPacketParser.parseBestiarysendMonsterData(inbound))
                .isEqualTo(BestiaryMonsterDataCommand(0x0BEE))
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
