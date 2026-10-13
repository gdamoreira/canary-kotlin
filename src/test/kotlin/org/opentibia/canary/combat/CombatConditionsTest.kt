package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import java.util.Random

/**
 * Transcript tests for the `Combat::doCombatDefault` condition-application path.
 *
 * Every expectation is derived from the exact C++ control flow in the cited
 * source lines:
 *  - `Combat::doCombat` (src/creatures/combat/combat.cpp:1469-1483)
 *  - `Combat::doCombatDefault` (src/creatures/combat/combat.cpp:1829-1864)
 *  - `Combat::CombatConditionFunc` / `Combat::CombatNullFunc`
 *    (src/creatures/combat/combat.cpp:1031-1103)
 *  - `Creature::addCombatCondition` (src/creatures/creature.cpp:1397-1410)
 */
class CombatConditionsTest {

    private fun burn(): ConditionDamage {
        val condition = ConditionDamage(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FIRE)
        condition.addDamage(3, time = 1000, value = -5)
        return condition
    }

    @Test
    fun `doCombatDefault clones the params condition onto the target with the caster owner`() {
        // combat.cpp:1079-1089: caster-gated clone, owner stamp, addCombatCondition.
        val caster = FakeCombatTarget(id = 7)
        val target = FakeCombatTarget()
        val condition = burn()
        val params = CombatParams(conditionList = mutableListOf(condition))

        Combat.doCombatDefault(caster, target, params)

        assertThat(target.appliedConditions).hasSize(1)
        val (applied, attackerPlayer) = target.appliedConditions.single()
        assertThat(applied).isNotSameAs(condition)
        assertThat(applied.type).isEqualTo(ConditionType.CONDITION_FIRE)
        assertThat((applied as ConditionDamage).scheduledDamage.map { it.value })
            .containsExactly(-5, -5, -5)
        assertThat(applied.owner).isEqualTo(7)
        assertThat(attackerPlayer).isFalse()

        // The params list keeps the pristine template condition.
        assertThat(condition.owner).isEqualTo(0)
        assertThat(params.conditionList).containsExactly(condition)
    }

    @Test
    fun `doCombat falls through to doCombatDefault when the params have no combat type`() {
        // combat.cpp:1479-1481: COMBAT_NONE runs doCombatDefault and still returns true.
        val target = FakeCombatTarget()
        val condition = burn()
        val params = CombatParams(conditionList = mutableListOf(condition))

        val applied = Combat.doCombat(
            caster = null,
            target = target,
            damage = CombatDamage(),
            params = params,
            random = Random(1),
        )

        assertThat(applied).isTrue()
        assertThat(target.appliedConditions).hasSize(1)
        assertThat(target.appliedConditions.single().first).isNotSameAs(condition)
    }

    @Test
    fun `doCombatDefault skips conditions the target is immune to`() {
        // combat.cpp:1079: `target && !target->isImmune(condition->getType())`
        val target = FakeCombatTarget()
        target.immuneConditionTypes.add(ConditionType.CONDITION_FIRE)

        Combat.doCombatDefault(null, target, CombatParams(conditionList = mutableListOf(burn())))

        assertThat(target.appliedConditions).isEmpty()
    }

    @Test
    fun `doCombatDefault blocks only aggressive self-targeting`() {
        // combat.cpp:1834: canCombat = !aggressive || caster != target
        val aggressiveTarget = FakeCombatTarget()
        Combat.doCombatDefault(
            caster = aggressiveTarget,
            target = aggressiveTarget,
            params = CombatParams(conditionList = mutableListOf(burn())),
        )
        assertThat(aggressiveTarget.appliedConditions).isEmpty()

        val buffTarget = FakeCombatTarget()
        Combat.doCombatDefault(
            caster = buffTarget,
            target = buffTarget,
            params = CombatParams(conditionList = mutableListOf(burn()), aggressive = false),
        )
        assertThat(buffTarget.appliedConditions).hasSize(1)
    }
}
