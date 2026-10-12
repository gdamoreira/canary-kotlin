package org.opentibia.canary.systems.bestiary

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Exact-number parity tests for the Bestiary kill tracking engine.
 *
 * Threshold/charm-point fixtures mirror the real Canary monster data masks
 * (data-otservbr-global/monster/...), and the tracking behavior is transcribed
 * from src/io/iobestiary.cpp.
 */
class BestiaryTest {

    private val rat = MonsterBestiaryInfo(
        raceid = 21,
        name = "Rat",
        bestiaryFirstUnlock = 10,
        bestiarySecondUnlock = 100,
        bestiaryToUnlock = 250,
        bestiaryCharmsPoints = 5,
        bestiaryStars = 1,
        bestiaryClass = "Mammal",
        bestiaryRace = BestiaryRace.BESTY_RACE_MAMMAL,
    )

    private val dragon = MonsterBestiaryInfo(
        raceid = 34,
        name = "Dragon",
        bestiaryFirstUnlock = 50,
        bestiarySecondUnlock = 500,
        bestiaryToUnlock = 1000,
        bestiaryCharmsPoints = 25,
        bestiaryStars = 3,
        bestiaryClass = "Dragon",
        bestiaryRace = BestiaryRace.BESTY_RACE_DRAGON,
    )

    private val demon = MonsterBestiaryInfo(
        raceid = 35,
        name = "Demon",
        bestiaryFirstUnlock = 100,
        bestiarySecondUnlock = 1000,
        bestiaryToUnlock = 2500,
        bestiaryCharmsPoints = 50,
        bestiaryStars = 4,
        bestiaryClass = "Demon",
        bestiaryRace = BestiaryRace.BESTY_RACE_DEMON,
    )

    @Test
    fun `rat kill stages award 5 charm points only at the final unlock`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()

        // iobestiary.cpp:272-281 -- initial status is 1 (below FirstUnlock).
        assertThat(bestiary.getKillStatus(rat, 0)).isEqualTo(1)

        // First kill emits the initial-stage message; status stays 1.
        bestiary.addBestiaryKill(state, rat, 9)
        assertThat(state.getBestiaryKillCount(21)).isEqualTo(9)
        assertThat(bestiary.getKillStatus(rat, 9)).isEqualTo(1)
        assertThat(state.getCharmPoints()).isEqualTo(0)

        // Reaches FirstUnlock (10) -> status 2, still no charm points.
        bestiary.addBestiaryKill(state, rat, 1)
        assertThat(state.getBestiaryKillCount(21)).isEqualTo(10)
        assertThat(bestiary.getKillStatus(rat, 10)).isEqualTo(2)
        assertThat(state.getCharmPoints()).isEqualTo(0)

        // Reaches SecondUnlock (100) -> status 3.
        bestiary.addBestiaryKill(state, rat, 90)
        assertThat(state.getBestiaryKillCount(21)).isEqualTo(100)
        assertThat(bestiary.getKillStatus(rat, 100)).isEqualTo(3)
        assertThat(state.getCharmPoints()).isEqualTo(0)

        // 249 < toUnlock (250): no award yet.
        bestiary.addBestiaryKill(state, rat, 149)
        assertThat(state.getBestiaryKillCount(21)).isEqualTo(249)
        assertThat(bestiary.getKillStatus(rat, 249)).isEqualTo(3)
        assertThat(state.getCharmPoints()).isEqualTo(0)

        // Crosses toUnlock (250) -> status 4 and awards exactly 5 charm points.
        bestiary.addBestiaryKill(state, rat, 1)
        assertThat(state.getBestiaryKillCount(21)).isEqualTo(250)
        assertThat(bestiary.getKillStatus(rat, 250)).isEqualTo(4)
        assertThat(state.getCharmPoints()).isEqualTo(5)
        assertThat(state.getMaxCharmPoints()).isEqualTo(5)

        // Further kills never award the stage points again.
        bestiary.addBestiaryKill(state, rat, 1)
        assertThat(state.getBestiaryKillCount(21)).isEqualTo(251)
        assertThat(state.getCharmPoints()).isEqualTo(5)

        // Stage-completion detection: initial + 3 unlock transitions.
        assertThat(state.textMessages).hasSize(4)
        assertThat(state.textMessages.last())
            .isEqualTo("You unlocked details for the creature 'Rat'")
        assertThat(state.bestiaryEntriesChanged).containsExactly(21, 21, 21, 21)
    }

    @Test
    fun `dragon thresholds 50 500 1000 award 25 charm points`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()

        bestiary.addBestiaryKill(state, dragon, 49)
        assertThat(bestiary.getKillStatus(dragon, state.getBestiaryKillCount(34))).isEqualTo(1)

        bestiary.addBestiaryKill(state, dragon, 1)
        assertThat(state.getBestiaryKillCount(34)).isEqualTo(50)
        assertThat(bestiary.getKillStatus(dragon, 50)).isEqualTo(2)

        bestiary.addBestiaryKill(state, dragon, 450)
        assertThat(state.getBestiaryKillCount(34)).isEqualTo(500)
        assertThat(bestiary.getKillStatus(dragon, 500)).isEqualTo(3)

        bestiary.addBestiaryKill(state, dragon, 500)
        assertThat(state.getBestiaryKillCount(34)).isEqualTo(1000)
        assertThat(bestiary.getKillStatus(dragon, 1000)).isEqualTo(4)
        assertThat(state.getCharmPoints()).isEqualTo(25)
    }

    @Test
    fun `demon awards exactly 50 charm points on final stage`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()

        bestiary.addBestiaryKill(state, demon, 2500)

        assertThat(state.getBestiaryKillCount(35)).isEqualTo(2500)
        assertThat(bestiary.getKillStatus(demon, 2500)).isEqualTo(4)
        assertThat(state.getCharmPoints()).isEqualTo(50)
        assertThat(state.getMaxCharmPoints()).isEqualTo(50)
    }

    @Test
    fun `addCharmPoints accumulates and raises the maximum only on positive awards`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()

        bestiary.addCharmPoints(state, 50)
        assertThat(state.getCharmPoints()).isEqualTo(50)
        assertThat(state.getMaxCharmPoints()).isEqualTo(50)

        bestiary.addCharmPoints(state, 25)
        assertThat(state.getCharmPoints()).isEqualTo(75)
        assertThat(state.getMaxCharmPoints()).isEqualTo(75)

        bestiary.addCharmPoints(state, 30, negative = true)
        assertThat(state.getCharmPoints()).isEqualTo(45)
        assertThat(state.getMaxCharmPoints()).isEqualTo(75)
    }

    @Test
    fun `addMinorCharmEchoes accumulates and raises the maximum only on positive awards`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()

        bestiary.addMinorCharmEchoes(state, 100)
        assertThat(state.getMinorCharmEchoes()).isEqualTo(100)
        assertThat(state.getMaxMinorCharmEchoes()).isEqualTo(100)

        bestiary.addMinorCharmEchoes(state, 25, negative = true)
        assertThat(state.getMinorCharmEchoes()).isEqualTo(75)
        assertThat(state.getMaxMinorCharmEchoes()).isEqualTo(100)
    }

    @Test
    fun `raceid zero monsters are ignored`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()
        val unknown = MonsterBestiaryInfo(raceid = 0, name = "Unknown")

        bestiary.addBestiaryKill(state, unknown, 500)

        assertThat(state.getBestiaryKillCount(0)).isEqualTo(0)
        assertThat(state.textMessages).isEmpty()
        assertThat(state.getCharmPoints()).isEqualTo(0)
    }

    @Test
    fun `finished and stage two lists use unlock thresholds`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()
        state.addBestiaryKillCount(21, 250) // Rat finished
        state.addBestiaryKillCount(34, 500) // Dragon at stage two only
        state.addBestiaryKillCount(35, 0)

        val list = listOf(rat, dragon, demon)

        assertThat(bestiary.getBestiaryFinished(state, list)).containsExactly(21)
        assertThat(bestiary.getBestiaryStageTwo(state, list)).containsExactly(21, 34)
    }

    @Test
    fun `kill counts by monster id only include monsters with kills`() {
        val state = PlayerBestiaryState()
        val bestiary = IOBestiary()
        state.addBestiaryKillCount(21, 12)
        state.addBestiaryKillCount(34, 0)

        val counts = bestiary.getBestiaryKillCountByMonsterIDs(state, listOf(21, 34, 35))
        assertThat(counts).containsExactly(org.assertj.core.api.Assertions.entry(21, 12))
    }

    @Test
    fun `calculateDifficult mirrors the integer-division bands`() {
        val bestiary = IOBestiary()
        assertThat(bestiary.calculateDifficult(199)).isEqualTo(4)
        assertThat(bestiary.calculateDifficult(1000)).isEqualTo(2)
        assertThat(bestiary.calculateDifficult(5000)).isEqualTo(1)
        assertThat(bestiary.calculateDifficult(25000)).isEqualTo(0)
    }
}
