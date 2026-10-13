package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `Condition::createCondition` (`src/creatures/combat/condition.cpp:343-412`)
 * factory parity for the types that share `ConditionGeneric`, the persistence
 * flag rules and the `ConditionFeared` branch.
 */
class ConditionFactoryParityTest {

    private val genericFamily = listOf(
        ConditionType.CONDITION_ROOTED,
        ConditionType.CONDITION_INFIGHT,
        ConditionType.CONDITION_DRUNK,
        ConditionType.CONDITION_EXHAUST,
        ConditionType.CONDITION_EXHAUST_COMBAT,
        ConditionType.CONDITION_EXHAUST_HEAL,
        ConditionType.CONDITION_MUTED,
        ConditionType.CONDITION_CHANNELMUTEDTICKS,
        ConditionType.CONDITION_YELLTICKS,
        ConditionType.CONDITION_POWERLESS,
        ConditionType.CONDITION_PACIFIED,
    )

    @Test
    fun `factory creates a generic condition for the drunk family`() {
        for (type in genericFamily) {
            val condition = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, type, 1000)

            assertThat(condition).`as`("factory result for %s", type).isInstanceOf(ConditionGeneric::class.java)
            assertThat(condition!!.type).isEqualTo(type)
        }
    }

    @Test
    fun `factory creates a generic condition for bakragore and goshnar taint`() {
        val bakragore = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_BAKRAGORE, 1000)
        assertThat(bakragore).isInstanceOf(ConditionGeneric::class.java)

        val taint = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_GOSHNARTAINT, 1000)
        assertThat(taint).isInstanceOf(ConditionGeneric::class.java)
    }

    @Test
    fun `plain generic family ignores the persistent flag like Canary`() {
        // condition.cpp:390-401 does not forward initSubId's neighbour: only
        // CONDITION_BAKRAGORE receives isPersistent (condition.cpp:403-404).
        for (type in genericFamily + ConditionType.CONDITION_GOSHNARTAINT) {
            val condition = Condition.createCondition(
                ConditionId.CONDITIONID_COMBAT,
                type,
                ticks = 1000,
                isPersistent = true,
            )

            assertThat((condition as ConditionGeneric).isPersistentFlag)
                .`as`("persistent flag for %s", type)
                .isFalse()
        }
    }

    @Test
    fun `bakragore propagates the persistent flag like Canary`() {
        val condition = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_BAKRAGORE,
            ticks = 1000,
            isPersistent = true,
        )

        assertThat((condition as ConditionGeneric).isPersistentFlag).isTrue()
    }

    @Test
    fun `factory creates the feared condition`() {
        // condition.cpp:384-385: CONDITION_FEARED -> ConditionFeared.
        val feared = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_FEARED,
            ticks = 1000,
        )

        assertThat(feared).isInstanceOf(ConditionFeared::class.java)
        assertThat(feared!!.type).isEqualTo(ConditionType.CONDITION_FEARED)
    }

    @Test
    fun `factory keeps returning null for types without a Canary class`() {
        // condition.cpp:409-410 `default: return nullptr`; CONDITION_AGONY has
        // no condition class in Canary either.
        assertThat(
            Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_AGONY, 1000),
        ).isNull()
        assertThat(
            Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_NONE, 1000),
        ).isNull()
    }
}
