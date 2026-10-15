package org.opentibia.canary.game

import io.netty.buffer.UnpooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.players.PlayerFrameSink
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.CylinderLink
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.items.Thing
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Source-cylinder notification tests for the player-cylinder moves
 * (`Game::internalMoveItem`, src/game/game.cpp:2659-2671):
 *
 *  - `Game::moveItemToPlayer`'s container source fires
 *    `fromCylinder->postRemoveNotification(item, toCylinder, itemIndex)`, so an
 *    equip out of a held backpack refreshes the player state through the
 *    `Container` -> `Player::postRemoveNotification(LINK_TOPPARENT)` route.
 *  - `Game::internalMoveItemToPlayer`'s tile source fires the same notification
 *    for the trade transfer.
 *
 * The Kotlin `Player` is not a `Cylinder`, so the container -> player route is
 * carried by the `Item::getHoldingPlayer` link the world records when a
 * container enters the player inventory.
 */
class GameSourceCylinderNotificationTest {

    private val center = Position(1000, 1000, 7)

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "helmet", isMovable = true, weight = 100.0, slotPosition = SLOTP_HEAD))
        it.register(ItemType(id = 1987, name = "backpack", isContainer = true, capacity = 8))
        it.register(ItemType(id = 3031, name = "gold coin", isStackable = true, weight = 0.1))
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

    /** A tile that records the source post-remove notifications it receives. */
    private class RecordingTile(position: Position) : Tile(position) {
        val removals = mutableListOf<Pair<Item, Int>>()

        override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink) {
            thing?.getItem()?.let { removals.add(it to index) }
            super.postRemoveNotification(thing, newParent, index, link)
        }
    }

    @Test
    fun `equipping from a held backpack emits the source container refresh`() {
        val harness = harness()
        val backpack = Container(1987, 8, harness.items.get(1987))
        assertThat(
            harness.game.internalPlayerAddItem(harness.player, backpack, slot = Slots_t.CONST_SLOT_BACKPACK),
        ).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK)).isSameAs(backpack)
        val helmet = Item(2160, 1, harness.items.get(2160))
        backpack.addThing(helmet)
        harness.frames.clear()

        harness.game.playerEquipItem(harness.player.id, 2160)

        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(helmet)
        // The destination inventory frame (0x78) from `Player::addThing`, then the
        // source `Player::postRemoveNotification` state refresh (0xF5/0xA0) and
        // the destination `postAddNotification` refresh (0xF5/0xA0).
        assertThat(opcodes(harness.frames)).containsExactly(
            0x78.toByte(),
            0xF5.toByte(),
            0xA0.toByte(),
            0xF5.toByte(),
            0xA0.toByte(),
        )
    }

    @Test
    fun `equipping from a container without a holding player keeps the destination-only refresh`() {
        val harness = harness()
        val backpack = Container(1987, 8, harness.items.get(1987))
        // Added through the raw domain path, so the world never records the
        // holding-player link (the port's `Player::internalAddThing` PARITY GAP).
        assertThat(harness.player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)
        val helmet = Item(2160, 1, harness.items.get(2160))
        backpack.addThing(helmet)
        harness.frames.clear()

        harness.game.playerEquipItem(harness.player.id, 2160)

        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(helmet)
        assertThat(opcodes(harness.frames)).containsExactly(0x78.toByte(), 0xF5.toByte(), 0xA0.toByte())
    }

    @Test
    fun `the trade transfer notifies the source tile`() {
        val harness = harness()
        val partner = Player(
            id = 2L,
            name = "Druid",
            maxHealth = 150,
            health = 100,
            position = Position(1001, 1000, 7),
        )
        harness.game.addPlayer(partner)
        harness.game.canThrowObjectTo = { _, _ -> true }

        val sourceTile = RecordingTile(center)
        sourceTile.ground = Item(id = 100)
        val offered = Item(3031, 3, harness.items.get(3031))
        assertThat(harness.game.internalAddItem(sourceTile, offered)).isEqualTo(ReturnValue.NOERROR)

        val partnerTile = RecordingTile(partner.position)
        partnerTile.ground = Item(id = 100)
        val counter = Item(3031, 2, harness.items.get(3031))
        assertThat(harness.game.internalAddItem(partnerTile, counter)).isEqualTo(ReturnValue.NOERROR)

        assertThat(harness.game.internalStartTrade(harness.player, partner, offered)).isTrue()
        assertThat(harness.game.internalStartTrade(partner, harness.player, counter)).isTrue()

        harness.game.playerAcceptTrade(harness.player.id)
        harness.game.playerAcceptTrade(partner.id)

        assertThat(partner.getItemCount(3031)).isEqualTo(3)
        assertThat(harness.player.getItemCount(3031)).isEqualTo(2)
        assertThat(sourceTile.removals.map { it.first }).containsExactly(offered)
        assertThat(partnerTile.removals.map { it.first }).containsExactly(counter)
    }
}
