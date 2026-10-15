package org.opentibia.canary.creatures.players.components

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.SqlDialect
import org.opentibia.canary.kv.KV
import org.opentibia.canary.kv.KVMemory
import javax.sql.DataSource

/**
 * Executes the portable `PlayerCyclopedia` statements (the PostgreSQL/H2 form
 * selected through [SqlDialect]) on the H2 test engine: the recent-deaths and
 * recent-kills `LIMIT ... OFFSET` pagination and the `EXTRACT(EPOCH ...)` time
 * windows all run end to end. The MariaDB backtick forms are pinned by
 * [PlayerCyclopediaTest].
 */
class PlayerCyclopediaSqlDialectTest {

    private lateinit var dataSource: HikariDataSource

    private val deathSends = mutableListOf<List<RecentDeathEntry>>()
    private val pvpKillSends = mutableListOf<List<RecentPvPKillEntry>>()

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("cyclopedia_dialect_${System.nanoTime()}")
        val nowSeconds = System.currentTimeMillis() / 1000
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE player_hirelings (player_id BIGINT, name VARCHAR(255))")
                statement.execute("INSERT INTO player_hirelings VALUES (42, 'Hireling A'), (42, 'Hireling B'), (43, 'Other')")
                statement.execute("CREATE TABLE players (id BIGINT PRIMARY KEY, name VARCHAR(255))")
                statement.execute("INSERT INTO players VALUES (43, 'Victim')")
                statement.execute(
                    "CREATE TABLE player_deaths (player_id BIGINT, \"time\" BIGINT, level INT, " +
                        "killed_by VARCHAR(255), mostdamage_by VARCHAR(255), is_player INT, " +
                        "mostdamage_is_player INT, unjustified INT, mostdamage_unjustified INT, participants VARCHAR(255))",
                )
                statement.execute(
                    "INSERT INTO player_deaths VALUES " +
                        "(42, ${nowSeconds - 86400}, 100, 'Dragon', 'Dragon', 0, 0, 0, 0, '')," +
                        "(42, ${nowSeconds - 172800}, 90, 'Demon', '', 0, 0, 0, 0, '')," +
                        "(42, ${nowSeconds - 100 * 86400}, 80, 'Ancient', '', 0, 0, 0, 0, '')," +
                        "(43, ${nowSeconds - 86400}, 10, 'Knight', 'Knight', 1, 1, 1, 1, '')," +
                        "(43, ${nowSeconds - 100 * 86400}, 5, 'Knight', 'Knight', 1, 1, 1, 1, '')",
                )
            }
        }
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun cyclopedia(player: FakePlayer): PlayerCyclopedia = PlayerCyclopedia(
        player = player,
        database = H2Database(dataSource),
        databaseTasks = H2DatabaseTasks(dataSource),
        playersByID = { player },
        sqlDialect = { SqlDialect.H2 },
    )

    @Test
    fun `loadSummaryData counts the hirelings through the portable query`() {
        val player = FakePlayer()

        cyclopedia(player).loadSummaryData()

        assertThat(player.kvStore.scoped("summary").scoped("hirelings").get("amount")!!.getInt()).isEqualTo(2)
    }

    @Test
    fun `loadDeathHistory reads the recent deaths through the portable query`() {
        val player = FakePlayer()
        player.deaths = deathSends

        cyclopedia(player).loadDeathHistory(1, 5)

        assertThat(deathSends).hasSize(1)
        assertThat(deathSends.single().map { it.cause }).containsExactly(
            "Died at level 100 by Dragon <b>(soloed)</b>",
            "Died at level 90 by Demon",
        )
    }

    @Test
    fun `loadDeathHistory paginates with LIMIT and OFFSET`() {
        val player = FakePlayer()
        player.deaths = deathSends

        cyclopedia(player).loadDeathHistory(2, 1)

        assertThat(deathSends).hasSize(1)
        assertThat(deathSends.single().map { it.cause }).containsExactly("Died at level 90 by Demon")
    }

    @Test
    fun `loadRecentKills joins the victim through the portable query`() {
        val player = FakePlayer()
        player.kills = pvpKillSends

        cyclopedia(player).loadRecentKills(1, 5)

        assertThat(pvpKillSends).hasSize(1)
        val entry = pvpKillSends.single().single()
        assertThat(entry.description).isEqualTo("Killed Victim.")
        assertThat(entry.status).isEqualTo(RecentKillStatus.UNJUSTIFIED)
    }

    // ----- H2 adapters ----------------------------------------------------------

    private class FakePlayer : CyclopediaPlayer {
        val kvStore: KV = KVMemory()
        var deaths: MutableList<List<RecentDeathEntry>>? = null
        var kills: MutableList<List<RecentPvPKillEntry>>? = null

        override fun getGUID(): Long = 42

        override fun getID(): Int = 7

        override fun getName(): String = "Knight"

        override fun kv(): KV = kvStore

        override fun addAsyncOngoingTask(task: PlayerAsyncTask) = Unit

        override fun resetAsyncOngoingTask(task: PlayerAsyncTask) = Unit

        override fun sendCyclopediaCharacterRecentDeaths(page: Int, pages: Int, entries: List<RecentDeathEntry>) {
            deaths?.add(entries)
        }

        override fun sendCyclopediaCharacterRecentPvPKills(page: Int, pages: Int, entries: List<RecentPvPKillEntry>) {
            kills?.add(entries)
        }
    }

    private class H2Database(private val dataSource: DataSource) : CyclopediaDatabase {
        override fun escapeString(value: String): String = "'$value'"

        override fun storeQuery(query: String): CyclopediaQueryResult? = execute(dataSource, query)
    }

    private class H2DatabaseTasks(private val dataSource: DataSource) : CyclopediaDatabaseTasks {
        override fun store(query: String, callback: (CyclopediaQueryResult?, Boolean) -> Unit) {
            callback(execute(dataSource, query), true)
        }
    }

    private companion object {
        fun execute(dataSource: DataSource, query: String): CyclopediaQueryResult? =
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(query)
                    val result = statement.resultSet ?: return null
                    result.use {
                        val labels = (1..result.metaData.columnCount).map { index ->
                            result.metaData.getColumnLabel(index)
                        }
                        val rows = mutableListOf<Map<String, Any?>>()
                        while (result.next()) {
                            rows.add(labels.associateWith { label -> result.getObject(label) })
                        }
                        if (rows.isEmpty()) null else H2Result(rows)
                    }
                }
            }
    }

    private class H2Result(private val rows: List<Map<String, Any?>>) : CyclopediaQueryResult {
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
}
