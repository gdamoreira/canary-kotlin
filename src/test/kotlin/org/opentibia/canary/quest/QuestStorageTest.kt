package org.opentibia.canary.quest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class QuestStorageTest {

    @Test
    fun `manages player arbitrary storage key-values`() {
        val storageManager = PlayerStorageManager()
        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))

        // Default unset storage returns -1 (Canary convention)
        assertThat(storageManager.getStorage(player, 10001)).isEqualTo(-1)

        storageManager.setStorage(player, 10001, 1)
        assertThat(storageManager.getStorage(player, 10001)).isEqualTo(1)

        storageManager.setStorage(player, 10001, 5)
        assertThat(storageManager.getStorage(player, 10001)).isEqualTo(5)
    }

    @Test
    fun `evaluates quest mission progress and quest log entries`() {
        val storageManager = PlayerStorageManager()
        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))

        val quest = Quest(
            id = 50,
            name = "The Ancient Tombs",
            storageKey = 20001,
            missions = listOf(
                QuestMission(name = "Tomb of Haban", stateStorageValue = 1, description = "Find the ancient ruby."),
                QuestMission(name = "Tomb of Morgath", stateStorageValue = 2, description = "Defeat the guardian.")
            )
        )

        // Quest not started
        assertThat(quest.isStarted(player, storageManager)).isFalse()

        // Start mission 1
        storageManager.setStorage(player, 20001, 1)
        assertThat(quest.isStarted(player, storageManager)).isTrue()
        assertThat(quest.getCurrentMission(player, storageManager)?.name).isEqualTo("Tomb of Haban")

        // Advance to mission 2
        storageManager.setStorage(player, 20001, 2)
        assertThat(quest.getCurrentMission(player, storageManager)?.name).isEqualTo("Tomb of Morgath")
    }
}
