package org.opentibia.canary.game.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.persistence.PlayerPersistenceService
import org.opentibia.canary.persistence.PlayerStorage
import org.opentibia.canary.social.guild.Guild
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * `SaveManager` (src/game/scheduling/save_manager.hpp:20-62,
 * save_manager.cpp:21-218): global save-all (players -> guilds -> map -> KV),
 * async scheduling with generation tokens and the shutdown flush.
 *
 * Canary reaches `Game::getGuilds`, `Map::save` and `KVStore::saveAll` through
 * globals; per AGENTS.md 1.1 they are constructor-injected seams ([guilds],
 * [guildSaver], [mapSaver], [keyValueSaver]). Player scheduling keeps a
 * [WeakReference] plus the per-GUID generation token, mirroring the C++
 * `std::weak_ptr` + `m_playerMap` validation (save_manager.cpp:118-149).
 * The online set Canary reads from `g_game().getPlayers()` is fed through
 * [registerOnlinePlayer]/[unregisterOnlinePlayer], bound by the composition
 * root to the world session install/removal (`Game::addPlayer`/`removePlayer`).
 *
 * PARITY GAPS: the `Player::PlayerLock` serialization lock and the dead-player
 * `loginPosition` rewrite (`player->isDead()`, save_manager.cpp:41-44) need
 * player state the Kotlin model does not carry yet.
 */
class SaveManager(
    private val playerStorage: PlayerStorage,
    private val featurePersistence: PlayerPersistenceService? = null,
    private val guilds: () -> Collection<Guild> = { emptyList() },
    private val guildSaver: ((Guild) -> Unit)? = null,
    private val mapSaver: (() -> Boolean)? = null,
    private val keyValueSaver: (() -> Boolean)? = null,
    private val saveAsync: Boolean = false,
    private val saveExecutor: ExecutorService? = null,
) {

    private val onlinePlayers = Collections.synchronizedMap(LinkedHashMap<Long, Player>())
    private val scheduledAtSequence = AtomicLong(0)
    private val playerScheduleSequence = AtomicLong(0)
    private val playerScheduledAt = ConcurrentHashMap<Long, Long>()
    private val shuttingDown = AtomicBoolean(false)

    val onlinePlayerCount: Int get() = onlinePlayers.size

    /**
     * `SaveManager` online-set insert for `Game::addPlayer` (game.cpp:11624):
     * the newest session of a character owns the registration, so a relog
     * replaces the previous object exactly like `Game::players` does.
     */
    fun registerOnlinePlayer(player: Player) {
        onlinePlayers[player.id] = player
    }

    /**
     * `SaveManager` online-set release for `Game::removePlayer`
     * (game.cpp:11631). Only the exact [player] object is released: a stale
     * session must not unregister a newer login of the same character
     * (GUID re-resolution points at a later session, save_manager.cpp:120-149).
     */
    fun unregisterOnlinePlayer(player: Player) {
        onlinePlayers.remove(player.id, player)
    }

    fun saveAll(): Int {
        logger.info { "Saving server..." }
        val players = snapshotPlayers()
        logger.info { "Saving ${players.size} players..." }
        val saved = if (saveExecutor != null && players.size > 1) {
            savePlayersInParallel(players)
        } else {
            savePlayersSequentially(players)
        }

        for (guild in guilds()) {
            saveGuild(guild)
        }
        saveMap()
        saveKV()

        logger.info { "Server save completed: $saved players saved." }
        return saved
    }

    fun scheduleAll() {
        val scheduledAt = scheduledAtSequence.incrementAndGet()
        if (!saveAsync || saveExecutor == null) {
            saveAll()
            return
        }

        saveExecutor.execute {
            if (scheduledAtSequence.get() != scheduledAt) {
                logger.warn { "Skipping save for server because another save has been scheduled." }
                return@execute
            }
            saveAll()
        }
    }

    fun savePlayer(player: Player): Boolean {
        if (isOnline(player) && !shuttingDown.get()) {
            schedulePlayer(WeakReference(player))
            return true
        }
        return doSavePlayer(player)
    }

    fun shutdown(): Boolean {
        if (!shuttingDown.compareAndSet(false, true)) {
            return true
        }
        logger.info { "Graceful shutdown initiated. Saving all active players..." }
        saveAll()
        onlinePlayers.clear()
        return true
    }

    private fun schedulePlayer(playerRef: WeakReference<Player>) {
        val player = playerRef.get()
        if (player == null) {
            logger.debug { "Skipping save for player because player is no longer online." }
            return
        }

        if (!saveAsync || saveExecutor == null) {
            if (!shuttingDown.get()) {
                logger.debug { "Saving player ${player.name}." }
            }
            doSavePlayer(player)
            return
        }

        logger.debug { "Scheduling player ${player.name} for saving." }
        val scheduledAt = playerScheduleSequence.incrementAndGet()
        playerScheduledAt[player.getGUID()] = scheduledAt
        saveExecutor.execute {
            val resolved = playerRef.get()
            if (resolved == null) {
                logger.debug { "Skipping save for player because player is no longer online." }
                return@execute
            }
            if (playerScheduledAt[resolved.getGUID()] != scheduledAt) {
                logger.warn { "Skipping save for player because another save has been scheduled." }
                return@execute
            }
            doSavePlayer(resolved)
        }
    }

    private fun doSavePlayer(player: Player): Boolean {
        playerScheduledAt.remove(player.getGUID())
        return try {
            playerStorage.save(player)
            featurePersistence?.save(player)
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to save player ${player.name}." }
            false
        }
    }

    /**
     * `SaveManager::saveGuild` (save_manager.cpp:182-194): public like the C++
     * method (save_manager.hpp:33) because `Game::removeGuild`
     * (game.cpp:11757-11762) saves through it; the save-all rotation calls it
     * per registered guild.
     */
    fun saveGuild(guild: Guild) {
        try {
            guildSaver?.invoke(guild)
        } catch (e: Exception) {
            logger.error(e) { "Failed to save guild ${guild.name}." }
        }
    }

    private fun saveMap() {
        try {
            if (mapSaver?.invoke() == false) {
                logger.error { "Failed to save map." }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save map." }
        }
    }

    private fun saveKV() {
        try {
            if (keyValueSaver?.invoke() == false) {
                logger.error { "Failed to save key-value store." }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save key-value store." }
        }
    }

    private fun snapshotPlayers(): List<Player> = synchronized(onlinePlayers) {
        onlinePlayers.values.toList()
    }

    private fun isOnline(player: Player): Boolean = onlinePlayers.containsKey(player.id)

    private fun savePlayersSequentially(players: List<Player>): Int {
        var saved = 0
        for (player in players) {
            try {
                if (doSavePlayer(player)) {
                    saved++
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save player ${player.name} during server save" }
            }
        }
        return saved
    }

    private fun savePlayersInParallel(players: List<Player>): Int {
        val executor = saveExecutor ?: return savePlayersSequentially(players)
        val pending = players.map { player ->
            executor.submit<Boolean> { doSavePlayer(player) } to player.name
        }
        var saved = 0
        for ((future, name) in pending) {
            try {
                if (future.get()) {
                    saved++
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save player $name during server save" }
            }
        }
        return saved
    }
}
