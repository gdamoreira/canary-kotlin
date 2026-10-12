package org.opentibia.canary.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class PersistenceTest {

    @Test
    fun `saves and loads player entity preserving stats and position`() {
        val storage = InMemoryPlayerStorage()
        val originalPlayer = Player(
            id = 42,
            name = "Aventador",
            maxHealth = 450,
            health = 410,
            maxMana = 150,
            mana = 120,
            capacity = 800.0,
            speed = 280,
            position = Position(32369, 32241, 7),
            experience = 64000,
            level = 25
        )

        storage.save(originalPlayer)

        val loadedPlayer = storage.loadByName("Aventador")
        assertThat(loadedPlayer).isNotNull
        assertThat(loadedPlayer?.id).isEqualTo(42L)
        assertThat(loadedPlayer?.name).isEqualTo("Aventador")
        assertThat(loadedPlayer?.health).isEqualTo(410)
        assertThat(loadedPlayer?.position).isEqualTo(Position(32369, 32241, 7))
        assertThat(loadedPlayer?.level).isEqualTo(25)
        assertThat(loadedPlayer?.experience).isEqualTo(64000L)
    }

    @Test
    fun `returns null when loading non-existent player`() {
        val storage = InMemoryPlayerStorage()
        val loaded = storage.loadByName("Ghost")
        assertThat(loaded).isNull()
    }
}
