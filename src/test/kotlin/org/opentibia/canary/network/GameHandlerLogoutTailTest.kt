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
import org.opentibia.canary.creatures.grouping.Party
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
import org.opentibia.canary.social.guild.Guild
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wave 66 C1 production-path tests for the logout tail at the disconnect
 * path: `ProtocolGame::logout` -> `g_game().removeCreature(player, true)`
 * (protocolgame.cpp:1178) must run the
 * `Player::onRemoveCreature(player, isLogout = true)` branch
 * (`src/creatures/players/player.cpp:12318`) — party leave, guild
 * `removeMember` (`src/creatures/players/grouping/guild.cpp:21`) and the
 * save-on-logout call — and then unregister the session through the ported
 * `Player::removeList`/`Game::removePlayer` (player.cpp:4511,
 * game.cpp:11631).
 */
class GameHandlerLogoutTailTest {

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

    private fun login(channel: EmbeddedChannel) {
        channel.writeInbound(loginPacket())
        assertThat(channel.readOutbound<ByteBuf>()).isNotNull
        awaitLane()
    }

    @Test
    fun `disconnecting removes the logged-out member from the guild online list`() {
        val ch = openChannel()
        login(ch)
        val player = server.game.getPlayerByID(1L)
        assertThat(player).isNotNull
        val guild = Guild(1, "Red Rose")
        server.game.addGuild(guild)
        player!!.setGuild(guild)
        assertThat(guild.getMembersOnline()).containsExactly(player)

        ch.close()
        awaitLane()

        assertThat(guild.getMembersOnline()).isEmpty()
        assertThat(server.game.getPlayerByID(1L)).isNull()
    }

    @Test
    fun `relogging after a disconnect keeps a single guild online entry`() {
        val first = openChannel()
        login(first)
        val guild = Guild(1, "Red Rose")
        server.game.addGuild(guild)
        server.game.getPlayerByID(1L)!!.setGuild(guild)

        first.close()
        awaitLane()
        assertThat(guild.getMembersOnline()).isEmpty()

        val second = openChannel()
        login(second)
        val relogged = server.game.getPlayerByID(1L)
        assertThat(relogged).isNotNull
        relogged!!.setGuild(guild)

        assertThat(guild.getMembersOnline()).containsExactly(relogged)
        assertThat(guild.getMemberCountOnline()).isEqualTo(1L)
    }

    @Test
    fun `logout saves the player exactly once through the disconnect path`() {
        val ch = openChannel()
        login(ch)
        val player = server.game.getPlayerByID(1L)!!
        var saves = 0
        player.savePlayer = { saves++ }

        ch.close()
        awaitLane()

        assertThat(saves).isEqualTo(1)
    }

    @Test
    fun `logout leaves the party through the disconnect path`() {
        val ch = openChannel()
        login(ch)
        val player = server.game.getPlayerByID(1L)!!
        val party = Party(leader = player)
        assertThat(player.getParty()).isSameAs(party)

        ch.close()
        awaitLane()

        assertThat(player.getParty()).isNull()
        assertThat(party.getLeader()).isNull()
    }
}
