package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.rewardchest.Reward

/**
 * Transcript-style tests proving the real (non-no-op) `ItemType`/`Tile`/`Container`/
 * `Position` seams in [WorldLuaOperations] delegate to the Kotlin domain objects. The
 * exact Canary names are invoked from real Lua chunks against real [ItemType]/[Item]/
 * [Container]/[Position] instances.
 */
class WorldItemTileBindingsTest {

    private fun envWith(world: WorldLuaOperations = WorldLuaOperations()): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { world.install(it) }

    @Test
    fun `World ItemType resolves registry types with the domain flags`() {
        val registry = ItemTypeRegistry().apply {
            register(ItemType(1987, "backpack", isContainer = true, capacity = 20, weight = 18.0))
            register(ItemType(2160, "crystal coin", isStackable = true, weight = 0.1))
        }
        val environment = envWith(
            WorldLuaOperations(itemTypes = registry, itemIdsByName = mapOf("crystal coin" to 2160)),
        )

        environment.loadChunk("byId = ItemType(1987)")
        environment.loadChunk("byName = ItemType('crystal coin')")
        environment.loadChunk("id = byName:getId()")
        environment.loadChunk("name = byId:getName()")
        environment.loadChunk("container = byId:isContainer()")
        environment.loadChunk("stackable = byName:isStackable()")
        environment.loadChunk("capacity = byId:getCapacity()")
        environment.loadChunk("weight = byName:getWeight(5)")
        environment.loadChunk("subType = byName:hasSubType()")
        environment.loadChunk("unknown = ItemType(9999)")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("backpack")
        assertThat(environment.getGlobal("container").toboolean()).isTrue()
        assertThat(environment.getGlobal("stackable").toboolean()).isTrue()
        assertThat(environment.getGlobal("capacity").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("weight").toint()).isEqualTo(50)
        assertThat(environment.getGlobal("subType").toboolean()).isTrue()
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
    }

    @Test
    fun `World Tile resolves tiles from the GameMap`() {
        val map = GameMap()
        map.getOrCreateTile(Position(100, 100, 7))
        val environment = envWith(WorldLuaOperations(gameMap = map))

        environment.loadChunk("tile = Tile(100, 100, 7)")
        environment.loadChunk("text = tile:getPosition():toString()")
        environment.loadChunk("fromTable = Tile({ x = 100, y = 100, z = 7 })")
        environment.loadChunk("tableText = fromTable:getPosition():toString()")
        environment.loadChunk("missing = Tile(1, 1, 1)")

        assertThat(environment.getGlobal("tile").isuserdata()).isTrue()
        assertThat(environment.getGlobal("text").tojstring()).isEqualTo("( 100, 100, 7 )")
        assertThat(environment.getGlobal("tableText").tojstring()).isEqualTo("( 100, 100, 7 )")
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `World Tile item queries reflect the domain tile`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val ground = Item(4608)
        val coin = Item(2160, count = 5, type = ItemType(2160, "crystal coin", isStackable = true))
        tile.ground = ground
        tile.addItem(coin)
        val environment = envWith(
            WorldLuaOperations(gameMap = map, itemIdsByName = mapOf("crystal coin" to 2160)),
        )
        environment.globals.set("tile", environment.userdata.wrap("Tile", tile))

        environment.loadChunk("ground = tile:getGround()")
        environment.loadChunk("groundId = ground:getId()")
        environment.loadChunk("count = tile:getItemCount()")
        environment.loadChunk("thingCount = tile:getThingCount()")
        environment.loadChunk("all = tile:getItems()")
        environment.loadChunk("length = #all")
        environment.loadChunk("byId = tile:getItemById(2160)")
        environment.loadChunk("byName = tile:getItemById('crystal coin')")
        environment.loadChunk("missing = tile:getItemById(9999)")
        environment.loadChunk("countById = tile:getItemCountById(2160)")
        environment.loadChunk("groundIndex = tile:getThingIndex(ground)")
        environment.loadChunk("first = tile:getThing(1)")
        environment.loadChunk("firstId = first:getId()")

        assertThat(environment.getGlobal("groundId").toint()).isEqualTo(4608)
        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("thingCount").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("length").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("byId").isuserdata()).isTrue()
        assertThat(environment.getGlobal("byName").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("countById").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("groundIndex").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("firstId").toint()).isEqualTo(2160)
    }

    @Test
    fun `World Tile addItem appends to the domain tile`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val registry = ItemTypeRegistry().apply {
            register(ItemType(2160, "crystal coin", isStackable = true))
        }
        val environment = envWith(WorldLuaOperations(gameMap = map, itemTypes = registry))
        environment.globals.set("tile", environment.userdata.wrap("Tile", tile))

        environment.loadChunk("added = tile:addItem(2160, 5)")
        environment.loadChunk("queried = tile:queryAdd(added)")
        environment.loadChunk("addedEx = tile:addItemEx(added)")

        assertThat(environment.getGlobal("added").isuserdata()).isTrue()
        assertThat(tile.items).hasSize(1)
        assertThat(tile.items[0].id).isEqualTo(2160)
        assertThat(environment.getGlobal("queried").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(environment.getGlobal("addedEx").toint()).isEqualTo(ReturnValue.NOTPOSSIBLE.ordinal)
    }

    @Test
    fun `World Position getTile and isSightClear use the map`() {
        val map = GameMap()
        map.getOrCreateTile(Position(100, 100, 7))
        val environment = envWith(WorldLuaOperations(gameMap = map))

        environment.loadChunk("here = Position(100, 100, 7)")
        environment.loadChunk("tile = here:getTile()")
        environment.loadChunk("text = tile:getPosition():toString()")
        environment.loadChunk("missing = Position(1, 1, 1):getTile()")
        environment.loadChunk("zones = here:getZones()")
        environment.loadChunk("zoneCount = #zones")
        environment.loadChunk("noZones = Position(1, 1, 1):getZones()")
        environment.loadChunk("adjacent = here:isSightClear({ x = 101, y = 101, z = 7 })")
        environment.loadChunk("crossFloor = here:isSightClear({ x = 101, y = 101, z = 6 }, true)")
        environment.loadChunk("far = here:isSightClear({ x = 120, y = 120, z = 7 })")
        environment.loadChunk("surface = Position(50, 50, 0):isSightClear({ x = 120, y = 120, z = 0 }, false)")

        assertThat(environment.getGlobal("text").tojstring()).isEqualTo("( 100, 100, 7 )")
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("zones").istable()).isTrue()
        assertThat(environment.getGlobal("zoneCount").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("noZones").isnil()).isTrue()
        assertThat(environment.getGlobal("adjacent").toboolean()).isTrue()
        assertThat(environment.getGlobal("crossFloor").toboolean()).isFalse()
        assertThat(environment.getGlobal("far").toboolean()).isFalse()
        assertThat(environment.getGlobal("surface").toboolean()).isTrue()
    }

    @Test
    fun `World Container addItem registerReward and getCorpseOwner use the domain`() {
        val registry = ItemTypeRegistry().apply {
            register(ItemType(2160, "crystal coin", isStackable = true))
        }
        val container = Container(1987, capacity = 3, type = ItemType(1987, "backpack", isContainer = true))
        container.setAttribute(ItemAttribute.CORPSEOWNER, 12345L)
        val environment = envWith(WorldLuaOperations(itemTypes = registry))
        environment.globals.set("bag", environment.userdata.wrap("Container", container))

        environment.loadChunk("added = bag:addItem(2160, 5)")
        environment.loadChunk("id = added:getId()")
        environment.loadChunk("count = added:getCount()")
        environment.loadChunk("size = bag:getSize()")
        environment.loadChunk("description = bag:getContentDescription()")
        environment.loadChunk("owner = bag:getCorpseOwner()")
        environment.loadChunk("rewarded = bag:registerReward()")
        environment.loadChunk("removed = bag:removeAllItems(nil, false)")
        environment.loadChunk("removedById = bag:removeItemById(2160, 1)")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("count").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("size").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("description").tojstring()).isEqualTo("crystal coin")
        assertThat(environment.getGlobal("owner").toint()).isEqualTo(12345)
        assertThat(environment.getGlobal("rewarded").toboolean()).isTrue()
        assertThat(container.items.map { it.id }).contains(Reward.ITEM_REWARD_CONTAINER)
        assertThat(environment.getGlobal("removed").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("removedById").toboolean()).isFalse()
    }
}
