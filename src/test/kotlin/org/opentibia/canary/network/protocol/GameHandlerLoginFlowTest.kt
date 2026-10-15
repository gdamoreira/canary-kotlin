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
import org.opentibia.canary.database.FeatureStateRepository
import org.opentibia.canary.database.PlayerFeatureState
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.persistence.PlayerPersistenceService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Verifies the game-login wiring in [GameHandler]: after the `Player` is created the
 * injected feature-state persistence load runs and the Lua `creatureEvent` `onLogin`
 * callbacks are dispatched on the WorldCommit lane.
 *
 * Canary order: `IOLoginData::loadPlayer` (`src/io/functions/iologindata_load_player.cpp`)
 * restores the persisted systems, then `Player::onPlacedCreature`
 * (`src/creatures/players/player.cpp:6539`) calls `CreatureEvents::playerLogin`
 * (`src/lua/creature/creatureevent.cpp:63`).
 */
class GameHandlerLoginFlowTest {

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

    private class RecordingFeatureStateRepository : FeatureStateRepository {
        val loadedPlayerIds = mutableListOf<Long>()

        override fun load(playerId: Long): PlayerFeatureState? {
            loadedPlayerIds += playerId
            return null
        }

        override fun save(state: PlayerFeatureState) = Unit
    }

    private val rsaCipher = RsaCipher()
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher

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
        dispatcher.shutdown()
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

    private fun channel(handler: GameHandler): EmbeddedChannel =
        EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), handler)

    @Test
    fun `loads feature state through the injected persistence service on login`() {
        val repository = RecordingFeatureStateRepository()
        val handler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            persistenceService = PlayerPersistenceService(repository),
        )
        val channel = channel(handler)

        channel.writeInbound(loginPacket())

        assertThat(repository.loadedPlayerIds).containsExactly(1L)
    }

    @Test
    fun `runs the registered Lua onLogin callback on the world commit lane when the player logs in`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local login = CreatureEvent("TestPlayerLogin")
            function login.onLogin(player)
                seen_login_player = player:getName()
            end
            login:register()

            local logout = CreatureEvent("TestPlayerLogout")
            function logout.onLogout(player)
                seen_logout_player = player:getName()
            end
            logout:register()
            """.trimIndent()
        )
        val handler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            luaEnvironment = environment,
        )
        val channel = channel(handler)

        channel.writeInbound(loginPacket())

        // The sentinel is queued after the login work: on the single-threaded lane its
        // completion proves the logged-in callback already ran confined there.
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

        assertThat(environment.getGlobal("seen_login_player").tojstring()).isEqualTo("Knight Hero")
        assertThat(environment.getGlobal("seen_logout_player").isnil()).isTrue()
    }

    @Test
    fun `logs in without a persistence service when none is injected`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val channel = channel(handler)

        channel.writeInbound(loginPacket())

        val response: ByteBuf? = channel.readOutbound()
        assertThat(response).isNotNull
    }

    @Test
    fun `notifies the injected player lifecycle hooks on login and disconnect`() {
        var appearances = 0
        var disappearances = 0
        val handler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            onPlayerAppear = { appearances++ },
            onPlayerDisappear = { disappearances++ },
        )
        val channel = channel(handler)

        channel.writeInbound(loginPacket())
        assertThat(appearances).isEqualTo(1)
        assertThat(disappearances).isEqualTo(0)

        channel.close()
        assertThat(disappearances).isEqualTo(1)
    }
}
