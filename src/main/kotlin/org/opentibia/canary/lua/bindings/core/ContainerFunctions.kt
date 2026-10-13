package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `Container` metatable. Mirrors the primitives in
 * src/lua/functions/items/container_functions.cpp.
 */
interface ContainerOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    fun getSize(container: Any): Int
    fun getMaxCapacity(container: Any): Int
    fun getCapacity(container: Any): Int
    fun getEmptySlots(container: Any, recursive: Boolean): Int
    fun getItemHoldingCount(container: Any): Int
    fun getItemCountById(container: Any, itemId: Int, subType: Int): Int
    fun getItem(container: Any, index: Int): Any?
    fun hasItem(container: Any, item: Any?): Boolean

    /** Returns the container contents, flattened when [recursive] is true. */
    fun getItems(container: Any, recursive: Boolean): List<Any>

    /** Mirrors `container:getContentDescription([oldProtocol = false])`. */
    fun getContentDescription(container: Any, oldProtocol: Boolean): String

    /**
     * Mirrors `container:addItem(itemId or name[, count/subType = 1[, index = INDEX_WHEREEVER[, flags = 0]]])`;
     * null means the item could not be added (the binding then returns `false`).
     */
    fun addItem(container: Any, itemId: Int, count: Int, index: Int, flags: Long): Any?

    /** Mirrors `container:addItemEx(item[, index = INDEX_WHEREEVER[, flags = 0]])`; returns a `ReturnValue`. */
    fun addItemEx(container: Any, item: Any, index: Int, flags: Long): Int

    /** Mirrors `container:getCorpseOwner()` (the `CORPSEOWNER` attribute). */
    fun getCorpseOwner(container: Any): Long

    /** Mirrors `container:registerReward()`. */
    fun registerReward(container: Any): Boolean

    /** Mirrors `container:removeAllItems(actor = nil, recursive = false)`. */
    fun removeAllItems(container: Any, actor: Any?, recursive: Boolean): Long

    /** Mirrors `container:removeItemById(itemId, count[, subType = -1])`. */
    fun removeItemById(container: Any, itemId: Int, count: Int, subType: Int): Boolean
}

/**
 * Registers the Canary `Container` userdata methods:
 *
 *   container:getSize()
 *   container:getMaxCapacity()
 *   container:getCapacity()
 *   container:getEmptySlots([recursive = false])
 *   container:getContentDescription([oldProtocol = false])
 *   container:getItemHoldingCount()
 *   container:getItemCountById(itemId or name[, subType = -1])
 *   container:getItem(index)
 *   container:hasItem(item)
 *   container:getItems([recursive = false])
 *   container:addItem(itemId or name[, count/subType = 1[, index = INDEX_WHEREEVER[, flags = 0]]])
 *   container:addItemEx(item[, index = INDEX_WHEREEVER[, flags = 0]])
 *   container:getCorpseOwner()
 *   container:registerReward()
 *   container:removeAllItems(actor = nil, recursive = false)
 *   container:removeItemById(itemId, count[, subType = -1])
 *
 * The registry has no class-inheritance chain, so the shared `Item` methods continue to
 * be registered on `Container` by [ItemFunctions] while these container-only methods are
 * registered here, keeping the same Lua-visible API as Canary.
 */
object ContainerFunctions {
    const val CONTAINER_TYPE = "Container"

    fun install(env: LuaEnvironment, operations: ContainerOperations = UnwiredContainerOperations) {
        val registry = env.userdata

        registry.registerMethod(CONTAINER_TYPE, "__eq", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val other = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(container == other)
        })

        registry.registerMethod(CONTAINER_TYPE, "getSize", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getSize(container))
        })

        registry.registerMethod(CONTAINER_TYPE, "getMaxCapacity", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getMaxCapacity(container))
        })

        registry.registerMethod(CONTAINER_TYPE, "getCapacity", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getCapacity(container))
        })

        registry.registerMethod(CONTAINER_TYPE, "getEmptySlots", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val recursive = args.arg(2).optboolean(false)
            LuaValue.valueOf(operations.getEmptySlots(container, recursive))
        })

        registry.registerMethod(CONTAINER_TYPE, "getItemHoldingCount", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getItemHoldingCount(container))
        })

        registry.registerMethod(CONTAINER_TYPE, "getItemCountById", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val subType = args.arg(3).optint(-1)
            LuaValue.valueOf(operations.getItemCountById(container, itemId, subType))
        })

        registry.registerMethod(CONTAINER_TYPE, "getItem", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val index = args.arg(2).toint()
            val item = operations.getItem(container, index) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(ItemFunctions.ITEM_TYPE, item)
        })

        registry.registerMethod(CONTAINER_TYPE, "hasItem", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = retrieveUserdata(args, 2)
            LuaValue.valueOf(operations.hasItem(container, item))
        })

        registry.registerMethod(CONTAINER_TYPE, "getItems", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val recursive = args.arg(2).optboolean(false)
            val items = operations.getItems(container, recursive)
            LuaTable().also { table ->
                items.forEachIndexed { index, item -> table.set(index + 1, env.userdata.wrap(ItemFunctions.ITEM_TYPE, item)) }
            }
        })

        registry.registerMethod(CONTAINER_TYPE, "getContentDescription", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val oldProtocol = args.arg(2).optboolean(false)
            LuaValue.valueOf(operations.getContentDescription(container, oldProtocol))
        })

        registry.registerMethod(CONTAINER_TYPE, "addItem", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(3).optint(1)
            val index = args.arg(4).optint(INDEX_WHEREEVER)
            val flags = args.arg(5).optlong(0)
            val item = operations.addItem(container, itemId, count, index, flags)
                ?: return@luaFunction LuaValue.FALSE
            wrapItemUserdata(env, item)
        })

        registry.registerMethod(CONTAINER_TYPE, "addItemEx", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            val index = args.arg(3).optint(INDEX_WHEREEVER)
            val flags = args.arg(4).optlong(0)
            LuaValue.valueOf(operations.addItemEx(container, item, index, flags))
        })

        registry.registerMethod(CONTAINER_TYPE, "getCorpseOwner", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getCorpseOwner(container).toDouble())
        })

        registry.registerMethod(CONTAINER_TYPE, "registerReward", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.registerReward(container))
        })

        registry.registerMethod(CONTAINER_TYPE, "removeAllItems", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val actor = retrieveUserdata(args, 2)
            val recursive = args.arg(3).optboolean(false)
            LuaValue.valueOf(operations.removeAllItems(container, actor, recursive).toDouble())
        })

        registry.registerMethod(CONTAINER_TYPE, "removeItemById", luaFunction { args ->
            val container = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(3).optint(1)
            val subType = args.arg(4).optint(-1)
            LuaValue.valueOf(operations.removeItemById(container, itemId, count, subType))
        })
    }

    /** Mirrors `INDEX_WHEREEVER` (src/items/cylinder.hpp:18). */
    private const val INDEX_WHEREEVER = -1
}

internal object UnwiredContainerOperations : ContainerOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("ContainerFunctions.itemIdByName seam is not wired")

    override fun getSize(container: Any): Int =
        throw NotImplementedError("ContainerFunctions.getSize seam is not wired")

    override fun getMaxCapacity(container: Any): Int =
        throw NotImplementedError("ContainerFunctions.getMaxCapacity seam is not wired")

    override fun getCapacity(container: Any): Int =
        throw NotImplementedError("ContainerFunctions.getCapacity seam is not wired")

    override fun getEmptySlots(container: Any, recursive: Boolean): Int =
        throw NotImplementedError("ContainerFunctions.getEmptySlots seam is not wired")

    override fun getItemHoldingCount(container: Any): Int =
        throw NotImplementedError("ContainerFunctions.getItemHoldingCount seam is not wired")

    override fun getItemCountById(container: Any, itemId: Int, subType: Int): Int =
        throw NotImplementedError("ContainerFunctions.getItemCountById seam is not wired")

    override fun getItem(container: Any, index: Int): Any? =
        throw NotImplementedError("ContainerFunctions.getItem seam is not wired")

    override fun hasItem(container: Any, item: Any?): Boolean =
        throw NotImplementedError("ContainerFunctions.hasItem seam is not wired")

    override fun getItems(container: Any, recursive: Boolean): List<Any> =
        throw NotImplementedError("ContainerFunctions.getItems seam is not wired")

    override fun getContentDescription(container: Any, oldProtocol: Boolean): String =
        throw NotImplementedError("ContainerFunctions.getContentDescription seam is not wired")

    override fun addItem(container: Any, itemId: Int, count: Int, index: Int, flags: Long): Any? =
        throw NotImplementedError("ContainerFunctions.addItem seam is not wired")

    override fun addItemEx(container: Any, item: Any, index: Int, flags: Long): Int =
        throw NotImplementedError("ContainerFunctions.addItemEx seam is not wired")

    override fun getCorpseOwner(container: Any): Long =
        throw NotImplementedError("ContainerFunctions.getCorpseOwner seam is not wired")

    override fun registerReward(container: Any): Boolean =
        throw NotImplementedError("ContainerFunctions.registerReward seam is not wired")

    override fun removeAllItems(container: Any, actor: Any?, recursive: Boolean): Long =
        throw NotImplementedError("ContainerFunctions.removeAllItems seam is not wired")

    override fun removeItemById(container: Any, itemId: Int, count: Int, subType: Int): Boolean =
        throw NotImplementedError("ContainerFunctions.removeItemById seam is not wired")
}
