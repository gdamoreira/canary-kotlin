package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.map.Position

/**
 * Unit tests for [KnownCreatureSet], the ported `ProtocolGame::knownCreatureSet`
 * (`src/server/network/protocol/protocolgame.hpp:620`) with the
 * `ProtocolGame::checkCreatureAsKnown` cache rules
 * (`src/server/network/protocol/protocolgame.cpp:2334-2372`): the first check
 * inserts and reports the creature unknown, the second reports it known, and at
 * the 1300-entry limit the first id that is neither a party member nor visible
 * is evicted. When every candidate is protected the "bad situation" fallback
 * removes the first entry (skipping the queried id).
 *
 * The visibility and party-protection collaborators are constructor seams, so
 * each eviction branch is driven deterministically; the live `ProtocolGame`
 * viewport `canSee` behavior is covered by the sink frame tests.
 */
class KnownCreatureSetTest {

    private fun player(id: Long = 1, name: String = "Hero"): Player = Player(
        id = id,
        name = name,
        maxHealth = 150,
        health = 100,
        maxMana = 50,
        mana = 50,
        capacity = 400.0,
        position = Position(1000, 1000, 7),
    )

    private fun monster(id: Long): Monster =
        Monster(id, "Rat", maxHealth = 100, health = 80, position = Position(1000, 1000, 7))

    private fun ids(from: Long, count: Int): List<Long> = (0 until count).map { from + it }

    private fun fill(set: KnownCreatureSet) {
        ids(5001L, 1300).forEach { set.checkCreatureAsKnown(it) }
    }

    @Test
    fun `the first check inserts the creature and reports it unknown without eviction`() {
        val set = KnownCreatureSet(player())

        val result = set.checkCreatureAsKnown(5000L)

        assertThat(result.known).isFalse()
        assertThat(result.removedKnown).isZero()
        assertThat(set.contains(5000L)).isTrue()
    }

    @Test
    fun `the second check reports the creature as known`() {
        val set = KnownCreatureSet(player())
        set.checkCreatureAsKnown(5000L)

        val result = set.checkCreatureAsKnown(5000L)

        assertThat(result.known).isTrue()
        assertThat(result.removedKnown).isZero()
    }

    @Test
    fun `the 1300-entry cache evicts the first inserted unseen creature`() {
        val set = KnownCreatureSet(player())
        fill(set)

        val result = set.checkCreatureAsKnown(6301L)

        assertThat(result.known).isFalse()
        assertThat(result.removedKnown).isEqualTo(5001L)
        assertThat(set.contains(5001L)).isFalse()
        assertThat(set.contains(5002L)).isTrue()
        assertThat(set.contains(6301L)).isTrue()
    }

    @Test
    fun `a visible creature is protected from eviction`() {
        val visible = monster(5001L)
        val set = KnownCreatureSet(
            player(),
            creatureById = { id -> if (id == 5001L) visible else null },
            canSee = { it == visible },
        )
        fill(set)

        val result = set.checkCreatureAsKnown(6301L)

        assertThat(result.removedKnown).isEqualTo(5002L)
        assertThat(set.contains(5001L)).isTrue()
        assertThat(set.contains(5002L)).isFalse()
    }

    @Test
    fun `a party member is protected from eviction even when unseen`() {
        val observer = player()
        val party = Party(observer)
        val member = player(id = 2, name = "Mate").also { it.setParty(party) }
        val set = KnownCreatureSet(
            observer,
            creatureById = { id -> if (id == 5001L) member else null },
            canSee = { false },
        )
        fill(set)

        val result = set.checkCreatureAsKnown(6301L)

        assertThat(result.removedKnown).isEqualTo(5002L)
        assertThat(set.contains(5001L)).isTrue()
        assertThat(set.contains(5002L)).isFalse()
    }

    @Test
    fun `an unseen player from another party is evicted`() {
        val observer = player()
        Party(observer)
        val stranger = player(id = 2, name = "Stranger")
        stranger.setParty(Party(stranger))
        val set = KnownCreatureSet(
            observer,
            creatureById = { id -> if (id == 5001L) stranger else null },
            canSee = { false },
        )
        fill(set)

        val result = set.checkCreatureAsKnown(6301L)

        assertThat(result.removedKnown).isEqualTo(5001L)
        assertThat(set.contains(5001L)).isFalse()
    }

    @Test
    fun `when every candidate is protected the fallback removes the first entry`() {
        val set = KnownCreatureSet(
            player(),
            creatureById = { id -> monster(id) },
            canSee = { it is Creature },
        )
        fill(set)

        val result = set.checkCreatureAsKnown(6301L)

        assertThat(result.known).isFalse()
        assertThat(result.removedKnown).isEqualTo(5001L)
        assertThat(set.contains(5001L)).isFalse()
        assertThat(set.contains(6301L)).isTrue()
    }
}
