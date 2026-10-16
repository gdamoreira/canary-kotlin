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
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for the login placement block of `ProtocolGame::login`
 * (src/server/network/protocol/protocolgame.cpp:1053-1063):
 *
 * ```
 * const bool placedCreature = g_game().placeCreature(player, player->getLoginPosition())
 *     || g_game().placeCreature(player, player->getTemplePosition(), false, true);
 * if (!placedCreature) {
 *     disconnectClient("Temple position is wrong. Please, contact the administrator.");
 * }
 * ```
 *
 * `Game::placeCreature` (game.cpp:1524) fans the appear out to every spectator
 * and `Game::internalPlaceCreature` -> `Creature::addList` registers the
 * session through `Game::addPlayer` (game.cpp:1513, player.cpp:4519).
 */
class GameHandlerLoginPlacementTest {

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
    private val xteaCipher = XteaCipher(xteaKey)

    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private lateinit var game: Game
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
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

    private fun openChannel(): EmbeddedChannel =
        EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher, game = game),
        ).also { channel = it }

    @Test
    fun `login places the session player on the map and the appear reaches spectators`() {
        groundArea(Position(1000, 1000, 7))
        val spectator = Player(
            id = 2,
            name = "Observer",
            maxHealth = 150,
            health = 150,
            position = Position(1001, 1000, 7),
        )
        val spectatorSink = InMemoryProtocolSink(spectator, gameMap)
        spectator.protocolSink = spectatorSink
        assertThat(game.placeCreature(spectator, spectator.position)).isTrue()

        dispatcher.start()
        val ch = openChannel()
        GameLoginTestSupport.readChallenge(ch)
        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull
        awaitLane()

        val sessionPlayer = game.getPlayerByID(1L)
        assertThat(sessionPlayer).isNotNull
        assertThat(sessionPlayer!!.getTile()).isNotNull
        assertThat(sessionPlayer.position).isEqualTo(Position(1000, 1000, 7))
        assertThat(sessionPlayer.getLoginPosition()).isEqualTo(Position(1000, 1000, 7))

        // `Game::placeCreature` fanout (game.cpp:1532-1536): the observer's sink
        // receives the `0x6A` add-creature frame for the logging-in player.
        assertThat(spectatorSink.frames).anyMatch { it.isNotEmpty() && it[0] == 0x6A.toByte() }
    }

    @Test
    fun `login without a valid login or temple position disconnects and does not register the session`() {
        dispatcher.start()
        val ch = openChannel()
        GameLoginTestSupport.readChallenge(ch)
        ch.writeInbound(loginPacket())

        val outbound = ch.readOutbound<ByteBuf>()
        assertThat(outbound).isNotNull
        val responseLen = outbound!!.readUnsignedShortLE()
        assertThat(outbound.readableBytes()).isEqualTo(responseLen)
        outbound.readUnsignedIntLE() // Adler32 checksum
        assertThat(xteaCipher.decrypt(outbound, outbound.readerIndex(), outbound.readableBytes())).isTrue

        assertThat(outbound.readUnsignedByte().toInt()).isEqualTo(GameHandler.PACKET_DISCONNECT)
        assertThat(outbound.readTibiaString()).isEqualTo("Temple position is wrong. Please, contact the administrator.")

        awaitLane()
        assertThat(game.getPlayerByID(1L)).isNull()
        assertThat(ch.isActive).isFalse
    }
}
