package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t

/**
 * The pronoun helpers `Player` delegates to in `src/utils/tools.cpp`:
 *
 * - [getPlayerSubjectPronoun] (tools.cpp:1895-1910)
 * - [getPlayerObjectPronoun] (tools.cpp:1912-1927)
 * - [getPlayerReflexivePronoun] (tools.cpp:1946-1961)
 * - [getVerbForPronoun] (tools.cpp:1963-1968)
 *
 * `Player::getPossessivePronoun` (player.cpp:7096-7098) already lives on the
 * host player; the extensions below add the remaining `Player::` siblings
 * (player.cpp:7088-7107).
 */
object PlayerPronouns {

    /** `getPlayerSubjectPronoun` (tools.cpp:1895-1910). */
    fun getPlayerSubjectPronoun(pronoun: PlayerPronoun_t, sex: PlayerSex_t, name: String): String =
        when (pronoun) {
            PlayerPronoun_t.PLAYERPRONOUN_THEY -> "they"
            PlayerPronoun_t.PLAYERPRONOUN_SHE -> "she"
            PlayerPronoun_t.PLAYERPRONOUN_HE -> "he"
            PlayerPronoun_t.PLAYERPRONOUN_ZE -> "ze"
            PlayerPronoun_t.PLAYERPRONOUN_NAME -> name
            PlayerPronoun_t.PLAYERPRONOUN_UNSET -> if (sex == PlayerSex_t.FEMALE) "she" else "he"
        }

    /** `getPlayerObjectPronoun` (tools.cpp:1912-1927). */
    fun getPlayerObjectPronoun(pronoun: PlayerPronoun_t, sex: PlayerSex_t, name: String): String =
        when (pronoun) {
            PlayerPronoun_t.PLAYERPRONOUN_THEY -> "them"
            PlayerPronoun_t.PLAYERPRONOUN_SHE -> "her"
            PlayerPronoun_t.PLAYERPRONOUN_HE -> "him"
            PlayerPronoun_t.PLAYERPRONOUN_ZE -> "zir"
            PlayerPronoun_t.PLAYERPRONOUN_NAME -> name
            PlayerPronoun_t.PLAYERPRONOUN_UNSET -> if (sex == PlayerSex_t.FEMALE) "her" else "him"
        }

    /** `getPlayerReflexivePronoun` (tools.cpp:1946-1961). */
    fun getPlayerReflexivePronoun(pronoun: PlayerPronoun_t, sex: PlayerSex_t, name: String): String =
        when (pronoun) {
            PlayerPronoun_t.PLAYERPRONOUN_THEY -> "themself"
            PlayerPronoun_t.PLAYERPRONOUN_SHE -> "herself"
            PlayerPronoun_t.PLAYERPRONOUN_HE -> "himself"
            PlayerPronoun_t.PLAYERPRONOUN_ZE -> "zirself"
            PlayerPronoun_t.PLAYERPRONOUN_NAME -> name
            PlayerPronoun_t.PLAYERPRONOUN_UNSET -> if (sex == PlayerSex_t.FEMALE) "herself" else "himself"
        }

    /** `getVerbForPronoun` (tools.cpp:1963-1968). */
    fun getVerbForPronoun(pronoun: PlayerPronoun_t, pastTense: Boolean): String {
        if (pronoun == PlayerPronoun_t.PLAYERPRONOUN_THEY) {
            return if (pastTense) "were" else "are"
        }
        return if (pastTense) "was" else "is"
    }
}

/** `Player::getSubjectPronoun` (player.cpp:7092-7094). */
fun Player.getSubjectPronoun(): String =
    PlayerPronouns.getPlayerSubjectPronoun(getPronoun(), getSex(), name)

/** `Player::getObjectPronoun` (player.cpp:7088-7090). */
fun Player.getObjectPronoun(): String =
    PlayerPronouns.getPlayerObjectPronoun(getPronoun(), getSex(), name)

/** `Player::getReflexivePronoun` (player.cpp:7100-7102). */
fun Player.getReflexivePronoun(): String =
    PlayerPronouns.getPlayerReflexivePronoun(getPronoun(), getSex(), name)

/** `Player::getSubjectVerb` (player.cpp:7104-7106); `past = false` by default. */
fun Player.getSubjectVerb(past: Boolean = false): String =
    PlayerPronouns.getVerbForPronoun(getPronoun(), past)
