package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations

/**
 * Transcript-style tests proving the extended `Game`/`Item` bindings delegate to the
 * real Kotlin domain objects through [WorldLuaOperations]. The exact Canary names are
 * invoked from real Lua chunks against real [Item]/[Container] instances.
 */
class GameItemWorldBindingsTest {

    private fun envWith(world: WorldLuaOperations = WorldLuaOperations()): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { world.install(it) }

    @Test
    fun `Game getExperienceForLevel uses the Canary experience formula`() {
        val environment = envWith()

        environment.loadChunk("exp = Game.getExperienceForLevel(8)")

        assertThat(environment.getGlobal("exp").toint()).isEqualTo(4200)
    }

    @Test
    fun `Item isItem isContainer and getContainer reflect the domain type`() {
        val environment = envWith()
        val container = Container(id = 1987, capacity = 3, type = ItemType(1987, "backpack", isContainer = true))
        val plain = Item(id = 2160, type = ItemType(2160, "crystal coin", isStackable = true))
        environment.globals.set("bag", environment.userdata.wrap("Container", container))
        environment.globals.set("coin", environment.userdata.wrap("Item", plain))

        environment.loadChunk("bagIs = bag:isContainer()")
        environment.loadChunk("coinIs = coin:isContainer()")
        environment.loadChunk("bagItem = bag:isItem()")
        environment.loadChunk("bagContainer = bag:getContainer()")
        environment.loadChunk("containerSize = bagContainer:getSize()")
        environment.loadChunk("coinContainer = coin:getContainer()")

        assertThat(environment.getGlobal("bagIs").toboolean()).isTrue()
        assertThat(environment.getGlobal("coinIs").toboolean()).isFalse()
        assertThat(environment.getGlobal("bagItem").toboolean()).isTrue()
        assertThat(environment.getGlobal("bagContainer").isuserdata()).isTrue()
        assertThat(environment.getGlobal("containerSize").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("coinContainer").isboolean()).isTrue()
        assertThat(environment.getGlobal("coinContainer").toboolean()).isFalse()
    }

    @Test
    fun `Item attribute methods use the domain attribute bag`() {
        val environment = envWith()
        val item = Item(id = 2160, type = ItemType(2160, "crystal coin", isStackable = true))
        item.setUniqueId(6543L)
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk("missing = item:hasAttribute(5)")
        environment.loadChunk("set = item:setAttribute(5, 180)")
        environment.loadChunk("has = item:hasAttribute(5)")
        environment.loadChunk("value = item:getAttribute(5)")
        environment.loadChunk("removed = item:removeAttribute(5)")
        environment.loadChunk("after = item:hasAttribute(5)")
        environment.loadChunk("protected = item:removeAttribute(2)")
        environment.loadChunk("setAid = item:setActionId(100)")
        environment.loadChunk("aid = item:getActionId()")
        environment.loadChunk("uid = item:getUniqueId()")

        assertThat(environment.getGlobal("missing").toboolean()).isFalse()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("has").toboolean()).isTrue()
        assertThat(environment.getGlobal("value").toint()).isEqualTo(180)
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").toboolean()).isFalse()
        assertThat(environment.getGlobal("protected").toboolean()).isFalse()
        assertThat(environment.getGlobal("setAid").toboolean()).isTrue()
        assertThat(environment.getGlobal("aid").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("uid").toint()).isEqualTo(6543)
    }

    @Test
    fun `Item attribute names resolve through the attribute catalog`() {
        val world = WorldLuaOperations(
            itemAttributeIdsByName = mapOf("duration" to 18, "description" to 3),
        )
        val environment = envWith(world)
        val item = Item(id = 2160)
        item.setAttribute(ItemAttribute.DURATION, 5_000L)
        item.setAttribute(ItemAttribute.DESCRIPTION, "noted")
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk("duration = item:getAttribute('duration')")
        environment.loadChunk("set = item:setAttribute('duration', 7000)")
        environment.loadChunk("updated = item:getAttribute('duration')")
        environment.loadChunk("description = item:getAttribute('description')")
        environment.loadChunk("has = item:hasAttribute('description')")

        assertThat(environment.getGlobal("duration").toint()).isEqualTo(5000)
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("updated").toint()).isEqualTo(7000)
        assertThat(environment.getGlobal("description").tojstring()).isEqualTo("noted")
        assertThat(environment.getGlobal("has").toboolean()).isTrue()
    }

    @Test
    fun `Item getCharges getFluidType getWeight getArticle and getPluralName read the domain`() {
        val environment = envWith()
        val sword = Item(id = 2400, type = ItemType(2400, "magic sword", weight = 42.0))
        sword.setAttribute(ItemAttribute.CHARGES, 7L)
        sword.setAttribute(ItemAttribute.FLUIDTYPE, 3L)
        sword.setAttribute(ItemAttribute.ARTICLE, "a")
        sword.setAttribute(ItemAttribute.PLURALNAME, "magic swords")
        environment.globals.set("sword", environment.userdata.wrap("Item", sword))

        val coins = Item(
            id = 2160,
            count = 10,
            type = ItemType(2160, "crystal coin", weight = 0.1, isStackable = true),
        )
        environment.globals.set("coins", environment.userdata.wrap("Item", coins))

        environment.loadChunk("charges = sword:getCharges()")
        environment.loadChunk("fluid = sword:getFluidType()")
        environment.loadChunk("weight = sword:getWeight()")
        environment.loadChunk("article = sword:getArticle()")
        environment.loadChunk("plural = sword:getPluralName()")
        environment.loadChunk("stackWeight = coins:getWeight()")

        assertThat(environment.getGlobal("charges").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("fluid").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("weight").toint()).isEqualTo(4200)
        assertThat(environment.getGlobal("article").tojstring()).isEqualTo("a")
        assertThat(environment.getGlobal("plural").tojstring()).isEqualTo("magic swords")
        assertThat(environment.getGlobal("stackWeight").toint()).isEqualTo(100)
    }
}
