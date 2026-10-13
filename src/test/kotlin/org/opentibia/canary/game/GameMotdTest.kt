package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.Database
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.utils.Tools

/**
 * Transcript tests for `Game::saveMotdNum` (game.cpp:9613), the `motdNum`
 * counter (`Game::incrementMotdNum`, game.hpp:566) and the `getMotdHash` /
 * `getMotdNum` accessors (game.hpp:559-564).
 */
class GameMotdTest {

    private lateinit var dataSource: com.zaxxer.hikari.HikariDataSource
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("motd_${System.nanoTime()}")
        database = Database(dataSource)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE server_config (\"config\" VARCHAR(255) PRIMARY KEY, \"value\" VARCHAR(255))")
                statement.execute("INSERT INTO server_config (\"config\", \"value\") VALUES ('motd_num', '0')")
                statement.execute("INSERT INTO server_config (\"config\", \"value\") VALUES ('motd_hash', '')")
            }
        }
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun configValue(config: String): String? =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT \"value\" FROM server_config WHERE \"config\" = ?").use { statement ->
                statement.setString(1, config)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString(1) else null
                }
            }
        }

    private fun newGame(): Game = Game(GameMap(), GameDispatcher(), database = database)

    @Test
    fun `saveMotdNum writes the counter and the motd hash`() {
        val game = newGame()
        game.serverMotd = "Welcome to Canary"
        game.incrementMotdNum()
        game.incrementMotdNum()

        game.saveMotdNum()

        assertThat(configValue("motd_num")).isEqualTo("2")
        assertThat(configValue("motd_hash")).isEqualTo(Tools.transformToSHA1("Welcome to Canary"))
    }

    @Test
    fun `getMotdNum and getMotdHash expose the loaded state`() {
        val game = newGame()

        assertThat(game.getMotdNum()).isZero()
        assertThat(game.getMotdHash()).isEmpty()

        game.incrementMotdNum()

        assertThat(game.getMotdNum()).isEqualTo(1)
    }
}
