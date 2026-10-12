package org.opentibia.canary.game.movement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

class MovementTest {

    @Test
    fun `calculates step duration matching Canary formula`() {
        // Canary formula: (groundSpeed * 1000) / creatureSpeed
        // groundSpeed for normal grass = 100
        val durationFast = MovementEngine.calculateStepDuration(creatureSpeed = 200, groundSpeed = 100)
        assertThat(durationFast).isEqualTo(500L) // (100 * 1000) / 200

        val durationSlow = MovementEngine.calculateStepDuration(creatureSpeed = 100, groundSpeed = 100)
        assertThat(durationSlow).isEqualTo(1000L) // (100 * 1000) / 100
    }

    @Test
    fun `translates Direction to coordinate offsets`() {
        val start = Position(100, 100, 7)
        assertThat(Direction.North.offset(start)).isEqualTo(Position(100, 99, 7))
        assertThat(Direction.East.offset(start)).isEqualTo(Position(101, 100, 7))
        assertThat(Direction.South.offset(start)).isEqualTo(Position(100, 101, 7))
        assertThat(Direction.West.offset(start)).isEqualTo(Position(99, 100, 7))
    }

    @Test
    fun `validates and performs creature step on GameMap`() {
        val map = GameMap()
        val startPos = Position(500, 500, 7)
        val targetPos = Position(501, 500, 7) // East

        map.getOrCreateTile(startPos)
        map.getOrCreateTile(targetPos)

        val player = Player(
            id = 1,
            name = "Hero",
            maxHealth = 100,
            health = 100,
            speed = 200,
            position = startPos
        )

        val success = MovementEngine.move(player, Direction.East, map)
        assertThat(success).isTrue()
        assertThat(player.position).isEqualTo(targetPos)
    }
}
