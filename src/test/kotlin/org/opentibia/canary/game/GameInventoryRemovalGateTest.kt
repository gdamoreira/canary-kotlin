package org.opentibia.canary.game

import io.netty.buffer.UnpooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.players.PlayerFrameSink
import org.opentibia.canary.creatures.players.queryRemove
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.items.containers.Inbox
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Removal-gate tests for `Game::internalRemoveItem` (src/game/game.cpp:2785-2845):
 * the ported `Player::queryRemove` (player.cpp:4865-4884) and `Item::canRemove`
 * (item.hpp:644) gates must reject an inventory removal exactly like the C++
 * `cylinder->queryRemove(...)`/`item->canRemove()` pair, with `force` bypassing
 * both.
 */
class GameInventoryRemovalGateTest {

    private val center = Position(1000, 1000, 7)

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "helmet", isMovable = true, weight = 100.0, slotPosition = SLOTP_HEAD))
        it.register(ItemType(id = 3031, name = "gold coin", isStackable = true, weight = 0.1))
        it.register(ItemType(id = 2000, name = "heavy statue", isMovable = false, weight = 0.0))
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

    private fun opcodes(frames: List<ByteArray>): List<Byte> = frames.map { it[0] }

    @Test
    fun `internalRemoveItem denies an inventory item whose canRemove is false`() {
        val harness = harness()
        val inbox = Inbox(id = 14142)
        assertThat(harness.player.addItem(inbox, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()

        val ret = harness.game.internalRemoveItem(inbox)

        assertThat(ret).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(inbox)
        assertThat(harness.frames).isEmpty()
    }

    @Test
    fun `internalRemoveItem force bypasses the canRemove gate`() {
        val harness = harness()
        val inbox = Inbox(id = 14142)
        assertThat(harness.player.addItem(inbox, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()

        val ret = harness.game.internalRemoveItem(inbox, force = true)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isNull()
    }

    @Test
    fun `internalRemoveItem denies a zero count like the queryRemove gate`() {
        val harness = harness()
        val coin = Item(3031, 1, harness.items.get(3031))
        assertThat(harness.player.addItem(coin, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()

        val ret = harness.game.internalRemoveItem(coin, count = 0)

        assertThat(ret).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(coin)
        assertThat(harness.frames).isEmpty()
    }

    @Test
    fun `internalRemoveItem denies a stack count above the held stack`() {
        val harness = harness()
        val coin = Item(3031, 2, harness.items.get(3031))
        assertThat(harness.player.addItem(coin, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()

        val ret = harness.game.internalRemoveItem(coin, count = 5)

        assertThat(ret).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(coin)
        assertThat(harness.frames).isEmpty()
    }

    @Test
    fun `a successful inventory removal still emits the state refresh`() {
        val harness = harness()
        val coin = Item(3031, 2, harness.items.get(3031))
        assertThat(harness.player.addItem(coin, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()

        val ret = harness.game.internalRemoveItem(coin, count = 2)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isNull()
        assertThat(opcodes(harness.frames)).containsExactly(0x79.toByte(), 0xF5.toByte(), 0xA0.toByte())
    }

    @Test
    fun `player queryRemove rejects a non-movable item without the ignore flag`() {
        val harness = harness()
        val statue = Item(2000, 1, harness.items.get(2000))
        assertThat(harness.player.addItem(statue, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)

        assertThat(harness.player.queryRemove(statue, 1, 0)).isEqualTo(ReturnValue.NOTMOVABLE)
        assertThat(
            harness.player.queryRemove(statue, 1, CylinderFlags.FLAG_IGNORENOTMOVABLE),
        ).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `player queryRemove rejects a thing outside the inventory`() {
        val harness = harness()
        val coin = Item(3031, 1, harness.items.get(3031))

        assertThat(harness.player.queryRemove(coin, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }
}
