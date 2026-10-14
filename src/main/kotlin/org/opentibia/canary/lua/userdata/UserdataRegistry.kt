package org.opentibia.canary.lua.userdata

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.runtime.LuaClassRegistry

/**
 * Push-side facade over [LuaClassRegistry] for the Canary-named userdata classes
 * ("Player", "Monster", "Npc", "Item", "Container", "Teleport", ...).
 *
 * Canary registers a class as a global table whose methods metatable carries the
 * optional `__call` constructor and, for a derived class, `__index` = the base class
 * table; the registry metatable stores `__metatable` = the class table, `__index` =
 * the class table, `h`/`p`/`t`, `__name` and `baseclass`
 * (`Lua::registerClass`, `src/lua/functions/lua_functions_loader.cpp:747-815`).
 * [wrap] attaches that registry metatable, so `getmetatable(self)` returns the class
 * table (what `data/libs/functions/revscriptsys.lua` reads), `rawgetmetatable(name)`
 * returns the registry metatable (what the same library mutates) and methods resolve
 * through the `__index` chain up to the base class.
 *
 * Classes are registered lazily on first use so a binding install and a
 * `rawgetmetatable`/`wrap` call always share the same class table and metatable. The
 * base-class map transcribes Canary's `registerSharedClass(..., baseClass, ...)`
 * calls; a binding that passes an explicit base class wins over the map.
 */
class UserdataRegistry(
    private val globals: Globals,
    private val classes: LuaClassRegistry,
) {
    /**
     * `Lua::registerSharedClass` (`lua_functions_loader.cpp:890-893`) for a class that
     * exposes a constructor: the class table becomes the global `typeName` and
     * `newFunction` is installed as its methods metatable `__call`. Canary constructors
     * receive the class table as argument 1; the port's binding lambdas take the user
     * arguments directly, so the adapter strips it.
     */
    fun registerClass(typeName: String, baseClass: String = "", newFunction: LuaValue? = null) {
        ensureClass(typeName, baseClass)
        if (newFunction != null) {
            val classTable = globals.get(typeName)
            if (classTable.istable()) {
                classTable.getmetatable().set("__call", constructor(newFunction))
            }
        }
    }

    /** Registers the class (and its base classes) once; later calls keep the class table. */
    fun ensureClass(typeName: String, baseClass: String = "") {
        if (classes.metatable(typeName) != null) {
            return
        }
        val base = baseClass.ifEmpty { BASE_CLASSES[typeName] ?: "" }
        if (base.isNotEmpty()) {
            ensureClass(base)
        }
        classes.registerSharedClass(typeName, base)
    }

    /** `luaL_getmetatable`: the registry metatable, auto-registering unknown names. */
    fun metatable(typeName: String): LuaTable {
        ensureClass(typeName)
        return classes.metatable(typeName)!!
    }

    /** `Lua::registerMethod`: the method lives on the class table. */
    fun registerMethod(typeName: String, methodName: String, fn: LuaValue) {
        ensureClass(typeName)
        classes.registerMethod(typeName, methodName, fn)
    }

    /** `Lua::registerMetaMethod`: the metamethod lives on the registry metatable. */
    fun registerMetaMethod(typeName: String, methodName: String, fn: LuaValue) {
        ensureClass(typeName)
        classes.registerMetaMethod(typeName, methodName, fn)
    }

    /** `Lua::pushUserdata` + `Lua::setMetatable`: attach the registry metatable. */
    fun wrap(typeName: String, obj: Any): LuaValue {
        ensureClass(typeName)
        return LuaValue.userdataOf(obj, metatable(typeName))
    }

    /** `Lua::setWeakMetatable` (`lua_functions_loader.cpp:284-323`). */
    fun setWeakMetatable(value: LuaValue, typeName: String) {
        ensureClass(typeName)
        classes.setWeakMetatable(value, typeName)
    }

    private fun constructor(newFunction: LuaValue): LuaValue = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = newFunction.invoke(args.subargs(2))
    }

    private companion object {
        /** `registerSharedClass(..., baseClass, ...)` calls (see each C++ init). */
        val BASE_CLASSES = mapOf(
            "Player" to "Creature",
            "Monster" to "Creature",
            "Npc" to "Creature",
            "Container" to "Item",
            "Teleport" to "Item",
        )
    }
}
