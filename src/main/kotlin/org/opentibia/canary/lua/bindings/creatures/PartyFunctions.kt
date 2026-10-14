package org.opentibia.canary.lua.bindings.creatures

import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.util.Collections
import java.util.WeakHashMap

/**
 * Engine seam for the `Party` metatable. Mirrors the primitives in
 * src/lua/functions/creatures/player/party_functions.cpp.
 *
 * Nullable results mirror the `lua_pushnil` guards (`getPlayer` miss, missing
 * receiver, invalid invitation). `disband` also invalidates the receiver exactly
 * like `luaPartyDisband` nulling the userdata's shared_ptr: every later call on
 * that party answers `nil`.
 */
interface PartyOperations {
    /** Mirrors `luaPartyCreate` including the `player:getParty()` guard. */
    fun create(player: Any): Any?

    fun disband(party: Any): Boolean?

    fun getLeader(party: Any): Any?

    fun setLeader(party: Any, player: Any?): Boolean?

    fun getMembers(party: Any): List<Any>?

    fun getMemberCount(party: Any): Int?

    fun getInvitees(party: Any): List<Any>?

    fun getInviteeCount(party: Any): Int?

    fun getUniqueVocationsCount(party: Any): Int?

    fun addInvite(party: Any, player: Any?): Boolean?

    fun removeInvite(party: Any, player: Any?): Boolean?

    fun addMember(party: Any, player: Any?): Boolean?

    fun removeMember(party: Any, player: Any?): Boolean?

    fun isSharedExperienceActive(party: Any): Boolean?

    fun isSharedExperienceEnabled(party: Any): Boolean?

    fun shareExperience(party: Any, experience: Long): Boolean?

    fun setSharedExperience(party: Any, active: Boolean): Boolean?
}

/**
 * Registers the Canary `Party` class and methods:
 *
 *   Party(player)
 *   party:disband()
 *   party:getLeader()
 *   party:setLeader(player)
 *   party:getMembers()
 *   party:getMemberCount()
 *   party:getInvitees()
 *   party:getInviteeCount()
 *   party:getUniqueVocationsCount()
 *   party:addInvite(player)
 *   party:removeInvite(player)
 *   party:addMember(player)
 *   party:removeMember(player)
 *   party:isSharedExperienceActive()
 *   party:isSharedExperienceEnabled()
 *   party:shareExperience(experience)
 *   party:setSharedExperience(active)
 *
 * plus `__eq` = `Lua::luaUserdataCompare`, exactly like
 * `PartyFunctions::init` (party_functions.cpp:19-38).
 */
object PartyFunctions {
    const val PARTY_TYPE = "Party"

    fun install(env: LuaEnvironment, operations: PartyOperations = WorldPartyOperations()) {
        val registry = env.userdata

        registry.registerClass(PARTY_TYPE, "", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.create(player)?.let { registry.wrap(PARTY_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(PARTY_TYPE, "disband", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.disband(party))
        })

        registry.registerMethod(PARTY_TYPE, "getLeader", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getLeader(party)?.let { registry.wrap("Player", it) } ?: LuaValue.NIL
        })

        registry.registerMethod(PARTY_TYPE, "setLeader", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.setLeader(party, retrieveUserdata(args, 2)))
        })

        registry.registerMethod(PARTY_TYPE, "getMembers", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getMembers(party)?.let { playerListTable(env, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(PARTY_TYPE, "getMemberCount", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getMemberCount(party))
        })

        registry.registerMethod(PARTY_TYPE, "getInvitees", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getInvitees(party)?.let { playerListTable(env, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(PARTY_TYPE, "getInviteeCount", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getInviteeCount(party))
        })

        registry.registerMethod(PARTY_TYPE, "getUniqueVocationsCount", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getUniqueVocationsCount(party))
        })

        registry.registerMethod(PARTY_TYPE, "addInvite", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.addInvite(party, retrieveUserdata(args, 2)))
        })

        registry.registerMethod(PARTY_TYPE, "removeInvite", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.removeInvite(party, retrieveUserdata(args, 2)))
        })

        registry.registerMethod(PARTY_TYPE, "addMember", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.addMember(party, retrieveUserdata(args, 2)))
        })

        registry.registerMethod(PARTY_TYPE, "removeMember", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.removeMember(party, retrieveUserdata(args, 2)))
        })

        registry.registerMethod(PARTY_TYPE, "isSharedExperienceActive", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.isSharedExperienceActive(party))
        })

        registry.registerMethod(PARTY_TYPE, "isSharedExperienceEnabled", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.isSharedExperienceEnabled(party))
        })

        registry.registerMethod(PARTY_TYPE, "shareExperience", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.shareExperience(party, args.arg(2).tolong()))
        })

        registry.registerMethod(PARTY_TYPE, "setSharedExperience", luaFunction { args ->
            val party = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.setSharedExperience(party, args.arg(2).toboolean()))
        })

        registerUserdataEquality(env, PARTY_TYPE)
    }
}

/**
 * Real, non-no-op [PartyOperations] backed by the Kotlin
 * [org.opentibia.canary.creatures.grouping.Party] domain object.
 *
 * PARITY GAPS: `g_game().updatePlayerShield(player)` (party_functions.cpp:67) has
 * no Kotlin game/protocol seam yet and is a no-op; `player->sendCreatureSkull`
 * goes through the existing [org.opentibia.canary.creatures.players.pvp.PlayerPvp]
 * protocol seam. `Party::getUniqueVocationsCount` resolves each player's vocation
 * through `Player::getVocation()`'s injected [org.opentibia.canary.creatures.vocations.Vocations]
 * registry, skipping players without one, exactly like the C++ `continue`.
 */
class WorldPartyOperations : PartyOperations {
    /**
     * `luaPartyDisband` nulls the userdata's shared_ptr after disbanding; this set
     * reproduces the invalidation so later calls on the same party answer `nil`.
     */
    private val disbanded: MutableSet<Party> = Collections.newSetFromMap(WeakHashMap())

    override fun create(player: Any): Any? {
        val target = player as? Player ?: return null
        if (target.getParty() != null) {
            return null
        }
        val party = Party(target)
        target.pvpPlayer.sendCreatureSkull(target)
        return party
    }

    override fun disband(party: Any): Boolean? {
        val target = resolve(party) ?: return null
        target.disband()
        disbanded.add(target)
        return true
    }

    override fun getLeader(party: Any): Any? = resolve(party)?.getLeader()

    override fun setLeader(party: Any, player: Any?): Boolean? {
        val target = player as? Player ?: return null
        val resolved = resolve(party) ?: return null
        return resolved.passPartyLeadership(target)
    }

    override fun getMembers(party: Any): List<Any>? = resolve(party)?.getMembers()

    override fun getMemberCount(party: Any): Int? = resolve(party)?.getMemberCount()

    override fun getInvitees(party: Any): List<Any>? = resolve(party)?.getInvitees()

    override fun getInviteeCount(party: Any): Int? = resolve(party)?.getInvitationCount()

    /** Mirrors `Party::getUniqueVocationsCount` (party.cpp:77-94). */
    override fun getUniqueVocationsCount(party: Any): Int? {
        val target = resolve(party) ?: return null
        val uniqueVocations = linkedSetOf<Int>()
        for (player in target.getPlayers()) {
            if (uniqueVocations.size >= 4) {
                break
            }
            val vocation = player.vocations?.getVocation(player.getVocationId()) ?: continue
            uniqueVocations.add(vocation.getBaseId())
        }
        return uniqueVocations.size
    }

    override fun addInvite(party: Any, player: Any?): Boolean? {
        val target = player as? Player ?: return null
        val resolved = resolve(party) ?: return null
        return resolved.invitePlayer(target)
    }

    override fun removeInvite(party: Any, player: Any?): Boolean? {
        val target = player as? Player ?: return null
        val resolved = resolve(party) ?: return null
        return resolved.removeInvite(target)
    }

    override fun addMember(party: Any, player: Any?): Boolean? {
        val target = player as? Player ?: return null
        val resolved = resolve(party) ?: return null
        return resolved.joinParty(target)
    }

    override fun removeMember(party: Any, player: Any?): Boolean? {
        val target = player as? Player ?: return null
        val resolved = resolve(party) ?: return null
        return resolved.leaveParty(target)
    }

    override fun isSharedExperienceActive(party: Any): Boolean? = resolve(party)?.isSharedExperienceActive()

    override fun isSharedExperienceEnabled(party: Any): Boolean? = resolve(party)?.isSharedExperienceEnabled()

    override fun shareExperience(party: Any, experience: Long): Boolean? {
        val resolved = resolve(party) ?: return null
        resolved.shareExperience(experience)
        return true
    }

    override fun setSharedExperience(party: Any, active: Boolean): Boolean? {
        val resolved = resolve(party) ?: return null
        return resolved.setSharedExperience(resolved.getLeader(), active)
    }

    private fun resolve(party: Any): Party? = (party as? Party)?.takeIf { it !in disbanded }
}
