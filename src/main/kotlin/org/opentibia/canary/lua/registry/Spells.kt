package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue
import org.opentibia.canary.spells.SoundEffect

enum class SpellType {
    INSTANT,
    RUNE
}

/**
 * Mutable mirror of the `Spell` state the Lua binding populates
 * (src/creatures/combat/spells.hpp). The Lua `Spell` userdata in Canary stores
 * a shared `Spell` pointer, so every setter mutates the same object returned by
 * later `Spell(name)`/`Spell(id)` look-ups; the Kotlin registry keeps the same
 * identity contract.
 *
 * @param onCastSpell the `onCastSpell` callback captured by the revscriptsys
 *   `SpellNewIndex`/`spell:onCastSpell` path.
 */
class LuaSpell(
    var name: String = "",
    var spellType: SpellType,
    var spellId: Int = 0,
    var words: String = "",
    var runeId: Int = 0,
    var charges: Int = 0,
    var onCastSpell: LuaValue = LuaValue.NIL,
    var level: Int = 0,
    var magicLevel: Int = 0,
    var mana: Int = 0,
    var manaPercent: Int = 0,
    var soul: Int = 0,
    var cooldown: Int = 1000,
    var group: Int = 0,
    var secondaryGroup: Int = 0,
    var groupCooldown: Int = 1000,
    var secondaryGroupCooldown: Int = 0,
    var range: Int = -1,
    val vocations: MutableList<String> = mutableListOf(),
    val vocSpellMap: MutableMap<Int, Boolean> = linkedMapOf(),
    var premium: Boolean = false,
    var enabled: Boolean = true,
    var needTarget: Boolean = false,
    var needWeapon: Boolean = false,
    var needLearn: Boolean = false,
    var allowOnSelf: Boolean = true,
    var pzLocked: Boolean = false,
    var selfTarget: Boolean = false,
    var blockingSolid: Boolean = false,
    var blockingCreature: Boolean = false,
    var aggressive: Boolean = true,
    var soundCastEffect: Int = SoundEffect.SPELL_OR_RUNE.value,
    var soundImpactEffect: Int = SoundEffect.SILENCE.value,
    var separator: String = "",
    var needDirection: Boolean = false,
    var hasParams: Boolean = false,
    var hasPlayerNameParam: Boolean = false,
    var needCasterTargetOrDirection: Boolean = false,
    var blockWalls: Boolean = true,
    var checkFloor: Boolean = true,
    var allowFarUse: Boolean = false,
    var monkSpellType: Int = 0,
) {
    /** `Spell::addVocMap` (`spells.cpp:1039-1047`). */
    fun addVocMap(vocationId: Int, value: Boolean) {
        if (vocationId == 0xFFFF) {
            return
        }
        vocSpellMap[vocationId] = value
    }
}

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

    /** `Spells::getInstantSpellByName` (`spells.cpp:328-335`), case-insensitive. */
    fun getInstantByName(name: String): LuaSpell? =
        instants.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** `Spells::getRuneSpellByName` (`spells.cpp:276-283`), case-insensitive. */
    fun getRuneByName(name: String): LuaSpell? =
        runes.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * `Spells::getInstantSpell` (`spells.cpp:285-317`): the longest
     * case-insensitive words prefix wins and extra text is only accepted for
     * `hasParam` spells with a space separator and at least one parameter
     * character.
     */
    fun getInstantSpell(words: String): LuaSpell? {
        var result: LuaSpell? = null

        for (instant in instants.values) {
            val instantWords = instant.words
            val spellLen = instantWords.length
            if (words.length < spellLen) {
                continue
            }
            if (!words.regionMatches(0, instantWords, 0, spellLen, ignoreCase = true)) {
                continue
            }

            if (result == null || spellLen > result.words.length) {
                result = instant
                if (words.length == spellLen) {
                    break
                }
            }
        }

        val found = result ?: return null
        val resultWords = found.words
        if (words.length > resultWords.length) {
            if (!found.hasParams) {
                return null
            }

            val spellLen = resultWords.length
            val paramLen = words.length - spellLen
            if (paramLen < 2 || words[spellLen] != ' ') {
                return null
            }
        }
        return found
    }

    fun instantWords(): Set<String> = instants.keys.toSet()

    fun runeIds(): Set<Int> = runes.keys.toSet()
}
