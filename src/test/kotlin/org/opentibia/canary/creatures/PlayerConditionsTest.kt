package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.FakeConditionTarget
import org.opentibia.canary.map.Position

/**
 * Transcribes the condition storage and tick loop Canary's `Player` inherits:
 *
 * - `Creature::addCondition` / `removeCondition` / `getCondition` /
 *   `getConditionsByType` / `hasCondition`
 *   (src/creatures/creature.cpp:1372-1605)
 * - `Creature::onAddCondition` haste/paralyze exclusion (creature.cpp:1216-1222)
 * - `Creature::executeConditions` (creature.cpp:1565-1583), the tick
 *   `Game::checkCreatures` runs on players right after `onThink`
 *   (src/game/game.cpp:7726-7733)
 *
 * The wall clock is injected (AGENTS.md 1.1) so `endTime`/`ticks` expiry is
 * deterministic, mirroring Canary's `OTSYS_TIME()`.
 */
class PlayerConditionsTest {

    private var clock = 1_000_000L

    private fun newPlayer() = Player(
        id = 7,
        name = "Condition Hero",
        maxHealth = 150,
        health = 150,
        maxMana = 50,
        mana = 50,
        position = Position(100, 100, 7),
    ).apply { now = { clock } }

    private fun generic(
        type: ConditionType,
        ticks: Int,
        conditionId: ConditionId = ConditionId.CONDITIONID_DEFAULT,
        subId: Int = 0,
    ): Condition =
        Condition.createCondition(conditionId, type, ticks, subId = subId, now = { clock })!!

    @Test
    fun `new player has no conditions`() {
        val player = newPlayer()

        assertThat(player.conditions).isEmpty()
        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK)).isNull()
        assertThat(player.hasCondition(ConditionType.CONDITION_DRUNK)).isFalse()
    }

    @Test
    fun `addCondition attaches a condition retrievable by type id and subId`() {
        val player = newPlayer()
        val condition = generic(ConditionType.CONDITION_DRUNK, 10_000, subId = 3)

        assertThat(player.addCondition(condition)).isTrue()

        assertThat(player.conditions).containsExactly(condition)
        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK)).isSameAs(condition)
        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_DEFAULT, 3))
            .isSameAs(condition)
        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_DEFAULT, 4)).isNull()
        assertThat(player.hasCondition(ConditionType.CONDITION_DRUNK, 3)).isTrue()
        assertThat(player.hasCondition(ConditionType.CONDITION_DRUNK, 0)).isFalse()
    }

    @Test
    fun `addCondition ignores a null condition`() {
        // Creature::addCondition null guard (creature.cpp:1374-1376).
        val player = newPlayer()

        assertThat(player.addCondition(null)).isFalse()
        assertThat(player.conditions).isEmpty()
    }

    @Test
    fun `re-adding a longer condition stacks into the stored instance`() {
        // Creature::addCondition finds the same (type,id,subId) and refreshes it
        // (creature.cpp:1381-1385) through Condition::updateCondition
        // (condition.cpp:543-557).
        val player = newPlayer()
        val stored = generic(ConditionType.CONDITION_DRUNK, 10_000)
        player.addCondition(stored)

        assertThat(player.addCondition(generic(ConditionType.CONDITION_DRUNK, 20_000))).isTrue()

        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK)).isSameAs(stored)
        assertThat(stored.ticks).isEqualTo(20_000)
        assertThat(player.getConditionsByType(ConditionType.CONDITION_DRUNK)).hasSize(1)
    }

    @Test
    fun `re-adding a shorter condition keeps the longer remaining duration`() {
        // Condition::updateCondition rejects the shorter refresh (condition.cpp:549-551).
        val player = newPlayer()
        val stored = generic(ConditionType.CONDITION_DRUNK, 20_000)
        player.addCondition(stored)

        player.addCondition(generic(ConditionType.CONDITION_DRUNK, 5_000))

        assertThat(stored.ticks).isEqualTo(20_000)
    }

    @Test
    fun `conditions of different subIds stay independent`() {
        // Creature::getCondition keys on (type, id, subId) (creature.cpp:1513-1525).
        val player = newPlayer()
        val first = generic(ConditionType.CONDITION_DRUNK, 10_000, subId = 1)
        val second = generic(ConditionType.CONDITION_DRUNK, 10_000, subId = 2)
        player.addCondition(first)
        player.addCondition(second)

        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_DEFAULT, 1))
            .isSameAs(first)
        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_DEFAULT, 2))
            .isSameAs(second)
        assertThat(player.getConditionsByType(ConditionType.CONDITION_DRUNK)).containsExactly(first, second)
    }

    @Test
    fun `adding paralyze cancels an active haste condition`() {
        // Creature::onAddCondition (creature.cpp:1216-1222).
        val player = newPlayer()
        val haste = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_HASTE, 10_000, param = 100, now = { clock },
        )!!
        player.addCondition(haste)

        val paralyze = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_PARALYZE, 10_000, param = -100, now = { clock },
        )!!
        assertThat(player.addCondition(paralyze)).isTrue()

        assertThat(player.getCondition(ConditionType.CONDITION_HASTE)).isNull()
        assertThat(player.getCondition(ConditionType.CONDITION_PARALYZE)).isSameAs(paralyze)
    }

    @Test
    fun `onThink ticks live conditions and keeps them attached`() {
        val player = newPlayer()
        val condition = generic(ConditionType.CONDITION_DRUNK, 10_000)
        player.addCondition(condition)

        clock += 1_000
        player.onThink(1_000)

        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK)).isSameAs(condition)
        assertThat(condition.ticks).isEqualTo(9_000)
    }

    @Test
    fun `onThink expires conditions once their duration elapsed`() {
        val player = newPlayer()
        player.addCondition(generic(ConditionType.CONDITION_DRUNK, 1_000))

        clock += 2_000
        player.onThink(1_000)

        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK)).isNull()
        assertThat(player.conditions).isEmpty()
    }

    @Test
    fun `executeConditions ends an expired condition through endCondition`() {
        // Creature::executeConditions (creature.cpp:1565-1583): the expired
        // condition calls endCondition before it is dropped. The injected
        // ConditionTarget records the speed reversal (condition.cpp:2561-2563).
        val player = newPlayer()
        val target = FakeConditionTarget(baseSpeed = 220)
        player.conditionTarget = target
        val haste = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_HASTE, 1_000, param = 100, now = { clock },
        )!!
        player.addCondition(haste)
        assertThat(target.speedChanges).containsExactly(100)

        clock += 2_000
        player.executeConditions(1_000)

        assertThat(target.speedChanges).containsExactly(100, -100)
        assertThat(player.getCondition(ConditionType.CONDITION_HASTE)).isNull()
    }

    @Test
    fun `removeCondition by type removes every condition of that type`() {
        // Creature::removeCondition(type) (creature.cpp:1412-1433).
        val player = newPlayer()
        val first = generic(ConditionType.CONDITION_DRUNK, 10_000, subId = 1)
        val second = generic(ConditionType.CONDITION_DRUNK, 10_000, subId = 2)
        val other = generic(ConditionType.CONDITION_INFIGHT, 10_000)
        player.addCondition(first)
        player.addCondition(second)
        player.addCondition(other)

        player.removeCondition(ConditionType.CONDITION_DRUNK)

        assertThat(player.getConditionsByType(ConditionType.CONDITION_DRUNK)).isEmpty()
        assertThat(player.getCondition(ConditionType.CONDITION_INFIGHT)).isSameAs(other)
    }

    @Test
    fun `removeCondition by type and id only removes the matching slot`() {
        // Creature::removeCondition(type, id) (creature.cpp:1435-1467).
        val player = newPlayer()
        val default = generic(ConditionType.CONDITION_DRUNK, 10_000, conditionId = ConditionId.CONDITIONID_DEFAULT)
        val head = generic(ConditionType.CONDITION_DRUNK, 10_000, conditionId = ConditionId.CONDITIONID_HEAD)
        player.addCondition(default)
        player.addCondition(head)

        player.removeCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_DEFAULT)

        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_DEFAULT)).isNull()
        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_HEAD))
            .isSameAs(head)
    }

    @Test
    fun `removeCondition by instance removes only that instance`() {
        // Creature::removeCondition(condition) (creature.cpp:1486-1498).
        val player = newPlayer()
        val stored = generic(ConditionType.CONDITION_DRUNK, 10_000, subId = 1)
        val sibling = generic(ConditionType.CONDITION_DRUNK, 10_000, subId = 2)
        val other = generic(ConditionType.CONDITION_INFIGHT, 10_000)
        player.addCondition(stored)
        player.addCondition(sibling)
        player.addCondition(other)

        player.removeCondition(stored)

        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_DEFAULT, 1)).isNull()
        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK, ConditionId.CONDITIONID_DEFAULT, 2))
            .isSameAs(sibling)
        assertThat(player.getCondition(ConditionType.CONDITION_INFIGHT)).isSameAs(other)
    }

    @Test
    fun `hasCondition respects endTime before the next tick`() {
        // Creature::hasCondition (creature.cpp:1585-1605): the stored condition is
        // still present, but its window already closed.
        val player = newPlayer()
        player.addCondition(generic(ConditionType.CONDITION_DRUNK, 1_000))

        clock += 2_000

        assertThat(player.hasCondition(ConditionType.CONDITION_DRUNK)).isFalse()
        assertThat(player.getCondition(ConditionType.CONDITION_DRUNK)).isNotNull()
    }
}
