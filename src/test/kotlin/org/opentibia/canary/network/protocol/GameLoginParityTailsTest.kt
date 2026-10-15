package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Login-sequence parity tests for `ProtocolGame::onRecvFirstMessage`'s
 * unsupported-version disconnect (src/server/network/protocol/protocolgame.cpp:
 * 1191-1210) and `ProtocolGame::login`'s client-version mismatch
 * (protocolgame.cpp:1383-1386).
 */
class GameLoginParityTailsTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? =
            if (name == "1" && password == "1") {
                Account(
                    id = 1,
                    name = "1",
                    passwordHash = "1",
                    characters = listOf(CharacterEntry("Knight Hero")),
                )
            } else {
                null
            }
    }

    private val rsaCipher = RsaCipher()
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        gameMap = GameMap()
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        dispatcher = GameDispatcher().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        channel?.finishAndReleaseAll()
        dispatcher.shutdown()
    }

    private fun loginPacket(version: Int = CLIENT_VERSION, clientVersionU32: Int = CLIENT_VERSION): ByteBuf {
        val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0) // check byte
        for (key in xteaKey) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0) // GM flag
        rsaBuf.writeTibiaString("1\n1") // session key
        rsaBuf.writeTibiaString("Knight Hero") // character name

        val body = Unpooled.buffer()
        body.writeShortLE(2) // OS
        body.writeShortLE(version)
        if (version == CLIENT_VERSION) {
            body.writeIntLE(clientVersionU32)
            body.writeTibiaString("")
            body.writeTibiaString("")
            body.writeByte(0)
        }
        body.writeBytes(rsaCipher.encrypt(rsaPlain))

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())
        val packet = Unpooled.buffer()
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    private fun readDisconnect(channel: EmbeddedChannel): String {
        channel.runPendingTasks()
        val frame = channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
        try {
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(GameHandler.PACKET_DISCONNECT)
            return frame.readTibiaString()
        } finally {
            frame.release()
        }
    }

    @Test
    fun `a version without a game-login layout is disconnected with the allowed protocols`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }

        ch.writeInbound(loginPacket(version = 9999))

        assertThat(readDisconnect(ch)).isEqualTo(
            ProtocolProfileRegistry.getUnsupportedClientProtocolMessage(includeOldProtocolProfiles = true),
        )
        assertThat(handler.game.getPlayerByID(1L)).isNull()
    }

    @Test
    fun `a modern client version mismatch is disconnected`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }

        ch.writeInbound(loginPacket(version = CLIENT_VERSION, clientVersionU32 = 1400))

        assertThat(readDisconnect(ch)).isEqualTo(
            ProtocolProfileRegistry.getUnsupportedClientProtocolMessage(includeOldProtocolProfiles = true),
        )
        assertThat(handler.game.getPlayerByID(1L)).isNull()
    }

    @Test
    fun `the supported current layout still logs in`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }

        ch.writeInbound(loginPacket())
        val ran = java.util.concurrent.CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue()

        assertThat(handler.game.getPlayerByID(1L)).isNotNull()
    }
}
