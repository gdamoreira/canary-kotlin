package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.players.components.PlayerStorageRow
import org.opentibia.canary.io.IOMapSerialize
import org.opentibia.canary.kv.KVSQL
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.house.CyclopediaHouseState
import org.opentibia.canary.map.house.GUEST_LIST
import org.opentibia.canary.map.house.HouseOwnerInfo
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.map.house.SUBOWNER_LIST
import org.opentibia.canary.systems.gamestore.CoinType
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.utility.DockerImageName

/**
 * Wave 73 (MariaDB support) end-to-end integration test against a throwaway
 * `mariadb:11.4` container (testcontainers), exercising the production seams
 * that unit tests can only pin by SQL shape on H2:
 *
 *  1. [DatabaseConnectionFactory.migrate] resolves the live product name to
 *     `classpath:db/migration/mariadb` and executes the full MariaDB tree
 *     (V1-V22 with V15 absent in both dialect trees, mirroring upstream
 *     Canary's MySQL schema).
 *  2. [SqlDialect.from] detects MARIADB from the connection metadata, so the
 *     backticked identifier and upsert shapes of [KVSQL], [IOMapSerialize] and
 *     the JDBC repositories run as designed.
 *  3. [KVSQL] round-trips through the `timestamp`/`value` keyword columns
 *     (src/kv/kv_sql.cpp:21-108).
 *  4. The `houses`/`house_lists` upserts of
 *     `IOMapSerialize::SaveHouseInfoGuard` (src/io/iomapserialize.cpp:403-468)
 *     execute as `INSERT ... ON DUPLICATE KEY UPDATE` (`DBInsert::execute`,
 *     src/database/database.cpp:453-481): a re-save updates in place.
 *  5. [JdbcAccountRepository] and [JdbcPlayerStorageRepository] round-trip
 *     (`AccountRepositoryDB`, src/account/account_repository_db.cpp;
 *     `DbPlayerStorageRepository`, src/io/player_storage_repository_db.cpp:25-57).
 *
 * Docker is not available in every local environment: the container is started
 * in `@BeforeAll` behind an assumption, so the class is reported as SKIPPED
 * without Docker and executes on Docker-enabled CI runners.
 */
class MariaDbDialectIntegrationTest {

    companion object {
        private const val MARIADB_PORT = 3306
        private const val EXPECTED_MIGRATIONS = 21

        private val dockerAvailable: Boolean = try {
            DockerClientFactory.instance().isDockerAvailable.also { println("DIAG docker available: $it") }
        } catch (e: Exception) {
            println("DIAG docker check failed: $e")
            false
        }

        private lateinit var container: MariaDBContainer<Nothing>
        private lateinit var dataSource: HikariDataSource
        private var appliedMigrations = 0

        @JvmStatic
        @BeforeAll
        fun startMariaDb() {
            assumeTrue(
                dockerAvailable,
                "Docker is unavailable; the MariaDB integration test runs on Docker-enabled CI only",
            )

            val mariaDb = MariaDBContainer<Nothing>(DockerImageName.parse("mariadb:11.4"))
            mariaDb.start()
            container = mariaDb

            val config = DatabaseConfig(
                host = mariaDb.host,
                port = mariaDb.getMappedPort(MARIADB_PORT),
                database = mariaDb.databaseName,
                user = mariaDb.username,
                password = mariaDb.password,
                engine = DatabaseEngine.MARIADB,
            )

            dataSource = DatabaseConnectionFactory.createDataSource(config)
            appliedMigrations = DatabaseConnectionFactory.migrate(dataSource)
        }

        @JvmStatic
        @AfterAll
        fun stopMariaDb() {
            if (::dataSource.isInitialized) {
                DatabaseConnectionFactory.closeDataSource(dataSource)
            }
            if (::container.isInitialized) {
                container.stop()
            }
        }
    }

    @Test
    fun `the Flyway chain applies the MariaDB tree and creates the key tables`() {
        assertThat(appliedMigrations).isGreaterThanOrEqualTo(EXPECTED_MIGRATIONS)
        assertThat(tableNames()).contains("players", "accounts", "kv_store", "houses", "house_lists")
    }

    @Test
    fun `the live connection metadata resolves the MariaDB dialect and migration tree`() {
        dataSource.connection.use { connection ->
            assertThat(connection.metaData.databaseProductName).isEqualTo("MariaDB")
            assertThat(SqlDialect.from(connection)).isEqualTo(SqlDialect.MARIADB)
        }
        assertThat(DatabaseConnectionFactory.migrationsLocationForProductName(productName()))
            .isEqualTo(DatabaseConnectionFactory.MARIADB_MIGRATIONS_LOCATION)
    }

    @Test
    fun `KVSQL round-trips through the backticked timestamp and value columns`() {
        val writer = KVSQL(dataSource)
        writer.set("mariadb.kv.level", ValueWrapper(73))
        assertThat(writer.saveAll()).isTrue()

        writer.set("mariadb.kv.level", ValueWrapper(74))
        assertThat(writer.saveAll()).isTrue()

        assertThat(countRows("kv_store", "key_name", "mariadb.kv.level")).isEqualTo(1)

        val loaded = KVSQL(dataSource).get("mariadb.kv.level")
        assertThat(loaded).isNotNull
        assertThat(loaded!!.getInt()).isEqualTo(74)
    }

    @Test
    fun `the houses and house lists upserts insert once and update in place`() {
        val map = GameMap(houses = Houses(ownerInfoLookup = { guid -> HouseOwnerInfo("Player $guid", guid * 10) }))
        val house = map.houses.addHouse(1).apply {
            name = "Central Flat 1"
            owner = 10
            paidUntil = 111
            rentWarnings = 2
            townId = 3
            rent = 500
            size = 17
            bedsCount = 4
            bidder = 11
            bidderName = "Bidder"
            highestBid = 100
            internalBid = 90
            bidEndDate = 999
            state = CyclopediaHouseState.Rented
            transferStatus = true
        }
        house.setAccessList(GUEST_LIST, "Alice")
        house.setAccessList(SUBOWNER_LIST, "Bob")

        val serializer = IOMapSerialize(dataSource = dataSource, map = map)
        assertThat(serializer.saveHouseInfo()).isTrue()

        house.name = "Renamed Flat"
        house.owner = 12
        house.setAccessList(GUEST_LIST, "Carol")

        assertThat(serializer.saveHouseInfo()).isTrue()

        assertThat(countRows("houses")).isEqualTo(1)
        assertThat(houseValue(1, "owner")).isEqualTo("12")
        assertThat(houseValue(1, "name")).isEqualTo("Renamed Flat")
        assertThat(countRows("house_lists")).isEqualTo(2)
        assertThat(houseList(1, GUEST_LIST)).isEqualTo("Carol\n")
        assertThat(houseList(1, SUBOWNER_LIST)).isEqualTo("Bob\n")
    }

    @Test
    fun `JdbcAccountRepository creates saves and reloads an account`() {
        val repository = JdbcAccountRepository(dataSource)
        val accountId = requireNotNull(
            repository.createAccount("MariaHero", "argon2-hash", "maria@test.com", 0, 3),
        )

        val created = repository.loadAccount(accountId)
        assertThat(created).isNotNull
        assertThat(created!!.accountType).isEqualTo(3)

        assertThat(repository.save(created.copy(accountType = 1, houseBidId = 42L))).isTrue()

        val reloaded = repository.loadAccount(accountId)
        assertThat(reloaded).isNotNull
        assertThat(reloaded!!.accountType).isEqualTo(1)
        assertThat(reloaded.houseBidId).isEqualTo(42L)

        val coinsAccount = accountId.toUInt()
        assertThat(repository.setCoins(coinsAccount, CoinType.Normal, 500u)).isTrue()
        assertThat(repository.getCoins(coinsAccount, CoinType.Normal)).isEqualTo(500u)
    }

    @Test
    fun `JdbcPlayerStorageRepository round-trips through the backticked key and value columns`() {
        val accountId = requireNotNull(
            JdbcAccountRepository(dataSource).createAccount("MariaStorageAccount", "hash", "storage@test.com", 0, 1),
        )
        val playerId = 7L
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO players (id, name, account_id) VALUES (?, ?, ?)").use { statement ->
                statement.setLong(1, playerId)
                statement.setString(2, "MariaStorageHero")
                statement.setLong(3, accountId)
                statement.executeUpdate()
            }
        }

        val repository = JdbcPlayerStorageRepository(dataSource)

        assertThat(repository.upsert(playerId, linkedMapOf(10 to 1, 20 to 2))).isTrue()
        assertThat(repository.load(playerId)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 10, value = 1),
            PlayerStorageRow(key = 20, value = 2),
        )

        assertThat(repository.upsert(playerId, linkedMapOf(10 to 99, 30 to 3))).isTrue()
        assertThat(repository.load(playerId)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 10, value = 99),
            PlayerStorageRow(key = 20, value = 2),
            PlayerStorageRow(key = 30, value = 3),
        )

        assertThat(repository.deleteKeys(playerId, listOf(20))).isTrue()
        assertThat(repository.load(playerId)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 10, value = 99),
            PlayerStorageRow(key = 30, value = 3),
        )
    }

    private fun productName(): String =
        dataSource.connection.use { connection -> connection.metaData.databaseProductName }

    private fun tableNames(): Set<String> {
        val names = linkedSetOf<String>()
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                ).use { result ->
                    while (result.next()) {
                        names += result.getString(1)
                    }
                }
            }
        }
        return names
    }

    private fun countRows(table: String): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM $table").use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun countRows(table: String, column: String, value: String): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM $table WHERE $column = ?").use { statement ->
                statement.setString(1, value)
                statement.executeQuery().use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun houseValue(houseId: Int, column: String): String? =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT $column FROM houses WHERE id = ?").use { statement ->
                statement.setInt(1, houseId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString(1) else null
                }
            }
        }

    private fun houseList(houseId: Int, listId: Int): String? =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT list FROM house_lists WHERE house_id = ? AND listid = ?").use { statement ->
                statement.setInt(1, houseId)
                statement.setInt(2, listId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString(1) else null
                }
            }
        }
}
