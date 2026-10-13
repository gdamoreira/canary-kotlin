package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the `Tile` Lua binding library. The engine seam is fake,
 * but every method is invoked through real Lua chunks with the exact Canary names
 * (Tile(10, 20, 7), tile:getGround(), tile:getItemById('gold coin'), ...).
 */
class TileBindingsTest {

    private class FakeTile(val position: Position)

    private class FakeTileOperations : TileOperations {
        private val first = Item(2148)
        private val second = Item(2160)

        override fun itemIdByName(name: String): Int = if (name == "gold coin") 2148 else 0

        override fun create(position: Position): Any? = FakeTile(position)

        override fun getPosition(tile: Any): Position = (tile as FakeTile).position

        override fun getGround(tile: Any): Any = first

        override fun getThing(tile: Any, index: Int): Any? = when (index) {
            0 -> first
            1 -> second
            else -> null
        }

        override fun getThingCount(tile: Any): Int = 2

        override fun getItems(tile: Any): List<Any> = listOf(first, second)

        override fun getItemCount(tile: Any): Int = 2

        override fun getItemCountById(tile: Any, itemId: Int, subType: Int): Int =
            if (itemId == 2148) 5 else 0

        override fun getItemById(tile: Any, itemId: Int, subType: Int): Any? =
            if (itemId == 2148) first else null

        override fun addItem(tile: Any, itemId: Int, count: Int, flags: Long): Any? = Item(itemId)

        override fun addItemEx(tile: Any, item: Any, flags: Long): Int = 0

        override fun queryAdd(tile: Any, thing: Any, flags: Long): Int = 0

        override fun getThingIndex(tile: Any, thing: Any): Int? = 1
    }

    /** Every method Canary registers in `TileFunctions::init` besides the class function. */
    private val methodNames = listOf(
        "getPosition", "getGround", "getThing", "getThingCount", "getTopVisibleThing",
        "getTopTopItem", "getTopDownItem", "getFieldItem",
        "getItemById", "getItemByType", "getItemByTopOrder", "getItemCountById",
        "getBottomCreature", "getTopCreature", "getBottomVisibleCreature", "getTopVisibleCreature",
        "getItems", "getItemCount", "getDownItemCount", "getTopItemCount",
        "getCreatures", "getCreatureCount",
        "getThingIndex",
        "hasProperty", "hasFlag",
        "queryAdd", "addItem", "addItemEx",
        "getHouse", "sweep",
    )

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Tile registers every Canary method name`() {
        val environment = env()
        TileFunctions.install(environment, FakeTileOperations())

        val checks = methodNames.joinToString(" and ") {
            "(rawgetmetatable('Tile').$it ~= nil)"
        }
        environment.loadChunk("checks = $checks and (rawgetmetatable('Tile').__eq ~= nil)")

        assertThat(methodNames).hasSize(30)
        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `Tile constructor resolves coordinates and tables through the seam`() {
        val environment = env()
        TileFunctions.install(environment, FakeTileOperations())
        PositionFunctions.install(environment)

        environment.loadChunk("tile = Tile(10, 20, 7)")
        environment.loadChunk("fromTable = Tile({ x = 30, y = 40, z = 5 })")
        environment.loadChunk("pos = tile:getPosition()")
        environment.loadChunk("posText = pos:toString()")
        environment.loadChunk("tableText = fromTable:getPosition():toString()")

        assertThat(environment.getGlobal("tile").isuserdata()).isTrue()
        assertThat(environment.getGlobal("posText").tojstring()).isEqualTo("( 10, 20, 7 )")
        assertThat(environment.getGlobal("tableText").tojstring()).isEqualTo("( 30, 40, 5 )")
    }

    @Test
    fun `Tile item queries delegate to the seam`() {
        val environment = env()
        TileFunctions.install(environment, FakeTileOperations())
        environment.globals.set("tile", environment.userdata.wrap("Tile", FakeTile(Position(10, 20, 7))))

        environment.loadChunk("ground = tile:getGround()")
        environment.loadChunk("thing = tile:getThing(1)")
        environment.loadChunk("missing = tile:getThing(9)")
        environment.loadChunk("thingCount = tile:getThingCount()")
        environment.loadChunk("all = tile:getItems()")
        environment.loadChunk("itemCount = tile:getItemCount()")
        environment.loadChunk("byId = tile:getItemById(2148)")
        environment.loadChunk("byName = tile:getItemById('gold coin')")
        environment.loadChunk("unknown = tile:getItemById(9999)")
        environment.loadChunk("countById = tile:getItemCountById(2148)")
        environment.loadChunk("index = tile:getThingIndex(ground)")

        assertThat(environment.getGlobal("ground").isuserdata()).isTrue()
        assertThat(environment.getGlobal("thing").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("thingCount").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("all").rawlen()).isEqualTo(2)
        assertThat(environment.getGlobal("itemCount").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("byId").isuserdata()).isTrue()
        assertThat(environment.getGlobal("byName").isuserdata()).isTrue()
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
        assertThat(environment.getGlobal("countById").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("index").toint()).isEqualTo(1)
    }

    @Test
    fun `Tile mutation calls delegate to the seam`() {
        val environment = env()
        TileFunctions.install(environment, FakeTileOperations())
        environment.globals.set("tile", environment.userdata.wrap("Tile", FakeTile(Position(10, 20, 7))))
        environment.globals.set("item", environment.userdata.wrap("Item", Item(2148)))

        environment.loadChunk("added = tile:addItem(2148, 5, 0)")
        environment.loadChunk("addedByName = tile:addItem('gold coin')")
        environment.loadChunk("addedEx = tile:addItemEx(item, 0)")
        environment.loadChunk("queried = tile:queryAdd(item, 0)")

        assertThat(environment.getGlobal("added").isuserdata()).isTrue()
        assertThat(environment.getGlobal("addedByName").isuserdata()).isTrue()
        assertThat(environment.getGlobal("addedEx").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("queried").toint()).isEqualTo(0)
    }

    @Test
    fun `Tile unmodeled queries expose the documented defaults`() {
        val environment = env()
        TileFunctions.install(environment, FakeTileOperations())
        environment.globals.set("tile", environment.userdata.wrap("Tile", FakeTile(Position(10, 20, 7))))
        environment.globals.set("creature", environment.userdata.wrap("Creature", Any()))

        environment.loadChunk("topTop = tile:getTopTopItem()")
        environment.loadChunk("topDown = tile:getTopDownItem()")
        environment.loadChunk("visible = tile:getTopVisibleThing(creature)")
        environment.loadChunk("downCount = tile:getDownItemCount()")
        environment.loadChunk("topCount = tile:getTopItemCount()")
        environment.loadChunk("creatures = tile:getCreatures()")
        environment.loadChunk("creatureCount = tile:getCreatureCount()")
        environment.loadChunk("topCreature = tile:getTopCreature()")
        environment.loadChunk("bottomCreature = tile:getBottomCreature()")
        environment.loadChunk("topVisibleCreature = tile:getTopVisibleCreature(creature)")
        environment.loadChunk("bottomVisibleCreature = tile:getBottomVisibleCreature(creature)")
        environment.loadChunk("field = tile:getFieldItem()")
        environment.loadChunk("byType = tile:getItemByType(3)")
        environment.loadChunk("byTopOrder = tile:getItemByTopOrder(2)")
        environment.loadChunk("flag = tile:hasFlag(1)")
        environment.loadChunk("property = tile:hasProperty(1)")
        environment.loadChunk("house = tile:getHouse()")
        environment.loadChunk("swept = tile:sweep(creature)")

        assertThat(environment.getGlobal("topTop").isuserdata()).isTrue()
        assertThat(environment.getGlobal("topDown").isuserdata()).isTrue()
        assertThat(environment.getGlobal("visible").isuserdata()).isTrue()
        assertThat(environment.getGlobal("downCount").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("topCount").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("creatures").istable()).isTrue()
        assertThat(environment.getGlobal("creatures").rawlen()).isEqualTo(0)
        assertThat(environment.getGlobal("creatureCount").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("topCreature").isnil()).isTrue()
        assertThat(environment.getGlobal("bottomCreature").isnil()).isTrue()
        assertThat(environment.getGlobal("topVisibleCreature").isnil()).isTrue()
        assertThat(environment.getGlobal("bottomVisibleCreature").isnil()).isTrue()
        assertThat(environment.getGlobal("field").isnil()).isTrue()
        assertThat(environment.getGlobal("byType").isnil()).isTrue()
        assertThat(environment.getGlobal("byTopOrder").isnil()).isTrue()
        assertThat(environment.getGlobal("flag").toboolean()).isFalse()
        assertThat(environment.getGlobal("property").toboolean()).isFalse()
        assertThat(environment.getGlobal("house").isnil()).isTrue()
        assertThat(environment.getGlobal("swept").isnil()).isTrue()
    }

    @Test
    fun `Tile equality compares the underlying tiles`() {
        val environment = env()
        TileFunctions.install(environment, FakeTileOperations())
        val tile = FakeTile(Position(10, 20, 7))
        environment.globals.set("a", environment.userdata.wrap("Tile", tile))
        environment.globals.set("b", environment.userdata.wrap("Tile", tile))
        environment.globals.set("c", environment.userdata.wrap("Tile", FakeTile(Position(1, 2, 3))))

        environment.loadChunk("same = (a == b)")
        environment.loadChunk("different = (a == c)")

        assertThat(environment.getGlobal("same").toboolean()).isTrue()
        assertThat(environment.getGlobal("different").toboolean()).isFalse()
    }
}
