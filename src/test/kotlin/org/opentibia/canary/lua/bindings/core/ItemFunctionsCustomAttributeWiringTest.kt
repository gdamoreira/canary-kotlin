package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations

/**
 * LuaJ transcript tests for the real `Item` custom-attribute store wiring:
 * `ItemFunctions::luaItemGetCustomAttribute`/`luaItemSetCustomAttribute`/
 * `luaItemRemoveCustomAttribute`
 * (src/lua/functions/items/item_functions.cpp:577-658) over
 * `ItemProperties::getCustomAttribute`/`setCustomAttribute`/`removeCustomAttribute`
 * (src/items/functions/item/attribute.cpp:105-153, case-insensitive keys).
 *
 * The production composition installs `WorldItemOperations`; it inherits the
 * `ItemOperations` self-attribute defaults, which now read and write the real
 * [Item] store instead of the wave 45 inert no-ops.
 */
class ItemFunctionsCustomAttributeWiringTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun coin(): Item = Item(id = 2160, type = ItemType(2160, "crystal coin", isStackable = true))

    @Test
    fun `world composition stores and returns every CustomAttribute variant`() {
        val environment = env()
        val item = coin()
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk(
            """
            setInteger = item:setCustomAttribute("count", 7)
            setDouble = item:setCustomAttribute("ratio", 1.5)
            setWhole = item:setCustomAttribute("whole", 3.0)
            setText = item:setCustomAttribute("label", "hail")
            setFlag = item:setCustomAttribute("flag", true)
            setNumericKey = item:setCustomAttribute(9, "nine")
            integer = item:getCustomAttribute("count")
            double = item:getCustomAttribute("ratio")
            whole = item:getCustomAttribute("whole")
            text = item:getCustomAttribute("label")
            flag = item:getCustomAttribute("flag")
            numericKey = item:getCustomAttribute(9)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("setInteger").toboolean()).isTrue()
        assertThat(environment.getGlobal("setDouble").toboolean()).isTrue()
        assertThat(environment.getGlobal("setWhole").toboolean()).isTrue()
        assertThat(environment.getGlobal("setText").toboolean()).isTrue()
        assertThat(environment.getGlobal("setFlag").toboolean()).isTrue()
        assertThat(environment.getGlobal("setNumericKey").toboolean()).isTrue()
        assertThat(environment.getGlobal("integer").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("double").todouble()).isEqualTo(1.5)
        assertThat(environment.getGlobal("whole").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("text").tojstring()).isEqualTo("hail")
        assertThat(environment.getGlobal("flag").toboolean()).isTrue()
        assertThat(environment.getGlobal("numericKey").tojstring()).isEqualTo("nine")

        assertThat(item.getCustomAttribute("count")?.getInteger()).isEqualTo(7L)
        assertThat(item.getCustomAttribute("ratio")?.getDouble()).isEqualTo(1.5)
        assertThat(item.getCustomAttribute("whole")?.getInteger()).isEqualTo(3L)
        assertThat(item.getCustomAttribute("label")?.getString()).isEqualTo("hail")
        assertThat(item.getCustomAttribute("flag")?.getBool()).isTrue()
        assertThat(item.getCustomAttribute("9")?.getString()).isEqualTo("nine")
    }

    @Test
    fun `default seam operations also write the real store`() {
        val environment = env()
        ItemFunctions.install(environment)
        val item = coin()
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk(
            """
            set = item:setCustomAttribute("key", 11)
            get = item:getCustomAttribute("key")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("get").toint()).isEqualTo(11)
        assertThat(item.getCustomAttribute("key")?.getInteger()).isEqualTo(11L)
    }

    @Test
    fun `lookups are case-insensitive and removal reports the store result`() {
        val environment = env()
        val item = coin()
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk(
            """
            set = item:setCustomAttribute("Mixed Key", 5)
            upper = item:getCustomAttribute("MIXED KEY")
            lower = item:getCustomAttribute("mixed key")
            removed = item:removeCustomAttribute("mIxEd kEy")
            removedAgain = item:removeCustomAttribute("mixed key")
            afterRemove = item:getCustomAttribute("mixed key")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("upper").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("lower").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("removedAgain").toboolean()).isFalse()
        assertThat(environment.getGlobal("afterRemove").isnil()).isTrue()
        assertThat(item.getCustomAttribute("mixed key")).isNull()
    }

    @Test
    fun `absent keys and unsupported values keep the Canary nil branches`() {
        val environment = env()
        val item = coin()
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk(
            """
            missing = item:getCustomAttribute("missing")
            invalidValue = item:setCustomAttribute("bad", {})
            invalidKey = item:getCustomAttribute({})
            missingRemove = item:removeCustomAttribute("missing")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("invalidValue").isnil()).isTrue()
        assertThat(environment.getGlobal("invalidKey").isnil()).isTrue()
        assertThat(environment.getGlobal("missingRemove").toboolean()).isFalse()
        assertThat(item.hasCustomAttribute()).isFalse()
    }

    @Test
    fun `explicit WorldLuaOperations composition exposes the same store`() {
        val environment = LuaEnvironment().apply { initState() }.also {
            WorldLuaOperations().install(it)
        }
        val item = coin()
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk(
            """
            set = item:setCustomAttribute("quest", true)
            get = item:getCustomAttribute("quest")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("get").toboolean()).isTrue()
        assertThat(item.getCustomAttribute("quest")?.getBool()).isTrue()
    }
}
