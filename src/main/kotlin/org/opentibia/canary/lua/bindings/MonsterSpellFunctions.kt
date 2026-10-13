package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Port of Canary's `MonsterSpell` (src/creatures/monsters/monsters.hpp:194-240) as
 * mutated by the `MonsterSpell` Lua methods. Every field keeps its Canary name and
 * default; the C++ `isScripted`/`needDirection`/`combatSpell`/`isMelee`/`outfit` flags
 * have no registered Lua setter and stay at their defaults.
 */
class MonsterSpellData {
    var name: String = ""
    var scriptName: String = ""
    var chance: Int = 100
    var range: Int = 0
    var interval: Int = 2000
    var minCombatValue: Int = 0
    var maxCombatValue: Int = 0
    var attack: Int = 0
    var skill: Int = 0
    var length: Int = 0
    var spread: Int = 0
    var radius: Int = 0
    var conditionMinDamage: Int = 0
    var conditionMaxDamage: Int = 0
    var conditionStartDamage: Int = 0
    var tickInterval: Int = 0
    var speedChange: Int = 0
    var duration: Int = 0
    var needTarget: Boolean = false

    /** `COMBAT_UNDEFINEDDAMAGE` (creatures_definitions.hpp:843). */
    var combatType: Int = 4
    var shoot: Int = 0
    var effect: Int = 0

    /** `CONDITION_NONE` (creatures_definitions.hpp:750). */
    var conditionType: Int = 254
    var outfitMonster: String = ""
    var outfitItem: Int = 0
    var soundImpactEffect: Int = 0
    var soundCastEffect: Int = 0
}

/**
 * Registers the Canary `MonsterSpell` class from
 * src/lua/functions/creatures/monster/monster_spell_functions.cpp:
 *
 *   MonsterSpell()
 *   monsterSpell:setType(type)
 *   monsterSpell:setScriptName(name)
 *   monsterSpell:setChance(chance)
 *   monsterSpell:setInterval(interval)
 *   monsterSpell:setRange(range)
 *   monsterSpell:setCombatValue(min, max)
 *   monsterSpell:setCombatType(combatType)
 *   monsterSpell:setAttackValue(attack, skill)
 *   monsterSpell:setNeedTarget(bool)
 *   monsterSpell:setCombatLength(length)
 *   monsterSpell:setCombatSpread(spread)
 *   monsterSpell:setCombatRadius(radius)
 *   monsterSpell:setConditionType(type)
 *   monsterSpell:setConditionDamage(min, max, start)
 *   monsterSpell:setConditionSpeedChange(speed)
 *   monsterSpell:setConditionDuration(duration)
 *   monsterSpell:setConditionTickInterval(interval)
 *   monsterSpell:setCombatShootEffect(effect)
 *   monsterSpell:setCombatEffect(effect)
 *   monsterSpell:setOutfitMonster(monsterName)
 *   monsterSpell:setOutfitItem(itemId)
 *   monsterSpell:castSound([sound])
 *   monsterSpell:impactSound([sound])
 *
 * `setConditionType(254)` (`CONDITION_NONE`) is rejected with `false`, mirroring the
 * C++ error report; every other setter answers `true`.
 */
object MonsterSpellFunctions {
    const val MONSTER_SPELL_TYPE = "MonsterSpell"

    fun install(env: LuaEnvironment) {
        val registry = env.userdata

        env.globals.set(MONSTER_SPELL_TYPE, luaFunction {
            env.userdata.wrap(MONSTER_SPELL_TYPE, MonsterSpellData())
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setType", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.name = args.arg(2).tojstring()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setScriptName", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.scriptName = args.arg(2).tojstring()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setChance", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.chance = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setInterval", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.interval = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setRange", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.range = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setCombatValue", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.minCombatValue = args.arg(2).toint()
            spell.maxCombatValue = args.arg(3).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setCombatType", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.combatType = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setAttackValue", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.attack = args.arg(2).toint()
            spell.skill = args.arg(3).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setNeedTarget", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.needTarget = args.arg(2).toboolean()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setCombatLength", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.length = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setCombatSpread", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.spread = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setCombatRadius", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.radius = args.arg(2).toint()
            LuaValue.TRUE
        })

        // luaMonsterSpellSetConditionType (monster_spell_functions.cpp:195) reports an
        // error and answers false when asked for CONDITION_NONE (254).
        registry.registerMethod(MONSTER_SPELL_TYPE, "setConditionType", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            val conditionType = args.arg(2).toint()
            if (conditionType == CONDITION_NONE) {
                return@luaFunction LuaValue.FALSE
            }
            spell.conditionType = conditionType
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setConditionDamage", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.conditionMinDamage = args.arg(2).toint()
            spell.conditionMaxDamage = args.arg(3).toint()
            spell.conditionStartDamage = args.arg(4).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setConditionSpeedChange", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.speedChange = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setConditionDuration", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.duration = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setConditionTickInterval", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.tickInterval = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setCombatShootEffect", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.shoot = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setCombatEffect", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.effect = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setOutfitMonster", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.outfitMonster = args.arg(2).tojstring()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "setOutfitItem", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            spell.outfitItem = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "castSound", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(spell.soundCastEffect)
            } else {
                spell.soundCastEffect = args.arg(2).toint()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(MONSTER_SPELL_TYPE, "impactSound", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? MonsterSpellData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(spell.soundImpactEffect)
            } else {
                spell.soundImpactEffect = args.arg(2).toint()
                LuaValue.TRUE
            }
        })
    }

    private const val COMBAT_UNDEFINEDDAMAGE = 4
    private const val CONDITION_NONE = 254
}
