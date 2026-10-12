package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.InMemoryAccountRepository
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString

class LoginProtocolTest {

    @Test
    fun `authenticates valid credentials and receives character list`() {
        val rsaCipher = RsaCipher()
        val accountRepo = InMemoryAccountRepository()
        val channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            LoginHandler(rsaCipher, accountRepo)
        )

        // Generate XTEA session key
        val xteaKey = intArrayOf(0x11223344, 0x55667788.toInt(), 0x99AABBCC.toInt(), 0x12345678)
        val xteaCipher = XteaCipher(xteaKey)

        // Build 128-byte RSA payload
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0) // check byte
        for (k in xteaKey) {
            rsaBuf.writeIntLE(k)
        }
        rsaBuf.writeTibiaString("1") // account
        rsaBuf.writeTibiaString("1") // password

        // Encrypt with RSA
        val rsaEncrypted = rsaCipher.encrypt(rsaPlain)

        // Build outer login packet body (without 2-byte outer length, as FrameDecoder handles it or unpooled payload)
        val bodyBuf = Unpooled.buffer()
        // OS and version
        bodyBuf.writeShortLE(2) // Windows
        bodyBuf.writeShortLE(1098) // Client version
        bodyBuf.writeBytes(rsaEncrypted)

        // Calculate Adler32 checksum over (protocol byte 0x01 + body)
        val unchecksummed = Unpooled.buffer()
        unchecksummed.writeByte(0x01) // PROTOCOL_LOGIN
        unchecksummed.writeBytes(bodyBuf)

        val checksum = Adler32Checksum.compute(unchecksummed, 0, unchecksummed.readableBytes())

        // Assemble full client packet: [2-byte length] [4-byte checksum] [1-byte protocol] [body]
        val clientPacket = Unpooled.buffer()
        clientPacket.writeShortLE(4 + unchecksummed.readableBytes())
        clientPacket.writeIntLE(checksum.toInt())
        clientPacket.writeBytes(unchecksummed)

        // Send to Netty pipeline
        channel.writeInbound(clientPacket)

        // Receive response
        val responseBytes: ByteBuf? = channel.readOutbound()
        assertThat(responseBytes).isNotNull

        // Response should be framed: [2-byte length] [4-byte Adler32] [Encrypted payload]
        val responseLen = responseBytes!!.readUnsignedShortLE()
        assertThat(responseBytes.readableBytes()).isEqualTo(responseLen)

        val responseChecksum = responseBytes.readUnsignedIntLE()
        val encryptedPayloadStart = responseBytes.readerIndex()
        val encryptedPayloadLen = responseBytes.readableBytes()

        // Verify response Adler32
        val calcChecksum = Adler32Checksum.compute(responseBytes, encryptedPayloadStart, encryptedPayloadLen)
        assertThat(responseChecksum).isEqualTo(calcChecksum)

        // Decrypt response with XTEA session key
        val decrypted = xteaCipher.decrypt(responseBytes, encryptedPayloadStart, encryptedPayloadLen)
        assertThat(decrypted).isTrue()

        // Read decrypted opcodes
        val motdOpcode = responseBytes.readUnsignedByte().toInt()
        assertThat(motdOpcode).isEqualTo(LoginHandler.RESPONSE_MOTD.toInt())
        val motdText = responseBytes.readTibiaString()
        assertThat(motdText).contains("Welcome to Canary Kotlin Edition!")

        val sessionKeyOpcode = responseBytes.readUnsignedByte().toInt()
        assertThat(sessionKeyOpcode).isEqualTo(LoginHandler.RESPONSE_SESSION_KEY.toInt())
        val sessionKey = responseBytes.readTibiaString()
        assertThat(sessionKey).isEqualTo("1\n1")

        val charListOpcode = responseBytes.readUnsignedByte().toInt()
        assertThat(charListOpcode).isEqualTo(LoginHandler.RESPONSE_CHAR_LIST.toInt())

        val numWorlds = responseBytes.readUnsignedByte().toInt()
        assertThat(numWorlds).isEqualTo(1)
        responseBytes.readByte() // world id
        val worldName = responseBytes.readTibiaString()
        assertThat(worldName).isEqualTo("Canary World")
        val worldIp = responseBytes.readTibiaString()
        assertThat(worldIp).isEqualTo("127.0.0.1")
        val worldPort = responseBytes.readUnsignedShortLE()
        assertThat(worldPort).isEqualTo(7172)
        responseBytes.readByte() // preview

        val numChars = responseBytes.readUnsignedByte().toInt()
        assertThat(numChars).isEqualTo(2)

        // Char 1
        responseBytes.readByte() // world id
        val char1 = responseBytes.readTibiaString()
        assertThat(char1).isEqualTo("Knight Hero")

        // Char 2
        responseBytes.readByte() // world id
        val char2 = responseBytes.readTibiaString()
        assertThat(char2).isEqualTo("Sorcerer Master")

        val premDays = responseBytes.readUnsignedByte().toInt()
        assertThat(premDays).isEqualTo(90)
    }

    @Test
    fun `rejects invalid credentials with disconnect message`() {
        val rsaCipher = RsaCipher()
        val accountRepo = InMemoryAccountRepository()
        val channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            LoginHandler(rsaCipher, accountRepo)
        )

        val xteaKey = intArrayOf(1, 2, 3, 4)
        val xteaCipher = XteaCipher(xteaKey)

        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0)
        for (k in xteaKey) {
            rsaBuf.writeIntLE(k)
        }
        rsaBuf.writeTibiaString("1")
        rsaBuf.writeTibiaString("wrong_password")

        val rsaEncrypted = rsaCipher.encrypt(rsaPlain)

        val bodyBuf = Unpooled.buffer()
        bodyBuf.writeShortLE(2)
        bodyBuf.writeShortLE(1098)
        bodyBuf.writeBytes(rsaEncrypted)

        val unchecksummed = Unpooled.buffer()
        unchecksummed.writeByte(0x01)
        unchecksummed.writeBytes(bodyBuf)

        val checksum = Adler32Checksum.compute(unchecksummed, 0, unchecksummed.readableBytes())

        val clientPacket = Unpooled.buffer()
        clientPacket.writeShortLE(4 + unchecksummed.readableBytes())
        clientPacket.writeIntLE(checksum.toInt())
        clientPacket.writeBytes(unchecksummed)

        channel.writeInbound(clientPacket)

        val responseBytes: ByteBuf? = channel.readOutbound()
        assertThat(responseBytes).isNotNull

        val responseLen = responseBytes!!.readUnsignedShortLE()
        assertThat(responseBytes.readableBytes()).isEqualTo(responseLen)
        responseBytes.readUnsignedIntLE() // checksum

        val decrypted = xteaCipher.decrypt(responseBytes, responseBytes.readerIndex(), responseBytes.readableBytes())
        assertThat(decrypted).isTrue()

        val opcode = responseBytes.readUnsignedByte().toInt()
        assertThat(opcode).isEqualTo(LoginHandler.RESPONSE_DISCONNECT.toInt())
        val message = responseBytes.readTibiaString()
        assertThat(message).isEqualTo("Account name or password is not correct.")
    }
}
