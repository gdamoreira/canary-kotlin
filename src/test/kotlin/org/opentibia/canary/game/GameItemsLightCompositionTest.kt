package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Composition tests for `Player::updateItemsLight` (src/players/player.cpp:6284-6305)
 * driven by the inventory-change producers: an inventory light change must
 * refresh the composed `PlayerLightState` and fan `Game::changeLight`
 * (game.cpp:7808) out to the player spectators.
 */
class GameItemsLightCompositionTest {

    private val center = Position(1000, 1000, 7)

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2159, name = "lamp", isMovable = true, lightLevel = 8, lightColor = 200))
        it.register(ItemType(id = 2160, name = "helmet", isMovable = true, weight = 100.0))
    }

    private class Harness(
        val game: Game,
        val owner: Player,
        val items: Items,
        val lights: MutableList<Long>,
    )

    private fun harness(): Harness {
        Spectators.clearCache()
        val items = itemsRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        game.map.getOrCreateTile(center).ground = Item(id = 100)
        val owner = Player(id = 1L, name = "Hero", maxHealth = 150, health = 100, position = center)
        assertThat(game.internalPlaceCreature(owner, center)).isTrue()
        val lights = mutableListOf<Long>()
        game.sendCreatureLight = { _, creature -> lights.add(creature.id) }
        return Harness(game, owner, items, lights)
    }

    @Test
    fun `adding a light item fires the items-light update and the changeLight fanout`() {
        val harness = harness()
        val lamp = Item(2159, 1, harness.items.get(2159))

        val ret = harness.game.internalPlayerAddItem(harness.owner, lamp)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.lights).containsExactly(harness.owner.id)
    }

    @Test
    fun `a lightless inventory change keeps the items-light value and does not notify`() {
        val harness = harness()
        val helmet = Item(2160, 1, harness.items.get(2160))

        val ret = harness.game.internalPlayerAddItem(harness.owner, helmet)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.lights).isEmpty()
    }

    @Test
    fun `removing the light item fires the items-light update again`() {
        val harness = harness()
        val lamp = Item(2159, 1, harness.items.get(2159))
        assertThat(harness.game.internalPlayerAddItem(harness.owner, lamp)).isEqualTo(ReturnValue.NOERROR)
        harness.lights.clear()

        val ret = harness.game.internalRemoveItem(lamp)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.lights).containsExactly(harness.owner.id)
    }
}
