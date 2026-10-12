package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.persistence.InMemoryPlayerStorage

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
}
