package org.opentibia.canary.lua.bindings.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Town
import org.opentibia.canary.map.Towns

/**
 * Transcript-style tests for the `Town` Lua binding library, mirroring
 * `src/lua/functions/map/town_functions.cpp`.
 */
class TownFunctionsTest {

    private class FakeTownOperations(
        private val towns: Towns = Towns(),
    ) : TownOperations {
        override fun getTown(id: Int): Town? = towns.getTown(id)

        override fun getTown(name: String): Town? = towns.getTown(name)
    }

    private fun environment(operations: TownOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { TownFunctions.install(it, operations) }

    private fun towns(): Towns = Towns().apply {
        addTown(
            3,
            Town(3).apply {
                setName("Thais")
                setTemplePos(Position(100, 100, 7))
            },
        )
        addTown(
            4,
            Town(4).apply {
                setName("Carlin")
                setTemplePos(Position(200, 200, 7))
            },
        )
    }

    @Test
    fun `Town constructor resolves by id and by name and returns nil for unknown values`() {
        val environment = environment(FakeTownOperations(towns()))

        environment.loadChunk(
            """
            byId = Town(3)
            byName = Town('Thais')
            missingId = Town(99)
            missingName = Town('Nowhere')
            idText = byId:getName()
            nameText = byName:getName()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byId").isuserdata()).isTrue()
        assertThat(environment.getGlobal("byName").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missingId").isnil()).isTrue()
        assertThat(environment.getGlobal("missingName").isnil()).isTrue()
        assertThat(environment.getGlobal("idText").tojstring()).isEqualTo("Thais")
        assertThat(environment.getGlobal("nameText").tojstring()).isEqualTo("Thais")
    }

    @Test
    fun `Town accessors mirror the domain values`() {
        val environment = environment(FakeTownOperations(towns()))

        environment.loadChunk(
            """
            town = Town('Carlin')
            id = town:getId()
            name = town:getName()
            temple = town:getTemplePosition():toString()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("id").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Carlin")
        assertThat(environment.getGlobal("temple").tojstring()).isEqualTo("( 200, 200, 7 )")
    }

    @Test
    fun `Town methods are registered on the Town metatable`() {
        val environment = environment(FakeTownOperations(towns()))

        environment.loadChunk(
            "checks = (rawgetmetatable('Town').getId ~= nil) and " +
                "(rawgetmetatable('Town').getName ~= nil) and " +
                "(rawgetmetatable('Town').getTemplePosition ~= nil) and " +
                "(rawgetmetatable('Town').__eq ~= nil) and " +
                "(Town ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }
}
