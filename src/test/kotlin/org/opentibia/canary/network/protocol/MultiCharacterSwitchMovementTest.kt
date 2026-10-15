package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString
import javax.sql.DataSource

class MultiCharacterSwitchMovementTest {

    private lateinit var dataSource: DataSource
    private lateinit var accountRepo: JdbcAccountRepository
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private lateinit var rsaCipher: RsaCipher

    @BeforeEach
    fun setUp() {
        val h2DataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_switch_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(h2DataSource)

        dataSource = h2DataSource
        accountRepo = JdbcAccountRepository(dataSource)
        gameMap = GameMap()
        dispatcher = GameDispatcher().apply { start() }
        rsaCipher = RsaCipher()

        // Seed 20x20 grass tiles around (1000, 1000, 7)
        for (y in 990..1010) {
            for (x in 990..1010) {
                val tile = gameMap.getOrCreateTile(Position(x, y, 7))
                tile.ground = Item(id = 106) // grass
            }
        }
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
        (dataSource as? com.zaxxer.hikari.HikariDataSource)?.close()
    }

    @Test
    fun `logs in, selects char1, moves East, logs out, selects char2, moves North, and disconnects`() {
        // --- PHASE 1: LOGIN (PORT 7171) ---
        val loginChannel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            LoginHandler(rsaCipher, accountRepo)
        )

        val loginXtea = intArrayOf(0x11111111, 0x22222222, 0x33333333, 0x44444444)
        val loginCipher = XteaCipher(loginXtea)

        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0)
        for (k in loginXtea) rsaBuf.writeIntLE(k)
        rsaBuf.writeTibiaString("1")
        rsaBuf.writeTibiaString("1")

        val loginBody = Unpooled.buffer()
        loginBody.writeShortLE(2) // Windows
        loginBody.writeShortLE(1098) // Client Version
        loginBody.writeBytes(rsaCipher.encrypt(rsaPlain))

        val unchecksummed = Unpooled.buffer()
        unchecksummed.writeByte(LoginHandler.PROTOCOL_LOGIN)
        unchecksummed.writeBytes(loginBody)
        val loginChecksum = Adler32Checksum.compute(unchecksummed, 0, unchecksummed.readableBytes())

        val clientLoginPacket = Unpooled.buffer()
        clientLoginPacket.writeShortLE(4 + unchecksummed.readableBytes())
        clientLoginPacket.writeIntLE(loginChecksum.toInt())
        clientLoginPacket.writeBytes(unchecksummed)

        loginChannel.writeInbound(clientLoginPacket)

        val loginResponse: ByteBuf = loginChannel.readOutbound()
        val respLen = loginResponse.readUnsignedShortLE()
        val respChecksum = loginResponse.readUnsignedIntLE()
        val decryptedLogin = loginCipher.decrypt(loginResponse, loginResponse.readerIndex(), loginResponse.readableBytes())
        assertThat(decryptedLogin).isTrue()

        // Read MOTD (0x14)
        assertThat(loginResponse.readUnsignedByte().toInt()).isEqualTo(LoginHandler.RESPONSE_MOTD.toInt())
        loginResponse.readTibiaString()

        // Read Session Key (0x28)
        assertThat(loginResponse.readUnsignedByte().toInt()).isEqualTo(LoginHandler.RESPONSE_SESSION_KEY.toInt())
        val sessionKey = loginResponse.readTibiaString()
        assertThat(sessionKey).isEqualTo("1\n1")

        // Read Character List (0x64)
        assertThat(loginResponse.readUnsignedByte().toInt()).isEqualTo(LoginHandler.RESPONSE_CHAR_LIST.toInt())

        // --- PHASE 2: CONNECT CHAR 1 ("Knight Hero") TO GAME WORLD (PORT 7172) ---
        val char1Channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            GameHandler(rsaCipher, accountRepo, gameMap, dispatcher)
        )

        val char1Xtea = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)
        val char1Cipher = XteaCipher(char1Xtea)

        val char1RsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val char1RsaBuf = Unpooled.wrappedBuffer(char1RsaPlain)
        char1RsaBuf.writerIndex(0)
        char1RsaBuf.writeByte(0)
        for (k in char1Xtea) char1RsaBuf.writeIntLE(k)
        char1RsaBuf.writeByte(0) // GM flag
        char1RsaBuf.writeTibiaString(sessionKey)
        char1RsaBuf.writeTibiaString("Knight Hero")

        val char1GameBody = Unpooled.buffer()
        char1GameBody.writeShortLE(2)
        char1GameBody.writeShortLE(1098)
        char1GameBody.writeBytes(rsaCipher.encrypt(char1RsaPlain))

        val char1Checksum = Adler32Checksum.compute(char1GameBody, 0, char1GameBody.readableBytes())
        val char1Packet = Unpooled.buffer()
        char1Packet.writeShortLE(4 + char1GameBody.readableBytes())
        char1Packet.writeIntLE(char1Checksum.toInt())
        char1Packet.writeBytes(char1GameBody)

        char1Channel.writeInbound(char1Packet)

        // The placement fanout's self branch queues the self appear frame.
        val char1SelfAppear: ByteBuf = char1Channel.readOutbound()
        char1SelfAppear.readUnsignedShortLE() // len
        char1SelfAppear.readUnsignedIntLE()   // checksum
        char1Cipher.decrypt(char1SelfAppear, char1SelfAppear.readerIndex(), char1SelfAppear.readableBytes())

        assertThat(char1SelfAppear.readUnsignedByte().toInt()).isEqualTo(GameHandler.PACKET_SELF_APPEAR)
        val char1CreatureId = char1SelfAppear.readUnsignedIntLE()
        char1SelfAppear.readUnsignedShortLE() // server beat
        char1SelfAppear.readUnsignedByte()    // expert PvP controls

        // Read Map Description
        val char1Response: ByteBuf = char1Channel.readOutbound()
        char1Response.readUnsignedShortLE() // len
        char1Response.readUnsignedIntLE()   // checksum
        char1Cipher.decrypt(char1Response, char1Response.readerIndex(), char1Response.readableBytes())
        assertThat(char1Response.readUnsignedByte().toInt()).isEqualTo(MapPacketWriter.PACKET_MAP_DESCRIPTION.toInt())
        val char1StartX = char1Response.readUnsignedShortLE()
        val char1StartY = char1Response.readUnsignedShortLE()
        val char1StartZ = char1Response.readUnsignedByte().toInt()
        assertThat(char1StartX).isEqualTo(1000)
        assertThat(char1StartY).isEqualTo(1000)
        assertThat(char1StartZ).isEqualTo(7)

        // --- STEP 3: MOVE CHAR 1 EAST (0x66) ---
        val moveEastPayload = Unpooled.buffer()
        moveEastPayload.writeByte(0x66) // Move East
        val moveEastPadded = padToBlockMultiple(moveEastPayload)

        val char1MoveChecksum = Adler32Checksum.compute(moveEastPadded, 0, moveEastPadded.readableBytes())
        val char1EncryptedMove = moveEastPadded.copy()
        char1Cipher.encrypt(char1EncryptedMove, 0, char1EncryptedMove.readableBytes())

        val char1MovePacket = Unpooled.buffer()
        char1MovePacket.writeShortLE(4 + char1EncryptedMove.readableBytes())
        char1MovePacket.writeIntLE(char1MoveChecksum.toInt())
        char1MovePacket.writeBytes(char1EncryptedMove)

        char1Channel.writeInbound(char1MovePacket)

        // Allow GameDispatcher tick to process move
        Thread.sleep(50)

        // Assert Char 1 moved East from (1000, 1000, 7) to (1001, 1000, 7)
        val char1Entity = gameMap.getTile(Position(1001, 1000, 7))
        assertThat(char1Entity).isNotNull

        // --- STEP 4: LOGOUT CHAR 1 (0x14) & CLOSE CHANNEL ---
        val logoutPayload = Unpooled.buffer()
        logoutPayload.writeByte(0x14) // Logout opcode
        val logoutPadded = padToBlockMultiple(logoutPayload)

        val logoutChecksum = Adler32Checksum.compute(logoutPadded, 0, logoutPadded.readableBytes())
        val logoutEncrypted = logoutPadded.copy()
        char1Cipher.encrypt(logoutEncrypted, 0, logoutEncrypted.readableBytes())

        val logoutPacket = Unpooled.buffer()
        logoutPacket.writeShortLE(4 + logoutEncrypted.readableBytes())
        logoutPacket.writeIntLE(logoutChecksum.toInt())
        logoutPacket.writeBytes(logoutEncrypted)

        char1Channel.writeInbound(logoutPacket)
        char1Channel.close()
        assertThat(char1Channel.isOpen).isFalse()

        // --- PHASE 3: CONNECT CHAR 2 ("Sorcerer Master") TO GAME WORLD ---
        val char2Channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            GameHandler(rsaCipher, accountRepo, gameMap, dispatcher)
        )

        val char2Xtea = intArrayOf(0x0A0B0C0D, 0x0E0F0102, 0x03040506, 0x0708090A)
        val char2Cipher = XteaCipher(char2Xtea)

        val char2RsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val char2RsaBuf = Unpooled.wrappedBuffer(char2RsaPlain)
        char2RsaBuf.writerIndex(0)
        char2RsaBuf.writeByte(0)
        for (k in char2Xtea) char2RsaBuf.writeIntLE(k)
        char2RsaBuf.writeByte(0) // GM flag
        char2RsaBuf.writeTibiaString(sessionKey)
        char2RsaBuf.writeTibiaString("Sorcerer Master")

        val char2GameBody = Unpooled.buffer()
        char2GameBody.writeShortLE(2)
        char2GameBody.writeShortLE(1098)
        char2GameBody.writeBytes(rsaCipher.encrypt(char2RsaPlain))

        val char2Checksum = Adler32Checksum.compute(char2GameBody, 0, char2GameBody.readableBytes())
        val char2Packet = Unpooled.buffer()
        char2Packet.writeShortLE(4 + char2GameBody.readableBytes())
        char2Packet.writeIntLE(char2Checksum.toInt())
        char2Packet.writeBytes(char2GameBody)

        char2Channel.writeInbound(char2Packet)

        // The placement fanout's self branch queues the self appear frame.
        val char2SelfAppear: ByteBuf = char2Channel.readOutbound()
        char2SelfAppear.readUnsignedShortLE() // len
        char2SelfAppear.readUnsignedIntLE()   // checksum
        char2Cipher.decrypt(char2SelfAppear, char2SelfAppear.readerIndex(), char2SelfAppear.readableBytes())

        assertThat(char2SelfAppear.readUnsignedByte().toInt()).isEqualTo(GameHandler.PACKET_SELF_APPEAR)
        val char2CreatureId = char2SelfAppear.readUnsignedIntLE()
        char2SelfAppear.readUnsignedShortLE() // server beat
        char2SelfAppear.readUnsignedByte()    // expert PvP controls

        // Read Map Description
        val char2Response: ByteBuf = char2Channel.readOutbound()
        char2Response.readUnsignedShortLE() // len
        char2Response.readUnsignedIntLE()   // checksum
        char2Cipher.decrypt(char2Response, char2Response.readerIndex(), char2Response.readableBytes())
        assertThat(char2Response.readUnsignedByte().toInt()).isEqualTo(MapPacketWriter.PACKET_MAP_DESCRIPTION.toInt())
        assertThat(char2Response.readUnsignedShortLE()).isEqualTo(1000)
        assertThat(char2Response.readUnsignedShortLE()).isEqualTo(1000)
        assertThat(char2Response.readUnsignedByte().toInt()).isEqualTo(7)

        // --- STEP 5: MOVE CHAR 2 NORTH (0x65) ---
        val moveNorthPayload = Unpooled.buffer()
        moveNorthPayload.writeByte(0x65) // Move North
        val moveNorthPadded = padToBlockMultiple(moveNorthPayload)

        val char2MoveChecksum = Adler32Checksum.compute(moveNorthPadded, 0, moveNorthPadded.readableBytes())
        val char2EncryptedMove = moveNorthPadded.copy()
        char2Cipher.encrypt(char2EncryptedMove, 0, char2EncryptedMove.readableBytes())

        val char2MovePacket = Unpooled.buffer()
        char2MovePacket.writeShortLE(4 + char2EncryptedMove.readableBytes())
        char2MovePacket.writeIntLE(char2MoveChecksum.toInt())
        char2MovePacket.writeBytes(char2EncryptedMove)

        char2Channel.writeInbound(char2MovePacket)
        Thread.sleep(50)

        // Assert Char 2 moved North from (1000, 1000, 7) to (1000, 999, 7)
        val char2Entity = gameMap.getTile(Position(1000, 999, 7))
        assertThat(char2Entity).isNotNull

        // --- STEP 6: DISCONNECT CHAR 2 ---
        char2Channel.close()
        assertThat(char2Channel.isOpen).isFalse()
    }

    private fun padToBlockMultiple(buf: ByteBuf): ByteBuf {
        val len = buf.readableBytes()
        val padding = (8 - (len % 8)) % 8
        val out = Unpooled.buffer(len + padding)
        out.writeBytes(buf)
        repeat(padding) { out.writeByte(0x33) }
        return out
    }
}
