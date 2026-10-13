package org.opentibia.canary.kv

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * Port of Canary's `KV` interface (`src/kv/kv.hpp:26-73`): scoped key/value access
 * with write-behind persistence.
 *
 * The C++ DI singleton `KVStore::getInstance()` (`kv.cpp:19-21`) is replaced by
 * constructor injection; [generateUUID] keeps the process-wide timestamp/counter state
 * of the C++ static members.
 */
interface KV {
    /** `KV::set(const std::string&, const ValueWrapper&)`. */
    fun set(key: String, value: ValueWrapper)

    /** `KV::get(key, forceLoad)`; null mirrors the `std::nullopt` miss. */
    fun get(key: String, forceLoad: Boolean = false): ValueWrapper?

    /** `KV::saveAll()`; stores that persist eagerly may keep the default. */
    fun saveAll(): Boolean = true

    /** `KV::scoped(scope)`. */
    fun scoped(scope: String): KV

    /** `KV::keys(prefix)`. */
    fun keys(prefix: String = ""): Set<String>

    /** `KV::remove(key)` (`kv.cpp:120-122`): schedules a deleted marker through `set`. */
    fun remove(key: String) {
        set(key, ValueWrapper.deleted())
    }

    /** `KV::flush()`: the base implementation delegates to `saveAll`. */
    fun flush() {
        saveAll()
    }

    companion object {
        private val uuidLock = ReentrantLock()
        private var lastTimestamp: Long = 0
        private var counter: Long = 0

        /**
         * `KV::generateUUID()` (`kv.hpp:49-67`): a 20-digit millisecond stamp and a
         * 12-digit counter that resets whenever the timestamp advances.
         */
        fun generateUUID(): String {
            uuidLock.withLock {
                val now = System.currentTimeMillis()
                if (now != lastTimestamp) {
                    counter = 0
                    lastTimestamp = now
                } else {
                    counter++
                }
                return String.format(Locale.ROOT, "%020d-%012d", lastTimestamp, counter)
            }
        }
    }
}

/**
 * Port of `ScopedKV` (`src/kv/kv.hpp:137-186`): every operation is delegated to the
 * root store with the `"{prefix}.{key}"` composition of `ScopedKV::buildKey`, and
 * [keys] delegates its prefixed key to the root.
 */
class ScopedKV(private val rootKV: KVStore, private val prefix: String) : KV {

    override fun set(key: String, value: ValueWrapper) = rootKV.set(buildKey(key), value)

    override fun get(key: String, forceLoad: Boolean): ValueWrapper? = rootKV.get(buildKey(key), forceLoad)

    override fun saveAll(): Boolean = rootKV.saveAll()

    override fun scoped(scope: String): KV {
        logger.trace { "ScopedKV::scoped(${buildKey(scope)})" }
        return ScopedKV(rootKV, buildKey(scope))
    }

    override fun keys(prefix: String): Set<String> = rootKV.keys(buildKey(prefix))

    private fun buildKey(key: String): String = "$prefix.$key"
}
