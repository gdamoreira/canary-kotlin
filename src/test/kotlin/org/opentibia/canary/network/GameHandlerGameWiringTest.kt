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
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.TradeAndContainerPacketHandler
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Production-path integration tests for the `Game` engine wiring: the handler built by
 * [CanaryServer] must register the logged-in [Player] in the server-owned `Game`
 * (`Game::addPlayer`, src/game/game.cpp:11624; `Player::addList`,
 * src/creatures/players/player.cpp:4519) and route opcode application through that
 * same `Game` (`GamePacketContext.game`), so a real client packet mutates engine state.
 *
 * The tested frame is the trade request (0x7D) whose application is
 * `Game::playerRequestTrade` (src/game/game.cpp:5797).
 */
class GameHandlerGameWiringTest {

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
    private lateinit var server: CanaryServer
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
    }

    @AfterEach
    fun tearDown() {
        channel?.close()
        dispatcher.shutdown()
    }

    private fun openChannel(): EmbeddedChannel =
        EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), server.createGameHandler())
            .also { channel = it }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
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
        body.writeShortLE(1098) // Protocol version
        body.writeBytes(rsaCipher.encrypt(rsaPlain))

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + body.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    /** Frames a post-login game packet exactly as the client does: padded + XTEA + Adler32. */
    private fun encryptedGamePacket(writePayload: (ByteBuf) -> Unit): ByteBuf {
        val payload = Unpooled.buffer()
        writePayload(payload)
        val padding = (8 - (payload.readableBytes() % 8)) % 8
        repeat(padding) { payload.writeByte(0x33) }

        val encrypted = payload.copy()
        xteaCipher.encrypt(encrypted, 0, encrypted.readableBytes())
        val checksum = Adler32Checksum.compute(encrypted, 0, encrypted.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + encrypted.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(encrypted)
        return packet
    }

    @Test
    fun `a trade packet routed through the production handler mutates the server game`() {
        val ch = openChannel()
        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull
        awaitLane()

        val loggedIn = server.game.getPlayerByID(1L)
        assertThat(loggedIn).isNotNull
        assertThat(loggedIn!!.name).isEqualTo("Knight Hero")

        val partner = Player(
            id = 2L,
            name = "Druid",
            maxHealth = 150,
            health = 150,
            position = Position(1001, 1000, 7),
        )
        server.game.addPlayer(partner)

        val item = Item(
            id = 3031,
            count = 3,
            type = ItemType(id = 3031, name = "gold coin", isStackable = true, weight = 0.1, isPickupable = true),
        )
        val tile = gameMap.getOrCreateTile(Position(1000, 1000, 7))
        assertThat(server.game.internalAddItem(tile, item)).isEqualTo(ReturnValue.NOERROR)

        val offeredItems = mutableListOf<Pair<String, Item>>()
        val applied = CountDownLatch(1)
        server.game.sendTradeItemRequest = { _, name, offeredItem, _ ->
            offeredItems.add(name to offeredItem)
            applied.countDown()
        }

        ch.writeInbound(
            encryptedGamePacket { frame ->
                frame.writeByte(TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE)
                frame.writeShortLE(1000)
                frame.writeShortLE(1000)
                frame.writeByte(7)
                frame.writeShortLE(3031)
                frame.writeByte(0)
                frame.writeIntLE(2)
            },
        )

        // The router parses on the lane and dispatches the application as a follow-up
        // lane task, so the engine hook itself is the completion barrier here.
        assertThat(applied.await(2, TimeUnit.SECONDS)).isTrue()
        assertThat(offeredItems).containsExactly("Knight Hero" to item)
    }

    @Test
    fun `disconnecting the client removes the player from the server game`() {
        val ch = openChannel()
        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull
        awaitLane()
        assertThat(server.game.getPlayerByID(1L)).isNotNull

        ch.close()
        awaitLane()

        assertThat(server.game.getPlayerByID(1L)).isNull()
    }
}
