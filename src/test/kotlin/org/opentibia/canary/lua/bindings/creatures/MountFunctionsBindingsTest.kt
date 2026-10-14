package org.opentibia.canary.lua.bindings.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.systems.mount.Mount
import org.opentibia.canary.systems.mount.Mounts

/**
 * Transcript-style tests for the Canary `Mount` metatable ported from
 * src/lua/functions/creatures/player/mount_functions.cpp.
 */
class MountFunctionsBindingsTest {

    private val bear = Mount(id = 1, clientId = 10, name = "Bear", speed = 10, premium = true, type = "normal")

    private class FakeMountOperations : MountOperations {
        var byNumber: (Int) -> Any? = { null }
        var byName: (String) -> Any? = { null }
        var lastNumber: Int? = null
        var lastName: String? = null

        override fun create(idOrName: Any): Any? = when (idOrName) {
            is Number -> {
                lastNumber = idOrName.toInt()
                byNumber(idOrName.toInt())
            }

            is String -> {
                lastName = idOrName
                byName(idOrName)
            }

            else -> null
        }

        override fun getName(mount: Any): String? = (mount as? Mount)?.name

        override fun getId(mount: Any): Int? = (mount as? Mount)?.id

        override fun getClientId(mount: Any): Int? = (mount as? Mount)?.clientId

        override fun getSpeed(mount: Any): Int? = (mount as? Mount)?.speed
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakeMountOperations = FakeMountOperations()): Pair<LuaEnvironment, FakeMountOperations> {
        val environment = env()
        MountFunctions.install(environment, fake)
        return environment to fake
    }

    @Test
    fun `Mount registers the Canary global and method names`() {
        val environment = env()
        val methods = listOf("getName", "getId", "getClientId", "getSpeed")
        val checks = methods.joinToString(" and ") { "(rawgetmetatable('Mount').__metatable.$it ~= nil)" }
        environment.loadChunk("callable = (type(Mount) == 'table') and (getmetatable(Mount).__call ~= nil)\nmethods = $checks")

        assertThat(environment.getGlobal("callable").toboolean()).isTrue()
        assertThat(environment.getGlobal("methods").toboolean()).isTrue()
    }

    @Test
    fun `Mount factory resolves by numeric id and by name`() {
        val (environment, fake) = installed()
        fake.byNumber = { id -> bear.takeIf { it.id == id } }
        fake.byName = { name -> bear.takeIf { it.name.equals(name, ignoreCase = true) } }
        environment.loadChunk(
            """
            byId = Mount(1)
            byName = Mount("Bear")
            byLowerName = Mount("bear")
            unknownId = Mount(9)
            unknownName = Mount("Dragon")
            id = byId:getId()
            clientId = byId:getClientId()
            name = byId:getName()
            speed = byId:getSpeed()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byId").touserdata()).isSameAs(bear)
        assertThat(environment.getGlobal("byName").touserdata()).isSameAs(bear)
        assertThat(environment.getGlobal("byLowerName").touserdata()).isSameAs(bear)
        assertThat(environment.getGlobal("unknownId").isnil()).isTrue()
        assertThat(environment.getGlobal("unknownName").isnil()).isTrue()
        assertThat(fake.lastNumber).isEqualTo(9)
        assertThat(fake.lastName).isEqualTo("Dragon")
        assertThat(environment.getGlobal("id").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("clientId").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Bear")
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(10)
    }

    @Test
    fun `Mount factory pushes nil for a non number and non string argument`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            boolean = Mount(true)
            table = Mount({})
            missing = Mount()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("boolean").isnil()).isTrue()
        assertThat(environment.getGlobal("table").isnil()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `World Mount resolves the Mounts registry by id and name`() {
        val mounts = Mounts()
        mounts.loadFromXml(
            """
            <mounts>
                <mount id="1" clientid="10" name="Bear" speed="10" premium="yes" type="normal"/>
                <mount id="2" clientid="11" name="Wolf" speed="20" premium="no" type="normal"/>
            </mounts>
            """.trimIndent(),
        )

        val environment = env()
        MountFunctions.install(environment, WorldMountOperations(mounts))
        environment.loadChunk(
            """
            byId = Mount(2)
            byName = Mount("Bean")
            unknown = Mount(9)
            name = byId:getName()
            speed = byId:getSpeed()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byId").touserdata()).isSameAs(mounts.getMountByID(2))
        assertThat(environment.getGlobal("byName").isnil()).isTrue()
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Wolf")
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(20)
    }
}
