package org.opentibia.canary.database

import org.opentibia.canary.creatures.players.vip.VipEntry
import org.opentibia.canary.creatures.players.vip.VipGroup
import org.opentibia.canary.creatures.players.vip.VipRepository
import javax.sql.DataSource

/**
 * Login-side VIP reads on top of [VipRepository]:
 *  - [getVipEntries] mirrors `IOLoginData::getVIPEntries`
 *    (src/io/iologindata.cpp:363-385), joining `players.name`;
 *  - [getVipGroupEntries] mirrors `IOLoginData::getVIPGroupEntries`
 *    (src/io/iologindata.cpp:424-444) and folds in the
 *    `account_vipgrouplist` membership reads of `loadPlayerVip`
 *    (iologindata_load_player.cpp:770-806) so the group objects carry their
 *    member guids.
 */
interface VipLoginRepository : VipRepository {
    fun getVipEntries(accountId: Long): List<VipEntry>

    fun getVipGroupEntries(accountId: Long, guid: Long = 0): List<VipGroup>
}

/**
 * JDBC persistence for `account_viplist`, `account_vipgroups` and
 * `account_vipgrouplist` (schema.sql:206-266).
 *
 * The eight mutators transcribe the `IOLoginData` VIP statics
 * (src/io/iologindata.cpp:387-499): plain `INSERT`/`UPDATE`/`DELETE` statements
 * that log and swallow a failed execute, exactly like the C++ helpers.
 */
class JdbcVipRepository(private val dataSource: DataSource) : VipLoginRepository {

    override fun addVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean) {
        execute(
            "INSERT INTO account_viplist (account_id, player_id, description, icon, notify) VALUES (?, ?, ?, ?, ?)",
        ) { stmt ->
            stmt.setLong(1, accountId)
            stmt.setLong(2, guid)
            stmt.setString(3, description)
            stmt.setInt(4, icon)
            stmt.setBoolean(5, notify)
        }
    }

    override fun editVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean) {
        execute(
            "UPDATE account_viplist SET description = ?, icon = ?, notify = ? WHERE account_id = ? AND player_id = ?",
        ) { stmt ->
            stmt.setString(1, description)
            stmt.setInt(2, icon)
            stmt.setBoolean(3, notify)
            stmt.setLong(4, accountId)
            stmt.setLong(5, guid)
        }
    }

    override fun removeVipEntry(accountId: Long, guid: Long) {
        execute("DELETE FROM account_viplist WHERE account_id = ? AND player_id = ?") { stmt ->
            stmt.setLong(1, accountId)
            stmt.setLong(2, guid)
        }
    }

    override fun addVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean) {
        execute(
            "INSERT INTO account_vipgroups (id, account_id, name, customizable) VALUES (?, ?, ?, ?)",
        ) { stmt ->
            stmt.setInt(1, groupId)
            stmt.setLong(2, accountId)
            stmt.setString(3, groupName)
            stmt.setBoolean(4, customizable)
        }
    }

    override fun editVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean) {
        execute(
            "UPDATE account_vipgroups SET name = ?, customizable = ? WHERE id = ? AND account_id = ?",
        ) { stmt ->
            stmt.setString(1, groupName)
            stmt.setBoolean(2, customizable)
            stmt.setInt(3, groupId)
            stmt.setLong(4, accountId)
        }
    }

    override fun removeVipGroupEntry(groupId: Int, accountId: Long) {
        execute("DELETE FROM account_vipgroups WHERE id = ? AND account_id = ?") { stmt ->
            stmt.setInt(1, groupId)
            stmt.setLong(2, accountId)
        }
    }

    override fun addGuidVipGroupEntry(groupId: Int, accountId: Long, guid: Long) {
        execute(
            "INSERT INTO account_vipgrouplist (account_id, player_id, vipgroup_id) VALUES (?, ?, ?)",
        ) { stmt ->
            stmt.setLong(1, accountId)
            stmt.setLong(2, guid)
            stmt.setInt(3, groupId)
        }
    }

    override fun removeGuidVipGroupEntry(accountId: Long, guid: Long) {
        execute("DELETE FROM account_vipgrouplist WHERE account_id = ? AND player_id = ?") { stmt ->
            stmt.setLong(1, accountId)
            stmt.setLong(2, guid)
        }
    }

    override fun getVipEntries(accountId: Long): List<VipEntry> {
        val result = mutableListOf<VipEntry>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT player_id,
                       (SELECT name FROM players WHERE id = account_viplist.player_id) AS name,
                       description, icon, notify
                FROM account_viplist
                WHERE account_id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, accountId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(
                            VipEntry(
                                guid = rs.getLong("player_id"),
                                name = rs.getString("name") ?: "",
                                description = rs.getString("description"),
                                icon = rs.getInt("icon"),
                                notify = rs.getBoolean("notify"),
                            ),
                        )
                    }
                }
            }
        }
        return result
    }

    override fun getVipGroupEntries(accountId: Long, guid: Long): List<VipGroup> {
        val groups = mutableListOf<VipGroup>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, name, customizable FROM account_vipgroups WHERE account_id = ? ORDER BY id",
            ).use { stmt ->
                stmt.setLong(1, accountId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        groups.add(
                            VipGroup(
                                id = rs.getInt("id"),
                                name = rs.getString("name"),
                                customizable = rs.getBoolean("customizable"),
                            ),
                        )
                    }
                }
            }

            conn.prepareStatement(
                "SELECT player_id, vipgroup_id FROM account_vipgrouplist WHERE account_id = ?",
            ).use { stmt ->
                stmt.setLong(1, accountId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val groupId = rs.getInt("vipgroup_id")
                        groups.firstOrNull { it.id == groupId }?.vipGroupGuids?.add(rs.getLong("player_id"))
                    }
                }
            }
        }
        return groups
    }

    private fun execute(sql: String, bind: (java.sql.PreparedStatement) -> Unit) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    bind(stmt)
                    stmt.executeUpdate()
                }
            }
        } catch (ex: java.sql.SQLException) {
            logger.error(ex) { "Failed VIP query: $sql" }
        }
    }

    private companion object {
        private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    }
}
