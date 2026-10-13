package org.opentibia.canary.network

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
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Proves [CanaryServer] is the runtime caller of the `startup` and `record` global event
 * families:
 *
 *  - `start()` fires every registered `startup` event, mirroring `Game::setGameState`
 *    (`g_globalEvents().startup()`, src/game/game.cpp:988; `GlobalEvents::startup`,
 *    src/lua/global/globalevent.cpp:112);
 *  - a successful game login fires every registered `record` event once the online count
 *    beats the stored record, mirroring `Player::onCreatureAppear`
 *    (src/creatures/players/player.cpp:12300) calling `Game::checkPlayersRecord`
 *    (src/game/game.cpp:9625) and `GlobalEvent::executeRecord`
 *    (src/lua/global/globalevent.cpp:314).
 */
class CanaryServerEventCallerTest {

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
    private lateinit var dispatcher: GameDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun loginPacket(): ByteBuf {
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

        val rsaEncrypted = rsaCipher.encrypt(rsaPlain)

        val body = Unpooled.buffer()
        body.writeShortLE(2) // OS
        body.writeShortLE(1098) // Protocol version
        body.writeBytes(rsaEncrypted)

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + body.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    @Test
    fun `start fires the registered global onStartup Lua event on the world commit lane`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local boot = GlobalEvent("ServerBoot")
            function boot.onStartup()
                seen_startup = true
            end
            boot:register()
            """.trimIndent()
        )
        val server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            gameMap = GameMap(),
            dispatcher = dispatcher,
            luaEnvironment = environment,
        )
        try {
            server.start()
            awaitLane()
            assertThat(environment.getGlobal("seen_startup").toboolean()).isTrue()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a game login fires the registered global onRecord Lua event once the record improves`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local record = GlobalEvent("ServerRecord")
            function record.onRecord(current, old)
                seen_record_current = current
                seen_record_old = old
            end
            record:register()
            """.trimIndent()
        )
        val server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            accountRepository = MockAccountRepo(),
            gameMap = GameMap(),
            dispatcher = dispatcher,
            luaEnvironment = environment,
        )
        try {
            val channel = EmbeddedChannel(
                TibiaFrameDecoder(),
                TibiaMessageEncoder(),
                server.createGameHandler(),
            )
            channel.writeInbound(loginPacket())
            awaitLane()

            assertThat(environment.getGlobal("seen_record_current").toint()).isEqualTo(1)
            assertThat(environment.getGlobal("seen_record_old").toint()).isEqualTo(0)

            server.onPlayerDisappear()
            assertThat(server.onPlayerAppear()).isFalse() // 1 online cannot beat the record of 1
        } finally {
            server.stop()
        }
    }
}
