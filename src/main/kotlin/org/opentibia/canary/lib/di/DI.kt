package org.opentibia.canary.lib.di

import org.opentibia.canary.lib.logging.LogWithSpdLog
import org.opentibia.canary.lib.logging.Logger
import kotlin.reflect.KClass

/**
 * Port of Canary's `DI` container (src/lib/di/container.hpp:18-64).
 *
 * [defaultContainer] binds the Canary default-container logger binding
 * (`Logger -> LogWithSpdLog`). The C++ `AccountRepository -> AccountRepositoryDB`
 * and `KVStore -> KVSQL` bindings are not installed here: the Kotlin repository
 * requires a configured `DataSource` and is wired at the composition root instead
 * of being constructed from hidden globals.
 */
object DI {

    @Volatile
    private var testContainer: Injector? = null

    private val defaultContainer: Injector = Injector()
        .bind(Logger::class) { LogWithSpdLog() }

    /** `DI::setTestContainer` (src/lib/di/container.hpp:28-30). */
    fun setTestContainer(container: Injector?) {
        testContainer = container
    }

    /** `DI::getTestContainer` (src/lib/di/container.hpp:31-33). */
    fun getTestContainer(): Injector? = testContainer

    /** `DI::create<T>()` (src/lib/di/container.hpp:40-43). */
    fun <T : Any> create(type: KClass<T>): T = (testContainer ?: defaultContainer).create(type)

    /** `DI::get<T>()` (src/lib/di/container.hpp:50-53). */
    fun <T : Any> get(type: KClass<T>): T = (testContainer ?: defaultContainer).get(type)
}

/** `inject<Type>()` (src/lib/di/container.hpp:61-64). */
inline fun <reified T : Any> inject(): T = DI.get(T::class)
