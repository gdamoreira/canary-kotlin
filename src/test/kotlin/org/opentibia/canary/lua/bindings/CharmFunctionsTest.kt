package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptError

/**
 * Transcript-style tests for the Canary `Charm` binding group, ported from
 * src/lua/functions/creatures/monster/charm_functions.cpp. The C++ constructor always
 * leaves `nil` on the stack (the shared userdata is pushed and then shadowed by an
 * unconditional `lua_pushnil`), which this port reproduces.
 */
class CharmFunctionsTest {

    private class FakeOps : CharmOperations {
        val requested = mutableListOf<Int>()
        val charms = mutableMapOf<Int, Any>()

        override fun charmById(id: Int): Any? {
            requested += id
            return charms[id]
        }
    }

    private fun installed(fake: FakeOps = FakeOps()): Pair<LuaEnvironment, FakeOps> {
        val environment = LuaEnvironment().apply { initState() }
        CharmFunctions.install(environment, fake)
        return environment to fake
    }

    @Test
    fun `Charm constructor always returns nil like the C++ stack shadow`() {
        val (environment, fake) = installed()
        fake.charms[1] = CharmData(name = "Wound")
        environment.loadChunk("charm = Charm(1)")
        environment.loadChunk("missing = Charm(99)")

        assertThat(environment.getGlobal("charm").isnil()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(fake.requested).containsExactly(1, 99)
    }

    @Test
    fun `Charm string and boolean attributes round-trip`() {
        val (environment, _) = installed()
        val charm = CharmData()
        environment.globals.set("charm", environment.userdata.wrap("Charm", charm))

        environment.loadChunk(
            """
            name = charm:name()
            setName = charm:name('Wound')
            description = charm:description()
            setDescription = charm:description('wounds the target')
            cancel = charm:messageCancel('cancelled')
            log = charm:messageServerLog()
            setLog = charm:messageServerLog(true)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("name").tojstring()).isEmpty()
        assertThat(environment.getGlobal("setName").toboolean()).isTrue()
        assertThat(environment.getGlobal("description").tojstring()).isEmpty()
        assertThat(environment.getGlobal("setDescription").toboolean()).isTrue()
        assertThat(environment.getGlobal("cancel").toboolean()).isTrue()
        assertThat(environment.getGlobal("log").toboolean()).isFalse()
        assertThat(environment.getGlobal("setLog").toboolean()).isTrue()
        assertThat(charm.name).isEqualTo("Wound")
        assertThat(charm.description).isEqualTo("wounds the target")
        assertThat(charm.cancelMessage).isEqualTo("cancelled")
        assertThat(charm.logMessage).isTrue()
    }

    @Test
    fun `Charm numeric attributes round-trip`() {
        val (environment, _) = installed()
        val charm = CharmData()
        environment.globals.set("charm", environment.userdata.wrap("Charm", charm))

        environment.loadChunk(
            """
            category = charm:category()
            setCategory = charm:category(1)
            type = charm:type()
            setType = charm:type(2)
            damage = charm:damageType()
            setDamage = charm:damageType(11)
            percentage = charm:percentage()
            setPercentage = charm:percentage(7.5)
            effect = charm:effect()
            setEffect = charm:effect(13)
            cast = charm:castSound()
            setCast = charm:castSound(3)
            impact = charm:impactSound()
            setImpact = charm:impactSound(5)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("category").toint()).isZero()
        assertThat(environment.getGlobal("setCategory").toboolean()).isTrue()
        assertThat(environment.getGlobal("type").toint()).isZero()
        assertThat(environment.getGlobal("setType").toboolean()).isTrue()
        assertThat(environment.getGlobal("damage").toint()).isEqualTo(255)
        assertThat(environment.getGlobal("setDamage").toboolean()).isTrue()
        assertThat(environment.getGlobal("percentage").todouble()).isZero()
        assertThat(environment.getGlobal("setPercentage").toboolean()).isTrue()
        assertThat(environment.getGlobal("effect").toint()).isZero()
        assertThat(environment.getGlobal("setEffect").toboolean()).isTrue()
        assertThat(environment.getGlobal("cast").toint()).isZero()
        assertThat(environment.getGlobal("setCast").toboolean()).isTrue()
        assertThat(environment.getGlobal("impact").toint()).isZero()
        assertThat(environment.getGlobal("setImpact").toboolean()).isTrue()
        assertThat(charm.category).isEqualTo(1)
        assertThat(charm.type).isEqualTo(2)
        assertThat(charm.damageType).isEqualTo(11)
        assertThat(charm.percent).isEqualTo(7.5)
        assertThat(charm.effect).isEqualTo(13)
        assertThat(charm.soundCastEffect).isEqualTo(3)
        assertThat(charm.soundImpactEffect).isEqualTo(5)
    }

    @Test
    fun `Charm points round-trip through a table`() {
        val (environment, _) = installed()
        val charm = CharmData(points = mutableListOf(1, 2))
        environment.globals.set("charm", environment.userdata.wrap("Charm", charm))

        environment.loadChunk(
            """
            before = charm:points()
            replaced = charm:points({5, 10, 15})
            after = charm:points()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("before").get(1).toint()).isEqualTo(1)
        assertThat(environment.getGlobal("before").get(2).toint()).isEqualTo(2)
        assertThat(environment.getGlobal("replaced").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").get(1).toint()).isEqualTo(5)
        assertThat(environment.getGlobal("after").get(3).toint()).isEqualTo(15)
        assertThat(charm.points).containsExactly(5, 10, 15)
    }

    @Test
    fun `Charm points reject a non-table argument`() {
        val (environment, _) = installed()
        environment.globals.set("charm", environment.userdata.wrap("Charm", CharmData()))

        assertThatThrownBy { environment.loadChunk("charm:points(5)") }
            .isInstanceOf(ScriptError::class.java)
            .hasMessageContaining("Expected a table for points.")
    }

    @Test
    fun `Charm chance stores a leading zero before the table values`() {
        val (environment, _) = installed()
        val charm = CharmData(chance = mutableListOf(0.0, 1.5))
        environment.globals.set("charm", environment.userdata.wrap("Charm", charm))

        environment.loadChunk(
            """
            before = charm:chance()
            replaced = charm:chance({0.25, 0.5})
            after = charm:chance()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("before").get(1).todouble()).isZero()
        assertThat(environment.getGlobal("before").get(2).todouble()).isEqualTo(1.5)
        assertThat(environment.getGlobal("replaced").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").get(1).todouble()).isZero()
        assertThat(environment.getGlobal("after").get(2).todouble()).isEqualTo(0.25)
        assertThat(environment.getGlobal("after").get(3).todouble()).isEqualTo(0.5)
        assertThat(charm.chance).containsExactly(0.0, 0.25, 0.5)
    }

    @Test
    fun `Charm chance rejects a non-table argument`() {
        val (environment, _) = installed()
        environment.globals.set("charm", environment.userdata.wrap("Charm", CharmData()))

        assertThatThrownBy { environment.loadChunk("charm:chance('x')") }
            .isInstanceOf(ScriptError::class.java)
            .hasMessageContaining("Expected a table for chance.")
    }
}
