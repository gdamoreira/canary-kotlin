package org.opentibia.canary.lua.bindings.creatures

import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `Group` metatable. Mirrors the primitives in
 * src/lua/functions/creatures/player/group_functions.cpp.
 *
 * Nullable results mirror Canary's `lua_pushnil` guards (`getGroup` miss, wrong
 * receiver, unresolvable field); `getFlags` is the `std::bitset::to_ulong` value
 * of the `Group::flags` array.
 */
interface GroupOperations {
    /** Mirrors `g_game().groups.getGroup(id)`. */
    fun create(id: Int): Any?

    fun getId(group: Any): Int?

    fun getName(group: Any): String?

    fun getFlags(group: Any): Long?

    fun getAccess(group: Any): Boolean?

    fun getMaxDepotItems(group: Any): Int?

    fun getMaxVipEntries(group: Any): Int?

    /** `group:hasFlag(flag)` through `Groups::getFlagNumber`. */
    fun hasFlag(group: Any, flag: Int): Boolean?
}

/**
 * Registers the Canary `Group` class and methods:
 *
 *   Group(id)
 *   group:getId()
 *   group:getName()
 *   group:getFlags()
 *   group:getAccess()
 *   group:getMaxDepotItems()
 *   group:getMaxVipEntries()
 *   group:hasFlag(flag)
 *
 * plus `__eq` = `Lua::luaUserdataCompare`, exactly like
 * `GroupFunctions::init` (group_functions.cpp:14-25).
 */
object GroupFunctions {
    const val GROUP_TYPE = "Group"

    fun install(env: LuaEnvironment, operations: GroupOperations = WorldGroupOperations()) {
        val registry = env.userdata

        registry.registerClass(GROUP_TYPE, "", luaFunction { args ->
            operations.create(args.arg(1).toint())?.let { registry.wrap(GROUP_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(GROUP_TYPE, "getId", luaFunction { args ->
            val group = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getId(group))
        })

        registry.registerMethod(GROUP_TYPE, "getName", luaFunction { args ->
            val group = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getName(group))
        })

        registry.registerMethod(GROUP_TYPE, "getFlags", luaFunction { args ->
            val group = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getFlags(group))
        })

        registry.registerMethod(GROUP_TYPE, "getAccess", luaFunction { args ->
            val group = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getAccess(group))
        })

        registry.registerMethod(GROUP_TYPE, "getMaxDepotItems", luaFunction { args ->
            val group = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getMaxDepotItems(group))
        })

        registry.registerMethod(GROUP_TYPE, "getMaxVipEntries", luaFunction { args ->
            val group = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getMaxVipEntries(group))
        })

        registry.registerMethod(GROUP_TYPE, "hasFlag", luaFunction { args ->
            val group = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.hasFlag(group, args.arg(2).toint()))
        })

        registerUserdataEquality(env, GROUP_TYPE)
    }
}

/**
 * Real, non-no-op [GroupOperations] backed by the Kotlin [Group] domain object.
 *
 * `groups` is the dependency-injected replacement for `g_game().groups.getGroup`
 * (`Groups::load`/`getGroup`, groups.cpp:78-169); without it every `Group(id)`
 * lookup misses and pushes `nil` like an empty registry.
 *
 * `getFlags` rebuilds the C++ `std::bitset<FlagLast>` bit positions from the
 * raised-flag set and returns `flags.to_ulong()` as a Lua number (40 bits fit
 * exactly in a double). `hasFlag` maps the numeric flag through
 * `Groups::getFlagNumber`; a number outside `PlayerFlags_t` answers `false`
 * instead of Canary's out-of-bounds `std::array` access.
 */
class WorldGroupOperations(
    private val groups: (Int) -> Group? = { null },
) : GroupOperations {
    override fun create(id: Int): Any? = groups(id)

    override fun getId(group: Any): Int? = (group as? Group)?.id

    override fun getName(group: Any): String? = (group as? Group)?.name

    override fun getFlags(group: Any): Long? {
        val target = group as? Group ?: return null
        var flags = 0L
        for (flag in target.flags) {
            if (flag.value < PlayerFlags_t.FlagLast.value) {
                flags = flags or (1L shl flag.value)
            }
        }
        return flags
    }

    override fun getAccess(group: Any): Boolean? = (group as? Group)?.access

    override fun getMaxDepotItems(group: Any): Int? = (group as? Group)?.maxDepotItems

    override fun getMaxVipEntries(group: Any): Int? = (group as? Group)?.maxVipEntries

    override fun hasFlag(group: Any, flag: Int): Boolean? {
        val target = group as? Group ?: return null
        val resolved = PlayerFlags_t.entries.firstOrNull { it.value == flag } ?: return false
        return resolved in target.flags
    }
}
