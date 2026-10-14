package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.persistence.InMemoryPlayerStorage
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the `Game` facade, mirroring `Game::internalPlaceCreature`,
 * `Game::removeCreature`, `Game::internalAddItem`, `Game::internalRemoveItem`,
 * `Game::internalMoveItem`, `Game::getPlayers` and `Game::getPlayerByName`.
 */
class GameTest {

    private val groundId = 100
    private val stoneId = 1285
    private val goldCoinId = 3031
    private val platinumCoinId = 3035

    private val centerPosition = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.groundTile(position: Position = centerPosition): Tile =
        map.getOrCreateTile(position).also { it.ground = Item(id = groundId) }

    private fun player(id: Long = 1, name: String = "Knight", position: Position = centerPosition): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun gold(count: Int): Item =
        Item(id = goldCoinId, count = count, type = ItemType(id = goldCoinId, name = "gold coin", isStackable = true))

    private fun platinum(count: Int): Item =
        Item(
            id = platinumCoinId,
            count = count,
            type = ItemType(id = platinumCoinId, name = "platinum coin", isStackable = true)
        )

    // ----- internalPlaceCreature / removeCreature -----

    @Test
    fun `internalPlaceCreature places a creature on an existing ground tile`() {
        val game = newGame()
        val tile = game.groundTile()
        val creature = player()

        assertThat(game.internalPlaceCreature(creature, centerPosition)).isTrue()

        assertThat(creature.position).isEqualTo(centerPosition)
        assertThat(creature.isRemoved()).isFalse()
        assertThat(tile.position).isEqualTo(centerPosition)
    }

    @Test
    fun `internalPlaceCreature rejects a missing tile`() {
        val game = newGame()

        assertThat(game.internalPlaceCreature(player(), Position(9999, 9999, 7))).isFalse()
    }

    @Test
    fun `internalPlaceCreature rejects a tile without ground`() {
        val game = newGame()
        game.map.getOrCreateTile(centerPosition)

        assertThat(game.internalPlaceCreature(player(), centerPosition)).isFalse()
    }

    @Test
    fun `internalPlaceCreature rejects an already placed creature`() {
        val game = newGame()
        game.groundTile()
        val creature = player()

        assertThat(game.internalPlaceCreature(creature, centerPosition)).isTrue()
        assertThat(game.internalPlaceCreature(creature, centerPosition, forced = true)).isFalse()
    }

    @Test
    fun `internalPlaceCreature rejects an occupied tile and honours forced placement`() {
        val game = newGame()
        game.groundTile()
        val first = player(id = 1, name = "First")
        val second = player(id = 2, name = "Second")

        assertThat(game.internalPlaceCreature(first, centerPosition)).isTrue()
        assertThat(game.internalPlaceCreature(second, centerPosition)).isFalse()
        assertThat(game.internalPlaceCreature(second, centerPosition, forced = true)).isTrue()
        assertThat(second.position).isEqualTo(centerPosition)
    }

    @Test
    fun `internalPlaceCreature finds a surrounding tile when the center is occupied`() {
        val game = newGame()
        game.groundTile(centerPosition)
        val neighbours = listOf(
            -1 to -1, 0 to -1, 1 to -1,
            -1 to 0, 1 to 0,
            -1 to 1, 0 to 1, 1 to 1
        )
        neighbours.forEach { (dx, dy) ->
            game.groundTile(Position(centerPosition.x + dx, centerPosition.y + dy, centerPosition.z))
        }

        assertThat(game.internalPlaceCreature(player(id = 1, name = "First"), centerPosition)).isTrue()
        val second = player(id = 2, name = "Second")
        assertThat(game.internalPlaceCreature(second, centerPosition)).isTrue()

        assertThat(second.position).isNotEqualTo(centerPosition)
        assertThat(second.position.getDistanceTo(centerPosition)).isEqualTo(1)
    }

    @Test
    fun `removeCreature removes the creature and rejects a second removal`() {
        val game = newGame()
        game.groundTile()
        val creature = player()
        assertThat(game.internalPlaceCreature(creature, centerPosition)).isTrue()

        assertThat(game.removeCreature(creature)).isTrue()

        assertThat(creature.isRemoved()).isTrue()
        assertThat(game.removeCreature(creature)).isFalse()
    }

    // ----- getTile / players -----

    @Test
    fun `getTile returns the tile created on the map and null otherwise`() {
        val game = newGame()

        assertThat(game.getTile(centerPosition)).isNull()
        val tile = game.groundTile()

        assertThat(game.getTile(centerPosition)).isSameAs(tile)
    }

    @Test
    fun `getPlayers exposes players by id and removePlayer drops them`() {
        val game = newGame()
        val knight = player(id = 1, name = "Knight")
        val druid = player(id = 2, name = "Druid")

        game.addPlayer(knight)
        game.addPlayer(druid)
        assertThat(game.getPlayers()).containsEntry(1L, knight).containsEntry(2L, druid)

        game.removePlayer(knight)

        assertThat(game.getPlayers()).hasSize(1).doesNotContainKey(1L).containsEntry(2L, druid)
    }

    @Test
    fun `getPlayerByName matches ignoring case and rejects empty or unknown names`() {
        val game = newGame()
        val knight = player(id = 1, name = "Knight")
        game.addPlayer(knight)

        assertThat(game.getPlayerByName("Knight")).isSameAs(knight)
        assertThat(game.getPlayerByName("knight")).isSameAs(knight)
        assertThat(game.getPlayerByName("KNIGHT")).isSameAs(knight)
        assertThat(game.getPlayerByName("")).isNull()
        assertThat(game.getPlayerByName("Druid")).isNull()
    }

    @Test
    fun `getPlayerByName loads offline players only when allowOffline is set`() {
        val storage = InMemoryPlayerStorage()
        storage.save(player(id = 42, name = "Offline"))
        val game = Game(GameMap(), GameDispatcher(), storage)

        assertThat(game.getPlayerByName("Offline")).isNull()

        val loaded = game.getPlayerByName("Offline", allowOffline = true)

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("Offline")
    }

    // ----- internalAddItem -----

    @Test
    fun `internalAddItem returns NOTPOSSIBLE for a null tile or item`() {
        val game = newGame()
        val tile = game.groundTile()

        assertThat(game.internalAddItem(null, Item(id = stoneId))).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(game.internalAddItem(tile, null)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `internalAddItem rejects a tile without ground unless FLAG_NOLIMIT is set`() {
        val game = newGame()
        val barrenTile = game.map.getOrCreateTile(centerPosition)
        val stone = Item(id = stoneId)

        assertThat(game.internalAddItem(barrenTile, stone)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(barrenTile.items).isEmpty()

        assertThat(game.internalAddItem(barrenTile, stone, flags = CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(ReturnValue.NOERROR)
        assertThat(barrenTile.items).containsExactly(stone)
    }

    @Test
    fun `internalAddItem adds a non stackable item to the tile`() {
        val game = newGame()
        val tile = game.groundTile()
        val stone = Item(id = stoneId)

        assertThat(game.internalAddItem(tile, stone)).isEqualTo(ReturnValue.NOERROR)
        assertThat(tile.items).containsExactly(stone)
    }

    @Test
    fun `internalAddItem merges stackable items into the top down stack capped by stack size`() {
        val game = newGame()
        val tile = game.groundTile()

        assertThat(game.internalAddItem(tile, gold(60))).isEqualTo(ReturnValue.NOERROR)
        assertThat(game.internalAddItem(tile, gold(60))).isEqualTo(ReturnValue.NOERROR)

        // `Tile::addThing` inserts every down item at the front of the down
        // range (src/items/tile.cpp:1242-1244) and `Tile::getTopDownItem` is
        // that front stack, so the remainder leads.
        assertThat(tile.items).hasSize(2)
        assertThat(tile.items[0].count).isEqualTo(20)
        assertThat(tile.items[1].count).isEqualTo(100)

        assertThat(game.internalAddItem(tile, gold(10))).isEqualTo(ReturnValue.NOERROR)

        assertThat(tile.items).hasSize(2)
        assertThat(tile.items[0].count).isEqualTo(30)
    }

    @Test
    fun `internalAddItem keeps separate stacks for different item ids`() {
        val game = newGame()
        val tile = game.groundTile()

        assertThat(game.internalAddItem(tile, gold(10))).isEqualTo(ReturnValue.NOERROR)
        assertThat(game.internalAddItem(tile, platinum(10))).isEqualTo(ReturnValue.NOERROR)

        // Down items insert at the front (src/items/tile.cpp:1242-1244).
        assertThat(tile.items).hasSize(2)
        assertThat(tile.items.map { it.id }).containsExactly(platinumCoinId, goldCoinId)
    }

    @Test
    fun `internalAddItem with test true performs no mutation`() {
        val game = newGame()
        val tile = game.groundTile()

        assertThat(game.internalAddItem(tile, Item(id = stoneId), test = true)).isEqualTo(ReturnValue.NOERROR)
        assertThat(tile.items).isEmpty()
    }

    // ----- internalRemoveItem -----

    @Test
    fun `internalRemoveItem returns NOTPOSSIBLE for an item without a parent`() {
        val game = newGame()

        assertThat(game.internalRemoveItem(Item(id = stoneId))).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `internalRemoveItem removes a full stack from its parent tile`() {
        val game = newGame()
        val tile = game.groundTile()
        val stone = Item(id = stoneId)
        game.internalAddItem(tile, stone)

        assertThat(game.internalRemoveItem(stone)).isEqualTo(ReturnValue.NOERROR)

        assertThat(tile.items).isEmpty()
        assertThat(game.internalRemoveItem(stone)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `internalRemoveItem decrements a partial stack and rejects over count removal`() {
        val game = newGame()
        val tile = game.groundTile()
        val coins = gold(100)
        game.internalAddItem(tile, coins)

        assertThat(game.internalRemoveItem(coins, count = 30)).isEqualTo(ReturnValue.NOERROR)
        assertThat(tile.items).containsExactly(coins)
        assertThat(coins.count).isEqualTo(70)

        assertThat(game.internalRemoveItem(coins, count = 71)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(coins.count).isEqualTo(70)

        assertThat(game.internalRemoveItem(coins)).isEqualTo(ReturnValue.NOERROR)
        assertThat(tile.items).isEmpty()
    }

    @Test
    fun `internalRemoveItem with test true performs no mutation`() {
        val game = newGame()
        val tile = game.groundTile()
        val coins = gold(100)
        game.internalAddItem(tile, coins)

        assertThat(game.internalRemoveItem(coins, count = 30, test = true)).isEqualTo(ReturnValue.NOERROR)
        assertThat(coins.count).isEqualTo(100)
        assertThat(tile.items).containsExactly(coins)
    }

    // ----- internalMoveItem -----

    @Test
    fun `internalMoveItem moves a non stackable item between tiles`() {
        val game = newGame()
        val fromTile = game.groundTile(Position(1000, 1000, 7))
        val toTile = game.groundTile(Position(1001, 1000, 7))
        val stone = Item(id = stoneId)
        game.internalAddItem(fromTile, stone)

        assertThat(game.internalMoveItem(fromTile, toTile, item = stone, count = 1))
            .isEqualTo(ReturnValue.NOERROR)

        assertThat(fromTile.items).isEmpty()
        assertThat(toTile.items).containsExactly(stone)
    }

    @Test
    fun `internalMoveItem merges a moved stack into the destination stack`() {
        val game = newGame()
        val fromTile = game.groundTile(Position(1000, 1000, 7))
        val toTile = game.groundTile(Position(1001, 1000, 7))
        val source = gold(100)
        val destination = gold(20)
        game.internalAddItem(fromTile, source)
        game.internalAddItem(toTile, destination)

        assertThat(game.internalMoveItem(fromTile, toTile, item = source, count = 100))
            .isEqualTo(ReturnValue.NOERROR)

        assertThat(fromTile.items).isEmpty()
        assertThat(toTile.items).hasSize(2)
        assertThat(toTile.items.map { it.count }).containsExactlyInAnyOrder(100, 20)
        assertThat(destination.count).isEqualTo(100)
    }

    @Test
    fun `internalMoveItem keeps the remainder on the source for a partial stack move`() {
        val game = newGame()
        val fromTile = game.groundTile(Position(1000, 1000, 7))
        val toTile = game.groundTile(Position(1001, 1000, 7))
        val source = gold(100)
        game.internalAddItem(fromTile, source)

        assertThat(game.internalMoveItem(fromTile, toTile, item = source, count = 40))
            .isEqualTo(ReturnValue.NOERROR)

        assertThat(fromTile.items).containsExactly(source)
        assertThat(source.count).isEqualTo(60)
        assertThat(toTile.items).hasSize(1)
        assertThat(toTile.items[0].count).isEqualTo(40)
    }

    @Test
    fun `internalMoveItem rejects a destination without ground and an item not on the source`() {
        val game = newGame()
        val fromTile = game.groundTile(Position(1000, 1000, 7))
        val barrenTile = game.map.getOrCreateTile(Position(1001, 1000, 7))
        val stone = Item(id = stoneId)
        game.internalAddItem(fromTile, stone)

        assertThat(game.internalMoveItem(fromTile, barrenTile, item = stone, count = 1))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)

        val other = Item(id = stoneId)
        val otherTile = game.groundTile(Position(1002, 1000, 7))
        assertThat(game.internalMoveItem(fromTile, otherTile, item = other, count = 1))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `internalMoveItem silently ignores a move onto the same top down item`() {
        val game = newGame()
        val tile = game.groundTile()
        val stone = Item(id = stoneId)
        game.internalAddItem(tile, stone)

        assertThat(game.internalMoveItem(tile, tile, item = stone, count = 1))
            .isEqualTo(ReturnValue.NOERROR)

        assertThat(tile.items).containsExactly(stone)
    }
}
