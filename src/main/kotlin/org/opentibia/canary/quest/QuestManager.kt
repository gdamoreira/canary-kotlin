package org.opentibia.canary.quest

import org.opentibia.canary.creatures.Player
import java.util.concurrent.ConcurrentHashMap

class PlayerStorageManager {
    // Key: (playerId, storageKey) -> integer value
    private val storages = ConcurrentHashMap<Pair<Long, Int>, Int>()

    fun getStorage(player: Player, key: Int): Int {
        return storages.getOrDefault(player.id to key, -1)
    }

    fun setStorage(player: Player, key: Int, value: Int) {
        storages[player.id to key] = value
    }
}

data class QuestMission(
    val name: String,
    val stateStorageValue: Int,
    val description: String
)

data class Quest(
    val id: Int,
    val name: String,
    val storageKey: Int,
    val missions: List<QuestMission> = emptyList()
) {
    fun isStarted(player: Player, storageManager: PlayerStorageManager): Boolean {
        return storageManager.getStorage(player, storageKey) > 0
    }

    fun getCurrentMission(player: Player, storageManager: PlayerStorageManager): QuestMission? {
        val currentValue = storageManager.getStorage(player, storageKey)
        return missions.find { it.stateStorageValue == currentValue }
    }
}
