package org.opentibia.canary.game

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.SqlDialect
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap

/**
 * Runtime SQL portability of `Game::generateHighscoreQuery` (game.cpp:9890):
 * MariaDB keeps upstream Canary's `@curRank`/`IF()` user-variable form, while
 * PostgreSQL (and H2 in PostgreSQL mode) gets the equivalent
 * `DENSE_RANK()`/`ROW_NUMBER()` window-function form selected through
 * [SqlDialect]. The window form executes on the H2 test engine, so the H2
 * assertions cover the exact string PostgreSQL receives.
 */
class GameHighscoreDialectTest {

    private data class RankedPlayer(
        val id: Long,
        val name: String,
        val level: Int,
        val vocation: Int,
        val experience: Long,
        val group: Int,
    )

    private lateinit var dataSource: HikariDataSource

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("highscore_dialect_${System.nanoTime()}")
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE players (id BIGINT PRIMARY KEY, name VARCHAR(255), level INT, " +
                        "vocation INT, experience BIGINT, group_id INT)",
                )
                for (player in PLAYERS) {
                    statement.execute(
                        "INSERT INTO players VALUES (${player.id}, '${player.name}', ${player.level}, " +
                            "${player.vocation}, ${player.experience}, ${player.group})",
                    )
                }
            }
        }
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newGame(vocations: Vocations? = null): Game =
        Game(GameMap(), GameDispatcher(), vocations = vocations)

    private fun rows(sql: String): List<Map<String, Any?>> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                val labels = (1..result.metaData.columnCount).map { result.metaData.getColumnLabel(it) }
                buildList {
                    while (result.next()) {
                        add(labels.associateWith { label -> result.getObject(label) })
                    }
                }
            }
        }
    }

    private fun number(row: Map<String, Any?>, column: String): Long = (row.getValue(column) as Number).toLong()

    // ----- dialect selection -----

    @Test
    fun `MariaDB keeps the upstream user-variable form`() {
        val game = newGame()

        val entries = game.generateHighscoreQuery("experience", 1, 5, 0xFFFFFFFFL, dialect = SqlDialect.MARIADB)
        assertThat(entries)
            .contains("@curRank := IF(@prevRank = `experience`")
            .contains("(@row := @row + 1) AS `rn`")
            .contains("(SELECT @curRank := 0, @prevRank := NULL, @row := 0, @ourRow := 0) `r`")
            .doesNotContain("DENSE_RANK()")

        val ourRank = game.generateHighscoreQuery("experience", 0, 5, 0xFFFFFFFFL, 10, SqlDialect.MARIADB)
        assertThat(ourRank)
            .contains("(@ourRow DIV 5) + 1 AS `page` FROM (")
            .contains(", @ourRow := IF(`id` = 10, @row - 1, @ourRow) AS `rw`")
            .contains("`rn` > ((@ourRow DIV 5) * 5)")
    }

    @Test
    fun `PostgreSQL and H2 share the window-function form`() {
        val game = newGame()

        val postgres = game.generateHighscoreQuery("experience", 1, 5, 0xFFFFFFFFL, dialect = SqlDialect.POSTGRESQL)
        val h2 = game.generateHighscoreQuery("experience", 1, 5, 0xFFFFFFFFL, dialect = SqlDialect.H2)

        assertThat(h2).isEqualTo(postgres)
        assertThat(postgres)
            .contains("DENSE_RANK() OVER (ORDER BY experience DESC) AS rank")
            .contains("ROW_NUMBER() OVER (ORDER BY experience DESC) AS rn")
            .contains("FROM players WHERE group_id < 4")
            .doesNotContain("@curRank")
            .doesNotContain("@row")
            .doesNotContain("@ourRow")
            .doesNotContain("IF(")
            .doesNotContain("`")
    }

    @Test
    fun `the window form filters the vocation inside the ranked query`() {
        val game = newGame(knightVocations())

        val query = game.generateHighscoreQuery("experience", 1, 5, 1, dialect = SqlDialect.POSTGRESQL)

        assertThat(query)
            .contains("(SELECT COUNT(*) FROM players WHERE group_id < 4 AND (vocation = 1 OR vocation = 2)) AS total")
            .contains("FROM players WHERE group_id < 4 AND (vocation = 1 OR vocation = 2)")
    }

    // ----- H2 execution of the shared PostgreSQL/H2 form -----

    @Test
    fun `the window form ranks ties densely and paginates the entries`() {
        val game = newGame()

        val result = rows(game.generateHighscoreQuery("experience", 1, 5, 0xFFFFFFFFL, dialect = SqlDialect.H2))

        assertThat(result.map { number(it, "id") }).containsExactly(10, 11, 12, 13, 14)
        assertThat(result.map { number(it, "rank") }).containsExactly(1, 2, 2, 3, 4)
        assertThat(result.map { number(it, "entries") }).containsExactly(1, 2, 3, 4, 5)
        assertThat(result.map { number(it, "total") }).containsOnly(11L)
        assertThat(result.map { number(it, "page") }).containsOnly(1L)
    }

    @Test
    fun `the window form answers the our-rank window with the player row`() {
        val game = newGame()

        val result = rows(
            game.generateHighscoreQuery("experience", 0, 5, 0xFFFFFFFFL, 16, SqlDialect.H2),
        )

        assertThat(result.map { number(it, "entries") }).containsExactly(6, 7, 8, 9, 10)
        assertThat(result.map { number(it, "page") }).containsOnly(2L)
        val ours = result.single { number(it, "id") == 16L }
        assertThat(number(ours, "entries")).isEqualTo(7)
        assertThat(number(ours, "rank")).isEqualTo(6)
    }

    @Test
    fun `the window form falls back to the first page for a player outside the ranking`() {
        val game = newGame()

        val result = rows(
            game.generateHighscoreQuery("experience", 0, 5, 0xFFFFFFFFL, 999, SqlDialect.H2),
        )

        assertThat(result.map { number(it, "entries") }).containsExactly(1, 2, 3, 4, 5)
        assertThat(result.map { number(it, "page") }).containsOnly(1L)
    }

    @Test
    fun `the window form applies the vocation filter to the executed ranking`() {
        val game = newGame(knightVocations())

        val result = rows(game.generateHighscoreQuery("experience", 1, 5, 1, dialect = SqlDialect.H2))

        assertThat(result.map { number(it, "id") }).containsExactly(10, 11, 12, 13, 14)
        assertThat(result.map { number(it, "vocation") }).containsOnly(1L)
        assertThat(result.map { number(it, "total") }).containsOnly(9L)
    }

    private fun knightVocations(): Vocations = Vocations().also {
        it.loadFromXml(
            "<vocations>" +
                "<vocation id=\"1\" name=\"Knight\" clientid=\"1\" fromvoc=\"1\"/>" +
                "<vocation id=\"2\" name=\"Elite Knight\" clientid=\"1\" fromvoc=\"1\"/>" +
                "</vocations>",
        )
    }

    private companion object {
        /**
         * 11 ranked players (`group_id < 4`), one staff member filtered out,
         * ids 11/12 tied so `DENSE_RANK` can be observed, and ids 19/21 on a
         * vocation outside the loaded family so the filter is discriminating.
         */
        val PLAYERS = listOf(
            RankedPlayer(10L, "Alpha", 100, 1, 1000L, 1),
            RankedPlayer(11L, "Beta", 90, 1, 900L, 1),
            RankedPlayer(12L, "Gamma", 80, 1, 900L, 1),
            RankedPlayer(13L, "Delta", 70, 1, 800L, 1),
            RankedPlayer(14L, "Epsilon", 60, 1, 700L, 1),
            RankedPlayer(15L, "Zeta", 50, 1, 600L, 1),
            RankedPlayer(16L, "Eta", 40, 1, 500L, 1),
            RankedPlayer(17L, "Theta", 30, 2, 400L, 1),
            RankedPlayer(18L, "Iota", 20, 2, 300L, 1),
            RankedPlayer(19L, "Kappa", 10, 3, 200L, 1),
            RankedPlayer(20L, "Staff", 200, 1, 100L, 4),
            RankedPlayer(21L, "Lambda", 5, 3, 50L, 1),
        )
    }
}
