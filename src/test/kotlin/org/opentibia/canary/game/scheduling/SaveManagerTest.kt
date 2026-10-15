package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.persistence.InMemoryPlayerStorage
import org.opentibia.canary.persistence.PlayerStorage
import org.opentibia.canary.social.guild.Guild
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SaveManagerTest {

    @Test
    fun `executes atomic server save for all online players`() {
        val storage = InMemoryPlayerStorage()
        val saveManager = SaveManager(storage)

        val player1 = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))
        val player2 = Player(2, "Bob", 150, 150, position = Position(200, 200, 7))

        saveManager.registerOnlinePlayer(player1)
        saveManager.registerOnlinePlayer(player2)

        val savedCount = saveManager.saveAll()
        assertThat(savedCount).isEqualTo(2)

        assertThat(storage.loadByName("Alice")).isNotNull
        assertThat(storage.loadByName("Bob")).isNotNull
    }

    @Test
    fun `graceful shutdown saves players and unregisters online sessions`() {
        val storage = InMemoryPlayerStorage()
        val saveManager = SaveManager(storage)

        val player = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))
        saveManager.registerOnlinePlayer(player)

        val success = saveManager.shutdown()
        assertThat(success).isTrue()
        assertThat(saveManager.onlinePlayerCount).isEqualTo(0)
        assertThat(storage.loadByName("Alice")).isNotNull
    }

    @Test
    fun `save all persists players guilds map and kv in canary order`() {
        val events = mutableListOf<String>()
        val storage = RecordingPlayerStorage(events)
        val guild = Guild(1, "Dragons")
        val saveManager = SaveManager(
            storage,
            guilds = { listOf(guild) },
            guildSaver = { events.add("guild:${it.name}") },
            mapSaver = {
                events.add("map")
                true
            },
            keyValueSaver = {
                events.add("kv")
                true
            },
        )

        saveManager.registerOnlinePlayer(Player(1, "Alice", 100, 100, position = Position(100, 100, 7)))
        saveManager.registerOnlinePlayer(Player(2, "Bob", 100, 100, position = Position(100, 100, 7)))

        assertThat(saveManager.saveAll()).isEqualTo(2)
        assertThat(events).containsExactly("player:Alice", "player:Bob", "guild:Dragons", "map", "kv")
    }

    @Test
    fun `shutdown flush saves every online player once and is idempotent`() {
        val storage = CountingPlayerStorage()
        val saveManager = SaveManager(storage)
        saveManager.registerOnlinePlayer(Player(1, "Alice", 100, 100, position = Position(100, 100, 7)))
        saveManager.registerOnlinePlayer(Player(2, "Bob", 100, 100, position = Position(100, 100, 7)))

        assertThat(saveManager.shutdown()).isTrue()
        assertThat(storage.saveCount).isEqualTo(2)
        assertThat(saveManager.onlinePlayerCount).isZero()

        assertThat(saveManager.shutdown()).isTrue()
        assertThat(storage.saveCount).isEqualTo(2)
    }

    @Test
    fun `schedule all skips stale scheduled saves`() {
        val executor = QueuedExecutorService()
        val storage = CountingPlayerStorage()
        val saveManager = SaveManager(storage, saveAsync = true, saveExecutor = executor)
        saveManager.registerOnlinePlayer(Player(1, "Alice", 100, 100, position = Position(100, 100, 7)))

        saveManager.scheduleAll()
        saveManager.scheduleAll()
        executor.runAll()

        assertThat(storage.saveCount).isEqualTo(1)
    }

    @Test
    fun `schedule player saves only the newest generation`() {
        val executor = QueuedExecutorService()
        val storage = CountingPlayerStorage()
        val saveManager = SaveManager(storage, saveAsync = true, saveExecutor = executor)
        val player = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))
        saveManager.registerOnlinePlayer(player)

        assertThat(saveManager.savePlayer(player)).isTrue()
        assertThat(saveManager.savePlayer(player)).isTrue()
        executor.runAll()

        assertThat(storage.saveCount).isEqualTo(1)
    }

    @Test
    fun `save player outside the online set saves immediately`() {
        val storage = CountingPlayerStorage()
        val saveManager = SaveManager(storage, saveAsync = true)
        val player = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))

        assertThat(saveManager.savePlayer(player)).isTrue()

        assertThat(storage.saveCount).isEqualTo(1)
    }

    @Test
    fun `unregister keeps a newer registration for the same character`() {
        val storage = CountingPlayerStorage()
        val saveManager = SaveManager(storage)
        val staleSession = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))
        val newSession = Player(1, "Alice", 150, 150, position = Position(200, 200, 7))
        saveManager.registerOnlinePlayer(staleSession)
        saveManager.registerOnlinePlayer(newSession)

        saveManager.unregisterOnlinePlayer(staleSession)

        assertThat(saveManager.onlinePlayerCount).isEqualTo(1)
        assertThat(saveManager.saveAll()).isEqualTo(1)
        assertThat(storage.saveCount).isEqualTo(1)
    }

    @Test
    fun `save all runs players in parallel when an executor is configured`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val storage = BarrierPlayerStorage(CyclicBarrier(2))
            val saveManager = SaveManager(storage, saveExecutor = pool)
            saveManager.registerOnlinePlayer(Player(1, "Alice", 100, 100, position = Position(100, 100, 7)))
            saveManager.registerOnlinePlayer(Player(2, "Bob", 100, 100, position = Position(100, 100, 7)))

            assertThat(saveManager.saveAll()).isEqualTo(2)
        } finally {
            pool.shutdownNow()
        }
    }

    private class RecordingPlayerStorage(private val events: MutableList<String>) : PlayerStorage {
        override fun save(player: Player) {
            events.add("player:${player.name}")
        }

        override fun loadByName(name: String): Player? = null
    }

    private class CountingPlayerStorage : PlayerStorage {
        private val count = AtomicInteger(0)
        val saveCount: Int get() = count.get()

        override fun save(player: Player) {
            count.incrementAndGet()
        }

        override fun loadByName(name: String): Player? = null
    }

    private class BarrierPlayerStorage(private val barrier: CyclicBarrier) : PlayerStorage {
        override fun save(player: Player) {
            barrier.await(2, TimeUnit.SECONDS)
        }

        override fun loadByName(name: String): Player? = null
    }

    private class QueuedExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun execute(command: Runnable) {
            synchronized(tasks) {
                tasks.addLast(command)
            }
        }

        fun runAll() {
            while (true) {
                val task = synchronized(tasks) { tasks.removeFirstOrNull() } ?: break
                task.run()
            }
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> = synchronized(tasks) {
            val pending = tasks.toMutableList()
            tasks.clear()
            pending
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
    }
}
