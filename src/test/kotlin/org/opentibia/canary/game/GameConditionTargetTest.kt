package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CreatureConditionTarget
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the production `ConditionTarget` behind
 * `Creature.conditionTarget`:
 *
 *  - the owner-resolved drain (`ConditionDamage::doDamage`,
 *    src/creatures/combat/condition.cpp:2000-2033) over
 *    `Creature::drainHealth(attacker, damage)` (creature.cpp:893-899);
 *  - the `g_game()` owner resolution (`Game::getPlayerByGUID`,
 *    src/game/game.cpp:1419, and `Game::getCreatureByID`, game.cpp:1296);
 *  - the world bindings that install the adapter: `Game::addPlayer`
 *    (game.cpp:1622-1650) for session players and
 *    `Game::internalPlaceCreature` (game.cpp:1495) for grid creatures.
 */
class GameConditionTargetTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.ground(): Unit {
        map.getOrCreateTile(center).ground = Item(id = 100)
    }

    private fun Game.owner(id: Long, position: Position): Player =
        Player(id = id, name = "Owner$id", maxHealth = 150, health = 150, position = position)
            .also { addPlayer(it) }

    private fun Game.victim(id: Long): Player =
        Player(id = id, name = "Victim$id", maxHealth = 150, health = 150, position = center)

    private fun ownedFireDamage(ownerId: Long, amount: Int): ConditionDamage =
        ConditionDamage(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FIRE).apply {
            setParam(ConditionParam.CONDITION_PARAM_OWNER, ownerId.toInt())
            addDamage(1, 1000, -amount)
        }

    @Test
    fun `adding a session player binds the production condition target`() {
        val game = newGame()
        val player = game.victim(1)

        game.addPlayer(player)

        assertThat(player.conditionTarget).isInstanceOf(CreatureConditionTarget::class.java)
    }

    @Test
    fun `placing a creature binds the production condition target`() {
        val game = newGame()
        game.ground()
        val monster = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = center)

        assertThat(game.internalPlaceCreature(monster, center)).isTrue()

        assertThat(monster.conditionTarget).isInstanceOf(CreatureConditionTarget::class.java)
    }

    @Test
    fun `condition damage drains with the owner credited`() {
        val game = newGame()
        game.ground()
        val owner = game.owner(7, Position(999, 1000, 7))
        val victim = game.victim(1)
        game.addPlayer(victim)
        assertThat(game.internalPlaceCreature(victim, center)).isTrue()

        assertThat(victim.addCondition(ownedFireDamage(owner.id, 20))).isTrue()

        assertThat(victim.health).isEqualTo(130)
        assertThat(victim.getDamageMap()[7L]?.total).isEqualTo(20)
        assertThat(victim.lastHitCreatureId).isEqualTo(7L)
    }

    @Test
    fun `condition ticks keep draining through the production target`() {
        val game = newGame()
        game.ground()
        val owner = game.owner(7, Position(999, 1000, 7))
        val victim = game.victim(1)
        game.addPlayer(victim)
        assertThat(game.internalPlaceCreature(victim, center)).isTrue()

        val condition = ConditionDamage(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FIRE).apply {
            setParam(ConditionParam.CONDITION_PARAM_OWNER, owner.id.toInt())
            addDamage(2, 1000, -5)
        }
        assertThat(victim.addCondition(condition)).isTrue()
        assertThat(victim.health).isEqualTo(145)

        victim.executeConditions(1000)

        assertThat(victim.health).isEqualTo(140)
        assertThat(victim.getDamageMap()[7L]?.total).isEqualTo(10)
    }

    @Test
    fun `condition damage with an unresolved owner drains without attribution`() {
        val game = newGame()
        game.ground()
        val victim = game.victim(1)
        game.addPlayer(victim)
        assertThat(game.internalPlaceCreature(victim, center)).isTrue()

        assertThat(victim.addCondition(ownedFireDamage(42, 20))).isTrue()

        assertThat(victim.health).isEqualTo(130)
        assertThat(victim.getDamageMap()).isEmpty()
    }
}
