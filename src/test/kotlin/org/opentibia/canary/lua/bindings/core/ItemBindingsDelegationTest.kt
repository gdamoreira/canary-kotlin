package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Fake-seam transcript tests pinning the exact argument plumbing of the extended `Item`
 * binding: cylinder vs position discrimination for `moveTo`, the Canary default counts for
 * `split`/`remove`, attribute name resolution, custom attribute keys and userdata wrapping.
 */
class ItemBindingsDelegationTest {

    private data class MoveToCall(
        val destination: Any?,
        val position: Position?,
        val flags: Long,
    )

    private class FakeItemOps : ItemOperations {
        val item = Item(id = 2160, type = ItemType(2160, "crystal coin", isStackable = true))
        var lastMoveTo: MoveToCall? = null
        var moveToResult = false
        var lastSplit: Pair<Any, Int>? = null
        var split: Any? = null
        var lastRemove: Pair<Any, Int>? = null
        var removeResult = false
        var container: Any? = null
        var parent: Any? = null
        var topParent: Any? = null
        var lastAttribute: Pair<Any, Int>? = null
        var attributeResult: Any? = null
        var lastCustom: Pair<Any, String>? = null
        var customResult: Any? = null
        var lastSetCustom: Triple<Any, String, Any?>? = null

        override fun itemIdByName(name: String): Int = if (name == "crystal coin") 2160 else 0
        override fun itemAttributeByName(name: String): Int = if (name == "duration") 18 else 0
        override fun getId(item: Any): Int = (item as Item).id
        override fun getCount(item: Any): Int = (item as Item).count
        override fun getName(item: Any): String = (item as Item).type?.name ?: ""
        override fun getSubType(item: Any): Int = 0
        override fun transform(item: Any, itemId: Int, subType: Int): Boolean = false
        override fun decay(item: Any, decayId: Int): Boolean = false
        override fun getActionId(item: Any): Int = 0
        override fun setActionId(item: Any, actionId: Int): Boolean = false
        override fun getUniqueId(item: Any): Long = 0L
        override fun getAttribute(item: Any, attribute: Int): Any? {
            lastAttribute = item to attribute
            return attributeResult
        }

        override fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean = false

        override fun getContainer(item: Any): Any? = container

        override fun getParent(item: Any): Any? = parent

        override fun getTopParent(item: Any): Any? = topParent

        override fun split(item: Any, count: Int): Any? {
            lastSplit = item to count
            return split
        }

        override fun remove(item: Any, count: Int): Boolean {
            lastRemove = item to count
            return removeResult
        }

        override fun moveTo(item: Any, destination: Any?, position: Position?, flags: Long): Boolean {
            lastMoveTo = MoveToCall(destination, position, flags)
            return moveToResult
        }

        override fun getCustomAttribute(item: Any, key: String): Any? {
            lastCustom = item to key
            return customResult
        }

        override fun setCustomAttribute(item: Any, key: String, value: Any?): Boolean {
            lastSetCustom = Triple(item, key, value)
            return true
        }
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Item moveTo tells a cylinder destination from a position`() {
        val environment = env()
        val operations = FakeItemOps()
        ItemFunctions.install(environment, operations)
        environment.globals.set("item", environment.userdata.wrap("Item", operations.item))
        val bag = Container(id = 1987, capacity = 3, type = ItemType(1987, "backpack", isContainer = true))
        environment.globals.set("bag", environment.userdata.wrap("Container", bag))
        environment.globals.set("pos", environment.userdata.wrap("Position", Position(30, 40, 7)))

        environment.loadChunk("toBag = item:moveTo(bag)")
        assertThat(operations.lastMoveTo).isEqualTo(MoveToCall(bag, null, 71))
        assertThat(environment.getGlobal("toBag").toboolean()).isFalse()

        environment.loadChunk("toTable = item:moveTo({ x = 10, y = 20, z = 7 }, 1)")
        assertThat(operations.lastMoveTo).isEqualTo(MoveToCall(null, Position(10, 20, 7), 1))

        environment.loadChunk("toPosition = item:moveTo(pos)")
        assertThat(operations.lastMoveTo).isEqualTo(MoveToCall(null, Position(30, 40, 7), 71))
    }

    @Test
    fun `Item split and remove use the Canary default counts`() {
        val environment = env()
        val operations = FakeItemOps()
        operations.split = Item(id = 2160, count = 1)
        operations.removeResult = true
        ItemFunctions.install(environment, operations)
        environment.globals.set("item", environment.userdata.wrap("Item", operations.item))

        environment.loadChunk("split = item:split()")
        assertThat(operations.lastSplit).isEqualTo(operations.item to 1)
        assertThat(environment.getGlobal("split").isuserdata()).isTrue()

        environment.loadChunk("removed = item:remove()")
        assertThat(operations.lastRemove).isEqualTo(operations.item to -1)
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()

        environment.loadChunk("removedMany = item:remove(3)")
        assertThat(operations.lastRemove).isEqualTo(operations.item to 3)

        environment.loadChunk("splitMany = item:split(2)")
        assertThat(operations.lastSplit).isEqualTo(operations.item to 2)
    }

    @Test
    fun `Item getContainer returns false when not a container and a Container otherwise`() {
        val environment = env()
        val operations = FakeItemOps()
        ItemFunctions.install(environment, operations)
        environment.globals.set("item", environment.userdata.wrap("Item", operations.item))

        environment.loadChunk("none = item:getContainer()")
        assertThat(environment.getGlobal("none").isboolean()).isTrue()
        assertThat(environment.getGlobal("none").toboolean()).isFalse()

        operations.container = Container(id = 1987, capacity = 3, type = ItemType(1987, "backpack", isContainer = true))
        environment.loadChunk("bag = item:getContainer()")
        environment.loadChunk("bagMt = getmetatable(bag) == rawgetmetatable('Container').__metatable")
        assertThat(environment.getGlobal("bag").isuserdata()).isTrue()
        assertThat(environment.getGlobal("bagMt").toboolean()).isTrue()
    }

    @Test
    fun `Item getParent and getTopParent wrap the cylinder userdata`() {
        val environment = env()
        val operations = FakeItemOps()
        operations.parent = Player(id = 1, name = "Hero", maxHealth = 100, health = 100, position = Position(1, 2, 7))
        operations.topParent = Container(id = 1987, capacity = 3, type = ItemType(1987, "backpack", isContainer = true))
        ItemFunctions.install(environment, operations)
        environment.globals.set("item", environment.userdata.wrap("Item", operations.item))

        environment.loadChunk("parent = item:getParent()")
        environment.loadChunk("parentMt = getmetatable(parent) == rawgetmetatable('Player').__metatable")
        environment.loadChunk("top = item:getTopParent()")
        environment.loadChunk("topMt = getmetatable(top) == rawgetmetatable('Container').__metatable")

        assertThat(environment.getGlobal("parentMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("topMt").toboolean()).isTrue()
    }

    @Test
    fun `Item attribute names and custom keys resolve like Canary`() {
        val environment = env()
        val operations = FakeItemOps()
        operations.attributeResult = 180
        operations.customResult = "stored"
        ItemFunctions.install(environment, operations)
        environment.globals.set("item", environment.userdata.wrap("Item", operations.item))

        environment.loadChunk("value = item:getAttribute('duration')")
        assertThat(operations.lastAttribute).isEqualTo(operations.item to 18)
        assertThat(environment.getGlobal("value").toint()).isEqualTo(180)

        environment.loadChunk("custom = item:getCustomAttribute(7)")
        assertThat(operations.lastCustom).isEqualTo(operations.item to "7")
        assertThat(environment.getGlobal("custom").tojstring()).isEqualTo("stored")

        environment.loadChunk("set = item:setCustomAttribute('key', true)")
        assertThat(operations.lastSetCustom).isEqualTo(Triple(operations.item, "key", true))
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
    }
}
