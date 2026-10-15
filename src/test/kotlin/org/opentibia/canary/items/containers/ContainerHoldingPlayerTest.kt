package org.opentibia.canary.items.containers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Item::getHoldingPlayer` port
 * (src/items/item.cpp:879-889): the port's [Player] is not a `Cylinder`, so
 * the world records the holding link on a container entering the player
 * inventory ([Container.setHoldingPlayer]) and a nested container resolves it
 * by walking the parent chain.
 */
class ContainerHoldingPlayerTest {

    private fun player(): Player = Player(
        id = 1L,
        name = "Hero",
        maxHealth = 150,
        health = 100,
        position = Position(1, 1, 7),
    )

    @Test
    fun `a held container and its nested children resolve the holding player`() {
        val holder = player()
        val backpack = Container(1987, 8)
        val bag = Container(1988, 8)
        backpack.addThing(bag)
        val coin = Item(3031, 1)
        bag.addThing(coin)

        backpack.setHoldingPlayer(holder)

        assertThat(backpack.getHoldingPlayer()).isSameAs(holder)
        assertThat(bag.getHoldingPlayer()).isSameAs(holder)
        assertThat(coin.getHoldingPlayer()).isSameAs(holder)
    }

    @Test
    fun `a container without a recorded holder resolves no player`() {
        val backpack = Container(1987, 8)

        assertThat(backpack.getHoldingPlayer()).isNull()
    }

    @Test
    fun `clearing the holding link detaches the whole subtree`() {
        val holder = player()
        val backpack = Container(1987, 8)
        val bag = Container(1988, 8)
        backpack.addThing(bag)
        backpack.setHoldingPlayer(holder)

        backpack.setHoldingPlayer(null)

        assertThat(backpack.getHoldingPlayer()).isNull()
        assertThat(bag.getHoldingPlayer()).isNull()
    }
}
