package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position

/**
 * Transcripts of `Npc::canWalkTo`, `Npc::getRandomStep` and `Npc::onThinkWalk`
 * (src/creatures/npcs/npc.cpp:1080-1104, 1189-1236).
 */
class NpcWalkTest {

    private val npcPos = Position(100, 100, 7)
    private val north = Position(100, 99, 7)

    private fun npcWithType(
        world: NpcTestWorld,
        configure: NpcType.() -> Unit = {},
    ): Npc {
        val npcType = NpcType("Wanderer")
        npcType.configure()
        return Npc(id = 1, name = "Wanderer", position = npcPos, npcType = npcType, world = world)
    }

    private val deterministicShuffle: (MutableList<Direction>) -> Unit = { directions ->
        directions.clear()
        directions.addAll(listOf(Direction.South, Direction.North, Direction.West, Direction.East))
    }

    @Test
    fun `canWalkTo rejects every direction when the walk radius is zero`() {
        val world = NpcTestWorld()
        world.walkablePositions.add(north)
        val npc = npcWithType(world) { info.walkRadius = 0 }

        assertThat(npc.canWalkTo(npcPos, Direction.North)).isFalse()
    }

    @Test
    fun `canWalkTo rejects positions outside the walk zone around masterPos`() {
        val world = NpcTestWorld()
        world.walkablePositions.add(north)
        val npc = npcWithType(world) { info.walkRadius = 1 }

        npc.setMasterPos(Position(110, 110, 7))
        assertThat(npc.canWalkTo(npcPos, Direction.North)).isFalse()

        npc.setMasterPos(Position(100, 100, 7))
        assertThat(npc.canWalkTo(npcPos, Direction.North)).isTrue()
    }

    @Test
    fun `canWalkTo requires the destination tile to accept the npc`() {
        val world = NpcTestWorld()
        val npc = npcWithType(world)

        assertThat(npc.canWalkTo(npcPos, Direction.North)).isFalse()

        world.walkablePositions.add(north)
        assertThat(npc.canWalkTo(npcPos, Direction.North)).isTrue()
    }

    @Test
    fun `canWalkTo rejects floor change and teleport tiles unless the npc type allows floor change`() {
        val world = NpcTestWorld()
        world.walkablePositions.add(north)
        world.floorChangeOrTeleportPositions.add(north)
        val npc = npcWithType(world)

        assertThat(npc.canWalkTo(npcPos, Direction.North)).isFalse()

        val floorChanging = NpcType("Traveller")
        floorChanging.info.floorChange = true
        val npcWithFloorChange = Npc(
            id = 2,
            name = "Traveller",
            position = npcPos,
            npcType = floorChanging,
            world = world,
        )
        assertThat(npcWithFloorChange.canWalkTo(npcPos, Direction.North)).isTrue()
    }

    @Test
    fun `canWalkTo rejects height tiles`() {
        val world = NpcTestWorld()
        world.walkablePositions.add(north)
        world.heightPositions.add(north)
        val npc = npcWithType(world)

        assertThat(npc.canWalkTo(npcPos, Direction.North)).isFalse()
    }

    @Test
    fun `canWalkTo is false without an injected world`() {
        val npc = Npc(id = 1, name = "Wanderer", position = npcPos, npcType = NpcType("Wanderer"))

        assertThat(npc.canWalkTo(npcPos, Direction.North)).isFalse()
    }

    @Test
    fun `getRandomStep returns the first walkable direction of the shuffled cardinal list`() {
        val world = NpcTestWorld()
        world.walkablePositions.add(north)
        val npc = npcWithType(world)

        assertThat(npc.getRandomStep(deterministicShuffle)).isEqualTo(Direction.North)
    }

    @Test
    fun `getRandomStep returns null when no cardinal neighbour is walkable`() {
        val world = NpcTestWorld()
        val npc = npcWithType(world)

        assertThat(npc.getRandomStep(deterministicShuffle)).isNull()
    }

    @Test
    fun `onThinkWalk queues a random step once the walk interval elapses`() {
        val world = NpcTestWorld()
        world.walkablePositions.add(north)
        val npc = npcWithType(world) { info.walkInterval = 2000 }

        assertThat(npc.onThinkWalk(1000, deterministicShuffle)).isNull()
        assertThat(npc.listWalkDir).isEmpty()
        assertThat(npc.eventWalk).isZero()

        assertThat(npc.onThinkWalk(1000, deterministicShuffle)).isEqualTo(Direction.North)
        assertThat(npc.listWalkDir).containsExactly(Direction.North)
        assertThat(npc.eventWalk).isNotZero()

        assertThat(npc.onThinkWalk(1000, deterministicShuffle)).isNull()
        assertThat(npc.listWalkDir).containsExactly(Direction.North)
    }

    @Test
    fun `onThinkWalk waits while a player is interacting`() {
        val world = NpcTestWorld()
        world.walkablePositions.add(north)
        val player = Player(7, "Aria", 100, 100, position = Position(90, 100, 7))
        world.creaturesById[7] = player
        val npc = npcWithType(world) { info.walkInterval = 100 }
        npc.setPlayerInteraction(7)

        assertThat(npc.onThinkWalk(500, deterministicShuffle)).isNull()
        assertThat(npc.listWalkDir).isEmpty()
        assertThat(npc.eventWalk).isZero()
    }

    @Test
    fun `onThinkWalk is disabled when the walk interval or base speed is zero`() {
        val world = NpcTestWorld()
        world.walkablePositions.add(north)
        val npc = npcWithType(world) { info.walkInterval = 0 }

        assertThat(npc.onThinkWalk(10_000, deterministicShuffle)).isNull()

        val slow = NpcType("Statue")
        slow.info.walkInterval = 2000
        val frozen = Npc(id = 2, name = "Statue", position = npcPos, npcType = slow, world = world)
        frozen.setBaseSpeed(0)

        assertThat(frozen.onThinkWalk(10_000, deterministicShuffle)).isNull()
    }
}
