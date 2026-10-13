package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionOutfit
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the Canary `Condition` binding group
 * (src/lua/functions/creatures/combat/condition_functions.cpp). The tests install
 * [ConditionFunctions] through [CombatFunctions] and drive the exact Canary method
 * names through real Lua chunks.
 */
class ConditionFunctionsBindingsTest {

    private class FakeCombatOps : CombatOperations {
        override fun getScriptId(): Int = CombatFunctions.EVENT_ID_LOADING
        override fun getAreaObject(id: Int): org.opentibia.canary.combat.AreaCombat? = null
        override fun execute(
            combat: LuaCombat,
            creature: Any,
            variant: org.opentibia.canary.lua.runtime.LuaVariant,
        ): Boolean = true
    }

    private fun installed(): LuaEnvironment {
        val environment = LuaEnvironment().apply { initState() }
        CombatFunctions.install(environment, FakeCombatOps(), FakeVariantOps())
        return environment
    }

    private class FakeVariantOps : VariantOperations {
        override fun thingPosition(value: Any): org.opentibia.canary.map.Position? = null
    }

    private fun condition(environment: LuaEnvironment, name: String): Condition =
        environment.getGlobal(name).touserdata() as Condition

    @Test
    fun `Condition constructor honours the type id subId and persists flags`() {
        val environment = installed()

        environment.loadChunk(
            """
            poison = Condition(1)
            fire = Condition(2, 1, 5)
            invalid = Condition(0)
            """.trimIndent()
        )

        val poison = condition(environment, "poison")
        assertThat(poison.id.value).isEqualTo(0)
        assertThat(poison.subId).isEqualTo(0)
        assertThat(poison.type).isEqualTo(ConditionType.CONDITION_POISON)
        assertThat(environment.getGlobal("invalid").isnil()).isTrue()

        val fire = condition(environment, "fire")
        assertThat(fire.id.value).isEqualTo(1)
        assertThat(fire.subId).isEqualTo(5)
        assertThat(fire.type).isEqualTo(ConditionType.CONDITION_FIRE)
    }

    @Test
    fun `Condition getters expose id subId type ticks and endTime`() {
        val environment = installed()

        environment.loadChunk(
            """
            c = Condition(1, 0, 7)
            id = c:getId()
            subId = c:getSubId()
            type = c:getType()
            ticks = c:getTicks()
            endTime = c:getEndTime()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("id").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("subId").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("type").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("ticks").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("endTime").toint()).isEqualTo(0)
    }

    @Test
    fun `Condition setParameter accepts numeric and boolean values`() {
        val environment = installed()

        environment.loadChunk(
            """
            c = Condition(1)
            ticks = c:setParameter(2, 5000)
            buff = c:setParameter(44, true)
            ticksAfter = c:getTicks()
            unknown = c:setParameter(9999, 1)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("ticks").toboolean()).isTrue()
        assertThat(environment.getGlobal("buff").toboolean()).isTrue()
        assertThat(environment.getGlobal("unknown").toboolean()).isTrue()
        assertThat(environment.getGlobal("ticksAfter").toint()).isEqualTo(5000)
        assertThat(condition(environment, "c").isBuff).isTrue()
    }

    @Test
    fun `Condition setTicks restamps the end time`() {
        val environment = installed()

        environment.loadChunk(
            """
            c = Condition(1)
            result = c:setTicks(1000)
            ticks = c:getTicks()
            endTime = c:getEndTime()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("result").toboolean()).isTrue()
        assertThat(environment.getGlobal("ticks").toint()).isEqualTo(1000)
        assertThat(environment.getGlobal("endTime").toint()).isGreaterThan(0)
    }

    @Test
    fun `Condition getIcons returns the PlayerIcon enum names`() {
        val environment = installed()

        environment.loadChunk(
            """
            poison = Condition(1)
            poisonIcons = poison:getIcons()
            first = poisonIcons[1]
            count = #poisonIcons

            infight = Condition(11)
            infightIcons = infight:getIcons()
            infightFirst = infightIcons[1]
            """.trimIndent()
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("first").tojstring()).isEqualTo("Poison")
        assertThat(environment.getGlobal("infightFirst").tojstring()).isEqualTo("Swords")
    }

    @Test
    fun `Condition clone returns a distinct condition with the same state`() {
        val environment = installed()

        environment.loadChunk(
            """
            c = Condition(1, 0, 3)
            c:setTicks(500)
            clone = c:clone()
            cloneType = clone:getType()
            cloneTicks = clone:getTicks()
            same = (clone == c)
            """.trimIndent()
        )

        val original = condition(environment, "c")
        val clone = condition(environment, "clone")
        assertThat(clone).isNotSameAs(original)
        assertThat(environment.getGlobal("cloneType").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("cloneTicks").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("same").toboolean()).isFalse()
    }

    @Test
    fun `Condition delete releases the condition and returns nil`() {
        val environment = installed()

        environment.loadChunk(
            """
            c = Condition(1)
            result = c:delete()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("result").isnil()).isTrue()
    }

    @Test
    fun `Condition setFormula only applies to speed conditions`() {
        val environment = installed()

        environment.loadChunk(
            """
            haste = Condition(5)
            poison = Condition(1)
            applied = haste:setFormula(1, 2, 3, 4)
            rejected = poison:setFormula(1, 2, 3, 4)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("applied").toboolean()).isTrue()
        assertThat(environment.getGlobal("rejected").isnil()).isTrue()
        val speed = condition(environment, "haste") as ConditionSpeed
        assertThat(speed.mina).isEqualTo(1.0f)
        assertThat(speed.minb).isEqualTo(2.0f)
        assertThat(speed.maxa).isEqualTo(3.0f)
        assertThat(speed.maxb).isEqualTo(4.0f)
    }

    @Test
    fun `Condition setOutfit accepts a table and the positional Canary order`() {
        val environment = installed()

        environment.loadChunk(
            """
            tableOutfit = Condition(7)
            fromTable = tableOutfit:setOutfit({
                lookType = 136, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4,
                lookAddons = 5, lookMount = 6, lookMountHead = 7, lookMountBody = 8,
                lookMountLegs = 9, lookMountFeet = 10, lookFamiliarsType = 11,
                lookAura = 12, lookEffect = 13, lookShader = 14,
            })

            positional = Condition(7)
            fromPositional = positional:setOutfit(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)

            invalid = Condition(1)
            rejected = invalid:setOutfit(1, 2, 3, 4, 5, 6)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("fromTable").toboolean()).isTrue()
        assertThat(environment.getGlobal("fromPositional").toboolean()).isTrue()
        assertThat(environment.getGlobal("rejected").isnil()).isTrue()

        val tableOutfit = (condition(environment, "tableOutfit") as ConditionOutfit).outfit
        assertThat(tableOutfit.lookType).isEqualTo(136)
        assertThat(tableOutfit.lookHead).isEqualTo(1)
        assertThat(tableOutfit.lookAddons).isEqualTo(5)
        assertThat(tableOutfit.lookMountFeet).isEqualTo(10)
        assertThat(tableOutfit.lookFamiliarsType).isEqualTo(11)
        assertThat(tableOutfit.lookAura).isEqualTo(12)
        assertThat(tableOutfit.lookEffect).isEqualTo(13)
        assertThat(tableOutfit.lookShader).isEqualTo(14)
        assertThat(tableOutfit.lookWing).isEqualTo(14)

        val positionalOutfit = (condition(environment, "positional") as ConditionOutfit).outfit
        assertThat(positionalOutfit.lookTypeEx).isEqualTo(2)
        assertThat(positionalOutfit.lookType).isEqualTo(3)
        assertThat(positionalOutfit.lookHead).isEqualTo(4)
        assertThat(positionalOutfit.lookBody).isEqualTo(5)
        assertThat(positionalOutfit.lookLegs).isEqualTo(6)
        assertThat(positionalOutfit.lookFeet).isEqualTo(7)
        assertThat(positionalOutfit.lookAddons).isEqualTo(8)
        assertThat(positionalOutfit.lookMount).isEqualTo(9)
        assertThat(positionalOutfit.lookMountHead).isEqualTo(10)
        assertThat(positionalOutfit.lookMountBody).isEqualTo(11)
        assertThat(positionalOutfit.lookMountLegs).isEqualTo(12)
        assertThat(positionalOutfit.lookMountFeet).isEqualTo(13)
        assertThat(positionalOutfit.lookFamiliarsType).isEqualTo(14)
        assertThat(positionalOutfit.lookWing).isEqualTo(15)
        assertThat(positionalOutfit.lookAura).isEqualTo(16)
        assertThat(positionalOutfit.lookEffect).isEqualTo(17)
        assertThat(positionalOutfit.lookShader).isEqualTo(18)
    }

    @Test
    fun `Condition addDamage only applies to damage conditions`() {
        val environment = installed()

        environment.loadChunk(
            """
            damage = Condition(3)
            applied = damage:addDamage(3, 1000, 5)
            haste = Condition(5)
            rejected = haste:addDamage(3, 1000, 5)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("applied").toboolean()).isTrue()
        assertThat(environment.getGlobal("rejected").isnil()).isTrue()
        assertThat(condition(environment, "damage")).isInstanceOf(ConditionDamage::class.java)
    }
}
