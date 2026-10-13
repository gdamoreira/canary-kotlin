package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap

/**
 * Transcript tests for the `Game` highscore members:
 * `Game::calculateHighscorePages` (game.cpp:10033),
 * `Game::getHighscoreCategoriesName` (game.cpp:12473) and
 * `Game::getHighscoreCategories` (game.cpp:12477).
 */
class GameHighscoreTest {

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    @Test
    fun `calculateHighscorePages rounds up and returns zero for an empty page size`() {
        assertThat(Game.calculateHighscorePages(totalEntries = 0, entriesPerPage = 5)).isZero()
        assertThat(Game.calculateHighscorePages(totalEntries = 1, entriesPerPage = 5)).isEqualTo(1)
        assertThat(Game.calculateHighscorePages(totalEntries = 5, entriesPerPage = 5)).isEqualTo(1)
        assertThat(Game.calculateHighscorePages(totalEntries = 6, entriesPerPage = 5)).isEqualTo(2)
        assertThat(Game.calculateHighscorePages(totalEntries = 1234, entriesPerPage = 0)).isZero()
    }

    @Test
    fun `getHighscoreCategoriesName exposes the Canary category names`() {
        val game = newGame()

        assertThat(game.getHighscoreCategoriesName()).isEqualTo(
            mapOf(
                0 to "Experience Points",
                1 to "Fist Fighting",
                2 to "Club Fighting",
                3 to "Sword Fighting",
                4 to "Axe Fighting",
                5 to "Distance Fighting",
                6 to "Shielding",
                7 to "Fishing",
                8 to "Magic Level",
                9 to "Loyalty Points",
                10 to "Achievement Points",
                11 to "Charm Points",
                12 to "Drome Score",
                13 to "Goshnar's Taint",
                14 to "Boss Points",
            ),
        )
    }

    @Test
    fun `getHighscoreCategories exposes the Canary ordered categories`() {
        val game = newGame()

        assertThat(game.getHighscoreCategories().map { it.id }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8)
        assertThat(game.getHighscoreCategories().map { it.name }).containsExactly(
            "Experience Points",
            "Fist Fighting",
            "Club Fighting",
            "Sword Fighting",
            "Axe Fighting",
            "Distance Fighting",
            "Shielding",
            "Fishing",
            "Magic Level",
        )
    }
}
