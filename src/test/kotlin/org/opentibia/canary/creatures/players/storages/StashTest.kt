package org.opentibia.canary.creatures.players.storages

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for Canary's supply-stash state, transcribed from
 * `Player::addItemOnStash`, `Player::getStashItemCount`, `Player::withdrawItem`
 * and `Player::getStashItems`
 * (src/creatures/players/player.cpp:5404-5439), with the `StashItemList`
 * alias from src/creatures/creatures_definitions.hpp:1707.
 */
class StashTest {

    @Test
    fun `addItemOnStash stores a new stack with the amount`() {
        val stash = Stash()

        stash.addItemOnStash(2160, 12)

        assertThat(stash.getStashItemCount(2160)).isEqualTo(12)
    }

    @Test
    fun `addItemOnStash accumulates onto an existing stack`() {
        val stash = Stash()
        stash.addItemOnStash(2160, 12)

        stash.addItemOnStash(2160, 8)

        assertThat(stash.getStashItemCount(2160)).isEqualTo(20)
    }

    @Test
    fun `getStashItemCount returns zero for items not in the stash`() {
        val stash = Stash()

        assertThat(stash.getStashItemCount(2160)).isZero()
        stash.addItemOnStash(2160, 1)
        assertThat(stash.getStashItemCount(2161)).isZero()
    }

    @Test
    fun `withdrawItem decrements a larger stack and keeps it`() {
        val stash = Stash()
        stash.addItemOnStash(2160, 20)

        val withdrawn = stash.withdrawItem(2160, 5)

        assertThat(withdrawn).isTrue()
        assertThat(stash.getStashItemCount(2160)).isEqualTo(15)
    }

    @Test
    fun `withdrawItem removes the stack when the amount matches exactly`() {
        val stash = Stash()
        stash.addItemOnStash(2160, 20)

        val withdrawn = stash.withdrawItem(2160, 20)

        assertThat(withdrawn).isTrue()
        assertThat(stash.getStashItemCount(2160)).isZero()
        assertThat(stash.getStashItems()).doesNotContainKey(2160)
    }

    @Test
    fun `withdrawItem refuses to withdraw more than stored and keeps the stack`() {
        val stash = Stash()
        stash.addItemOnStash(2160, 20)

        val withdrawn = stash.withdrawItem(2160, 21)

        assertThat(withdrawn).isFalse()
        assertThat(stash.getStashItemCount(2160)).isEqualTo(20)
    }

    @Test
    fun `withdrawItem refuses unknown items`() {
        val stash = Stash()
        stash.addItemOnStash(2160, 20)

        val withdrawn = stash.withdrawItem(9999, 1)

        assertThat(withdrawn).isFalse()
        assertThat(stash.getStashItems()).containsExactlyEntriesOf(mapOf(2160 to 20))
    }

    @Test
    fun `withdrawItem of zero keeps the stack exactly like the cpp amount branch`() {
        val stash = Stash()
        stash.addItemOnStash(2160, 20)

        val withdrawn = stash.withdrawItem(2160, 0)

        assertThat(withdrawn).isTrue()
        assertThat(stash.getStashItemCount(2160)).isEqualTo(20)
    }

    @Test
    fun `getStashItems returns the stash ordered by item id`() {
        val stash = Stash()
        stash.addItemOnStash(2160, 20)
        stash.addItemOnStash(100, 3)
        stash.addItemOnStash(11444, 1)

        assertThat(stash.getStashItems().keys).containsExactly(100, 2160, 11444)
        assertThat(stash.getStashItems().entries.map { it.key to it.value })
            .containsExactly(100 to 3, 2160 to 20, 11444 to 1)
    }

    @Test
    fun `getStashItems returns a snapshot`() {
        val stash = Stash()
        stash.addItemOnStash(2160, 20)
        val snapshot = stash.getStashItems()

        stash.withdrawItem(2160, 20)

        assertThat(snapshot).containsExactlyEntriesOf(mapOf(2160 to 20))
        assertThat(stash.getStashItems()).isEmpty()
    }
}
