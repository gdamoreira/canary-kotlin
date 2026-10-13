package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Value kinds of Canary's `ValueWrapper` (`src/kv/value_wrapper.hpp:24-60`):
 * `StringType`, `BooleanType`, `IntType`, `DoubleType`, `ArrayType`, `MapType`.
 *
 * `getValueWrapper` (kv_functions.cpp:135-182) stores every Lua number as a double,
 * so [KvNumber] is the only numeric kind the Lua bridge produces.
 */
sealed interface KvValue {
    data class KvString(val value: String) : KvValue
    data class KvBoolean(val value: Boolean) : KvValue
    data class KvNumber(val value: Double) : KvValue
    data class KvArray(val values: List<KvValue>) : KvValue
    data class KvMap(val values: Map<String, KvValue>) : KvValue
}

/**
 * Port of Canary's `KV` interface (`src/kv/kv.hpp:34-79`) without persistence:
 * [KVStore] holds the in-memory entries and [ScopedKV] prefixes every key with its
 * scope, exactly like the C++ pair.
 */
interface KV {
    /** `KV::set(key, value)`. */
    fun set(key: String, value: KvValue)

    /** `KV::get(key[, forceLoad])`; null mirrors the `std::nullopt` miss. */
    fun get(key: String, forceLoad: Boolean = false): KvValue?

    /** `KV::scoped(scope)`. */
    fun scoped(scope: String): KV

    /** `KV::keys([prefix])`. */
    fun keys(prefix: String = ""): Set<String>

    /** `KV::remove(key)` (`kv.cpp:120-122`). */
    fun remove(key: String)
}

/**
 * In-memory `KVStore` (`src/kv/kv.cpp`): a max-size bounded key/value store. The C++
 * persistence and the one-million-entry LRU eviction are documented parity gaps; the
 * observable Lua contract (set/get/keys/remove under scopes) is reproduced.
 */
class KVStore(private val maxSize: Int = MAX_SIZE) : KV {
    private val store = LinkedHashMap<String, KvValue>()

    override fun set(key: String, value: KvValue) {
        store.remove(key)
        store[key] = value
        while (store.size > maxSize) {
            val eldest = store.keys.firstOrNull() ?: break
            store.remove(eldest)
        }
    }

    override fun get(key: String, forceLoad: Boolean): KvValue? = store[key]

    override fun scoped(scope: String): KV = ScopedKV(this, scope)

    override fun keys(prefix: String): Set<String> =
        store.keys.filterTo(linkedSetOf()) { it.startsWith(prefix) }

    override fun remove(key: String) {
        store.remove(key)
    }

    companion object {
        /** `KVStore::MAX_SIZE` (`src/kv/kv.hpp:95`). */
        const val MAX_SIZE: Int = 1_000_000
    }
}

/**
 * `ScopedKV` (`src/kv/kv.hpp:157-208`): every operation is delegated to the root store
 * with `"{prefix}.{key}"`, and [keys] delegates the prefixed key to the root.
 */
class ScopedKV(private val root: KVStore, private val prefix: String) : KV {
    override fun set(key: String, value: KvValue) = root.set(buildKey(key), value)

    override fun get(key: String, forceLoad: Boolean): KvValue? = root.get(buildKey(key), forceLoad)

    override fun scoped(scope: String): KV = ScopedKV(root, buildKey(scope))

    override fun keys(prefix: String): Set<String> = root.keys(buildKey(prefix))

    override fun remove(key: String) = root.remove(buildKey(key))

    private fun buildKey(key: String): String = "$prefix.$key"
}

/**
 * Engine seam for the KV binding: resolves the target store for a call and forwards
 * set/get/keys/remove, mirroring `KVFunctions::luaKV*`'s `isUserdata(L, 1)` branch
 * (src/lua/functions/core/libs/kv_functions.cpp:34-133).
 *
 * [scoped] receives the receiver (`null` for the global `kv` table, the method
 * receiver otherwise) and the scope key.
 */
interface KvOperations {
    /** The `g_kv()` root store the `kv` table operates on. */
    fun root(): KV

    fun scoped(target: Any?, scope: String): KV
    fun set(target: KV, key: String, value: KvValue)
    fun get(target: KV, key: String, forceLoad: Boolean): KvValue?
    fun keys(target: KV, prefix: String): Set<String>
    fun remove(target: KV, key: String)
}

/** [KvOperations] over the given root store (`g_kv()` stands in for [store]). */
class WorldKvOperations(private val store: KV = KVStore()) : KvOperations {
    override fun root(): KV = store

    override fun scoped(target: Any?, scope: String): KV =
        (target as? KV)?.scoped(scope) ?: store.scoped(scope)

    override fun set(target: KV, key: String, value: KvValue) = target.set(key, value)

    override fun get(target: KV, key: String, forceLoad: Boolean): KvValue? = target.get(key, forceLoad)

    override fun keys(target: KV, prefix: String): Set<String> = target.keys(prefix)

    override fun remove(target: KV, key: String) = target.remove(key)
}

/**
 * `KVFunctions::getValueWrapper` (kv_functions.cpp:135-182): boolean, number and
 * string map directly; a table with a positive length is an array, any other table a
 * string-keyed map; nil and unsupported values return null (the C++ invalid-param
 * warning + `std::nullopt`).
 */
internal fun luaToKvValue(value: LuaValue): KvValue? = when {
    value.isboolean() -> KvValue.KvBoolean(value.toboolean())
    value.isnumber() -> KvValue.KvNumber(value.todouble())
    value.isstring() -> KvValue.KvString(value.tojstring())
    value.istable() -> {
        val table = value.checktable()
        val length = table.length()
        if (length > 0) tableToKvArray(table, length) else tableToKvMap(table)
    }

    else -> null
}

private fun tableToKvArray(table: LuaTable, length: Int): KvValue? {
    val values = ArrayList<KvValue>(length)
    for (index in 1..length) {
        val entry = luaToKvValue(table.get(index)) ?: return null
        values += entry
    }
    return KvValue.KvArray(values)
}

private fun tableToKvMap(table: LuaTable): KvValue? {
    val values = LinkedHashMap<String, KvValue>()
    var failed = false
    forEachTableEntry(table) { key, value ->
        if (failed) {
            return@forEachTableEntry
        }
        val converted = luaToKvValue(value)
        if (converted == null) {
            failed = true
        } else {
            values[key.tojstring()] = converted
        }
    }
    return if (failed) null else KvValue.KvMap(values)
}

/** `KVFunctions::pushValueWrapper` (kv_functions.cpp:212-232). */
internal fun kvValueToLua(value: KvValue): LuaValue = when (value) {
    is KvValue.KvString -> LuaValue.valueOf(value.value)
    is KvValue.KvBoolean -> LuaValue.valueOf(value.value)
    is KvValue.KvNumber -> LuaValue.valueOf(value.value)
    is KvValue.KvArray -> luaArrayOf(value.values.map(::kvValueToLua))
    is KvValue.KvMap -> LuaTable().also { table ->
        value.values.forEach { (key, entry) -> table.set(key, kvValueToLua(entry)) }
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
            val value = luaToKvValue(args.arg(args.narg()))
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
            operations.get(target, key, forceLoad)?.let(::kvValueToLua) ?: LuaValue.NIL
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
