package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations

/**
 * LuaJ transcript tests for the `item:getDescription(distance)` renderer wiring
 * (`ItemFunctions::luaItemGetDescription`, src/lua/functions/items/item_functions.cpp:854-864
 * over `Item::getDescription(int32_t)`, src/items/item.cpp:3352-3355).
 *
 * The production composition installs [WorldItemOperations] with the booted
 * `Item::items` registry, so the type resolves like the C++ `items[id]` lookup
 * and the fluid subtype names resolve through the same registry.
 */
class ItemFunctionsDescriptionWiringTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `world composition renders the item description`() {
        val environment = env()
        val swordType = ItemType(id = 2400, name = "magic sword", article = "a", weight = 42.0)
        val item = Item(id = 2400, type = swordType)
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk("desc = item:getDescription(1)")

        assertThat(environment.getGlobal("desc").tojstring()).isEqualTo(item.getDescription(1))
        assertThat(environment.getGlobal("desc").tojstring()).isEqualTo("a magic sword.\nIt weighs 42.00 oz.")
    }

    @Test
    fun `world composition honors the look distance`() {
        val environment = env()
        val swordType = ItemType(id = 2400, name = "magic sword", article = "a", weight = 42.0)
        val item = Item(id = 2400, type = swordType)
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk("desc = item:getDescription(3)")

        assertThat(environment.getGlobal("desc").tojstring()).isEqualTo("a magic sword.")
    }

    @Test
    fun `booted item registry resolves the fluid subtype through the Lua seam`() {
        val environment = env()
        val items = Items().apply { register(ItemType(id = 7, name = "blood")) }
        WorldLuaOperations(itemTypes = items).install(environment)
        val vial = Item(
            id = 2006,
            count = 7,
            type = ItemType(id = 2006, name = "vial", article = "a", isFluidContainer = true, weight = 1.8),
        )
        environment.globals.set("item", environment.userdata.wrap("Item", vial))

        environment.loadChunk("desc = item:getDescription(1)")

        assertThat(environment.getGlobal("desc").tojstring()).isEqualTo("a vial of blood.\nIt weighs 1.80 oz.")
    }

    @Test
    fun `default item operations render the real item`() {
        val environment = env()
        ItemFunctions.install(environment)
        val item = Item(id = 2160, type = ItemType(2160, "crystal coin", isStackable = true, weight = 0.1))
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk("desc = item:getDescription(1)")

        assertThat(environment.getGlobal("desc").tojstring()).isEqualTo("crystal coin.\nIt weighs 0.10 oz.")
    }
}
