package org.opentibia.canary.network

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.CLIENT_VERSION

/**
 * Production-composition tests for the per-player mount definitions: the
 * `{CORE_DIRECTORY}/XML/mounts.xml` registry `GAME_STATE_INIT` loads
 * (`mounts->loadFromXml()`, src/game/game.cpp:982) must reach every session
 * `PlayerFeatures.mountDefinitions` (`Mounts::getInstance()` reads of
 * `Player::toggleMount`/`hasMount`/`tameMount`, player.cpp:7918-8128) instead
 * of staying per-player empty copies.
 */
class CanaryServerMountDefinitionsTest {

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

    @TempDir
    lateinit var coreDir: Path

    private lateinit var dispatcher: GameDispatcher

    private val mountsXml = """
        <mounts>
            <mount id="1" clientid="1" name="Widow Queen" speed="10" premium="yes"/>
        </mounts>
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    private fun writeMountsXml() {
        val file = File(coreDir.toFile(), "XML/mounts.xml")
        file.parentFile.mkdirs()
        file.writeText(mountsXml)
    }

    private fun loginPacket(): ByteBuf {
        val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)
        val rsaCipher = RsaCipher()

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

        val rsaEncrypted = rsaCipher.encrypt(rsaPlain)

        val body = Unpooled.buffer()
        body.writeShortLE(2)
        body.writeShortLE(CLIENT_VERSION)
        body.writeIntLE(CLIENT_VERSION)
        body.writeTibiaString("")
        body.writeTibiaString("")
        body.writeByte(0)
        body.writeBytes(rsaEncrypted)

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + body.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    private fun loginSession(core: String?): CanaryServer {
        val gameMap = GameMap()
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        val server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            accountRepository = MockAccountRepo(),
            gameMap = gameMap,
            dispatcher = dispatcher,
            coreDirectory = core,
        )
        server.start()
        val channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            server.createGameHandler(),
        )
        channel.writeInbound(loginPacket())
        awaitLane()
        return server
    }

    @Test
    fun `a session player resolves the booted mount definitions`() {
        writeMountsXml()
        val server = loginSession(coreDir.toString())
        try {
            val player = server.game.getPlayerByName("Knight Hero")
            assertThat(player).isNotNull

            assertThat(player!!.features.mounts.tameMount(1)).isTrue()
            assertThat(player.features.mounts.hasAnyMount()).isTrue
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a session player without loaded definitions cannot resolve a mount`() {
        val server = loginSession(core = null)
        try {
            val player = server.game.getPlayerByName("Knight Hero")
            assertThat(player).isNotNull

            assertThat(player!!.features.mounts.tameMount(1)).isFalse()
            assertThat(player.features.mounts.hasAnyMount()).isFalse
        } finally {
            server.stop()
        }
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
