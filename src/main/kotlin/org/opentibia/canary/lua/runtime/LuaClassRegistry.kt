package org.opentibia.canary.lua.runtime

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction

/**
 * Port of the class/userdata registration flow of `Lua`
 * (`src/lua/functions/lua_functions_loader.cpp:747-916` and its declarations in
 * `lua_functions_loader.hpp:450-498`).
 *
 * A registered class is a global table whose metatable is a methods table carrying the
 * optional `__call` constructor and, for a base class, `__index` = the base class
 * table. The registry metatable named after the class stores `__metatable` = the class
 * table (so `getmetatable` returns the class table like Canary), `__index` = the class
 * table, `h` (name hash), `p` (parent depth), `__name`, `baseclass` and `t` (the
 * [LuaDataType] ordinal). Registry metatables live in the same `__metatables__` table
 * the LuaJ compatibility shim's `rawgetmetatable` reads.
 *
 * Deviations forced by the JVM (PARITY GAPS):
 * - `h` uses `String.hashCode()` instead of `std::hash<std::string>` (the field is an
 *   internal identity token, not script-visible data).
 * - `__gc` / `registerSharedClass` keep the C++ signature but LuaJ never invokes
 *   userdata finalizers; shared ownership is the JVM's garbage collector.
 */
class LuaClassRegistry(private val globals: Globals) {

    private val metatables: LuaTable = (globals.get(METATABLES_GLOBAL) as? LuaTable)
        ?: LuaTable().also { globals.set(METATABLES_GLOBAL, it) }

    private val weakMetatables = mutableSetOf<String>()

    /**
     * `Lua::registerClass` (lua_functions_loader.cpp:747-815): publish `className` as
     * a global table, attach its methods metatable and write the registry metatable.
     */
    fun registerClass(className: String, baseClass: String = "", newFunction: LuaValue? = null) {
        val classTable = LuaTable()
        globals.set(className, classTable)

        val methodsTable = LuaTable()
        if (newFunction != null) {
            methodsTable.set("__call", newFunction)
        }

        var parents = 0L
        if (baseClass.isNotEmpty()) {
            val baseClassTable = globals.get(baseClass)
            parents = (metatable(baseClass)?.rawget(PARENTS_KEY)?.tolong() ?: 0L) + 1L
            methodsTable.set("__index", baseClassTable)
        }

        classTable.setmetatable(methodsTable)

        val metatable = LuaTable()
        metatables.set(className, metatable)
        metatable.set("__metatable", classTable)
        metatable.set("__index", classTable)
        metatable.rawset(HASH_KEY, LuaValue.valueOf(className.hashCode().toDouble()))
        metatable.rawset(PARENTS_KEY, LuaValue.valueOf(parents.toDouble()))
        metatable.set("__name", className)
        if (baseClass.isNotEmpty()) {
            metatable.set("baseclass", baseClass)
        }
        metatable.rawset(TYPE_KEY, LuaValue.valueOf(LuaDataType.fromClassName(className).ordinal.toDouble()))
    }

    /**
     * `Lua::registerSharedClass` (lua_functions_loader.cpp:890-893 and the typed
     * `registerSharedClass<T>` overload): a class whose userdata is reference-counted,
     * plus the `__gc` metamethod. LuaJ never runs `__gc`, so the hook is inert.
     */
    fun registerSharedClass(className: String) {
        registerClass(className)
        registerMetaMethod(className, "__gc", luaGarbageCollection)
    }

    /** `Lua::registerMethod` (lua_functions_loader.cpp:817-825). */
    fun registerMethod(globalName: String, methodName: String, func: LuaValue) {
        globals.get(globalName).set(methodName, func)
    }

    /** `Lua::registerMetaMethod` (lua_functions_loader.cpp:833-841). */
    fun registerMetaMethod(className: String, methodName: String, func: LuaValue) {
        metatable(className)?.set(methodName, func)
    }

    /** `Lua::registerTable` (lua_functions_loader.cpp:827-831). */
    fun registerTable(tableName: String) {
        globals.set(tableName, LuaTable())
    }

    /** `Lua::registerVariable` (lua_functions_loader.cpp:843-850). */
    fun registerVariable(tableName: String, name: String, value: Double) {
        globals.get(tableName).set(name, LuaValue.valueOf(value))
    }

    /** `Lua::registerGlobalBoolean` (lua_functions_loader.cpp:852-856). */
    fun registerGlobalBoolean(name: String, value: Boolean) {
        globals.set(name, LuaValue.valueOf(value))
    }

    /** `Lua::registerGlobalMethod` (lua_functions_loader.cpp:858-862). */
    fun registerGlobalMethod(functionName: String, func: LuaValue) {
        globals.set(functionName, func)
    }

    /** `Lua::registerGlobalVariable` (lua_functions_loader.cpp:864-868). */
    fun registerGlobalVariable(name: String, value: Double) {
        globals.set(name, LuaValue.valueOf(value))
    }

    /** `Lua::registerGlobalString` (lua_functions_loader.cpp:870-874). */
    fun registerGlobalString(variable: String, name: String) {
        globals.set(variable, LuaValue.valueOf(name))
    }

    /**
     * `luaL_getmetatable` (no create): the registry metatable named `className`, or
     * null when the class was never registered.
     */
    fun metatable(className: String): LuaTable? = metatables.get(className) as? LuaTable

    /** `Lua::setMetatable` (lua_functions_loader.cpp:275-282). */
    fun setMetatable(value: LuaValue, className: String) {
        metatable(className)?.let { value.setmetatable(it) }
    }

    /**
     * `Lua::setWeakMetatable` (lua_functions_loader.cpp:284-323): build the shared
     * `className_weak` metatable once by copying `__index`, `__metatable`, `__eq`, the
     * `h`/`p`/`t` fields and the name, with `__gc` cleared, then attach it.
     */
    fun setWeakMetatable(value: LuaValue, className: String) {
        val weakName = "${className}_weak"
        if (weakMetatables.add(className)) {
            val child = metatable(className)
            val weak = LuaTable()
            metatables.set(weakName, weak)
            for (key in WEAK_COPIED_STRING_KEYS) {
                weak.set(key, child?.get(key) ?: LuaValue.NIL)
            }
            for (key in WEAK_COPIED_INT_KEYS) {
                weak.rawset(key, child?.rawget(key) ?: LuaValue.NIL)
            }
            weak.set("__gc", LuaValue.NIL)
            weak.set("__name", className)
        }
        metatable(weakName)?.let { value.setmetatable(it) }
    }

    /**
     * `Lua::checkMetatableInheritance` (lua_functions_loader.cpp:918-965): walk
     * `__name` -> `baseclass` -> registry metatable -> `__index` table to decide
     * whether `value` is typed as `expectedName` or one of its descendants.
     */
    fun checkMetatableInheritance(value: LuaValue, expectedName: String): Boolean {
        var metatable = value.getmetatable() ?: return false

        while (true) {
            val name = metatable.get("__name")
            if (name.isstring() && name.tojstring() == expectedName) {
                return true
            }

            val baseClass = metatable.get("baseclass")
            if (baseClass.isstring()) {
                val baseName = baseClass.tojstring()
                if (baseName == expectedName) {
                    return true
                }
                metatable = metatable(baseName) ?: return false
                continue
            }

            val index = metatable.get("__index")
            if (!index.istable()) {
                return false
            }
            metatable = index
        }
    }

    /** `Lua::getUserdataType` (lua_functions_loader.cpp:570-580). */
    fun getUserdataType(value: LuaValue): LuaDataType {
        val type = value.getmetatable()?.rawget(TYPE_KEY)?.toint() ?: 0
        return LuaDataType.entries.getOrNull(type) ?: LuaDataType.Unknown
    }

    /** `Lua::getUserdataTypeName` (lua_functions_loader.cpp:582-584). */
    fun getUserdataTypeName(userType: LuaDataType): String = userType.name

    private val luaGarbageCollection: LuaValue = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = LuaValue.NONE
    }

    private companion object {
        const val METATABLES_GLOBAL = "__metatables__"

        /** `'h'`, `'p'`, `'t'` raw metatable slots (lua_functions_loader.cpp:789-811). */
        val HASH_KEY = 'h'.code
        val PARENTS_KEY = 'p'.code
        val TYPE_KEY = 't'.code

        val WEAK_COPIED_STRING_KEYS = arrayOf("__index", "__metatable", "__eq")
        val WEAK_COPIED_INT_KEYS = intArrayOf(HASH_KEY, PARENTS_KEY, TYPE_KEY)
    }
}
