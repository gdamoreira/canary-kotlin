package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.SaveManager
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.persistence.InMemoryPlayerStorage
import org.opentibia.canary.persistence.PlayerStorage

/**
 * Transcript tests for the session save rotation seams of `Game::addPlayer`
 * (src/game/game.cpp:11624) and `Game::removePlayer` (src/game/game.cpp:11631):
 * Canary's `SaveManager::saveAll` rotates the players of `g_game().getPlayers()`
 * (save_manager.cpp:31-52), so the port's session install/removal must feed the
 * `SaveManager` online set through the composition seams the listener binds.
 */
class GameSaveManagerRegistrationTest {

    private fun newGame(saveManager: SaveManager): Game =
        Game(GameMap(), GameDispatcher()).also { game ->
            game.registerOnlinePlayer = { player -> saveManager.registerOnlinePlayer(player) }
            game.unregisterOnlinePlayer = { player -> saveManager.unregisterOnlinePlayer(player) }
        }

    private fun player(id: Long, name: String = "Alice"): Player =
        Player(id = id, name = name, maxHealth = 100, health = 100, position = Position(100, 100, 7))

    @Test
    fun `addPlayer installs the session in the save rotation and removePlayer releases it`() {
        val saveManager = SaveManager(InMemoryPlayerStorage())
        val game = newGame(saveManager)
        val player = player(1L)

        game.addPlayer(player)
        assertThat(saveManager.onlinePlayerCount).isEqualTo(1)

        game.removePlayer(player)
        assertThat(saveManager.onlinePlayerCount).isZero()
    }

    @Test
    fun `save all persists the player installed through addPlayer`() {
        val storage: PlayerStorage = InMemoryPlayerStorage()
        val saveManager = SaveManager(storage)
        val game = newGame(saveManager)
        val player = player(1L)

        game.addPlayer(player)

        assertThat(saveManager.saveAll()).isEqualTo(1)
        assertThat(storage.loadByName("Alice")).isNotNull()
    }
}
