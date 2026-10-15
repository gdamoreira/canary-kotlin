package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the `ProtocolGame::sendAddCreature` self branch
 * (src/server/network/protocol/protocolgame.cpp:8641-8682): the self appear
 * opcode, the player id, the server beat, the `version >= 981` speed-formula
 * doubles (`Creature::speedA`/`speedB`/`speedC` at precision 3,
 * src/creatures/creature.hpp:76-78), the `version >= 1054` / `version >= 1058`
 * expert-PvP control bytes, the `version >= 1080` store-images URL and
 * coin-packet pair and the current-profile exiva-button byte.
 */
class SelfAppearTailFrameTest {

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun frame(tail: SelfAppearTail): ByteArray {
        val out = Unpooled.buffer()
        GameWindowWriter.writeSelfAddCreature(out, PLAYER_ID, tail)
        val result = ByteArray(out.readableBytes())
        out.readBytes(result)
        out.release()
        return result
    }

    @Test
    fun `the current profile tail carries the speed doubles, both expert bytes, the store pair and the exiva byte`() {
        // `857.36 * 10^3 + INT32_MAX`, `261.29 * 10^3 + INT32_MAX` and
        // `-4795.01 * 10^3 + INT32_MAX` as uint32_le (`NetworkMessage::addDouble`,
        // networkmessage.cpp:174), each behind its precision byte 0x03.
        assertThat(frame(SelfAppearTail())).isEqualTo(
            bytes(
                0x17,
                0x01, 0x00, 0x00, 0x00, // player id
                0x32, 0x00, // SERVER_BEAT
                0x03, 0x0F, 0x15, 0x0D, 0x80, // speedA
                0x03, 0xA9, 0xFC, 0x03, 0x80, // speedB
                0x03, 0x7D, 0xD5, 0xB6, 0x7F, // speedC
                0x00, // version >= 1054: can change PvP framing option
                0x00, // version >= 1058: expert mode button enabled
                0x00, 0x00, // version >= 1080: empty STORE_IMAGES_URL
                0x19, 0x00, // version >= 1080: STORE_COIN_PACKET (25)
                0x00, // current profile: exiva button enabled
            ),
        )
    }

    @Test
    fun `version 980 trims the speed doubles and both expert bytes and the store pair`() {
        assertThat(frame(SelfAppearTail(clientVersion = 980))).isEqualTo(
            bytes(
                0x17,
                0x01, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x00, // exiva button
            ),
        )
    }

    @Test
    fun `version 1057 carries the first expert byte only`() {
        assertThat(frame(SelfAppearTail(clientVersion = 1057))).isEqualTo(
            bytes(
                0x17,
                0x01, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x03, 0x0F, 0x15, 0x0D, 0x80,
                0x03, 0xA9, 0xFC, 0x03, 0x80,
                0x03, 0x7D, 0xD5, 0xB6, 0x7F,
                0x00, // version >= 1054
                0x00, // exiva button
            ),
        )
    }

    @Test
    fun `version 1079 carries both expert bytes without the store pair`() {
        assertThat(frame(SelfAppearTail(clientVersion = 1079, oldProtocol = true))).isEqualTo(
            bytes(
                0x17,
                0x01, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x03, 0x0F, 0x15, 0x0D, 0x80,
                0x03, 0xA9, 0xFC, 0x03, 0x80,
                0x03, 0x7D, 0xD5, 0xB6, 0x7F,
                0x00, // version >= 1054
                0x00, // version >= 1058
                // old-protocol profiles write no exiva byte.
            ),
        )
    }

    @Test
    fun `the LoginSpeedFormula feature gate removes the doubles`() {
        assertThat(frame(SelfAppearTail(loginSpeedFormula = false))).isEqualTo(
            bytes(
                0x17,
                0x01, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x00,
                0x00,
                0x00, 0x00,
                0x19, 0x00,
                0x00,
            ),
        )
    }

    @Test
    fun `the expert controls and store values mirror the resolved inputs`() {
        assertThat(
            frame(
                SelfAppearTail(
                    expertPvpControlsEnabled = true,
                    storeImagesUrl = "https://store.example/",
                    storeCoinPacket = 7,
                    exivaEnabled = true,
                ),
            ),
        ).isEqualTo(
            bytes(
                0x17,
                0x01, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x03, 0x0F, 0x15, 0x0D, 0x80,
                0x03, 0xA9, 0xFC, 0x03, 0x80,
                0x03, 0x7D, 0xD5, 0xB6, 0x7F,
                0x01, // version >= 1054
                0x01, // version >= 1058
                0x16, 0x00, // "https://store.example/".length
                'h'.code, 't'.code, 't'.code, 'p'.code, 's'.code, ':'.code, '/'.code, '/'.code,
                's'.code, 't'.code, 'o'.code, 'r'.code, 'e'.code, '.'.code, 'e'.code, 'x'.code,
                'a'.code, 'm'.code, 'p'.code, 'l'.code, 'e'.code, '/'.code,
                0x07, 0x00, // coin packet
                0x01, // exiva button
            ),
        )
    }

    @Test
    fun `version 979 uses the legacy self appear opcode`() {
        assertThat(frame(SelfAppearTail(clientVersion = 979, loginSpeedFormula = false))).isEqualTo(
            bytes(
                GameWindowWriter.PACKET_SELF_APPEAR_LEGACY,
                0x01, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x00,
            ),
        )
    }

    private companion object {
        const val PLAYER_ID = 1L
    }
}
