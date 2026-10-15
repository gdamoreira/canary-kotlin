package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.utils.Tools

/**
 * The Canary god-account seed (`schema.sql:869-884`): account `god`/`god`
 * (SHA-1 hash, type 6) and the `GOD` player in group 6, added by Flyway `V23`
 * to both migration trees. Also pins the JDBC login chain
 * (`Account::authenticatePassword`, account.cpp:302-313): argon2id, then the
 * legacy SHA-1 comparison (the port additionally accepts the plaintext seed).
 */
class GodAccountSeedTest {

    private lateinit var dataSource: HikariDataSource

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_god_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `V23 seeds the god account and the GOD player`() {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT name, \"password\", email, \"type\" FROM accounts WHERE id = 2"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertThat(result.next()).isTrue()
                    assertThat(result.getString("name")).isEqualTo("god")
                    assertThat(result.getString("password"))
                        .isEqualTo("21298df8a3277357ee55b01df9530b535cf08ec1")
                    assertThat(result.getString("email")).isEqualTo("@god")
                    assertThat(result.getInt("type")).isEqualTo(6)
                }
            }

            connection.prepareStatement(
                "SELECT name, group_id, account_id FROM players WHERE id = 7"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertThat(result.next()).isTrue()
                    assertThat(result.getString("name")).isEqualTo("GOD")
                    assertThat(result.getInt("group_id")).isEqualTo(6)
                    assertThat(result.getLong("account_id")).isEqualTo(2L)
                }
            }
        }
    }

    @Test
    fun `the god account authenticates through the JDBC sha1 chain`() {
        val repository = JdbcAccountRepository(dataSource)

        assertThat(repository.authenticate("god", "god")).isNotNull
        assertThat(repository.authenticate("god", "wrong")).isNull()
        assertThat(Tools.transformToSHA1("god"))
            .isEqualTo("21298df8a3277357ee55b01df9530b535cf08ec1")
    }

    @Test
    fun `the seeded plaintext account still authenticates`() {
        val repository = JdbcAccountRepository(dataSource)

        // V2 seeds account `1` with a plaintext password; the port keeps the
        // plaintext fallback alongside the C++ argon2/sha1 chain.
        assertThat(repository.authenticate("1", "1")).isNotNull
    }
}
