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
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Production-composition tests for the wave-71 H2 death tails on a session
 * player:
 *
 * - `Creature::deprecatedOnKilledCreature`'s Lua `onKill` loop
 *   (src/creatures/creature.cpp:1290-1303) bound to the session
 *   [ScriptingDispatch] with the `kill` event-type filter;
 * - `Player::getProtocolVersion` (player.cpp:1022-1028) bound to the session
 *   client version behind the loot-message colour gate (creature.cpp:789).
 */
class GameHandlerKillEventBindingTest {

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

    private lateinit var environment: LuaEnvironment
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private lateinit var game: Game
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        environment = LuaEnvironment().apply { initState() }
        gameMap = GameMap()
        dispatcher = GameDispatcher()
        game = Game(gameMap, dispatcher)
    }

    @AfterEach
    fun tearDown() {
        channel?.close()
        dispatcher.shutdown()
    }

    private fun groundArea(center: Position, radius: Int = 1) {
        for (y in center.y - radius..center.y + radius) {
            for (x in center.x - radius..center.x + radius) {
                gameMap.getOrCreateTile(Position(x, y, center.z)).ground = Item(id = 106)
            }
        }
    }

    private fun loginPacket(): ByteBuf {
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
        packet.writeShortLE(4 + body.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `the session kill event host dispatches the attached Lua kill event`() {
        environment.loadChunk(
            """
            local kill = CreatureEvent("SessionKillEvent")
            function kill.onKill(creature, target, lastHit)
                seen_session_kill = creature:getName() .. ">" .. target:getName() .. ":" .. tostring(lastHit)
            end
            kill:register()

            local think = CreatureEvent("SessionThinkEvent")
            function think.onThink(creature, interval)
                seen_session_wrong = creature:getName()
            end
            think:register()
            """.trimIndent(),
        )
        groundArea(Position(1000, 1000, 7))
        dispatcher.start()
        val ch = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher, luaEnvironment = environment, game = game),
        ).also { channel = it }
        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull
        awaitLane()

        val sessionPlayer = game.getPlayerByID(1L)
        assertThat(sessionPlayer).isNotNull
        assertThat(sessionPlayer!!.registerCreatureEvent("SessionKillEvent")).isTrue()
        assertThat(sessionPlayer.registerCreatureEvent("SessionThinkEvent")).isTrue()

        val victim = Player(
            id = 2,
            name = "Victim",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        // `Creature::deprecatedOnKilledCreature` (creature.cpp:1290-1303).
        assertThat(sessionPlayer.deprecatedOnKilledCreature(victim, true)).isFalse
        awaitLane()

        assertThat(environment.getGlobal("seen_session_kill").tojstring()).isEqualTo("Knight Hero>Victim:true")
        assertThat(environment.getGlobal("seen_session_wrong").isnil()).isTrue()
    }

    @Test
    fun `the session player resolves the client protocol version`() {
        groundArea(Position(1000, 1000, 7))
        dispatcher.start()
        val ch = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher, luaEnvironment = environment, game = game),
        ).also { channel = it }
        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull
        awaitLane()

        val sessionPlayer = game.getPlayerByID(1L)
        assertThat(sessionPlayer).isNotNull
        assertThat(sessionPlayer!!.getProtocolVersion()).isEqualTo(CLIENT_VERSION)
    }
}
