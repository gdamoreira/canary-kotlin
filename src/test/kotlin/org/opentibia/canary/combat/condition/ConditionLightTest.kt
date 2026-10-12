package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionLightTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    @Test
    fun `start installs the light and derives the change interval`() {
        val target = FakeConditionTarget()
        val light = ConditionLight(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_LIGHT,
            ticks = 10000,
            initLightlevel = 5,
            initLightcolor = 215,
            now = now,
        )

        assertThat(light.startCondition(target)).isTrue()
        // ConditionLight::startCondition -> lightChangeInterval = ticks / level.
        assertThat(light.lightChangeInterval).isEqualTo(2000)
        assertThat(light.internalLightTicks).isEqualTo(0)
        assertThat(target.creatureLightInfo).isEqualTo(LightInfo(5, 215))
        assertThat(target.lightChanges).isEqualTo(1)
    }

    @Test
    fun `execute dims the light one level per change interval`() {
        val target = FakeConditionTarget()
        val light = ConditionLight(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_LIGHT,
            ticks = 10000,
            initLightlevel = 5,
            initLightcolor = 215,
            now = now,
        )
        light.startCondition(target)

        light.executeCondition(target, 1000)
        assertThat(target.creatureLightInfo.level).isEqualTo(5)

        light.executeCondition(target, 1000)
        // internalLightTicks reached 2000 -> one level consumed (condition.cpp:2773-2785).
        assertThat(target.creatureLightInfo.level).isEqualTo(4)
        assertThat(light.internalLightTicks).isEqualTo(0)
        assertThat(target.lightChanges).isEqualTo(2)
    }

    @Test
    fun `end resets to the normal creature light`() {
        val target = FakeConditionTarget()
        val light = ConditionLight(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_LIGHT,
            ticks = 10000,
            initLightlevel = 4,
            initLightcolor = 215,
            now = now,
        )
        light.startCondition(target)

        light.endCondition(target)

        assertThat(target.creatureLightInfo).isEqualTo(LightInfo(0, 0))
        assertThat(target.lightChanges).isEqualTo(2)
    }

    @Test
    fun `add reproduces the stacking numbers and resets the internal ticks`() {
        val target = FakeConditionTarget()
        val active = ConditionLight(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_LIGHT,
            ticks = 10000,
            initLightlevel = 5,
            initLightcolor = 215,
            now = now,
        )

        val incoming = ConditionLight(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_LIGHT,
            ticks = 6000,
            initLightlevel = 2,
            initLightcolor = 100,
            now = now,
        )
        active.addCondition(target, incoming)

        // ConditionLight::addCondition (condition.cpp:2795-2807).
        assertThat(active.ticks).isEqualTo(6000)
        assertThat(active.lightInfo).isEqualTo(LightInfo(2, 100))
        assertThat(active.lightChangeInterval).isEqualTo(3000)
        assertThat(active.internalLightTicks).isEqualTo(0)
        assertThat(target.creatureLightInfo).isEqualTo(LightInfo(2, 100))
    }

    @Test
    fun `setParam follows the Canary light branch including its fall-through`() {
        val light = ConditionLight(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_LIGHT,
            ticks = 10000,
            initLightlevel = 1,
            initLightcolor = 215,
            now = now,
        )

        // condition.cpp:2820-2827: level is clamped to at least 1.
        assertThat(light.setParam(ConditionParam.CONDITION_PARAM_LIGHT_LEVEL, 0)).isTrue()
        assertThat(light.lightInfo.level).isEqualTo(1)
        assertThat(light.setParam(ConditionParam.CONDITION_PARAM_LIGHT_LEVEL, 7)).isTrue()
        assertThat(light.lightInfo.level).isEqualTo(7)

        // condition.cpp:2828-2830.
        assertThat(light.setParam(ConditionParam.CONDITION_PARAM_LIGHT_COLOR, 100)).isTrue()
        assertThat(light.lightInfo.color).isEqualTo(100)

        // Faithful quirk (condition.cpp:2813-2817): a base-handled param returns
        // false even though the base already mutated the state.
        assertThat(light.setParam(ConditionParam.CONDITION_PARAM_TICKS, 500)).isFalse()
        assertThat(light.ticks).isEqualTo(500)
    }

    @Test
    fun `clone preserves the light state`() {
        val light = ConditionLight(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_LIGHT,
            ticks = 10000,
            initLightlevel = 3,
            initLightcolor = 42,
            now = now,
        )
        light.startCondition(FakeConditionTarget())

        val copy = light.clone() as ConditionLight
        assertThat(copy.lightInfo).isEqualTo(LightInfo(3, 42))
        assertThat(copy.ticks).isEqualTo(10000)
    }
}
