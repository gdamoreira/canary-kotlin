package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.bindings.core.pushPosition
import org.opentibia.canary.lua.bindings.core.luaToPosition

/**
 * Transcript-style tests for the Canary `Variant` binding group
 * (src/lua/functions/creatures/combat/variant_functions.cpp). The tests install
 * [VariantFunctions] through [CombatFunctions] and drive the exact Canary method
 * names through real Lua chunks.
 */
class VariantFunctionsBindingsTest {

    private class FakeCombatOps : CombatOperations {
        override fun getScriptId(): Int = CombatFunctions.EVENT_ID_LOADING
        override fun getAreaObject(id: Int): org.opentibia.canary.combat.AreaCombat? = null
        override fun execute(
            combat: LuaCombat,
            creature: Any,
            variant: org.opentibia.canary.lua.runtime.LuaVariant,
        ): Boolean = true
    }

    private class FakeVariantOps : VariantOperations {
        var thing: Any? = null
        var position: Position? = null

        override fun thingPosition(value: Any): Position? {
            thing = value
            return position
        }
    }

    private fun installed(): Pair<LuaEnvironment, FakeVariantOps> {
        val environment = LuaEnvironment().apply { initState() }
        val operations = FakeVariantOps()
        CombatFunctions.install(environment, FakeCombatOps(), operations)
        return environment to operations
    }

    private fun positionOf(environment: LuaEnvironment, name: String): Position =
        luaToPosition(environment.getGlobal(name).get("pos"))!!

    @Test
    fun `Variant number keeps the number and reports zero for the other getters`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            v = Variant(42)
            type = v.type
            number = v:getNumber()
            text = v:getString()
            position = v:getPosition()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("type").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("number").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("text").tojstring()).isEmpty()
        val position = luaToPosition(environment.getGlobal("position"))!!
        assertThat(position).isEqualTo(Position(0, 0, 0))
    }

    @Test
    fun `Variant string keeps the text and reports zero for the number`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            v = Variant("hello")
            type = v.type
            text = v:getString()
            number = v:getNumber()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("type").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("text").tojstring()).isEqualTo("hello")
        assertThat(environment.getGlobal("number").toint()).isEqualTo(0)
    }

    @Test
    fun `Variant table becomes a position variant`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            v = Variant({x = 10, y = 20, z = 7})
            type = v.type
            """.trimIndent()
        )

        assertThat(environment.getGlobal("type").toint()).isEqualTo(2)
        assertThat(positionOf(environment, "v")).isEqualTo(Position(10, 20, 7))
    }

    @Test
    fun `Variant creature userdata becomes a target position through the seam`() {
        val (environment, operations) = installed()
        val player = Player(1, "Hero", 100, 100, position = Position(30, 40, 7))
        operations.position = player.position
        environment.globals.set("creature", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            v = Variant(creature)
            type = v.type
            """.trimIndent()
        )

        assertThat(environment.getGlobal("type").toint()).isEqualTo(3)
        assertThat(operations.thing).isSameAs(player)
        assertThat(positionOf(environment, "v")).isEqualTo(Position(30, 40, 7))
    }

    @Test
    fun `Variant position table keeps the position type`() {
        val (environment, _) = installed()
        environment.globals.set("position", pushPosition(environment, Position(1, 2, 3)))

        environment.loadChunk(
            """
            v = Variant(position)
            type = v.type
            number = v:getNumber()
            text = v:getString()
            """.trimIndent()
        )

        // `VariantFunctions::luaVariantCreate` (variant_functions.cpp:32-34): a table
        // becomes `VARIANT_POSITION` (2) with `pos` read through `Lua::getPosition`.
        assertThat(environment.getGlobal("type").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("number").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("text").tojstring()).isEmpty()
        assertThat(positionOf(environment, "v")).isEqualTo(Position(1, 2, 3))
    }

    @Test
    fun `Variant unknown input keeps the none type`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            v = Variant(true)
            type = v.type
            number = v:getNumber()
            text = v:getString()
            zero = v:getPosition()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("type").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("number").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("text").tojstring()).isEmpty()
        assertThat(luaToPosition(environment.getGlobal("zero"))).isEqualTo(Position(0, 0, 0))
    }


}
