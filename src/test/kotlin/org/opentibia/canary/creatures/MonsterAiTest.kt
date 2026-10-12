package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

class MonsterAiTest {

    @Test
    fun `spawns monsters and executes AI step towards player`() {
        val map = GameMap()
        val spawnPos = Position(100, 100, 7)
        map.getOrCreateTile(spawnPos)
        map.getOrCreateTile(Position(100, 99, 7)) // North
        map.getOrCreateTile(Position(100, 98, 7)) // North North

        val spawnManager = SpawnManager(map)
        val spawner = spawnManager.addSpawner(
            monsterName = "Rat",
            centerPos = spawnPos,
            radius = 3,
            respawnSeconds = 60
        )

        val monster = spawner.spawn()
        assertThat(monster).isNotNull
        assertThat(monster?.position).isEqualTo(spawnPos)

        // Place player 2 tiles to the North
        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 98, 7))

        // Monster AI step towards nearest target
        MonsterAiEngine.think(monster!!, listOf(player), map)

        // Monster should move North towards player (to 100, 99, 7)
        assertThat(monster.position).isEqualTo(Position(100, 99, 7))
    }
}
