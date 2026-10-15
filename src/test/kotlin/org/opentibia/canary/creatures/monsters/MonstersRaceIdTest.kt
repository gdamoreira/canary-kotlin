package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for `Monsters::getMonsterTypeByRaceId`
 * (src/creatures/monsters/monsters.cpp:375-388):
 *  - `g_ioBosstiary().getMonsterTypeByBossRaceId(raceId)` is resolved first but
 *    returned only while `isBoss` is set;
 *  - the `g_game().getBestiaryList()` race id then resolves through
 *    `Monsters::getMonsterType`, and an unknown race id yields `nullptr`.
 *
 * The globals are constructor-injected seams (AGENTS.md 1.1):
 * [Monsters] takes `bestiaryList` for `Game::getBestiaryList` and
 * `bossTypeByRaceId` for the bosstiary lookup.
 */
class MonstersRaceIdTest {

    private fun monstersWith(
        bestiaryList: Map<Int, String>,
        bossTypeByRaceId: (Int) -> MonsterType? = { null },
    ): Monsters = Monsters(bestiaryList = { bestiaryList }, bossTypeByRaceId = bossTypeByRaceId)

    @Test
    fun `getMonsterTypeByRaceId resolves the bestiary race id through the registry`() {
        val rat = MonsterType("Rat")
        val monsters = monstersWith(mapOf(42 to "Rat"))
        monsters.tryAddMonsterType("Rat", rat)

        assertThat(monsters.getMonsterTypeByRaceId(42)).isSameAs(rat)
    }

    @Test
    fun `getMonsterTypeByRaceId returns null for an unknown race id`() {
        val monsters = monstersWith(mapOf(42 to "Rat"))
        monsters.tryAddMonsterType("Rat", MonsterType("Rat"))

        assertThat(monsters.getMonsterTypeByRaceId(99)).isNull()
    }

    @Test
    fun `getMonsterTypeByRaceId returns null when the bestiary name is not registered`() {
        // The C++ `g_monsters().getMonsterType(it->second)` miss yields nullptr
        // (monsters.cpp:386-387).
        val monsters = monstersWith(mapOf(42 to "Rat"))

        assertThat(monsters.getMonsterTypeByRaceId(42)).isNull()
    }

    @Test
    fun `getMonsterTypeByRaceId prefers the boss type while isBoss is set`() {
        val rat = MonsterType("Rat")
        val rotwormQueen = MonsterType("Rotworm Queen")
        val monsters = monstersWith(
            mapOf(42 to "Rat"),
            bossTypeByRaceId = { raceId -> if (raceId == 42) rotwormQueen else null },
        )
        monsters.tryAddMonsterType("Rat", rat)
        monsters.tryAddMonsterType("Rotworm Queen", rotwormQueen)

        assertThat(monsters.getMonsterTypeByRaceId(42, isBoss = true)).isSameAs(rotwormQueen)
    }

    @Test
    fun `getMonsterTypeByRaceId keeps the bestiary lookup for a boss race id when isBoss is false`() {
        // The C++ boss lookup still runs but is only returned for `isBoss`
        // (monsters.cpp:376-379); the bestiary map decides otherwise.
        val rat = MonsterType("Rat")
        val rotwormQueen = MonsterType("Rotworm Queen")
        val monsters = monstersWith(
            mapOf(42 to "Rat"),
            bossTypeByRaceId = { raceId -> if (raceId == 42) rotwormQueen else null },
        )
        monsters.tryAddMonsterType("Rat", rat)
        monsters.tryAddMonsterType("Rotworm Queen", rotwormQueen)

        assertThat(monsters.getMonsterTypeByRaceId(42, isBoss = false)).isSameAs(rat)
    }

    @Test
    fun `getMonsterTypeByRaceId returns null for a boss-only race id when isBoss is false`() {
        val rotwormQueen = MonsterType("Rotworm Queen")
        val monsters = monstersWith(
            emptyMap(),
            bossTypeByRaceId = { raceId -> if (raceId == 30015) rotwormQueen else null },
        )
        monsters.tryAddMonsterType("Rotworm Queen", rotwormQueen)

        assertThat(monsters.getMonsterTypeByRaceId(30015, isBoss = false)).isNull()
        assertThat(monsters.getMonsterTypeByRaceId(30015, isBoss = true)).isSameAs(rotwormQueen)
    }
}
