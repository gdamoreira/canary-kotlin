package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Item` metatable family. Mirrors the primitives in
 * src/lua/functions/items/item_functions.cpp.
 *
 * Members whose state the Kotlin domain does not model yet (parent links, cloning,
 * custom attribute bags, tile residence) carry a documented default so scripts keep
 * running instead of failing.
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

    /** Mirrors `item:isItem()`; false when the value is not a modeled item. */
    fun isItem(item: Any): Boolean = false

    /** Mirrors `item:getContainer()`; null means Canary's "not a container" (`false`). */
    fun getContainer(item: Any): Any? = null

    /** Mirrors `item:getParent()`; null when no parent linkage is modeled. */
    fun getParent(item: Any): Any? = null

    /** Mirrors `item:getTopParent()`; null when no parent linkage is modeled. */
    fun getTopParent(item: Any): Any? = null

    /** Mirrors `item:clone()`; null when cloning is not modeled. */
    fun clone(item: Any): Any? = null

    /** Mirrors `item:split([count])`; null when splitting is not modeled. */
    fun split(item: Any, count: Int): Any? = null

    /** Mirrors `item:remove([count = -1])`; false when removal is not modeled. */
    fun remove(item: Any, count: Int): Boolean = false

    /** Mirrors `item:getCharges()`; 0 when the attribute is absent. */
    fun getCharges(item: Any): Int = 0

    /** Mirrors `item:getFluidType()`; 0 when the attribute is absent. */
    fun getFluidType(item: Any): Int = 0

    /** Mirrors `item:getWeight()` in Canary's hundredths of an ounce; 0 when unknown. */
    fun getWeight(item: Any): Int = 0

    /** Mirrors `item:getPluralName()`; empty when the attribute/type carries none. */
    fun getPluralName(item: Any): String = ""

    /** Mirrors `item:getArticle()`; empty when the attribute/type carries none. */
    fun getArticle(item: Any): String = ""

    /** Mirrors `item:getPosition()`; the origin when the item has no modeled position. */
    fun getPosition(item: Any): Position = Position(0, 0, 0)

    /** Mirrors `item:getTile()`; null when the item has no modeled tile. */
    fun getTile(item: Any): Any? = null

    /** Mirrors `item:hasAttribute(key)`; false when the attribute is absent. */
    fun hasAttribute(item: Any, attribute: Int): Boolean = false

    /** Mirrors `item:removeAttribute(key)`; false for protected or absent attributes. */
    fun removeAttribute(item: Any, attribute: Int): Boolean = false

    /** Mirrors `item:getCustomAttribute(key)`; null when the custom bag is not modeled. */
    fun getCustomAttribute(item: Any, key: String): Any? = null

    /** Mirrors `item:setCustomAttribute(key, value)`; false when the custom bag is not modeled. */
    fun setCustomAttribute(item: Any, key: String, value: Any?): Boolean = false

    /** Mirrors `item:removeCustomAttribute(key)`; false when the custom bag is not modeled. */
    fun removeCustomAttribute(item: Any, key: String): Boolean = false

    /** Mirrors `item:canBeMoved()`; false when movability is not modeled. */
    fun canBeMoved(item: Any): Boolean = false

    /**
     * Mirrors `item:moveTo(position or cylinder[, flags])`: [destination] is the resolved
     * cylinder userdata (Container/Player/Tile) and [position] the resolved map position.
     */
    fun moveTo(item: Any, destination: Any?, position: Position?, flags: Long): Boolean = false

    /** Mirrors `item:getDescription(distance)`; empty when no description is modeled. */
    fun getDescription(item: Any, distance: Int): String = ""

    /** Mirrors `item:isContainer()`; false when container status is not modeled. */
    fun isContainer(item: Any): Boolean = false
}

/**
 * Registers the Canary `Item` userdata methods:
 *
 *   item:isItem()
 *   item:getContainer()
 *   item:getParent()
 *   item:getTopParent()
 *   item:getId()
 *   item:clone()
 *   item:split([count = 1])
 *   item:remove([count = -1])
 *   item:getUniqueId()
 *   item:getActionId()
 *   item:setActionId(actionId)
 *   item:getCount()
 *   item:getCharges()
 *   item:getFluidType()
 *   item:getWeight()
 *   item:getSubType()
 *   item:getName()
 *   item:getPluralName()
 *   item:getArticle()
 *   item:getPosition()
 *   item:getTile()
 *   item:hasAttribute(key)
 *   item:getAttribute(key)
 *   item:setAttribute(key, value)
 *   item:removeAttribute(key)
 *   item:getCustomAttribute(key)
 *   item:setCustomAttribute(key, value)
 *   item:removeCustomAttribute(key)
 *   item:canBeMoved()
 *   item:moveTo(position or cylinder[, flags])
 *   item:transform(itemId or name[, count/subType = -1])
 *   item:decay(decayId)
 *   item:getDescription(distance)
 *   item:isContainer()
 *
 * The same methods are registered on `Container`, mirroring Canary's shared `Item`
 * class hierarchy.
 */
object ItemFunctions {
    const val ITEM_TYPE = "Item"
    val ITEM_TYPES = listOf(ITEM_TYPE, "Container")

    /** Mirrors Canary `CylinderFlags_t` defaults used by `ItemFunctions::luaItemMoveTo`. */
    private const val DEFAULT_MOVE_TO_FLAGS = (1L shl 0) or (1L shl 1) or (1L shl 2) or (1L shl 6)

    fun install(env: LuaEnvironment, operations: ItemOperations = UnwiredItemOperations) {
        val registry = env.userdata
        for (type in ITEM_TYPES) {
            registry.registerMethod(type, "isItem", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.isItem(item))
            })

            registry.registerMethod(type, "getContainer", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val container = operations.getContainer(item) ?: return@luaFunction LuaValue.FALSE
                env.userdata.wrap("Container", container)
            })

            registry.registerMethod(type, "getParent", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val parent = operations.getParent(item) ?: return@luaFunction LuaValue.NIL
                wrapCylinderUserdata(env, parent)
            })

            registry.registerMethod(type, "getTopParent", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val topParent = operations.getTopParent(item) ?: return@luaFunction LuaValue.NIL
                wrapCylinderUserdata(env, topParent)
            })

            registry.registerMethod(type, "getId", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getId(item))
            })

            registry.registerMethod(type, "clone", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val clone = operations.clone(item) ?: return@luaFunction LuaValue.NIL
                wrapItemUserdata(env, clone)
            })

            registry.registerMethod(type, "split", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val count = args.arg(2).optint(1)
                val split = operations.split(item, count) ?: return@luaFunction LuaValue.NIL
                wrapItemUserdata(env, split)
            })

            registry.registerMethod(type, "remove", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val count = args.arg(2).optint(-1)
                LuaValue.valueOf(operations.remove(item, count))
            })

            registry.registerMethod(type, "getCount", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getCount(item))
            })

            registry.registerMethod(type, "getCharges", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getCharges(item))
            })

            registry.registerMethod(type, "getFluidType", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getFluidType(item))
            })

            registry.registerMethod(type, "getWeight", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getWeight(item))
            })

            registry.registerMethod(type, "getName", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getName(item))
            })

            registry.registerMethod(type, "getPluralName", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getPluralName(item))
            })

            registry.registerMethod(type, "getArticle", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getArticle(item))
            })

            registry.registerMethod(type, "getSubType", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.getSubType(item))
            })

            registry.registerMethod(type, "getPosition", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                env.userdata.wrap(PositionFunctions.POSITION_TYPE, operations.getPosition(item))
            })

            registry.registerMethod(type, "getTile", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val tile = operations.getTile(item) ?: return@luaFunction LuaValue.NIL
                env.userdata.wrap("Tile", tile)
            })

            registry.registerMethod(type, "hasAttribute", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val attribute = resolveItemAttribute(args.arg(2), operations::itemAttributeByName)
                LuaValue.valueOf(operations.hasAttribute(item, attribute))
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

            registry.registerMethod(type, "removeAttribute", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val attribute = resolveItemAttribute(args.arg(2), operations::itemAttributeByName)
                LuaValue.valueOf(operations.removeAttribute(item, attribute))
            })

            registry.registerMethod(type, "getCustomAttribute", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val key = customAttributeKey(args.arg(2)) ?: return@luaFunction LuaValue.NIL
                attributeValueToLua(operations.getCustomAttribute(item, key))
            })

            registry.registerMethod(type, "setCustomAttribute", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val key = customAttributeKey(args.arg(2)) ?: return@luaFunction LuaValue.NIL
                val value = luaToAttributeValue(args.arg(3)) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.setCustomAttribute(item, key, value))
            })

            registry.registerMethod(type, "removeCustomAttribute", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val key = customAttributeKey(args.arg(2)) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.removeCustomAttribute(item, key))
            })

            registry.registerMethod(type, "canBeMoved", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.canBeMoved(item))
            })

            registry.registerMethod(type, "moveTo", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val rawTarget = args.arg(2)
                val target = retrieveUserdata(args, 2)
                val destination = target?.takeIf { it !is Position }
                val position = when {
                    target is Position -> target
                    destination == null -> luaToPosition(rawTarget)
                    else -> null
                }
                val flags = args.arg(3).optlong(DEFAULT_MOVE_TO_FLAGS)
                LuaValue.valueOf(operations.moveTo(item, destination, position, flags))
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

            registry.registerMethod(type, "getDescription", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                val distance = args.arg(2).toint()
                LuaValue.valueOf(operations.getDescription(item, distance))
            })

            registry.registerMethod(type, "isContainer", luaFunction { args ->
                val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
                LuaValue.valueOf(operations.isContainer(item))
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
        }
    }

    /**
     * Parses Canary's `item:getAttribute/setAttribute/hasAttribute(key)` argument: a numeric
     * attribute id or a string attribute name resolved through [ItemOperations.itemAttributeByName].
     * Anything else mirrors Canary's `ItemAttribute_t::NONE` (0).
     */
    private fun resolveItemAttribute(value: LuaValue, byName: (String) -> Int): Int = when {
        value.isnumber() -> value.toint()
        value.isstring() -> byName(value.tojstring())
        else -> 0
    }

    /**
     * Mirrors `item:get/setCustomAttribute(key)`: numbers become their int64 decimal string,
     * strings are used verbatim, anything else is rejected.
     */
    private fun customAttributeKey(value: LuaValue): String? = when {
        value.isnumber() -> value.tolong().toString()
        value.isstring() -> value.tojstring()
        else -> null
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
