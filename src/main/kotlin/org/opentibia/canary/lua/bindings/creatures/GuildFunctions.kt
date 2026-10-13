package org.opentibia.canary.lua.bindings.creatures

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRank

/**
 * Engine seam for the `Guild` metatable. Mirrors the primitives in
 * src/lua/functions/creatures/player/guild_functions.cpp.
 *
 * `getRankById`/`getRankByLevel` return the `GuildRank` payload the C++ renders as
 * a `{id, name, level}` table; nullable results mirror the `lua_pushnil` guards.
 */
interface GuildOperations {
    /** Mirrors `g_game().getGuild(id)` (game.cpp). */
    fun create(id: Long): Any?

    fun getId(guild: Any): Long?

    fun getName(guild: Any): String?

    fun getMembersOnline(guild: Any): List<Any>?

    fun getBankBalance(guild: Any): Long?

    fun setBankBalance(guild: Any, bankBalance: Long): Boolean?

    fun addRank(guild: Any, id: Long, name: String, level: Int): Boolean?

    fun getRankById(guild: Any, id: Long): GuildRank?

    fun getRankByLevel(guild: Any, level: Int): GuildRank?

    fun getMotd(guild: Any): String?

    fun setMotd(guild: Any, motd: String): Boolean?
}

/**
 * Registers the Canary `Guild` class and methods:
 *
 *   Guild(id)
 *   guild:getId()
 *   guild:getName()
 *   guild:getMembersOnline()
 *   guild:getBankBalance()
 *   guild:setBankBalance(bankBalance)
 *   guild:addRank(id, name, level)
 *   guild:getRankById(id)
 *   guild:getRankByLevel(level)
 *   guild:getMotd()
 *   guild:setMotd(motd)
 *
 * plus `__eq` = `Lua::luaUserdataCompare`, exactly like
 * `GuildFunctions::init` (guild_functions.cpp:16-32).
 */
object GuildFunctions {
    const val GUILD_TYPE = "Guild"

    fun install(env: LuaEnvironment, operations: GuildOperations = WorldGuildOperations()) {
        val registry = env.userdata

        env.globals.set(GUILD_TYPE, luaFunction { args ->
            operations.create(args.arg(1).tolong())?.let { registry.wrap(GUILD_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(GUILD_TYPE, "getId", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getId(guild))
        })

        registry.registerMethod(GUILD_TYPE, "getName", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getName(guild))
        })

        registry.registerMethod(GUILD_TYPE, "getMembersOnline", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getMembersOnline(guild)?.let { playerListTable(env, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(GUILD_TYPE, "getBankBalance", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getBankBalance(guild))
        })

        registry.registerMethod(GUILD_TYPE, "setBankBalance", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.setBankBalance(guild, args.arg(2).tolong()))
        })

        registry.registerMethod(GUILD_TYPE, "addRank", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(
                operations.addRank(
                    guild,
                    args.arg(2).tolong(),
                    args.arg(3).tojstring(),
                    args.arg(4).toint(),
                ),
            )
        })

        registry.registerMethod(GUILD_TYPE, "getRankById", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getRankById(guild, args.arg(2).tolong())?.let { rankTable(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(GUILD_TYPE, "getRankByLevel", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getRankByLevel(guild, args.arg(2).toint())?.let { rankTable(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(GUILD_TYPE, "getMotd", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getMotd(guild))
        })

        registry.registerMethod(GUILD_TYPE, "setMotd", luaFunction { args ->
            val guild = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.setMotd(guild, args.arg(2).tojstring()))
        })

        registerUserdataEquality(env, GUILD_TYPE)
    }

    /** Mirrors `luaGuildGetRankById`/`luaGuildGetRankByLevel`'s `{id,name,level}` table. */
    private fun rankTable(rank: GuildRank): LuaTable = LuaTable().also { table ->
        table.set("id", LuaValue.valueOf(rank.id.toDouble()))
        table.set("name", LuaValue.valueOf(rank.name))
        table.set("level", LuaValue.valueOf(rank.level))
    }
}

/**
 * Real, non-no-op [GuildOperations] backed by the Kotlin
 * [org.opentibia.canary.social.guild.Guild] domain object.
 *
 * `guilds` is the dependency-injected replacement for `g_game().getGuild(id)`;
 * without it every `Guild(id)` lookup misses and pushes `nil`. The lookup must
 * return the canonical guild instance for an id so the `__eq` identity comparison
 * keeps working, like the game's shared_ptr registry. Bank balance and MOTD live
 * on the in-memory [org.opentibia.canary.social.guild.Guild], exactly like
 * `Guild::setBankBalance`/`Guild::setMotd` (guild.hpp:67-85); persistence stays
 * with `IOGuild::saveGuild`.
 */
class WorldGuildOperations(
    private val guilds: (Long) -> Guild? = { null },
) : GuildOperations {
    override fun create(id: Long): Any? = guilds(id)

    override fun getId(guild: Any): Long? = (guild as? Guild)?.id

    override fun getName(guild: Any): String? = (guild as? Guild)?.name

    override fun getMembersOnline(guild: Any): List<Any>? = (guild as? Guild)?.getMembersOnline()

    override fun getBankBalance(guild: Any): Long? = (guild as? Guild)?.getBankBalance()

    override fun setBankBalance(guild: Any, bankBalance: Long): Boolean? {
        val target = guild as? Guild ?: return null
        target.setBankBalance(bankBalance)
        return true
    }

    override fun addRank(guild: Any, id: Long, name: String, level: Int): Boolean? {
        val target = guild as? Guild ?: return null
        target.addRank(id, name, level)
        return true
    }

    override fun getRankById(guild: Any, id: Long): GuildRank? = (guild as? Guild)?.getRankById(id)

    override fun getRankByLevel(guild: Any, level: Int): GuildRank? = (guild as? Guild)?.getRankByLevel(level)

    override fun getMotd(guild: Any): String? = (guild as? Guild)?.getMotd()

    override fun setMotd(guild: Any, motd: String): Boolean? {
        val target = guild as? Guild ?: return null
        target.setMotd(motd)
        return true
    }
}
