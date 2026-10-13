package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Tile` metatable. Mirrors the primitives in
 * src/lua/functions/map/tile_functions.cpp.
 *
 * The Kotlin [org.opentibia.canary.map.Tile] models a ground item plus a flat item list
 * and carries no creature list, tile flags, always-on-top ordering, magic-field
 * classification, house link or `ItemProperty` data. Members whose state the domain
 * does not model carry a documented default (empty list, null, false, 0) so scripts
 * keep running instead of failing.
 */
interface TileOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    /** Mirrors `Tile(x, y, z)`/`Tile(position)` resolving `g_game().map.getTile`. */
    fun create(position: Position): Any?

    fun getPosition(tile: Any): Position?
    fun getGround(tile: Any): Any?

    /** 0 is the ground item, then the tile item list in order. */
    fun getThing(tile: Any, index: Int): Any?

    fun getThingCount(tile: Any): Int

    /** The tile item list (ground excluded), matching Canary's `getItems`. */
    fun getItems(tile: Any): List<Any>

    fun getItemCount(tile: Any): Int
    fun getItemCountById(tile: Any, itemId: Int, subType: Int): Int
    fun getItemById(tile: Any, itemId: Int, subType: Int): Any?

    /** Mirrors `tile:addItem(itemId or name[, count = 1[, flags = 0]])`. */
    fun addItem(tile: Any, itemId: Int, count: Int, flags: Long): Any?

    /** Mirrors `tile:addItemEx(item[, flags = 0])`; returns a `ReturnValue`. */
    fun addItemEx(tile: Any, item: Any, flags: Long): Int

    /** Mirrors `tile:queryAdd(thing[, flags = 0])`; returns a `ReturnValue`. */
    fun queryAdd(tile: Any, thing: Any, flags: Long): Int

    /** Index of [thing] on the tile, or -1 when it is not on the tile. */
    fun getThingIndex(tile: Any, thing: Any): Int?

    /**
     * Mirrors `getTopVisibleThing`: without a creature list or `lookThrough` data the
     * first item (Canary's first down item) is returned, else the ground.
     */
    fun getTopVisibleThing(tile: Any, creature: Any?): Any? =
        getItems(tile).firstOrNull() ?: getGround(tile)

    /** Mirrors `getTopTopItem`; the Kotlin tile keeps no top/down split, so the last item. */
    fun getTopTopItem(tile: Any): Any? = getItems(tile).lastOrNull()

    /** Mirrors `getTopDownItem`; the Kotlin tile keeps no top/down split, so the first item. */
    fun getTopDownItem(tile: Any): Any? = getItems(tile).firstOrNull()

    /** Mirrors `getFieldItem`; null until magic-field classification is modeled. */
    fun getFieldItem(tile: Any): Any? = null

    /** Mirrors `getItemByType`; null until `ItemTypes_t` is modeled. */
    fun getItemByType(tile: Any, itemType: Int): Any? = null

    /** Mirrors `getItemByTopOrder`; null until `alwaysOnTopOrder` is modeled. */
    fun getItemByTopOrder(tile: Any, topOrder: Int): Any? = null

    /** Mirrors `getBottomCreature`; null until a tile creature list is modeled. */
    fun getBottomCreature(tile: Any): Any? = null

    /** Mirrors `getTopCreature`; null until a tile creature list is modeled. */
    fun getTopCreature(tile: Any): Any? = null

    /** Mirrors `getBottomVisibleCreature`; null until a tile creature list is modeled. */
    fun getBottomVisibleCreature(tile: Any, creature: Any?): Any? = null

    /** Mirrors `getTopVisibleCreature`; null until a tile creature list is modeled. */
    fun getTopVisibleCreature(tile: Any, creature: Any?): Any? = null

    /** Mirrors `getDownItemCount`; every Kotlin tile item counts as a down item. */
    fun getDownItemCount(tile: Any): Int = getItems(tile).size

    /** Mirrors `getTopItemCount`; 0 because the Kotlin tile keeps no top/down split. */
    fun getTopItemCount(tile: Any): Int = 0

    /** Mirrors `getCreatures`; empty until a tile creature list is modeled. */
    fun getCreatures(tile: Any): List<Any> = emptyList()

    /** Mirrors `getCreatureCount`; 0 until a tile creature list is modeled. */
    fun getCreatureCount(tile: Any): Int = 0

    /** Mirrors `hasProperty`; false until `ItemProperty` data is modeled. */
    fun hasProperty(tile: Any, property: Int, item: Any?): Boolean = false

    /** Mirrors `hasFlag`; false until `TileFlags_t` is modeled. */
    fun hasFlag(tile: Any, flag: Int): Boolean = false

    /** Mirrors `getHouse`; null until a house link is modeled. */
    fun getHouse(tile: Any): Any? = null

    /** Mirrors `sweep`; null until house ownership/transfer is modeled. */
    fun sweep(tile: Any, actor: Any): Boolean? = null
}

/**
 * Registers the Canary `Tile` userdata methods plus the `Tile(x, y, z)`/`Tile(position)`
 * global class function and `__eq` metamethod (src/lua/functions/map/tile_functions.cpp):
 *
 *   Tile(x, y, z)
 *   tile:getPosition()
 *   tile:getGround()
 *   tile:getThing(index)
 *   tile:getThingCount()
 *   tile:getTopVisibleThing(creature)
 *   tile:getTopTopItem()
 *   tile:getTopDownItem()
 *   tile:getFieldItem()
 *   tile:getItemById(itemId or name[, subType = -1])
 *   tile:getItemByType(itemType)
 *   tile:getItemByTopOrder(topOrder)
 *   tile:getItemCountById(itemId or name[, subType = -1])
 *   tile:getBottomCreature()
 *   tile:getTopCreature()
 *   tile:getBottomVisibleCreature(creature)
 *   tile:getTopVisibleCreature(creature)
 *   tile:getItems()
 *   tile:getItemCount()
 *   tile:getDownItemCount()
 *   tile:getTopItemCount()
 *   tile:getCreatures()
 *   tile:getCreatureCount()
 *   tile:getThingIndex(thing)
 *   tile:hasProperty(property[, item])
 *   tile:hasFlag(flag)
 *   tile:queryAdd(thing[, flags = 0])
 *   tile:addItem(itemId or name[, count/subType = 1[, flags = 0]])
 *   tile:addItemEx(item[, flags = 0])
 *   tile:getHouse()
 *   tile:sweep(actor)
 */
object TileFunctions {
    const val TILE_TYPE = "Tile"

    fun install(env: LuaEnvironment, operations: TileOperations = UnwiredTileOperations) {
        val registry = env.userdata

        env.globals.set(TILE_TYPE, luaFunction { args ->
            val position = tilePosition(args) ?: return@luaFunction LuaValue.NIL
            operations.create(position)?.let { env.userdata.wrap(TILE_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(TILE_TYPE, "__eq", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val other = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(tile == other)
        })

        registry.registerMethod(TILE_TYPE, "getPosition", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val position = operations.getPosition(tile) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(PositionFunctions.POSITION_TYPE, position)
        })

        registry.registerMethod(TILE_TYPE, "getGround", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val ground = operations.getGround(tile) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, ground)
        })

        registry.registerMethod(TILE_TYPE, "getThing", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val thing = operations.getThing(tile, args.arg(2).toint()) ?: return@luaFunction LuaValue.NIL
            wrapCylinderUserdata(env, thing)
        })

        registry.registerMethod(TILE_TYPE, "getThingCount", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getThingCount(tile))
        })

        registry.registerMethod(TILE_TYPE, "getTopVisibleThing", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val thing = operations.getTopVisibleThing(tile, retrieveUserdata(args, 2)) ?: return@luaFunction LuaValue.NIL
            wrapCylinderUserdata(env, thing)
        })

        registry.registerMethod(TILE_TYPE, "getTopTopItem", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = operations.getTopTopItem(tile) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, item)
        })

        registry.registerMethod(TILE_TYPE, "getTopDownItem", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = operations.getTopDownItem(tile) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, item)
        })

        registry.registerMethod(TILE_TYPE, "getFieldItem", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = operations.getFieldItem(tile) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, item)
        })

        registry.registerMethod(TILE_TYPE, "getItemById", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val subType = args.arg(3).optint(-1)
            val item = operations.getItemById(tile, itemId, subType) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, item)
        })

        registry.registerMethod(TILE_TYPE, "getItemByType", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = operations.getItemByType(tile, args.arg(2).toint()) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, item)
        })

        registry.registerMethod(TILE_TYPE, "getItemByTopOrder", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = operations.getItemByTopOrder(tile, args.arg(2).toint()) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, item)
        })

        registry.registerMethod(TILE_TYPE, "getItemCountById", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val subType = args.arg(3).optint(-1)
            LuaValue.valueOf(operations.getItemCountById(tile, itemId, subType))
        })

        registry.registerMethod(TILE_TYPE, "getBottomCreature", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = operations.getBottomCreature(tile) ?: return@luaFunction LuaValue.NIL
            wrapCreatureUserdata(env, creature)
        })

        registry.registerMethod(TILE_TYPE, "getTopCreature", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = operations.getTopCreature(tile) ?: return@luaFunction LuaValue.NIL
            wrapCreatureUserdata(env, creature)
        })

        registry.registerMethod(TILE_TYPE, "getBottomVisibleCreature", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = operations.getBottomVisibleCreature(tile, retrieveUserdata(args, 2))
                ?: return@luaFunction LuaValue.NIL
            wrapCreatureUserdata(env, creature)
        })

        registry.registerMethod(TILE_TYPE, "getTopVisibleCreature", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = operations.getTopVisibleCreature(tile, retrieveUserdata(args, 2))
                ?: return@luaFunction LuaValue.NIL
            wrapCreatureUserdata(env, creature)
        })

        registry.registerMethod(TILE_TYPE, "getItems", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val items = operations.getItems(tile)
            LuaTable().also { table ->
                items.forEachIndexed { index, item -> table.set(index + 1, wrapItemUserdata(env, item)) }
            }
        })

        registry.registerMethod(TILE_TYPE, "getItemCount", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getItemCount(tile))
        })

        registry.registerMethod(TILE_TYPE, "getDownItemCount", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getDownItemCount(tile))
        })

        registry.registerMethod(TILE_TYPE, "getTopItemCount", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getTopItemCount(tile))
        })

        registry.registerMethod(TILE_TYPE, "getCreatures", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creatures = operations.getCreatures(tile)
            LuaTable().also { table ->
                creatures.forEachIndexed { index, creature ->
                    table.set(index + 1, wrapCreatureUserdata(env, creature))
                }
            }
        })

        registry.registerMethod(TILE_TYPE, "getCreatureCount", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getCreatureCount(tile))
        })

        registry.registerMethod(TILE_TYPE, "getThingIndex", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val thing = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.getThingIndex(tile, thing)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(TILE_TYPE, "hasProperty", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = if (args.narg() >= 3) retrieveUserdata(args, 3) else null
            LuaValue.valueOf(operations.hasProperty(tile, args.arg(2).toint(), item))
        })

        registry.registerMethod(TILE_TYPE, "hasFlag", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.hasFlag(tile, args.arg(2).toint()))
        })

        registry.registerMethod(TILE_TYPE, "queryAdd", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val thing = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.queryAdd(tile, thing, args.arg(3).optlong(0)))
        })

        registry.registerMethod(TILE_TYPE, "addItem", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(3).optint(1)
            val flags = args.arg(4).optlong(0)
            val item = operations.addItem(tile, itemId, count, flags) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, item)
        })

        registry.registerMethod(TILE_TYPE, "addItemEx", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.addItemEx(tile, item, args.arg(3).optlong(0)))
        })

        registry.registerMethod(TILE_TYPE, "getHouse", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val house = operations.getHouse(tile) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("House", house)
        })

        registry.registerMethod(TILE_TYPE, "sweep", luaFunction { args ->
            val tile = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val actor = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.sweep(tile, actor)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })
    }

    /**
     * Parses `Tile(x, y, z)`/`Tile(position)` the way Canary's `luaTileCreate` resolves
     * a map position: a table/`Position` userdata or the raw x/y/z arguments.
     */
    private fun tilePosition(args: Varargs): Position? {
        val first = args.arg(1)
        if (first.istable()) {
            return luaToPosition(first)
        }
        val userdata = retrieveUserdata(args, 1)
        if (userdata is Position) {
            return userdata
        }
        if (first.isnil()) {
            return null
        }
        return Position(first.optint(0), args.arg(2).optint(0), args.arg(3).optint(0))
    }
}

internal object UnwiredTileOperations : TileOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("TileFunctions.itemIdByName seam is not wired")

    override fun create(position: Position): Any? =
        throw NotImplementedError("TileFunctions.create seam is not wired")

    override fun getPosition(tile: Any): Position? =
        throw NotImplementedError("TileFunctions.getPosition seam is not wired")

    override fun getGround(tile: Any): Any? =
        throw NotImplementedError("TileFunctions.getGround seam is not wired")

    override fun getThing(tile: Any, index: Int): Any? =
        throw NotImplementedError("TileFunctions.getThing seam is not wired")

    override fun getThingCount(tile: Any): Int =
        throw NotImplementedError("TileFunctions.getThingCount seam is not wired")

    override fun getItems(tile: Any): List<Any> =
        throw NotImplementedError("TileFunctions.getItems seam is not wired")

    override fun getItemCount(tile: Any): Int =
        throw NotImplementedError("TileFunctions.getItemCount seam is not wired")

    override fun getItemCountById(tile: Any, itemId: Int, subType: Int): Int =
        throw NotImplementedError("TileFunctions.getItemCountById seam is not wired")

    override fun getItemById(tile: Any, itemId: Int, subType: Int): Any? =
        throw NotImplementedError("TileFunctions.getItemById seam is not wired")

    override fun addItem(tile: Any, itemId: Int, count: Int, flags: Long): Any? =
        throw NotImplementedError("TileFunctions.addItem seam is not wired")

    override fun addItemEx(tile: Any, item: Any, flags: Long): Int =
        throw NotImplementedError("TileFunctions.addItemEx seam is not wired")

    override fun queryAdd(tile: Any, thing: Any, flags: Long): Int =
        throw NotImplementedError("TileFunctions.queryAdd seam is not wired")

    override fun getThingIndex(tile: Any, thing: Any): Int? =
        throw NotImplementedError("TileFunctions.getThingIndex seam is not wired")
}
