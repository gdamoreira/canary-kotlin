package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.items.containers.depot.DepotLocker
import org.opentibia.canary.map.Position

/**
 * Transcribes `Player::depotChests` (src/creatures/players/player.hpp:1673) and
 * `Player::getDepotChest` (src/creatures/players/player.cpp:1998-2022): the
 * per-player depot-box map keyed by box id is the same instance
 * `IOLoginDataLoad::loadPlayerDepotItems`/`IOLoginDataSave::savePlayerDepotItems`
 * walk, and `Player::getDepotLocker` (player.cpp:2024-2058) materializes the
 * boxes through it.
 */
class PlayerDepotChestsTest {

    private fun newPlayer() = Player(
        id = 1L,
        name = "Depot Hero",
        maxHealth = 150,
        health = 150,
        position = Position(1000, 1000, 7),
    )

    @Test
    fun `getDepotChest auto creates the box chest and reuses it`() {
        val player = newPlayer()

        assertThat(player.getDepotChests()).isEmpty()

        val chest = player.getDepotChest(3, true)

        assertThat(chest).isNotNull
        assertThat(chest!!.id).isEqualTo(DepotChest.ITEM_DEPOT_NULL + 3)
        assertThat(player.getDepotChest(3, true)).isSameAs(chest)
        assertThat(player.getDepotChests()).containsEntry(3, chest)
    }

    @Test
    fun `getDepotChest returns null without auto create`() {
        val player = newPlayer()

        assertThat(player.getDepotChest(3, false)).isNull()
        assertThat(player.getDepotChests()).isEmpty()
    }

    @Test
    fun `the town depot ids select the roman numeral chests`() {
        val player = newPlayer()

        assertThat(player.getDepotChest(18, true)!!.id).isEqualTo(DepotChest.ITEM_DEPOT_XVIII)
        assertThat(player.getDepotChest(19, true)!!.id).isEqualTo(DepotChest.ITEM_DEPOT_XIX)
        assertThat(player.getDepotChest(20, true)!!.id).isEqualTo(DepotChest.ITEM_DEPOT_XX)
    }

    @Test
    fun `the depot locker materializes the boxes through the depot chest map`() {
        val player = newPlayer()

        player.getDepotLocker(1)

        assertThat(player.getDepotChests()).isNotEmpty
        assertThat(player.getDepotChest(1, false)).isNotNull
        assertThat(player.getDepotChest(DepotLocker.DEPOT_BOXES, false)).isNotNull
    }
}
