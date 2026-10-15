package org.opentibia.canary.game

import io.netty.buffer.UnpooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.players.PlayerFrameSink
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * State-change wiring tests for the player-cylinder notification producers
 * (src/creatures/players/player.cpp:8664-8760) reached through the ported
 * `Game::internalAddItem`/`internalRemoveItem`/`internalMoveItem` player
 * branches (src/game/game.cpp:2693-3003, 2436):
 *
 *  - `Player::addItem` via `Game::internalPlayerAddItem` -> `Game::internalAddItem`
 *    -> `Player::postAddNotification` (game.cpp:2767-2785) emits the
 *    `sendInventoryIds` (0xF5) and `sendStats` (0xA0) refresh.
 *  - `Game::internalRemoveItem` -> `Player::postRemoveNotification`
 *    (game.cpp:2836) emits the same refresh.
 *  - `Game::playerEquipItem` -> `moveItemToPlayer` ->
 *    `Player::postAddNotification` (game.cpp:2666).
 *  - The shop tail reads `Game::getShopOwner` and defers through
 *    `scheduleSaleUpdate` (`Player::updateSaleShopList`, player.cpp:6051-6054).
 */
class GameInventoryNotificationTest {

    private val center = Position(1000, 1000, 7)

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "helmet", isMovable = true, weight = 100.0, slotPosition = SLOTP_HEAD))
        it.register(ItemType(id = 1987, name = "backpack", isContainer = true, capacity = 8))
    }

    private class Harness(
        val game: Game,
        val player: Player,
        val items: Items,
        val frames: MutableList<ByteArray>,
    )

    private fun harness(): Harness {
        val items = itemsRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val player = Player(
            id = 1L,
            name = "Hero",
            maxHealth = 150,
            health = 100,
            position = center,
        )
        game.addPlayer(player)
        val frames = mutableListOf<ByteArray>()
        player.protocolSink = PlayerFrameSink(
            player = player,
            map = game.map,
            frameAllocatorProvider = { UnpooledByteBufAllocator.DEFAULT },
            frameWriter = { frame ->
                val bytes = ByteArray(frame.readableBytes())
                frame.readBytes(bytes)
                frame.release()
                frames.add(bytes)
            },
        )
        return Harness(game, player, items, frames)
    }

    private fun item(harness: Harness, id: Int = 2160, count: Int = 1): Item =
        Item(id = id, count = count, type = harness.items.get(id))

    private fun opcodes(frames: List<ByteArray>): List<Byte> = frames.map { it[0] }

    private fun npc(): Npc = Npc(id = 9L, name = "Alesar", position = Position(1001, 1000, 7))

    // --- internalPlayerAddItem -> postAddNotification -----------------------------

    @Test
    fun `internalPlayerAddItem emits the inventory state update`() {
        val harness = harness()
        val scheduled = mutableListOf<Player>()
        harness.game.scheduleSaleUpdate = { scheduled.add(it) }
        harness.game.getShopOwner = { npc() }

        val ret = harness.game.internalPlayerAddItem(harness.player, item(harness))

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(opcodes(harness.frames)).containsExactly(0x78.toByte(), 0xF5.toByte(), 0xA0.toByte())
        assertThat(scheduled).containsExactly(harness.player)
        assertThat(harness.game.getScheduledSaleUpdate(harness.player)).isTrue()
    }

    @Test
    fun `internalPlayerAddItem without a shop owner skips the sale schedule`() {
        val harness = harness()
        val scheduled = mutableListOf<Player>()
        harness.game.scheduleSaleUpdate = { scheduled.add(it) }

        val ret = harness.game.internalPlayerAddItem(harness.player, item(harness))

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(opcodes(harness.frames)).containsExactly(0x78.toByte(), 0xF5.toByte(), 0xA0.toByte())
        assertThat(scheduled).isEmpty()
        assertThat(harness.game.getScheduledSaleUpdate(harness.player)).isFalse()
    }

    // --- internalRemoveItem -> postRemoveNotification -----------------------------

    @Test
    fun `internalRemoveItem removes an inventory item and emits the state update`() {
        val harness = harness()
        val item = item(harness)
        assertThat(harness.player.addItem(item, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()

        val ret = harness.game.internalRemoveItem(item)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isNull()
        // `Player::removeItem` sends the removal inventory frame (0x79), then
        // `postRemoveNotification` refreshes the ids and stats.
        assertThat(opcodes(harness.frames)).containsExactly(0x79.toByte(), 0xF5.toByte(), 0xA0.toByte())
    }

    @Test
    fun `internalRemoveItem keeps an already scheduled sale update`() {
        val harness = harness()
        val item = item(harness)
        assertThat(harness.player.addItem(item, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()
        harness.game.getShopOwner = { npc() }
        harness.game.setScheduledSaleUpdate(harness.player, true)
        val scheduled = mutableListOf<Player>()
        harness.game.scheduleSaleUpdate = { scheduled.add(it) }

        val ret = harness.game.internalRemoveItem(item)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(scheduled).isEmpty()
        assertThat(harness.game.getScheduledSaleUpdate(harness.player)).isTrue()
    }

    @Test
    fun `internalRemoveItem with test true keeps the inventory item`() {
        val harness = harness()
        val item = item(harness)
        assertThat(harness.player.addItem(item, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()

        val ret = harness.game.internalRemoveItem(item, test = true)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(item)
        assertThat(harness.frames).isEmpty()
    }

    @Test
    fun `internalRemoveItem rejects an item in no inventory and on no tile`() {
        val harness = harness()

        assertThat(harness.game.internalRemoveItem(item(harness))).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(harness.frames).isEmpty()
    }

    // --- playerEquipItem -> moveItemToPlayer -> postAddNotification ---------------

    @Test
    fun `playerEquipItem emits the state update when the item leaves the backpack`() {
        val harness = harness()
        val bagType = harness.items.get(1987)
        val backpack = Container(1987, 8, bagType)
        assertThat(harness.player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)
        val helmet = item(harness)
        backpack.addThing(helmet)
        harness.frames.clear()

        harness.game.playerEquipItem(harness.player.id, 2160)

        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(helmet)
        assertThat(opcodes(harness.frames)).containsExactly(0x78.toByte(), 0xF5.toByte(), 0xA0.toByte())
    }
}
