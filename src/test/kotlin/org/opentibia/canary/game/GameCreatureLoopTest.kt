package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `Game::checkCreatures` (game.cpp:7719): the rotating
 * `checkCreatureLists` buckets call `Creature::onThink` for alive flagged
 * creatures and prune dead/unflagged entries.
 */
class GameCreatureLoopTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private class ThinkingCreature(id: Long, position: Position) :
        Creature(id, "creature-$id", maxHealth = 100, health = 100, position = position) {

        val thinkIntervals = mutableListOf<Int>()

        override fun onThink(interval: Int) {
            thinkIntervals.add(interval)
        }
    }

    private fun thinkingCreature(id: Long = 1): ThinkingCreature = ThinkingCreature(id, center)

    @Test
    fun `checkCreatures thinks each flagged creature once per bucket rotation`() {
        val game = newGame()
        val creature = thinkingCreature()

        game.addCreatureCheck(creature)
        repeat(10) { game.checkCreatures() }

        assertThat(creature.thinkIntervals).containsExactly(1000)
    }

    @Test
    fun `checkCreatures keeps thinking an alive creature on later rotations`() {
        val game = newGame()
        val creature = thinkingCreature()
        game.addCreatureCheck(creature)

        repeat(20) { game.checkCreatures() }

        assertThat(creature.thinkIntervals).containsExactly(1000, 1000)
    }

    @Test
    fun `checkCreatures drops a dead creature and clears the vector flag`() {
        val game = newGame()
        val creature = thinkingCreature()
        game.addCreatureCheck(creature)
        creature.health = 0

        repeat(10) { game.checkCreatures() }

        assertThat(creature.thinkIntervals).isEmpty()
        assertThat(creature.inCheckCreaturesVector).isFalse()

        repeat(10) { game.checkCreatures() }

        assertThat(creature.thinkIntervals).isEmpty()
    }

    @Test
    fun `checkCreatures drops an unflagged creature`() {
        val game = newGame()
        val creature = thinkingCreature()
        game.addCreatureCheck(creature)
        creature.creatureCheck = false

        repeat(10) { game.checkCreatures() }

        assertThat(creature.thinkIntervals).isEmpty()
        assertThat(creature.inCheckCreaturesVector).isFalse()
    }

    @Test
    fun `checkCreatures keeps a flagged creature in its bucket`() {
        val game = newGame()
        val creature = thinkingCreature()
        game.addCreatureCheck(creature)

        repeat(30) { game.checkCreatures() }

        assertThat(creature.thinkIntervals).containsExactly(1000, 1000, 1000)
    }

    @Test
    fun `checkCreatures skips creatures removed from the world`() {
        val game = newGame()
        val player = Player(id = 1, name = "Knight", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)

        repeat(10) { game.checkCreatures() }

        assertThat(game.getPlayerByID(1)).isSameAs(player)
    }
}
