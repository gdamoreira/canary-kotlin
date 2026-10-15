package org.opentibia.canary.network.protocol.livestream

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.livestream.LivestreamManager
import org.opentibia.canary.creatures.players.livestream.LivestreamState
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.CanaryServer
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.CLIENT_VERSION
import org.opentibia.canary.network.protocol.InMemoryProtocolSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Login-entry tests for `ProtocolGame::castViewerLogin`
 * (src/server/network/protocol/protocolgame.cpp:12380-12403) and the viewer arm
 * of `ProtocolGame::parsePacketFromDispatcher` (protocolgame.cpp:1647-1666):
 * an `@livestream` game login resolves the caster through
 * `g_game().getPlayerByName`, the `canWatch` gate emits the appear sequence
 * through the viewer session transport (the caster's `Player::client` stays the
 * owner's), the `m_isLivestreamViewer` flag routes the one-byte `0xCA`
 * container resend through that same viewer transport, and a rejected viewer
 * gets `disconnectClient(reason)`.
 */
class LivestreamViewerLoginTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? = null
    }

    private val rsaCipher = RsaCipher()
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private lateinit var server: CanaryServer
    private lateinit var caster: Player
    private lateinit var ownerSink: InMemoryProtocolSink
    private lateinit var manager: LivestreamManager
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
        gameMap = GameMap()
        for (y in 999..1001) {
            for (x in 999..1001) {
                gameMap.getOrCreateTile(Position(x, y, 7)).ground = Item(id = 106)
            }
        }
        server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            accountRepository = MockAccountRepo(),
            gameMap = gameMap,
            dispatcher = dispatcher,
        )
        caster = Player(
            id = 1L,
            name = "Caster",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        ownerSink = InMemoryProtocolSink(caster, gameMap)
        caster.protocolSink = ownerSink
        assertThat(server.game.placeCreature(caster, caster.position)).isTrue()

        manager = LivestreamManager(casterClient = { player -> player.protocolSink }, now = { 1_000_000L })
        server.game.livestream = manager
    }

    @AfterEach
    fun tearDown() {
        channel?.finishAndReleaseAll()
        dispatcher.shutdown()
    }

    private fun startBroadcast(description: String = "Watch me"): Unit {
        manager.setInitialState(caster, "", description, 0)
        manager.applyState(caster, LivestreamState(description = description, broadcast = true))
    }

    private fun openViewerChannel(): EmbeddedChannel =
        EmbeddedChannel(server.createGameHandler()).also { channel = it }

    /** The game login body for the `@livestream` account descriptor. */
    private fun viewerLoginPacket(
        password: String = "secret",
        characterName: String = "Caster",
        version: Int = CLIENT_VERSION,
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
        rsaBuf.writeTibiaString("@livestream\n$password") // session key
        rsaBuf.writeTibiaString(characterName) // character name

        val body = Unpooled.buffer()
        body.writeShortLE(2) // OS
        body.writeShortLE(version)
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

    /** Wraps [payload] with a deliberately wrong checksum so the handler treats it as raw. */
    private fun writeRawInbound(channel: EmbeddedChannel, payload: ByteBuf) {
        val checksum = Adler32Checksum.compute(payload, 0, payload.readableBytes())
        val frame = Unpooled.buffer()
        frame.writeIntLE(checksum.toInt() xor 0x1)
        frame.writeBytes(payload)
        payload.release()
        channel.writeInbound(frame)
    }

    private fun awaitRouted() {
        val first = CountDownLatch(1)
        dispatcher.dispatch { first.countDown() }
        assertThat(first.await(2, TimeUnit.SECONDS)).isTrue()

        val second = CountDownLatch(1)
        dispatcher.dispatch(DispatcherLane.WorldCommit) { second.countDown() }
        assertThat(second.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun drain(channel: EmbeddedChannel): List<ByteArray> {
        channel.runPendingTasks()
        val frames = mutableListOf<ByteArray>()
        while (true) {
            val frame = channel.readOutbound<ByteBuf>() ?: break
            val bytes = ByteArray(frame.readableBytes())
            frame.readBytes(bytes)
            frame.release()
            frames += bytes
        }
        return frames
    }

    private fun opcodes(frames: List<ByteArray>): List<Int> = frames.map { it[0].toInt() and 0xFF }

    @Test
    fun `an @livestream login emits the viewer appear sequence through the viewer session`() {
        startBroadcast()
        val ch = openViewerChannel()

        ch.writeInbound(viewerLoginPacket())

        val frames = drain(ch)
        // `addViewer`'s channel event (0xF3) precedes
        // `sendLivestreamViewerAppear`: the self add-creature (0x17) + map
        // description (0x64), the broadcast text (0xB4), the description say
        // (0xAA), the icons (0xA2), the channel (0xAC) and the command list
        // (0xB4) (protocolgame.cpp:12410-12434).
        assertThat(opcodes(frames)).containsExactly(0xF3, 0x17, 0x64, 0xB4, 0xAA, 0xA2, 0xAC, 0xB4)
        assertThat(frames[0][0]).isNotEqualTo(0x14.toByte())
    }

    @Test
    fun `an @livestream login sets the viewer flag so the 0xCA resend reaches the viewer session`() {
        startBroadcast()
        caster.addContainer(5, Container(1987, 8, ItemType(id = 1987, name = "backpack", isContainer = true)))
        val ownerFramesBefore = ownerSink.frames.size
        val ch = openViewerChannel()
        ch.writeInbound(viewerLoginPacket())
        drain(ch)

        writeRawInbound(
            ch,
            Unpooled.buffer().apply {
                writeByte(0xCA)
                writeByte(5)
            },
        )
        awaitRouted()

        val frames = drain(ch)
        assertThat(frames).hasSize(1)
        // `resendLivestreamViewerContainer` re-sends the open container through
        // the viewer protocol (0x6E + cid), not through the caster's
        // `Player::client`.
        assertThat(frames[0][0]).isEqualTo(0x6E.toByte())
        assertThat(frames[0][1]).isEqualTo(5.toByte())
        assertThat(ownerSink.frames.drop(ownerFramesBefore).map { it[0] })
            .doesNotContain(0x6E.toByte())
    }

    @Test
    fun `a viewer 0xCA for an unopened container closes it on the viewer session`() {
        startBroadcast()
        val ownerFramesBefore = ownerSink.frames.size
        val ch = openViewerChannel()
        ch.writeInbound(viewerLoginPacket())
        drain(ch)

        writeRawInbound(
            ch,
            Unpooled.buffer().apply {
                writeByte(0xCA)
                writeByte(9)
            },
        )
        awaitRouted()

        val frames = drain(ch)
        assertThat(frames).hasSize(1)
        assertThat(frames[0][0]).isEqualTo(0x6F.toByte())
        assertThat(frames[0][1]).isEqualTo(9.toByte())
        assertThat(ownerSink.frames.drop(ownerFramesBefore).map { it[0] })
            .doesNotContain(0x6F.toByte())
    }

    @Test
    fun `an @livestream login for a non-broadcasting caster disconnects with the gate reason`() {
        val ch = openViewerChannel()

        ch.writeInbound(viewerLoginPacket())

        val frames = drain(ch)
        assertThat(frames).isNotEmpty
        assertThat(frames[0][0]).isEqualTo(0x14.toByte())
        assertThat(String(frames[0], 3, frames[0].size - 3, Charsets.UTF_8))
            .isEqualTo("This player is not currently streaming.")
        assertThat(opcodes(frames)).doesNotContain(0x17)
    }

    @Test
    fun `an @livestream login for an unknown caster disconnects with the unavailable reason`() {
        startBroadcast()
        val ch = openViewerChannel()

        ch.writeInbound(viewerLoginPacket(characterName = "Nobody"))

        val frames = drain(ch)
        assertThat(frames).isNotEmpty
        assertThat(frames[0][0]).isEqualTo(0x14.toByte())
        assertThat(String(frames[0], 3, frames[0].size - 3, Charsets.UTF_8))
            .isEqualTo("Livestream unavailable.")
    }

    @Test
    fun `closing a viewer session removes the viewer and keeps the caster online`() {
        startBroadcast()
        val ch = openViewerChannel()
        ch.writeInbound(viewerLoginPacket())
        drain(ch)

        ch.close()
        awaitRouted()

        assertThat(server.game.getPlayerByID(1L)).isNotNull
        assertThat(caster.protocolSink).isSameAs(ownerSink)
    }
}
