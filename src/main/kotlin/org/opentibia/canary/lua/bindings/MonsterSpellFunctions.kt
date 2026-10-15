package org.opentibia.canary.lua.bindings

import io.github.oshai.kotlinlogging.KotlinLogging
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.AreaCombat
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.FormulaType
import org.opentibia.canary.combat.Weapons as CombatWeapons
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionOutfit
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.BaseSpell
import org.opentibia.canary.creatures.monsters.MonsterCombatSpell
import org.opentibia.canary.creatures.monsters.MonsterCombatSpellCombat
import org.opentibia.canary.creatures.monsters.MonsterSpellCaster
import org.opentibia.canary.creatures.monsters.MonsterSpellTarget
import org.opentibia.canary.creatures.monsters.SpellBlock
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.spells.LuaVariant
import org.opentibia.canary.spells.LuaVariantType
import org.opentibia.canary.spells.MonsterRegisteredSpellRunner
import org.opentibia.canary.spells.RuneSpell
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.spells.SpellCaster
import org.opentibia.canary.spells.SpellEngine
import org.opentibia.canary.spells.SpellScriptRunner
import org.opentibia.canary.spells.Spells
import kotlin.math.abs
import kotlin.math.max

private val logger = KotlinLogging.logger {}

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
 * Monster-side actor for a block resolved through the canonical spell registry
 * (`sb.spell = g_spells().getSpellByName(spell->name)`, monsters.cpp:77-80).
 *
 * Canary stores the registered `InstantSpell`/`RuneSpell` (a `BaseSpell`)
 * directly in `spellBlock_t::spell` and casts it through the spell's Lua script
 * interface (`spellBlock.spell->castSpell(monster, target)`, monster.cpp:1979,
 * 2220). The Kotlin port keeps the registered script state in the [LuaSpell]
 * mirror, so this actor carries the same registered object into the block the
 * attack/defense rules cast and drives the registered `onCastSpell` body
 * through [MonsterRegisteredSpellRunner].
 *
 * The variant construction transcribes `InstantSpell::castSpell(creature,
 * target)`/`(creature)` (spells.cpp:1312-1348) and
 * `RuneSpell::castSpell(creature, target)` (spells.cpp:1569-1575); the monster
 * cast sites always use the two-argument overload.
 */
class MonsterRegisteredSpell(
    /** `spellBlock_t::spell` target: the registered Lua `Spell` state. */
    val spell: LuaSpell,
    /**
     * Lua bridge override; null resolves the live [SpellBindings] runner at
     * cast time so a re-initialized environment never keeps a stale reference
     * (AGENTS.md deferred-callback lifetime safety).
     */
    private val runner: MonsterRegisteredSpellRunner? = null,
    /**
     * `InstantSpell::canThrowSpell` gate override (spells.cpp:1299-1310); null
     * resolves the [MonsterSpellFunctions] gate the world attaches at install
     * time, which keeps a re-initialized environment from holding a stale map.
     */
    private val canThrowSpellGate: ((Monster, Creature, LuaSpell) -> Boolean)? = null,
) : BaseSpell {

    override fun castSpell(caster: Monster, target: Creature) {
        val scripts = runner ?: SpellBindings.monsterRegisteredSpellRunner()
        if (!spell.onCastSpell.isfunction()) {
            return
        }

        when (spell.spellType) {
            SpellType.RUNE -> castRune(caster, target, scripts)
            SpellType.INSTANT -> castInstant(caster, target, scripts)
        }
    }

    /** `RuneSpell::castSpell(creature, target)` (spells.cpp:1569-1575). */
    private fun castRune(caster: Monster, target: Creature, scripts: MonsterRegisteredSpellRunner) {
        val variant = LuaVariant()
        variant.type = LuaVariantType.VARIANT_NUMBER
        variant.number = target.id
        variant.runeName = spell.name
        scripts.executeCastSpell(caster, spell, variant, false)
    }

    /** `InstantSpell::castSpell(creature, target)` (spells.cpp:1341-1348). */
    private fun castInstant(caster: Monster, target: Creature, scripts: MonsterRegisteredSpellRunner) {
        if (spell.needTarget) {
            val variant = LuaVariant()
            variant.type = LuaVariantType.VARIANT_NUMBER
            variant.number = target.id
            scripts.executeCastSpell(caster, spell, variant, false)
            return
        }

        // `InstantSpell::castSpell(creature)` (spells.cpp:1312-1339).
        val variant = LuaVariant()
        variant.instantName = spell.name

        if (spell.needCasterTargetOrDirection) {
            val attacked = caster.getAttackedCreature()
            if (attacked == null || attacked.health <= 0) {
                return
            }
            // `if (!canThrowSpell(creature, target)) return false;`
            // (spells.cpp:1319-1321) over the wave-64 `SpellEngine` gate.
            val canThrow = canThrowSpellGate ?: { casterCreature, attackedCreature, registered ->
                MonsterSpellFunctions.canThrowSpell(casterCreature, attackedCreature, registered)
            }
            if (!canThrow(caster, attacked, spell)) {
                return
            }
            variant.type = LuaVariantType.VARIANT_NUMBER
            variant.number = attacked.id
        } else if (spell.needDirection) {
            variant.type = LuaVariantType.VARIANT_POSITION
            variant.pos = Spells.getCasterPosition(MonsterSpellCaster(caster), caster.direction)
        } else {
            variant.type = LuaVariantType.VARIANT_POSITION
            variant.pos = caster.position
        }
        scripts.executeCastSpell(caster, spell, variant, false)
    }
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

    /**
     * `InstantSpell::canThrowSpell` (spells.cpp:1299-1310) over the wave-64
     * `SpellEngine` gate and the `Game::canThrowObjectTo` map fold. Installed
     * by [install]; an unwired map leaves the gate permissive exactly like the
     * pre-gate port did (PARITY GAP: the real C++ always has a map).
     */
    @Volatile
    private var canThrowSpellGate: ((Monster, Creature, LuaSpell) -> Boolean)? = null

    fun install(env: LuaEnvironment, gameMap: GameMap? = null) {
        val registry = env.userdata

        registry.registerClass(MONSTER_SPELL_TYPE, "", luaFunction {
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

        canThrowSpellGate = gameMap?.let(::canThrowSpellGateFor)
    }

    /**
     * Re-binds the `canThrowSpell` gate over [gameMap] after install; the
     * composition root calls this alongside `WorldLuaOperations.attachCombat`
     * when the world (and its map) exists.
     */
    fun attachCanThrowSpellWorld(gameMap: GameMap) {
        canThrowSpellGate = canThrowSpellGateFor(gameMap)
    }

    /** Builds the `Game::canThrowObjectTo`-backed gate of one map. */
    private fun canThrowSpellGateFor(gameMap: GameMap): (Monster, Creature, LuaSpell) -> Boolean {
        val engine = SpellEngine(Spells(), LuaSpellWorld(gameMap), MonsterSpellNoScriptRunner)
        return { caster, target, spell ->
            engine.canThrowSpell(
                MonsterSpellCaster(caster),
                monsterInstantSpell(spell),
                MonsterSpellTarget(target),
            )
        }
    }

    /**
     * `InstantSpell::canThrowSpell` (spells.cpp:1299-1310) for a registered
     * monster spell: the [SpellEngine] gate folds the floor guard and the
     * `Game::canThrowObjectTo` sight/range check. A miss before [install] or
     * without a wired map keeps the historical permissive behavior.
     */
    fun canThrowSpell(caster: Monster, target: Creature, spell: LuaSpell): Boolean =
        canThrowSpellGate?.invoke(caster, target, spell) ?: true

    /**
     * The registered [LuaSpell] view the engine gate reads: `InstantSpell`'s
     * `checkLineOfSight` defaults true and has no Lua setter
     * (spells.hpp:316-347), the registered `spell:range(...)` carries over.
     */
    private fun monsterInstantSpell(spell: LuaSpell): InstantSpell {
        val instant = InstantSpell()
        instant.name = spell.name
        instant.range = spell.range
        return instant
    }

    /**
     * `Monsters::deserializeSpell` (`src/creatures/monsters/monsters.cpp:61-283`):
     * turns a `MonsterSpell` registration into the `spellBlock_t` the type's
     * `attackSpells`/`defenseSpells` vectors hold, building the `Combat` payload
     * and the [MonsterCombatSpell] actor that carries the combat type, impact
     * effect and shoot the scalar-only deserializer dropped.
     *
     * The `sb.spell = g_spells().getSpellByName(spell->name)` look-up
     * (monsters.cpp:77-80) resolves against the canonical registry
     * ([SpellBindings], the same instance behind `LuaEnvironment.spells`): a
     * registered name yields the [MonsterRegisteredSpell] carrying the
     * registered `Spell` state, a miss keeps the build-from-descriptor
     * `CombatSpell` branch.
     *
     * PARITY GAP: Canary's registry values are the compiled
     * `InstantSpell`/`RuneSpell` actors; the port's Lua registry stores the
     * mutable [LuaSpell] mirror, which is what the actor carries.
     */
    fun deserializeSpell(spell: MonsterSpellData): SpellBlock? {
        if (spell.scriptName.isEmpty() && spell.name.isEmpty()) {
            return null
        }

        val block = SpellBlock(
            chance = minOf(spell.chance, 100),
            speed = spell.interval,
            range = minOf(spell.range, Creature.MAP_MAX_VIEW_PORT_X * 2),
            minCombatValue = minOf(spell.minCombatValue, spell.maxCombatValue),
            maxCombatValue = maxOf(spell.minCombatValue, spell.maxCombatValue),
            soundCastEffect = soundEffectOf(spell.soundCastEffect),
            soundImpactEffect = soundEffectOf(spell.soundImpactEffect),
        )

        // `sb.spell = g_spells().getSpellByName(spell->name); if (sb.spell) return true;`
        // (monsters.cpp:77-80): a registered name returns the block untouched by
        // the descriptor combat build (`combatSpell`/`isMelee` stay false).
        SpellBindings.getSpellByName(spell.name)?.let { registered ->
            block.spell = MonsterRegisteredSpell(registered)
            return block
        }

        val combat = MonsterCombatSpellCombat()
        block.combatSpell = true

        var needDirection = false
        if (spell.length > 0) {
            spell.spread = max(0, spell.spread)

            combat.area = AreaCombat().also { it.setupArea(spell.length, spell.spread) }
            needDirection = true
        }

        if (spell.radius > 0) {
            combat.area = AreaCombat().also { it.setupArea(spell.radius) }
        }

        when (val spellName = spell.name.lowercase()) {
            "melee" -> {
                block.isMelee = true

                if (spell.attack > 0 && spell.skill > 0) {
                    block.minCombatValue = 0
                    block.maxCombatValue = -CombatWeapons.getMaxMeleeDamage(spell.skill, spell.attack)
                }

                block.range = 1
                combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_TYPE, COMBAT_PHYSICALDAMAGE)
                combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_BLOCKARMOR, 1)
                combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_BLOCKSHIELD, 1)
                combat.params.origin = CombatOrigin.MELEE
            }

            "combat" -> {
                if (spell.combatType == COMBAT_PHYSICALDAMAGE) {
                    combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_BLOCKARMOR, 1)
                    combat.params.origin = CombatOrigin.RANGED
                } else if (spell.combatType == COMBAT_HEALING) {
                    combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_AGGRESSIVE, 0)
                }

                combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_TYPE, spell.combatType)
            }

            "speed" -> {
                var speedChange = 0
                var duration = 10000

                if (spell.duration != 0) {
                    duration = spell.duration
                }

                if (spell.speedChange != 0) {
                    speedChange = spell.speedChange
                    if (speedChange < -1000) {
                        // Cant be slower than 100%
                        speedChange = -1000
                    }
                }

                val conditionType = if (speedChange > 0) {
                    combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_AGGRESSIVE, 0)
                    ConditionType.CONDITION_HASTE
                } else {
                    ConditionType.CONDITION_PARALYZE
                }
                val condition = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, conditionType, duration, 0)
                    as? ConditionSpeed
                    ?: return null
                val multiplier = 1.0f + speedChange / 1000.0f
                condition.setFormulaVars(multiplier / 2, 40f, multiplier, 40f)
                combat.params.conditionList += condition
            }

            "outfit" -> {
                var duration = 10000

                if (spell.duration != 0) {
                    duration = spell.duration
                }

                val condition = Condition.createCondition(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_OUTFIT,
                    duration,
                    0,
                ) as? ConditionOutfit ?: return null

                if (spell.outfitMonster.isNotEmpty()) {
                    condition.setLazyMonsterOutfit(spell.outfitMonster)
                } else if (spell.outfitItem > 0) {
                    condition.setOutfit(Outfit(lookTypeEx = spell.outfitItem))
                } else {
                    logger.error {
                        "[Monsters::deserializeSpell] - Missing outfit monster or item in outfit spell for: ${spell.name}"
                    }
                    return null
                }

                combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_AGGRESSIVE, 0)
                combat.params.conditionList += condition
            }

            "invisible" -> {
                var duration = 10000

                if (spell.duration != 0) {
                    duration = spell.duration
                }

                val condition = Condition.createCondition(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_INVISIBLE,
                    duration,
                    0,
                ) ?: return null
                combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_AGGRESSIVE, 0)
                combat.params.conditionList += condition
            }

            "drunk" -> {
                var duration = 10000

                if (spell.duration != 0) {
                    duration = spell.duration
                }

                val condition = Condition.createCondition(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_DRUNK,
                    duration,
                    0,
                ) ?: return null
                combat.params.conditionList += condition
            }

            "soulwars fear", "fear" -> {
                var duration = 6000

                if (spell.duration != 0) {
                    duration = spell.duration
                }

                val condition = Condition.createCondition(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_FEARED,
                    duration,
                    0,
                ) ?: return null
                combat.params.conditionList += condition
            }

            "firefield" -> combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_CREATEITEM, ITEM_FIREFIELD_PVP_FULL)

            "poisonfield" -> combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_CREATEITEM, ITEM_POISONFIELD_PVP)

            "energyfield" -> combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_CREATEITEM, ITEM_ENERGYFIELD_PVP)

            "condition" -> {
                if (spell.conditionType == CONDITION_NONE) {
                    logger.error { "[Monsters::deserializeSpell] - ${spell.name} condition is not set for spell" }
                }
            }

            "strength", "effect" -> Unit

            else -> {
                logger.error {
                    "[Monsters::deserializeSpell] - unknown or missing parameter on spell with name: $spellName"
                }
            }
        }

        if (spell.shoot != CONST_ANI_NONE) {
            combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_DISTANCEEFFECT, spell.shoot)
        }

        if (spell.effect != CONST_ME_NONE) {
            combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_EFFECT, spell.effect)
        }

        // If a spell has a condition, it always applies, no matter what kind of spell it is
        if (spell.conditionType != CONDITION_NONE) {
            val minDamage = abs(spell.conditionMinDamage)
            var maxDamage = abs(spell.conditionMaxDamage)
            var startDamage = abs(spell.conditionStartDamage)
            val tickInterval = if (spell.tickInterval > 0) spell.tickInterval else 2000

            if (startDamage > minDamage) {
                startDamage = 0
            }

            if (maxDamage == 0) {
                maxDamage = minDamage
            }

            val condition = getDamageCondition(
                conditionType = spell.conditionType,
                maxDamage = maxDamage,
                minDamage = minDamage,
                startDamage = startDamage,
                tickInterval = tickInterval,
            ) ?: run {
                logger.error {
                    "[Monsters::deserializeSpell] - Invalid damage condition type ${spell.conditionType} for: ${spell.name}"
                }
                return null
            }
            combat.params.conditionList += condition
        }

        combat.formulaType = FormulaType.DAMAGE
        combat.mina = block.minCombatValue.toDouble()
        combat.minb = 0.0
        combat.maxa = block.maxCombatValue.toDouble()
        combat.maxb = 0.0

        val combatSpell = MonsterCombatSpell(combat, spell.needTarget, needDirection)
        combatSpell.soundCastEffect = block.soundCastEffect
        combatSpell.soundImpactEffect = block.soundImpactEffect

        block.spell = combatSpell
        block.combatSpell = true

        return block
    }

    /** `Monsters::getDamageCondition` (`src/creatures/monsters/monsters.cpp:47-59`). */
    private fun getDamageCondition(
        conditionType: Int,
        maxDamage: Int,
        minDamage: Int,
        startDamage: Int,
        tickInterval: Int,
    ): ConditionDamage? {
        val type = ConditionType.entries.firstOrNull { it.value == conditionType } ?: return null
        val condition = Condition.createDamageCondition(ConditionId.CONDITIONID_COMBAT, type) ?: return null

        condition.setParam(ConditionParam.CONDITION_PARAM_TICKINTERVAL, tickInterval)
        condition.setParam(ConditionParam.CONDITION_PARAM_MINVALUE, minDamage)
        condition.setParam(ConditionParam.CONDITION_PARAM_MAXVALUE, maxDamage)
        condition.setParam(ConditionParam.CONDITION_PARAM_STARTVALUE, startDamage)
        condition.setParam(ConditionParam.CONDITION_PARAM_DELAYED, 1)
        return condition
    }

    private fun soundEffectOf(value: Int): SoundEffect =
        SoundEffect.entries.firstOrNull { it.value == value } ?: SoundEffect.SILENCE

    private const val COMBAT_PHYSICALDAMAGE = 0
    private const val COMBAT_HEALING = 7
    private const val COMBAT_UNDEFINEDDAMAGE = 4
    private const val CONDITION_NONE = 254

    /** `CONST_ANI_NONE` (`src/utils/utils_definitions.hpp`). */
    private const val CONST_ANI_NONE = 0

    /** `CONST_ME_NONE` (`src/utils/utils_definitions.hpp`). */
    private const val CONST_ME_NONE = 0

    /** `ITEM_FIREFIELD_PVP_FULL` (`src/utils/utils_definitions.hpp:524`). */
    private const val ITEM_FIREFIELD_PVP_FULL = 2118

    /** `ITEM_POISONFIELD_PVP` (`src/utils/utils_definitions.hpp:518`). */
    private const val ITEM_POISONFIELD_PVP = 105

    /** `ITEM_ENERGYFIELD_PVP` (`src/utils/utils_definitions.hpp:522`). */
    private const val ITEM_ENERGYFIELD_PVP = 2122
}

/**
 * Inert `SpellScriptRunner` for the `canThrowSpell`-only [SpellEngine] of
 * [MonsterSpellFunctions]: the gate never executes a spell script, so both
 * overloads are unreachable (they mirror the interface for construction).
 */
private object MonsterSpellNoScriptRunner : SpellScriptRunner {
    override fun executeCastSpell(caster: SpellCaster, spell: InstantSpell, variant: LuaVariant): Boolean = false

    override fun executeCastSpell(
        caster: SpellCaster,
        rune: RuneSpell,
        variant: LuaVariant,
        isHotkey: Boolean,
    ): Boolean = false
}
