package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.players.vip.VipEntry
import org.opentibia.canary.creatures.players.vip.VipGroup

/**
 * H2 (Flyway V10) round-trip tests for [JdbcVipRepository], transcribed from
 * the `IOLoginData` VIP statics:
 *  - `getVIPEntries` (src/io/iologindata.cpp:363-385) joins `players.name`;
 *  - `addVIPEntry` / `editVIPEntry` / `removeVIPEntry` (:387-422);
 *  - `getVIPGroupEntries` (:424-444);
 *  - `addVIPGroupEntry` / `editVIPGroupEntry` / `removeVIPGroupEntry` (:446-479);
 *  - `addGuidVIPGroupEntry` / `removeGuidVIPGroupEntry` (:481-499);
 *  - the `account_vipgrouplist` membership reads of `loadPlayerVip`
 *    (iologindata_load_player.cpp:770-806).
 * Tables mirror schema.sql:206-266.
 */
class JdbcVipRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcVipRepository
    private var accountId = 0L

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_vip_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        repository = JdbcVipRepository(dataSource)
        accountId = JdbcAccountRepository(dataSource)
            .createAccount("vip-account", "hash", "vip@test.com", 0, 1)!!
        insertPlayer(101L, "Vip Target")
        insertPlayer(102L, "Vip Friend")
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun insertPlayer(id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO players (id, name, account_id, deletion, lastlogin) VALUES (?, ?, ?, 0, ?)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.setLong(3, accountId)
                stmt.setLong(4, getTimeNow())
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `addVipEntry persists description icon and notify for the account`() {
        repository.addVipEntry(accountId, 101L, "rival", 3, true)

        assertThat(repository.getVipEntries(accountId)).containsExactly(
            VipEntry(guid = 101L, name = "Vip Target", description = "rival", icon = 3, notify = true),
        )
    }

    @Test
    fun `getVipEntries resolves the target name from the players table`() {
        repository.addVipEntry(accountId, 102L, "", 0, false)

        assertThat(repository.getVipEntries(accountId).single().name).isEqualTo("Vip Friend")
    }

    @Test
    fun `editVipEntry updates an existing row`() {
        repository.addVipEntry(accountId, 101L, "old", 1, false)

        repository.editVipEntry(accountId, 101L, "new", 2, true)

        assertThat(repository.getVipEntries(accountId)).containsExactly(
            VipEntry(guid = 101L, name = "Vip Target", description = "new", icon = 2, notify = true),
        )
    }

    @Test
    fun `removeVipEntry deletes only the matching account row`() {
        repository.addVipEntry(accountId, 101L, "", 0, false)
        repository.addVipEntry(accountId, 102L, "", 0, false)

        repository.removeVipEntry(accountId, 101L)

        assertThat(repository.getVipEntries(accountId).map { it.guid }).containsExactly(102L)
    }

    @Test
    fun `addVipGroupEntry persists a group for the account`() {
        repository.addVipGroupEntry(1, accountId, "Enemies", false)

        assertThat(repository.getVipGroupEntries(accountId)).containsExactly(
            VipGroup(id = 1, name = "Enemies", customizable = false),
        )
    }

    @Test
    fun `editVipGroupEntry updates name and customizable`() {
        repository.addVipGroupEntry(1, accountId, "Enemies", false)

        repository.editVipGroupEntry(1, accountId, "Friends", true)

        assertThat(repository.getVipGroupEntries(accountId)).containsExactly(
            VipGroup(id = 1, name = "Friends", customizable = true),
        )
    }

    @Test
    fun `removeVipGroupEntry deletes the group and its memberships`() {
        repository.addVipGroupEntry(1, accountId, "Enemies", false)
        repository.addGuidVipGroupEntry(1, accountId, 101L)

        repository.removeVipGroupEntry(1, accountId)

        assertThat(repository.getVipGroupEntries(accountId)).isEmpty()
    }

    @Test
    fun `addGuidVipGroupEntry stores one membership row per group`() {
        repository.addVipGroupEntry(1, accountId, "Enemies", false)
        repository.addVipGroupEntry(2, accountId, "Friends", false)

        repository.addGuidVipGroupEntry(1, accountId, 101L)
        repository.addGuidVipGroupEntry(2, accountId, 101L)

        val groups = repository.getVipGroupEntries(accountId)
        assertThat(groups.first { it.id == 1 }.vipGroupGuids).containsExactly(101L)
        assertThat(groups.first { it.id == 2 }.vipGroupGuids).containsExactly(101L)
    }

    @Test
    fun `removeGuidVipGroupEntry clears every group of the guid`() {
        repository.addVipGroupEntry(1, accountId, "Enemies", false)
        repository.addVipGroupEntry(2, accountId, "Friends", false)
        repository.addGuidVipGroupEntry(1, accountId, 101L)
        repository.addGuidVipGroupEntry(2, accountId, 101L)

        repository.removeGuidVipGroupEntry(accountId, 101L)

        assertThat(repository.getVipGroupEntries(accountId).flatMap { it.vipGroupGuids }).isEmpty()
    }

    @Test
    fun `getVipGroupEntries ignores the guid parameter like Canary`() {
        repository.addVipGroupEntry(1, accountId, "Enemies", false)

        assertThat(repository.getVipGroupEntries(accountId, guid = 999L)).hasSize(1)
    }

    @Test
    fun `getVipEntries returns an empty list for an account without entries`() {
        assertThat(repository.getVipEntries(accountId)).isEmpty()
    }
}
