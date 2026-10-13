package org.opentibia.canary.spells

/**
 * Learned instant-spell names owned by a player.
 *
 * Transcribes `Player::learnedInstantSpellList` and its three operations
 * (`player.cpp:3122-3145`). In Canary the list lives on `Player`; since the
 * port may not touch `Player` for this task, it is a standalone state holder
 * the [SpellCaster] seam exposes. The flag short-circuits of
 * `Player::hasLearnedInstantSpell` live in [SpellEngine.hasLearnedInstantSpell].
 */
class SpellBook {

    /** `Player::learnedInstantSpellList` (`player.hpp:1696`). */
    val learnedInstantSpellList: MutableList<String> = mutableListOf()

    /** `Player::learnInstantSpell` list append (`player.cpp:3122-3126`). */
    fun learnInstantSpell(spellName: String) {
        if (!hasLearned(spellName)) {
            learnedInstantSpellList.add(spellName)
        }
    }

    /** `Player::forgetInstantSpell` (`player.cpp:3128-3130`) — `std::erase`. */
    fun forgetInstantSpell(spellName: String) {
        learnedInstantSpellList.removeAll { it == spellName }
    }

    /** Case-insensitive membership (`player.cpp:3141-3143`). */
    fun hasLearned(spellName: String): Boolean =
        learnedInstantSpellList.any { it.equals(spellName, ignoreCase = true) }
}
