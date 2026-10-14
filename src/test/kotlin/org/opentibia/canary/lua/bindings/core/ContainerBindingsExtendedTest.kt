package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the extended `Container` Lua binding library. The engine
 * seam is fake, but the methods are invoked through real Lua chunks with the exact
 * Canary names (container:addItem, container:addItemEx, container:removeItemById, ...).
 */
class ContainerBindingsExtendedTest {

    private class FakeItem(val id: Int)

    private class FakeContainer(val acceptsItems: Boolean = true)

    private class FakeContainerOperations : ContainerOperations {
        val addedItemIds = mutableListOf<Int>()

        override fun itemIdByName(name: String): Int = if (name == "gold coin") 2148 else 0

        override fun getSize(container: Any): Int = 0

        override fun getMaxCapacity(container: Any): Int = 20

        override fun getCapacity(container: Any): Int = 20

        override fun getEmptySlots(container: Any, recursive: Boolean): Int = 20

        override fun getItemHoldingCount(container: Any): Int = 0

        override fun getItemCountById(container: Any, itemId: Int, subType: Int): Int = 0

        override fun getItem(container: Any, index: Int): Any? = null

        override fun hasItem(container: Any, item: Any?): Boolean = false

        override fun getItems(container: Any, recursive: Boolean): List<Any> = emptyList()

        override fun getContentDescription(container: Any, oldProtocol: Boolean): String =
            if (oldProtocol) "legacy content" else "content"

        override fun addItem(container: Any, itemId: Int, count: Int, index: Int, flags: Long): Any? {
            if (!(container as FakeContainer).acceptsItems) {
                return null
            }
            addedItemIds += itemId
            return FakeItem(itemId)
        }

        override fun addItemEx(container: Any, item: Any, index: Int, flags: Long): Int = 0

        override fun getCorpseOwner(container: Any): Long = 777L

        override fun registerReward(container: Any): Boolean = true

        override fun removeAllItems(container: Any, actor: Any?, recursive: Boolean): Long = 3L

        override fun removeItemById(container: Any, itemId: Int, count: Int, subType: Int): Boolean = true
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Container registers the extended Canary method names`() {
        val environment = env()
        ContainerFunctions.install(environment, FakeContainerOperations())

        environment.loadChunk(
            "checks = (rawgetmetatable('Container').__metatable.getContentDescription ~= nil) and " +
                "(rawgetmetatable('Container').__metatable.addItem ~= nil) and " +
                "(rawgetmetatable('Container').__metatable.addItemEx ~= nil) and " +
                "(rawgetmetatable('Container').__metatable.getCorpseOwner ~= nil) and " +
                "(rawgetmetatable('Container').__metatable.registerReward ~= nil) and " +
                "(rawgetmetatable('Container').__metatable.removeAllItems ~= nil) and " +
                "(rawgetmetatable('Container').__metatable.removeItemById ~= nil) and " +
                "(rawgetmetatable('Container').__eq ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `Container addItem resolves ids and names through the seam`() {
        val environment = env()
        val operations = FakeContainerOperations()
        ContainerFunctions.install(environment, operations)
        environment.globals.set("bag", environment.userdata.wrap("Container", FakeContainer()))

        environment.loadChunk("byId = bag:addItem(2160, 5, 0, 0)")
        environment.loadChunk("byName = bag:addItem('gold coin')")
        environment.loadChunk("unknown = bag:addItem('missing thing')")

        assertThat(environment.getGlobal("byId").isuserdata()).isTrue()
        assertThat(environment.getGlobal("byName").isuserdata()).isTrue()
        assertThat(operations.addedItemIds).containsExactly(2160, 2148)
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
    }

    @Test
    fun `Container addItem returns false when the seam rejects the item`() {
        val environment = env()
        ContainerFunctions.install(environment, FakeContainerOperations())
        environment.globals.set("bag", environment.userdata.wrap("Container", FakeContainer(acceptsItems = false)))

        environment.loadChunk("rejected = bag:addItem(2160)")

        assertThat(environment.getGlobal("rejected").isboolean()).isTrue()
        assertThat(environment.getGlobal("rejected").toboolean()).isFalse()
    }

    @Test
    fun `Container addItemEx returns the seam return value`() {
        val environment = env()
        ContainerFunctions.install(environment, FakeContainerOperations())
        environment.globals.set("bag", environment.userdata.wrap("Container", FakeContainer()))
        environment.globals.set("item", environment.userdata.wrap("Item", FakeItem(2160)))

        environment.loadChunk("ret = bag:addItemEx(item, 0, 0)")
        environment.loadChunk("missing = bag:addItemEx(nil)")

        assertThat(environment.getGlobal("ret").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `Container content and owner queries delegate to the seam`() {
        val environment = env()
        ContainerFunctions.install(environment, FakeContainerOperations())
        environment.globals.set("bag", environment.userdata.wrap("Container", FakeContainer()))

        environment.loadChunk("content = bag:getContentDescription()")
        environment.loadChunk("legacy = bag:getContentDescription(true)")
        environment.loadChunk("owner = bag:getCorpseOwner()")

        assertThat(environment.getGlobal("content").tojstring()).isEqualTo("content")
        assertThat(environment.getGlobal("legacy").tojstring()).isEqualTo("legacy content")
        assertThat(environment.getGlobal("owner").toint()).isEqualTo(777)
    }

    @Test
    fun `Container reward and removal methods delegate to the seam`() {
        val environment = env()
        ContainerFunctions.install(environment, FakeContainerOperations())
        environment.globals.set("bag", environment.userdata.wrap("Container", FakeContainer()))
        environment.globals.set("actor", environment.userdata.wrap("Player", Any()))

        environment.loadChunk("rewarded = bag:registerReward()")
        environment.loadChunk("removed = bag:removeAllItems(actor, true)")
        environment.loadChunk("removedById = bag:removeItemById('gold coin', 2, -1)")

        assertThat(environment.getGlobal("rewarded").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("removedById").toboolean()).isTrue()
    }

    @Test
    fun `Container equality compares the underlying containers`() {
        val environment = env()
        ContainerFunctions.install(environment, FakeContainerOperations())
        val container = FakeContainer()
        environment.globals.set("a", environment.userdata.wrap("Container", container))
        environment.globals.set("b", environment.userdata.wrap("Container", container))
        environment.globals.set("c", environment.userdata.wrap("Container", FakeContainer()))

        environment.loadChunk("same = (a == b)")
        environment.loadChunk("different = (a == c)")

        assertThat(environment.getGlobal("same").toboolean()).isTrue()
        assertThat(environment.getGlobal("different").toboolean()).isFalse()
    }
}
