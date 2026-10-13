package org.opentibia.canary.lib.di

import org.opentibia.canary.lib.logging.Logger

/**
 * Port of `SoftSingleton` (src/lib/di/soft_singleton.hpp:12-28,
 * src/lib/di/soft_singleton.cpp:13-26). The C++ `g_logger()` reference member is
 * resolved through [DI] so test containers can substitute the logger.
 */
class SoftSingleton(id: String) {

    private val id: String = id
    private var instanceCount = 0
    private val logger: Logger = DI.get(Logger::class)

    /** `SoftSingleton::increment` (soft_singleton.cpp:16-26). */
    fun increment() {
        instanceCount++
        if (instanceCount > 1) {
            logger.warn(
                "$instanceCount instances created for $id. This is a soft singleton, you probably want to use g_${id.lowercase()} instead.",
            )
        }
    }

    /** `SoftSingleton::decrement` (soft_singleton.cpp:28-30). */
    fun decrement() {
        instanceCount--
    }
}

/**
 * Port of `SoftSingletonGuard` (src/lib/di/soft_singleton.hpp:30-42). The C++
 * destructor decrement is mapped to [close] so the guard can be used with
 * `use { }`.
 */
class SoftSingletonGuard(private val tracker: SoftSingleton) : AutoCloseable {

    init {
        tracker.increment()
    }

    override fun close() {
        tracker.decrement()
    }
}
