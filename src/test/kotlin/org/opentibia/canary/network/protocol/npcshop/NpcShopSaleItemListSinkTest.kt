package org.opentibia.canary.network.protocol.npcshop

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.protocol.CLIENT_VERSION
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.npcs.NpcInfo
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.GameHandler
import org.opentibia.canary.network.protocol.NettyProtocolSink
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for `Player::sendSaleItemList` -> `ProtocolGame::sendSaleItemList`
 * (src/creatures/players/player.cpp:2408, src/server/network/protocol/protocolgame.cpp:6669):
 * the shop-open sale-list refresh emits the two leading `sendResourceBalance`
 * frames (`RESOURCE_BANK` then the inventory money/custom currency) followed by
 * the `0x7B` entry list, each as its own output message.
 */
class NpcShopSaleItemListSinkTest {

    private var channel: EmbeddedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.finishAndReleaseAll()
    }

    private fun player(): Player = Player(
        id = 1L,
        name = "Merchant Customer",
        maxHealth = 150,
        health = 150,
        maxMana = 50,
        mana = 50,
        position = Position(100, 100, 7),
    )

    private fun npc(currency: Int = NpcInfo.ITEM_GOLD_COIN): Npc {
        val type = NpcType("Alesar")
        type.info.currencyId = currency
        return Npc(id = 9L, name = "Alesar", position = Position(101, 100, 7), npcType = type)
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun readFrame(channel: EmbeddedChannel): ByteArray {
        val frame = channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
        val bytes = ByteArray(frame.readableBytes())
        frame.readBytes(bytes)
        frame.release()
        return bytes
    }

    @Test
    fun `sendSaleItemList emits the bank money and 0x7B frames after shop open`() {
        val channel = EmbeddedChannel().also { this.channel = it }
        val player = player()
        player.features.vitals.setBankBalance(250L)
        val npc = npc()
        npc.addShopPlayer(player.getGUID(), listOf(ShopBlock(itemId = 2160, itemSellPrice = 5)))
        val sink = NettyProtocolSink(channel, player, GameMap(), shopOwner = { npc })

        sink.sendSaleItemList(mapOf(2160 to 3, NpcShopWindowSerializer.ITEM_GOLD_COIN to 7))

        assertThat(readFrame(channel)).isEqualTo(
            bytes(0xEE, 0x00, 250, 0, 0, 0, 0, 0, 0, 0),
        )
        assertThat(readFrame(channel)).isEqualTo(
            bytes(0xEE, 0x01, 7, 0, 0, 0, 0, 0, 0, 0),
        )
        assertThat(readFrame(channel)).isEqualTo(
            bytes(0x7B, 0x01, 0x00, 0x70, 0x08, 0x03, 0x00),
        )
        assertThat(channel.readOutbound<ByteBuf>()).isNull()
    }

    @Test
    fun `sendSaleItemList is silent without a shop owner`() {
        val channel = EmbeddedChannel().also { this.channel = it }
        val player = player()
        player.features.vitals.setBankBalance(250L)
        val sink = NettyProtocolSink(channel, player, GameMap())

        sink.sendSaleItemList(mapOf(2160 to 3))

        assertThat(channel.readOutbound<ByteBuf>()).isNull()
    }

    @Test
    fun `a custom shop currency writes the custom inventory balance`() {
        val channel = EmbeddedChannel().also { this.channel = it }
        val player = player()
        val npc = npc(currency = 3043)
        npc.addShopPlayer(player.getGUID(), listOf(ShopBlock(itemId = 2160, itemSellPrice = 5)))
        val sink = NettyProtocolSink(channel, player, GameMap(), shopOwner = { npc })

        sink.sendSaleItemList(mapOf(3043 to 9))

        assertThat(readFrame(channel)).isEqualTo(bytes(0xEE, 0x00, 0, 0, 0, 0, 0, 0, 0, 0))
        // `RESOURCE_INVENTORY_CURRENCY_CUSTOM` (0x02) on the current profile.
        assertThat(readFrame(channel)).isEqualTo(bytes(0xEE, 0x02, 9, 0, 0, 0, 0, 0, 0, 0))
        assertThat(readFrame(channel)).isEqualTo(bytes(0x7B, 0x00, 0x00))
    }

    @Test
    fun `gold currency sums crystal platinum and gold into the money frame`() {
        val channel = EmbeddedChannel().also { this.channel = it }
        val player = player()
        val npc = npc()
        npc.addShopPlayer(player.getGUID(), emptyList())
        val sink = NettyProtocolSink(channel, player, GameMap(), shopOwner = { npc })

        sink.sendSaleItemList(
            mapOf(
                NpcShopWindowSerializer.ITEM_CRYSTAL_COIN to 2,
                NpcShopWindowSerializer.ITEM_PLATINUM_COIN to 3,
                NpcShopWindowSerializer.ITEM_GOLD_COIN to 4,
            ),
        )

        readFrame(channel) // BANK frame
        // 2 * 10000 + 3 * 100 + 4 = 20304 (0x4F50)
        assertThat(readFrame(channel)).isEqualTo(bytes(0xEE, 0x01, 0x50, 0x4F, 0, 0, 0, 0, 0, 0))
        assertThat(readFrame(channel)).isEqualTo(bytes(0x7B, 0x00, 0x00))
    }
}

/**
 * The production [GameHandler] binding of `Player::updateState`'s shop tail
 * (src/creatures/players/player.cpp:6056-6064): after an inventory change with
 * an open shop window, `updateState` resolves the shop owner through
 * `Game.getShopOwner` and defers `Game::updatePlayerSaleItems`
 * (player.cpp:6046, 6051-6054) on the WorldCommit lane, so the session sink
 * emits the sale list.
 */
class GameHandlerShopSaleListRefreshTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? =
            Account(
                id = 1,
                name = name,
                passwordHash = password,
                characters = listOf(CharacterEntry("Merchant Customer")),
            )
    }

    private val rsaCipher = RsaCipher()
    private lateinit var dispatcher: GameDispatcher
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        channel?.finishAndReleaseAll()
        dispatcher.shutdown()
    }

    private fun rawLoginPacket(): ByteBuf {
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0)
        for (key in intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0)
        rsaBuf.writeTibiaString("1\n1")
        rsaBuf.writeTibiaString("Merchant Customer")

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
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    /**
     * Waits for the WorldCommit lane drain: `game.updateState` defers
     * `Game::updatePlayerSaleItems` on that lane (player.cpp:6046).
     */
    private fun awaitWorldCommit() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch(DispatcherLane.WorldCommit) { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun drainOutbound(channel: EmbeddedChannel) {
        channel.runPendingTasks()
        while (channel.readOutbound<ByteBuf>() != null) {
            // drain the login frames
        }
    }

    private fun login(gameMap: GameMap): Pair<GameHandler, EmbeddedChannel> {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val channel = EmbeddedChannel(handler).also { this.channel = it }
        channel.writeInbound(rawLoginPacket())
        awaitLane()
        drainOutbound(channel)
        return handler to channel
    }

    @Test
    fun `an inventory change with an open shop emits the deferred sale list`() {
        val gameMap = GameMap()
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        val (handler, channel) = login(gameMap)

        val player = requireNotNull(handler.game.getPlayerByID(1L))
        player.features.vitals.setBankBalance(80L)

        val npc = Npc(
            id = 9L,
            name = "Alesar",
            position = Position(1000, 1001, 7),
            npcType = NpcType("Alesar"),
        )
        npc.addShopPlayer(player.getGUID(), listOf(ShopBlock(itemId = 2160, itemSellPrice = 5)))
        handler.game.getShopOwner = { npc }

        // Inventory change: `Player::addItem` (0x78 inventory item frame)...
        assertThat(player.addItem(Item(id = 2160, count = 4))).isEqualTo(ReturnValue.NOERROR)
        channel.runPendingTasks()
        val inventoryFrame = channel.readOutbound<ByteBuf>()
        assertThat(inventoryFrame).isNotNull
        assertThat(inventoryFrame!!.readUnsignedByte().toInt()).isEqualTo(0x78)
        inventoryFrame.release()

        // ... then the `Player::updateState` shop tail defers
        // `Game::updatePlayerSaleItems` through the dispatcher.
        handler.game.updateState(player)
        awaitWorldCommit()

        channel.runPendingTasks()
        // `sendInventoryIds` (0xF5) and `sendStats` (0xA0) run inline.
        assertThat(channel.readOutbound<ByteBuf>()!!.readUnsignedByte().toInt()).isEqualTo(0xF5)
        assertThat(channel.readOutbound<ByteBuf>()!!.readUnsignedByte().toInt()).isEqualTo(0xA0)

        val bank = requireNotNull(channel.readOutbound<ByteBuf>())
        assertThat(bank.readUnsignedByte().toInt()).isEqualTo(0xEE)
        assertThat(bank.readUnsignedByte().toInt()).isEqualTo(0x00)
        assertThat(bank.readLongLE()).isEqualTo(80L)
        bank.release()

        val money = requireNotNull(channel.readOutbound<ByteBuf>())
        assertThat(money.readUnsignedByte().toInt()).isEqualTo(0xEE)
        assertThat(money.readUnsignedByte().toInt()).isEqualTo(0x01)
        assertThat(money.readLongLE()).isZero()
        money.release()

        val saleList = requireNotNull(channel.readOutbound<ByteBuf>())
        assertThat(saleList.readUnsignedByte().toInt()).isEqualTo(NpcShopWindowSerializer.PACKET_SALE_ITEM_LIST)
        assertThat(saleList.readUnsignedShortLE()).isEqualTo(1)
        assertThat(saleList.readUnsignedShortLE()).isEqualTo(2160)
        assertThat(saleList.readUnsignedShortLE()).isEqualTo(4)
        saleList.release()

        assertThat(channel.readOutbound<ByteBuf>()).isNull()
    }

    @Test
    fun `an inventory change without an open shop emits no sale list`() {
        val gameMap = GameMap()
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        val (handler, channel) = login(gameMap)

        val player = requireNotNull(handler.game.getPlayerByID(1L))
        assertThat(player.addItem(Item(id = 2160, count = 4))).isEqualTo(ReturnValue.NOERROR)
        handler.game.updateState(player)
        awaitWorldCommit()

        channel.runPendingTasks()
        assertThat(channel.readOutbound<ByteBuf>()!!.readUnsignedByte().toInt()).isEqualTo(0x78)
        assertThat(channel.readOutbound<ByteBuf>()!!.readUnsignedByte().toInt()).isEqualTo(0xF5)
        assertThat(channel.readOutbound<ByteBuf>()!!.readUnsignedByte().toInt()).isEqualTo(0xA0)
        assertThat(channel.readOutbound<ByteBuf>()).isNull()
    }
}

/**
 * The production [GameHandler] bindings of the `Game::updatePlayerSaleItems`
 * path (src/game/game.cpp:11596): `Game.sendSaleItemList` forwards the resolved
 * inventory map to the session sink, which resolves the recorded shop owner
 * through `Game.getShopOwner`.
 */
class GameHandlerShopSaleItemListTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? =
            Account(
                id = 1,
                name = name,
                passwordHash = password,
                characters = listOf(CharacterEntry("Merchant Customer")),
            )
    }

    private var channel: EmbeddedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.finishAndReleaseAll()
    }

    @Test
    fun `updatePlayerSaleItems sends the sale list through the session sink`() {
        val dispatcher = GameDispatcher()
        val handler = GameHandler(RsaCipher(), MockAccountRepo(), GameMap(), dispatcher)
        val player = Player(
            id = 1L,
            name = "Merchant Customer",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        )
        player.features.vitals.setBankBalance(80L)
        player.addItem(Item(id = 2160, count = 4))
        handler.game.addPlayer(player)

        val type = NpcType("Alesar")
        val npc = Npc(id = 9L, name = "Alesar", position = Position(101, 100, 7), npcType = type)
        npc.addShopPlayer(player.getGUID(), listOf(ShopBlock(itemId = 2160, itemSellPrice = 5)))
        handler.game.getShopOwner = { npc }

        val channel = EmbeddedChannel().also { this.channel = it }
        player.protocolSink = NettyProtocolSink(channel, player, handler.game.map, shopOwner = { target ->
            handler.game.getShopOwner(target)
        })

        handler.game.updatePlayerSaleItems(player.getGUID())

        val bank = channel.readOutbound<ByteBuf>()
        assertThat(bank.readUnsignedByte().toInt()).isEqualTo(0xEE)
        assertThat(bank.readUnsignedByte().toInt()).isEqualTo(0x00)
        assertThat(bank.readLongLE()).isEqualTo(80L)
        bank.release()

        val money = channel.readOutbound<ByteBuf>()
        assertThat(money.readUnsignedByte().toInt()).isEqualTo(0xEE)
        assertThat(money.readUnsignedByte().toInt()).isEqualTo(0x01)
        assertThat(money.readLongLE()).isZero()
        money.release()

        val saleList = channel.readOutbound<ByteBuf>()
        assertThat(saleList.readUnsignedByte().toInt()).isEqualTo(NpcShopWindowSerializer.PACKET_SALE_ITEM_LIST)
        assertThat(saleList.readUnsignedShortLE()).isEqualTo(1)
        assertThat(saleList.readUnsignedShortLE()).isEqualTo(2160)
        assertThat(saleList.readUnsignedShortLE()).isEqualTo(4)
        saleList.release()
    }
}
