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
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.players.playerSendSink
import org.opentibia.canary.creatures.players.sendPartyCreatureHealth
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
 * Login-path test for the live player-send transport: the sink `GameHandler`
 * installs on the logged-in `Player` must resolve as the [org.opentibia.canary.creatures.players.PlayerSendSink]
 * and serialize the extended senders onto the connection channel, closing the
 * wave-54 gap where the installed base sink made every `Player::send*` seam a
 * silent no-op.
 *
 * The channel carries no codec handlers so the outbound frames are readable as
 * raw bytes; the login packet mirrors [GameHandlerLoginFlowTest] without the
 * two-byte frame length the decoder would strip.
 */
class GameHandlerSinkTransportTest {

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
        // `ProtocolGame::login` places the session player (protocolgame.cpp:1056):
        // the stub login position needs a tile for the placement to succeed.
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        dispatcher = GameDispatcher().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        channel?.finishAndReleaseAll()
        dispatcher.shutdown()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun rawLoginPacket(): ByteBuf {
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
        body.writeShortLE(CLIENT_VERSION)
        body.writeIntLE(CLIENT_VERSION)
        body.writeTibiaString("")
        body.writeTibiaString("")
        body.writeByte(0)
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

    private fun drainOutbound(channel: EmbeddedChannel) {
        while (true) {
            val frame = channel.readOutbound<ByteBuf>() ?: break
            frame.release()
        }
    }

    private fun readFrame(channel: EmbeddedChannel): ByteArray {
        val frame = channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
        val bytes = ByteArray(frame.readableBytes())
        frame.readBytes(bytes)
        frame.release()
        return bytes
    }

    @Test
    fun `login installs a sink that serializes the player send surface onto the channel`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val channel = EmbeddedChannel(handler).also { this.channel = it }

        channel.writeInbound(rawLoginPacket())
        awaitLane()
        channel.runPendingTasks()
        drainOutbound(channel)

        val loggedIn = requireNotNull(handler.game.getPlayerByID(1L)) { "player was not registered" }
        assertThat(loggedIn.playerSendSink).isNotNull()

        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = Position(1000, 1000, 7))
        loggedIn.sendPartyCreatureHealth(rat, 80)

        assertThat(readFrame(channel)).isEqualTo(
            bytes(
                0x8B,
                0x88, 0x13, 0x00, 0x00,
                0x00,
                0x61, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x88, 0x13, 0x00, 0x00,
                0x01,
                0x03, 0x00, 'R'.code, 'a'.code, 't'.code,
                0x50,
                0x02,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xD7,
                0xC8, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0xFF, 0x00, 0x01,
            ),
        )
    }
}
