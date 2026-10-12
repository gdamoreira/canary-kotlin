package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class DatabasePersistenceTest {

    @Test
    fun `persists player entity to sql database and retrieves matching record`() {
        val database = SqlitePlayerDatabase()
        database.initSchema()

        val player = Player(
            id = 77,
            name = "Ranger",
            maxHealth = 300,
            health = 280,
            maxMana = 100,
            mana = 90,
            capacity = 600.0,
            speed = 240,
            position = Position(32000, 32000, 7),
            experience = 15000,
            level = 15
        )

        database.savePlayer(player)

        val retrieved = database.loadPlayerByName("Ranger")
        assertThat(retrieved).isNotNull
        assertThat(retrieved?.id).isEqualTo(77L)
        assertThat(retrieved?.name).isEqualTo("Ranger")
        assertThat(retrieved?.health).isEqualTo(280)
        assertThat(retrieved?.level).isEqualTo(15)
        assertThat(retrieved?.position).isEqualTo(Position(32000, 32000, 7))
    }
}
