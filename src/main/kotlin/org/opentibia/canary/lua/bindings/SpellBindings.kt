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
 *   rune:level(20)
 *   rune:mana(80)
 *   rune:cooldown(2000)
 *   rune:group(1)
 *   rune:vocation("sorcerer")
 *   rune:range(3)
 *   function rune.onCastSpell(creature, var, isHotkey) ... end
 *   rune:register()
 *
 * `Spell("instant")` / `Spell("rune")` (or the `SPELL_INSTANT` / `SPELL_RUNE` enum
 * values) create a new spell, mirroring SpellFunctions::luaSpellCreate. Each
 * constructor setter also supports the zero-argument getter form of its C++ counterpart
 * (e.g. `spell:level()` returns the stored level).
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
                var level = 0
                var magicLevel = 0
                var mana = 0
                var cooldown = 0
                var group = 0
                var secondaryGroup = 0
                var groupCooldown = 0
                var secondaryGroupCooldown = 0
                var range = 0
                val vocations = mutableListOf<String>()

                self.set("name", stringField({ name }, { name = it }))
                self.set("id", intField({ spellId }, { spellId = it }))
                self.set("words", stringField({ words }, { words = it }))
                self.set("runeId", intField({ runeId }, { runeId = it }))
                self.set("level", intField({ level }, { level = it }))
                self.set("magicLevel", intField({ magicLevel }, { magicLevel = it }))
                self.set("mana", intField({ mana }, { mana = it }))
                self.set("cooldown", intField({ cooldown }, { cooldown = it }))
                self.set("range", intField({ range }, { range = it }))
                self.set("group", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        if (a.narg() <= 1) {
                            return LuaValue.varargsOf(LuaValue.valueOf(group), LuaValue.valueOf(secondaryGroup))
                        }
                        group = a.arg(2).toint()
                        if (a.narg() > 2) {
                            secondaryGroup = a.arg(3).toint()
                        }
                        return LuaValue.TRUE
                    }
                })
                self.set("groupCooldown", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        if (a.narg() <= 1) {
                            return LuaValue.varargsOf(
                                LuaValue.valueOf(groupCooldown),
                                LuaValue.valueOf(secondaryGroupCooldown),
                            )
                        }
                        groupCooldown = a.arg(2).toint()
                        if (a.narg() > 2) {
                            secondaryGroupCooldown = a.arg(3).toint()
                        }
                        return LuaValue.TRUE
                    }
                })
                self.set("vocation", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        if (a.narg() <= 1) {
                            val table = LuaTable()
                            vocations.forEachIndexed { index, vocation ->
                                table.set(index + 1, LuaValue.valueOf(vocation))
                            }
                            return table
                        }
                        for (i in 2..a.narg()) {
                            vocations.add(a.arg(i).tojstring())
                        }
                        return LuaValue.TRUE
                    }
                })

                self.set("register", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val onCastSpell = a.arg1().get("onCastSpell")
                        if (!onCastSpell.isfunction()) {
                            return LuaValue.FALSE
                        }
                        val spell = LuaSpell(
                            name = name,
                            spellType = spellType,
                            spellId = spellId,
                            words = words,
                            runeId = runeId,
                            onCastSpell = onCastSpell,
                            level = level,
                            magicLevel = magicLevel,
                            mana = mana,
                            cooldown = cooldown,
                            group = group,
                            secondaryGroup = secondaryGroup,
                            groupCooldown = groupCooldown,
                            secondaryGroupCooldown = secondaryGroupCooldown,
                            range = range,
                            vocations = vocations.toList(),
                        )
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

    private fun intField(get: () -> Int, set: (Int) -> Unit): LuaValue = object : VarArgFunction() {
        override fun invoke(a: Varargs): Varargs {
            if (a.narg() <= 1) {
                return LuaValue.valueOf(get())
            }
            set(a.arg(2).toint())
            return LuaValue.TRUE
        }
    }

    private fun stringField(get: () -> String, set: (String) -> Unit): LuaValue = object : VarArgFunction() {
        override fun invoke(a: Varargs): Varargs {
            if (a.narg() <= 1) {
                return LuaValue.valueOf(get())
            }
            set(a.arg(2).tojstring())
            return LuaValue.TRUE
        }
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
