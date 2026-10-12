package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `Item` metatable family. Mirrors the primitives in
 * src/lua/functions/items/item_functions.cpp.
 */
interface ItemOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    fun getId(item: Any): Int
    fun getCount(item: Any): Int
    fun getName(item: Any): String
    fun getSubType(item: Any): Int
    fun transform(item: Any, itemId: Int, subType: Int): Boolean
    fun decay(item: Any, decayId: Int): Boolean
}

/**
 * Registers the Canary `Item` userdata methods:
 *
 *   item:getId()
 *   item:getCount()
 *   item:getName()
 *   item:getSubType()
 *   item:transform(itemId or name[, count/subType = -1])
 *   item:decay(decayId)
 *
 * The same methods are registered on `Container`, mirroring Canary's shared `Item`
 * class hierarchy.
 */
object ItemFunctions {
    val ITEM_TYPES = listOf("Item", "Container")

    fun install(env: LuaEnvironment, operations: ItemOperations = UnwiredItemOperations) {
        val registry = env.userdata
        for (type in ITEM_TYPES) {
            registry.registerMethod(type, "getId", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getId(item))
            })

            registry.registerMethod(type, "getCount", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getCount(item))
            })

            registry.registerMethod(type, "getName", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getName(item))
            })

            registry.registerMethod(type, "getSubType", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getSubType(item))
            })

            registry.registerMethod(type, "transform", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
                val subType = args.arg(3).optint(-1)
                LuaValue.valueOf(operations.transform(item, itemId, subType))
            })

            registry.registerMethod(type, "decay", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val decayId = args.arg(2).toint()
                LuaValue.valueOf(operations.decay(item, decayId))
            })
        }
    }
}

internal object UnwiredItemOperations : ItemOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("ItemFunctions.itemIdByName seam is not wired")

    override fun getId(item: Any): Int =
        throw NotImplementedError("ItemFunctions.getId seam is not wired")

    override fun getCount(item: Any): Int =
        throw NotImplementedError("ItemFunctions.getCount seam is not wired")

    override fun getName(item: Any): String =
        throw NotImplementedError("ItemFunctions.getName seam is not wired")

    override fun getSubType(item: Any): Int =
        throw NotImplementedError("ItemFunctions.getSubType seam is not wired")

    override fun transform(item: Any, itemId: Int, subType: Int): Boolean =
        throw NotImplementedError("ItemFunctions.transform seam is not wired")

    override fun decay(item: Any, decayId: Int): Boolean =
        throw NotImplementedError("ItemFunctions.decay seam is not wired")
}
