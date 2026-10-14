package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.Thing
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.AccessHouseLevel
import org.opentibia.canary.map.house.HouseTile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Engine seam for the `Tile` metatable. Mirrors the primitives in
 * src/lua/functions/map/tile_functions.cpp over the Kotlin
 * [org.opentibia.canary.map.Tile] domain object (ground, top/down item list,
 * creature list, tile flags, house link).
 *
 * Operations backed by another object fall back to the previously documented
 * defaults (empty list, null, false, 0) so scripts keep running against an unwired map.
 */
interface TileOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    /** Mirrors `Tile(x, y, z)`/`Tile(position)` resolving `g_game().map.getTile`. */
    fun create(position: Position): Any?

    fun getPosition(tile: Any): Position? = (tile as? Tile)?.position

    fun getGround(tile: Any): Any? = (tile as? Tile)?.ground

    /** 0 is the ground item, then Canary's top-item/creature/down-item order. */
    fun getThing(tile: Any, index: Int): Any? = (tile as? Tile)?.getThing(index)

    fun getThingCount(tile: Any): Int = (tile as? Tile)?.getThingCount() ?: 0

    /** Mirrors `getTopVisibleThing`: first visible creature, else the first visible item. */
    fun getTopVisibleThing(tile: Any, creature: Any?): Any? =
        (tile as? Tile)?.getTopVisibleThing(creature as? Creature)
            ?: getItems(tile).firstOrNull() ?: getGround(tile)

    /** Mirrors `getTopTopItem`; the last always-on-top item in the tile list. */
    fun getTopTopItem(tile: Any): Any? = (tile as? Tile)?.getTopTopItem() ?: getItems(tile).lastOrNull()

    /** Mirrors `getTopDownItem`; the first down item in the tile list. */
    fun getTopDownItem(tile: Any): Any? = (tile as? Tile)?.getTopDownItem() ?: getItems(tile).firstOrNull()

    /** Mirrors `getFieldItem`; the first `MagicField` on the tile, else null. */
    fun getFieldItem(tile: Any): Any? = (tile as? Tile)?.getFieldItem()

    /**
     * Mirrors `getItemByType(itemType)`: the tile flag gates the search for
     * teleport/magic-field/mailbox/trash-holder/bed/depot, then the ground and the
     * item list are scanned by `ItemType::type`.
     */
    fun getItemByType(tile: Any, itemType: Int): Any? {
        val target = tile as? Tile ?: return null
        val found = when (itemType) {
            ItemTypes_t.ITEM_TYPE_TELEPORT.value -> target.hasFlag(TileFlags.TILESTATE_TELEPORT.value)
            ItemTypes_t.ITEM_TYPE_MAGICFIELD.value -> target.hasFlag(TileFlags.TILESTATE_MAGICFIELD.value)
            ItemTypes_t.ITEM_TYPE_MAILBOX.value -> target.hasFlag(TileFlags.TILESTATE_MAILBOX.value)
            ItemTypes_t.ITEM_TYPE_TRASHHOLDER.value -> target.hasFlag(TileFlags.TILESTATE_TRASHHOLDER.value)
            ItemTypes_t.ITEM_TYPE_BED.value -> target.hasFlag(TileFlags.TILESTATE_BED.value)
            ItemTypes_t.ITEM_TYPE_DEPOT.value -> target.hasFlag(TileFlags.TILESTATE_DEPOT.value)
            else -> true
        }
        if (!found) {
            return null
        }
        target.ground?.let { if (it.type?.type?.value == itemType) return it }
        return target.items.firstOrNull { it.type?.type?.value == itemType }
    }

    /** Mirrors `getItemByTopOrder`; the top item with the requested `alwaysOnTopOrder`. */
    fun getItemByTopOrder(tile: Any, topOrder: Int): Any? = (tile as? Tile)?.getItemByTopOrder(topOrder)

    /** Mirrors `getBottomCreature`; the last creature on the tile, else null. */
    fun getBottomCreature(tile: Any): Any? = (tile as? Tile)?.getBottomCreature()

    /** Mirrors `getTopCreature`; the first creature on the tile, else null. */
    fun getTopCreature(tile: Any): Any? = (tile as? Tile)?.getTopCreature()

    /** Mirrors `getBottomVisibleCreature`; nil when the seeing creature is absent. */
    fun getBottomVisibleCreature(tile: Any, creature: Any?): Any? {
        val seer = creature as? Creature ?: return null
        return (tile as? Tile)?.getBottomVisibleCreature(seer)
    }

    /** Mirrors `getTopVisibleCreature`; nil when the seeing creature is absent. */
    fun getTopVisibleCreature(tile: Any, creature: Any?): Any? {
        val seer = creature as? Creature ?: return null
        return (tile as? Tile)?.getTopVisibleCreature(seer)
    }

    /** The tile item list (ground excluded), matching Canary's `getItems`. */
    fun getItems(tile: Any): List<Any> = (tile as? Tile)?.items?.toList() ?: emptyList()

    fun getItemCount(tile: Any): Int = (tile as? Tile)?.getItemCount() ?: 0

    /** Mirrors `Tile::getItemTypeCount` (ground first, then the item list). */
    fun getItemCountById(tile: Any, itemId: Int, subType: Int): Int =
        (tile as? Tile)?.getItemTypeCount(itemId, subType) ?: 0

    /** Mirrors `Game::findItemOfType` over the tile's `getThing` order. */
    fun getItemById(tile: Any, itemId: Int, subType: Int): Any? {
        val target = tile as? Tile ?: return null
        for (index in 0 until target.getThingCount()) {
            val item = target.getThing(index)?.getItem() ?: continue
            if (item.id == itemId && (subType == -1 || subType == item.getSubType())) {
                return item
            }
        }
        return null
    }

    /** Mirrors `tile:addItem(itemId or name[, count = 1[, flags = 0]])`. */
    fun addItem(tile: Any, itemId: Int, count: Int, flags: Long): Any?

    /**
     * Mirrors `tile:addItemEx(item[, flags = 0])`; null is Canary's nil (item already
     * parented), otherwise the `ReturnValue` ordinal.
     */
    fun addItemEx(tile: Any, item: Any, flags: Long): Int? {
        val target = tile as? Tile ?: return null
        val candidate = item as? Item ?: return null
        if (candidate.getParent() != null) {
            return null
        }
        val ret = target.queryAdd(INDEX_WHEREEVER, candidate, candidate.getItemCount(), flags.toInt())
        if (ret == ReturnValue.NOERROR) {
            target.internalAddThing(candidate)
        }
        return ret.ordinal
    }

    /** Mirrors `tile:queryAdd(thing[, flags = 0])`; returns the `ReturnValue` ordinal. */
    fun queryAdd(tile: Any, thing: Any, flags: Long): Int {
        val target = tile as? Tile ?: return ReturnValue.NOTPOSSIBLE.ordinal
        val cylinder = thing as? Thing ?: return ReturnValue.NOTPOSSIBLE.ordinal
        return target.queryAdd(0, cylinder, 1, flags.toInt()).ordinal
    }

    /** Index of [thing] on the tile, or -1 when it is not on the tile. */
    fun getThingIndex(tile: Any, thing: Any): Int? = (tile as? Tile)?.getThingIndex(thing as? Thing)

    /** Mirrors `getDownItemCount`; the down-item prefix of the tile item list. */
    fun getDownItemCount(tile: Any): Int = (tile as? Tile)?.getDownItemCount() ?: getItems(tile).size

    /** Mirrors `getTopItemCount`; the always-on-top suffix of the tile item list. */
    fun getTopItemCount(tile: Any): Int = (tile as? Tile)?.getTopItemCount() ?: 0

    /** Mirrors `getCreatures`; the tile creature list in Canary's stack order. */
    fun getCreatures(tile: Any): List<Any> = (tile as? Tile)?.getCreatures()?.toList() ?: emptyList()

    /** Mirrors `getCreatureCount`. */
    fun getCreatureCount(tile: Any): Int = (tile as? Tile)?.getCreatureCount() ?: 0

    /** Mirrors `hasProperty(property[, item])`. */
    fun hasProperty(tile: Any, property: Int, item: Any?): Boolean {
        val target = tile as? Tile ?: return false
        val prop = ItemProperty.entries.firstOrNull { it.value == property } ?: return false
        return if (item != null) target.hasProperty(item as? Item, prop) else target.hasProperty(prop)
    }

    /** Mirrors `hasFlag`; false until the flag is set on the tile. */
    fun hasFlag(tile: Any, flag: Int): Boolean = (tile as? Tile)?.hasFlag(flag) ?: false

    /** Mirrors `getHouse`; the linked house for a house tile, else null. */
    fun getHouse(tile: Any): Any? = (tile as? Tile)?.getHouse()

    /** Mirrors `sweep`; null when the actor is missing, not the owner, or the tile is not a house. */
    fun sweep(tile: Any, actor: Any): Boolean? {
        val target = tile as? Tile ?: return null
        val player = actor as? Player ?: return null
        val house = target.getHouse() ?: return null
        if (house.getHouseAccessLevel(player).value < AccessHouseLevel.HOUSE_OWNER.value) {
            return null
        }
        if (target.houseId == 0) {
            return null
        }
        return house.transferToDepot(player, HouseTile(target.position, target.houseId))
    }
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

        env.userdata.registerClass(TILE_TYPE, "", luaFunction { args ->
            val position = tilePosition(args) ?: return@luaFunction LuaValue.NIL
            operations.create(position)?.let { env.userdata.wrap(TILE_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMetaMethod(TILE_TYPE, "__eq", luaFunction { args ->
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
            operations.addItemEx(tile, item, args.arg(3).optlong(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
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

    override fun addItem(tile: Any, itemId: Int, count: Int, flags: Long): Any? =
        throw NotImplementedError("TileFunctions.addItem seam is not wired")
}
