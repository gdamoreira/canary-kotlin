package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection

class JdbcRepositoriesTest {

    private lateinit var dbFile: File
    private lateinit var dataSource: SQLiteDataSource
    private lateinit var accountRepo: JdbcAccountRepository
    private lateinit var playerRepo: JdbcPlayerRepository

    @BeforeEach
    fun setUp() {
        dbFile = File.createTempFile("canary_test_", ".db")
        dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${dbFile.absolutePath}"
        }

        // Initialize SQLite test schema
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE accounts (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        password TEXT NOT NULL,
                        premdays INTEGER NOT NULL DEFAULT 0
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE players (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        account_id INTEGER NOT NULL,
                        healthmax INTEGER NOT NULL DEFAULT 150,
                        health INTEGER NOT NULL DEFAULT 150,
                        manamax INTEGER NOT NULL DEFAULT 50,
                        mana INTEGER NOT NULL DEFAULT 50,
                        cap REAL NOT NULL DEFAULT 400.0,
                        speed INTEGER NOT NULL DEFAULT 220,
                        level INTEGER NOT NULL DEFAULT 1,
                        experience INTEGER NOT NULL DEFAULT 0,
                        posx INTEGER NOT NULL DEFAULT 1000,
                        posy INTEGER NOT NULL DEFAULT 1000,
                        posz INTEGER NOT NULL DEFAULT 7,
                        deletion INTEGER NOT NULL DEFAULT 0
                    );
                    """.trimIndent()
                )
                // Seed test data
                stmt.execute("INSERT INTO accounts (id, name, password, premdays) VALUES (1, '1', '1', 90);")
                stmt.execute("INSERT INTO players (id, name, account_id, health, level, posx, posy, posz) VALUES (1, 'Knight Hero', 1, 150, 10, 1000, 1000, 7);")
            }
        }

        accountRepo = JdbcAccountRepository(dataSource)
        playerRepo = JdbcPlayerRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `authenticates account and loads associated character entries`() {
        val account = accountRepo.authenticate("1", "1")
        assertThat(account).isNotNull
        assertThat(account?.id).isEqualTo(1L)
        assertThat(account?.name).isEqualTo("1")
        assertThat(account?.characters).hasSize(1)
        assertThat(account?.characters?.first()?.name).isEqualTo("Knight Hero")
    }

    @Test
    fun `loads and updates player data in database`() {
        val player = playerRepo.loadByName("Knight Hero")
        assertThat(player).isNotNull
        assertThat(player?.name).isEqualTo("Knight Hero")
        assertThat(player?.level).isEqualTo(10)

        // Modify and save
        player!!.drainHealth(50)
        player.position = Position(1005, 1005, 7)
        playerRepo.save(player)

        val reloaded = playerRepo.loadByName("Knight Hero")
        assertThat(reloaded?.health).isEqualTo(100)
        assertThat(reloaded?.position).isEqualTo(Position(1005, 1005, 7))
    }
}
