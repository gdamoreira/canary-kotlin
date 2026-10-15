package org.opentibia.canary.network.protocol

import com.zaxxer.hikari.HikariDataSource
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
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.database.JdbcPlayerDepotRepository
import org.opentibia.canary.database.JdbcPlayerItemsRepository
import org.opentibia.canary.database.JdbcPlayerKillsRepository
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.database.JdbcPlayerRewardsRepository
import org.opentibia.canary.database.JdbcPlayerSpellsRepository
import org.opentibia.canary.database.JdbcPlayerStashRepository
import org.opentibia.canary.database.JdbcPlayerStorageRepository
import org.opentibia.canary.database.JdbcVipRepository
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.iologindata.IOLoginData
import org.opentibia.canary.io.iologindata.IOLoginDataSubloads
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
import org.opentibia.canary.network.protocol.misc.MiscSessionWriter
import org.opentibia.canary.network.protocol.vip.VipWindowWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for the `ProtocolGame::login` VIP burst
 * (src/server/network/protocol/protocolgame.cpp:8746-8784): `sendVIPGroups`
 * (0xD4) on version >= 1100 followed by one `sendVIP` (0xD2) per
 * `IOLoginData::getVIPEntries(player->getAccountId())` record, with the live
 * `PlayerVIP::getStatus` of each online entry and `VipStatus_t::Offline` for
 * the rest.
 */
class GameHandlerLoginVipBurstTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? =
            if (name == "1" && password == "1") {
                Account(
                    id = 1,
                    name = "1",
                    passwordHash = "1",
                    characters = listOf(
                        CharacterEntry("Knight Hero"),
                        CharacterEntry("Sorcerer Master"),
                    ),
                )
            } else {
                null
            }
    }

    private val rsaCipher = RsaCipher()
    private val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)
    private val xteaCipher = XteaCipher(xteaKey)

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_vip_burst_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)

        // `account_viplist` + `account_vipgroups`/`account_vipgrouplist` rows of
        // account 1 (the seeded `Knight Hero` owner).
        dataSource.connection.use { conn ->
            conn.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO account_viplist (account_id, player_id, description, icon, notify) " +
                        "VALUES (1, 2, 'Best friend', 3, 1)",
                )
                statement.executeUpdate(
                    "INSERT INTO account_vipgroups (id, account_id, name, customizable) VALUES (1, 1, 'Hunting', 1)",
                )
                statement.executeUpdate(
                    "INSERT INTO account_vipgrouplist (account_id, player_id, vipgroup_id) VALUES (1, 2, 1)",
                )
            }
        }

        loginData = IOLoginData(
            players = JdbcPlayerRepository(dataSource),
            accounts = JdbcAccountRepository(dataSource),
            vip = JdbcVipRepository(dataSource),
            storageRepository = JdbcPlayerStorageRepository(dataSource),
            spells = JdbcPlayerSpellsRepository(dataSource),
            kills = JdbcPlayerKillsRepository(dataSource),
            stash = JdbcPlayerStashRepository(dataSource),
            rewards = JdbcPlayerRewardsRepository(dataSource),
            features = JdbcFeatureStateRepository(dataSource),
            items = JdbcPlayerItemsRepository(dataSource),
            depots = JdbcPlayerDepotRepository(dataSource),
            // `IOLoginDataLoad::loadPlayerVip` (iologindata_load_player.cpp:770-806):
            // the account VIP list and groups populate the session `PlayerVIP`
            // the burst's `sendVip` group ids and `sendVipGroups` read.
            subloads = IOLoginDataSubloads(vip = JdbcVipRepository(dataSource)),
        )

        dispatcher = GameDispatcher().apply { start() }
        gameMap = GameMap()
        for (y in 999..1001) {
            for (x in 999..1001) {
                gameMap.getOrCreateTile(Position(x, y, 7)).ground = Item(id = 106)
            }
        }
    }

    @AfterEach
    fun tearDown() {
        channel?.close()
        dispatcher.shutdown()
        dataSource.close()
    }

    private fun loginPacket(): ByteBuf = loginPacket("Knight Hero")

    private fun loginPacket(characterName: String): ByteBuf {
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0) // check byte
        for (key in xteaKey) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0) // GM flag
        rsaBuf.writeTibiaString("1\n1") // session key
        rsaBuf.writeTibiaString(characterName)

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

    /** Reads one outbound frame, verifies its framing and decrypts it in place. */
    private fun readFrame(ch: EmbeddedChannel): ByteBuf {
        val frame = ch.readOutbound<ByteBuf>()
        assertThat(frame).isNotNull
        val length = frame!!.readUnsignedShortLE()
        assertThat(frame.readableBytes()).isEqualTo(length)
        frame.readUnsignedIntLE() // Adler32 checksum
        assertThat(xteaCipher.decrypt(frame, frame.readerIndex(), frame.readableBytes())).isTrue
        return frame
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    /**
     * The `ProtocolGame::sendAddCreature` tail frames queued between the self
     * appear frame and the map description (protocolgame.cpp:8686-8693):
     * `sendAllowBugReport` (0x1A), `sendTibiaTime` (0xEF),
     * `sendPendingStateEntered` (0x0A) and `sendEnterWorld` (0x0F).
     */
    private fun assertLoginTail(ch: EmbeddedChannel) {
        assertThat(readFrame(ch).readUnsignedByte().toInt())
            .isEqualTo(MiscSessionWriter.PACKET_ALLOW_BUG_REPORT)
        assertThat(readFrame(ch).readUnsignedByte().toInt())
            .isEqualTo(CreatureWindowWriter.PACKET_TIBIA_TIME)
        assertThat(readFrame(ch).readUnsignedByte().toInt())
            .isEqualTo(MiscSessionWriter.PACKET_PENDING_STATE_ENTERED)
        assertThat(readFrame(ch).readUnsignedByte().toInt())
            .isEqualTo(MiscSessionWriter.PACKET_ENTER_WORLD)
    }

    @Test
    fun `login sends the VIP groups dialog and one entry per persisted record`() {
        val handler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            ioLoginData = loginData,
        )
        val ch = EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), handler)
        channel = ch

        ch.writeInbound(loginPacket())
        awaitLane()

        // 1. Self appear (0x17), map description (0x64) and the stats/skills
        // frames of the login tail, each as its own output message.
        val loginFrame = readFrame(ch)
        assertThat(loginFrame.readUnsignedByte().toInt()).isEqualTo(GameHandler.PACKET_SELF_APPEAR)

        assertLoginTail(ch)

        val mapFrame = readFrame(ch)
        assertThat(mapFrame.readUnsignedByte().toInt()).isEqualTo(MapPacketWriter.PACKET_MAP_DESCRIPTION.toInt())

        val statsFrame = readFrame(ch)
        assertThat(statsFrame.readUnsignedByte().toInt()).isEqualTo(GameWindowWriter.PACKET_STATS)

        val skillsFrame = readFrame(ch)
        assertThat(skillsFrame.readUnsignedByte().toInt()).isEqualTo(GameWindowWriter.PACKET_SKILLS)

        // 2. `sendVIPGroups` (0xD4): one group, `maxGroupEntries` (0 for a
        // non-premium account) minus the group count as the trailing byte.
        val groupsFrame = readFrame(ch)
        assertThat(groupsFrame.readUnsignedByte().toInt()).isEqualTo(SocialWindowWriter.PACKET_VIP_GROUPS)
        assertThat(groupsFrame.readUnsignedByte().toInt()).isEqualTo(1)
        assertThat(groupsFrame.readUnsignedByte().toInt()).isEqualTo(1) // group id
        assertThat(groupsFrame.readTibiaString()).isEqualTo("Hunting")
        assertThat(groupsFrame.readUnsignedByte().toInt()).isEqualTo(1) // customizable
        assertThat(groupsFrame.readUnsignedByte().toInt()).isEqualTo(0xFF) // 0 - 1

        // 3. `sendVIP` (0xD2) for guid 2 (`Sorcerer Master`, offline).
        val entryFrame = readFrame(ch)
        assertThat(entryFrame.readUnsignedByte().toInt()).isEqualTo(SocialWindowWriter.PACKET_VIP)
        assertThat(entryFrame.readUnsignedIntLE()).isEqualTo(2L)
        assertThat(entryFrame.readTibiaString()).isEqualTo("Sorcerer Master")
        assertThat(entryFrame.readTibiaString()).isEqualTo("Best friend")
        assertThat(entryFrame.readUnsignedIntLE()).isEqualTo(3L) // icon
        assertThat(entryFrame.readUnsignedByte().toInt()).isEqualTo(1) // notify
        assertThat(entryFrame.readUnsignedByte().toInt()).isEqualTo(0) // Offline
        assertThat(entryFrame.readUnsignedByte().toInt()).isEqualTo(1) // group count
        assertThat(entryFrame.readUnsignedByte().toInt()).isEqualTo(1) // group id

        assertThat(ch.readOutbound<ByteBuf>()).isNull()
    }

    @Test
    fun `an online VIP resolves its live status in the burst`() {
        val game = Game(gameMap, dispatcher)
        val handler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            ioLoginData = loginData,
            game = game,
        )
        val ch = EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), handler)
        channel = ch

        // The listed player logs in first: `Game::addPlayer` registers guid 2,
        // so the burst resolves the live `PlayerVIP::getStatus`.
        val otherHandler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            ioLoginData = loginData,
            game = game,
        )
        val otherChannel = EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), otherHandler)
        otherChannel.writeInbound(loginPacket(characterName = "Sorcerer Master"))
        awaitLane()

        ch.writeInbound(loginPacket())
        awaitLane()

        val loginFrame = readFrame(ch)
        assertThat(loginFrame.readUnsignedByte().toInt()).isEqualTo(GameHandler.PACKET_SELF_APPEAR)

        assertLoginTail(ch)

        val mapFrame = readFrame(ch)
        assertThat(mapFrame.readUnsignedByte().toInt()).isEqualTo(MapPacketWriter.PACKET_MAP_DESCRIPTION.toInt())

        val statsFrame = readFrame(ch)
        assertThat(statsFrame.readUnsignedByte().toInt()).isEqualTo(GameWindowWriter.PACKET_STATS)

        val skillsFrame = readFrame(ch)
        assertThat(skillsFrame.readUnsignedByte().toInt()).isEqualTo(GameWindowWriter.PACKET_SKILLS)

        val groupsFrame = readFrame(ch)
        assertThat(groupsFrame.readUnsignedByte().toInt()).isEqualTo(SocialWindowWriter.PACKET_VIP_GROUPS)
        groupsFrame.skipBytes(groupsFrame.readableBytes())

        val entryFrame = readFrame(ch)
        assertThat(entryFrame.readUnsignedByte().toInt()).isEqualTo(SocialWindowWriter.PACKET_VIP)
        assertThat(entryFrame.readUnsignedIntLE()).isEqualTo(2L)
        assertThat(entryFrame.readTibiaString()).isEqualTo("Sorcerer Master")
        entryFrame.readTibiaString() // description
        entryFrame.readUnsignedIntLE() // icon
        entryFrame.readUnsignedByte() // notify
        assertThat(entryFrame.readUnsignedByte().toInt()).isEqualTo(1) // Online

        otherChannel.close()
    }

    @Test
    fun `a login announces the new session status on the online VIP lists`() {
        // `Player::vip()` (player.hpp:739) bound like the production
        // composition ([CanaryServer]/the handler's default `Game`).
        val game = Game(gameMap, dispatcher, playerVip = { player -> player.features.vip })
        val firstHandler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            ioLoginData = loginData,
            game = game,
        )
        val firstChannel = EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), firstHandler)
        firstChannel.writeInbound(loginPacket(characterName = "Sorcerer Master"))
        awaitLane()

        // The observer lists the later session, so `Player::addList`
        // (player.cpp:4519-4525) must announce it through the attached sink.
        val observer = game.getPlayerByID(2L)
        assertThat(observer).isNotNull
        observer!!.features.vip.addInternal(1L)

        val secondHandler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            ioLoginData = loginData,
            game = game,
        )
        val secondChannel = EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), secondHandler)
        channel = secondChannel
        secondChannel.writeInbound(loginPacket())
        awaitLane()

        var statusFrame: ByteBuf? = null
        while (true) {
            val frame = readFrameOrNull(firstChannel) ?: break
            if (frame.getUnsignedByte(frame.readerIndex()).toInt() == VipWindowWriter.PACKET_VIP_STATUS) {
                statusFrame = frame
                break
            }
        }

        assertThat(statusFrame).isNotNull
        assertThat(statusFrame!!.readUnsignedByte().toInt()).isEqualTo(VipWindowWriter.PACKET_VIP_STATUS)
        assertThat(statusFrame.readUnsignedIntLE()).isEqualTo(1L)
        assertThat(statusFrame.readUnsignedByte().toInt()).isEqualTo(1) // Online

        secondChannel.close()
        firstChannel.close()
    }

    /** Reads and decrypts one outbound frame, or `null` when the queue is empty. */
    private fun readFrameOrNull(ch: EmbeddedChannel): ByteBuf? {
        val frame = ch.readOutbound<ByteBuf>() ?: return null
        val length = frame.readUnsignedShortLE()
        assertThat(frame.readableBytes()).isEqualTo(length)
        frame.readUnsignedIntLE() // Adler32 checksum
        assertThat(xteaCipher.decrypt(frame, frame.readerIndex(), frame.readableBytes())).isTrue
        return frame
    }
}
