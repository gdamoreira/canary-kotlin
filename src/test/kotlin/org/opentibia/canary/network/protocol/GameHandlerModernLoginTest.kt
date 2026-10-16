package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.RsaCipher

/**
 * Transcript tests for the modern (`Proto::ClientPendingGame`) game login:
 * `ProtocolGame::sendLoginChallenge` (protocolgame.cpp:1442-1480), the echoed
 * challenge validation (protocolgame.cpp:1366-1373) and the modern transport
 * switch (`TransportCodec`, transport_codec.cpp).
 */
class GameHandlerModernLoginTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null
        override fun authenticate(name: String, password: String): Account? {
            return if (name == "1" && password == "1") {
                Account(
                    id = 1,
                    name = "1",
                    passwordHash = "1",
                    characters = listOf(CharacterEntry("Knight Hero"))
                )
            } else null
        }
    }

    private fun channel(): EmbeddedChannel {
        val gameMap = GameMap()
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        val dispatcher = GameDispatcher().apply { start() }
        return EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            GameHandler(RsaCipher(), MockAccountRepo(), gameMap, dispatcher)
        )
    }

    @Test
    fun `modern login echoes the challenge and answers with modern framed packets`() {
        val rsaCipher = RsaCipher()
        val ch = channel()
        val key = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

        val challenge = GameLoginTestSupport.readChallenge(ch)
        ch.writeInbound(GameLoginTestSupport.modernLoginPacket(rsaCipher, key, "1\n1", "Knight Hero", challenge))

        val response = ch.readOutbound<ByteBuf>()
        assertThat(response).isNotNull

        val payload = GameLoginTestSupport.readModernFrame(response!!, key)
        // Opcode 0x17 (`sendAddCreature` self branch, `version >= 980`)
        assertThat(payload.readUnsignedByte().toInt()).isEqualTo(GameHandler.PACKET_SELF_APPEAR)
        assertThat(payload.readUnsignedIntLE()).isEqualTo(1L)
        payload.release()
        response.release()
    }

    @Test
    fun `modern login with a stale challenge is refused`() {
        val rsaCipher = RsaCipher()
        val ch = channel()
        val key = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

        val challenge = GameLoginTestSupport.readChallenge(ch)
        val stale = GameLoginTestSupport.Challenge(challenge.timestamp + 1, challenge.random)
        ch.writeInbound(GameLoginTestSupport.modernLoginPacket(rsaCipher, key, "1\n1", "Knight Hero", stale))

        assertThat(ch.isOpen).isFalse()
        assertThat(ch.readOutbound<ByteBuf>()).isNull()
    }
}
