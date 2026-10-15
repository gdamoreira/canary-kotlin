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
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.spells.WorldType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for the 0xCA exiva-restrictions frame on no-PvP worlds
 * (src/server/network/protocol/protocolgame.cpp:12252-12312) and its login arm
 * (protocolgame.cpp:8676-8682), plus the inbound `parseExivaRestrictions` apply
 * (protocolgame.cpp:12209-12250).
 */
class ExivaRestrictionsFramesTest {

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

    private fun frameBytes(build: ByteBuf.() -> Unit): ByteArray {
        val out = Unpooled.buffer()
        out.build()
        val result = ByteArray(out.readableBytes())
        out.readBytes(result)
        out.release()
        return result
    }

    private fun exivaFrame(build: ByteBuf.() -> Unit): ByteArray = frameBytes {
        writeByte(0xCA)
        writeByte(0x00) // allowAll
        writeByte(0x01) // allowOwnGuild
        writeByte(0x01) // allowOwnParty
        writeByte(0x01) // allowVipList
        writeByte(0x01) // allowPlayerWhitelist
        writeByte(0x01) // allowGuildWhitelist
        build()
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
        val result = ByteArray(frame.readableBytes())
        frame.readBytes(result)
        frame.release()
        return result
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun context(
        game: Game? = null,
        playerGuidByName: (String) -> Long = { 0L },
        guildIdByName: (String) -> Long = { 0L },
        maxWhitelist: Int = 100,
    ): GamePacketContext = GamePacketContext(
        map = GameMap(),
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
        protocolProfile = ProtocolProfileRegistry.getCurrentProfile(),
        exivaPlayerGuidByName = playerGuidByName,
        exivaGuildIdByName = guildIdByName,
        maxExivaWhitelist = maxWhitelist,
    )

    private fun writeUpdate(
        frame: ByteBuf,
        addedPlayers: List<String> = emptyList(),
        removedPlayers: List<String> = emptyList(),
        addedGuilds: List<String> = emptyList(),
        removedGuilds: List<String> = emptyList(),
    ) {
        frame.writeByte(ExivaRestrictionsPacketHandler.OPCODE_EXIVA_RESTRICTIONS)
        frame.writeByte(0x00) // allowAll
        frame.writeByte(0x01)
        frame.writeByte(0x01)
        frame.writeByte(0x01)
        frame.writeByte(0x01)
        frame.writeByte(0x01)
        for (names in listOf(addedPlayers, removedPlayers, addedGuilds, removedGuilds)) {
            frame.writeShortLE(names.size)
            for (name in names) {
                frame.writeTibiaString(name)
            }
        }
    }

    @Test
    fun `login on a no-pvp world queues the exiva frame before the self appear`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        handler.game.setWorldType(WorldType.NO_PVP)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket())
        awaitLane()

        // `sendAddCreature`'s `!oldProtocol` exiva tail: the button byte is
        // written inline by the self appear frame and `sendExivaRestrictions(true)`
        // queues the login-mode 0xCA frame first (protocolgame.cpp:8676-8682).
        assertThat(readBytes(ch)).isEqualTo(
            exivaFrame {
                writeShortLE(0) // player whitelist count
                writeShortLE(0) // player whitelist terminator
                writeShortLE(0) // guild whitelist count
                writeShortLE(0) // guild whitelist terminator
            },
        )

        val selfAppear = readBytes(ch)
        assertThat(selfAppear[0]).isEqualTo(0x17.toByte())
        assertThat(selfAppear.last()).isEqualTo(0x01.toByte()) // exiva button enabled
    }

    @Test
    fun `login on a pvp world writes the exiva button without the 0xCA frame`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket())
        awaitLane()

        val selfAppear = readBytes(ch)
        assertThat(selfAppear[0]).isEqualTo(0x17.toByte())
        assertThat(selfAppear.last()).isEqualTo(0x00.toByte()) // exiva button disabled
    }

    @Test
    fun `the login-mode frame resolves the stored whitelists through the injected providers`() {
        val player = hero()
        player.getExivaRestrictions().playerWhitelist += listOf(101L, 202L, 303L)
        player.getExivaRestrictions().guildWhitelist += listOf(7L, 8L)

        val ch = EmbeddedChannel()
        try {
            val sink = NettyProtocolSink(
                ch,
                player,
                GameMap(),
                playerNameByGuid = { guid ->
                    when (guid) {
                        101L -> "Alice"
                        202L -> "Bob"
                        else -> ""
                    }
                },
                guildNameById = { id -> if (id == 7L) "Red Rose" else "" },
            )

            sink.sendExivaRestrictions(isLogin = true)

            assertThat(readBytes(ch)).isEqualTo(
                exivaFrame {
                    writeShortLE(3)
                    writeTibiaString("Alice")
                    writeTibiaString("Bob")
                    writeTibiaString("") // unresolved guid 303
                    writeShortLE(0) // terminator
                    writeShortLE(2)
                    writeTibiaString("Red Rose")
                    writeTibiaString("") // unresolved guild 8
                    writeShortLE(0) // terminator
                },
            )
        } finally {
            ch.finishAndReleaseAll()
        }
    }

    @Test
    fun `an inbound update on a no-pvp world applies the restrictions and echoes the accepted names`() {
        val game = Game(gameMap, dispatcher)
        game.setWorldType(WorldType.NO_PVP)
        val player = hero()
        val sink = InMemoryProtocolSink(player, gameMap)
        player.protocolSink = sink

        val frame = Unpooled.buffer()
        writeUpdate(
            frame,
            addedPlayers = listOf("Alice", "Unknown"),
            addedGuilds = listOf("Red Rose"),
        )

        val handled = GamePacketRouter.route(
            player,
            frame,
            context(
                game = game,
                playerGuidByName = { name -> if (name == "Alice") 101L else 0L },
                guildIdByName = { name -> if (name == "Red Rose") 7L else 0L },
            ),
        )
        awaitLane()

        assertThat(handled).isEqualTo(1)
        assertThat(player.getExivaRestrictions().playerWhitelist).containsExactly(101L)
        assertThat(player.getExivaRestrictions().guildWhitelist).containsExactly(7L)

        // `sendExivaRestrictions(false, added, removed, addedGuild, removedGuild)`
        // echoes only the resolved/accepted names (protocolgame.cpp:12250-12312).
        assertThat(sink.frames).containsExactly(
            exivaFrame {
                writeShortLE(1)
                writeTibiaString("Alice")
                writeShortLE(0) // removed players
                writeShortLE(1)
                writeTibiaString("Red Rose")
                writeShortLE(0) // removed guilds
            },
        )
    }

    @Test
    fun `the whitelist cap rejects names beyond MAX_EXIVA_WHITELIST`() {
        val game = Game(gameMap, dispatcher)
        game.setWorldType(WorldType.NO_PVP)
        val player = hero()
        val sink = InMemoryProtocolSink(player, gameMap)
        player.protocolSink = sink

        val frame = Unpooled.buffer()
        writeUpdate(frame, addedPlayers = listOf("Alice", "Bob", "Carol"))

        GamePacketRouter.route(
            player,
            frame,
            context(
                game = game,
                playerGuidByName = { name -> mapOf("Alice" to 101L, "Bob" to 202L, "Carol" to 303L)[name] ?: 0L },
                maxWhitelist = 2,
            ),
        )
        awaitLane()

        assertThat(player.getExivaRestrictions().playerWhitelist).containsExactly(101L, 202L)
    }

    @Test
    fun `a 0xCA on a pvp world is dropped without touching the restrictions`() {
        val game = Game(gameMap, dispatcher)
        val player = hero()
        val sink = InMemoryProtocolSink(player, gameMap)
        player.protocolSink = sink

        val frame = Unpooled.buffer()
        writeUpdate(frame, addedPlayers = listOf("Alice"))

        val handled = GamePacketRouter.route(player, frame, context(game = game))
        awaitLane()

        assertThat(handled).isEqualTo(1)
        assertThat(player.getExivaRestrictions().playerWhitelist).isEmpty()
        assertThat(sink.frames).isEmpty()
    }
}
