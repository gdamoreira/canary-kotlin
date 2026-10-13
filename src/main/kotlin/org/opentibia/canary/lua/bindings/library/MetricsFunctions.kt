package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam behind the global `metrics` table. Mirrors the `g_metrics()` call of
 * `MetricsFunctions::luaMetricsAddCounter`
 * (src/lua/functions/core/libs/metrics_functions.cpp:21-28).
 */
interface MetricsOperations {
    /** `metrics.addCounter(name, value, attributes)`. */
    fun addCounter(name: String, value: Double, attributes: Map<String, String>)
}

/**
 * Documented no-op used when no metrics backend is wired: `g_metrics()` is not
 * modeled by the port's `MetricsRegistry` (which exposes the online-player/tick
 * gauges only), so `metrics.addCounter` degrades to a no-op until a backend seam is
 * injected (PARITY GAP).
 */
object UnwiredMetricsOperations : MetricsOperations {
    override fun addCounter(name: String, value: Double, attributes: Map<String, String>) {}
}

/**
 * Registers the global `metrics` table with the single registration of
 * `MetricsFunctions::init` (metrics_functions.cpp:15-18):
 *
 *   metrics.addCounter(name, value, attributes)
 *
 * The attributes table becomes a string map exactly like
 * `MetricsFunctions::getAttributes` (metrics_functions.cpp:30-40).
 */
object MetricsFunctions {
    const val METRICS_GLOBAL = "metrics"

    fun install(env: LuaEnvironment, operations: MetricsOperations = UnwiredMetricsOperations) {
        val metrics = (env.globals.get(METRICS_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(METRICS_GLOBAL, it) }

        metrics.set("addCounter", libraryFunction { args ->
            val attributes = LinkedHashMap<String, String>()
            val table = args.arg(3)
            if (table is LuaTable) {
                forEachTableEntry(table) { key, value ->
                    attributes[luaStringValue(key)] = luaStringValue(value)
                }
            }
            operations.addCounter(luaArgString(args, 1), luaArgDouble(args, 2), attributes)
            LuaValue.NIL
        })
    }

    /** `Lua::getString` for table keys/values: numbers convert, other types read empty. */
    private fun luaStringValue(value: LuaValue): String =
        if (value.isstring() || value.isnumber()) value.tojstring() else ""
}
