package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType
import org.opentibia.canary.lua.registry.Spells
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the global `Spell(type)` constructor used by revscriptsys.lua scripts:
 *
 *   local rune = Spell("rune")
 *   rune:name("explosion rune")
 *   rune:id(18)
 *   rune:runeId(3200)
 *   function rune.onCastSpell(creature, var, isHotkey) ... end
 *   rune:register()
 *
 * `Spell("instant")` / `Spell("rune")` (or the `SPELL_INSTANT` / `SPELL_RUNE` enum
 * values) create a new spell, mirroring SpellFunctions::luaSpellCreate.
 */
object SpellBindings {
    private const val SPELL_INSTANT = 1
    private const val SPELL_RUNE = 2

    fun install(env: LuaEnvironment): Spells {
        val spells = Spells()

        env.globals.set("SPELL_INSTANT", LuaValue.valueOf(SPELL_INSTANT))
        env.globals.set("SPELL_RUNE", LuaValue.valueOf(SPELL_RUNE))

        env.globals.set("Spell", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val spellType = resolveType(args.arg(1)) ?: return LuaValue.NIL
                val self = LuaTable()
                var name = ""
                var spellId = 0
                var words = ""
                var runeId = 0

                self.set("name", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        name = a.arg(2).tojstring()
                        return LuaValue.NONE
                    }
                })

                self.set("id", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        spellId = a.arg(2).checkint()
                        return LuaValue.NONE
                    }
                })

                self.set("words", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        words = a.arg(2).checkjstring()
                        return LuaValue.NONE
                    }
                })

                self.set("runeId", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        runeId = a.arg(2).checkint()
                        return LuaValue.NONE
                    }
                })

                self.set("register", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val onCastSpell = a.arg1().get("onCastSpell")
                        if (!onCastSpell.isfunction()) {
                            return LuaValue.FALSE
                        }
                        val spell = LuaSpell(name, spellType, spellId, words, runeId, onCastSpell)
                        val registered = when (spellType) {
                            SpellType.INSTANT -> spells.registerInstant(spell)
                            SpellType.RUNE -> spells.registerRune(spell)
                        }
                        return LuaValue.valueOf(registered)
                    }
                })

                return self
            }
        })

        return spells
    }

    private fun resolveType(value: LuaValue): SpellType? {
        if (value.type() == LuaValue.TSTRING) {
            return when (value.tojstring().lowercase()) {
                "instant" -> SpellType.INSTANT
                "rune" -> SpellType.RUNE
                else -> null
            }
        }
        if (value.type() == LuaValue.TNUMBER) {
            return when (value.toint()) {
                SPELL_INSTANT -> SpellType.INSTANT
                SPELL_RUNE -> SpellType.RUNE
                else -> null
            }
        }
        return null
    }
}
