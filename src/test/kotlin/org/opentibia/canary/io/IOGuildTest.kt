package org.opentibia.canary.io

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.social.guild.GuildWarStatus
import org.opentibia.canary.social.guild.JdbcGuildRepository

/**
 * H2 (Flyway V1-V13) round-trip tests for [IOGuild], transcribed from Canary's
 * guild persistence:
 * `IOGuild::loadGuild` / `IOGuild::saveGuild` / `IOGuild::getGuildIdByName` /
 * `IOGuild::getWarList` (src/io/ioguild.cpp:15-77) and the `guilds`,
 * `guild_ranks` and `guild_wars` tables (schema.sql:338-425).
 *
 * The SQL lives in [JdbcGuildRepository]; these tests exercise the
 * `IOGuild`-shaped surface end to end against the Flyway schema.
 */
class IOGuildTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var ioGuild: IOGuild

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_io_guild_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        ioGuild = IOGuild(JdbcGuildRepository(dataSource))
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun seedGuild(id: Long, name: String, balance: Long = 0, ownerId: Long = 1) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO guilds (id, name, balance, ownerid, creationdata) VALUES (?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.setLong(3, balance)
                stmt.setLong(4, ownerId)
                stmt.setLong(5, 1_700_000_000)
                stmt.executeUpdate()
            }
        }
    }

    private fun seedRank(id: Long, guildId: Long, name: String, level: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO guild_ranks (id, guild_id, name, level) VALUES (?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setLong(2, guildId)
                stmt.setString(3, name)
                stmt.setInt(4, level)
                stmt.executeUpdate()
            }
        }
    }

    private fun seedWar(guild1: Long, guild2: Long, status: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO guild_wars (guild1, guild2, name1, name2, status) VALUES (?, ?, '', '', ?)",
            ).use { stmt ->
                stmt.setLong(1, guild1)
                stmt.setLong(2, guild2)
                stmt.setInt(3, status)
                stmt.executeUpdate()
            }
        }
    }

    private fun storedBalance(guildId: Long): Long = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT balance FROM guilds WHERE id = ?").use { stmt ->
            stmt.setLong(1, guildId)
            stmt.executeQuery().use { rs ->
                assertThat(rs.next()).isTrue()
                rs.getLong("balance")
            }
        }
    }

    @Test
    fun `loads a guild with its balance and ranks`() {
        seedGuild(10, "Red Rose", balance = 5000, ownerId = 1)
        seedRank(100, 10, "The Leader", 3)
        seedRank(101, 10, "Vice-Leader", 2)
        seedRank(102, 10, "Member", 1)

        val guild = ioGuild.loadGuild(10)

        assertThat(guild).isNotNull
        assertThat(guild?.name).isEqualTo("Red Rose")
        assertThat(guild?.getBankBalance()).isEqualTo(5000)
        assertThat(guild?.getRanks()?.map { it.name })
            .containsExactlyInAnyOrder("The Leader", "Vice-Leader", "Member")
        assertThat(guild?.getRankByLevel(1)?.name).isEqualTo("Member")
    }

    @Test
    fun `loadGuild returns null for an unknown guild`() {
        assertThat(ioGuild.loadGuild(404)).isNull()
    }

    @Test
    fun `saveGuild updates only the balance like canary`() {
        seedGuild(10, "Red Rose", balance = 100, ownerId = 1)
        val guild = ioGuild.loadGuild(10)!!
        guild.setBankBalance(999)

        ioGuild.saveGuild(guild)

        assertThat(storedBalance(10)).isEqualTo(999)
    }

    @Test
    fun `saveGuild ignores a null guild without throwing`() {
        ioGuild.saveGuild(null)
    }

    @Test
    fun `getGuildIdByName resolves the unique name or returns zero`() {
        seedGuild(10, "Red Rose", ownerId = 1)

        assertThat(ioGuild.getGuildIdByName("Red Rose")).isEqualTo(10)
        assertThat(ioGuild.getGuildIdByName("Unknown")).isZero()
    }

    @Test
    fun `getWarList returns only the active opponents`() {
        seedGuild(10, "Red Rose", ownerId = 1)
        seedWar(10, 20, GuildWarStatus.ACTIVE)
        seedWar(30, 10, GuildWarStatus.ACTIVE)
        seedWar(10, 40, 2)
        seedWar(10, 50, GuildWarStatus.ENDED)

        assertThat(ioGuild.getWarList(10)).containsExactlyInAnyOrder(20L, 30L)
    }
}
