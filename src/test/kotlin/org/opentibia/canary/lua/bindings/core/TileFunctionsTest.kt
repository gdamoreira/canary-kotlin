package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.MagicField
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.House
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `src/lua/functions/map/tile_functions.cpp` driven through
 * real Lua chunks against real [Tile] domain objects wired by [WorldLuaOperations].
 */
class TileFunctionsTest {

    private fun envWith(
        map: GameMap,
        registry: ItemTypeRegistry = ItemTypeRegistry(),
        itemIdsByName: Map<String, Int> = emptyMap(),
    ): LuaEnvironment = LuaEnvironment().apply { initState() }
        .also { WorldLuaOperations(itemTypes = registry, itemIdsByName = itemIdsByName, gameMap = map).install(it) }

    private fun LuaEnvironment.wrapTile(tile: Tile) {
        globals.set("tile", userdata.wrap("Tile", tile))
    }

    private fun LuaEnvironment.wrapPlayer(name: String, player: Player) {
        globals.set(name, userdata.wrap("Player", player))
    }

    private fun player(id: Long, name: String, position: Position): Player =
        Player(id, name, 100, 100, position = position)

    @Test
    fun `Tile creature queries read the domain creature list`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val bottom = player(1, "Bottom", tile.position)
        val top = player(2, "Top", tile.position)
        tile.internalAddThing(bottom)
        tile.internalAddThing(top)
        val environment = envWith(map)
        environment.wrapTile(tile)
        environment.wrapPlayer("bottom", bottom)

        environment.loadChunk("count = tile:getCreatureCount()")
        environment.loadChunk("creatures = tile:getCreatures()")
        environment.loadChunk("length = #creatures")
        environment.loadChunk("topName = tile:getTopCreature():getName()")
        environment.loadChunk("bottomName = tile:getBottomCreature():getName()")
        environment.loadChunk("topVisible = tile:getTopVisibleCreature(bottom):getName()")
        environment.loadChunk("bottomVisible = tile:getBottomVisibleCreature(bottom):getName()")
        environment.loadChunk("noSeer = tile:getTopVisibleCreature()")

        assertThat(environment.getGlobal("count").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("length").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("topName").tojstring()).isEqualTo("Top")
        assertThat(environment.getGlobal("bottomName").tojstring()).isEqualTo("Bottom")
        assertThat(environment.getGlobal("topVisible").tojstring()).isEqualTo("Top")
        assertThat(environment.getGlobal("bottomVisible").tojstring()).isEqualTo("Bottom")
        assertThat(environment.getGlobal("noSeer").isnil()).isTrue()
    }

    @Test
    fun `Tile thing ordering exposes ground top creature and down items`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val ground = Item(100, type = ItemType(100, "grass", isGroundTile = true))
        val down = Item(101, type = ItemType(101, "down"))
        val top = Item(102, type = ItemType(102, "top", alwaysOnTopOrder = 3))
        val walker = player(3, "Walker", tile.position)
        tile.setGround(ground)
        tile.internalAddThing(down)
        tile.internalAddThing(top)
        tile.internalAddThing(walker)
        val environment = envWith(map)
        environment.wrapTile(tile)

        environment.loadChunk("thingCount = tile:getThingCount()")
        environment.loadChunk("groundId = tile:getThing(0):getId()")
        environment.loadChunk("topId = tile:getThing(1):getId()")
        environment.loadChunk("walkerName = tile:getThing(2):getName()")
        environment.loadChunk("downId = tile:getThing(3):getId()")
        environment.loadChunk("missing = tile:getThing(9)")
        environment.loadChunk("downCount = tile:getDownItemCount()")
        environment.loadChunk("topCount = tile:getTopItemCount()")
        environment.loadChunk("topTopId = tile:getTopTopItem():getId()")
        environment.loadChunk("topDownId = tile:getTopDownItem():getId()")
        environment.loadChunk("byOrderId = tile:getItemByTopOrder(3):getId()")
        environment.loadChunk("byOrderMissing = tile:getItemByTopOrder(2)")
        environment.loadChunk("items = tile:getItems()")
        environment.loadChunk("itemsLength = #items")
        environment.loadChunk("itemCount = tile:getItemCount()")

        assertThat(environment.getGlobal("thingCount").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("groundId").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("topId").toint()).isEqualTo(102)
        assertThat(environment.getGlobal("walkerName").tojstring()).isEqualTo("Walker")
        assertThat(environment.getGlobal("downId").toint()).isEqualTo(101)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("downCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("topCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("topTopId").toint()).isEqualTo(102)
        assertThat(environment.getGlobal("topDownId").toint()).isEqualTo(101)
        assertThat(environment.getGlobal("byOrderId").toint()).isEqualTo(102)
        assertThat(environment.getGlobal("byOrderMissing").isnil()).isTrue()
        assertThat(environment.getGlobal("itemsLength").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("itemCount").toint()).isEqualTo(2)
    }

    @Test
    fun `Tile getFieldItem returns the domain magic field`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val field = MagicField(
            1497,
            25,
            type = ItemType(1497, "fire field", type = ItemTypes_t.ITEM_TYPE_MAGICFIELD),
        )
        tile.internalAddThing(field)
        val emptyTile = map.getOrCreateTile(Position(101, 100, 7))
        val environment = envWith(map)
        environment.wrapTile(tile)

        environment.loadChunk("fieldId = tile:getFieldItem():getId()")
        environment.loadChunk("byTypeId = tile:getItemByType(${ItemTypes_t.ITEM_TYPE_MAGICFIELD.value}):getId()")
        environment.loadChunk("flag = tile:hasFlag(${TileFlags.TILESTATE_MAGICFIELD.value})")
        environment.loadChunk("noField = tile:getFieldItem() ~= nil")
        environment.globals.set("empty", environment.userdata.wrap("Tile", emptyTile))
        environment.loadChunk("emptyField = empty:getFieldItem()")

        assertThat(environment.getGlobal("fieldId").toint()).isEqualTo(1497)
        assertThat(environment.getGlobal("byTypeId").toint()).isEqualTo(1497)
        assertThat(environment.getGlobal("flag").toboolean()).isTrue()
        assertThat(environment.getGlobal("noField").toboolean()).isTrue()
        assertThat(environment.getGlobal("emptyField").isnil()).isTrue()
    }

    @Test
    fun `Tile getItemByType honors the tile flag gate`() {
        val map = GameMap()
        val flagged = map.getOrCreateTile(Position(100, 100, 7))
        val unflagged = map.getOrCreateTile(Position(101, 100, 7))
        flagged.setFlag(TileFlags.TILESTATE_TELEPORT.value)
        val teleportType = ItemType(1387, "teleport", type = ItemTypes_t.ITEM_TYPE_TELEPORT)
        flagged.internalAddThing(Item(1387, type = teleportType))
        unflagged.internalAddThing(Item(1387, type = teleportType))
        val registry = ItemTypeRegistry().apply { register(teleportType) }
        val environment = envWith(map, registry)
        environment.wrapTile(flagged)

        environment.loadChunk("foundId = tile:getItemByType(${ItemTypes_t.ITEM_TYPE_TELEPORT.value}):getId()")
        environment.globals.set("unflagged", environment.userdata.wrap("Tile", unflagged))
        environment.loadChunk("gatedOut = unflagged:getItemByType(${ItemTypes_t.ITEM_TYPE_TELEPORT.value})")
        environment.loadChunk("unknownType = tile:getItemByType(999)")

        assertThat(environment.getGlobal("foundId").toint()).isEqualTo(1387)
        assertThat(environment.getGlobal("gatedOut").isnil()).isTrue()
        assertThat(environment.getGlobal("unknownType").isnil()).isTrue()
    }

    @Test
    fun `Tile getHouse returns the house link`() {
        val map = GameMap()
        val houseTile = map.getOrCreateTile(Position(100, 100, 7))
        val plainTile = map.getOrCreateTile(Position(101, 100, 7))
        houseTile.houseId = 5
        houseTile.house = House(id = 5, name = "Test house", townId = 0)
        val environment = envWith(map)
        environment.wrapTile(houseTile)

        environment.loadChunk("house = tile:getHouse()")
        environment.globals.set("plain", environment.userdata.wrap("Tile", plainTile))
        environment.loadChunk("plainHouse = plain:getHouse()")

        assertThat(environment.getGlobal("house").isuserdata()).isTrue()
        assertThat(environment.getGlobal("plainHouse").isnil()).isTrue()
    }

    @Test
    fun `Tile sweep requires house owner access`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val owner = player(0, "Owner", tile.position)
        val stranger = player(7, "Stranger", tile.position)
        tile.houseId = 6
        tile.house = House(id = 6, name = "Owned", owner = 0, townId = 0)
        val environment = envWith(map)
        environment.wrapTile(tile)
        environment.wrapPlayer("owner", owner)
        environment.wrapPlayer("stranger", stranger)

        environment.loadChunk("swept = tile:sweep(owner)")
        environment.loadChunk("refused = tile:sweep(stranger)")
        environment.loadChunk("noActor = tile:sweep()")

        assertThat(environment.getGlobal("swept").isboolean()).isTrue()
        assertThat(environment.getGlobal("swept").toboolean()).isFalse()
        assertThat(environment.getGlobal("refused").isnil()).isTrue()
        assertThat(environment.getGlobal("noActor").isnil()).isTrue()
    }

    @Test
    fun `Tile hasProperty and hasFlag read the domain tile`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val wall = Item(120, type = ItemType(120, "wall", isGroundTile = true, blockSolid = true))
        val extraWall = Item(121, type = ItemType(121, "extra wall", blockSolid = true))
        tile.setGround(wall)
        tile.internalAddThing(extraWall)
        val onlyWallTile = map.getOrCreateTile(Position(101, 100, 7))
        val onlyWall = Item(120, type = ItemType(120, "wall", isGroundTile = true, blockSolid = true))
        onlyWallTile.setGround(onlyWall)
        val environment = envWith(map)
        environment.wrapTile(tile)
        environment.globals.set("extraWall", environment.userdata.wrap("Item", extraWall))
        environment.globals.set("onlyWall", environment.userdata.wrap("Item", onlyWall))
        environment.globals.set("onlyWallTile", environment.userdata.wrap("Tile", onlyWallTile))

        environment.loadChunk("flag = tile:hasFlag(${TileFlags.TILESTATE_BLOCKSOLID.value})")
        environment.loadChunk("property = tile:hasProperty(${ItemProperty.CONST_PROP_BLOCKSOLID.value})")
        environment.loadChunk("excluded = tile:hasProperty(${ItemProperty.CONST_PROP_BLOCKSOLID.value}, extraWall)")
        environment.loadChunk("onlyExcluded = onlyWallTile:hasProperty(${ItemProperty.CONST_PROP_BLOCKSOLID.value}, onlyWall)")

        assertThat(environment.getGlobal("flag").toboolean()).isTrue()
        assertThat(environment.getGlobal("property").toboolean()).isTrue()
        assertThat(environment.getGlobal("excluded").toboolean()).isTrue()
        assertThat(environment.getGlobal("onlyExcluded").toboolean()).isFalse()
    }

    @Test
    fun `Tile queryAdd applies the cylinder rules`() {
        val map = GameMap()
        val emptyTile = map.getOrCreateTile(Position(100, 100, 7))
        val groundTile = map.getOrCreateTile(Position(101, 100, 7))
        groundTile.setGround(Item(100, type = ItemType(100, "grass", isGroundTile = true)))
        val plain = Item(101, type = ItemType(101, "plain"))
        val walker = player(3, "Walker", emptyTile.position)
        val environment = envWith(map)
        environment.wrapTile(emptyTile)
        environment.globals.set("groundTile", environment.userdata.wrap("Tile", groundTile))
        environment.globals.set("plain", environment.userdata.wrap("Item", plain))
        environment.wrapPlayer("walker", walker)

        environment.loadChunk("blocked = tile:queryAdd(plain)")
        environment.loadChunk("unlimited = tile:queryAdd(plain, ${CylinderFlags.FLAG_NOLIMIT})")
        environment.loadChunk("allowed = groundTile:queryAdd(plain)")
        environment.loadChunk("creatureBlocked = tile:queryAdd(walker)")
        environment.loadChunk("creatureAllowed = groundTile:queryAdd(walker)")

        assertThat(environment.getGlobal("blocked").toint()).isEqualTo(ReturnValue.NOTPOSSIBLE.ordinal)
        assertThat(environment.getGlobal("unlimited").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(environment.getGlobal("allowed").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(environment.getGlobal("creatureBlocked").toint()).isEqualTo(ReturnValue.NOTPOSSIBLE.ordinal)
        assertThat(environment.getGlobal("creatureAllowed").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
    }

    @Test
    fun `Tile addItem caps the stack and rejects unknown ids`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        tile.setGround(Item(4608, type = ItemType(4608, "grass", isGroundTile = true)))
        val registry = ItemTypeRegistry().apply {
            register(ItemType(2160, "crystal coin", isStackable = true, stackSize = 100))
        }
        val environment = envWith(map, registry, mapOf("crystal coin" to 2160))
        environment.wrapTile(tile)

        environment.loadChunk("added = tile:addItem(2160, 500)")
        environment.loadChunk("count = added:getCount()")
        environment.loadChunk("tileCount = tile:getItemCount()")
        environment.loadChunk("byName = tile:addItem('crystal coin', 3)")
        environment.loadChunk("byNameCount = byName:getCount()")
        environment.loadChunk("unknown = tile:addItem(9999)")

        assertThat(environment.getGlobal("count").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("tileCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("byNameCount").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
    }

    @Test
    fun `Tile addItemEx adds parentless items and rejects parented ones`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        tile.setGround(Item(100, type = ItemType(100, "grass", isGroundTile = true)))
        val item = Item(101, type = ItemType(101, "plain"))
        val environment = envWith(map)
        environment.wrapTile(tile)
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk("added = tile:addItemEx(item)")
        environment.loadChunk("again = tile:addItemEx(item)")
        environment.loadChunk("tileCount = tile:getItemCount()")

        assertThat(environment.getGlobal("added").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(environment.getGlobal("tileCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("again").isnil()).isTrue()
    }

    @Test
    fun `Tile getTopVisibleThing skips look-through items and prefers creatures`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val visible = Item(101, type = ItemType(101, "visible"))
        val hidden = Item(102, type = ItemType(102, "look through", lookThrough = true))
        val walker = player(3, "Walker", tile.position)
        tile.setGround(Item(100, type = ItemType(100, "grass", isGroundTile = true)))
        tile.internalAddThing(visible)
        tile.internalAddThing(hidden)
        val environment = envWith(map)
        environment.wrapTile(tile)
        environment.wrapPlayer("walker", walker)

        environment.loadChunk("thingId = tile:getTopVisibleThing():getId()")
        tile.internalAddThing(walker)
        environment.loadChunk("creatureName = tile:getTopVisibleThing(walker):getName()")

        assertThat(environment.getGlobal("thingId").toint()).isEqualTo(101)
        assertThat(environment.getGlobal("creatureName").tojstring()).isEqualTo("Walker")
    }

    @Test
    fun `Tile getItemById and getItemCountById resolve subtypes`() {
        val map = GameMap()
        val tile = map.getOrCreateTile(Position(100, 100, 7))
        val coinType = ItemType(2160, "crystal coin", isStackable = true)
        tile.internalAddThing(Item(2160, 5, type = coinType))
        val environment = envWith(map, ItemTypeRegistry().apply { register(coinType) })
        environment.wrapTile(tile)

        environment.loadChunk("byId = tile:getItemById(2160):getId()")
        environment.loadChunk("bySubType = tile:getItemById(2160, 5):getId()")
        environment.loadChunk("missingSubType = tile:getItemById(2160, 3)")
        environment.loadChunk("count = tile:getItemCountById(2160)")
        environment.loadChunk("countBySubType = tile:getItemCountById(2160, 5)")
        environment.loadChunk("countMissingSubType = tile:getItemCountById(2160, 3)")

        assertThat(environment.getGlobal("byId").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("bySubType").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("missingSubType").isnil()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("countBySubType").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("countMissingSubType").toint()).isEqualTo(0)
    }
}
