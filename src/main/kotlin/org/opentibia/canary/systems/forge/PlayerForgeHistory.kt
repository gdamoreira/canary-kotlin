package org.opentibia.canary.systems.forge

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
