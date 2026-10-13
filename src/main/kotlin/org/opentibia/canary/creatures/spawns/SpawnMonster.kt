package org.opentibia.canary.creatures.spawns

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/** `NONBLOCKABLE_SPAWN_MONSTER_INTERVAL` (src/creatures/monsters/spawns/spawn_monster.hpp:97). */
const val NONBLOCKABLE_SPAWN_MONSTER_INTERVAL = 1400L

/** `MONSTER_MINSPAWN_INTERVAL` (src/creatures/monsters/spawns/spawn_monster.cpp:26). */
const val MONSTER_MINSPAWN_INTERVAL = 1000L

/** `MONSTER_MAXSPAWN_INTERVAL` (src/creatures/monsters/spawns/spawn_monster.cpp:27). */
const val MONSTER_MAXSPAWN_INTERVAL = 86400000L

/** `CONST_ME_TELEPORT` (src/utils/utils_definitions.hpp:71). */
const val CONST_ME_TELEPORT = 11

/**
 * Port of `MonsterType` limited to what the spawn engine consumes
 * (src/creatures/monsters/monsters.hpp): name, `info.isBlockable`, `isBoss()`
 * and `canSpawn(position)`.
 */
class MonsterType(
    val name: String,
    val isBlockable: Boolean = false,
    val isBoss: Boolean = false,
    private val canSpawnAt: (Position) -> Boolean = { true },
) {
    /** `MonsterType::canSpawn` (src/creatures/monsters/monsters.cpp:35). */
    fun canSpawn(pos: Position): Boolean = canSpawnAt(pos)
}

/** `g_monsters().getMonsterType(name)` (src/creatures/monsters/monsters.hpp). */
fun interface MonsterTypeLookup {
    fun getMonsterType(name: String): MonsterType?
}

/** `std::make_shared<Monster>(monsterType)` (src/creatures/monsters/spawns/spawn_monster.cpp:199). */
fun interface MonsterFactory {
    fun create(monsterType: MonsterType, position: Position): Monster
}

/** `spawnBlock_t` (src/creatures/monsters/spawns/spawn_monster.hpp:19). */
class MonsterSpawnBlock(
    var pos: Position,
    var direction: Direction = Direction.North,
    var lastSpawn: Long = 0,
    var interval: Long = 0,
) {
    val monsterTypes: MutableMap<MonsterType, Int> = linkedMapOf()

    /** `spawnBlock_t::getMonsterType` (src/creatures/monsters/spawns/spawn_monster.cpp:477). */
    fun getMonsterType(random: Random): MonsterType? {
        if (monsterTypes.isEmpty()) {
            return null
        }

        if (monsterTypes.size == 1) {
            return monsterTypes.keys.first()
        }

        var totalWeight = 0
        for ((monsterType, weight) in monsterTypes) {
            if (monsterType.isBoss) {
                if (monsterTypes.size > 1) {
                    logger.warn { "[SpawnMonster] Boss monster ${monsterType.name} has been added to spawn block with other monsters. This is not allowed." }
                }
                return monsterType
            }
            totalWeight += weight
        }
        if (totalWeight == 0) {
            return null
        }

        var randomWeight = random.nextInt(totalWeight)
        val orderedMonsterTypes = monsterTypes.entries.sortedByDescending { it.value }
        for ((monsterType, weight) in orderedMonsterTypes) {
            if (randomWeight < weight) {
                return monsterType
            }
            randomWeight -= weight
        }
        return null
    }

    /** `spawnBlock_t::hasBoss` (src/creatures/monsters/spawns/spawn_monster.cpp:526). */
    fun hasBoss(): Boolean = monsterTypes.keys.any { it.isBoss }
}

/**
 * Port of `SpawnMonster` (src/creatures/monsters/spawns/spawn_monster.hpp:30).
 *
 * The world is provided through [monsterFactory] (creature creation),
 * [monsterTypeLookup] (`g_monsters()`), [scheduler] (`g_dispatcher`) and
 * [findPlayer] (`SpawnMonster::findPlayer`). [now] mirrors `OTSYS_TIME()`.
 */
class SpawnMonster(
    val centerPos: Position,
    val radius: Int,
    private val monsterTypeLookup: MonsterTypeLookup,
    private val monsterFactory: MonsterFactory,
    private val scheduler: SpawnScheduler,
    private val now: () -> Long = System::currentTimeMillis,
    private val findPlayer: (Position) -> Boolean = { false },
    private val random: Random = Random.Default,
    private val rateSpawn: Int = 1,
    private val boostedMonsterName: String = "",
    private val eventsSpawnMonsterSchedule: Int = 100,
    private val addMagicEffect: (Position, Int) -> Unit = { _, _ -> },
) {
    private var interval: Long = 30000
    private var checkSpawnMonsterEvent: SpawnTask? = null
    private var spawnGeneration = 0L

    internal val spawnedMonsterMap = linkedMapOf<Int, Monster>()
    internal val monsterSpawnMap = linkedMapOf<Int, MonsterSpawnBlock>()

    /** `SpawnMonster::getInterval` (spawn_monster.hpp:46). */
    fun getInterval(): Long = interval

    /** `SpawnMonster::addMonster` (src/creatures/monsters/spawns/spawn_monster.cpp:364). */
    fun addMonster(name: String, pos: Position, dir: Direction, scheduleInterval: Long, weight: Int = 1): Boolean {
        val monsterType = monsterTypeLookup.getMonsterType(name)
        if (monsterType == null) {
            logger.error { "Can not find $name" }
            return false
        }

        val boostedRate = if (name == boostedMonsterName) 2 else 1
        var adjustedInterval = scheduleInterval * 100 / maxOf(1L, rateSpawn.toLong() * boostedRate * eventsSpawnMonsterSchedule)
        if (adjustedInterval < MONSTER_MINSPAWN_INTERVAL) {
            logger.warn {
                "[SpawnsMonster::addMonster] - $name ${pos.toString()} spawntime cannot be less than ${MONSTER_MINSPAWN_INTERVAL / 1000} seconds, set to ${MONSTER_MINSPAWN_INTERVAL / 1000} by default."
            }
            adjustedInterval = MONSTER_MINSPAWN_INTERVAL
        } else if (adjustedInterval > MONSTER_MAXSPAWN_INTERVAL) {
            logger.warn {
                "[SpawnsMonster::addMonster] - $name ${pos.toString()} spawntime can not be more than ${MONSTER_MAXSPAWN_INTERVAL / 1000} seconds, set to ${MONSTER_MAXSPAWN_INTERVAL / 1000} by default"
            }
            adjustedInterval = MONSTER_MAXSPAWN_INTERVAL
        }
        interval = gcd(interval, adjustedInterval)

        var spawnMonsterId = monsterSpawnMap.size + 1
        var sb: MonsterSpawnBlock? = null
        for ((id, maybeSb) in monsterSpawnMap) {
            if (maybeSb.pos == pos) {
                sb = maybeSb
                spawnMonsterId = id
                break
            }
        }

        if (sb != null) {
            if (sb.monsterTypes.containsKey(monsterType)) {
                logger.error { "[SpawnMonster] Monster $name already exists in spawn block at ${pos.toString()}" }
                return false
            }
            if (monsterType.isBoss && sb.monsterTypes.isNotEmpty()) {
                logger.error { "[SpawnMonster] Boss monster $name has been added to spawn block with other monsters. This is not allowed." }
                return false
            }
            if (sb.hasBoss()) {
                logger.error { "[SpawnMonster] Monster $name has been added to spawn block with a boss. This is not allowed." }
                return false
            }
        }
        if (sb == null) {
            sb = MonsterSpawnBlock(pos)
            monsterSpawnMap[spawnMonsterId] = sb
        }
        sb.monsterTypes[monsterType] = weight
        sb.pos = pos
        sb.direction = dir
        sb.interval = adjustedInterval
        sb.lastSpawn = 0
        return true
    }

    /** `SpawnMonster::startup` (src/creatures/monsters/spawns/spawn_monster.cpp:224). */
    fun startup(delayed: Boolean = false) {
        val generation = spawnGeneration
        for ((spawnMonsterId, sb) in monsterSpawnMap) {
            val monsterType = sb.getMonsterType(random) ?: continue
            if (delayed) {
                val weakSelf = WeakReference(this)
                scheduler.schedule(0) {
                    weakSelf.get()?.scheduleSpawn(spawnMonsterId, monsterType, 0, true, generation)
                }
            } else {
                scheduleSpawn(spawnMonsterId, monsterType, 0, true, generation)
            }
        }
    }

    /** `SpawnMonster::startSpawnMonsterCheck` (src/creatures/monsters/spawns/spawn_monster.cpp:165). */
    fun startSpawnMonsterCheck() {
        if (checkSpawnMonsterEvent != null) {
            return
        }
        val weakSelf = WeakReference(this)
        checkSpawnMonsterEvent = scheduler.schedule(getInterval()) {
            weakSelf.get()?.checkSpawnMonster()
        }
    }

    /** `SpawnMonster::stopEvent` (src/creatures/monsters/spawns/spawn_monster.cpp:469). */
    fun stopEvent() {
        ++spawnGeneration
        checkSpawnMonsterEvent?.cancel()
        checkSpawnMonsterEvent = null
    }

    /** `SpawnMonster::checkSpawnMonster` (src/creatures/monsters/spawns/spawn_monster.cpp:271). */
    private fun checkSpawnMonster() {
        if (checkSpawnMonsterEvent == null) {
            return
        }

        checkSpawnMonsterEvent = null
        cleanup()

        for ((spawnMonsterId, sb) in monsterSpawnMap) {
            if (spawnedMonsterMap.containsKey(spawnMonsterId)) {
                continue
            }

            val monsterType = sb.getMonsterType(random) ?: continue
            if (!monsterType.canSpawn(sb.pos) || (monsterType.isBlockable && findPlayer(sb.pos))) {
                sb.lastSpawn = now()
                continue
            }
            if (now() < sb.lastSpawn + sb.interval) {
                continue
            }

            if (monsterType.isBlockable) {
                spawnMonster(spawnMonsterId, sb, monsterType)
            } else {
                scheduleSpawn(spawnMonsterId, monsterType, 3 * NONBLOCKABLE_SPAWN_MONSTER_INTERVAL, false, spawnGeneration)
            }
        }

        if (spawnedMonsterMap.size < monsterSpawnMap.size) {
            startSpawnMonsterCheck()
        }
    }

    /** `SpawnMonster::removeMonster` (src/creatures/monsters/spawns/spawn_monster.cpp:430). */
    fun removeMonster(monster: Monster) {
        val spawnMonsterId = spawnedMonsterMap.entries.firstOrNull { it.value === monster }?.key ?: return
        spawnedMonsterMap.remove(spawnMonsterId)
    }

    /** `SpawnMonster::removeMonsters` (src/creatures/monsters/spawns/spawn_monster.cpp:441). */
    fun removeMonsters() {
        ++spawnGeneration
        monsterSpawnMap.clear()
        spawnedMonsterMap.clear()
    }

    /** `SpawnMonster::isInSpawnMonsterZone` (src/creatures/monsters/spawns/spawn_monster.cpp:191). */
    fun isInSpawnMonsterZone(pos: Position): Boolean = SpawnsMonster.isInZone(centerPos, radius, pos)

    /**
     * `Monster::isInSpawnRange` (src/creatures/monsters/monster.cpp:3322) for a
     * creature owned by this spawn. The block position plays the role of
     * `masterPos`; creatures that were not spawned here are treated like a
     * creature whose `spawnMonster` weak pointer expired.
     */
    fun isInSpawnRange(monster: Monster, pos: Position, despawnRadius: Int, despawnRange: Int): Boolean {
        val sb = blockOf(monster) ?: return true

        if (despawnRadius == 0) {
            return true
        }

        if (!SpawnsMonster.isInZone(sb.pos, despawnRadius, pos)) {
            return false
        }

        if (despawnRange == 0) {
            return true
        }

        return abs(pos.z - sb.pos.z) <= despawnRange
    }

    internal fun blockOf(monster: Monster): MonsterSpawnBlock? {
        val spawnMonsterId = spawnedMonsterMap.entries.firstOrNull { it.value === monster }?.key ?: return null
        return monsterSpawnMap[spawnMonsterId]
    }

    private fun scheduleSpawn(spawnMonsterId: Int, monsterType: MonsterType, remainingInterval: Long, startup: Boolean, generation: Long) {
        if (generation != spawnGeneration) {
            return
        }

        val sb = monsterSpawnMap[spawnMonsterId] ?: return

        if (remainingInterval <= 0) {
            spawnMonster(spawnMonsterId, sb, monsterType, startup)
        } else {
            addMagicEffect(sb.pos, CONST_ME_TELEPORT)
            val weakSelf = WeakReference(this)
            scheduler.schedule(NONBLOCKABLE_SPAWN_MONSTER_INTERVAL) {
                weakSelf.get()?.scheduleSpawn(spawnMonsterId, monsterType, remainingInterval - NONBLOCKABLE_SPAWN_MONSTER_INTERVAL, startup, generation)
            }
        }
    }

    private fun spawnMonster(spawnMonsterId: Int, sb: MonsterSpawnBlock, monsterType: MonsterType, startup: Boolean = false): Boolean {
        if (spawnedMonsterMap.containsKey(spawnMonsterId)) {
            return false
        }

        val monster = monsterFactory.create(monsterType, sb.pos)
        monster.direction = sb.direction
        spawnedMonsterMap[spawnMonsterId] = monster
        sb.lastSpawn = now()
        return true
    }

    /** `SpawnMonster::cleanup` (src/creatures/monsters/spawns/spawn_monster.cpp:345). */
    fun cleanup() {
        val iterator = spawnedMonsterMap.entries.iterator()
        while (iterator.hasNext()) {
            val (spawnMonsterId, monster) = iterator.next()
            if (monster.isRemoved()) {
                monsterSpawnMap[spawnMonsterId]?.lastSpawn = now()
                iterator.remove()
            }
        }
    }

    private fun gcd(a: Long, b: Long): Long {
        var left = a
        var right = b
        while (right != 0L) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left
    }
}

/** Port of `SpawnsMonster` (src/creatures/monsters/spawns/spawn_monster.hpp:78). */
class SpawnsMonster {
    private val spawnMonsterList = mutableListOf<SpawnMonster>()

    internal var loaded = false
    internal var started = false

    fun isStarted(): Boolean = started

    fun isLoaded(): Boolean = loaded

    fun getspawnMonsterList(): List<SpawnMonster> = spawnMonsterList

    internal fun addSpawnMonster(spawnMonster: SpawnMonster) {
        spawnMonsterList.add(spawnMonster)
    }

    /** `SpawnsMonster::startup` (src/creatures/monsters/spawns/spawn_monster.cpp:122). */
    fun startup() {
        if (!isLoaded() || isStarted()) {
            return
        }

        for (spawnMonster in spawnMonsterList) {
            spawnMonster.startup()
        }

        started = true
    }

    /** `SpawnsMonster::clear` (src/creatures/monsters/spawns/spawn_monster.cpp:134). */
    fun clear() {
        for (spawnMonster in spawnMonsterList) {
            spawnMonster.stopEvent()
        }
        spawnMonsterList.clear()

        loaded = false
        started = false
    }

    companion object {
        /** `SpawnsMonster::isInZone` (src/creatures/monsters/spawns/spawn_monster.cpp:157). */
        fun isInZone(centerPos: Position, radius: Int, pos: Position): Boolean {
            if (radius == -1) {
                return true
            }

            return pos.x >= centerPos.x - radius && pos.x <= centerPos.x + radius &&
                pos.y >= centerPos.y - radius && pos.y <= centerPos.y + radius
        }
    }
}
