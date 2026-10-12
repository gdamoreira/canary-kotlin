package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue

enum class SpellType {
    INSTANT,
    RUNE
}

data class LuaSpell(
    val name: String,
    val spellType: SpellType,
    val spellId: Int,
    val words: String,
    val runeId: Int,
    val onCastSpell: LuaValue
)

/**
 * Mirrors Canary's Spells registry (src/creatures/combat/spells.hpp): instant spells
 * are keyed by cast words, rune spells by rune item id, both populated by
 * `Spell(type):register()`.
 */
class Spells {
    private val instants = LinkedHashMap<String, LuaSpell>()
    private val runes = LinkedHashMap<Int, LuaSpell>()

    fun registerInstant(spell: LuaSpell): Boolean {
        if (spell.words.isBlank() || instants.containsKey(spell.words)) {
            return false
        }
        instants[spell.words] = spell
        return true
    }

    fun registerRune(spell: LuaSpell): Boolean {
        if (runes.containsKey(spell.runeId)) {
            return false
        }
        runes[spell.runeId] = spell
        return true
    }

    fun getInstant(words: String): LuaSpell? = instants[words]

    fun getRune(runeId: Int): LuaSpell? = runes[runeId]

    fun instantWords(): Set<String> = instants.keys.toSet()

    fun runeIds(): Set<Int> = runes.keys.toSet()
}
