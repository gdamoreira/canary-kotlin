package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.kv.KV
import org.opentibia.canary.kv.KVMemory
import org.opentibia.canary.kv.ValueVariant
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the KV binding: resolves the target store for a call and forwards
 * set/get/keys/remove over the ported [KV] engine (`src/kv/kv.hpp`), mirroring
 * `KVFunctions::luaKV*`'s `isUserdata(L, 1)` branch
 * (src/lua/functions/core/libs/kv_functions.cpp:34-133).
 *
 * [scoped] receives the receiver (`null` for the global `kv` table, the method
 * receiver otherwise) and the scope key.
 */
interface KvOperations {
    /** The `g_kv()` root store the `kv` table operates on. */
    fun root(): KV

    fun scoped(target: Any?, scope: String): KV
    fun set(target: KV, key: String, value: ValueWrapper)
    fun get(target: KV, key: String, forceLoad: Boolean): ValueWrapper?
    fun keys(target: KV, prefix: String): Set<String>
    fun remove(target: KV, key: String)
}

/**
 * [KvOperations] over the given root store. `g_kv()` is injected as [store]; when no
 * SQL-backed store is supplied the [KVMemory] fixture stands in for `KVSQL`
 * (`src/kv/kv_sql.cpp`).
 */
class WorldKvOperations(private val store: KV = KVMemory()) : KvOperations {
    override fun root(): KV = store

    override fun scoped(target: Any?, scope: String): KV =
        (target as? KV)?.scoped(scope) ?: store.scoped(scope)

    override fun set(target: KV, key: String, value: ValueWrapper) = target.set(key, value)

    override fun get(target: KV, key: String, forceLoad: Boolean): ValueWrapper? = target.get(key, forceLoad)

    override fun keys(target: KV, prefix: String): Set<String> = target.keys(prefix)

    override fun remove(target: KV, key: String) = target.remove(key)
}

/**
 * `KVFunctions::getValueWrapper` (kv_functions.cpp:135-182): boolean, number and
 * string map directly; a table with a positive length is an array, any other table a
 * string-keyed map; nil and unsupported values return null (the C++ invalid-param
 * warning + `std::nullopt`).
 */
internal fun luaToValueWrapper(value: LuaValue): ValueWrapper? = when {
    value.isboolean() -> ValueWrapper(value.toboolean())
    value.isnumber() -> ValueWrapper(value.todouble())
    value.isstring() -> ValueWrapper(value.tojstring())
    value.istable() -> {
        val table = value.checktable()
        val length = table.length()
        if (length > 0) tableToValueWrapperArray(table, length) else tableToValueWrapperMap(table)
    }

    else -> null
}

private fun tableToValueWrapperArray(table: LuaTable, length: Int): ValueWrapper? {
    val values = ArrayList<ValueWrapper>(length)
    for (index in 1..length) {
        val entry = luaToValueWrapper(table.get(index)) ?: return null
        values += entry
    }
    return ValueWrapper(values)
}

private fun tableToValueWrapperMap(table: LuaTable): ValueWrapper? {
    val values = LinkedHashMap<String, ValueWrapper>()
    var failed = false
    forEachTableEntry(table) { key, value ->
        if (failed) {
            return@forEachTableEntry
        }
        val converted = luaToValueWrapper(value)
        if (converted == null) {
            failed = true
        } else {
            values[key.tojstring()] = converted
        }
    }
    return if (failed) null else ValueWrapper(values)
}

/** `KVFunctions::pushValueWrapper` (kv_functions.cpp:212-232). */
internal fun valueWrapperToLua(value: ValueWrapper): LuaValue = when (val variant = value.getVariant()) {
    is ValueVariant.StringValue -> LuaValue.valueOf(variant.value)
    is ValueVariant.BooleanValue -> LuaValue.valueOf(variant.value)
    is ValueVariant.IntValue -> LuaValue.valueOf(variant.value.toDouble())
    is ValueVariant.DoubleValue -> LuaValue.valueOf(variant.value)
    is ValueVariant.ArrayValue -> luaArrayOf(variant.values.map(::valueWrapperToLua))
    is ValueVariant.MapValue -> LuaTable().also { table ->
        variant.values.forEach { (key, entry) -> table.set(key, valueWrapperToLua(entry)) }
    }
}

/**
 * Registers Canary's KV interface (src/lua/functions/core/libs/kv_functions.cpp:18-32):
 *
 *   kv.scoped(key) | kv:scoped(key)
 *   kv.set(key, value) | scopedKV:set(key, value)
 *   kv.get(key[, forceLoad = false]) | scopedKV:get(key[, forceLoad = false])
 *   kv.keys([prefix = ""]) | scopedKV:keys([prefix = ""])
 *   kv.remove(key) | scopedKV:remove(key)
 *
 * and the `KV` userdata class carrying the same five methods, like Canary's
 * `registerSharedClass<KV>` + `registerMethod(L, "KV", ...)`.
 */
object KvFunctions {
    const val KV_GLOBAL = "kv"
    const val KV_TYPE = "KV"

    fun install(env: LuaEnvironment, operations: KvOperations = WorldKvOperations()) {
        val root = operations.root()
        val metatable = env.userdata.metatable(KV_TYPE)
        if (metatable.get("__index").isnil()) {
            metatable.set("__index", metatable)
        }
        env.globals.set(KV_TYPE, metatable)

        val kv = (env.globals.get(KV_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(KV_GLOBAL, it) }

        val scoped = libraryFunction { args ->
            val key = luaArgString(args, args.narg())
            val target = args.arg(1).touserdata()
            env.userdata.wrap(KV_TYPE, operations.scoped(target, key))
        }
        kv.set("scoped", scoped)
        metatable.set("scoped", scoped)

        val set = libraryFunction { args ->
            val value = luaToValueWrapper(args.arg(args.narg()))
            if (value == null) {
                return@libraryFunction LuaValue.FALSE
            }
            val target = args.arg(1).touserdata() as? KV ?: root
            operations.set(target, luaArgString(args, args.narg() - 1), value)
            LuaValue.TRUE
        }
        kv.set("set", set)
        metatable.set("set", set)

        val get = libraryFunction { args ->
            val receiver = args.arg(1).touserdata() as? KV
            var keyIndex = args.narg()
            var forceLoad = false
            if (args.arg(args.narg()).isboolean()) {
                forceLoad = args.arg(args.narg()).toboolean()
                keyIndex--
            }
            val key = luaArgString(args, keyIndex)
            val target = receiver ?: root
            operations.get(target, key, forceLoad)?.let(::valueWrapperToLua) ?: LuaValue.NIL
        }
        kv.set("get", get)
        metatable.set("get", get)

        val keys = libraryFunction { args ->
            val receiver = args.arg(1).touserdata() as? KV
            val top = args.arg(args.narg())
            val prefix = if (top.type() == LuaValue.TSTRING) top.tojstring() else ""
            val target = receiver ?: root
            val result = if (receiver != null) {
                operations.keys(target, "")
            } else {
                operations.keys(target, prefix)
            }
            luaArrayOf(result.map(LuaValue::valueOf))
        }
        kv.set("keys", keys)
        metatable.set("keys", keys)

        val remove = libraryFunction { args ->
            val receiver = args.arg(1).touserdata() as? KV
            val target = receiver ?: root
            operations.remove(target, luaArgString(args, args.narg()))
            LuaValue.NIL
        }
        kv.set("remove", remove)
        metatable.set("remove", remove)
    }
}
