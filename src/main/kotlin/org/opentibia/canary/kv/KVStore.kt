package org.opentibia.canary.kv

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * Port of `KVStore` (`src/kv/kv.hpp:75-135`, `src/kv/kv.cpp`): the bounded in-memory
 * cache with LRU eviction and write-behind persistence.
 *
 * The C++ store keeps a `phmap` map plus an LRU list of key iterators; this port uses
 * an access-ordered [LinkedHashMap], where iteration starts at the least recently
 * accessed entry, so eviction picks the same key (the deleted-marker entries that the
 * C++ queue pins to the eviction end follow access recency here instead; that only
 * affects which cached entry is evicted under `MAX_SIZE`, never set/get/keys/save).
 *
 * [maxSize] is exposed for tests; production uses `KVStore::MAX_SIZE`.
 */
abstract class KVStore(private val maxSize: Int = MAX_SIZE) : KV {

    private val store = LinkedHashMap<String, ValueWrapper>(16, 0.75f, true)
    private val pendingEvictions = mutableListOf<Pair<String, ValueWrapper>>()
    private val mutex = ReentrantLock()

    override fun set(key: String, value: ValueWrapper) {
        mutex.withLock { setLocked(key, value) }
        processEvictions()
    }

    override fun get(key: String, forceLoad: Boolean): ValueWrapper? {
        logger.trace { "KVStore::get($key)" }

        if (!forceLoad) {
            mutex.withLock {
                val value = store[key] ?: return@withLock
                if (value.isDeleted) {
                    return null
                }
                return value
            }
        }

        val value = load(key) ?: return null
        mutex.withLock { setLocked(key, value) }
        processEvictions()
        return value
    }

    override fun keys(prefix: String): Set<String> {
        val keys = linkedSetOf<String>()

        mutex.withLock {
            for ((key, value) in store) {
                if (key.startsWith(prefix) && !value.isDeleted) {
                    keys += key.substring(prefix.length)
                }
            }
        }

        keys += loadPrefix(prefix)
        return keys
    }

    override fun scoped(scope: String): KV {
        logger.trace { "KVStore::scoped($scope)" }
        return ScopedKV(this, scope)
    }

    /**
     * `KVStore::flush()` (`kv.hpp:89-104`): persists a snapshot of the cache and the
     * pending evictions, then clears both. Entries are saved one by one through
     * [save], exactly like the C++ `flush` (unlike [saveAll]).
     */
    override fun flush() {
        val snapshot: List<Pair<String, ValueWrapper>>
        mutex.withLock {
            snapshot = store.map { it.key to it.value } + pendingEvictions
            store.clear()
            pendingEvictions.clear()
        }

        for ((key, value) in snapshot) {
            save(key, value)
        }
    }

    /** `KVStore::getStore()`: a snapshot of every cached entry, deleted markers included. */
    protected fun getStore(): Map<String, ValueWrapper> = mutex.withLock { LinkedHashMap(store) }

    /** `KVStore::load(key)`: the persistence miss is `null`. */
    protected abstract fun load(key: String): ValueWrapper?

    /** `KVStore::save(key, value)`: persists (or deletes) a single entry. */
    protected abstract fun save(key: String, value: ValueWrapper): Boolean

    /** `KVStore::loadPrefix(prefix)`: persisted keys with the prefix already stripped. */
    protected abstract fun loadPrefix(prefix: String): List<String>

    private fun setLocked(key: String, value: ValueWrapper) {
        if (store.containsKey(key)) {
            store[key] = value
            return
        }

        if (store.size >= maxSize && store.isNotEmpty()) {
            logger.debug { "KVStore::set() - MAX_SIZE reached, removing last element" }
            val evictedKey = store.keys.first()
            val evictedValue = store[evictedKey]
            store.remove(evictedKey)
            if (evictedValue != null) {
                pendingEvictions += evictedKey to evictedValue
            }
        }

        store[key] = value
    }

    /** `KVStore::processEvictions()`: persists the evicted entries outside the lock. */
    private fun processEvictions() {
        val evictions: List<Pair<String, ValueWrapper>>
        mutex.withLock {
            if (pendingEvictions.isEmpty()) {
                return
            }
            evictions = pendingEvictions.toList()
            pendingEvictions.clear()
        }

        for ((key, value) in evictions) {
            save(key, value)
        }
    }

    companion object {
        /** `KVStore::MAX_SIZE` (`src/kv/kv.hpp:77`). */
        const val MAX_SIZE: Int = 1_000_000
    }
}
