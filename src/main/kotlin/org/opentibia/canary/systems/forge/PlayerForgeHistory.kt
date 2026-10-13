package org.opentibia.canary.systems.forge

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.SQLException
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Mirror of Canary `ForgeHistory`
 * (src/creatures/players/components/player_forge_history.hpp:16-39).
 *
 * Field types follow the C++ declaration: tier/bonus are uint8_t (Int), the
 * counters and timestamps are 64-bit (Long).
 */
data class ForgeHistory(
    var actionType: ForgeAction_t = ForgeAction_t.FUSION,
    var tier: Int = 0,
    var bonus: Int = 0,
    var createdAt: Long = 0,
    var id: Long = 0,
    var cost: Long = 0,
    var dustCost: Long = 0,
    var coresCost: Long = 0,
    var gained: Long = 0,
    var success: Boolean = false,
    var tierLoss: Boolean = false,
    var successCore: Boolean = false,
    var tierCore: Boolean = false,
    var convergence: Boolean = false,
    var description: String = "",
    var firstItemName: String = "",
    var secondItemName: String = "",
)

/**
 * Dependency-injected replacement for Canary's g_database() access used by
 * `PlayerForgeHistory::load`/`save`
 * (src/creatures/players/components/player_forge_history.cpp:43-113).
 */
interface ForgeHistoryRepository {
    fun load(playerGuid: Long): List<ForgeHistory>
    fun delete(playerGuid: Long, ids: List<Long>): Boolean
    fun insert(playerGuid: Long, entries: List<ForgeHistory>): Boolean
}

class InMemoryForgeHistoryRepository : ForgeHistoryRepository {
    private val rows = mutableMapOf<Long, MutableList<ForgeHistory>>()

    override fun load(playerGuid: Long): List<ForgeHistory> =
        rows[playerGuid]?.map { it.copy() } ?: emptyList()

    override fun delete(playerGuid: Long, ids: List<Long>): Boolean {
        rows[playerGuid]?.removeIf { it.id in ids }
        return true
    }

    override fun insert(playerGuid: Long, entries: List<ForgeHistory>): Boolean {
        val list = rows.getOrPut(playerGuid) { mutableListOf() }
        list.addAll(entries.map { it.copy() })
        return true
    }
}

/**
 * JDBC persistence for a player's forge-history rows, mirroring Canary's
 * `g_database()` calls in `PlayerForgeHistory::load`/`save`
 * (src/creatures/players/components/player_forge_history.cpp:43-113) against
 * the `forge_history` table (schema.sql:314-329).
 *
 * Load reads only the columns Canary reads (`id`, `action_type`,
 * `description`, `done_at`, `is_success`), leaving the non-persisted
 * [ForgeHistory] fields at their defaults, and orders by `id` to reproduce the
 * in-memory vector order the C++ load appends in. Insert mirrors the
 * `(player_id, action_type, description, done_at, is_success)` batch and delete
 * mirrors the `player_id` + `id IN (...)` statement; both report Canary's
 * `bool` failure contract instead of throwing.
 */
class JdbcForgeHistoryRepository(private val dataSource: DataSource) : ForgeHistoryRepository {

    override fun load(playerGuid: Long): List<ForgeHistory> {
        val result = mutableListOf<ForgeHistory>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, action_type, description, done_at, is_success
                FROM forge_history
                WHERE player_id = ?
                ORDER BY id
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, playerGuid)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(
                            ForgeHistory(
                                actionType = ForgeAction_t.entries.firstOrNull {
                                    it.value == rs.getInt("action_type")
                                } ?: ForgeAction_t.FUSION,
                                createdAt = rs.getLong("done_at"),
                                id = rs.getLong("id"),
                                success = rs.getBoolean("is_success"),
                                description = rs.getString("description"),
                            ),
                        )
                    }
                }
            }
        }
        return result
    }

    override fun delete(playerGuid: Long, ids: List<Long>): Boolean {
        if (ids.isEmpty()) {
            return true
        }

        return try {
            dataSource.connection.use { conn ->
                val placeholders = ids.joinToString(", ") { "?" }
                conn.prepareStatement(
                    "DELETE FROM forge_history WHERE player_id = ? AND id IN ($placeholders)",
                ).use { stmt ->
                    stmt.setLong(1, playerGuid)
                    ids.forEachIndexed { index, id -> stmt.setLong(index + 2, id) }
                    stmt.executeUpdate()
                }
            }
            true
        } catch (ex: SQLException) {
            logger.error(ex) { "Failed to delete forge history entries for player $playerGuid" }
            false
        }
    }

    override fun insert(playerGuid: Long, entries: List<ForgeHistory>): Boolean {
        if (entries.isEmpty()) {
            return true
        }

        return try {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement(
                        """
                        INSERT INTO forge_history (player_id, action_type, description, done_at, is_success)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { stmt ->
                        entries.forEach { entry ->
                            stmt.setLong(1, playerGuid)
                            stmt.setInt(2, entry.actionType.value)
                            stmt.setString(3, entry.description)
                            stmt.setLong(4, entry.createdAt)
                            stmt.setBoolean(5, entry.success)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    conn.commit()
                } catch (ex: Exception) {
                    conn.rollback()
                    throw ex
                }
            }
            true
        } catch (ex: SQLException) {
            logger.error(ex) { "Failed to insert forge history entries for player $playerGuid" }
            false
        }
    }
}

/**
 * Mirror of Canary class `PlayerForgeHistory`
 * (src/creatures/players/components/player_forge_history.hpp:41-57).
 *
 * `add` mirrors the createdAt collision-advance loop
 * (src/creatures/players/components/player_forge_history.cpp:24-34); `remove`
 * keeps the same removed-id breadcrumb (cpp:36-41); `load`/`save` delegate I/O
 * to the injected repository (cpp:43-113).
 */
class PlayerForgeHistory(
    private val playerGuid: Long,
    private val repository: ForgeHistoryRepository = InMemoryForgeHistoryRepository(),
) {
    private val history = mutableListOf<ForgeHistory>()
    private val modifiedHistory = mutableListOf<ForgeHistory>()
    private val removedHistoryIds = mutableListOf<Long>()

    fun get(): List<ForgeHistory> = history

    fun add(history: ForgeHistory) {
        val adjusted = history.copy()
        while (this.history.any { it.createdAt == adjusted.createdAt }) {
            adjusted.createdAt += 1
        }
        this.history.add(adjusted)
        modifiedHistory.add(adjusted)
    }

    fun remove(id: Long) {
        removedHistoryIds.add(id)
        history.removeIf { it.id == id }
    }

    fun load(): Boolean {
        for (stored in repository.load(playerGuid)) {
            history.add(
                ForgeHistory(
                    id = stored.id,
                    actionType = stored.actionType,
                    description = stored.description,
                    createdAt = stored.createdAt,
                    success = stored.success,
                )
            )
        }
        return true
    }

    fun save(): Boolean {
        if (modifiedHistory.isEmpty() && removedHistoryIds.isEmpty()) {
            return true
        }

        if (removedHistoryIds.isNotEmpty()) {
            if (!repository.delete(playerGuid, removedHistoryIds)) {
                return false
            }
            removedHistoryIds.clear()
        }

        if (modifiedHistory.isNotEmpty() && !repository.insert(playerGuid, modifiedHistory)) {
            return false
        }

        modifiedHistory.clear()
        return true
    }
}
