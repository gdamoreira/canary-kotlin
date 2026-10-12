package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `Container` metatable. Mirrors the read-only primitives in
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
}

/**
 * Registers the Canary `Container` userdata methods:
 *
 *   container:getSize()
 *   container:getMaxCapacity()
 *   container:getCapacity()
 *   container:getEmptySlots([recursive = false])
 *   container:getItemHoldingCount()
 *   container:getItemCountById(itemId or name[, subType = -1])
 *   container:getItem(index)
 *   container:hasItem(item)
 *   container:getItems([recursive = false])
 *
 * The registry has no class-inheritance chain, so the shared `Item` methods continue to
 * be registered on `Container` by [ItemFunctions] while these container-only methods are
 * registered here, keeping the same Lua-visible API as Canary.
 */
object ContainerFunctions {
    const val CONTAINER_TYPE = "Container"

    fun install(env: LuaEnvironment, operations: ContainerOperations = UnwiredContainerOperations) {
        val registry = env.userdata

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
    }
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
}
