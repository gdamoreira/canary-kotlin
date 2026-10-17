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
import org.opentibia.canary.network.protocol.livestream.LivestreamViewerLogin
import org.opentibia.canary.network.message.writeTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `ProtocolGame::sendClientLoginPreamble` (src/server/network/protocol/
 * protocolgame.cpp:12361-12378) on the live login path:
 *
 *  - `sendFeatures` (0x43, protocolgame.cpp:10682-10703) for an `OTCv8` login
 *    (`otclientV8 > 0`, probed at protocolgame.cpp:1378-1383);
 *  - `sendOTCRFeatures` (0x43, protocolgame.cpp:10706-10725) for any other
 *    OTClient (`operatingSystem >= CLIENTOS_OTCLIENT_LINUX`, 10) — and the
 *    `isOTCR` session flag it sets;
 *  - the trailing `0x32 0x00 uint16_t 0x00` opcode frame every OTClient gets.
 *
 * The preamble precedes every other login frame (the C++ `login()` opens with
 * `sendClientLoginPreamble`, protocolgame.cpp:936), and a failed
 * authentication never reaches it (`IOLoginData::gameWorldAuthentication`
 * disconnects before the dispatch to `login`, protocolgame.cpp:1421-1439).
 */
class GameLoginPreambleTest {

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

    /**
     * The legacy-framed game login (`ProtocolGame::sendLoginPacket`, src/client/
     * protocolgamesend.cpp): checksum, OS `u16`, protocol version `u16`, the
     * current-profile extras, then the RSA block. [otclientV8] appends the
     * OTCv8 probe inside the RSA block (`u16` length, `"OTCv8"`, version `u16`).
     */
    private fun loginPacket(
        operatingSystem: Int = 2,
        otclientV8: Int? = null,
        password: String = "1",
        accountDescriptor: String = "1",
    ): ByteBuf {
        val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0) // check byte
        for (key in xteaKey) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0) // GM flag
        rsaBuf.writeTibiaString("$accountDescriptor\n$password") // session key
        rsaBuf.writeTibiaString("Knight Hero") // character name
        if (otclientV8 != null) {
            // `gameLoginLayout->hasOtcV8Probe` (protocolgame.cpp:1378-1383).
            rsaBuf.writeShortLE(5)
            rsaBuf.writeBytes("OTCv8".toByteArray(Charsets.US_ASCII))
            rsaBuf.writeShortLE(otclientV8)
        }

        val body = Unpooled.buffer()
        body.writeShortLE(operatingSystem)
        body.writeShortLE(CLIENT_VERSION)
        body.writeIntLE(CLIENT_VERSION)
        body.writeTibiaString("15.25.794c2e.1400")
        body.writeTibiaString("asset-hash")
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

    private fun readBytes(channel: EmbeddedChannel): ByteArray {
        channel.runPendingTasks()
        val frame = channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
        return try {
            ByteArray(frame.readableBytes()).also { frame.readBytes(it) }
        } finally {
            frame.release()
        }
    }

    private fun readOpcode(channel: EmbeddedChannel): Int {
        channel.runPendingTasks()
        val frame = channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
        return try {
            frame.getUnsignedByte(frame.readerIndex()).toInt()
        } finally {
            frame.release()
        }
    }

    @Test
    fun `an OTClient login opens with the OTCR feature list and the login opcode frame`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(operatingSystem = 10))
        awaitLane()

        // `sendOTCRFeatures`: the enabled list (config default 101, 102, 103,
        // 118 — configmanager.cpp:596-599) under a `uint16_t` total.
        assertThat(readBytes(ch)).isEqualTo(
            bytes(
                0x43,
                0x04, 0x00,
                101, 0x01,
                102, 0x01,
                103, 0x01,
                118, 0x01,
            ),
        )

        // The `0x32 0x00 uint16_t 0x00` opcode frame.
        assertThat(readBytes(ch)).isEqualTo(bytes(0x32, 0x00, 0x00, 0x00))

        // The world frames follow (`sendAddCreature` self appear, 0x17).
        assertThat(readOpcode(ch)).isEqualTo(0x17)
    }

    @Test
    fun `a plain Tibia client login has no feature preamble`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(operatingSystem = 2))
        awaitLane()

        assertThat(readOpcode(ch)).isEqualTo(0x17)
    }

    @Test
    fun `an OTCv8 login gets the feature map instead of the OTCR list`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(operatingSystem = 21, otclientV8 = 261))
        awaitLane()

        // `sendFeatures`: only `GameFeature_t::ExtendedOpcode` (80) is enabled.
        assertThat(readBytes(ch)).isEqualTo(bytes(0x43, 0x01, 0x00, 0x50, 0x01))

        // Every OTClient still gets the opcode frame.
        assertThat(readBytes(ch)).isEqualTo(bytes(0x32, 0x00, 0x00, 0x00))

        assertThat(readOpcode(ch)).isEqualTo(0x17)
    }

    @Test
    fun `a failed authentication never sends the preamble`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(operatingSystem = 10, password = "wrong"))
        awaitLane()

        assertThat(readOpcode(ch)).isEqualTo(GameHandler.PACKET_DISCONNECT)
    }

    @Test
    fun `the login records the reported operating system on the player`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(operatingSystem = 12))
        awaitLane()

        assertThat(handler.game.getPlayerByName("Knight Hero")?.getOperatingSystem()?.value).isEqualTo(12)
    }

    @Test
    fun `a livestream viewer login opens with the preamble too`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(
            loginPacket(
                operatingSystem = 10,
                accountDescriptor = LivestreamViewerLogin.ACCOUNT_DESCRIPTOR,
                password = "secret",
            ),
        )
        awaitLane()

        // `ProtocolGame::castViewerLogin` sends the preamble before the
        // admission check (protocolgame.cpp:12381), so even a rejected viewer
        // gets the feature frames first.
        assertThat(readBytes(ch)).isEqualTo(
            bytes(0x43, 0x04, 0x00, 101, 0x01, 102, 0x01, 103, 0x01, 118, 0x01),
        )
        assertThat(readBytes(ch)).isEqualTo(bytes(0x32, 0x00, 0x00, 0x00))
        assertThat(readOpcode(ch)).isEqualTo(GameHandler.PACKET_DISCONNECT)
    }
}
