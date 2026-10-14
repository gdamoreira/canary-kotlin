package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.map.Position

/**
 * Transcribes the pronoun helpers of Canary:
 *
 * - tools.cpp:1895-1910 `getPlayerSubjectPronoun`
 * - tools.cpp:1912-1927 `getPlayerObjectPronoun`
 * - tools.cpp:1946-1961 `getPlayerReflexivePronoun`
 * - tools.cpp:1963-1968 `getVerbForPronoun`
 * - player.cpp:7092-7107 `Player::getSubjectPronoun`, `getObjectPronoun`,
 *   `getReflexivePronoun`, `getSubjectVerb(bool past)`
 *
 * `Player::getPossessivePronoun` already lives on the host Player
 * (Creature.kt). This file adds the three sibling nouns plus the verb helper.
 */
class PlayerPronounsTest {

    private fun newPlayer(
        pronoun: PlayerPronoun_t = PlayerPronoun_t.PLAYERPRONOUN_UNSET,
        sex: PlayerSex_t = PlayerSex_t.MALE,
        name: String = "Hero",
    ) = Player(
        id = 9,
        name = name,
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    ).apply {
        setPronoun(pronoun)
        setSex(sex)
    }

    @Test
    fun `subject pronoun follows the pronoun then sex fallback`() {
        // tools.cpp:1895-1910.
        val name = "Arachna"

        assertThat(PlayerPronouns.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, PlayerSex_t.MALE, name)).isEqualTo("they")
        assertThat(PlayerPronouns.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, PlayerSex_t.MALE, name)).isEqualTo("she")
        assertThat(PlayerPronouns.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_HE, PlayerSex_t.FEMALE, name)).isEqualTo("he")
        assertThat(PlayerPronouns.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_ZE, PlayerSex_t.MALE, name)).isEqualTo("ze")
        assertThat(PlayerPronouns.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME, PlayerSex_t.MALE, name)).isEqualTo(name)
        assertThat(PlayerPronouns.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.FEMALE, name)).isEqualTo("she")
        assertThat(PlayerPronouns.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.MALE, name)).isEqualTo("he")
    }

    @Test
    fun `object pronoun follows the pronoun then sex fallback`() {
        // tools.cpp:1912-1927.
        val name = "Arachna"

        assertThat(PlayerPronouns.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, PlayerSex_t.MALE, name)).isEqualTo("them")
        assertThat(PlayerPronouns.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, PlayerSex_t.MALE, name)).isEqualTo("her")
        assertThat(PlayerPronouns.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_HE, PlayerSex_t.FEMALE, name)).isEqualTo("him")
        assertThat(PlayerPronouns.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_ZE, PlayerSex_t.MALE, name)).isEqualTo("zir")
        assertThat(PlayerPronouns.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME, PlayerSex_t.MALE, name)).isEqualTo(name)
        assertThat(PlayerPronouns.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.FEMALE, name)).isEqualTo("her")
        assertThat(PlayerPronouns.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.MALE, name)).isEqualTo("him")
    }

    @Test
    fun `reflexive pronoun follows the pronoun then sex fallback`() {
        // tools.cpp:1946-1961.
        val name = "Arachna"

        assertThat(PlayerPronouns.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, PlayerSex_t.MALE, name)).isEqualTo("themself")
        assertThat(PlayerPronouns.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, PlayerSex_t.MALE, name)).isEqualTo("herself")
        assertThat(PlayerPronouns.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_HE, PlayerSex_t.FEMALE, name)).isEqualTo("himself")
        assertThat(PlayerPronouns.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_ZE, PlayerSex_t.MALE, name)).isEqualTo("zirself")
        assertThat(PlayerPronouns.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME, PlayerSex_t.MALE, name)).isEqualTo(name)
        assertThat(PlayerPronouns.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.FEMALE, name)).isEqualTo("herself")
        assertThat(PlayerPronouns.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.MALE, name)).isEqualTo("himself")
    }

    @Test
    fun `verb only pluralizes for the they pronoun`() {
        // tools.cpp:1963-1968.
        assertThat(PlayerPronouns.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, pastTense = false)).isEqualTo("are")
        assertThat(PlayerPronouns.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, pastTense = true)).isEqualTo("were")
        assertThat(PlayerPronouns.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, pastTense = false)).isEqualTo("is")
        assertThat(PlayerPronouns.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, pastTense = true)).isEqualTo("was")
        assertThat(PlayerPronouns.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME, pastTense = false)).isEqualTo("is")
        assertThat(PlayerPronouns.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, pastTense = true)).isEqualTo("was")
    }

    @Test
    fun `player accessors read the host pronoun and sex`() {
        // player.cpp:7092-7107.
        val they = newPlayer(pronoun = PlayerPronoun_t.PLAYERPRONOUN_THEY, name = "Vash")
        assertThat(they.getSubjectPronoun()).isEqualTo("they")
        assertThat(they.getObjectPronoun()).isEqualTo("them")
        assertThat(they.getReflexivePronoun()).isEqualTo("themself")
        assertThat(they.getSubjectVerb()).isEqualTo("are")
        assertThat(they.getSubjectVerb(past = true)).isEqualTo("were")

        val female = newPlayer(sex = PlayerSex_t.FEMALE)
        assertThat(female.getSubjectPronoun()).isEqualTo("she")
        assertThat(female.getObjectPronoun()).isEqualTo("her")
        assertThat(female.getReflexivePronoun()).isEqualTo("herself")
        assertThat(female.getSubjectVerb()).isEqualTo("is")

        val named = newPlayer(pronoun = PlayerPronoun_t.PLAYERPRONOUN_NAME, name = "Vash")
        assertThat(named.getSubjectPronoun()).isEqualTo("Vash")
        assertThat(named.getObjectPronoun()).isEqualTo("Vash")
        assertThat(named.getReflexivePronoun()).isEqualTo("Vash")
    }
}
