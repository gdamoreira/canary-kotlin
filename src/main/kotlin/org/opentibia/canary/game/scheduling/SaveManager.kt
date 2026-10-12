package org.opentibia.canary.game.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.persistence.PlayerStorage
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class SaveManager(private val playerStorage: PlayerStorage) {

    private val onlinePlayers = ConcurrentHashMap<Long, Player>()

    val onlinePlayerCount: Int get() = onlinePlayers.size

    fun registerOnlinePlayer(player: Player) {
        onlinePlayers[player.id] = player
    }

    fun unregisterOnlinePlayer(player: Player) {
        onlinePlayers.remove(player.id)
    }

    fun saveAll(): Int {
        var count = 0
        for (player in onlinePlayers.values) {
            try {
                playerStorage.save(player)
                count++
            } catch (e: Exception) {
                logger.error(e) { "Failed to save player ${player.name} during server save" }
            }
        }
        logger.info { "Server save completed: $count players saved." }
        return count
    }

    fun shutdown(): Boolean {
        logger.info { "Graceful shutdown initiated. Saving all active players..." }
        saveAll()
        onlinePlayers.clear()
        return true
    }
}
