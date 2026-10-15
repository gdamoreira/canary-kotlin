package org.opentibia.canary.network.protocol

import com.zaxxer.hikari.HikariDataSource
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
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.IOMarket
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
import org.opentibia.canary.systems.imbuement.ImbuementItem
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * Production-composition tests for [GameHandler]: the handler must build its
 * `Game` and `GamePacketContext` with the injected item registry, `IOMarket`
 * and imbuement registry, and expose the market escrow
 * (`IOMarket::processExpiredOffers`, iomarket.cpp:155-232) over the live
 * `Game` so expired offers reach the player inbox / bank balance.
 *
 * The imbuement path drives `Game::playerApplyImbuement` (game.cpp:7116)
 * through an [org.opentibia.canary.systems.imbuement.ImbuementEngine] composed
 * over the real [Item] custom-attribute store.
 */
class GameHandlerRuntimeCompositionTest {

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
    private lateinit var ioMarket: IOMarket
    private lateinit var items: Items
    private lateinit var imbuements: Imbuements
    private lateinit var handler: GameHandler
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_handler_compose_${System.nanoTime()}")
        assertThat(DatabaseConnectionFactory.migrate(dataSource)).isGreaterThanOrEqualTo(10)
        ioMarket = IOMarket(dataSource = dataSource)
        dispatcher = GameDispatcher().apply { start() }
        gameMap = GameMap()
        // `ProtocolGame::login` places the session player (protocolgame.cpp:1056):
        // the stub login position needs a tile for the placement to succeed.
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        items = marketItems()
        imbuements = Imbuements().apply { assertThat(loadFromXml(IMBUEMENT_XML)).isTrue() }
        handler = GameHandler(
            rsaCipher = rsaCipher,
            accountRepository = MockAccountRepo(),
            gameMap = gameMap,
            dispatcher = dispatcher,
            itemTypes = items,
            ioMarket = ioMarket,
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
        EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), handler).also { channel = it }

    private fun login(ch: EmbeddedChannel): Player {
        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull
        awaitLane()
        return handler.game.getPlayerByID(1L)!!
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
    fun `browse packet reaches the injected IOMarket through the handler Game`() {
        val ch = openChannel()
        val player = login(ch)
        player.setInMarket(true)
        val browsed = mutableListOf<Int>()
        val detailed = mutableListOf<Int>()
        val applied = CountDownLatch(1)
        handler.game.sendMarketBrowseItem = { _, itemId, _, _, _ -> browsed += itemId }
        handler.game.sendMarketDetail = { _, itemId, _ ->
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

    @Test
    fun `create offer packet persists through the context IOMarket`() {
        val ch = openChannel()
        val player = login(ch)
        player.setInMarket(true)
        handler.game.getBankBalance = { 100_000L }
        handler.game.setBankBalance = { _, _ -> }
        handler.game.removeMoney = { _, _, _, _ -> true }
        val applied = CountDownLatch(1)
        handler.game.updateUIExhausted = { applied.countDown() }

        ch.writeInbound(
            encryptedGamePacket { frame ->
                frame.writeByte(MarketPacketParser.OPCODE_MARKET_CREATE_OFFER)
                frame.writeByte(MarketOfferType.Buy.ordinal)
                frame.writeShortLE(2160)
                frame.writeShortLE(5)
                frame.writeLongLE(1000)
                frame.writeByte(0)
            },
        )

        assertThat(applied.await(2, TimeUnit.SECONDS)).isTrue()
        val offers = ioMarket.getOwnOffers(MarketOfferType.Buy, player.getGUID())
        assertThat(offers).hasSize(1)
        assertThat(offers[0].itemId).isEqualTo(2160)
        assertThat(offers[0].amount).isEqualTo(5)
        assertThat(offers[0].price).isEqualTo(1000)
    }

    @Test
    fun `market escrow delivers items into the player inbox and credits the bank`() {
        val player = Player(
            id = 7L,
            name = "Seller",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        handler.game.addPlayer(player)
        var balance = 0L
        handler.game.getBankBalance = { balance }
        handler.game.setBankBalance = { _, value -> balance = value }

        val escrow = handler.marketEscrow
        assertThat(escrow.getItemType(2160)?.stackable).isTrue()
        assertThat(escrow.getItemType(9999)).isNull()

        val handle = escrow.resolvePlayer(player.getGUID(), false)
        assertThat(handle).isNotNull

        assertThat(escrow.addItemToInbox(handle!!, 2160, 7, 0)).isTrue()
        assertThat(player.inbox.items).hasSize(1)
        assertThat(player.inbox.items[0].id).isEqualTo(2160)
        assertThat(player.inbox.items[0].getItemCount()).isEqualTo(7)

        escrow.addBankBalance(handle, 500)
        assertThat(balance).isEqualTo(500)
    }

    @Test
    fun `login hands the player async queue back through the server dispatcher`() {
        val ch = openChannel()
        val player = login(ch)
        val callerThread = Thread.currentThread()
        var ranOn: Thread? = null
        val applied = CountDownLatch(1)

        player.addAsyncTask {
            ranOn = Thread.currentThread()
            applied.countDown()
        }
        awaitLane()

        assertThat(applied.await(2, TimeUnit.SECONDS)).isTrue()
        assertThat(ranOn).isNotNull
        assertThat(ranOn).isNotSameAs(callerThread)
    }

    @Test
    fun `apply imbuement writes the real item store and consumes the unified stash`() {
        val player = Player(
            id = 8L,
            name = "Imbuer",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        handler.game.addPlayer(player)
        val item = Item(id = 2400, type = items.get(2400))
        player.setImbuingItem(item)
        handler.game.getImbuingItem = { item }
        handler.game.removeMoney = { _, _, _, _ -> true }
        player.stash.addItemOnStash(11444, 20)

        val strike = imbuements.imbuementMap.values.single { it.name == "Strike" && it.baseId == 2 }
        handler.game.playerApplyImbuement(player.id, strike.id, 0)

        assertThat(item.hasImbuementAttribute("500")).isTrue()
        assertThat(item.getCustomAttribute("500")?.getInteger()).isEqualTo((72000L shl 8) or strike.id.toLong())
        assertThat(player.stash.getStashItemCount(11444)).isZero()
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
