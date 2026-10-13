package org.opentibia.canary.kv

/**
 * Port of the `KVMemory` fixture (`tests/fixture/kv/in_memory_kv.hpp`): a [KVStore]
 * whose persistence hooks are no-ops, used by the C++ unit tests and by the Lua `kv`
 * seam when no SQL-backed store is injected.
 *
 * PARITY NOTE: production Canary binds `KVStore` to [KVSQL] through the DI container;
 * the bootstrap seam that injects the SQL store into the Lua environment is not ported
 * yet, so the default engine is this in-memory store.
 */
class KVMemory : KVStore() {

    /** `KVMemory::reset()`: flushes (clearing the cache) and returns the same store. */
    fun reset(): KVMemory {
        flush()
        return this
    }

    override fun load(key: String): ValueWrapper? = null

    override fun save(key: String, value: ValueWrapper): Boolean = false

    override fun loadPrefix(prefix: String): List<String> = emptyList()
}
