package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the remaining small `Game` helpers:
 *
 *  - `Game::internalRemoveItems` (game.cpp:11765)
 *  - `Game::createPlayerTask` (game.cpp:7586)
 *  - `Game::resetNpcs` (game.cpp:777)
 *  - `Game::playerBrowseField` (game.cpp:5599)
 */
class GameRemainderHelpersTest {

    private val center = Position(1000, 1000, 7)

    private class Harness(
        val game: Game,
        val items: Items,
        val cancels: MutableList<ReturnValue>,
    )

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "magic sword", isMovable = true))
        it.register(ItemType(id = 2170, name = "gold coin", isMovable = true, isStackable = true, stackSize = 100))
        it.register(ItemType(id = 2171, name = "silver coin", isMovable = true, isStackable = true, stackSize = 100))
        it.register(ItemType(id = 470, name = "browse field", isContainer = true, capacity = 30))
    }

    private fun newGame(): Harness {
        val items = itemsRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        return Harness(game, items, cancels)
    }

    private fun Game.tile(position: Position = center) =
        map.getOrCreateTile(position).also {
            it.ground = Item(id = 100, type = ItemType(id = 100, name = "ground", isGroundTile = true))
        }

    private fun player(game: Game, id: Long = 1, name: String = "Hero"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also { game.addPlayer(it) }

    private fun item(harness: Harness, id: Int, count: Int = 1): Item =
        Item(id = id, count = count, type = harness.items.get(id))

    // ----- internalRemoveItems -----

    @Test
    fun `internalRemoveItems consumes stackable items across the vector`() {
        val harness = newGame()
        val tile = harness.game.tile()
        val first = item(harness, 2170, count = 5)
        val second = item(harness, 2171, count = 3)
        assertThat(harness.game.internalAddItem(tile, first, flags = CylinderFlags.FLAG_NOLIMIT)).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.game.internalAddItem(tile, second, flags = CylinderFlags.FLAG_NOLIMIT)).isEqualTo(ReturnValue.NOERROR)

        harness.game.internalRemoveItems(listOf(first, second), 7, stackable = true)

        assertThat(tile.items).doesNotContain(first)
        assertThat(tile.items).contains(second)
        assertThat(second.getItemCount()).isEqualTo(1)
    }

    @Test
    fun `internalRemoveItems removes every entry when not stackable`() {
        val harness = newGame()
        val tile = harness.game.tile()
        val first = item(harness, 2160)
        val second = item(harness, 2160)
        assertThat(harness.game.internalAddItem(tile, first, flags = CylinderFlags.FLAG_NOLIMIT)).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.game.internalAddItem(tile, second, flags = CylinderFlags.FLAG_NOLIMIT)).isEqualTo(ReturnValue.NOERROR)

        harness.game.internalRemoveItems(listOf(first, second), 1, stackable = false)

        assertThat(tile.items).isEmpty()
    }

    // ----- createPlayerTask -----

    @Test
    fun `createPlayerTask builds a delayed player task`() {
        val harness = newGame()
        var ran = false

        val task = harness.game.createPlayerTask(400, { ran = true }, "Game::createPlayerTask")

        assertThat(task.delayMs).isEqualTo(400)
        assertThat(task.context).isEqualTo("Game::createPlayerTask")
        task.action()
        assertThat(ran).isTrue()
    }

    // ----- resetNpcs -----

    @Test
    fun `resetNpcs clears every npc player interaction`() {
        val harness = newGame()
        val npc = Npc(id = 50, name = "Shopkeeper", position = center)
        npc.setPlayerInteraction(1, 2)
        harness.game.addNpc(npc)
        assertThat(npc.isInteractingWithPlayer(1)).isTrue()

        harness.game.resetNpcs()

        assertThat(npc.isInteractingWithPlayer(1)).isFalse()
    }

    // ----- playerBrowseField -----

    @Test
    fun `playerBrowseField ignores an unknown player`() {
        val harness = newGame()
        harness.game.playerBrowseField(404, center)
    }

    @Test
    fun `playerBrowseField cancels a different floor`() {
        val harness = newGame()
        val user = player(harness.game)

        harness.game.playerBrowseField(user.id, Position(1000, 1000, 8))
        harness.game.playerBrowseField(user.id, Position(1000, 1000, 6))

        assertThat(harness.cancels).containsExactly(
            ReturnValue.FIRSTGODOWNSTAIRS,
            ReturnValue.FIRSTGOUPSTAIRS,
        )
    }

    @Test
    fun `playerBrowseField cancels when there is no path to the field`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.map.getOrCreateTile(Position(1000, 1002, 7)).ground = Item(id = 100)
        harness.game.getPathTo = { _, _, _, _, _, _ -> null }

        harness.game.playerBrowseField(user.id, Position(1000, 1002, 7))

        assertThat(harness.cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerBrowseField queues the walk when a path exists`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.map.getOrCreateTile(Position(1000, 1002, 7)).ground = Item(id = 100)
        harness.game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.South) }
        var queued: List<Direction>? = null
        harness.game.queuePlayerAutoWalk = { _, dirs -> queued = dirs; true }

        harness.game.playerBrowseField(user.id, Position(1000, 1002, 7))

        assertThat(queued).containsExactly(Direction.South)
    }

    @Test
    fun `playerBrowseField ignores a missing tile`() {
        val harness = newGame()
        val user = player(harness.game)

        harness.game.playerBrowseField(user.id, Position(1001, 1000, 7))

        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerBrowseField obeys the event and callback vetoes`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.tile()
        var created = false
        harness.game.containerCreateBrowseField = { _ -> created = true; Container(id = 470, capacity = 30) }
        harness.game.eventPlayerOnBrowseField = { _, _ -> false }

        harness.game.playerBrowseField(user.id, center)

        assertThat(created).isFalse()

        harness.game.eventPlayerOnBrowseField = { _, _ -> true }
        harness.game.callbackPlayerOnBrowseField = { _, _ -> false }

        harness.game.playerBrowseField(user.id, center)

        assertThat(created).isFalse()
    }

    @Test
    fun `playerBrowseField opens the browse container under the dummy id`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.tile()
        val browse = Container(id = 470, capacity = 30)
        harness.game.containerCreateBrowseField = { _ -> browse }
        var sent: Triple<Int, Container, Int>? = null
        user.sendContainer = { cid, container, _, index -> sent = Triple(cid, container, index) }

        harness.game.playerBrowseField(user.id, center)

        val dummyId = 0xF - ((center.x % 3) * 3 + (center.y % 3))
        assertThat(user.getContainerByID(dummyId)).isSameAs(browse)
        assertThat(sent).isEqualTo(Triple(dummyId, browse, 0))
    }

    @Test
    fun `playerBrowseField closes an existing dummy container and reuses the field`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.tile()
        val browse = Container(id = 470, capacity = 30)
        harness.game.containerCreateBrowseField = { _ -> browse }
        val dummyId = 0xF - ((center.x % 3) * 3 + (center.y % 3))
        val alreadyOpen = Container(id = 5000, capacity = 8)
        user.addContainer(dummyId, alreadyOpen)
        var closed: Pair<Player, Container>? = null
        harness.game.playerOnCloseContainer = { p, container -> closed = p to container }
        var created = false
        harness.game.containerCreateBrowseField = { _ -> created = true; browse }

        harness.game.playerBrowseField(user.id, center)

        assertThat(closed).isEqualTo(user to alreadyOpen)
        assertThat(user.getContainerByID(dummyId)).isNull()
        assertThat(created).isTrue()
    }
}
