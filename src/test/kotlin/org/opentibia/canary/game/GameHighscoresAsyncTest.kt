package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.components.CyclopediaDatabaseTasks
import org.opentibia.canary.creatures.players.components.CyclopediaQueryResult
import org.opentibia.canary.creatures.players.components.PlayerAsyncTask
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.HighscoresWindowSerializer

/**
 * Transcript tests for the async highscore family:
 * `Game::playerHighscores` (game.cpp:10124), `Game::getSkillNameById`
 * (game.cpp:10157), `Game::generateHighscoreQuery` (game.cpp:9890),
 * `Game::generateVocationConditionHighscore` (game.cpp:9968),
 * `Game::processHighscoreResults` (game.cpp:9986),
 * `Game::generateHighscoreOrGetCachedQueryForEntries` (game.cpp:10084) and
 * `Game::generateHighscoreOrGetCachedQueryForOurRank` (game.cpp:10104).
 */
class GameHighscoresAsyncTest {

    private data class SentHighscores(
        val player: Player,
        val characters: List<HighscoresWindowSerializer.HighscoreCharacterEntry>,
        val category: Int,
        val vocation: Long,
        val page: Int,
        val pages: Int,
        val updateTimer: Long,
    )

    private class FakeResult(private val rows: List<Map<String, Any>>) : CyclopediaQueryResult {
        private var index = 0

        override fun getNumber(name: String): Long = (rows[index][name] as? Number)?.toLong() ?: 0L

        override fun getString(name: String): String = rows[index][name] as? String ?: ""

        override fun countResults(): Int = rows.size

        override fun next(): Boolean {
            if (index + 1 >= rows.size) {
                return false
            }
            index++
            return true
        }
    }

    private val center = Position(1000, 1000, 7)
    private val storedQueries = mutableListOf<Pair<String, (CyclopediaQueryResult?, Boolean) -> Unit>>()
    private val sentHighscores = mutableListOf<SentHighscores>()
    private val noDataPlayers = mutableListOf<Player>()

    private fun newGame(vocations: Vocations? = null): Game {
        val game = Game(
            GameMap(),
            GameDispatcher(),
            vocations = vocations,
            databaseTasks = object : CyclopediaDatabaseTasks {
                override fun store(query: String, callback: (CyclopediaQueryResult?, Boolean) -> Unit) {
                    storedQueries.add(query to callback)
                }
            },
        )
        game.sendHighscoresNoData = { player -> noDataPlayers.add(player) }
        game.sendHighscores = { player, characters, category, vocation, page, pages, updateTimer ->
            sentHighscores.add(SentHighscores(player, characters, category, vocation, page, pages, updateTimer))
        }
        return game
    }

    private fun player(id: Long = 10, name: String = "Knight"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center)

    private fun rowsResult(): FakeResult = FakeResult(
        listOf(
            mapOf(
                "page" to 1L,
                "total" to 10L,
                "vocation" to 1L,
                "name" to "Alpha",
                "points" to 12345L,
                "id" to 55L,
                "rank" to 1L,
                "level" to 100L,
            ),
        ),
    )

    // ----- playerHighscores -----

    @Test
    fun `playerHighscores drops a request with an async task in flight`() {
        val game = newGame()
        val player = player()
        player.addAsyncOngoingTask(PlayerAsyncTask.Highscore)

        game.playerHighscores(player, HighscoreType_t.HIGHSCORE_GETENTRIES, 0, 0xFFFFFFFFL, "Canary", 1, 5)

        assertThat(storedQueries).isEmpty()
    }

    @Test
    fun `playerHighscores sends no-data when entriesPerPage is zero`() {
        val game = newGame()
        val player = player()

        game.playerHighscores(player, HighscoreType_t.HIGHSCORE_GETENTRIES, 0, 0xFFFFFFFFL, "Canary", 1, 0)

        assertThat(noDataPlayers).containsExactly(player)
        assertThat(storedQueries).isEmpty()
    }

    @Test
    fun `playerHighscores stores the generated entries query and marks the async task`() {
        val game = newGame()
        val player = player()

        game.playerHighscores(player, HighscoreType_t.HIGHSCORE_GETENTRIES, 0, 0xFFFFFFFFL, "Canary", 1, 5)

        assertThat(storedQueries).hasSize(1)
        assertThat(storedQueries.single().first).contains("`experience` AS `points`")
        assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.Highscore)).isTrue()
    }

    @Test
    fun `playerHighscores processes the result rows into the highscores frame`() {
        val game = newGame()
        val player = player()
        game.addPlayer(player)
        game.playerHighscores(player, HighscoreType_t.HIGHSCORE_GETENTRIES, 0, 0xFFFFFFFFL, "Canary", 1, 5)

        storedQueries.single().second(rowsResult(), false)

        assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.Highscore)).isFalse()
        assertThat(sentHighscores).hasSize(1)
        val sent = sentHighscores.single()
        assertThat(sent.player).isSameAs(player)
        assertThat(sent.category).isZero()
        assertThat(sent.vocation).isEqualTo(0xFFFFFFFFL)
        assertThat(sent.page).isEqualTo(1)
        assertThat(sent.pages).isEqualTo(2)
        assertThat(sent.characters).hasSize(1)
        assertThat(sent.characters.single().name).isEqualTo("Alpha")
        assertThat(sent.characters.single().points).isEqualTo(12345)
        assertThat(sent.characters.single().rank).isEqualTo(1)
        assertThat(sent.characters.single().level).isEqualTo(100)
        assertThat(sent.characters.single().vocation).isZero()
    }

    @Test
    fun `playerHighscores uses the cached characters on a cache hit`() {
        val game = newGame()
        val player = player()
        game.addPlayer(player)
        game.playerHighscores(player, HighscoreType_t.HIGHSCORE_GETENTRIES, 0, 0xFFFFFFFFL, "Canary", 1, 5)
        storedQueries.single().second(rowsResult(), false)

        game.playerHighscores(player, HighscoreType_t.HIGHSCORE_GETENTRIES, 0, 0xFFFFFFFFL, "Canary", 1, 5)
        val secondResult = FakeResult(
            listOf(
                mapOf(
                    "page" to 1L,
                    "total" to 10L,
                    "vocation" to 1L,
                    "name" to "Beta",
                    "points" to 1L,
                    "id" to 56L,
                    "rank" to 2L,
                    "level" to 2L,
                ),
            ),
        )
        storedQueries[1].second(secondResult, false)

        assertThat(sentHighscores).hasSize(2)
        assertThat(sentHighscores[1].characters.single().name).isEqualTo("Alpha")
    }

    @Test
    fun `playerHighscores sends no-data when the query result is null`() {
        val game = newGame()
        val player = player()
        game.addPlayer(player)
        game.playerHighscores(player, HighscoreType_t.HIGHSCORE_GETENTRIES, 0, 0xFFFFFFFFL, "Canary", 1, 5)

        storedQueries.single().second(null, false)

        assertThat(noDataPlayers).containsExactly(player)
        assertThat(sentHighscores).isEmpty()
    }

    @Test
    fun `playerHighscores resolves the our-rank query with the player guid`() {
        val game = newGame()
        val player = player(id = 77)

        game.playerHighscores(player, HighscoreType_t.HIGHSCORE_OURRANK, 0, 0xFFFFFFFFL, "Canary", 0, 5)

        assertThat(storedQueries).hasSize(1)
        assertThat(storedQueries.single().first).contains("@ourRow := IF(`id` = 77, @row - 1, @ourRow)")
    }

    // ----- getSkillNameById -----

    @Test
    fun `getSkillNameById maps categories and defaults unknown to experience`() {
        val game = newGame()

        assertThat(game.getSkillNameById(1).name).isEqualTo("skill_fist")
        assertThat(game.getSkillNameById(8).name).isEqualTo("maglevel")
        assertThat(game.getSkillNameById(14).name).isEqualTo("boss_points")
        val fallback = game.getSkillNameById(200)
        assertThat(fallback.name).isEqualTo("experience")
        assertThat(fallback.category).isZero()
    }

    // ----- generateHighscoreQuery -----

    @Test
    fun `generateHighscoreQuery builds the entries query`() {
        val game = newGame()

        val query = game.generateHighscoreQuery("experience", 1, 5, 0xFFFFFFFFL)

        assertThat(query).isEqualTo(
            "SELECT `id`, `name`, `level`, `vocation`, `points`, `rank`, `rn` AS `entries`, " +
                "(SELECT COUNT(*) FROM `players` WHERE `group_id` < 4) AS `total`, " +
                "1 AS `page` FROM (" +
                "SELECT `id`, `name`, `level`, `vocation`, `experience` AS `points`, " +
                "@curRank := IF(@prevRank = `experience`, @curRank, IF(@prevRank := `experience`, @curRank + 1, @curRank + 1)) AS `rank`, " +
                "(@row := @row + 1) AS `rn`" +
                " FROM (SELECT `id`, `name`, `level`, `vocation`, `experience` FROM `players` `p`, " +
                "(SELECT @curRank := 0, @prevRank := NULL, @row := 0, @ourRow := 0) `r` " +
                "WHERE `group_id` < 4 ORDER BY `experience` DESC) `t`" +
                ") `T` WHERE `rn` > 0 AND `rn` <= 5",
        )
    }

    @Test
    fun `generateHighscoreQuery builds the our-rank query with a vocation condition`() {
        val game = newGame()
        val vocations = Vocations()
        vocations.loadFromXml(
            "<vocations><vocation id=\"1\" name=\"Knight\" clientid=\"1\" fromvoc=\"1\"/></vocations>",
        )
        val gameWithVocations = newGame(vocations)
        assertThat(game.generateHighscoreQuery("experience", 0, 5, 1, 10)).isNotEmpty()

        val query = gameWithVocations.generateHighscoreQuery("experience", 0, 5, 1, 10)

        assertThat(query).contains("(@ourRow DIV 5) + 1 AS `page` FROM (")
        assertThat(query).contains(", @ourRow := IF(`id` = 10, @row - 1, @ourRow) AS `rw`")
        assertThat(query).contains("(SELECT COUNT(*) FROM `players` WHERE `group_id` < 4 AND (`vocation` = 1)) AS `total`")
        assertThat(query).contains("ORDER BY `experience` DESC) `t`(`vocation` = 1)) `T` WHERE ")
        assertThat(query).contains("`rn` > ((@ourRow DIV 5) * 5) AND `rn` <= (((@ourRow DIV 5) * 5) + 5)")
    }

    @Test
    fun `generateHighscoreQuery returns empty for an empty category or page size`() {
        val game = newGame()

        assertThat(game.generateHighscoreQuery("", 1, 5, 0xFFFFFFFFL)).isEmpty()
        assertThat(game.generateHighscoreQuery("experience", 1, 0, 0xFFFFFFFFL)).isEmpty()
    }

    // ----- generateVocationConditionHighscore -----

    @Test
    fun `generateVocationConditionHighscore joins the derived vocations`() {
        val vocations = Vocations()
        vocations.loadFromXml(
            "<vocations>" +
                "<vocation id=\"1\" name=\"Knight\" clientid=\"1\" fromvoc=\"1\"/>" +
                "<vocation id=\"2\" name=\"Elite Knight\" clientid=\"1\" fromvoc=\"1\"/>" +
                "<vocation id=\"3\" name=\"Druid\" clientid=\"2\" fromvoc=\"3\"/>" +
                "</vocations>",
        )
        val game = newGame(vocations)

        val condition = game.generateVocationConditionHighscore(1, " AND ")

        assertThat(condition).isEqualTo(" AND (`vocation` = 1 OR `vocation` = 2)")
    }
}
