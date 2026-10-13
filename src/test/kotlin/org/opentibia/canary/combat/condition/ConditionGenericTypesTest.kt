package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `ConditionGeneric` (`src/creatures/combat/condition.cpp:563-653`) behaviour
 * for the drunk/muted/channel-muted/pacified/rooted family whose Canary
 * `createCondition` branch shares this class.
 */
class ConditionGenericTypesTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    private fun generic(
        type: ConditionType,
        ticks: Int = 1000,
        subId: Int = 0,
        buff: Boolean = false,
    ) = ConditionGeneric(ConditionId.CONDITIONID_COMBAT, type, ticks, buff, subId, now = now)

    @Test
    fun `muted is not removable on death`() {
        // Condition::isRemovableOnDeath (`condition.cpp:500-505`).
        assertThat(generic(ConditionType.CONDITION_MUTED).isRemovableOnDeath()).isFalse()
    }

    @Test
    fun `rooted and powerless carry their icons`() {
        assertThat(generic(ConditionType.CONDITION_ROOTED).getIcons()).containsExactly(PlayerIcon.Rooted)
        assertThat(generic(ConditionType.CONDITION_POWERLESS).getIcons()).containsExactly(PlayerIcon.Powerless)
    }

    @Test
    fun `muted channel-muted pacified and yell ticks carry no icon`() {
        // ConditionGeneric::getIcons (`condition.cpp:588-649`) only inserts an
        // icon for a fixed type list; these four fall through.
        for (type in listOf(
            ConditionType.CONDITION_MUTED,
            ConditionType.CONDITION_CHANNELMUTEDTICKS,
            ConditionType.CONDITION_PACIFIED,
            ConditionType.CONDITION_YELLTICKS,
        )) {
            assertThat(generic(type).getIcons()).`as`("icons for %s", type).isEmpty()
        }
    }

    @Test
    fun `generic stacking refreshes ticks and ignores shorter durations`() {
        val active = generic(ConditionType.CONDITION_DRUNK, ticks = 5000)
        active.startCondition(null)

        active.addCondition(null, generic(ConditionType.CONDITION_DRUNK, ticks = 10000))
        assertThat(active.ticks).isEqualTo(10000)

        active.addCondition(null, generic(ConditionType.CONDITION_DRUNK, ticks = 1000))
        assertThat(active.ticks).isEqualTo(10000)
    }

    @Test
    fun `clone keeps the identity and duration of the generic condition`() {
        val condition = generic(ConditionType.CONDITION_PACIFIED, ticks = 4000, subId = 3)

        val clone = condition.clone() as ConditionGeneric

        assertThat(clone).isNotSameAs(condition)
        assertThat(clone.id).isEqualTo(condition.id)
        assertThat(clone.type).isEqualTo(condition.type)
        assertThat(clone.subId).isEqualTo(3)
        assertThat(clone.ticks).isEqualTo(4000)
    }

    @Test
    fun `exhaust variants are distinct generic types`() {
        for (type in listOf(
            ConditionType.CONDITION_EXHAUST,
            ConditionType.CONDITION_EXHAUST_COMBAT,
            ConditionType.CONDITION_EXHAUST_HEAL,
        )) {
            val condition = generic(type)
            assertThat(condition.type).isEqualTo(type)
            assertThat(condition.getIcons()).isEmpty()
        }
    }
}
