package org.opentibia.canary.systems.bestiary

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Charm definition/state parity tests, transcribed from src/io/iobestiary.cpp
 * and src/creatures/players/player.cpp (CharmInfo, parseRacebyCharm, tier).
 *
 * Charm point tables mirror the Lua config in data/scripts/systems/bestiary_charms.lua
 * (which stays Lua); only the engine state is asserted here.
 */
class BestiaryCharmTest {

    private val wound = Charm(
        name = "Wound",
        id = CharmRune.CHARM_WOUND,
        description = "Deals physical damage.",
        category = CharmCategory.CHARM_MAJOR,
        type = CharmType.CHARM_OFFENSIVE,
        points = listOf(240, 360, 1200),
        binary = 1 shl CharmRune.CHARM_WOUND.value,
        chance = listOf(5.0, 10.0, 11.0),
        percent = 5.0,
    )

    private val dragon = MonsterBestiaryInfo(
        raceid = 34,
        name = "Dragon",
        bestiaryFirstUnlock = 50,
        bestiarySecondUnlock = 500,
        bestiaryToUnlock = 1000,
        bestiaryCharmsPoints = 25,
        bestiaryStars = 3,
    )

    @Test
    fun `charm exposes exact point table and tiered chance`() {
        assertThat(wound.points).containsExactly(240, 360, 1200)
        assertThat(wound.getChance(0)).isEqualTo(5.0)
        assertThat(wound.getChance(1)).isEqualTo(10.0)
        assertThat(wound.getChance(2)).isEqualTo(11.0)
        assertThat(wound.getChance(3)).isEqualTo(0.0)
        assertThat(wound.binary).isEqualTo(1)
    }

    @Test
    fun `charm tier defaults to zero and is assignable`() {
        val state = PlayerBestiaryState()

        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(0)
        state.setCharmTier(CharmRune.CHARM_WOUND, 1)
        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(1)
        state.setCharmTier(CharmRune.CHARM_WOUND, 3)
        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(3)

        // CHARM_NONE is invalid and always yields tier 0.
        assertThat(state.getCharmTier(CharmRune.CHARM_NONE)).isEqualTo(0)
    }

    @Test
    fun `parseRacebyCharm stores and reads the assigned race`() {
        val state = PlayerBestiaryState()

        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(0)
        // set=true returns the local (0) exactly like Canary's Player::parseRacebyCharm.
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND, set = true, newRaceid = 34)).isEqualTo(0)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(34)
    }

    @Test
    fun `bit toggle and unlocked rune bit checks use the charm binary`() {
        val bestiary = IOBestiary()

        assertThat(bestiary.bitToggle(0, wound, on = true)).isEqualTo(1)
        assertThat(bestiary.bitToggle(1, wound, on = false)).isEqualTo(0)
        assertThat(bestiary.hasCharmUnlockedRuneBit(wound, 1)).isTrue()
        assertThat(bestiary.hasCharmUnlockedRuneBit(wound, 2)).isFalse()
    }

    @Test
    fun `used rune bits are decoded most-significant-first`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()
        state.setUsedRunesBit(0b101) // CHARM_WOUND (0) and CHARM_POISON (2)

        assertThat(bestiary.getCharmUsedRuneBitAll(state))
            .containsExactly(CharmRune.CHARM_POISON, CharmRune.CHARM_WOUND)
    }

    @Test
    fun `getBestiaryCharm finds registered charms and force-creates unknown ones`() {
        val bestiary = IOBestiary(mutableListOf(wound))

        assertThat(bestiary.getBestiaryCharm(CharmRune.CHARM_WOUND)).isSameAs(wound)
        assertThat(bestiary.getBestiaryCharm(CharmRune.CHARM_ZAP)).isNull()

        val forced = bestiary.getBestiaryCharm(CharmRune.CHARM_ZAP, force = true)
        assertThat(forced).isNotNull
        assertThat(forced!!.id).isEqualTo(CharmRune.CHARM_ZAP)
        assertThat(forced.binary).isEqualTo(1 shl CharmRune.CHARM_ZAP.value)
        assertThat(bestiary.getBestiaryCharm(CharmRune.CHARM_ZAP)).isSameAs(forced)
    }

    @Test
    fun `assigning a charm rune creature sets race and used bit then resets`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary(mutableListOf(wound))

        bestiary.setCharmRuneCreature(state, wound, 34)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(34)
        assertThat(state.getUsedRunesBit()).isEqualTo(1)

        val onTarget = bestiary.getCharmFromTarget(state, dragon, CharmCategory.CHARM_MAJOR)
        assertThat(onTarget.major).isEqualTo(CharmRune.CHARM_WOUND)
        assertThat(onTarget.minor).isEqualTo(CharmRune.CHARM_NONE)

        bestiary.resetCharmRuneCreature(state, wound)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(0)
        assertThat(state.getUsedRunesBit()).isEqualTo(0)

        state.setCharmTier(CharmRune.CHARM_WOUND, 2)
        bestiary.resetAllCharmRuneCreatures(state)
        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(0)
    }
}
