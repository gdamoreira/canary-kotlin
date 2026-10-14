package org.opentibia.canary.lua.bindings

import io.github.oshai.kotlinlogging.KotlinLogging
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.combat.AreaCombat
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatParams
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.FormulaType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionOutfit
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.LuaVariant
import org.opentibia.canary.lua.runtime.LuaVariantType
import org.opentibia.canary.map.Position

private val logger = KotlinLogging.logger {}

/**
 * Engine seam for the `Combat` binding group
 * (src/lua/functions/creatures/combat/combat_functions.cpp). `setArea` needs the
 * Lua area registry (`g_luaEnvironment().getAreaObject`), the loading-script
 * gate needs `Lua::getScriptEnv()->getScriptId()` and `execute` dispatches the
 * combat itself (`Combat::doCombat` over the world map).
 */
interface CombatOperations {
    /** `Lua::getScriptEnv()->getScriptId()`; [CombatFunctions.EVENT_ID_LOADING] while loading. */
    fun getScriptId(): Int

    /** `g_luaEnvironment().getAreaObject(id)`; null when the area was never created. */
    fun getAreaObject(id: Int): AreaCombat?

    /** `Combat::doCombat` dispatch for `combat:execute(creature, variant)`. */
    fun execute(combat: LuaCombat, creature: Any, variant: LuaVariant): Boolean
}

/** Engine seam for `Variant(thing)`: `Lua::getThing(thing)->getPosition()`. */
interface VariantOperations {
    /** Returns the `Thing` position for a userdata receiver; null for non-things. */
    fun thingPosition(value: Any): Position?
}

/**
 * Lua-side `Combat` payload mirroring the non-copyable C++ `Combat` instance
 * (src/creatures/combat/combat.hpp:345-357): params, area, formula values,
 * callbacks, origin and the instant/rune spell names stamped by `execute`.
 */
class LuaCombat {
    val params: CombatParams = CombatParams()

    var area: AreaCombat? = null

    var formulaType: FormulaType = FormulaType.UNDEFINED

    var mina: Double = 0.0

    var minb: Double = 0.0

    var maxa: Double = 0.0

    var maxb: Double = 0.0

    /** `Combat::params` callback slots keyed by `CallBackParam_t`. */
    val callbacks: MutableMap<Int, LuaValue> = linkedMapOf()

    var instantSpellName: String = ""

    var runeSpellName: String = ""

    /** `Combat::addCondition` (combat.cpp:663-665). */
    fun addCondition(condition: Condition) {
        params.conditionList.add(condition)
    }
}

/**
 * Registers the Canary `Combat` class from
 * src/lua/functions/creatures/combat/combat_functions.cpp:
 *
 *   Combat()
 *   combat:setParameter(key, value)
 *   combat:setFormula(type, mina, minb, maxa, maxb)
 *   combat:setArea(area)
 *   combat:addCondition(condition)
 *   combat:setCallback(key, function)
 *   combat:setOrigin(origin)
 *   combat:execute(creature, variant)
 *
 * Like `CombatFunctions::init`, installation also registers the `Condition` and
 * `Variant` groups.
 */
object CombatFunctions {
    const val COMBAT_CLASS = "Combat"

    /** `EVENT_ID_LOADING` (`src/lua/lua_definitions.hpp:16`). */
    const val EVENT_ID_LOADING = 1

    fun install(
        env: LuaEnvironment,
        operations: CombatOperations = UnwiredCombatOperations,
        variantOperations: VariantOperations = UnwiredVariantOperations,
    ) {
        ConditionFunctions.install(env)
        VariantFunctions.install(env, variantOperations)

        env.userdata.registerClass(COMBAT_CLASS, "", luaFunction {
            env.userdata.wrap(COMBAT_CLASS, LuaCombat())
        })

        val registry = env.userdata
        registry.registerMethod(COMBAT_CLASS, "setParameter", luaFunction { args ->
            val combat = retrieveUserdata(args, 1) as? LuaCombat ?: return@luaFunction LuaValue.NIL
            val key = luaNumber(args.arg(2))
            val value = if (args.arg(3).isboolean()) {
                if (args.arg(3).toboolean()) 1 else 0
            } else {
                luaNumber(args.arg(3))
            }
            LuaValue.valueOf(setCombatParam(combat, key, value))
        })

        registry.registerMethod(COMBAT_CLASS, "setFormula", luaFunction { args ->
            val combat = retrieveUserdata(args, 1) as? LuaCombat ?: return@luaFunction LuaValue.NIL
            combat.formulaType = FormulaType.entries.firstOrNull { it.value == luaNumber(args.arg(2)) }
                ?: FormulaType.UNDEFINED
            combat.mina = luaDouble(args.arg(3))
            combat.minb = luaDouble(args.arg(4))
            combat.maxa = luaDouble(args.arg(5))
            combat.maxb = luaDouble(args.arg(6))
            LuaValue.TRUE
        })

        registry.registerMethod(COMBAT_CLASS, "setArea", luaFunction { args ->
            if (operations.getScriptId() != EVENT_ID_LOADING) {
                logger.error { "[CombatFunctions::luaCombatSetArea] - This function can only be used while loading the script." }
                return@luaFunction LuaValue.NIL
            }

            val area = operations.getAreaObject(luaNumber(args.arg(2)))
            if (area == null) {
                logger.error { "[CombatFunctions::luaCombatSetArea] - Area not found" }
                return@luaFunction LuaValue.NIL
            }

            val combat = retrieveUserdata(args, 1) as? LuaCombat ?: return@luaFunction LuaValue.NIL
            combat.area = area.clone()
            LuaValue.TRUE
        })

        registry.registerMethod(COMBAT_CLASS, "addCondition", luaFunction { args ->
            val condition = retrieveUserdata(args, 2) as? Condition
            val combat = retrieveUserdata(args, 1) as? LuaCombat
            if (combat != null && condition != null) {
                combat.addCondition(condition.clone())
                LuaValue.TRUE
            } else {
                LuaValue.NIL
            }
        })

        registry.registerMethod(COMBAT_CLASS, "setCallback", luaFunction { args ->
            val combat = retrieveUserdata(args, 1) as? LuaCombat ?: return@luaFunction LuaValue.NIL
            val key = luaNumber(args.arg(2))
            if (!isValidCallbackKey(key)) {
                return@luaFunction LuaValue.NIL
            }

            val callback = env.globals.get(luaString(args.arg(3)))
            combat.callbacks[key] = callback
            LuaValue.valueOf(callback.isfunction())
        })

        registry.registerMethod(COMBAT_CLASS, "setOrigin", luaFunction { args ->
            val combat = retrieveUserdata(args, 1) as? LuaCombat ?: return@luaFunction LuaValue.NIL
            combat.params.origin = CombatOrigin.entries.firstOrNull { it.value == luaNumber(args.arg(2)) }
                ?: CombatOrigin.NONE
            LuaValue.TRUE
        })

        registry.registerMethod(COMBAT_CLASS, "execute", luaFunction { args ->
            val combat = retrieveUserdata(args, 1) as? LuaCombat ?: return@luaFunction LuaValue.FALSE

            val creatureArgument = args.arg(2)
            if (creatureArgument.isuserdata() && creatureArgument.touserdata() !is Creature) {
                return@luaFunction LuaValue.FALSE
            }
            val creature = creatureArgument.touserdata() as? Creature ?: return@luaFunction LuaValue.FALSE

            val variant = parseVariant(args.arg(3))
            combat.instantSpellName = variant.instantName
            combat.runeSpellName = variant.runeName
            LuaValue.valueOf(operations.execute(combat, creature, variant))
        })
    }

    /** `Combat::setParam` (combat.cpp:590-669), keyed by `CombatParam_t`. */
    private fun setCombatParam(combat: LuaCombat, key: Int, value: Int): Boolean = when (key) {
        0 -> {
            combat.params.combatType = combatTypeOf(value)
            true
        }

        1 -> {
            combat.params.impactEffect = value
            true
        }

        2 -> {
            combat.params.distanceEffect = value
            true
        }

        3 -> {
            combat.params.blockedByShield = value != 0
            true
        }

        4 -> {
            combat.params.blockedByArmor = value != 0
            true
        }

        5 -> {
            combat.params.targetCasterOrTopMost = value != 0
            true
        }

        6 -> {
            combat.params.itemId = value
            true
        }

        7 -> {
            combat.params.aggressive = value != 0
            true
        }

        8 -> {
            combat.params.dispelType = ConditionType.entries.firstOrNull { it.value == value }
                ?: ConditionType.CONDITION_NONE
            true
        }

        9 -> {
            combat.params.useCharges = value != 0
            true
        }

        10 -> {
            combat.params.soundCastEffect = value
            true
        }

        11 -> {
            combat.params.soundImpactEffect = value
            true
        }

        12 -> {
            combat.params.chainEffect = value
            true
        }

        else -> false
    }

    /** `Combat::setCallback` valid `CallBackParam_t` keys (combat.cpp:672-711). */
    private fun isValidCallbackKey(key: Int): Boolean = key in 0..5

    private fun parseVariant(value: LuaValue): LuaVariant {
        if (!value.istable()) {
            return LuaVariant()
        }
        val type = LuaVariantType.entries.getOrNull(value.get("type").optint(0)) ?: LuaVariantType.NONE
        return LuaVariant(
            type = type,
            number = value.get("number").optlong(0L),
            text = luaString(value.get("string")),
            instantName = luaString(value.get("instantName")),
            runeName = luaString(value.get("runeName")),
            pos = value.get("pos").touserdata() as? Position,
        )
    }

    private fun combatTypeOf(value: Int): CombatType =
        CombatType.entries.firstOrNull { it.value == value } ?: CombatType.NONE
}

/**
 * Registers the Canary `Condition` class from
 * src/lua/functions/creatures/combat/condition_functions.cpp:
 *
 *   Condition(conditionType[, conditionId = CONDITIONID_COMBAT, subId = 0, isPersistent = false])
 *   condition:delete()
 *   condition:getId()
 *   condition:getSubId()
 *   condition:getType()
 *   condition:getIcons()
 *   condition:getEndTime()
 *   condition:clone()
 *   condition:getTicks()
 *   condition:setTicks(ticks)
 *   condition:setParameter(key, value)
 *   condition:setFormula(mina, minb, maxa, maxb)
 *   condition:setOutfit(outfit)
 *   condition:addDamage(rounds, time, value)
 */
object ConditionFunctions {
    const val CONDITION_CLASS = "Condition"

    fun install(env: LuaEnvironment) {
        env.userdata.registerClass(CONDITION_CLASS, "", luaFunction { args -> createCondition(env, args) })

        val registry = env.userdata
        registry.registerMethod(CONDITION_CLASS, "delete", luaFunction { LuaValue.NIL })

        registry.registerMethod(CONDITION_CLASS, "getId", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(condition.id.value)
        })

        registry.registerMethod(CONDITION_CLASS, "getSubId", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(condition.subId)
        })

        registry.registerMethod(CONDITION_CLASS, "getType", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(condition.type.value)
        })

        registry.registerMethod(CONDITION_CLASS, "getIcons", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            val table = LuaTable()
            condition.getIcons().forEachIndexed { index, icon ->
                table.set(index + 1, LuaValue.valueOf(icon.name))
            }
            table
        })

        registry.registerMethod(CONDITION_CLASS, "getEndTime", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(condition.endTime.toDouble())
        })

        registry.registerMethod(CONDITION_CLASS, "clone", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(CONDITION_CLASS, condition.clone())
        })

        registry.registerMethod(CONDITION_CLASS, "getTicks", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(condition.ticks)
        })

        registry.registerMethod(CONDITION_CLASS, "setTicks", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            condition.setTicks(luaNumber(args.arg(2)))
            LuaValue.TRUE
        })

        registry.registerMethod(CONDITION_CLASS, "setParameter", luaFunction { args ->
            val condition = condition(args) ?: return@luaFunction LuaValue.NIL
            val key = ConditionParam.entries.firstOrNull { it.value == luaNumber(args.arg(2)) }
            val value = if (args.arg(3).isboolean()) {
                if (args.arg(3).toboolean()) 1 else 0
            } else {
                luaNumber(args.arg(3))
            }
            if (key != null) {
                condition.setParam(key, value)
            }
            LuaValue.TRUE
        })

        registry.registerMethod(CONDITION_CLASS, "setFormula", luaFunction { args ->
            val condition = condition(args) as? ConditionSpeed ?: return@luaFunction LuaValue.NIL
            val maxb = luaDouble(args.arg(5))
            val maxa = luaDouble(args.arg(4))
            val minb = luaDouble(args.arg(3))
            val mina = luaDouble(args.arg(2))
            condition.setFormulaVars(mina.toFloat(), minb.toFloat(), maxa.toFloat(), maxb.toFloat())
            LuaValue.TRUE
        })

        registry.registerMethod(CONDITION_CLASS, "setOutfit", luaFunction { args ->
            val outfit = if (args.arg(2).istable()) {
                outfitFromTable(args.arg(2))
            } else {
                outfitFromArguments(args)
            }
            val condition = condition(args) as? ConditionOutfit ?: return@luaFunction LuaValue.NIL
            condition.setOutfit(outfit)
            LuaValue.TRUE
        })

        registry.registerMethod(CONDITION_CLASS, "addDamage", luaFunction { args ->
            val condition = condition(args) as? ConditionDamage ?: return@luaFunction LuaValue.NIL
            val value = luaNumber(args.arg(4))
            val time = luaNumber(args.arg(3))
            val rounds = luaNumber(args.arg(2))
            LuaValue.valueOf(condition.addDamage(rounds, time, value))
        })
    }

    private fun condition(args: Varargs): Condition? = retrieveUserdata(args, 1) as? Condition

    private fun createCondition(env: LuaEnvironment, args: Varargs): LuaValue {
        val conditionType = ConditionType.entries.firstOrNull { it.value == luaNumber(args.arg(1)) }
            ?: ConditionType.CONDITION_NONE
        if (conditionType == ConditionType.CONDITION_NONE) {
            logger.error { "[ConditionFunctions::luaConditionCreate] - Invalid condition type" }
            return LuaValue.NIL
        }

        val conditionId = ConditionId.entries.firstOrNull { it.value == luaNumberOr(args.arg(2), 0) }
            ?: ConditionId.CONDITIONID_COMBAT
        val subId = luaNumberOr(args.arg(3), 0)
        val isPersistent = args.arg(4).toboolean()

        val condition = Condition.createCondition(
            conditionId,
            conditionType,
            ticks = 0,
            param = 0,
            buff = false,
            subId = subId,
            isPersistent = isPersistent,
        ) ?: return LuaValue.NIL
        return env.userdata.wrap(CONDITION_CLASS, condition)
    }

    /** `Lua::getOutfit` (`lua_functions_loader.cpp:439-464`), including the `lookWing` quirk. */
    private fun outfitFromTable(table: LuaValue): Outfit = Outfit(
        lookMountFeet = table.get("lookMountFeet").optint(0),
        lookMountLegs = table.get("lookMountLegs").optint(0),
        lookMountBody = table.get("lookMountBody").optint(0),
        lookMountHead = table.get("lookMountHead").optint(0),
        lookFamiliarsType = table.get("lookFamiliarsType").optint(0),
        lookMount = table.get("lookMount").optint(0),
        lookAddons = table.get("lookAddons").optint(0),
        lookFeet = table.get("lookFeet").optint(0),
        lookLegs = table.get("lookLegs").optint(0),
        lookBody = table.get("lookBody").optint(0),
        lookHead = table.get("lookHead").optint(0),
        lookTypeEx = table.get("lookTypeEx").optint(0),
        lookType = table.get("lookType").optint(0),
        // Canary reads lookWing from the "lookShader" field (loader.cpp:457).
        lookWing = table.get("lookShader").optint(0),
        lookAura = table.get("lookAura").optint(0),
        lookEffect = table.get("lookEffect").optint(0),
        lookShader = table.get("lookShader").optint(0),
    )

    /** `ConditionFunctions::luaConditionSetOutfit` positional order (condition_functions.cpp:205-226). */
    private fun outfitFromArguments(args: Varargs): Outfit = Outfit(
        lookFamiliarsType = luaNumberOr(args.arg(14), 0),
        lookMountFeet = luaNumberOr(args.arg(13), 0),
        lookMountLegs = luaNumberOr(args.arg(12), 0),
        lookMountBody = luaNumberOr(args.arg(11), 0),
        lookMountHead = luaNumberOr(args.arg(10), 0),
        lookMount = luaNumberOr(args.arg(9), 0),
        lookAddons = luaNumberOr(args.arg(8), 0),
        lookFeet = luaNumberOr(args.arg(7), 0),
        lookLegs = luaNumberOr(args.arg(6), 0),
        lookBody = luaNumberOr(args.arg(5), 0),
        lookHead = luaNumberOr(args.arg(4), 0),
        lookType = luaNumberOr(args.arg(3), 0),
        lookTypeEx = luaNumberOr(args.arg(2), 0),
        lookWing = luaNumberOr(args.arg(15), 0),
        lookAura = luaNumberOr(args.arg(16), 0),
        lookEffect = luaNumberOr(args.arg(17), 0),
        lookShader = luaNumberOr(args.arg(18), 0),
    )
}

/**
 * Registers the Canary `Variant` class from
 * src/lua/functions/creatures/combat/variant_functions.cpp:
 *
 *   Variant(number or string or position or thing)
 *   variant:getNumber()
 *   variant:getString()
 *   variant:getPosition()
 *
 * The value is the same field table `Lua::pushVariant` produces (`type`,
 * `number`/`string`/`pos`, `instantName`, `runeName`) so the getters read the
 * exact fields Canary's `Lua::getVariant` reads.
 */
object VariantFunctions {
    const val VARIANT_CLASS = "Variant"

    fun install(env: LuaEnvironment, operations: VariantOperations = UnwiredVariantOperations) {
        env.userdata.registerClass(VARIANT_CLASS, "", luaFunction { args -> createVariant(env, args.arg(1), operations) })

        val registry = env.userdata
        registry.registerMethod(VARIANT_CLASS, "getNumber", luaFunction { args ->
            val variant = args.arg(1)
            if (variant.get("type").optint(0) == LuaVariantType.NUMBER.ordinal) {
                variant.get("number")
            } else {
                LuaValue.valueOf(0)
            }
        })

        registry.registerMethod(VARIANT_CLASS, "getString", luaFunction { args ->
            val variant = args.arg(1)
            if (variant.get("type").optint(0) == LuaVariantType.STRING.ordinal) {
                variant.get("string")
            } else {
                LuaValue.valueOf("")
            }
        })

        registry.registerMethod(VARIANT_CLASS, "getPosition", luaFunction { args ->
            val variant = args.arg(1)
            val type = variant.get("type").optint(0)
            if (type == LuaVariantType.POSITION.ordinal || type == LuaVariantType.TARGETPOSITION.ordinal) {
                variant.get("pos")
            } else {
                env.userdata.wrap("Position", Position(0, 0, 0))
            }
        })
    }

    private fun createVariant(env: LuaEnvironment, value: LuaValue, operations: VariantOperations): LuaValue {
        val table = LuaTable()
        var type = LuaVariantType.NONE

        if (value.isuserdata()) {
            value.touserdata()?.let { thing ->
                operations.thingPosition(thing)?.let { position ->
                    type = LuaVariantType.TARGETPOSITION
                    table.set("pos", env.userdata.wrap("Position", position))
                }
            }
        } else if (value.istable()) {
            type = LuaVariantType.POSITION
            table.set(
                "pos",
                env.userdata.wrap(
                    "Position",
                    Position(value.get("x").optint(0), value.get("y").optint(0), value.get("z").optint(0)),
                ),
            )
        } else if (value.tonumber().isnumber()) {
            type = LuaVariantType.NUMBER
            table.set("number", LuaValue.valueOf(value.tonumber().todouble()))
        } else if (value.isstring()) {
            type = LuaVariantType.STRING
            table.set("string", LuaValue.valueOf(value.tojstring()))
        }

        table.set("type", LuaValue.valueOf(type.ordinal))
        table.set("instantName", LuaValue.valueOf(""))
        table.set("runeName", LuaValue.valueOf(""))
        table.setmetatable(env.userdata.metatable(VARIANT_CLASS))
        return table
    }
}

/** `Lua::getNumber` with an explicit default, used by the optional C++ arguments. */
internal fun luaNumberOr(value: LuaValue, default: Int): Int =
    if (value.isnil()) default else luaNumber(value)

internal object UnwiredCombatOperations : CombatOperations {
    override fun getScriptId(): Int =
        throw NotImplementedError("CombatFunctions.getScriptId seam is not wired")

    override fun getAreaObject(id: Int): AreaCombat? =
        throw NotImplementedError("CombatFunctions.getAreaObject seam is not wired")

    override fun execute(combat: LuaCombat, creature: Any, variant: LuaVariant): Boolean =
        throw NotImplementedError("CombatFunctions.execute seam is not wired")
}

internal object UnwiredVariantOperations : VariantOperations {
    override fun thingPosition(value: Any): Position? =
        throw NotImplementedError("VariantFunctions.thingPosition seam is not wired")
}
