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
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.vocations.Vocations
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
import org.opentibia.canary.database.PlayerItemState
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.iologindata.IOLoginData
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the login load wiring of [GameHandler]: the handshake
 * resolves the character through `IOLoginData::getGuidByName` and materializes
 * the persisted state with `IOLoginData::loadPlayerById(..., false)`
 * (src/io/iologindata.cpp:81-93, protocolgame.cpp:1023) into the session
 * player the handler registers in the world `Game` (`Game::addPlayer`,
 * src/game/game.cpp:11624).
 */
class GameHandlerLoginLoadTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? =
            if (name == "1" && password == "1") {
                Account(
                    id = 1,
                    name = "1",
                    passwordHash = "1",
                    characters = listOf(CharacterEntry("Login Knight")),
                )
            } else {
                null
            }
    }

    private val rsaCipher = RsaCipher()
    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private lateinit var vocations: Vocations
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_handler_load_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Login Knight")

        vocations = Vocations().apply { loadFromXml(VOCATIONS_XML) }
        val itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2376, "backpack", isContainer = true, capacity = 20, isMovable = true))
            register(ItemType(2160, "crystal coin", isStackable = true))
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
            itemTypes = itemTypes,
            vocations = vocations,
        )
        loginData.savePlayerItem(
            101L,
            listOf(
                PlayerItemState(pid = Slots_t.CONST_SLOT_BACKPACK.value, sid = 101, itemId = 2376),
                PlayerItemState(pid = 101, sid = 102, itemId = 2160, count = 50),
            ),
        )
        JdbcPlayerSpellsRepository(dataSource).save(101L, listOf("exura"))

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

    private fun insertPlayer(id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, level, vocation, health, healthmax, mana, manamax,
                    experience, posx, posy, posz, cap, sex, pronoun, town_id,
                    skill_sword, skill_sword_tries
                ) VALUES (?, ?, 1, 10, 4, 100, 100, 50, 50, 1, 1000, 1000, 7, 350, 1, 3, 1, 55, 1234)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
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
        rsaBuf.writeTibiaString("Login Knight") // character name

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

    @Test
    fun `login loads the persisted player state into the world session player`() {
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
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull

        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

        val sessionPlayer = handler.game.getPlayerByID(101L)
        assertThat(sessionPlayer).isNotNull
        assertThat(sessionPlayer!!.name).isEqualTo("Login Knight")
        assertThat(sessionPlayer.health).isEqualTo(100)
        assertThat(sessionPlayer.getBaseSkill(skills_t.SKILL_SWORD)).isEqualTo(55)
        assertThat(sessionPlayer.learnedInstantSpellList).containsExactly("exura")

        val backpack = sessionPlayer.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK)
        assertThat(backpack).isNotNull
        assertThat(backpack!!.id).isEqualTo(2376)
    }

    @Test
    fun `login without an injected loader keeps the stub session player`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), handler)
        channel = ch

        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull

        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

        val stubPlayer = handler.game.getPlayerByID(1L)
        assertThat(stubPlayer).isNotNull
        assertThat(stubPlayer!!.name).isEqualTo("Login Knight")
        assertThat(stubPlayer.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK)).isNull()
    }

    @Test
    fun `login binds the injected vocation and monster registries to the session player`() {
        val rat = MonsterType("Rat")
        val handler = GameHandler(
            rsaCipher,
            MockAccountRepo(),
            gameMap,
            dispatcher,
            ioLoginData = loginData,
            vocations = vocations,
            monsterTypeProvider = { name -> if (name.equals("Rat", ignoreCase = true)) rat else null },
        )
        val ch = EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), handler)
        channel = ch

        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull

        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

        val sessionPlayer = handler.game.getPlayerByID(101L)
        assertThat(sessionPlayer).isNotNull
        assertThat(sessionPlayer!!.vocations).isSameAs(vocations)
        assertThat(sessionPlayer.monsterTypeProvider("Rat")).isSameAs(rat)
    }

    private companion object {
        const val VOCATIONS_XML = """
            <vocations>
                <vocation id="4" name="Druid" clientid="4" baseid="4" description="A druid"
                          gaincap="5" gainhp="5" gainmana="30" gainhpticks="6" gainhpamount="1"
                          gainmanaticks="6" gainmanaamount="2" manamultiplier="1.8"
                          attackspeed="2000" basespeed="110" soulmax="200" gainsoulticks="120000"
                          fromvoc="0"/>
            </vocations>
        """
    }
}
