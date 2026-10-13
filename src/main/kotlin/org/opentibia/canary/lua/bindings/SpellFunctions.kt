package org.opentibia.canary.lua.bindings

import io.github.oshai.kotlinlogging.KotlinLogging
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType
import org.opentibia.canary.lua.registry.Spells
import org.opentibia.canary.lua.runtime.LuaEnvironment

private val logger = KotlinLogging.logger {}

/**
 * Engine seam for the `Spell` binding group
 * (src/lua/functions/creatures/combat/spell_functions.cpp). The rune `register`
 * path updates the rune `ItemType` through [getItemType], and `spell:vocation`
 * resolves names through the `g_vocations()` equivalents.
 */
interface SpellOperations {
    /** `Item::items.getItemType(rune->getRuneItemId())`; auto-creates like the C++ map. */
    fun getItemType(id: Int): ItemType

    /** `g_vocations().getVocationId(name)`; `0` (`VOCATION_NONE`) when unknown. */
    fun vocationIdByName(name: String): Int

    /** `g_vocations().getVocation(id)->getVocName()`; null when the id is unknown. */
    fun vocationName(vocationId: Int): String?
}

/**
 * Registers the Canary `Spell` class from
 * src/lua/functions/creatures/combat/spell_functions.cpp:
 *
 *   Spell(words, name or id)   -- existing instant/rune spell look-up
 *   Spell(type)                -- SPELL_INSTANT / SPELL_RUNE
 *   spell:onCastSpell(callback)
 *   spell:register()
 *   spell:name([name])
 *   spell:id([id])
 *   spell:group([primaryGroup[, secondaryGroup]])
 *   spell:cooldown([cooldown])
 *   spell:groupCooldown([primaryCd[, secondaryCd]])
 *   spell:level([level])
 *   spell:magicLevel([level])
 *   spell:mana([mana])
 *   spell:manaPercent([percent])
 *   spell:soul([soul])
 *   spell:range([range])
 *   spell:isPremium([bool])
 *   spell:isEnabled([bool])
 *   spell:needTarget([bool])
 *   spell:needWeapon([bool])
 *   spell:needLearn([bool])
 *   spell:allowOnSelf([bool])
 *   spell:setPzLocked([bool])
 *   spell:isSelfTarget([bool])
 *   spell:isBlocking([blockingSolid, blockingCreature])
 *   spell:isAggressive([bool])
 *   spell:vocation([vocation...])
 *   spell:castSound([effect])
 *   spell:impactSound([effect])
 *   spell:words([words[, separator]])            -- instant only
 *   spell:needDirection([bool])                  -- instant only
 *   spell:hasParams([bool])                      -- instant only
 *   spell:hasPlayerNameParam([bool])             -- instant only
 *   spell:needCasterTargetOrDirection([bool])    -- instant only
 *   spell:isBlockingWalls([bool])                -- instant only
 *   spell:runeId([id])                           -- rune only
 *   spell:charges([charges])                     -- rune only
 *   spell:allowFarUse([bool])                    -- rune only
 *   spell:blockWalls([bool])                     -- rune only
 *   spell:checkFloor([bool])                     -- rune only
 *   spell:monkSpellType([type])
 *
 * The userdata carries the [LuaSpell] stored in the [Spells] registry, so a
 * looked-up `Spell(name)` shares state with the registered spell exactly like
 * Canary's `std::shared_ptr<Spell>`.
 */
object SpellFunctions {
    const val SPELL_CLASS = "Spell"
    const val SPELL_INSTANT = 1
    const val SPELL_RUNE = 2

    fun install(env: LuaEnvironment, spells: Spells, operations: SpellOperations = UnwiredSpellOperations) {
        env.globals.set("SPELL_INSTANT", LuaValue.valueOf(SPELL_INSTANT))
        env.globals.set("SPELL_RUNE", LuaValue.valueOf(SPELL_RUNE))
        env.globals.set(SPELL_CLASS, luaFunction { args -> createSpell(env, args, spells) })

        val registry = env.userdata
        registry.registerMethod(SPELL_CLASS, "__newindex", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell
            if (spell != null && args.arg(2).tojstring() == "onCastSpell") {
                spell.onCastSpell = args.arg(3)
            }
            LuaValue.NONE
        })

        registry.registerMethod(SPELL_CLASS, "onCastSpell", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell ?: return@luaFunction LuaValue.NIL
            spell.onCastSpell = if (args.arg(2).isfunction()) args.arg(2) else LuaValue.NIL
            LuaValue.valueOf(spell.onCastSpell.isfunction())
        })

        registry.registerMethod(SPELL_CLASS, "register", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell ?: run {
                logger.error { "[SpellFunctions::luaSpellRegister] - Spell not found" }
                return@luaFunction LuaValue.FALSE
            }

            when (spell.spellType) {
                SpellType.INSTANT -> {
                    if (!spell.onCastSpell.isfunction()) {
                        LuaValue.FALSE
                    } else {
                        LuaValue.valueOf(spells.registerInstant(spell))
                    }
                }

                SpellType.RUNE -> {
                    if (spell.magicLevel != 0 || spell.level != 0) {
                        val itemType = operations.getItemType(spell.runeId)
                        if (itemType.name.isEmpty()) {
                            itemType.name = spell.name
                        }
                        itemType.runeMagLevel = spell.magicLevel
                        itemType.runeLevel = spell.level
                        itemType.charges = spell.charges
                    }
                    if (!spell.onCastSpell.isfunction()) {
                        LuaValue.FALSE
                    } else {
                        LuaValue.valueOf(spells.registerRune(spell))
                    }
                }
            }
        })

        registry.registerMethod(SPELL_CLASS, "name", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(spell.name)
            } else {
                spell.name = luaString(args.arg(2))
                LuaValue.TRUE
            }
        })

        registry.registerMethod(SPELL_CLASS, "id", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell ?: return@luaFunction LuaValue.NIL
            if (spell.spellType != SpellType.INSTANT && spell.spellType != SpellType.RUNE) {
                logger.error {
                    "The method: 'spell:id(id)' is only for use of instant spells and rune spells"
                }
                return@luaFunction LuaValue.FALSE
            }
            if (args.narg() == 1) {
                LuaValue.valueOf(spell.spellId)
            } else {
                spell.spellId = luaNumber(args.arg(2)) and 0xFFFF
                LuaValue.TRUE
            }
        })

        registry.registerMethod(SPELL_CLASS, "group", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                return@luaFunction LuaValue.varargsOf(
                    LuaValue.valueOf(spell.group),
                    LuaValue.valueOf(spell.secondaryGroup),
                )
            }

            if (args.narg() == 2) {
                val group = luaNumber(args.arg(2))
                when {
                    group != 0 -> {
                        spell.group = group
                        LuaValue.TRUE
                    }

                    args.arg(2).isstring() -> {
                        val parsed = stringToSpellGroup(luaString(args.arg(2)))
                        if (parsed != 0) {
                            spell.group = parsed
                        } else {
                            logger.warn {
                                "[SpellFunctions::luaSpellGroup] - Unknown group: ${luaString(args.arg(2))}"
                            }
                            return@luaFunction LuaValue.FALSE
                        }
                        LuaValue.TRUE
                    }

                    else -> {
                        logger.warn {
                            "[SpellFunctions::luaSpellGroup] - Unknown group: ${luaString(args.arg(2))}"
                        }
                        LuaValue.FALSE
                    }
                }
            } else {
                // Canary reads the secondary group from argument #2 in this branch
                // (spell_functions.cpp:277); the numeric path reproduces that quirk.
                val primaryGroup = luaNumber(args.arg(2))
                val secondaryGroup = luaNumber(args.arg(2))
                when {
                    primaryGroup != 0 && secondaryGroup != 0 -> {
                        spell.group = primaryGroup
                        spell.secondaryGroup = secondaryGroup
                        LuaValue.TRUE
                    }

                    args.arg(2).isstring() && args.arg(3).isstring() -> {
                        val primary = stringToSpellGroup(luaString(args.arg(2)))
                        if (primary == 0) {
                            logger.warn {
                                "[SpellFunctions::luaSpellGroup] - Unknown primaryGroup: ${luaString(args.arg(2))}"
                            }
                            return@luaFunction LuaValue.FALSE
                        }
                        val secondary = stringToSpellGroup(luaString(args.arg(3)))
                        if (secondary == 0) {
                            logger.warn {
                                "[SpellFunctions::luaSpellGroup] - Unknown secondaryGroup: ${luaString(args.arg(3))}"
                            }
                            return@luaFunction LuaValue.FALSE
                        }
                        spell.group = primary
                        spell.secondaryGroup = secondary
                        LuaValue.TRUE
                    }

                    else -> {
                        logger.warn {
                            "[SpellFunctions::luaSpellGroup] - Unknown primaryGroup: " +
                                "${luaString(args.arg(2))} or secondaryGroup: ${luaString(args.arg(3))}"
                        }
                        LuaValue.FALSE
                    }
                }
            }
        })

        registry.registerMethod(SPELL_CLASS, "cooldown", luaFunction { args ->
            intProperty(args, { it.cooldown }, { spell, value -> spell.cooldown = value })
        })

        registry.registerMethod(SPELL_CLASS, "groupCooldown", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.varargsOf(
                    LuaValue.valueOf(spell.groupCooldown),
                    LuaValue.valueOf(spell.secondaryGroupCooldown),
                )
            } else if (args.narg() == 2) {
                spell.groupCooldown = luaNumber(args.arg(2))
                LuaValue.TRUE
            } else {
                spell.groupCooldown = luaNumber(args.arg(2))
                spell.secondaryGroupCooldown = luaNumber(args.arg(3))
                LuaValue.TRUE
            }
        })

        registry.registerMethod(SPELL_CLASS, "level", luaFunction { args ->
            intProperty(args, { it.level }, { spell, value -> spell.level = value })
        })

        registry.registerMethod(SPELL_CLASS, "magicLevel", luaFunction { args ->
            intProperty(args, { it.magicLevel }, { spell, value -> spell.magicLevel = value })
        })

        registry.registerMethod(SPELL_CLASS, "mana", luaFunction { args ->
            intProperty(args, { it.mana }, { spell, value -> spell.mana = value })
        })

        registry.registerMethod(SPELL_CLASS, "manaPercent", luaFunction { args ->
            intProperty(args, { it.manaPercent }, { spell, value -> spell.manaPercent = value })
        })

        registry.registerMethod(SPELL_CLASS, "soul", luaFunction { args ->
            intProperty(args, { it.soul }, { spell, value -> spell.soul = value })
        })

        registry.registerMethod(SPELL_CLASS, "range", luaFunction { args ->
            intProperty(args, { it.range }, { spell, value -> spell.range = value })
        })

        registry.registerMethod(SPELL_CLASS, "isPremium", luaFunction { args ->
            boolProperty(args, { it.premium }, { spell, value -> spell.premium = value })
        })

        registry.registerMethod(SPELL_CLASS, "isEnabled", luaFunction { args ->
            boolProperty(args, { it.enabled }, { spell, value -> spell.enabled = value })
        })

        registry.registerMethod(SPELL_CLASS, "needTarget", luaFunction { args ->
            boolProperty(args, { it.needTarget }, { spell, value -> spell.needTarget = value })
        })

        registry.registerMethod(SPELL_CLASS, "needWeapon", luaFunction { args ->
            boolProperty(args, { it.needWeapon }, { spell, value -> spell.needWeapon = value })
        })

        registry.registerMethod(SPELL_CLASS, "needLearn", luaFunction { args ->
            boolProperty(args, { it.needLearn }, { spell, value -> spell.needLearn = value })
        })

        registry.registerMethod(SPELL_CLASS, "allowOnSelf", luaFunction { args ->
            boolProperty(args, { it.allowOnSelf }, { spell, value -> spell.allowOnSelf = value })
        })

        registry.registerMethod(SPELL_CLASS, "setPzLocked", luaFunction { args ->
            boolProperty(args, { it.pzLocked }, { spell, value -> spell.pzLocked = value })
        })

        registry.registerMethod(SPELL_CLASS, "isSelfTarget", luaFunction { args ->
            boolProperty(args, { it.selfTarget }, { spell, value -> spell.selfTarget = value })
        })

        registry.registerMethod(SPELL_CLASS, "isBlocking", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.varargsOf(
                    LuaValue.valueOf(spell.blockingSolid),
                    LuaValue.valueOf(spell.blockingCreature),
                )
            } else {
                spell.blockingSolid = args.arg(2).toboolean()
                spell.blockingCreature = args.arg(3).toboolean()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(SPELL_CLASS, "isAggressive", luaFunction { args ->
            boolProperty(args, { it.aggressive }, { spell, value -> spell.aggressive = value })
        })

        registry.registerMethod(SPELL_CLASS, "vocation", luaFunction { args ->
            val spell = retrieveUserdata(args, 1) as? LuaSpell ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                val table = LuaTable()
                // Canary iterates `Spell::vocSpellMap` (std::map ordered by id).
                spell.vocSpellMap.keys.sorted().forEachIndexed { index, vocationId ->
                    operations.vocationName(vocationId)?.let { table.set(index + 1, LuaValue.valueOf(it)) }
                }
                table.setmetatable(env.userdata.metatable(SPELL_CLASS))
                return@luaFunction table
            }

            val parameters = args.narg() - 1
            for (index in 0 until parameters) {
                val raw = luaString(args.arg(2 + index))
                if (raw.contains(';')) {
                    val vocationList = raw.split(';')
                    val vocationId = operations.vocationIdByName(vocationList[0])
                    if (vocationList.size > 1) {
                        spell.addVocMap(vocationId, vocationList[1] == "true")
                    }
                    spell.vocations += raw
                } else {
                    val vocationId = operations.vocationIdByName(raw)
                    spell.addVocMap(vocationId, false)
                    spell.vocations += raw
                }
            }
            LuaValue.TRUE
        })

        registry.registerMethod(SPELL_CLASS, "castSound", luaFunction { args ->
            intProperty(args, { it.soundCastEffect }, { spell, value -> spell.soundCastEffect = value and 0xFFFF })
        })

        registry.registerMethod(SPELL_CLASS, "impactSound", luaFunction { args ->
            intProperty(args, { it.soundImpactEffect }, { spell, value -> spell.soundImpactEffect = value and 0xFFFF })
        })

        registry.registerMethod(SPELL_CLASS, "words", luaFunction { args ->
            val spell = instantState(args) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.varargsOf(LuaValue.valueOf(spell.words), LuaValue.valueOf(spell.separator))
            } else {
                val separator = if (args.narg() == 3) luaString(args.arg(3)) else ""
                spell.words = luaString(args.arg(2))
                spell.separator = separator
                LuaValue.TRUE
            }
        })

        registry.registerMethod(SPELL_CLASS, "needDirection", luaFunction { args ->
            instantProperty(args, { it.needDirection }, { spell, value -> spell.needDirection = value })
        })

        registry.registerMethod(SPELL_CLASS, "hasParams", luaFunction { args ->
            instantProperty(args, { it.hasParams }, { spell, value -> spell.hasParams = value })
        })

        registry.registerMethod(SPELL_CLASS, "hasPlayerNameParam", luaFunction { args ->
            instantProperty(
                args,
                { it.hasPlayerNameParam },
                { spell, value -> spell.hasPlayerNameParam = value },
            )
        })

        registry.registerMethod(SPELL_CLASS, "needCasterTargetOrDirection", luaFunction { args ->
            instantProperty(
                args,
                { it.needCasterTargetOrDirection },
                { spell, value -> spell.needCasterTargetOrDirection = value },
            )
        })

        registry.registerMethod(SPELL_CLASS, "isBlockingWalls", luaFunction { args ->
            instantProperty(args, { it.blockWalls }, { spell, value -> spell.blockWalls = value })
        })

        registry.registerMethod(SPELL_CLASS, "runeId", luaFunction { args ->
            val spell = runeState(args) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(spell.runeId)
            } else {
                spell.runeId = luaNumber(args.arg(2)) and 0xFFFF
                LuaValue.TRUE
            }
        })

        registry.registerMethod(SPELL_CLASS, "charges", luaFunction { args ->
            val spell = runeState(args) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(spell.charges)
            } else {
                spell.charges = luaNumber(args.arg(2))
                LuaValue.TRUE
            }
        })

        registry.registerMethod(SPELL_CLASS, "allowFarUse", luaFunction { args ->
            runeProperty(args, { it.allowFarUse }, { spell, value -> spell.allowFarUse = value })
        })

        registry.registerMethod(SPELL_CLASS, "blockWalls", luaFunction { args ->
            runeProperty(args, { it.blockWalls }, { spell, value -> spell.blockWalls = value })
        })

        registry.registerMethod(SPELL_CLASS, "checkFloor", luaFunction { args ->
            runeProperty(args, { it.checkFloor }, { spell, value -> spell.checkFloor = value })
        })

        registry.registerMethod(SPELL_CLASS, "monkSpellType", luaFunction { args ->
            intProperty(
                args,
                { it.monkSpellType },
                { spell, value -> spell.monkSpellType = value and 0xFF },
            )
        })
    }

    private fun createSpell(env: LuaEnvironment, args: Varargs, spells: Spells): LuaValue {
        if (args.narg() < 1) {
            logger.error { "[SpellFunctions::luaSpellCreate] - There is no parameter set!" }
            return LuaValue.NIL
        }

        val argument = args.arg(1)
        var spellType = 0

        if (argument.tonumber().isnumber()) {
            val id = luaNumber(argument) and 0xFFFF
            spells.getRune(id)?.let { return env.userdata.wrap(SPELL_CLASS, it) }
            spellType = id
        } else if (argument.isstring()) {
            val name = argument.tojstring()
            spells.getInstantByName(name)?.let { return env.userdata.wrap(SPELL_CLASS, it) }
            spells.getInstantSpell(name)?.let { return env.userdata.wrap(SPELL_CLASS, it) }
            spells.getRuneByName(name)?.let { return env.userdata.wrap(SPELL_CLASS, it) }

            when (name.lowercase()) {
                "instant" -> spellType = SPELL_INSTANT
                "rune" -> spellType = SPELL_RUNE
            }
        }

        return when (spellType) {
            SPELL_INSTANT -> env.userdata.wrap(
                SPELL_CLASS,
                LuaSpell(spellType = SpellType.INSTANT),
            )

            SPELL_RUNE -> env.userdata.wrap(
                SPELL_CLASS,
                LuaSpell(spellType = SpellType.RUNE),
            )

            else -> LuaValue.NIL
        }
    }

    private fun state(args: Varargs): LuaSpell? = retrieveUserdata(args, 1) as? LuaSpell

    private fun instantState(args: Varargs): LuaSpell? =
        state(args)?.takeIf { it.spellType == SpellType.INSTANT }

    private fun runeState(args: Varargs): LuaSpell? =
        state(args)?.takeIf { it.spellType == SpellType.RUNE }

    private fun intProperty(
        args: Varargs,
        get: (LuaSpell) -> Int,
        set: (LuaSpell, Int) -> Unit,
    ): LuaValue {
        val spell = state(args) ?: return LuaValue.NIL
        return if (args.narg() == 1) {
            LuaValue.valueOf(get(spell))
        } else {
            set(spell, luaNumber(args.arg(2)))
            LuaValue.TRUE
        }
    }

    private fun boolProperty(
        args: Varargs,
        get: (LuaSpell) -> Boolean,
        set: (LuaSpell, Boolean) -> Unit,
    ): LuaValue {
        val spell = state(args) ?: return LuaValue.NIL
        return if (args.narg() == 1) {
            LuaValue.valueOf(get(spell))
        } else {
            set(spell, args.arg(2).toboolean())
            LuaValue.TRUE
        }
    }

    private fun instantProperty(
        args: Varargs,
        get: (LuaSpell) -> Boolean,
        set: (LuaSpell, Boolean) -> Unit,
    ): LuaValue {
        val spell = instantState(args) ?: return LuaValue.NIL
        return if (args.narg() == 1) {
            LuaValue.valueOf(get(spell))
        } else {
            set(spell, args.arg(2).toboolean())
            LuaValue.TRUE
        }
    }

    private fun runeProperty(
        args: Varargs,
        get: (LuaSpell) -> Boolean,
        set: (LuaSpell, Boolean) -> Unit,
    ): LuaValue {
        val spell = runeState(args) ?: return LuaValue.NIL
        return if (args.narg() == 1) {
            LuaValue.valueOf(get(spell))
        } else {
            set(spell, args.arg(2).toboolean())
            LuaValue.TRUE
        }
    }

    /** `stringToSpellGroup` (`src/utils/tools.cpp:1633-1671`). */
    private fun stringToSpellGroup(value: String): Int = when (value.lowercase()) {
        "attack", "1" -> 1
        "healing", "2" -> 2
        "support", "3" -> 3
        "special", "4" -> 4
        "crippling", "6" -> 6
        "focus", "7" -> 7
        "ultimatestrikes", "8" -> 8
        "burstsofnature", "9" -> 9
        "greatbeams", "10" -> 10
        "virtue", "11" -> 11
        else -> 0
    }
}

internal object UnwiredSpellOperations : SpellOperations {
    override fun getItemType(id: Int): ItemType =
        throw NotImplementedError("SpellFunctions.getItemType seam is not wired")

    override fun vocationIdByName(name: String): Int =
        throw NotImplementedError("SpellFunctions.vocationIdByName seam is not wired")

    override fun vocationName(vocationId: Int): String? =
        throw NotImplementedError("SpellFunctions.vocationName seam is not wired")
}
