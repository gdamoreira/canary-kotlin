package org.opentibia.canary.lib.di

import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Port of the `di::extension::runtime_injector` surface
 * (src/lib/di/runtime_provider.hpp:23-179, src/lib/di/injector.hpp:14-30) and the
 * `di::extension::shared` scope (src/lib/di/shared.hpp:21-85).
 *
 * [create] maps to `di::injector::create<T>()` and always builds a new instance;
 * [get] maps `DI::create<T &>()`/`DI::get<T>()` and returns the container-managed
 * shared instance (shared scope). Unregistered concrete types are constructed
 * through constructor injection, mirroring the boost::di default provider; when a
 * type cannot be resolved the `assert_error_policy` message `"Type not bound!"`
 * is raised as an [IllegalStateException].
 */
class Injector {

    private val bindings = ConcurrentHashMap<KClass<*>, (Injector) -> Any>()
    private val shared = ConcurrentHashMap<KClass<*>, Any>()

    /** `di::bind<Expected>().to<Given>()` installed in the runtime injector. */
    fun <T : Any> bind(type: KClass<T>, factory: (Injector) -> T): Injector {
        bindings[type] = factory
        return this
    }

    /** `install(const injector &)` (src/lib/di/runtime_provider.hpp:146-157). */
    fun install(source: Injector): Injector {
        bindings.putAll(source.bindings)
        return this
    }

    fun <T : Any> isBound(type: KClass<T>): Boolean = bindings.containsKey(type)

    /** `create<T>()`: a new instance on every call. */
    fun <T : Any> create(type: KClass<T>): T = create(type, HashSet())

    /** `create<T &>()`: the same container-managed instance on every call. */
    fun <T : Any> get(type: KClass<T>): T = get(type, HashSet())

    private fun <T : Any> create(type: KClass<T>, resolving: MutableSet<KClass<*>>): T {
        bindings[type]?.let { factory ->
            @Suppress("UNCHECKED_CAST")
            return factory(this) as T
        }
        return construct(type, resolving)
    }

    private fun <T : Any> get(type: KClass<T>, resolving: MutableSet<KClass<*>>): T {
        shared[type]?.let {
            @Suppress("UNCHECKED_CAST")
            return it as T
        }

        val instance = create(type, resolving)
        val existing = shared.putIfAbsent(type, instance)
        @Suppress("UNCHECKED_CAST")
        return (existing ?: instance) as T
    }

    private fun <T : Any> construct(type: KClass<T>, resolving: MutableSet<KClass<*>>): T {
        val javaClass = type.java
        if (javaClass.isInterface || Modifier.isAbstract(javaClass.modifiers) || !resolving.add(type)) {
            throw IllegalStateException(TYPE_NOT_BOUND)
        }

        try {
            for (constructor in javaClass.declaredConstructors.sortedByDescending { it.parameterCount }) {
                val arguments = resolveArguments(constructor, resolving) ?: continue
                constructor.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return constructor.newInstance(*arguments) as T
            }
        } finally {
            resolving.remove(type)
        }

        throw IllegalStateException(TYPE_NOT_BOUND)
    }

    private fun resolveArguments(constructor: Constructor<*>, resolving: MutableSet<KClass<*>>): Array<Any?>? {
        val parameterTypes = constructor.parameterTypes
        val arguments = arrayOfNulls<Any?>(parameterTypes.size)

        for (index in parameterTypes.indices) {
            val parameterType = parameterTypes[index]
            if (parameterType.isPrimitive) {
                return null
            }
            arguments[index] = try {
                get(parameterType.kotlin, resolving)
            } catch (e: IllegalStateException) {
                return null
            }
        }

        return arguments
    }

    companion object {
        private const val TYPE_NOT_BOUND = "Type not bound!"
    }
}
