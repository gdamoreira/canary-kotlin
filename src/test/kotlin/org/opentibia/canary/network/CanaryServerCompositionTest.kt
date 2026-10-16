package org.opentibia.canary.network

import com.zaxxer.hikari.HikariDataSource
import org.opentibia.canary.network.protocol.CLIENT_VERSION
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.PlayerTask
import org.opentibia.canary.creatures.players.WalkStartPolicy
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.MarketConfig
import org.opentibia.canary.io.marketTimeNow
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.market.MarketPacketParser
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * Production-composition tests for [CanaryServer]: the booted `DataSource` and
 * item/imbuement registries must reach every [org.opentibia.canary.network.protocol.GameHandler]
 * through the server-owned `g_game()` (`Game`), including the `IOMarket::getInstance()`
 * equivalent built over the escrow and the `Game::playerApplyImbuement` engine, and the
 * `Player` walk/imbuing seams must be bound to the host methods.
 *
 * The market/imbuement transcripts follow `IOMarket::processExpiredOffers`
 * (iomarket.cpp:155-232) and `Game::playerApplyImbuement` (game.cpp:7116).
 */
class CanaryServerCompositionTest {

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

    private lateinit var dataSource: HikariDataSource
    private lateinit var dispatcher: GameDispatcher
    private lateinit var gameMap: GameMap
    private lateinit var ioItems: Items
    private lateinit var imbuements: Imbuements
    private lateinit var server: CanaryServer
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_server_compose_${System.nanoTime()}")
        assertThat(DatabaseConnectionFactory.migrate(dataSource)).isGreaterThanOrEqualTo(10)
        dispatcher = GameDispatcher().apply { start() }
        gameMap = GameMap()
        // `ProtocolGame::login` places the session player (protocolgame.cpp:1056):
        // the stub login position needs a tile for the placement to succeed.
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        ioItems = marketItems()
        imbuements = Imbuements().apply { assertThat(loadFromXml(IMBUEMENT_XML)).isTrue() }
        server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            accountRepository = MockAccountRepo(),
            gameMap = gameMap,
            dispatcher = dispatcher,
            dataSource = dataSource,
            itemTypes = ioItems,
            imbuements = imbuements,
        )
    }

    @AfterEach
    fun tearDown() {
        channel?.close()
        dispatcher.shutdown()
        dataSource.close()
    }

    private fun marketItems(): Items = Items().also { registry ->
        registry.register(
            ItemType(
                id = 2160,
                name = "market item",
                wareId = 2160,
                isStackable = true,
                weight = 1.0,
            ),
        )
        registry.register(
            ItemType(
                id = 2400,
                name = "imbued sword",
                imbuementSlot = 2,
                imbuementTypes = linkedMapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3),
            ),
        )
    }

    private fun openChannel(): EmbeddedChannel =
        EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), server.createGameHandler())
            .also { channel = it }

    private fun login(ch: EmbeddedChannel): Player {
        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull
        awaitLane()
        return server.game.getPlayerByID(1L)!!
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun loginPacket(): ByteBuf {
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

        val body = Unpooled.buffer()
        body.writeShortLE(2)
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
    fun `booted registries and data source compose the server handler`() {
        assertThat(server.ioMarket).isNotNull
        val handler = server.createGameHandler()

        assertThat(handler.game).isSameAs(server.game)
        assertThat(handler.marketEscrow.getItemType(2160)?.stackable).isTrue()
        assertThat(handler.marketEscrow.getItemType(9999)).isNull()
    }

    @Test
    fun `market browse reaches the composed server IOMarket through the handler`() {
        val ch = openChannel()
        val player = login(ch)
        player.setInMarket(true)
        val browsed = mutableListOf<Int>()
        val detailed = mutableListOf<Int>()
        val applied = CountDownLatch(1)
        server.game.sendMarketBrowseItem = { _, itemId, _, _, _ -> browsed += itemId }
        server.game.sendMarketDetail = { _, itemId, _ ->
            detailed += itemId
            applied.countDown()
        }

        ch.writeInbound(
            encryptedGamePacket { frame ->
                frame.writeByte(MarketPacketParser.OPCODE_MARKET_BROWSE)
                frame.writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
                frame.writeShortLE(2160)
            },
        )

        assertThat(applied.await(2, TimeUnit.SECONDS)).isTrue()
        assertThat(browsed).containsExactly(2160)
        assertThat(detailed).containsExactly(2160)
    }

    /**
     * `Game::playerMove` -> `Creature::startAutoWalk` -> `Creature::onCreatureWalk`
     * (game.cpp:4282, creature.cpp:219-289/342-383): the walk runs through
     * `Map::moveCreature` so the tiles/sectors update and the spectator
     * `sendCreatureMove` (0x6D) confirmation reaches the client. The legacy
     * direct-position path left the map untouched and never confirmed, so the
     * client reverted every step.
     */
    @Test
    fun `walk packet moves the player through the map and confirms with sendCreatureMove`() {
        val ch = openChannel()
        val player = login(ch)
        gameMap.getOrCreateTile(Position(1001, 1000, 7)).ground = Item(id = 106)

        ch.writeInbound(encryptedGamePacket { it.writeByte(0x66) })

        assertThat(awaitCondition(2000) { player.position == Position(1001, 1000, 7) }).isTrue()
        awaitLane()

        assertThat(gameMap.getTile(Position(1001, 1000, 7))!!.getCreatures()).contains(player)
        assertThat(gameMap.getTile(Position(1000, 1000, 7))!!.getCreatures()).doesNotContain(player)

        assertThat(drainOutboundOpcodes(ch)).contains(0x6D)
    }

    private fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            Thread.sleep(10)
        }
        return condition()
    }

    /** Decrypts every queued outbound frame and returns the first (opcode) byte of each. */
    private fun drainOutboundOpcodes(ch: EmbeddedChannel): List<Int> {
        val opcodes = mutableListOf<Int>()
        while (true) {
            val frame = ch.readOutbound<ByteBuf>() ?: break
            frame.readUnsignedShortLE() // len
            frame.readUnsignedIntLE() // checksum
            xteaCipher.decrypt(frame, frame.readerIndex(), frame.readableBytes())
            opcodes += frame.readUnsignedByte().toInt()
            frame.release()
        }
        return opcodes
    }

    @Test
    fun `imbuement application reaches the composed engine and the player accessor`() {
        server.createGameHandler()
        val player = Player(
            id = 8L,
            name = "Imbuer",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        server.game.addPlayer(player)
        val item = Item(id = 2400, type = ioItems.get(2400))
        player.setImbuingItem(item)
        server.game.removeMoney = { _, _, _, _ -> true }
        player.stash.addItemOnStash(11444, 20)

        val strike = imbuements.imbuementMap.values.single { it.name == "Strike" && it.baseId == 2 }
        server.game.playerApplyImbuement(player.id, strike.id, 0)

        assertThat(player.getImbuingItem()).isSameAs(item)
        assertThat(server.game.getImbuingItem(player)).isSameAs(item)
        assertThat(item.hasImbuementAttribute("500")).isTrue()
        assertThat(player.stash.getStashItemCount(11444)).isZero()
    }

    @Test
    fun `game walk seams call the host player methods`() {
        server.createGameHandler()
        val player = Player(
            id = 9L,
            name = "Walker",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )

        var followRequests = 0
        player.creatureGoToFollowCreature = { followRequests++ }
        server.game.updateCreatureWalk(player)
        assertThat(followRequests).isEqualTo(1)

        var walkPolicy: WalkStartPolicy? = null
        player.addEventWalkProvider = { walkPolicy = it }
        server.game.startAutoWalk(player, listOf(Direction.North), true, WalkStartPolicy.RespectDelay)
        assertThat(player.listWalkDir).containsExactly(Direction.North)
        assertThat(walkPolicy).isEqualTo(WalkStartPolicy.RespectDelay)

        var scheduledLane: DispatcherLane? = null
        var scheduledDelay = 0L
        var scheduledContext: String? = null
        player.scheduleEvent = { lane, delayMs, context, _ ->
            scheduledLane = lane
            scheduledDelay = delayMs
            scheduledContext = context
            1L
        }
        server.game.setNextWalkTask(player, PlayerTask(150L, "composition test") {})
        assertThat(scheduledLane).isEqualTo(DispatcherLane.PlayerWalk)
        assertThat(scheduledDelay).isEqualTo(150L)
        assertThat(scheduledContext).isEqualTo("composition test")
    }

    @Test
    fun `startup escrow delivers expired offers through the composed server market`() {
        val seller = Player(
            id = 555L,
            name = "Seller",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        server.game.addPlayer(seller)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO accounts (id, name, \"password\", email, premdays, \"type\") " +
                    "VALUES (55, '55', '1', 'seller@opentibia.com', 0, 1)",
            ).use { it.executeUpdate() }
            connection.prepareStatement(
                "INSERT INTO players (id, name, account_id, balance) VALUES (555, 'Seller', 55, 0)",
            ).use { it.executeUpdate() }
        }
        val created = marketTimeNow() - MarketConfig().marketOfferDuration - 10
        DatabaseConnectionFactory.executeQuery(
            dataSource,
            "INSERT INTO market_offers (player_id, sale, itemtype, amount, created, anonymous, price, tier) " +
                "VALUES (555, ${MarketOfferType.Sell.ordinal}, 2160, 3, $created, 0, 100, 0)",
        )

        server.start()
        try {
            awaitLane()
            assertThat(seller.inbox.items).hasSize(1)
            assertThat(seller.inbox.items[0].id).isEqualTo(2160)
            assertThat(seller.inbox.items[0].getItemCount()).isEqualTo(3)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `getImbuingItem accessor mirrors the C++ public field`() {
        val player = Player(
            id = 10L,
            name = "Holder",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        assertThat(player.getImbuingItem()).isNull()

        val item = Item(id = 2400, type = ioItems.get(2400))
        player.setImbuingItem(item)
        assertThat(player.getImbuingItem()).isSameAs(item)

        player.setImbuingItem(null)
        assertThat(player.getImbuingItem()).isNull()
    }
}

private const val IMBUEMENT_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <base id="2" name="Intricate" price="30000" protectionPrice="30000" percent="70" removecost="15000" duration="72000"/>
    <base id="3" name="Powerful" price="200000" protectionPrice="50000" percent="50" removecost="15000" duration="72000"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="2" category="3" iconid="1" premium="1" storage="0">
        <attribute key="effect" type="skill" value="critical" bonus="2500" chance="1000"/>
        <attribute key="item" value="11444" count="20"/>
        <attribute key="scroll" value="51742"/>
    </imbuement>
</imbuements>
"""
