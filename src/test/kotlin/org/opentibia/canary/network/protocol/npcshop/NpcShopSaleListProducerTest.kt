package org.opentibia.canary.network.protocol.npcshop

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
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.creatures.players.getAllSaleItemIdAndCount
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.GameHandler
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The full inventory-change producer chain with an open shop
 * (src/creatures/players/player.cpp:8664-8703, src/game/game.cpp:2693-2785):
 * `Game::internalPlayerAddItem` -> `Game::internalAddItem` ->
 * `Player::postAddNotification` -> `Player::updateState`'s refresh block ->
 * `Player::updateSaleShopList` (player.cpp:6051-6054) ->
 * `Game::updatePlayerSaleItems` (game.cpp:11596) -> the session sink emits the
 * sale list, with no manual `game.updateState` call.
 */
class NpcShopSaleListProducerTest {

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
        body.writeShortLE(1098)
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
    fun `a produced inventory change with an open shop refreshes the sale list`() {
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

        // The producer: `Game::internalPlayerAddItem` -> `Game::internalAddItem`
        // -> `Player::postAddNotification` (no manual `updateState`). The item
        // carries a type so `Item::isPickupable` admits the inventory branch
        // (a stackable type keeps the requested stack count).
        val produced = handler.game.internalPlayerAddItem(
            player,
            Item(
                id = 2160,
                count = 4,
                type = ItemType(id = 2160, name = "magic sword", isMovable = true, isStackable = true, stackSize = 10),
            ),
        )
        assertThat(produced)
            .isEqualTo(ReturnValue.NOERROR)

        channel.runPendingTasks()
        // `Player::addItem` (0x78) then the notification refresh (0xF5, 0xA0).
        assertThat(channel.readOutbound<ByteBuf>()!!.readUnsignedByte().toInt()).isEqualTo(0x78)
        assertThat(channel.readOutbound<ByteBuf>()!!.readUnsignedByte().toInt()).isEqualTo(0xF5)
        assertThat(channel.readOutbound<ByteBuf>()!!.readUnsignedByte().toInt()).isEqualTo(0xA0)

        awaitWorldCommit()

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
        // `Game::updatePlayerSaleItems` clears the scheduled flag (game.cpp:11601).
        assertThat(handler.game.getScheduledSaleUpdate(player)).isFalse()
    }
}
