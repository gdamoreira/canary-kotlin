package org.opentibia.canary.creatures.spawns

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position

class SpawnNpcTest {

    private var now = 0L
    private val scheduler = FakeSpawnScheduler { now }
    private val factory = FakeNpcFactory()
    private val effects = mutableListOf<Pair<Position, Int>>()

    private fun spawnAt(center: Position, vararg types: NpcType): SpawnNpc =
        SpawnNpc(
            centerPos = center,
            radius = 3,
            npcTypeLookup = npcTypeLookup(*types),
            npcFactory = factory,
            scheduler = scheduler,
            now = { now },
            addMagicEffect = { pos, effect -> effects.add(pos to effect) },
        )

    @Test
    fun `spawn zone radius is inclusive on x and y and ignores z with radius -1 meaning unbounded`() {
        val center = Position(100, 100, 7)

        assertThat(SpawnsNpc.isInZone(center, -1, Position(0, 0, 0))).isTrue
        assertThat(SpawnsNpc.isInZone(center, 3, Position(103, 103, 7))).isTrue
        assertThat(SpawnsNpc.isInZone(center, 3, Position(97, 97, 7))).isTrue
        assertThat(SpawnsNpc.isInZone(center, 3, Position(104, 100, 7))).isFalse
        assertThat(SpawnsNpc.isInZone(center, 3, Position(100, 96, 7))).isFalse
        assertThat(SpawnsNpc.isInZone(center, 3, Position(100, 100, 12))).isTrue
    }

    @Test
    fun `addNpc keeps the smallest interval for the check tick and creates one block per npc`() {
        val shopkeeper = NpcType("Shopkeeper")
        val pos = Position(100, 100, 7)
        val spawn = spawnAt(pos, shopkeeper)

        assertThat(spawn.getInterval()).isEqualTo(60000L)

        assertThat(spawn.addNpc("Shopkeeper", pos, Direction.North, 30000L)).isTrue
        assertThat(spawn.getInterval()).isEqualTo(30000L)

        assertThat(spawn.addNpc("Shopkeeper", pos, Direction.South, 45000L)).isTrue
        assertThat(spawn.getInterval()).isEqualTo(30000L)
        assertThat(spawn.spawnNpcMap).hasSize(2)
        assertThat(spawn.spawnNpcMap[1]!!.npcType).isSameAs(shopkeeper)
        assertThat(spawn.spawnNpcMap[1]!!.direction).isEqualTo(Direction.North)
        assertThat(spawn.spawnNpcMap[2]!!.direction).isEqualTo(Direction.South)

        assertThat(spawn.addNpc("Missing", pos, Direction.North, 30000L)).isFalse
        assertThat(spawn.spawnNpcMap).hasSize(2)
    }

    @Test
    fun `npc respawn starts three teleport ticks once its interval elapsed`() {
        val shopkeeper = NpcType("Shopkeeper")
        val pos = Position(100, 100, 7)
        val spawn = spawnAt(pos, shopkeeper)
        spawn.addNpc("Shopkeeper", pos, Direction.North, 30000L)

        spawn.startup()
        val first = spawn.spawnedNpcMap[1]!!.first()
        assertThat(first.position).isEqualTo(pos)

        now = 500
        first.setRemoved()
        spawn.startSpawnNpcCheck()

        now = 30500
        scheduler.runDue()
        assertThat(spawn.spawnedNpcMap).isEmpty()
        assertThat(factory.created).hasSize(1)

        now = 60500
        scheduler.runDue()
        assertThat(factory.created).hasSize(1)
        assertThat(effects).hasSize(1)

        now = 61900
        scheduler.runDue()
        now = 63300
        scheduler.runDue()
        assertThat(factory.created).hasSize(1)
        assertThat(effects).hasSize(3)

        now = 64700
        scheduler.runDue()
        assertThat(factory.created).hasSize(2)
        assertThat(spawn.spawnedNpcMap[1]!!.first().position).isEqualTo(pos)
    }

    @Test
    fun `npc postpones respawn while a player is on the spawn position`() {
        val shopkeeper = NpcType("Shopkeeper")
        val pos = Position(100, 100, 7)
        var playerPresent = false
        val spawn = SpawnNpc(
            centerPos = pos,
            radius = 3,
            npcTypeLookup = npcTypeLookup(shopkeeper),
            npcFactory = factory,
            scheduler = scheduler,
            now = { now },
            findPlayer = { playerPresent },
        )
        spawn.addNpc("Shopkeeper", pos, Direction.North, 30000L)
        spawn.startup()
        val first = spawn.spawnedNpcMap[1]!!.first()

        now = 500
        first.setRemoved()
        spawn.startSpawnNpcCheck()

        now = 30500
        scheduler.runDue()
        playerPresent = true
        now = 60500
        scheduler.runDue()
        assertThat(spawn.spawnedNpcMap).isEmpty()
        assertThat(factory.created).hasSize(1)

        playerPresent = false
        now = 90500
        scheduler.runDue()
        now = 91900
        scheduler.runDue()
        now = 93300
        scheduler.runDue()
        now = 94700
        scheduler.runDue()
        assertThat(factory.created).hasSize(2)
        assertThat(spawn.spawnedNpcMap[1]!!).hasSize(1)
    }

    @Test
    fun `isInSpawnRange applies despawn radius then z range against the block position`() {
        val shopkeeper = NpcType("Shopkeeper")
        val pos = Position(100, 100, 7)
        val spawn = spawnAt(pos, shopkeeper)
        spawn.addNpc("Shopkeeper", pos, Direction.North, 30000L)
        spawn.startup()
        val npc = spawn.spawnedNpcMap[1]!!.first()

        assertThat(spawn.isInSpawnRange(npc, Position(100, 100, 7), 50, 2)).isTrue
        assertThat(spawn.isInSpawnRange(npc, Position(150, 120, 7), 50, 2)).isTrue
        assertThat(spawn.isInSpawnRange(npc, Position(151, 100, 7), 50, 2)).isFalse
        assertThat(spawn.isInSpawnRange(npc, Position(100, 100, 10), 50, 2)).isFalse
        assertThat(spawn.isInSpawnRange(npc, Position(500, 500, 7), 0, 2)).isTrue
        assertThat(spawn.isInSpawnRange(npc, Position(100, 100, 30), 50, 0)).isTrue
    }
}
