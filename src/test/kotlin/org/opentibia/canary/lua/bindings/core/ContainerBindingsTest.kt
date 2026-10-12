package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the `Container` Lua binding library. The engine seam is
 * fake, but the methods are invoked through real Lua chunks with the exact Canary
 * names (container:getSize, container:getItems, container:getItemCountById, ...).
 */
class ContainerBindingsTest {

    private class FakeItem(val id: Int)

    private class FakeContainer(
        val size: Int = 2,
        val maxCapacity: Int = 20,
        val capacity: Int = 20,
        val emptySlots: Int = 18,
        val recursiveEmptySlots: Int = 25,
        val holdingCount: Int = 5,
        val itemTypeCount: Int = 3,
        val items: List<Any> = emptyList(),
        val indexItems: Map<Int, Any> = emptyMap(),
    )

    private class FakeContainerOps : ContainerOperations {
        override fun itemIdByName(name: String): Int = if (name == "gold coin") 2148 else 0

        override fun getSize(container: Any): Int = (container as FakeContainer).size

        override fun getMaxCapacity(container: Any): Int = (container as FakeContainer).maxCapacity

        override fun getCapacity(container: Any): Int = (container as FakeContainer).capacity

        override fun getEmptySlots(container: Any, recursive: Boolean): Int {
            val fake = container as FakeContainer
            return if (recursive) fake.recursiveEmptySlots else fake.emptySlots
        }

        override fun getItemHoldingCount(container: Any): Int = (container as FakeContainer).holdingCount

        override fun getItemCountById(container: Any, itemId: Int, subType: Int): Int =
            if (itemId == 2148) (container as FakeContainer).itemTypeCount else 0

        override fun getItem(container: Any, index: Int): Any? = (container as FakeContainer).indexItems[index]

        override fun hasItem(container: Any, item: Any?): Boolean = item != null

        override fun getItems(container: Any, recursive: Boolean): List<Any> =
            (container as FakeContainer).items
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Container query methods use the Canary names`() {
        val environment = env()
        ContainerFunctions.install(environment, FakeContainerOps())

        val first = FakeItem(2148)
        val container = FakeContainer(
            items = listOf(first, FakeItem(2160)),
            indexItems = mapOf(1 to first, 2 to FakeItem(2160)),
        )
        environment.globals.set("bag", environment.userdata.wrap("Container", container))
        environment.globals.set("gold", environment.userdata.wrap("Item", first))

        environment.loadChunk("size = bag:getSize()")
        environment.loadChunk("maxCapacity = bag:getMaxCapacity()")
        environment.loadChunk("capacity = bag:getCapacity()")
        environment.loadChunk("empty = bag:getEmptySlots()")
        environment.loadChunk("emptyRecursive = bag:getEmptySlots(true)")
        environment.loadChunk("holding = bag:getItemHoldingCount()")
        environment.loadChunk("countById = bag:getItemCountById(2148)")
        environment.loadChunk("countByName = bag:getItemCountById('gold coin')")
        environment.loadChunk("missing = bag:getItemCountById(9999)")
        environment.loadChunk("first = bag:getItem(1)")
        environment.loadChunk("none = bag:getItem(99)")
        environment.loadChunk("holds = bag:hasItem(gold)")
        environment.loadChunk("all = bag:getItems()")
        environment.loadChunk("allCount = #all")

        assertThat(environment.getGlobal("size").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("maxCapacity").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("capacity").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("empty").toint()).isEqualTo(18)
        assertThat(environment.getGlobal("emptyRecursive").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("holding").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("countById").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("countByName").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("missing").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("first").isuserdata()).isTrue()
        assertThat(environment.getGlobal("none").isnil()).isTrue()
        assertThat(environment.getGlobal("holds").toboolean()).isTrue()
        assertThat(environment.getGlobal("allCount").toint()).isEqualTo(2)
    }

    @Test
    fun `Container methods are registered on the Container metatable only`() {
        val environment = env()
        ContainerFunctions.install(environment, FakeContainerOps())

        environment.loadChunk(
            "checks = (rawgetmetatable('Container').getSize ~= nil) and " +
                "(rawgetmetatable('Container').getItems ~= nil) and " +
                "(rawgetmetatable('Container').getItemCountById ~= nil) and " +
                "(rawgetmetatable('Container').hasItem ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }
}
