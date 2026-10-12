package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionOutfitTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    @Test
    fun `start applies the configured outfit and end restores the default`() {
        val target = FakeConditionTarget()
        target.defaultOutfitValue = Outfit(lookType = 136)
        val outfit = ConditionOutfit(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            ticks = 10000,
            now = now,
        )
        outfit.setOutfit(Outfit(lookType = 42, lookHead = 3))

        assertThat(outfit.startCondition(target)).isTrue()
        assertThat(target.outfitValue).isEqualTo(Outfit(lookType = 42, lookHead = 3))

        outfit.endCondition(target)
        assertThat(target.outfitValue).isEqualTo(Outfit(lookType = 136))
    }

    @Test
    fun `warn unsafe scripts blocks an unregistered look type`() {
        val target = FakeConditionTarget()
        val outfit = ConditionOutfit(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            ticks = 10000,
            warnUnsafeScripts = true,
            isLookTypeRegistered = { false },
            now = now,
        )
        outfit.setOutfit(Outfit(lookType = 9999))

        // condition.cpp:2692-2696: unregistered look type blocks the condition.
        assertThat(outfit.startCondition(target)).isFalse()
        assertThat(target.outfitChanges).isEmpty()
        assertThat(outfit.endTime).isEqualTo(0L)
    }

    @Test
    fun `warn unsafe scripts allows a registered look type`() {
        val target = FakeConditionTarget()
        val outfit = ConditionOutfit(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            ticks = 10000,
            warnUnsafeScripts = true,
            isLookTypeRegistered = { it == 42 },
            now = now,
        )
        outfit.setOutfit(Outfit(lookType = 42))

        assertThat(outfit.startCondition(target)).isTrue()
        assertThat(target.outfitValue).isEqualTo(Outfit(lookType = 42))
    }

    @Test
    fun `lazy monster outfit is resolved on start`() {
        val target = FakeConditionTarget()
        val outfit = ConditionOutfit(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            ticks = 10000,
            monsterOutfit = { name -> if (name == "Demon") Outfit(lookType = 35) else null },
            now = now,
        )
        outfit.setLazyMonsterOutfit("Demon")

        // condition.cpp:2698-2706: the zero outfit is replaced by the monster outfit.
        assertThat(outfit.startCondition(target)).isTrue()
        assertThat(target.outfitValue).isEqualTo(Outfit(lookType = 35))
    }

    @Test
    fun `lazy monster outfit fails for a missing monster`() {
        val target = FakeConditionTarget()
        val outfit = ConditionOutfit(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            ticks = 10000,
            monsterOutfit = { null },
            now = now,
        )
        outfit.setLazyMonsterOutfit("Missing")

        assertThat(outfit.startCondition(target)).isFalse()
        assertThat(target.outfitChanges).isEmpty()
    }

    @Test
    fun `add stacks ticks and copies the incoming outfit`() {
        val target = FakeConditionTarget()
        val active = ConditionOutfit(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            ticks = 1000,
            now = now,
        )
        val incoming = ConditionOutfit(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            ticks = 6000,
            now = now,
        )
        incoming.setOutfit(Outfit(lookType = 7, lookTypeEx = 0))

        active.addCondition(target, incoming)

        // condition.cpp:2730-2747.
        assertThat(active.ticks).isEqualTo(6000)
        assertThat(active.monsterName).isEmpty()
        assertThat(target.outfitValue).isEqualTo(Outfit(lookType = 7))
    }

    @Test
    fun `clone preserves outfit and lazy monster name`() {
        val outfit = ConditionOutfit(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            ticks = 10000,
            now = now,
        )
        outfit.setOutfit(Outfit(lookType = 11, lookLegs = 2))
        outfit.setLazyMonsterOutfit("Rat")

        val copy = outfit.clone() as ConditionOutfit
        assertThat(copy.outfit).isEqualTo(Outfit(lookType = 11, lookLegs = 2))
        assertThat(copy.monsterName).isEqualTo("Rat")
    }
}
