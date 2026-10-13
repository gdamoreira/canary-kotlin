package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.game.zones.Area
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.game.zones.Zones
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.core.wrapCreatureUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.WeakHashMap

/**
 * Engine seam behind the `Zone` userdata class. Mirrors the `Zone`/`Zone::*`
 * operations of `ZoneFunctions` (src/lua/functions/core/game/zone_functions.cpp).
 *
 * `null` returns model the C++ `LUA_ERROR_ZONE_NOT_FOUND` branches that
 * [ZoneFunctions] answers with `false` or the original argument.
 */
interface ZoneOperations {
    /** `Zone(name)`: `Zone::getZone(name)` else `Zone::addZone(name)`. */
    fun create(name: String): Zone?

    /** Static `Zone.getByName(name)`; null is the C++ `lua_pushnil`. */
    fun getByName(name: String): Zone?

    /** Static `Zone.getAll()`: `Zone::getZones()`. */
    fun getAll(): List<Zone>

    /** Static `Zone.getByPosition(pos)`; null when `g_game().map.getTile` misses. */
    fun getByPosition(position: Position): List<Zone>?

    /** `Zone:getRemoveDestination()`. */
    fun getRemoveDestination(zone: Zone): Position

    /** `Zone:setRemoveDestination(pos)`. */
    fun setRemoveDestination(zone: Zone, position: Position)

    /** `Zone:getCreatures()`; empty because the port models no zone caches yet. */
    fun getCreatures(zone: Zone): List<Any> = emptyList()

    /** `Zone:getPlayers()`; empty because the port models no zone caches yet. */
    fun getPlayers(zone: Zone): List<Any> = emptyList()

    /** `Zone:getMonsters()`; empty because the port models no zone caches yet. */
    fun getMonsters(zone: Zone): List<Any> = emptyList()

    /** `Zone:getNpcs()`; empty because the port models no zone caches yet. */
    fun getNpcs(zone: Zone): List<Any> = emptyList()

    /** `Zone:getItems()`; empty because the port models no zone caches yet. */
    fun getItems(zone: Zone): List<Any> = emptyList()

    fun removePlayers(zone: Zone) {}
    fun removeMonsters(zone: Zone) {}
    fun removeNpcs(zone: Zone) {}

    /** `Zone:setMonsterVariant(variant)`; false when no spawn registry is wired. */
    fun setMonsterVariant(zone: Zone, variant: String): Boolean = false

    fun reportError(message: String) {}
}

/** [ZoneOperations] over the ported [Zones] registry and optional [GameMap]. */
class WorldZoneOperations(
    private val zones: Zones,
    private val gameMap: GameMap? = null,
) : ZoneOperations {
    /** `Zone::removeDestination` (`src/game/zones/zone.hpp:184`), script state in this port. */
    private val removeDestinations = WeakHashMap<Zone, Position>()

    override fun create(name: String): Zone? = zones.getZone(name) ?: zones.addZone(name)

    override fun getByName(name: String): Zone? = zones.getZone(name)

    override fun getAll(): List<Zone> = zones.getZones()

    override fun getByPosition(position: Position): List<Zone>? =
        gameMap?.getTile(position)?.getZones()?.toList()

    override fun getRemoveDestination(zone: Zone): Position =
        removeDestinations[zone] ?: Position(0, 0, 0)

    override fun setRemoveDestination(zone: Zone, position: Position) {
        removeDestinations[zone] = position
    }
}

/**
 * Registers Canary's zone interface (src/lua/functions/core/game/zone_functions.cpp:16-43):
 *
 *   Zone(name)
 *   zone:getName() / addArea(fromPos, toPos) / subtractArea(fromPos, toPos)
 *   zone:getRemoveDestination() / setRemoveDestination(pos)
 *   zone:getPositions() / getCreatures() / getPlayers() / getMonsters() / getNpcs() / getItems()
 *   zone:removePlayers() / removeMonsters() / removeNpcs() / refresh()
 *   zone:setMonsterVariant(variant)
 *   Zone.getByPosition(pos) / Zone.getByName(name) / Zone.getAll()
 *
 * `Zone.__eq` compares names like `ZoneFunctions::luaZoneCompare`. The `getCreatures`
 * family and the remove/variant members consult [ZoneOperations], which documents the
 * unported spawn-cache side effects.
 */
object ZoneFunctions {
    const val ZONE_TYPE = "Zone"

    fun install(env: LuaEnvironment, operations: ZoneOperations = WorldZoneOperations(Zones())) {
        val zoneClass = LuaTable()
        zoneClass.set("__call", libraryFunction { args ->
            val zone = operations.create(luaArgString(args, 2))
                ?: return@libraryFunction LuaValue.NIL
            env.userdata.wrap(ZONE_TYPE, zone)
        })
        zoneClass.setmetatable(LuaTable().also { meta -> meta.set("__call", zoneClass.get("__call")) })
        env.globals.set(ZONE_TYPE, zoneClass)

        val metatable = env.userdata.metatable(ZONE_TYPE)
        if (metatable.get("__index").isnil()) {
            metatable.set("__index", metatable)
        }
        metatable.set("__eq", libraryFunction { args ->
            val zone1 = args.arg(1).touserdata() as? Zone
            val zone2 = args.arg(2).touserdata() as? Zone
            if (zone1 == null || zone2 == null) {
                operations.reportError("Zone not found")
                return@libraryFunction LuaValue.FALSE
            }
            LuaValue.valueOf(zone1.name == zone2.name)
        })

        metatable.set("getName", zoneFunction(operations) { args, zone ->
            LuaValue.valueOf(zone.name)
        })

        metatable.set("addArea", zoneFunction(operations) { args, zone ->
            zone.addArea(Area(luaToPosition(args.arg(2)) ?: Position(0, 0, 0), luaToPosition(args.arg(3)) ?: Position(0, 0, 0)))
            LuaValue.TRUE
        })

        metatable.set("subtractArea", zoneFunction(operations) { args, zone ->
            zone.subtractArea(Area(luaToPosition(args.arg(2)) ?: Position(0, 0, 0), luaToPosition(args.arg(3)) ?: Position(0, 0, 0)))
            LuaValue.TRUE
        })

        metatable.set("getRemoveDestination", zoneFunction(operations) { _, zone ->
            env.userdata.wrap("Position", operations.getRemoveDestination(zone))
        })

        metatable.set("setRemoveDestination", zoneFunction(operations) { args, zone ->
            val position = luaToPosition(args.arg(2))
            if (position != null) {
                operations.setRemoveDestination(zone, position)
            }
            LuaValue.NIL
        })

        metatable.set("getPositions", zoneFunction(operations) { _, zone ->
            luaArrayOf(zone.getPositions().map { env.userdata.wrap("Position", it) })
        })

        metatable.set("refresh", zoneFunction(operations) { _, zone ->
            zone.refresh()
            LuaValue.NIL
        })

        metatable.set("setMonsterVariant", zoneFunction(operations) { args, zone ->
            val variant = luaArgString(args, 2)
            if (variant.isEmpty()) {
                LuaValue.FALSE
            } else {
                LuaValue.valueOf(operations.setMonsterVariant(zone, variant))
            }
        })

        metatable.set("getCreatures", zoneFunction(operations) { _, zone ->
            creatureTable(env, operations.getCreatures(zone))
        })
        metatable.set("getPlayers", zoneFunction(operations) { _, zone ->
            creatureTable(env, operations.getPlayers(zone))
        })
        metatable.set("getMonsters", zoneFunction(operations) { _, zone ->
            creatureTable(env, operations.getMonsters(zone))
        })
        metatable.set("getNpcs", zoneFunction(operations) { _, zone ->
            creatureTable(env, operations.getNpcs(zone))
        })
        metatable.set("getItems", zoneFunction(operations) { _, zone ->
            LuaTable().also { table ->
                operations.getItems(zone).forEachIndexed { index, item ->
                    table.set(index + 1, env.userdata.wrap("Item", item))
                }
            }
        })

        metatable.set("removePlayers", zoneFunction(operations) { _, zone ->
            operations.removePlayers(zone)
            LuaValue.NIL
        })
        metatable.set("removeMonsters", zoneFunction(operations) { _, zone ->
            operations.removeMonsters(zone)
            LuaValue.NIL
        })
        metatable.set("removeNpcs", zoneFunction(operations) { _, zone ->
            operations.removeNpcs(zone)
            LuaValue.NIL
        })

        zoneClass.set("getByName", libraryFunction { args ->
            operations.getByName(luaArgString(args, 1))?.let { env.userdata.wrap(ZONE_TYPE, it) } ?: LuaValue.NIL
        })

        zoneClass.set("getByPosition", libraryFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@libraryFunction LuaValue.NIL
            val zones = operations.getByPosition(position) ?: return@libraryFunction LuaValue.NIL
            luaArrayOf(zones.map { env.userdata.wrap(ZONE_TYPE, it) })
        })

        zoneClass.set("getAll", libraryFunction {
            luaArrayOf(operations.getAll().map { env.userdata.wrap(ZONE_TYPE, it) })
        })
    }

    private fun zoneFunction(
        operations: ZoneOperations,
        body: (Varargs, Zone) -> LuaValue,
    ) = libraryFunction { args ->
        val zone = args.arg(1).touserdata() as? Zone
        if (zone == null) {
            operations.reportError("Zone not found")
            LuaValue.FALSE
        } else {
            body(args, zone)
        }
    }

    private fun creatureTable(env: LuaEnvironment, creatures: List<Any>): LuaTable =
        LuaTable().also { table ->
            creatures.forEachIndexed { index, creature ->
                table.set(index + 1, wrapCreatureUserdata(env, creature))
            }
        }
}
