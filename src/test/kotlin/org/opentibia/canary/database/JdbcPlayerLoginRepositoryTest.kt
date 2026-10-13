package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * H2 (Flyway V10) transcript tests for the `IOLoginData` player-row surface of
 * [JdbcPlayerRepository]:
 *  - `getNameByGuid` (src/io/iologindata.cpp:300-308);
 *  - `getGuidByName` (:310-320);
 *  - `getGuidByNameEx` (:322-340), including the `group_id` special-VIP flag;
 *  - `formatPlayerName` (:342-355);
 *  - `increaseBankBalance` (:357-361);
 *  - `loadPlayerById` / `loadPlayerByName` / `loadPlayerBasicInfo` reads
 *    (iologindata_load_player.cpp:81-93, 121-221) over the V10
 *    `pronoun`/`balance`/`blessingsN` player columns.
 */
class JdbcPlayerLoginRepositoryTest {

    private lateinit var dataSource: HikariDataSource

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_login_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun insertPlayer(
        id: Long,
        name: String,
        accountId: Long = 1L,
        groupId: Int = 1,
        vocation: Int = 4,
        level: Int = 50,
        health: Int = 725,
        healthMax: Int = 725,
        mana: Int = 120,
        manaMax: Int = 250,
        cap: Int = 350,
        speed: Int = 300,
        experience: Long = 5_000_000L,
        posX: Int = 1000,
        posY: Int = 1000,
        posZ: Int = 7,
        sex: Int = 1,
        pronoun: Int = 3,
        balance: Long = 0,
        deletion: Long = 0,
        blessings: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0, 0),
    ) {
        val columns = mutableListOf<Any?>(
            id, name, accountId, groupId, vocation, level, health, healthMax,
            mana, manaMax, cap, speed, experience, posX, posY, posZ, sex, pronoun,
            balance, deletion,
        )
        columns.addAll(blessings)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, group_id, vocation, level, health, healthmax,
                    mana, manamax, cap, speed, experience, posx, posy, posz, sex, pronoun,
                    balance, deletion,
                    blessings1, blessings2, blessings3, blessings4,
                    blessings5, blessings6, blessings7, blessings8
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                columns.forEachIndexed { index, value ->
                    when (value) {
                        is Long -> stmt.setLong(index + 1, value)
                        is Int -> stmt.setInt(index + 1, value)
                        is String -> stmt.setString(index + 1, value)
                        else -> stmt.setObject(index + 1, value)
                    }
                }
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `loadRecordByName maps the persisted player columns`() {
        insertPlayer(id = 101, name = "Login Hero", balance = 5_000, blessings = listOf(1, 0, 3, 0, 0, 0, 0, 8))

        val record = JdbcPlayerRepository(dataSource).loadRecordByName("Login Hero")

        assertThat(record).isNotNull
        assertThat(record!!.id).isEqualTo(101L)
        assertThat(record.accountId).isEqualTo(1L)
        assertThat(record.groupId).isEqualTo(1)
        assertThat(record.vocation).isEqualTo(4)
        assertThat(record.level).isEqualTo(50)
        assertThat(record.health).isEqualTo(725)
        assertThat(record.healthMax).isEqualTo(725)
        assertThat(record.mana).isEqualTo(120)
        assertThat(record.manaMax).isEqualTo(250)
        assertThat(record.capacity).isEqualTo(35_000.0)
        assertThat(record.speed).isEqualTo(300)
        assertThat(record.experience).isEqualTo(5_000_000L)
        assertThat(record.posX).isEqualTo(1000)
        assertThat(record.posY).isEqualTo(1000)
        assertThat(record.posZ).isEqualTo(7)
        assertThat(record.sex).isEqualTo(1)
        assertThat(record.pronoun).isEqualTo(3)
        assertThat(record.balance).isEqualTo(5_000L)
        assertThat(record.blessings).containsExactly(1, 0, 3, 0, 0, 0, 0, 8)
    }

    @Test
    fun `loadRecordById returns the same row as the name lookup`() {
        insertPlayer(id = 102, name = "Id Hero")

        val repository = JdbcPlayerRepository(dataSource)
        assertThat(repository.loadRecordById(102L)).isEqualTo(repository.loadRecordByName("Id Hero"))
    }

    @Test
    fun `loadRecord lookups return null for missing players`() {
        val repository = JdbcPlayerRepository(dataSource)

        assertThat(repository.loadRecordByName("Ghost")).isNull()
        assertThat(repository.loadRecordById(999_999L)).isNull()
    }

    @Test
    fun `loadByName rejects deleted players`() {
        insertPlayer(id = 103, name = "Deleted Hero", deletion = 1_700_000_000L)

        val repository = JdbcPlayerRepository(dataSource)
        assertThat(repository.loadByName("Deleted Hero")).isNull()
        assertThat(repository.loadRecordByName("Deleted Hero")).isNotNull
    }

    @Test
    fun `getNameByGuid returns the persisted name or an empty string`() {
        insertPlayer(id = 104, name = "Guid Hero")

        val repository = JdbcPlayerRepository(dataSource)
        assertThat(repository.getNameByGuid(104L)).isEqualTo("Guid Hero")
        assertThat(repository.getNameByGuid(999_999L)).isEmpty()
    }

    @Test
    fun `getGuidByName returns the id or zero like Canary`() {
        insertPlayer(id = 105, name = "Name Hero")

        val repository = JdbcPlayerRepository(dataSource)
        assertThat(repository.getGuidByName("Name Hero")).isEqualTo(105L)
        assertThat(repository.getGuidByName("Ghost")).isZero()
    }

    @Test
    fun `getGuidByNameEx reports the special vip group flag through the resolver`() {
        insertPlayer(id = 106, name = "Vip Hero", groupId = 5)
        val repository = JdbcPlayerRepository(dataSource) { groupId -> groupId == 5 }

        val entry = repository.getGuidByNameEx("Vip Hero")

        assertThat(entry).isNotNull
        assertThat(entry!!.guid).isEqualTo(106L)
        assertThat(entry.name).isEqualTo("Vip Hero")
        assertThat(entry.specialVip).isTrue()
        assertThat(repository.getGuidByNameEx("Ghost")).isNull()
    }

    @Test
    fun `formatPlayerName returns the canonical database name`() {
        insertPlayer(id = 107, name = "Format Hero")

        val repository = JdbcPlayerRepository(dataSource)
        assertThat(repository.formatPlayerName("Format Hero")).isEqualTo("Format Hero")
        assertThat(repository.formatPlayerName("format hero")).isNull()
    }

    @Test
    fun `increaseBankBalance adds to the persisted balance`() {
        insertPlayer(id = 108, name = "Bank Hero", balance = 100)

        val repository = JdbcPlayerRepository(dataSource)

        assertThat(repository.increaseBankBalance(108L, 50L)).isTrue()
        assertThat(repository.loadRecordByName("Bank Hero")!!.balance).isEqualTo(150L)

        assertThat(repository.increaseBankBalance(999_999L, 50L)).isFalse()
    }

    @Test
    fun `savePlayerRecord persists stats blessings and position`() {
        insertPlayer(id = 109, name = "Save Hero", health = 725, healthMax = 725, mana = 120, manaMax = 250)
        val repository = JdbcPlayerRepository(dataSource)
        val player = repository.loadByName("Save Hero")!!
        player.drainHealth(200)
        player.mana = 99
        player.position = Position(1050, 1060, 7)
        player.experience = 5_100_000L
        player.addBlessing(1, 2)

        assertThat(repository.savePlayerRecord(player)).isTrue()

        val reloaded = repository.loadRecordByName("Save Hero")!!
        assertThat(reloaded.health).isEqualTo(525)
        assertThat(reloaded.mana).isEqualTo(99)
        assertThat(reloaded.posX).isEqualTo(1050)
        assertThat(reloaded.posY).isEqualTo(1060)
        assertThat(reloaded.experience).isEqualTo(5_100_000L)
        assertThat(reloaded.blessings.first()).isEqualTo(2)
    }

    @Test
    fun `savePlayerRecord returns false for an unknown player id`() {
        val repository = JdbcPlayerRepository(dataSource)
        val unknown = org.opentibia.canary.creatures.Player(
            id = 999_999L,
            name = "Unknown Hero",
            maxHealth = 100,
            health = 100,
            position = Position(1, 2, 7),
        )

        assertThat(repository.savePlayerRecord(unknown)).isFalse()
    }
}
