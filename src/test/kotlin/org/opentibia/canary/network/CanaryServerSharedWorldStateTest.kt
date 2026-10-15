package org.opentibia.canary.network

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.protocol.CLIENT_VERSION
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.DispatcherInternalWork
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Composition-root transcript for the world-shared `Player` state:
 * - `Player::muteCountMap` (src/creatures/players/player.hpp:281) is a static
 *   map shared by every player, so the chat escalation survives relogs; the
 *   port replaces the static with a [CanaryServer]-owned instance injected into
 *   every session (AGENTS.md 1.1).
 * - the `g_game().transformItem(item, item->getID(), charges - 1)` binding
 *   (player.cpp:1065, 7588, 7697, 7718) is one world lambda shared by every
 *   `PlayerFeatures`.
 * - `g_configManager().getNumber(MAX_MESSAGEBUFFER)` (configmanager.cpp:315) is
 *   applied to the player when it enters the world (`Game::addPlayer`,
 *   game.cpp:11624).
 * - `g_dispatcher().setQueueLatencyLoggingEnabled(true)`
 *   (src/canary_server.cpp:311) is enabled at server startup.
 */
class CanaryServerSharedWorldStateTest {

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
    private val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

    private lateinit var dispatcher: GameDispatcher
    private lateinit var server: CanaryServer
    private val channels = mutableListOf<EmbeddedChannel>()

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
        val config = ConfigManager(
            ConfigKeys.all.associateWith { it.luaIdentifier },
            properties = ConfigSource.of(mapOf("maxMessageBuffer" to "7")),
        )
        // `ProtocolGame::login` places the session player (protocolgame.cpp:1056):
        // the stub login position needs a ground area for the placement to
        // succeed (a second session falls back to the neighbouring tiles).
        val gameMap = GameMap()
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
            configManager = config,
        )
    }

    @AfterEach
    fun tearDown() {
        channels.forEach { it.close() }
        dispatcher.shutdown()
    }

    private fun openChannel(): EmbeddedChannel =
        EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), server.createGameHandler())
            .also { channels.add(it) }

    private fun login(channel: EmbeddedChannel): Player {
        channel.writeInbound(loginPacket())
        assertThat(channel.readOutbound<ByteBuf>()).isNotNull
        awaitLane()
        return server.game.getPlayerByID(1L)!!
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun loginPacket(): ByteBuf {
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0)
        for (key in xteaKey) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0)
        rsaBuf.writeTibiaString("1\n1")
        rsaBuf.writeTibiaString("Knight Hero")

        val body = Unpooled.buffer()
        body.writeShortLE(2)
        body.writeShortLE(CLIENT_VERSION)
        body.writeIntLE(CLIENT_VERSION)
        body.writeTibiaString("")
        body.writeTibiaString("")
        body.writeByte(0)
        body.writeBytes(rsaCipher.encrypt(rsaPlain))

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + body.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    @Test
    fun `every session shares the root owned mute count map and transform binding`() {
        val first = login(openChannel())
        val second = login(openChannel())

        assertThat(first.muteCountMap).isSameAs(second.muteCountMap)
        assertThat(first.muteCountMap).isSameAs(server.muteCountMap)
        assertThat(first.features.muteCountMap).isSameAs(server.muteCountMap)

        assertThat(first.transformItemCharges).isSameAs(second.transformItemCharges)
        assertThat(first.transformItemCharges).isSameAs(server.transformItemCharges)
        assertThat(first.features.transformItemCharges).isSameAs(server.transformItemCharges)
    }

    @Test
    fun `the configured max message buffer reaches the logged in player`() {
        val player = login(openChannel())

        assertThat(player.maxMessageBuffer).isEqualTo(7)
    }

    @Test
    fun `start enables the queue latency telemetry on the world dispatcher`() {
        dispatcher.observeInternalWork(DispatcherInternalWork.CreatureAsyncRequeue, 1, 10)
        assertThat(dispatcher.internalWorkSnapshot(DispatcherInternalWork.CreatureAsyncRequeue).workUnits).isZero()

        server.start()
        try {
            dispatcher.observeInternalWork(DispatcherInternalWork.CreatureAsyncRequeue, 2, 10)
            assertThat(
                dispatcher.internalWorkSnapshot(DispatcherInternalWork.CreatureAsyncRequeue).workUnits,
            ).isEqualTo(2)
        } finally {
            server.stop()
        }
    }
}
