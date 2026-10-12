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

    /** Resolves an item attribute name to its id, returning 0 when unknown. */
    fun itemAttributeByName(name: String): Int

    fun getId(item: Any): Int
    fun getCount(item: Any): Int
    fun getName(item: Any): String
    fun getSubType(item: Any): Int
    fun transform(item: Any, itemId: Int, subType: Int): Boolean
    fun decay(item: Any, decayId: Int): Boolean
    fun getActionId(item: Any): Int
    fun setActionId(item: Any, actionId: Int): Boolean
    fun getUniqueId(item: Any): Long
    fun getAttribute(item: Any, attribute: Int): Any?
    fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean
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
 *   item:getActionId()
 *   item:setActionId(actionId)
 *   item:getUniqueId()
 *   item:getAttribute(key)
 *   item:setAttribute(key, value)
 *
 * The same methods are registered on `Container`, mirroring Canary's shared `Item`
 * class hierarchy.
 */
object ItemFunctions {
    const val ITEM_TYPE = "Item"
    val ITEM_TYPES = listOf(ITEM_TYPE, "Container")

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

            registry.registerMethod(type, "getActionId", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getActionId(item))
            })

            registry.registerMethod(type, "setActionId", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val actionId = args.arg(2).toint()
                LuaValue.valueOf(operations.setActionId(item, actionId))
            })

            registry.registerMethod(type, "getUniqueId", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getUniqueId(item).toDouble())
            })

            registry.registerMethod(type, "getAttribute", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val attribute = resolveItemAttribute(args.arg(2), operations::itemAttributeByName)
                attributeValueToLua(operations.getAttribute(item, attribute))
            })

            registry.registerMethod(type, "setAttribute", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val attribute = resolveItemAttribute(args.arg(2), operations::itemAttributeByName)
                val value = luaToAttributeValue(args.arg(3))
                LuaValue.valueOf(operations.setAttribute(item, attribute, value))
            })
        }
    }

    /**
     * Parses Canary's `item:getAttribute/setAttribute(key)` argument: a numeric attribute id
     * or a string attribute name resolved through [ItemOperations.itemAttributeByName].
     * Anything else mirrors Canary's `ItemAttribute_t::NONE` (0).
     */
    private fun resolveItemAttribute(value: LuaValue, byName: (String) -> Int): Int = when {
        value.isnumber() -> value.toint()
        value.isstring() -> byName(value.tojstring())
        else -> 0
    }
}

internal object UnwiredItemOperations : ItemOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("ItemFunctions.itemIdByName seam is not wired")

    override fun itemAttributeByName(name: String): Int =
        throw NotImplementedError("ItemFunctions.itemAttributeByName seam is not wired")

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

    override fun getActionId(item: Any): Int =
        throw NotImplementedError("ItemFunctions.getActionId seam is not wired")

    override fun setActionId(item: Any, actionId: Int): Boolean =
        throw NotImplementedError("ItemFunctions.setActionId seam is not wired")

    override fun getUniqueId(item: Any): Long =
        throw NotImplementedError("ItemFunctions.getUniqueId seam is not wired")

    override fun getAttribute(item: Any, attribute: Int): Any? =
        throw NotImplementedError("ItemFunctions.getAttribute seam is not wired")

    override fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean =
        throw NotImplementedError("ItemFunctions.setAttribute seam is not wired")
}
