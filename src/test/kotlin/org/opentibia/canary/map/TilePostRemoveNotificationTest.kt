package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.network.protocol.InMemoryProtocolSink
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Tests for `Tile::postRemoveNotification` (src/items/tile.cpp:1711-1736):
 * every multifloor player spectator of the tile receives
 * `Player::postRemoveNotification(..., LINK_NEAR)`, and a tile left with more
 * than eight things is refreshed through `Tile::onUpdateTile`
 * (src/items/tile.cpp:682-689).
 */
class TilePostRemoveNotificationTest {

    private val center = Position(1000, 1000, 7)
    private val neighbour = Position(1001, 1000, 7)

    private fun itemRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 1987, name = "backpack", isContainer = true, capacity = 8))
        it.register(ItemType(id = 2160, name = "helmet", isMovable = true, weight = 100.0))
    }

    private class Harness(
        val game: Game,
        val tile: Tile,
        val spectator: Player,
        val items: Items,
        val frames: MutableList<ByteArray>,
    )

    private fun harness(spectatorPosition: Position = center): Harness {
        Spectators.clearCache()
        val items = itemRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        game.map.getOrCreateTile(center).ground = Item(id = 100)
        game.map.getOrCreateTile(neighbour).ground = Item(id = 100)
        val spectator = Player(
            id = 1L,
            name = "Observer",
            maxHealth = 150,
            health = 100,
            position = spectatorPosition,
        )
        assertThat(game.internalPlaceCreature(spectator, spectatorPosition)).isTrue()
        val sink = InMemoryProtocolSink(spectator, game.map)
        spectator.protocolSink = sink
        return Harness(game, game.map.getOrCreateTile(center), spectator, items, sink.frames)
    }

    @Test
    fun `removing a container open in the spectator closes it through the tile notification`() {
        val harness = harness()
        val backpack = Container(1987, 8, harness.items.get(1987))
        assertThat(harness.game.internalAddItem(harness.tile, backpack)).isEqualTo(ReturnValue.NOERROR)
        harness.spectator.addContainer(3, backpack)
        harness.frames.clear()

        assertThat(harness.game.internalRemoveItem(backpack)).isEqualTo(ReturnValue.NOERROR)

        assertThat(harness.frames.map { it[0] }).containsExactly(0x6F.toByte())
    }

    @Test
    fun `a tile left with more than eight things refreshes it for the spectator`() {
        val harness = harness(spectatorPosition = neighbour)
        for (index in 1..9) {
            val kept = Item(2160, 1, harness.items.get(2160))
            assertThat(harness.game.internalAddItem(harness.tile, kept)).isEqualTo(ReturnValue.NOERROR)
        }
        val removed = Item(2160, 1, harness.items.get(2160))
        assertThat(harness.game.internalAddItem(harness.tile, removed)).isEqualTo(ReturnValue.NOERROR)
        harness.frames.clear()

        assertThat(harness.game.internalRemoveItem(removed)).isEqualTo(ReturnValue.NOERROR)

        // ground + 9 remaining items = 10 things > 8 -> Tile::onUpdateTile
        assertThat(harness.frames.map { it[0] }).containsExactly(0x69.toByte())
    }
}
