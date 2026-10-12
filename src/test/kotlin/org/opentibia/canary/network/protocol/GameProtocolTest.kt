package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString

class GameProtocolTest {

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

    @Test
    fun `authenticates game client and sends initial world enter packet`() {
        val rsaCipher = RsaCipher()
        val accountRepo = MockAccountRepo()
        val gameMap = GameMap()
        val dispatcher = GameDispatcher().apply { start() }

        val channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            GameHandler(rsaCipher, accountRepo, gameMap, dispatcher)
        )

        val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)
        val xteaCipher = XteaCipher(xteaKey)

        // Build 128-byte RSA block
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0) // check byte
        for (k in xteaKey) {
            rsaBuf.writeIntLE(k)
        }
        rsaBuf.writeByte(0) // GM flag
        rsaBuf.writeTibiaString("1\n1") // session key
        rsaBuf.writeTibiaString("Knight Hero") // character name

        val rsaEncrypted = rsaCipher.encrypt(rsaPlain)

        val bodyBuf = Unpooled.buffer()
        bodyBuf.writeShortLE(2) // OS
        bodyBuf.writeShortLE(1098) // Protocol version
        bodyBuf.writeBytes(rsaEncrypted)

        val checksum = Adler32Checksum.compute(bodyBuf, 0, bodyBuf.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + bodyBuf.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(bodyBuf)

        channel.writeInbound(packet)

        val responseBytes: ByteBuf? = channel.readOutbound()
        assertThat(responseBytes).isNotNull

        val responseLen = responseBytes!!.readUnsignedShortLE()
        assertThat(responseBytes.readableBytes()).isEqualTo(responseLen)

        val respChecksum = responseBytes.readUnsignedIntLE()
        val calcChecksum = Adler32Checksum.compute(responseBytes, responseBytes.readerIndex(), responseBytes.readableBytes())
        assertThat(respChecksum).isEqualTo(calcChecksum)

        val decrypted = xteaCipher.decrypt(responseBytes, responseBytes.readerIndex(), responseBytes.readableBytes())
        assertThat(decrypted).isTrue()

        // Opcode 0x0A (Login / Self Appear)
        val opcode = responseBytes.readUnsignedByte().toInt()
        assertThat(opcode).isEqualTo(0x0A)

        val creatureId = responseBytes.readUnsignedIntLE()
        assertThat(creatureId).isEqualTo(1L)

        dispatcher.shutdown()
    }
}
