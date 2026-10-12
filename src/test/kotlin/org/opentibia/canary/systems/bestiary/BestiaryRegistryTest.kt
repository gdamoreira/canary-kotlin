package org.opentibia.canary.systems.bestiary

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

/**
 * Parity tests for the monster/bestiary-race registry.
 *
 * Transcribed from:
 *  - `Game::addBestiaryList` / `Game::getBestiaryList` (src/game/game.cpp:9517-9524, game.hpp:459-461)
 *  - `Monsters::getMonsterType` / `getMonsterTypeByRaceId` (src/creatures/monsters/monsters.cpp:363-388)
 *  - `IOBestiary::findRaceByName` (src/io/iobestiary.cpp:250-270)
 *  - `IOBestiary::getBestiaryRaceUnlocked` (src/io/iobestiary.cpp:334-349)
 */
class BestiaryRegistryTest {

    private val rat = MonsterBestiaryInfo(
        raceid = 21,
        name = "Rat",
        bestiaryClass = "Mammal",
        bestiaryRace = BestiaryRace.BESTY_RACE_MAMMAL,
    )
    private val wolf = MonsterBestiaryInfo(
        raceid = 22,
        name = "Wolf",
        bestiaryClass = "Mammal",
        bestiaryRace = BestiaryRace.BESTY_RACE_MAMMAL,
    )
    private val dragon = MonsterBestiaryInfo(
        raceid = 34,
        name = "Dragon",
        bestiaryClass = "Dragon",
        bestiaryRace = BestiaryRace.BESTY_RACE_DRAGON,
    )
    private val demon = MonsterBestiaryInfo(
        raceid = 35,
        name = "Demon",
        bestiaryClass = "Demon",
        bestiaryRace = BestiaryRace.BESTY_RACE_DEMON,
    )
    private val raceZero = MonsterBestiaryInfo(
        raceid = 0,
        name = "Ghost",
        bestiaryClass = "Undead",
        bestiaryRace = BestiaryRace.BESTY_RACE_UNDEAD,
    )

    private val registry = BestiaryRegistry(listOf(rat, dragon, wolf, demon, raceZero))

    @Test
    fun `getBestiaryList mirrors the raceId-sorted name map`() {
        assertThat(registry.getBestiaryList()).containsExactly(
            entry(21, "Rat"),
            entry(22, "Wolf"),
            entry(34, "Dragon"),
            entry(35, "Demon"),
        )
    }

    @Test
    fun `addBestiaryList ignores realigned duplicate raceIds`() {
        val registry = BestiaryRegistry()
        registry.addBestiaryList(21, "Rat")
        registry.addBestiaryList(21, "Rat Realigned")

        assertThat(registry.getBestiaryList()).containsExactly(entry(21, "Rat"))
    }

    @Test
    fun `findRaceByName filters by the bestiary class string`() {
        assertThat(registry.findRaceByName("Mammal")).containsExactly(
            entry(21, "Rat"),
            entry(22, "Wolf"),
        )
        assertThat(registry.findRaceByName("Dragon")).containsExactly(entry(34, "Dragon"))
        assertThat(registry.findRaceByName("NoSuchClass")).isEmpty()
    }

    @Test
    fun `findRaceByName can filter by BestiaryType_t race number`() {
        assertThat(registry.findRaceByName("", onlyString = false, raceNumber = BestiaryRace.BESTY_RACE_MAMMAL))
            .containsExactly(entry(21, "Rat"), entry(22, "Wolf"))
        assertThat(registry.findRaceByName("", onlyString = false, raceNumber = BestiaryRace.BESTY_RACE_DRAGON))
            .containsExactly(entry(34, "Dragon"))
    }

    @Test
    fun `getBestiaryRaceUnlocked counts only killed monsters of the race`() {
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(21, 5)
        state.addBestiaryKillCount(34, 3)

        assertThat(registry.getBestiaryRaceUnlocked(state, BestiaryRace.BESTY_RACE_MAMMAL)).isEqualTo(1)
        assertThat(registry.getBestiaryRaceUnlocked(state, BestiaryRace.BESTY_RACE_DRAGON)).isEqualTo(1)
        assertThat(registry.getBestiaryRaceUnlocked(state, BestiaryRace.BESTY_RACE_DEMON)).isEqualTo(0)
    }

    @Test
    fun `monster lookup is case-insensitive by name and resolves by race id`() {
        assertThat(registry.getMonsterType("DRAGON")).isSameAs(dragon)
        assertThat(registry.getMonsterType("missing")).isNull()
        assertThat(registry.getMonsterTypeByRaceId(34)).isSameAs(dragon)
        assertThat(registry.getMonsterTypeByRaceId(0)).isNull()
    }
}
