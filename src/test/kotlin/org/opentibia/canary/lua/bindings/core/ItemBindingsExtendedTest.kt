package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the extended `Item`/`Container` binding. Each test installs
 * the seam defaults and runs real Lua chunks with the exact Canary names (item:moveTo,
 * item:remove, item:getContainer, item:getDescription, item:isContainer, ...), pinning
 * the safe no-op contract for item state the Kotlin domain does not model yet. The
 * real-domain delegation is covered by [GameItemWorldBindingsTest] and the fake-seam
 * argument plumbing by [ItemBindingsDelegationTest].
 */
class ItemBindingsExtendedTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Item and Container expose the extended Canary method names`() {
        val environment = env()
        ItemFunctions.install(environment)

        environment.loadChunk(
            "checks = (rawgetmetatable('Item').__metatable.isItem ~= nil) and " +
                "(rawgetmetatable('Container').__metatable.isItem ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getContainer ~= nil) and " +
                "(rawgetmetatable('Container').__metatable.getContainer ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getParent ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getTopParent ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.clone ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.split ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.remove ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getCharges ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getFluidType ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getWeight ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getPluralName ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getArticle ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getPosition ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getTile ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.hasAttribute ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.removeAttribute ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getCustomAttribute ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.setCustomAttribute ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.removeCustomAttribute ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.canBeMoved ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.moveTo ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.getDescription ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.isContainer ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `Item identity and ownership queries use the default seam values`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))

        environment.loadChunk("isItem = item:isItem()")
        environment.loadChunk("container = item:getContainer()")
        environment.loadChunk("parent = item:getParent()")
        environment.loadChunk("topParent = item:getTopParent()")
        environment.loadChunk("clone = item:clone()")
        environment.loadChunk("split = item:split()")
        environment.loadChunk("removed = item:remove()")

        assertThat(environment.getGlobal("isItem").toboolean()).isFalse()
        assertThat(environment.getGlobal("container").isboolean()).isTrue()
        assertThat(environment.getGlobal("container").toboolean()).isFalse()
        assertThat(environment.getGlobal("parent").isnil()).isTrue()
        assertThat(environment.getGlobal("topParent").isnil()).isTrue()
        assertThat(environment.getGlobal("clone").isnil()).isTrue()
        assertThat(environment.getGlobal("split").isnil()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isFalse()
    }

    @Test
    fun `Item numeric and string queries use the default seam values`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))

        environment.loadChunk("charges = item:getCharges()")
        environment.loadChunk("fluid = item:getFluidType()")
        environment.loadChunk("weight = item:getWeight()")
        environment.loadChunk("plural = item:getPluralName()")
        environment.loadChunk("article = item:getArticle()")
        environment.loadChunk("description = item:getDescription(3)")
        environment.loadChunk("position = item:getPosition()")
        environment.loadChunk("positionText = position:toString()")
        environment.loadChunk("tile = item:getTile()")
        environment.loadChunk("isContainer = item:isContainer()")
        environment.loadChunk("movable = item:canBeMoved()")

        assertThat(environment.getGlobal("charges").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("fluid").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("weight").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("plural").tojstring()).isEmpty()
        assertThat(environment.getGlobal("article").tojstring()).isEmpty()
        assertThat(environment.getGlobal("description").tojstring()).isEmpty()
        assertThat(environment.getGlobal("position").isuserdata()).isTrue()
        assertThat(environment.getGlobal("positionText").tojstring()).isEqualTo("( 0, 0, 0 )")
        assertThat(environment.getGlobal("tile").isnil()).isTrue()
        assertThat(environment.getGlobal("isContainer").toboolean()).isFalse()
        assertThat(environment.getGlobal("movable").toboolean()).isFalse()
    }

    @Test
    fun `Item attribute and movement calls use the default seam values`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))

        environment.loadChunk("has = item:hasAttribute(5)")
        environment.loadChunk("removedAttr = item:removeAttribute(5)")
        environment.loadChunk("custom = item:getCustomAttribute('key')")
        environment.loadChunk("setCustom = item:setCustomAttribute('key', 1)")
        environment.loadChunk("removedCustom = item:removeCustomAttribute('key')")
        environment.loadChunk("moved = item:moveTo({ x = 100, y = 200, z = 7 })")

        assertThat(environment.getGlobal("has").toboolean()).isFalse()
        assertThat(environment.getGlobal("removedAttr").toboolean()).isFalse()
        assertThat(environment.getGlobal("custom").isnil()).isTrue()
        assertThat(environment.getGlobal("setCustom").toboolean()).isFalse()
        assertThat(environment.getGlobal("removedCustom").toboolean()).isFalse()
        assertThat(environment.getGlobal("moved").toboolean()).isFalse()
    }
}
