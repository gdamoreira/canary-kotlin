package org.opentibia.canary.lua.bindings.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.systems.imbuement.BaseImbuement
import org.opentibia.canary.systems.imbuement.CategoryImbuement
import org.opentibia.canary.systems.imbuement.Imbuement
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * Transcript-style tests for the `Imbuement` Lua binding library, mirroring
 * `src/lua/functions/items/imbuement_functions.cpp`.
 */
class ImbuementFunctionsTest {

    private class FakeImbuementOperations(
        private val imbuements: Imbuements = Imbuements(),
        private val baseLookup: (Imbuement) -> BaseImbuement? = { imbuements.getBaseByID(it.baseId) },
        private val categoryLookup: (Imbuement) -> CategoryImbuement? = { imbuements.getCategoryByID(it.category) },
    ) : ImbuementOperations {
        override fun getImbuement(id: Int): Imbuement? = imbuements.getImbuement(id)

        override fun getBase(imbuement: Imbuement): BaseImbuement? = baseLookup(imbuement)

        override fun getCategory(imbuement: Imbuement): CategoryImbuement? = categoryLookup(imbuement)
    }

    private fun environment(operations: ImbuementOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { ImbuementFunctions.install(it, operations) }

    private fun imbuements(): Imbuements = Imbuements().apply {
        assertThat(loadFromXml(XML)).isTrue()
    }

    @Test
    fun `Imbuement constructor resolves the registry entry and returns nil for an unknown id`() {
        val environment = environment(FakeImbuementOperations(imbuements()))

        environment.loadChunk("imbuement = Imbuement(1)")
        environment.loadChunk("missing = Imbuement(99)")

        assertThat(environment.getGlobal("imbuement").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `Imbuement accessors mirror the domain values`() {
        val environment = environment(FakeImbuementOperations(imbuements()))

        environment.loadChunk(
            """
            imbuement = Imbuement(1)
            id = imbuement:getId()
            name = imbuement:getName()
            premium = imbuement:isPremium()
            damage = imbuement:getElementDamage()
            combat = imbuement:getCombatType()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("id").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Scorch")
        assertThat(environment.getGlobal("premium").toboolean()).isTrue()
        assertThat(environment.getGlobal("damage").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("combat").toint()).isEqualTo(CombatType.FIREDAMAGE.value)
    }

    @Test
    fun `Imbuement getItems returns the Canary itemid-keyed table`() {
        val environment = environment(FakeImbuementOperations(imbuements()))

        environment.loadChunk(
            """
            imbuement = Imbuement(1)
            items = imbuement:getItems()
            count = items[9636].count
            itemid = items[9636].itemid
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("itemid").toint()).isEqualTo(9636)
    }

    @Test
    fun `Imbuement getBase renders the Canary base fields`() {
        val environment = environment(FakeImbuementOperations(imbuements()))

        environment.loadChunk(
            """
            base = Imbuement(1):getBase()
            id = base.id
            name = base.name
            price = base.price
            protection = base.protection
            percent = base.percent
            removeCost = base.removeCost
            duration = base.duration
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("id").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Basic")
        assertThat(environment.getGlobal("price").toint()).isEqualTo(5_000)
        assertThat(environment.getGlobal("protection").toint()).isEqualTo(10_000)
        assertThat(environment.getGlobal("percent").toint()).isEqualTo(90)
        assertThat(environment.getGlobal("removeCost").toint()).isEqualTo(15_000)
        assertThat(environment.getGlobal("duration").toint()).isEqualTo(72_000)
    }

    @Test
    fun `Imbuement getCategory renders the Canary category fields`() {
        val environment = environment(FakeImbuementOperations(imbuements()))

        environment.loadChunk(
            """
            category = Imbuement(1):getCategory()
            id = category.id
            name = category.name
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("id").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Elemental Damage")
    }

    @Test
    fun `Imbuement getBase and getCategory return nil when the registry misses them`() {
        val environment = environment(
            FakeImbuementOperations(
                imbuements(),
                baseLookup = { null },
                categoryLookup = { null },
            ),
        )

        environment.loadChunk(
            """
            base = Imbuement(1):getBase()
            category = Imbuement(1):getCategory()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("base").isnil()).isTrue()
        assertThat(environment.getGlobal("category").isnil()).isTrue()
    }

    @Test
    fun `Imbuement methods are registered on the Imbuement metatable`() {
        val environment = environment(FakeImbuementOperations(imbuements()))

        environment.loadChunk(
            "checks = (rawgetmetatable('Imbuement').__metatable.getName ~= nil) and " +
                "(rawgetmetatable('Imbuement').__metatable.getId ~= nil) and " +
                "(rawgetmetatable('Imbuement').__metatable.getItems ~= nil) and " +
                "(rawgetmetatable('Imbuement').__metatable.getBase ~= nil) and " +
                "(rawgetmetatable('Imbuement').__metatable.getCategory ~= nil) and " +
                "(rawgetmetatable('Imbuement').__metatable.isPremium ~= nil) and " +
                "(rawgetmetatable('Imbuement').__metatable.getElementDamage ~= nil) and " +
                "(rawgetmetatable('Imbuement').__metatable.getCombatType ~= nil) and " +
                "(rawgetmetatable('Imbuement').__eq ~= nil) and " +
                "(Imbuement ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    private companion object {
        const val XML = """
            <imbuements>
                <base id="1" name="Basic" price="5000" protectionPrice="10000" removecost="15000" duration="72000" percent="90" />
                <category id="0" name="Elemental Damage" agressive="1" />
                <imbuement base="1" iconid="1" premium="1" category="0" name="Scorch">
                    <item key="item" value="9636" count="25" />
                    <effect key="effect" type="damage" combat="fire" value="10" />
                </imbuement>
            </imbuements>
        """
    }
}
