package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.bindings.core.wrapCreatureUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Monster` metatable. Mirrors the primitives of
 * src/lua/functions/creatures/monster/monster_functions.cpp plus the shared
 * `Creature` targeting/master methods the file's records cover.
 *
 * Every method returns `null` when the receiver is not a monster, matching Canary's
 * `Lua::getUserdataShared<Monster>(L, 1, "Monster")` nil results.
 */
interface MonsterOperations {
    fun isMonster(monster: Any): Boolean
    fun getType(monster: Any): Any?
    fun getName(monster: Any): String?
    fun getSpawnPosition(monster: Any): Position?

    /** `position == null` mirrors Canary's "use the monster's current position" branch. */
    fun isInSpawnRange(monster: Any, position: Position?): Boolean?
    fun isIdle(monster: Any): Boolean?
    fun setIdle(monster: Any, idle: Boolean): Boolean?
    fun isTarget(monster: Any, creature: Any?): Boolean?
    fun isOpponent(monster: Any, creature: Any?): Boolean?
    fun isFriend(monster: Any, creature: Any?): Boolean?
    fun addFriend(monster: Any, creature: Any?): Boolean?
    fun removeFriend(monster: Any, creature: Any?): Boolean?
    fun getFriendList(monster: Any): List<Any>?
    fun addTarget(monster: Any, creature: Any?, pushFront: Boolean): Boolean?
    fun removeTarget(monster: Any, creature: Any?): Boolean?
    fun getTargetList(monster: Any): List<Any>?
    fun isChallenged(monster: Any): Boolean?
    fun selectTarget(monster: Any, creature: Any?): Boolean?
    fun getTarget(monster: Any): Any?
    fun setTarget(monster: Any, target: Any?): Boolean?
    fun getMaster(monster: Any): Any?
    fun setMaster(monster: Any, master: Any?): Boolean?
}

/**
 * Registers the Canary `Monster` methods, delegating every state lookup to
 * [MonsterOperations]:
 *
 *   monster:isMonster()
 *   monster:getType()
 *   monster:getName()
 *   monster:getSpawnPosition()
 *   monster:isInSpawnRange([position])
 *   monster:isIdle()
 *   monster:setIdle(idle)
 *   monster:isTarget(creature)
 *   monster:isOpponent(creature)
 *   monster:isFriend(creature)
 *   monster:addFriend(creature)
 *   monster:removeFriend(creature)
 *   monster:getFriendList()
 *   monster:getFriendCount()
 *   monster:addTarget(creature[, pushFront = false])
 *   monster:removeTarget(creature)
 *   monster:getTargetList()
 *   monster:getTargetCount()
 *   monster:isChallenged()
 *   monster:selectTarget(creature)
 *   monster:getTarget()
 *   monster:setTarget(target)
 *   monster:getMaster()
 *   monster:setMaster(master)
 *
 * `getFriendCount`/`getTargetCount` are derived from the list seams exactly like Canary
 * reads `getFriendList().size()`/`getTargetList().size()`.
 *
 * Placement parity gap: Canary registers `getTarget`/`setTarget`/`getMaster`/`setMaster`
 * on the shared `Creature` class (creature_functions.cpp:38-44), making them available
 * to `Player`/`Npc` too. This wave installs them on the `Monster` metatable only.
 */
object MonsterFunctions {
    const val MONSTER_TYPE = "Monster"
    const val MONSTER_TYPE_TYPE = "MonsterType"

    fun install(env: LuaEnvironment, operations: MonsterOperations = UnwiredMonsterOperations) {
        val registry = env.userdata

        registry.registerMethod(MONSTER_TYPE, "isMonster", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.isMonster(monster))
        })

        registry.registerMethod(MONSTER_TYPE, "getType", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getType(monster)?.let { env.userdata.wrap(MONSTER_TYPE_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getName", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getName(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getSpawnPosition", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSpawnPosition(monster)?.let { env.userdata.wrap("Position", it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "isInSpawnRange", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val position = if (args.narg() >= 2) luaToPosition(args.arg(2)) else null
            operations.isInSpawnRange(monster, position)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "isIdle", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isIdle(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "setIdle", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val idle = args.arg(2).toboolean()
            // Canary ignores the underlying result and reports success for a monster receiver.
            operations.setIdle(monster, idle)?.let { LuaValue.TRUE } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "isTarget", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isTarget(monster, retrieveUserdata(args, 2))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "isOpponent", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isOpponent(monster, retrieveUserdata(args, 2))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "isFriend", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isFriend(monster, retrieveUserdata(args, 2))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "addFriend", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            // Canary ignores the underlying result and reports success for a monster receiver.
            operations.addFriend(monster, retrieveUserdata(args, 2))?.let { LuaValue.TRUE } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "removeFriend", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeFriend(monster, retrieveUserdata(args, 2))?.let { LuaValue.TRUE } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getFriendList", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val friends = operations.getFriendList(monster) ?: return@luaFunction LuaValue.NIL
            creatureTable(env, friends)
        })

        registry.registerMethod(MONSTER_TYPE, "getFriendCount", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getFriendList(monster)?.let { LuaValue.valueOf(it.size) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "addTarget", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = retrieveUserdata(args, 2)
            val pushFront = args.arg(3).optboolean(false)
            // Canary ignores the underlying result and reports success for a monster receiver.
            operations.addTarget(monster, creature, pushFront)?.let { LuaValue.TRUE } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "removeTarget", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeTarget(monster, retrieveUserdata(args, 2))?.let { LuaValue.TRUE } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getTargetList", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val targets = operations.getTargetList(monster) ?: return@luaFunction LuaValue.NIL
            creatureTable(env, targets)
        })

        registry.registerMethod(MONSTER_TYPE, "getTargetCount", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getTargetList(monster)?.let { LuaValue.valueOf(it.size) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "isChallenged", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isChallenged(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "selectTarget", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.selectTarget(monster, retrieveUserdata(args, 2))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getTarget", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getTarget(monster)?.let { wrapCreatureUserdata(env, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "setTarget", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setTarget(monster, retrieveUserdata(args, 2))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getMaster", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getMaster(monster)?.let { wrapCreatureUserdata(env, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "setMaster", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setMaster(monster, retrieveUserdata(args, 2))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })
    }

    private fun creatureTable(env: LuaEnvironment, creatures: List<Any>): LuaTable =
        LuaTable().also { table ->
            creatures.forEachIndexed { index, creature ->
                table.set(index + 1, wrapCreatureUserdata(env, creature))
            }
        }
}

internal object UnwiredMonsterOperations : MonsterOperations {
    override fun isMonster(monster: Any): Boolean =
        throw NotImplementedError("MonsterFunctions.isMonster seam is not wired")

    override fun getType(monster: Any): Any? =
        throw NotImplementedError("MonsterFunctions.getType seam is not wired")

    override fun getName(monster: Any): String? =
        throw NotImplementedError("MonsterFunctions.getName seam is not wired")

    override fun getSpawnPosition(monster: Any): Position? =
        throw NotImplementedError("MonsterFunctions.getSpawnPosition seam is not wired")

    override fun isInSpawnRange(monster: Any, position: Position?): Boolean? =
        throw NotImplementedError("MonsterFunctions.isInSpawnRange seam is not wired")

    override fun isIdle(monster: Any): Boolean? =
        throw NotImplementedError("MonsterFunctions.isIdle seam is not wired")

    override fun setIdle(monster: Any, idle: Boolean): Boolean? =
        throw NotImplementedError("MonsterFunctions.setIdle seam is not wired")

    override fun isTarget(monster: Any, creature: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.isTarget seam is not wired")

    override fun isOpponent(monster: Any, creature: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.isOpponent seam is not wired")

    override fun isFriend(monster: Any, creature: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.isFriend seam is not wired")

    override fun addFriend(monster: Any, creature: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.addFriend seam is not wired")

    override fun removeFriend(monster: Any, creature: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.removeFriend seam is not wired")

    override fun getFriendList(monster: Any): List<Any>? =
        throw NotImplementedError("MonsterFunctions.getFriendList seam is not wired")

    override fun addTarget(monster: Any, creature: Any?, pushFront: Boolean): Boolean? =
        throw NotImplementedError("MonsterFunctions.addTarget seam is not wired")

    override fun removeTarget(monster: Any, creature: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.removeTarget seam is not wired")

    override fun getTargetList(monster: Any): List<Any>? =
        throw NotImplementedError("MonsterFunctions.getTargetList seam is not wired")

    override fun isChallenged(monster: Any): Boolean? =
        throw NotImplementedError("MonsterFunctions.isChallenged seam is not wired")

    override fun selectTarget(monster: Any, creature: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.selectTarget seam is not wired")

    override fun getTarget(monster: Any): Any? =
        throw NotImplementedError("MonsterFunctions.getTarget seam is not wired")

    override fun setTarget(monster: Any, target: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.setTarget seam is not wired")

    override fun getMaster(monster: Any): Any? =
        throw NotImplementedError("MonsterFunctions.getMaster seam is not wired")

    override fun setMaster(monster: Any, master: Any?): Boolean? =
        throw NotImplementedError("MonsterFunctions.setMaster seam is not wired")
}
