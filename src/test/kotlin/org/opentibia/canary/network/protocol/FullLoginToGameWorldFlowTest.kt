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

class FullLoginToGameWorldFlowTest {

    private lateinit var dataSource: DataSource
    private lateinit var accountRepo: JdbcAccountRepository
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private lateinit var rsaCipher: RsaCipher

    @BeforeEach
    fun setUp() {
        val h2DataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_flow_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(h2DataSource)

        dataSource = h2DataSource
        accountRepo = JdbcAccountRepository(dataSource)
        gameMap = GameMap()
        dispatcher = GameDispatcher().apply { start() }
        rsaCipher = RsaCipher()

        // Populate map spawn area around (1000, 1000, 7)
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
    fun `executes full end-to-end player login, character selection and map description load`() {
        // STEP 1: Connect to Login Server (Port 7171)
        val loginChannel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            LoginHandler(rsaCipher, accountRepo)
        )

        val loginXtea = intArrayOf(0x11111111, 0x22222222, 0x33333333, 0x44444444)
        val loginCipher = XteaCipher(loginXtea)

        val loginRsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val loginRsaBuf = Unpooled.wrappedBuffer(loginRsaPlain)
        loginRsaBuf.writerIndex(0)
        loginRsaBuf.writeByte(0)
        for (k in loginXtea) loginRsaBuf.writeIntLE(k)
        loginRsaBuf.writeTibiaString("1")
        loginRsaBuf.writeTibiaString("1")

        val loginRsaEnc = rsaCipher.encrypt(loginRsaPlain)
        val loginBody = Unpooled.buffer()
        loginBody.writeShortLE(2) // Windows
        loginBody.writeShortLE(1098) // Client Version
        loginBody.writeBytes(loginRsaEnc)

        val unchecksummed = Unpooled.buffer()
        unchecksummed.writeByte(LoginHandler.PROTOCOL_LOGIN)
        unchecksummed.writeBytes(loginBody)
        val loginChecksum = Adler32Checksum.compute(unchecksummed, 0, unchecksummed.readableBytes())

        val clientLoginPacket = Unpooled.buffer()
        clientLoginPacket.writeShortLE(4 + unchecksummed.readableBytes())
        clientLoginPacket.writeIntLE(loginChecksum.toInt())
        clientLoginPacket.writeBytes(unchecksummed)

        loginChannel.writeInbound(clientLoginPacket)

        val loginResponse: ByteBuf? = loginChannel.readOutbound()
        assertThat(loginResponse).isNotNull

        val respLen = loginResponse!!.readUnsignedShortLE()
        val respChecksum = loginResponse.readUnsignedIntLE()
        val decryptedLogin = loginCipher.decrypt(loginResponse, loginResponse.readerIndex(), loginResponse.readableBytes())
        assertThat(decryptedLogin).isTrue()

        // Verify opcodes: MOTD (0x14), Session Key (0x28), Char List (0x64)
        assertThat(loginResponse.readUnsignedByte().toInt()).isEqualTo(LoginHandler.RESPONSE_MOTD.toInt())
        val motd = loginResponse.readTibiaString()
        assertThat(motd).contains("Welcome to Canary Kotlin Edition!")

        assertThat(loginResponse.readUnsignedByte().toInt()).isEqualTo(LoginHandler.RESPONSE_SESSION_KEY.toInt())
        val sessionKey = loginResponse.readTibiaString()
        assertThat(sessionKey).isEqualTo("1\n1")

        assertThat(loginResponse.readUnsignedByte().toInt()).isEqualTo(LoginHandler.RESPONSE_CHAR_LIST.toInt())

        // STEP 2: Connect to Game Server (Port 7172) using selected character "Knight Hero"
        val gameChannel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            GameHandler(rsaCipher, accountRepo, gameMap, dispatcher)
        )

        val gameXtea = intArrayOf(0x55555555, 0x66666666, 0x77777777, 0x88888888.toInt())
        val gameCipher = XteaCipher(gameXtea)

        val gameRsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val gameRsaBuf = Unpooled.wrappedBuffer(gameRsaPlain)
        gameRsaBuf.writerIndex(0)
        gameRsaBuf.writeByte(0)
        for (k in gameXtea) gameRsaBuf.writeIntLE(k)
        gameRsaBuf.writeByte(0) // GM flag
        gameRsaBuf.writeTibiaString(sessionKey)
        gameRsaBuf.writeTibiaString("Knight Hero")

        val gameRsaEnc = rsaCipher.encrypt(gameRsaPlain)
        val gameBody = Unpooled.buffer()
        gameBody.writeShortLE(2) // Windows
        gameBody.writeShortLE(1098) // Client Version
        gameBody.writeBytes(gameRsaEnc)

        val gameChecksum = Adler32Checksum.compute(gameBody, 0, gameBody.readableBytes())
        val clientGamePacket = Unpooled.buffer()
        clientGamePacket.writeShortLE(4 + gameBody.readableBytes())
        clientGamePacket.writeIntLE(gameChecksum.toInt())
        clientGamePacket.writeBytes(gameBody)

        gameChannel.writeInbound(clientGamePacket)

        // The placement fanout's `sendAddCreature` self branch queues the self
        // appear frame first (protocolgame.cpp:8639-8668).
        val selfFrame: ByteBuf? = gameChannel.readOutbound()
        assertThat(selfFrame).isNotNull

        val selfLen = selfFrame!!.readUnsignedShortLE()
        assertThat(selfFrame.readableBytes()).isEqualTo(selfLen)
        selfFrame.readUnsignedIntLE()
        assertThat(gameCipher.decrypt(selfFrame, selfFrame.readerIndex(), selfFrame.readableBytes())).isTrue()

        // Verify Self-Appear (0x17)
        val selfAppearOpcode = selfFrame.readUnsignedByte().toInt()
        assertThat(selfAppearOpcode).isEqualTo(GameHandler.PACKET_SELF_APPEAR)

        val creatureId = selfFrame.readUnsignedIntLE()
        assertThat(creatureId).isEqualTo(1L)
        val graphicsSpeed = selfFrame.readUnsignedShortLE()
        assertThat(graphicsSpeed).isEqualTo(50)
        // The `version >= 981` speed-formula doubles, both `>= 1054`/`>= 1058`
        // expert bytes, the `>= 1080` store pair and the exiva byte
        // (protocolgame.cpp:8646-8682).
        selfFrame.skipBytes(15)
        val expertPvpControls = selfFrame.readUnsignedByte().toInt()
        assertThat(expertPvpControls).isEqualTo(0)
        assertThat(selfFrame.readUnsignedByte().toInt()).isEqualTo(0)
        assertThat(selfFrame.readTibiaString()).isEmpty()
        assertThat(selfFrame.readUnsignedShortLE()).isEqualTo(25)
        assertThat(selfFrame.readUnsignedByte().toInt()).isEqualTo(0)
        selfFrame.release()

        // The `sendAddCreature` tail frames: bug report (0x1A), tibia time
        // (0xEF), pending state (0x0A) and enter world (0x0F).
        val tailOpcodes = List(4) { readLoginFrameOpcode(gameChannel, gameCipher) }
        assertThat(tailOpcodes).containsExactly(0x1A, 0xEF, 0x0A, 0x0F)

        // The login tail then sends the map description as its own frame.
        val gameResponse: ByteBuf? = gameChannel.readOutbound()
        assertThat(gameResponse).isNotNull

        val gameRespLen = gameResponse!!.readUnsignedShortLE()
        assertThat(gameResponse.readableBytes()).isEqualTo(gameRespLen)
        gameResponse.readUnsignedIntLE()
        val decryptedGame = gameCipher.decrypt(gameResponse, gameResponse.readerIndex(), gameResponse.readableBytes())
        assertThat(decryptedGame).isTrue()

        // Verify Map Description (0x64)
        val mapDescOpcode = gameResponse.readUnsignedByte().toInt()
        assertThat(mapDescOpcode).isEqualTo(MapPacketWriter.PACKET_MAP_DESCRIPTION.toInt())
        assertThat(gameResponse.readUnsignedShortLE()).isEqualTo(1000) // Pos X
        assertThat(gameResponse.readUnsignedShortLE()).isEqualTo(1000) // Pos Y
        assertThat(gameResponse.readUnsignedByte().toInt()).isEqualTo(7)    // Pos Z
        gameResponse.release()
    }

    /** Reads one outbound frame, verifies its framing, decrypts it and returns its opcode. */
    private fun readLoginFrameOpcode(channel: EmbeddedChannel, cipher: XteaCipher): Int {
        val frame = channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
        val length = frame.readUnsignedShortLE()
        assertThat(frame.readableBytes()).isEqualTo(length)
        frame.readUnsignedIntLE() // Adler32 checksum
        assertThat(cipher.decrypt(frame, frame.readerIndex(), frame.readableBytes())).isTrue
        val opcode = frame.readUnsignedByte().toInt()
        frame.release()
        return opcode
    }
}
