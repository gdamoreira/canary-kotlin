package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.AreaCombat
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.FormulaType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.LuaVariantType
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.bindings.core.pushPosition

/**
 * Transcript-style tests for the Canary `Combat` binding group
 * (src/lua/functions/creatures/combat/combat_functions.cpp). Each test installs
 * [CombatFunctions] with a fake [CombatOperations] seam and drives the exact Canary
 * method names through real Lua chunks.
 */
class CombatFunctionsBindingsTest {

    private class FakeCombatOps : CombatOperations {
        var currentScriptId = CombatFunctions.EVENT_ID_LOADING
        val areas = linkedMapOf<Int, AreaCombat>()
        var executed: Triple<LuaCombat, Any, org.opentibia.canary.lua.runtime.LuaVariant>? = null
        var executeResult = true

        override fun getScriptId(): Int = currentScriptId

        override fun getAreaObject(id: Int): AreaCombat? = areas[id]

        override fun execute(combat: LuaCombat, creature: Any, variant: org.opentibia.canary.lua.runtime.LuaVariant): Boolean {
            executed = Triple(combat, creature, variant)
            return executeResult
        }
    }

    private fun installed(): Pair<LuaEnvironment, FakeCombatOps> {
        val environment = LuaEnvironment().apply { initState() }
        val operations = FakeCombatOps()
        CombatFunctions.install(environment, operations)
        return environment to operations
    }

    private fun state(environment: LuaEnvironment, name: String): LuaCombat =
        environment.getGlobal(name).touserdata() as LuaCombat

    private fun wrapPlayer(environment: LuaEnvironment): Player {
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        environment.globals.set("creature", environment.userdata.wrap("Player", player))
        return player
    }

    @Test
    fun `Combat execute forwards the creature and parsed variant to the seam`() {
        val (environment, operations) = installed()
        val player = wrapPlayer(environment)

        environment.loadChunk(
            """
            c = Combat()
            v = Variant(42)
            result = c:execute(creature, v)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("result").toboolean()).isTrue()
        val (combat, creature, variant) = operations.executed!!
        assertThat(combat).isSameAs(state(environment, "c"))
        assertThat(creature).isSameAs(player)
        assertThat(variant.type).isEqualTo(LuaVariantType.NUMBER)
        assertThat(variant.number).isEqualTo(42)
    }

    @Test
    fun `Combat execute rejects non creature userdata and follows the seam result`() {
        val (environment, operations) = installed()
        wrapPlayer(environment)
        environment.globals.set("position", pushPosition(environment, Position(1, 2, 3)))

        environment.loadChunk(
            """
            c = Combat()
            bad = c:execute(42, Variant(1))
            wrongType = c:execute(position, Variant(1))
            """.trimIndent()
        )

        assertThat(environment.getGlobal("bad").toboolean()).isFalse()
        assertThat(environment.getGlobal("wrongType").toboolean()).isFalse()
        assertThat(operations.executed).isNull()

        operations.executeResult = false
        environment.loadChunk("lateResult = c:execute(creature, Variant(1))")
        assertThat(environment.getGlobal("lateResult").toboolean()).isFalse()
    }

    @Test
    fun `Combat setParameter maps every CombatParam_t slot`() {
        val (environment, operations) = installed()
        wrapPlayer(environment)

        environment.loadChunk(
            """
            c = Combat()
            type = c:setParameter(0, 2)
            effect = c:setParameter(1, 15)
            distance = c:setParameter(2, 16)
            shield = c:setParameter(3, true)
            armor = c:setParameter(4, true)
            topMost = c:setParameter(5, false)
            createItem = c:setParameter(6, 1234)
            aggressive = c:setParameter(7, false)
            dispel = c:setParameter(8, 4)
            useCharges = c:setParameter(9, true)
            castSound = c:setParameter(10, 30)
            impactSound = c:setParameter(11, 31)
            chainEffect = c:setParameter(12, 32)
            invalid = c:setParameter(99, 1)
            c:execute(creature, Variant(1))
            """.trimIndent()
        )

        val params = state(environment, "c").params
        assertThat(params.combatType).isEqualTo(CombatType.EARTHDAMAGE)
        assertThat(params.impactEffect).isEqualTo(15)
        assertThat(params.distanceEffect).isEqualTo(16)
        assertThat(params.blockedByShield).isTrue()
        assertThat(params.blockedByArmor).isTrue()
        assertThat(params.targetCasterOrTopMost).isFalse()
        assertThat(params.itemId).isEqualTo(1234)
        assertThat(params.aggressive).isFalse()
        assertThat(params.dispelType).isEqualTo(ConditionType.CONDITION_BLEEDING)
        assertThat(params.useCharges).isTrue()
        assertThat(params.soundCastEffect).isEqualTo(30)
        assertThat(params.soundImpactEffect).isEqualTo(31)
        assertThat(params.chainEffect).isEqualTo(32)
        assertThat(environment.getGlobal("invalid").toboolean()).isFalse()
        assertThat(operations.executed).isNotNull
    }

    @Test
    fun `Combat setFormula stores the formula type and coefficients`() {
        val (environment, _) = installed()
        wrapPlayer(environment)

        environment.loadChunk(
            """
            c = Combat()
            result = c:setFormula(3, 1, 2, 3, 4)
            c:execute(creature, Variant(1))
            """.trimIndent()
        )

        assertThat(environment.getGlobal("result").toboolean()).isTrue()
        val combat = state(environment, "c")
        assertThat(combat.formulaType).isEqualTo(FormulaType.DAMAGE)
        assertThat(combat.mina).isEqualTo(1.0)
        assertThat(combat.minb).isEqualTo(2.0)
        assertThat(combat.maxa).isEqualTo(3.0)
        assertThat(combat.maxb).isEqualTo(4.0)
    }

    @Test
    fun `Combat setArea requires the loading script id and a registered area`() {
        val (environment, operations) = installed()

        environment.loadChunk("c = Combat()")
        environment.loadChunk("missing = c:setArea(7)")
        assertThat(environment.getGlobal("missing").isnil()).isTrue()

        operations.areas[7] = AreaCombat()
        environment.loadChunk("ok = c:setArea(7)")
        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(state(environment, "c").area).isNotNull()
        assertThat(state(environment, "c").area).isNotSameAs(operations.areas[7])

        operations.currentScriptId = 0
        environment.loadChunk("notLoading = c:setArea(7)")
        assertThat(environment.getGlobal("notLoading").isnil()).isTrue()
    }

    @Test
    fun `Combat addCondition clones the condition into the params`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            condition = Condition(1)
            c = Combat()
            added = c:addCondition(condition)
            invalid = c:addCondition(42)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("invalid").isnil()).isTrue()
        val condition = environment.getGlobal("condition").touserdata()
        val conditions = state(environment, "c").params.conditionList
        assertThat(conditions).hasSize(1)
        assertThat(conditions.single()).isNotSameAs(condition)
    }

    @Test
    fun `Combat setCallback resolves the Lua function by name`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            function myCallback() return true end
            c = Combat()
            ok = c:setCallback(0, "myCallback")
            missing = c:setCallback(0, "nothing")
            invalid = c:setCallback(99, "myCallback")
            """.trimIndent()
        )

        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(environment.getGlobal("missing").toboolean()).isFalse()
        assertThat(environment.getGlobal("invalid").isnil()).isTrue()
        assertThat(state(environment, "c").callbacks).hasSize(1)
    }

    @Test
    fun `Combat setOrigin writes the params origin`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            c = Combat()
            result = c:setOrigin(3)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("result").toboolean()).isTrue()
        assertThat(state(environment, "c").params.origin).isEqualTo(CombatOrigin.MELEE)
    }
}
