package org.opentibia.canary.creatures.players.components

/**
 * Minimal port of Canary's `KV` / `ScopedKV` (`src/kv/kv.hpp:26-186`,
 * `src/kv/kv.cpp:70-127`) covering the numeric entries the player title,
 * badge and achievement components read and write.
 *
 * A scoped handle composes keys as `"{prefix}.{key}"` exactly like
 * `ScopedKV::buildKey` (kv.hpp:179-181) and shares the same underlying store
 * as its root, so `titles.unlocked.{name}` is the key the C++ title component
 * writes through `m_player.kv()->scoped("titles")->scoped("unlocked")`
 * (player_title.cpp:174-180).
 *
 * PARITY NOTES:
 *  - values are `Long` only; the C++ `ValueWrapper` also carries strings,
 *    booleans and lists, none of which these components store.
 *  - `get(forceLoad)` ignores the flag: the in-memory store has no lazy
 *    database backing (`KVStore::get`, kv.cpp:70-98).
 *  - `remove` drops the entry instead of writing the C++ deleted marker
 *    (`KV::remove`, kv.cpp:120-122); `get`/`keys` observe the same state.
 */
interface KV {
    fun set(key: String, value: Long)

    fun get(key: String, forceLoad: Boolean = false): Long?

    fun remove(key: String)

    fun scoped(scope: String): KV

    fun keys(prefix: String = ""): Set<String>
}

/**
 * In-memory [KV] used for local runs and tests, standing in for the SQL-backed
 * `KVSQL` (`src/kv/kv_sql.cpp`). `keys` strips the composed scope prefix from
 * each stored key, mirroring `ScopedKV::keys` (kv.hpp:174-176) delegating to
 * `KVStore::keys` (kv.cpp:100-118).
 */
class InMemoryKV private constructor(
    private val store: MutableMap<String, Long>,
    private val prefix: String,
) : KV {

    constructor() : this(linkedMapOf(), "")

    override fun set(key: String, value: Long) {
        store[buildKey(key)] = value
    }

    override fun get(key: String, forceLoad: Boolean): Long? = store[buildKey(key)]

    override fun remove(key: String) {
        store.remove(buildKey(key))
    }

    override fun scoped(scope: String): KV = InMemoryKV(store, buildKey(scope))

    override fun keys(prefix: String): Set<String> {
        val fullPrefix = buildKey(prefix)
        return store.keys
            .filter { it.startsWith(fullPrefix) }
            .mapTo(linkedSetOf()) { it.substring(fullPrefix.length) }
    }

    private fun buildKey(key: String): String =
        if (prefix.isEmpty()) key else "$prefix.$key"
}
