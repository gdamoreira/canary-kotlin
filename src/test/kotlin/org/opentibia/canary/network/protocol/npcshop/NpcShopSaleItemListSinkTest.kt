package org.opentibia.canary.network.protocol.npcshop

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.npcs.NpcInfo
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.protocol.GameHandler
import org.opentibia.canary.network.protocol.NettyProtocolSink

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
