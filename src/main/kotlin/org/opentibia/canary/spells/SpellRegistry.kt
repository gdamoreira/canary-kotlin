package org.opentibia.canary.spells

import org.opentibia.canary.creatures.Player

typealias SpellAction = (caster: Player) -> Boolean

data class Spell(
    val words: String,
    val name: String,
    val manaCost: Int,
    val levelRequired: Int,
    val action: SpellAction
)

class SpellRegistry {
    private val spells = mutableMapOf<String, Spell>()

    fun register(spell: Spell) {
        spells[spell.words.lowercase()] = spell
    }

    fun castSpell(caster: Player, words: String): Boolean {
        val spell = spells[words.lowercase()] ?: return false

        if (caster.level < spell.levelRequired) return false
        if (caster.mana < spell.manaCost) return false

        val executed = spell.action(caster)
        if (executed) {
            caster.mana -= spell.manaCost
            return true
        }
        return false
    }
}
