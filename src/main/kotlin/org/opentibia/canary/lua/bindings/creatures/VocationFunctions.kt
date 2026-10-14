package org.opentibia.canary.lua.bindings.creatures

import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `Vocation` metatable. Mirrors the primitives in
 * src/lua/functions/creatures/player/vocation_functions.cpp.
 *
 * Nullable results mirror the `lua_pushnil` guards; `getDemotion`/`getPromotion`
 * return the resolved [Vocation] (or null for `VOCATION_NONE`/self lookups).
 */
interface VocationOperations {
    /** Mirrors `Vocation(id or name)` through `g_vocations()`. */
    fun create(idOrName: Any): Any?

    fun getId(vocation: Any): Int?

    fun getClientId(vocation: Any): Int?

    fun getBaseId(vocation: Any): Int?

    fun getName(vocation: Any): String?

    fun getDescription(vocation: Any): String?

    fun getRequiredSkillTries(vocation: Any, skillType: Int, skillLevel: Int): Long?

    fun getRequiredManaSpent(vocation: Any, magicLevel: Int): Long?

    fun getCapacityGain(vocation: Any): Int?

    fun getHealthGain(vocation: Any): Int?

    fun getHealthGainTicks(vocation: Any): Int?

    fun getHealthGainAmount(vocation: Any): Int?

    fun getManaGain(vocation: Any): Int?

    fun getManaGainTicks(vocation: Any): Int?

    fun getManaGainAmount(vocation: Any): Int?

    fun getMaxSoul(vocation: Any): Int?

    fun getSoulGainTicks(vocation: Any): Int?

    fun getBaseAttackSpeed(vocation: Any): Int?

    fun getAttackSpeed(vocation: Any): Int?

    fun getBaseSpeed(vocation: Any): Int?

    fun getDemotion(vocation: Any): Any?

    fun getPromotion(vocation: Any): Any?
}

/**
 * Registers the Canary `Vocation` class and methods:
 *
 *   Vocation(id or name)
 *   vocation:getId()
 *   vocation:getClientId()
 *   vocation:getBaseId()
 *   vocation:getName()
 *   vocation:getDescription()
 *   vocation:getRequiredSkillTries(skillType, skillLevel)
 *   vocation:getRequiredManaSpent(magicLevel)
 *   vocation:getCapacityGain()
 *   vocation:getHealthGain()
 *   vocation:getHealthGainTicks()
 *   vocation:getHealthGainAmount()
 *   vocation:getManaGain()
 *   vocation:getManaGainTicks()
 *   vocation:getManaGainAmount()
 *   vocation:getMaxSoul()
 *   vocation:getSoulGainTicks()
 *   vocation:getBaseAttackSpeed()
 *   vocation:getAttackSpeed()
 *   vocation:getBaseSpeed()
 *   vocation:getDemotion()
 *   vocation:getPromotion()
 *
 * plus `__eq` = `Lua::luaUserdataCompare`, exactly like
 * `VocationFunctions::init` (vocation_functions.cpp:14-42).
 */
object VocationFunctions {
    const val VOCATION_TYPE = "Vocation"

    fun install(env: LuaEnvironment, operations: VocationOperations = WorldVocationOperations()) {
        val registry = env.userdata

        registry.registerClass(VOCATION_TYPE, "", luaFunction { args ->
            val argument = args.arg(1)
            val vocation = when {
                argument.isnumber() -> operations.create(argument.toint())
                argument.isstring() -> operations.create(argument.tojstring())
                else -> operations.create("")
            }
            vocation?.let { registry.wrap(VOCATION_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(VOCATION_TYPE, "getId", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getId(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getClientId", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getClientId(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getBaseId", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getBaseId(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getName", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getName(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getDescription", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getDescription(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getRequiredSkillTries", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getRequiredSkillTries(vocation, args.arg(2).toint(), args.arg(3).toint()))
        })

        registry.registerMethod(VOCATION_TYPE, "getRequiredManaSpent", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getRequiredManaSpent(vocation, args.arg(2).toint()))
        })

        registry.registerMethod(VOCATION_TYPE, "getCapacityGain", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getCapacityGain(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getHealthGain", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getHealthGain(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getHealthGainTicks", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getHealthGainTicks(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getHealthGainAmount", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getHealthGainAmount(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getManaGain", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getManaGain(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getManaGainTicks", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getManaGainTicks(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getManaGainAmount", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getManaGainAmount(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getMaxSoul", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getMaxSoul(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getSoulGainTicks", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getSoulGainTicks(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getBaseAttackSpeed", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getBaseAttackSpeed(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getAttackSpeed", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getAttackSpeed(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getBaseSpeed", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getBaseSpeed(vocation))
        })

        registry.registerMethod(VOCATION_TYPE, "getDemotion", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getDemotion(vocation)?.let { registry.wrap(VOCATION_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(VOCATION_TYPE, "getPromotion", luaFunction { args ->
            val vocation = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getPromotion(vocation)?.let { registry.wrap(VOCATION_TYPE, it) } ?: LuaValue.NIL
        })

        registerUserdataEquality(env, VOCATION_TYPE)
    }
}

/**
 * Real, non-no-op [VocationOperations] backed by the Kotlin
 * [org.opentibia.canary.creatures.vocations.Vocations] registry, the
 * dependency-injected replacement for `g_vocations()`.
 *
 * `create` mirrors `luaVocationCreate` (vocation_functions.cpp:44-60): numbers go
 * straight to `Vocations::getVocation`, strings through
 * `Vocations::getVocationId`. Without a wired registry every lookup misses and
 * pushes `nil`.
 */
class WorldVocationOperations(
    private val vocations: Vocations? = null,
) : VocationOperations {
    override fun create(idOrName: Any): Any? = when (idOrName) {
        is Number -> vocations?.getVocation(idOrName.toInt())
        is String -> vocations?.let { registry -> registry.getVocation(registry.getVocationId(idOrName)) }
        else -> null
    }

    override fun getId(vocation: Any): Int? = (vocation as? Vocation)?.getId()

    override fun getClientId(vocation: Any): Int? = (vocation as? Vocation)?.getClientId()

    override fun getBaseId(vocation: Any): Int? = (vocation as? Vocation)?.getBaseId()

    override fun getName(vocation: Any): String? = (vocation as? Vocation)?.getVocName()

    override fun getDescription(vocation: Any): String? = (vocation as? Vocation)?.getVocDescription()

    override fun getRequiredSkillTries(vocation: Any, skillType: Int, skillLevel: Int): Long? =
        (vocation as? Vocation)?.getReqSkillTries(skillType, skillLevel)

    override fun getRequiredManaSpent(vocation: Any, magicLevel: Int): Long? =
        (vocation as? Vocation)?.getReqMana(magicLevel)

    override fun getCapacityGain(vocation: Any): Int? = (vocation as? Vocation)?.getCapGain()

    override fun getHealthGain(vocation: Any): Int? = (vocation as? Vocation)?.getHPGain()

    override fun getHealthGainTicks(vocation: Any): Int? = (vocation as? Vocation)?.getHealthGainTicks()

    override fun getHealthGainAmount(vocation: Any): Int? = (vocation as? Vocation)?.getHealthGainAmount()

    override fun getManaGain(vocation: Any): Int? = (vocation as? Vocation)?.getManaGain()

    override fun getManaGainTicks(vocation: Any): Int? = (vocation as? Vocation)?.getManaGainTicks()

    override fun getManaGainAmount(vocation: Any): Int? = (vocation as? Vocation)?.getManaGainAmount()

    override fun getMaxSoul(vocation: Any): Int? = (vocation as? Vocation)?.getSoulMax()

    override fun getSoulGainTicks(vocation: Any): Int? = (vocation as? Vocation)?.getSoulGainTicks()

    override fun getBaseAttackSpeed(vocation: Any): Int? = (vocation as? Vocation)?.getBaseAttackSpeed()

    override fun getAttackSpeed(vocation: Any): Int? = (vocation as? Vocation)?.getAttackSpeed()

    override fun getBaseSpeed(vocation: Any): Int? = (vocation as? Vocation)?.getBaseSpeed()

    /**
     * Mirrors `luaVocationGetDemotion` (vocation_functions.cpp:253-272):
     * `VOCATION_NONE` resolves to `nil`, and a demoted vocation identical to the
     * receiver is rejected.
     */
    override fun getDemotion(vocation: Any): Any? {
        val target = vocation as? Vocation ?: return null
        val registry = vocations ?: return null
        val fromId = target.getFromVocation()
        if (fromId == Vocation.VOCATION_NONE) {
            return null
        }
        val demoted = registry.getVocation(fromId) ?: return null
        return if (demoted !== target) demoted else null
    }

    /**
     * Mirrors `luaVocationGetPromotion` (vocation_functions.cpp:274-293):
     * `Vocations::getPromotedVocation` returning `VOCATION_NONE` (or the receiver
     * itself) resolves to `nil`.
     */
    override fun getPromotion(vocation: Any): Any? {
        val target = vocation as? Vocation ?: return null
        val registry = vocations ?: return null
        val promotedId = registry.getPromotedVocation(target.getId())
        if (promotedId == Vocation.VOCATION_NONE) {
            return null
        }
        val promoted = registry.getVocation(promotedId) ?: return null
        return if (promoted !== target) promoted else null
    }
}
