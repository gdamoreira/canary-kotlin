package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.monsters.RespawnType
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

    /*
     * Batch 2: the forge/fiendish, hazard, soul pit, defense, immunity and critical
     * surface. Every method defaults to the not-found branch (`null`) so seams that do
     * not model the state yet keep compiling; the Lua-visible contract of each method
     * is only complete once the implementing seam overrides it. All C++ line
     * references are into src/lua/functions/creatures/monster/monster_functions.cpp.
     */

    /** `Monster(id)` resolving `Game::getMonsterByID`; null when no registry owns the id. */
    fun getMonsterById(id: Long): Any? = null

    /** `monster:setType(name or raceid, restoreHealth = false)`. */
    fun setType(monster: Any, nameOrRaceId: Any, restoreHealth: Boolean): Boolean? = null

    fun changeTargetDistance(monster: Any, distance: Int, duration: Int): Boolean? = null
    fun searchTarget(monster: Any, searchType: Int): Boolean? = null
    fun setSpawnPosition(monster: Any, interval: Int): Boolean? = null
    fun getRespawnType(monster: Any): RespawnType? = null
    fun getTimeToChangeFiendish(monster: Any): Long? = null
    fun setTimeToChangeFiendish(monster: Any, endTime: Long): Boolean? = null
    fun getMonsterForgeClassification(monster: Any): Int? = null
    fun setMonsterForgeClassification(monster: Any, classification: Int): Boolean? = null
    fun getForgeStack(monster: Any): Int? = null
    fun setForgeStack(monster: Any, stack: Int): Boolean? = null
    fun configureForgeSystem(monster: Any, stack: Int): Boolean? = null
    fun clearFiendishStatus(monster: Any): Boolean? = null
    fun isForgeable(monster: Any): Boolean? = null
    fun setName(monster: Any, name: String, nameDescription: String?): Boolean? = null
    fun getHazard(monster: Any): Boolean? = null
    fun setHazard(monster: Any, value: Boolean): Boolean? = null
    fun getHazardCrit(monster: Any): Boolean? = null
    fun setHazardCrit(monster: Any, value: Boolean): Boolean? = null
    fun getHazardDodge(monster: Any): Boolean? = null
    fun setHazardDodge(monster: Any, value: Boolean): Boolean? = null
    fun getHazardDamageBoost(monster: Any): Boolean? = null
    fun setHazardDamageBoost(monster: Any, value: Boolean): Boolean? = null
    fun getHazardDefenseBoost(monster: Any): Boolean? = null
    fun setHazardDefenseBoost(monster: Any, value: Boolean): Boolean? = null
    fun getSoulPit(monster: Any): Boolean? = null
    fun setSoulPit(monster: Any, value: Boolean): Boolean? = null
    fun addReflectElement(monster: Any, element: Int, percent: Int): Boolean? = null
    fun addDefense(monster: Any, defense: Int): Boolean? = null
    fun getDefense(monster: Any): Int? = null
    fun isDead(monster: Any): Boolean? = null
    fun isImmune(monster: Any): Boolean? = null
    fun setImmune(monster: Any, immune: Boolean): Boolean? = null
    fun getCriticalChance(monster: Any): Int? = null
    fun setCriticalChance(monster: Any, chance: Int): Boolean? = null
    fun getCriticalDamage(monster: Any): Int? = null
    fun setCriticalDamage(monster: Any, damage: Int): Boolean? = null
    fun addAttackSpell(monster: Any, spell: Any?): Boolean? = null
    fun addDefenseSpell(monster: Any, spell: Any?): Boolean? = null
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

        // --- batch 2: constructor, forge/fiendish, hazard, defense, immunity -------

        env.globals.set(MONSTER_TYPE, luaFunction { args ->
            val argument = args.arg(1)
            val monster = when {
                argument.isnumber() -> operations.getMonsterById(argument.tolong())
                argument.isuserdata() -> argument.touserdata()?.takeIf { operations.isMonster(it) }
                else -> null
            }
            monster?.let { env.userdata.wrap(MONSTER_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "setType", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val nameOrRaceId: Any = if (args.arg(2).isnumber()) args.arg(2).toint() else args.arg(2).tojstring()
            val restoreHealth = args.arg(3).optboolean(false)
            operations.setType(monster, nameOrRaceId, restoreHealth)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "changeTargetDistance", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val distance = args.arg(2).optint(1)
            val duration = args.arg(3).optint(12000)
            operations.changeTargetDistance(monster, distance, duration)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "searchTarget", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.searchTarget(monster, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "setSpawnPosition", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setSpawnPosition(monster, args.arg(2).optint(90))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        // luaMonsterGetRespawnType (monster_functions.cpp:455) returns the period and the
        // underground flag as two values.
        registry.registerMethod(MONSTER_TYPE, "getRespawnType", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val respawnType = operations.getRespawnType(monster) ?: return@luaFunction LuaValue.NIL
            LuaValue.varargsOf(
                arrayOf(
                    LuaValue.valueOf(respawnType.period.value),
                    LuaValue.valueOf(respawnType.underground),
                ),
            )
        })

        registry.registerMethod(MONSTER_TYPE, "getTimeToChangeFiendish", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getTimeToChangeFiendish(monster)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        // The C++ setter pushes no result, so the Lua call evaluates to nil.
        registry.registerMethod(MONSTER_TYPE, "setTimeToChangeFiendish", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setTimeToChangeFiendish(monster, args.arg(2).optlong(1L))
            LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getMonsterForgeClassification", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getMonsterForgeClassification(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        // The C++ setter pushes no result, so the Lua call evaluates to nil.
        registry.registerMethod(MONSTER_TYPE, "setMonsterForgeClassification", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setMonsterForgeClassification(monster, args.arg(2).optint(0))
            LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getForgeStack", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getForgeStack(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        // The C++ setter pushes no result, so the Lua call evaluates to nil.
        registry.registerMethod(MONSTER_TYPE, "setForgeStack", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setForgeStack(monster, args.arg(2).optint(0))
            LuaValue.NIL
        })

        // luaMonsterConfigureForgeSystem (monster_functions.cpp:559) is the one forge
        // method that answers false for a missing receiver.
        registry.registerMethod(MONSTER_TYPE, "configureForgeSystem", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            operations.configureForgeSystem(monster, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.FALSE
        })

        // The C++ clear pushes no result, so the Lua call evaluates to nil.
        registry.registerMethod(MONSTER_TYPE, "clearFiendishStatus", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.clearFiendishStatus(monster)
            LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "isForgeable", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isForgeable(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "setName", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val nameDescription = if (args.narg() >= 3) args.arg(3).tojstring() else null
            operations.setName(monster, args.arg(2).tojstring(), nameDescription)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        registerBooleanToggle(env, "hazard", operations::getHazard, operations::setHazard)
        registerBooleanToggle(env, "hazardCrit", operations::getHazardCrit, operations::setHazardCrit)
        registerBooleanToggle(env, "hazardDodge", operations::getHazardDodge, operations::setHazardDodge)
        registerBooleanToggle(env, "hazardDamageBoost", operations::getHazardDamageBoost, operations::setHazardDamageBoost)
        registerBooleanToggle(
            env,
            "hazardDefenseBoost",
            operations::getHazardDefenseBoost,
            operations::setHazardDefenseBoost,
        )
        registerBooleanToggle(env, "soulPit", operations::getSoulPit, operations::setSoulPit)

        registry.registerMethod(MONSTER_TYPE, "addReflectElement", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addReflectElement(monster, args.arg(2).toint(), args.arg(3).toint())?.let {
                LuaValue.valueOf(it)
            } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "addDefense", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addDefense(monster, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "getDefense", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getDefense(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "isDead", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isDead(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        // luaMonsterImmune (monster_functions.cpp:788): an argument sets the flag, the
        // current value is always returned.
        registry.registerMethod(MONSTER_TYPE, "immune", luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() > 1) {
                operations.setImmune(monster, args.arg(2).toboolean())
            }
            operations.isImmune(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        // luaMonsterCriticalChance/CriticalDamage push `Lua::pushBoolean` of the
        // uint16_t field (monster_functions.cpp:812, 829), so the Lua-visible result is
        // the truthiness of the stored value.
        registerCriticalAttribute(
            env,
            "criticalChance",
            operations::getCriticalChance,
            operations::setCriticalChance,
        )
        registerCriticalAttribute(
            env,
            "criticalDamage",
            operations::getCriticalDamage,
            operations::setCriticalDamage,
        )

        // luaMonsterAddAttackSpell/AddDefenseSpell (monster_functions.cpp:840, 862)
        // never push a result on success, so the Lua call always evaluates to nil.
        registry.registerMethod(MONSTER_TYPE, "addAttackSpell", luaFunction { args ->
            val monster = retrieveUserdata(args, 1)
            val spell = retrieveUserdata(args, 2)
            if (monster != null && spell != null) {
                operations.addAttackSpell(monster, spell)
            }
            LuaValue.NIL
        })

        registry.registerMethod(MONSTER_TYPE, "addDefenseSpell", luaFunction { args ->
            val monster = retrieveUserdata(args, 1)
            val spell = retrieveUserdata(args, 2)
            if (monster != null && spell != null) {
                operations.addDefenseSpell(monster, spell)
            }
            LuaValue.NIL
        })
    }

    /** Registers one of the `hazard`/`hazardCrit`/.../`soulPit` get-set pairs. */
    private fun registerBooleanToggle(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Boolean?,
        setter: (Any, Boolean) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE, name, luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(monster, args.arg(2).toboolean())
                getter(monster)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    /**
     * Registers `criticalChance`/`criticalDamage`, whose C++ getter feeds the
     * `uint16_t` field through `Lua::pushBoolean`.
     */
    private fun registerCriticalAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Int?,
        setter: (Any, Int) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE, name, luaFunction { args ->
            val monster = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val value = args.arg(2).optint(0)
            if (args.narg() > 1) {
                setter(monster, value)
            }
            getter(monster)?.let { LuaValue.valueOf(it != 0) } ?: LuaValue.NIL
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
