package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the remaining Player accessors.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/player.cpp:2058-2063 (`getRewardChest`)
 *  - src/creatures/players/player.cpp:6815-6833 (`setSpecialMenuAvailable`)
 */
class PlayerMenuAndRewardChestTest {

    private fun newPlayer() = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    @Test
    fun `getRewardChest lazily returns the single owned chest`() {
        // player.cpp:2058-2063.
        val player = newPlayer()

        val first = player.getRewardChest()
        assertThat(first).isSameAs(player.getRewardChest())
        assertThat(first).isSameAs(player.features.rewardChest)
    }

    @Test
    fun `setSpecialMenuAvailable stores the three gates and notifies the client`() {
        // player.cpp:6826-6832.
        val player = newPlayer()
        var notifications = 0
        player.sendSpecialContainersAvailable = { notifications++ }

        player.setSpecialMenuAvailable(stashBool = true, marketMenuBool = true, depotSearchBool = true)

        assertThat(player.isStashMenuAvailable()).isTrue()
        assertThat(player.marketMenu).isTrue()
        assertThat(player.isDepotSearchAvailable()).isTrue()
        assertThat(notifications).isEqualTo(1)
    }

    @Test
    fun `disabling depot search closes an open search window`() {
        // player.cpp:6817-6821.
        val player = newPlayer()
        player.setSpecialMenuAvailable(true, true, true)
        player.setDepotSearchIsOpen(itemId = 2160, tier = 1)
        var closes = 0
        player.sendCloseDepotSearch = { closes++ }

        player.setSpecialMenuAvailable(stashBool = false, marketMenuBool = false, depotSearchBool = false)

        assertThat(player.isDepotSearchOpen()).isFalse()
        assertThat(player.getDepotSearchItemId()).isZero()
        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun `keeping depot search enabled leaves the open window alone`() {
        // player.cpp:6817 guard (`!depotSearchBool`).
        val player = newPlayer()
        player.setSpecialMenuAvailable(true, true, true)
        player.setDepotSearchIsOpen(itemId = 2160, tier = 1)
        var closes = 0
        player.sendCloseDepotSearch = { closes++ }

        player.setSpecialMenuAvailable(stashBool = true, marketMenuBool = true, depotSearchBool = true)

        assertThat(player.isDepotSearchOpen()).isTrue()
        assertThat(closes).isZero()
    }
}
