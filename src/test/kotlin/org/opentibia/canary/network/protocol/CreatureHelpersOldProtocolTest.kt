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
import org.opentibia.canary.network.message.writeTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Helper-count broadcast tests for `Game::updatePlayerHelpers`
 * (src/game/game.cpp:9690-9699) reaching the per-session
 * `ProtocolGame::sendCreatureHelpers` 0x94 frame
 * (protocolgame.cpp:10928-10938).
 *
 * `ProtocolGame::sendCreatureHelpers` returns early unless the session is
 * `oldProtocol`, so the frame only reaches an old-protocol profile session; the
 * `Player::sendCreatureHelpers` `client != nullptr` gate is the attached sink
 * (player.cpp:1820-1823).
 */
class CreatureHelpersOldProtocolTest {

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

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun loginPacket(version: Int): ByteBuf {
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
            body.writeIntLE(CLIENT_VERSION)
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

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun drain(channel: EmbeddedChannel) {
        channel.runPendingTasks()
        while (channel.readOutbound<ByteBuf>() != null) {
            // drain the login frames
        }
    }

    private fun readBytes(channel: EmbeddedChannel): ByteArray {
        channel.runPendingTasks()
        val frame = channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
        val result = ByteArray(frame.readableBytes())
        frame.readBytes(result)
        frame.release()
        return result
    }

    @Test
    fun `an old-protocol session receives the 0x94 helper frame through the world update seam`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        handler.game.getPlayerHelpers = { 7 }
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(1100))
        awaitLane()
        drain(ch)

        val player = requireNotNull(handler.game.getPlayerByID(1L))
        handler.game.updatePlayerHelpers(player)

        // 0x94, creature id uint32 LE, helpers uint16 LE.
        assertThat(readBytes(ch)).isEqualTo(bytes(0x94, 0x01, 0x00, 0x00, 0x00, 0x07, 0x00))
    }

    @Test
    fun `a current-profile session receives no helper frame`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        handler.game.getPlayerHelpers = { 7 }
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(CLIENT_VERSION))
        awaitLane()
        drain(ch)

        val player = requireNotNull(handler.game.getPlayerByID(1L))
        handler.game.updatePlayerHelpers(player)

        ch.runPendingTasks()
        assertThat(ch.readOutbound<ByteBuf>()).isNull()
    }
}
