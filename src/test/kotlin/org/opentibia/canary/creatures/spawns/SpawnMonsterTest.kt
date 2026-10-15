package org.opentibia.canary.creatures.spawns

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import kotlin.random.Random

class SpawnMonsterTest {

    private var now = 0L
    private val scheduler = FakeSpawnScheduler { now }
    private val factory = FakeMonsterFactory()
    private val effects = mutableListOf<Pair<Position, Int>>()

    private fun spawnAt(center: Position, vararg types: MonsterType): SpawnMonster =
        SpawnMonster(
            centerPos = center,
            radius = 3,
            monsterTypeLookup = monsterTypeLookup(*types),
            monsterFactory = factory,
            scheduler = scheduler,
            now = { now },
            addMagicEffect = { pos, effect -> effects.add(pos to effect) },
        )

    @Test
    fun `spawn zone radius is inclusive on x and y and ignores z with radius -1 meaning unbounded`() {
        val center = Position(100, 100, 7)

        assertThat(SpawnsMonster.isInZone(center, -1, Position(0, 0, 0))).isTrue
        assertThat(SpawnsMonster.isInZone(center, 3, Position(103, 103, 7))).isTrue
        assertThat(SpawnsMonster.isInZone(center, 3, Position(97, 97, 7))).isTrue
        assertThat(SpawnsMonster.isInZone(center, 3, Position(104, 100, 7))).isFalse
        assertThat(SpawnsMonster.isInZone(center, 3, Position(100, 96, 7))).isFalse
        assertThat(SpawnsMonster.isInZone(center, 3, Position(100, 100, 12))).isTrue
    }

    @Test
    fun `addMonster clamps spawntime to one second or one day and folds it into the check interval gcd`() {
        val rat = MonsterType("Rat", isBlockable = true)

        val fast = spawnAt(Position(100, 100, 7), rat)
        assertThat(fast.getInterval()).isEqualTo(30000L)
        assertThat(fast.addMonster("Rat", Position(100, 100, 7), Direction.North, scheduleInterval = 2000L)).isTrue
        assertThat(fast.getInterval()).isEqualTo(2000L)
        assertThat(fast.monsterSpawnMap[1]!!.interval).isEqualTo(2000L)

        val tooFast = spawnAt(Position(200, 200, 7), rat)
        assertThat(tooFast.addMonster("Rat", Position(200, 200, 7), Direction.North, scheduleInterval = 500L)).isTrue
        assertThat(tooFast.monsterSpawnMap[1]!!.interval).isEqualTo(MONSTER_MINSPAWN_INTERVAL)

        val tooSlow = spawnAt(Position(300, 300, 7), rat)
        assertThat(tooSlow.addMonster("Rat", Position(300, 300, 7), Direction.North, scheduleInterval = 999_999_999L)).isTrue
        assertThat(tooSlow.monsterSpawnMap[1]!!.interval).isEqualTo(MONSTER_MAXSPAWN_INTERVAL)
        assertThat(tooSlow.getInterval()).isEqualTo(30000L)
    }

    @Test
    fun `addMonster merges same-position blocks and rejects duplicate or boss-mixed types`() {
        val rat = MonsterType("Rat", isBlockable = true)
        val caveRat = MonsterType("Cave Rat", isBlockable = true)
        val dragon = MonsterType("Dragon", isBlockable = true, isBoss = true)
        val pos = Position(100, 100, 7)
        val spawn = spawnAt(pos, rat, caveRat, dragon)

        assertThat(spawn.addMonster("Rat", pos, Direction.North, 2000L)).isTrue
        assertThat(spawn.addMonster("Cave Rat", pos, Direction.East, 3000L)).isTrue
        assertThat(spawn.monsterSpawnMap).hasSize(1)
        assertThat(spawn.monsterSpawnMap[1]!!.monsterTypes.keys).containsExactly(rat, caveRat)
        assertThat(spawn.monsterSpawnMap[1]!!.direction).isEqualTo(Direction.East)

        assertThat(spawn.addMonster("Rat", pos, Direction.North, 2000L)).isFalse
        assertThat(spawn.addMonster("Dragon", pos, Direction.North, 2000L)).isFalse

        val bossSpawn = spawnAt(Position(300, 300, 7), rat, dragon)
        assertThat(bossSpawn.addMonster("Dragon", Position(300, 300, 7), Direction.North, 2000L)).isTrue
        assertThat(bossSpawn.addMonster("Rat", Position(300, 300, 7), Direction.North, 2000L)).isFalse
    }

    @Test
    fun `weighted selection always picks a boss and otherwise follows the weights`() {
        val common = MonsterType("Rat")
        val rare = MonsterType("Dragon")
        val block = MonsterSpawnBlock(Position(0, 0, 7))
        block.monsterTypes[common] = 3
        block.monsterTypes[rare] = 1

        val random = Random(1234)
        var commonCount = 0
        var rareCount = 0
        repeat(1000) {
            when (block.getMonsterType(random)) {
                common -> commonCount++
                rare -> rareCount++
            }
        }
        assertThat(commonCount).isGreaterThan(rareCount)
        assertThat(rareCount).isGreaterThan(0)

        val boss = MonsterType("Boss", isBoss = true)
        val bossBlock = MonsterSpawnBlock(Position(0, 0, 7))
        bossBlock.monsterTypes[boss] = 1
        bossBlock.monsterTypes[common] = 100
        assertThat(bossBlock.getMonsterType(Random(1))).isSameAs(boss)
    }

    @Test
    fun `blockable monster respawns after its spawntime once cleanup observes the removal`() {
        val rat = MonsterType("Rat", isBlockable = true)
        val pos = Position(100, 100, 7)
        val spawn = spawnAt(pos, rat)
        spawn.addMonster("Rat", pos, Direction.North, 2000L)

        spawn.startup()
        val first = spawn.spawnedMonsterMap[1]
        assertThat(first).isNotNull
        assertThat(first!!.position).isEqualTo(pos)

        now = 500
        first.setRemoved()
        spawn.startSpawnMonsterCheck()
        assertThat(scheduler.pendingCount).isEqualTo(1)

        now = 2500
        scheduler.runDue()
        assertThat(spawn.spawnedMonsterMap).isEmpty()
        assertThat(factory.created).hasSize(1)

        now = 4500
        scheduler.runDue()
        val second = spawn.spawnedMonsterMap[1]
        assertThat(second).isNotNull
        assertThat(second!!.position).isEqualTo(pos)
        assertThat(second).isNotSameAs(first)
        assertThat(factory.created).hasSize(2)
    }

    @Test
    fun `blockable monster postpones respawn while a player is on the spawn position`() {
        val rat = MonsterType("Rat", isBlockable = true)
        val pos = Position(100, 100, 7)
        var playerPresent = false
        val spawn = SpawnMonster(
            centerPos = pos,
            radius = 3,
            monsterTypeLookup = monsterTypeLookup(rat),
            monsterFactory = factory,
            scheduler = scheduler,
            now = { now },
            findPlayer = { playerPresent },
        )
        spawn.addMonster("Rat", pos, Direction.North, 2000L)
        spawn.startup()
        val first = spawn.spawnedMonsterMap[1]!!

        now = 500
        first.setRemoved()
        spawn.startSpawnMonsterCheck()

        playerPresent = true
        now = 2500
        scheduler.runDue()
        now = 4500
        scheduler.runDue()
        assertThat(factory.created).hasSize(1)

        playerPresent = false
        now = 6500
        scheduler.runDue()
        assertThat(factory.created).hasSize(2)
        assertThat(spawn.spawnedMonsterMap[1]!!.position).isEqualTo(pos)
    }

    @Test
    fun `unblockable monster spawns after three teleport effect ticks`() {
        val demon = MonsterType("Demon", isBlockable = false)
        val pos = Position(100, 100, 7)
        val spawn = spawnAt(pos, demon)
        spawn.addMonster("Demon", pos, Direction.North, 30000L)
        spawn.startup()
        val first = spawn.spawnedMonsterMap[1]!!

        now = 500
        first.setRemoved()
        spawn.startSpawnMonsterCheck()

        now = 30500
        scheduler.runDue()
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
        assertThat(effects).allSatisfy { effect -> assertThat(effect.second).isEqualTo(CONST_ME_TELEPORT) }
        assertThat(effects).allSatisfy { effect -> assertThat(effect.first).isEqualTo(pos) }

        now = 64700
        scheduler.runDue()
        assertThat(factory.created).hasSize(2)
        assertThat(spawn.spawnedMonsterMap[1]!!.position).isEqualTo(pos)
    }

    @Test
    fun `removeMonsters invalidates pending scheduled spawns through the generation guard`() {
        val rat = MonsterType("Rat", isBlockable = true)
        val spawn = spawnAt(Position(100, 100, 7), rat)
        spawn.addMonster("Rat", Position(100, 100, 7), Direction.North, 2000L)

        spawn.startup(delayed = true)
        assertThat(scheduler.pendingCount).isEqualTo(1)

        spawn.removeMonsters()
        scheduler.runDue()

        assertThat(factory.created).isEmpty()
        assertThat(spawn.spawnedMonsterMap).isEmpty()
        assertThat(spawn.monsterSpawnMap).isEmpty()
    }

    @Test
    fun `isInSpawnRange applies despawn radius then z range against the block position`() {
        val rat = MonsterType("Rat", isBlockable = true)
        val pos = Position(100, 100, 7)
        val spawn = spawnAt(pos, rat)
        spawn.addMonster("Rat", pos, Direction.North, 2000L)
        spawn.startup()
        val monster = spawn.spawnedMonsterMap[1]!!

        assertThat(spawn.isInSpawnRange(monster, Position(100, 100, 7), 50, 2)).isTrue
        assertThat(spawn.isInSpawnRange(monster, Position(150, 120, 7), 50, 2)).isTrue
        assertThat(spawn.isInSpawnRange(monster, Position(151, 100, 7), 50, 2)).isFalse
        assertThat(spawn.isInSpawnRange(monster, Position(100, 100, 9), 50, 2)).isTrue
        assertThat(spawn.isInSpawnRange(monster, Position(100, 100, 10), 50, 2)).isFalse
        assertThat(spawn.isInSpawnRange(monster, Position(500, 500, 7), 0, 2)).isTrue
        assertThat(spawn.isInSpawnRange(monster, Position(100, 100, 30), 50, 0)).isTrue
    }

    @Test
    fun `isInSpawnRange returns true for creatures not owned by the spawn`() {
        val spawn = spawnAt(Position(100, 100, 7), MonsterType("Rat", isBlockable = true))
        val stranger = Monster(99L, "Stranger", 100, 100, position = Position(0, 0, 7))

        assertThat(stranger.isRemoved()).isFalse
        assertThat(spawn.isInSpawnRange(stranger, Position(1000, 1000, 7), 50, 2)).isTrue
    }

    @Test
    fun `startSpawnMonsterCheck arms only one pending check and stopEvent cancels it`() {
        val spawn = spawnAt(Position(100, 100, 7), MonsterType("Rat", isBlockable = true))

        spawn.startSpawnMonsterCheck()
        spawn.startSpawnMonsterCheck()
        assertThat(scheduler.pendingCount).isEqualTo(1)

        spawn.stopEvent()
        assertThat(scheduler.pendingCount).isEqualTo(0)
    }

    @Test
    fun `spawning dispatches onSpawn once per new monster with the block position`() {
        // spawn_monster.cpp:220: `monster->onSpawn(sb.pos)` after the successful
        // placement, direction and spawn-owner wiring.
        val rat = MonsterType("Rat", isBlockable = true)
        val pos = Position(100, 100, 7)
        val spawned = mutableListOf<Pair<Monster, Position>>()
        val spawn = SpawnMonster(
            centerPos = pos,
            radius = 3,
            monsterTypeLookup = monsterTypeLookup(rat),
            monsterFactory = factory,
            scheduler = scheduler,
            now = { now },
            onMonsterSpawn = { monster, position -> spawned.add(monster to position) },
        )
        spawn.addMonster("Rat", pos, Direction.North, 2000L)

        spawn.startup()

        assertThat(spawned).containsExactly(factory.created.single() to pos)

        val first = spawn.spawnedMonsterMap[1]!!
        now = 500
        first.setRemoved()
        spawn.startSpawnMonsterCheck()
        now = 2500
        scheduler.runDue()
        now = 4500
        scheduler.runDue()

        assertThat(spawned).hasSize(2)
        assertThat(spawned[1].first).isSameAs(factory.created[1])
        assertThat(spawned[1].second).isEqualTo(pos)
    }

    @Test
    fun `a monster that cannot enter the world never dispatches onSpawn`() {
        val rat = MonsterType("Rat", isBlockable = true)
        val pos = Position(100, 100, 7)
        val spawned = mutableListOf<Monster>()
        val spawn = SpawnMonster(
            centerPos = pos,
            radius = 3,
            monsterTypeLookup = monsterTypeLookup(rat),
            monsterFactory = { _, _, _ -> null },
            scheduler = scheduler,
            now = { now },
            onMonsterSpawn = { monster, _ -> spawned.add(monster) },
        )
        spawn.addMonster("Rat", pos, Direction.North, 2000L)

        spawn.startup()

        assertThat(spawned).isEmpty()
        assertThat(spawn.spawnedMonsterMap).isEmpty()
    }
}
