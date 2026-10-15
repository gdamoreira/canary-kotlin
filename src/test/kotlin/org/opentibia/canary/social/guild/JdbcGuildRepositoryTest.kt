package org.opentibia.canary.social.guild

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.map.Position

/**
 * H2 (Flyway V1-V9) round-trip tests for [JdbcGuildRepository], transcribed
 * from Canary's guild persistence:
 * `IOGuild::loadGuild` / `IOGuild::saveGuild` / `IOGuild::getGuildIdByName` /
 * `IOGuild::getWarList` (src/io/ioguild.cpp:15-77) and the `guilds`,
 * `guild_ranks`, `guild_wars` and `guild_invites` tables (schema.sql:338-455).
 *
 * Canary's `IOGuild::saveGuild` writes only the guild balance; ranks, MOTD and
 * membership come from the website (schema.sql:423-455).
 */
class JdbcGuildRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcGuildRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_guild_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(9)
        repository = JdbcGuildRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun seedGuild(id: Long, name: String, balance: Long = 0, ownerId: Long = 1, motd: String = "") {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO guilds (id, name, balance, ownerid, creationdata, motd) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.setLong(3, balance)
                stmt.setLong(4, ownerId)
                stmt.setLong(5, 1_700_000_000)
                stmt.setString(6, motd)
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

    private fun storedGuildRow(id: Long): Pair<Long, String> =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT balance, motd FROM guilds WHERE id = ?").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).isTrue()
                    rs.getLong("balance") to rs.getString("motd")
                }
            }
        }

    @Test
    fun `loads a guild with its balance and ranks`() {
        seedGuild(10, "Red Rose", balance = 5000, ownerId = 1)
        seedRank(100, 10, "The Leader", 3)
        seedRank(101, 10, "Vice-Leader", 2)
        seedRank(102, 10, "Member", 1)

        val guild = repository.loadGuild(10)

        assertThat(guild).isNotNull
        assertThat(guild?.id).isEqualTo(10)
        assertThat(guild?.name).isEqualTo("Red Rose")
        assertThat(guild?.getBankBalance()).isEqualTo(5000)
        assertThat(guild?.getRanks()?.map { it.name })
            .containsExactlyInAnyOrder("The Leader", "Vice-Leader", "Member")
        assertThat(guild?.getRankByLevel(1)?.name).isEqualTo("Member")
    }

    @Test
    fun `loadGuild returns null for an unknown guild`() {
        assertThat(repository.loadGuild(404)).isNull()
    }

    @Test
    fun `loadGuild installs the removal seam on the materialized guild`() {
        seedGuild(10, "Red Rose", ownerId = 1)
        val removed = mutableListOf<Long>()
        val seamRepository = JdbcGuildRepository(dataSource, removeGuild = { removed.add(it) })
        val guild = seamRepository.loadGuild(10)!!
        val member = Player(1, "Wolfie", 100, 100, position = Position(100, 100, 7))
        guild.addMember(member)

        guild.removeMember(member)

        assertThat(removed).containsExactly(10L)
    }

    @Test
    fun `saveGuild persists only the balance like canary`() {
        seedGuild(10, "Red Rose", balance = 100, ownerId = 1, motd = "old motd")
        val guild = repository.loadGuild(10)!!
        guild.setMotd("new motd")
        guild.setBankBalance(999)

        repository.saveGuild(guild)

        assertThat(storedGuildRow(10)).isEqualTo(999L to "old motd")
        assertThat(repository.loadGuild(10)!!.getMotd()).isEmpty()
    }

    @Test
    fun `getGuildIdByName returns the id or zero`() {
        seedGuild(10, "Red Rose", ownerId = 1)

        assertThat(repository.getGuildIdByName("Red Rose")).isEqualTo(10)
        assertThat(repository.getGuildIdByName("Unknown")).isZero()
    }

    @Test
    fun `getWarList returns only the active opponents`() {
        seedGuild(10, "Red Rose", ownerId = 1)
        seedWar(10, 20, GuildWarStatus.ACTIVE)
        seedWar(30, 10, GuildWarStatus.ACTIVE)
        seedWar(10, 40, 2)
        seedWar(10, 50, GuildWarStatus.ENDED)

        assertThat(repository.getWarList(10)).containsExactlyInAnyOrder(20L, 30L)
    }

    @Test
    fun `getWarList returns empty without active wars`() {
        seedGuild(10, "Red Rose", ownerId = 1)

        assertThat(repository.getWarList(10)).isEmpty()
    }

    @Test
    fun `guild invites round trip and reject duplicates`() {
        seedGuild(10, "Red Rose", ownerId = 1)

        assertThat(repository.addGuildInvite(1, 10, 1000)).isTrue()
        assertThat(repository.addGuildInvite(1, 10, 2000)).isFalse()
        assertThat(repository.addGuildInvite(2, 10, 3000)).isTrue()

        assertThat(repository.getGuildInvites(1))
            .containsExactly(GuildInvite(playerId = 1, guildId = 10, date = 1000))
        assertThat(repository.getGuildInvites(2))
            .containsExactly(GuildInvite(playerId = 2, guildId = 10, date = 3000))
    }

    @Test
    fun `removeGuildInvite drops only the matching pair`() {
        seedGuild(10, "Red Rose", ownerId = 1)
        repository.addGuildInvite(1, 10, 1000)
        repository.addGuildInvite(2, 10, 3000)

        repository.removeGuildInvite(1, 10)

        assertThat(repository.getGuildInvites(1)).isEmpty()
        assertThat(repository.getGuildInvites(2)).hasSize(1)
        repository.removeGuildInvite(1, 10)
        assertThat(repository.getGuildInvites(2)).hasSize(1)
    }
}
