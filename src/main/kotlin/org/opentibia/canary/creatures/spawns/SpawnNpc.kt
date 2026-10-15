package org.opentibia.canary.creatures.spawns

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import java.lang.ref.WeakReference
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/** `NONBLOCKABLE_SPAWN_NPC_INTERVAL` (src/creatures/npcs/spawns/spawn_npc.hpp:94). */
const val NONBLOCKABLE_SPAWN_NPC_INTERVAL = 1400L

/** `MINSPAWN_INTERVAL` (src/creatures/npcs/spawns/spawn_npc.cpp:22). */
const val NPC_MINSPAWN_INTERVAL = 1000L

/** `MAXSPAWN_INTERVAL` (src/creatures/npcs/spawns/spawn_npc.cpp:23). */
const val NPC_MAXSPAWN_INTERVAL = 86400000L

/**
 * Port of `NpcType` limited to what the spawn engine consumes
 * (src/creatures/npcs/npcs.hpp): name and `canSpawn(position)`.
 */
class NpcType(
    val name: String,
    private val canSpawnAt: (Position) -> Boolean = { true },
) {
    /** `NpcType::canSpawn` (src/creatures/npcs/npcs.cpp:22). */
    fun canSpawn(pos: Position): Boolean = canSpawnAt(pos)
}

/** `g_npcs().getNpcType(name)` (src/creatures/npcs/npcs.hpp). */
fun interface NpcTypeLookup {
    fun getNpcType(name: String): NpcType?
}

/**
 * `std::make_shared<Npc>(npcType)` plus the world-entry tail of
 * `SpawnNpc::spawnNpc` (src/creatures/npcs/spawns/spawn_npc.cpp:194-205): the
 * factory creates the npc and places it (`g_game().internalPlaceCreature` on
 * startup, `g_game().placeCreature` on a respawn), because the port keeps the
 * engine free of the `g_game()` global. `null` mirrors the C++ `return false`
 * when the npc cannot enter the world.
 */
fun interface NpcFactory {
    fun create(npcType: NpcType, position: Position, startup: Boolean): Npc?
}

/** `spawnBlockNpc_t` (src/creatures/npcs/spawns/spawn_npc.hpp:17). */
class NpcSpawnBlock(
    var pos: Position,
    var npcType: NpcType,
    var direction: Direction,
    var interval: Long,
    var lastSpawnNpc: Long = 0,
)

/**
 * Port of `SpawnNpc` (src/creatures/npcs/spawns/spawn_npc.hpp:25).
 *
 * The world is provided through [npcFactory] (creature creation),
 * [npcTypeLookup] (`g_npcs()`), [scheduler] (`g_dispatcher`) and
 * [findPlayer] (`SpawnNpc::findPlayer`). [now] mirrors `OTSYS_TIME()`.
 */
class SpawnNpc(
    val centerPos: Position,
    val radius: Int,
    private val npcTypeLookup: NpcTypeLookup,
    private val npcFactory: NpcFactory,
    private val scheduler: SpawnScheduler,
    private val now: () -> Long = System::currentTimeMillis,
    private val findPlayer: (Position) -> Boolean = { false },
    private val addMagicEffect: (Position, Int) -> Unit = { _, _ -> },
) {
    private var interval: Long = 60000
    private var checkSpawnNpcEvent: SpawnTask? = null
    private var spawnGeneration = 0L

    internal val spawnNpcMap = linkedMapOf<Int, NpcSpawnBlock>()
    internal val spawnedNpcMap = linkedMapOf<Int, MutableList<Npc>>()

    /** `SpawnNpc::getInterval` (src/creatures/npcs/spawns/spawn_npc.cpp:215). */
    fun getInterval(): Long = interval

    /** `SpawnNpc::addNpc` (src/creatures/npcs/spawns/spawn_npc.cpp:312). */
    fun addNpc(name: String, pos: Position, dir: Direction, scheduleInterval: Long): Boolean {
        val npcType = npcTypeLookup.getNpcType(name)
        if (npcType == null) {
            logger.error { "Can not find $name" }
            return false
        }

        interval = minOf(interval, scheduleInterval)

        val sb = NpcSpawnBlock(
            pos = pos,
            npcType = npcType,
            direction = dir,
            interval = scheduleInterval,
        )

        val spawnId = spawnNpcMap.size + 1
        spawnNpcMap[spawnId] = sb
        return true
    }

    /** `SpawnNpc::startup` (src/creatures/npcs/spawns/spawn_npc.cpp:219). */
    fun startup() {
        for ((spawnId, sb) in spawnNpcMap) {
            spawnNpc(spawnId, sb.npcType, sb.pos, sb.direction, true)
        }
    }

    /** `SpawnNpc::startSpawnNpcCheck` (src/creatures/npcs/spawns/spawn_npc.cpp:156). */
    fun startSpawnNpcCheck() {
        if (checkSpawnNpcEvent != null) {
            return
        }
        val weakSelf = WeakReference(this)
        checkSpawnNpcEvent = scheduler.schedule(getInterval()) {
            weakSelf.get()?.checkSpawnNpc()
        }
    }

    /** `SpawnNpc::stopEvent` (src/creatures/npcs/spawns/spawn_npc.cpp:342). */
    fun stopEvent() {
        ++spawnGeneration
        checkSpawnNpcEvent?.cancel()
        checkSpawnNpcEvent = null
    }

    /** `SpawnNpc::checkSpawnNpc` (src/creatures/npcs/spawns/spawn_npc.cpp:230). */
    private fun checkSpawnNpc() {
        checkSpawnNpcEvent = null
        cleanup()

        for ((spawnId, sb) in spawnNpcMap) {
            if (spawnedNpcMap.containsKey(spawnId)) {
                continue
            }

            if (!sb.npcType.canSpawn(sb.pos)) {
                sb.lastSpawnNpc = now()
                continue
            }

            if (now() >= sb.lastSpawnNpc + sb.interval) {
                if (findPlayer(sb.pos)) {
                    sb.lastSpawnNpc = now()
                    continue
                }

                scheduleSpawnNpc(spawnId, 3 * NONBLOCKABLE_SPAWN_NPC_INTERVAL, spawnGeneration)
            }
        }

        if (spawnedNpcMap.size < spawnNpcMap.size) {
            startSpawnNpcCheck()
        }
    }

    /** `SpawnNpc::removeNpc` (src/creatures/npcs/spawns/spawn_npc.cpp:333). */
    fun removeNpc(npc: Npc) {
        for (spawnedNpcs in spawnedNpcMap.values) {
            if (spawnedNpcs.remove(npc)) {
                return
            }
        }
    }

    /** `SpawnNpc::isInSpawnNpcZone` (src/creatures/npcs/spawns/spawn_npc.cpp:189). */
    fun isInSpawnNpcZone(pos: Position): Boolean = SpawnsNpc.isInZone(centerPos, radius, pos)

    /**
     * `Npc::isInSpawnRange` (src/creatures/npcs/npc.cpp:1132) for a creature
     * owned by this spawn. The block position plays the role of `masterPos`;
     * creatures that were not spawned here are treated like a creature whose
     * `spawnNpc` weak pointer expired.
     */
    fun isInSpawnRange(npc: Npc, pos: Position, despawnRadius: Int, despawnRange: Int): Boolean {
        val sb = blockOf(npc) ?: return true

        if (despawnRadius == 0) {
            return true
        }

        if (!SpawnsNpc.isInZone(sb.pos, despawnRadius, pos)) {
            return false
        }

        if (despawnRange == 0) {
            return true
        }

        return abs(pos.z - sb.pos.z) <= despawnRange
    }

    private fun blockOf(npc: Npc): NpcSpawnBlock? {
        for ((spawnId, spawnedNpcs) in spawnedNpcMap) {
            if (spawnedNpcs.any { it === npc }) {
                return spawnNpcMap[spawnId]
            }
        }
        return null
    }

    private fun scheduleSpawnNpc(spawnId: Int, remainingInterval: Long, generation: Long) {
        if (generation != spawnGeneration) {
            return
        }

        val sb = spawnNpcMap[spawnId] ?: return

        if (remainingInterval <= 0) {
            spawnNpc(spawnId, sb.npcType, sb.pos, sb.direction)
        } else {
            addMagicEffect(sb.pos, CONST_ME_TELEPORT)
            val weakSelf = WeakReference(this)
            scheduler.schedule(NONBLOCKABLE_SPAWN_NPC_INTERVAL) {
                weakSelf.get()?.scheduleSpawnNpc(spawnId, remainingInterval - NONBLOCKABLE_SPAWN_NPC_INTERVAL, generation)
            }
        }
    }

    private fun spawnNpc(spawnId: Int, npcType: NpcType, pos: Position, dir: Direction, startup: Boolean = false): Boolean {
        val npc = npcFactory.create(npcType, pos, startup) ?: return false
        npc.direction = dir
        spawnedNpcMap.getOrPut(spawnId) { mutableListOf() }.add(npc)
        spawnNpcMap[spawnId]?.lastSpawnNpc = now()
        return true
    }

    /** `SpawnNpc::cleanup` (src/creatures/npcs/spawns/spawn_npc.cpp:298). */
    fun cleanup() {
        val iterator = spawnedNpcMap.entries.iterator()
        while (iterator.hasNext()) {
            val (spawnId, spawnedNpcs) = iterator.next()
            val surviving = spawnedNpcs.filterNot { it.isRemoved() }
            if (surviving.size != spawnedNpcs.size) {
                spawnNpcMap[spawnId]?.lastSpawnNpc = now()
                if (surviving.isEmpty()) {
                    iterator.remove()
                } else {
                    spawnedNpcs.clear()
                    spawnedNpcs.addAll(surviving)
                }
            }
        }
    }
}

/** Port of `SpawnsNpc` (src/creatures/npcs/spawns/spawn_npc.hpp:69). */
class SpawnsNpc {
    private val spawnNpcList = mutableListOf<SpawnNpc>()

    internal var loaded = false
    internal var started = false
    internal var fileName: String = ""

    fun isStarted(): Boolean = started

    fun setStarted(value: Boolean): Boolean {
        started = value
        return started
    }

    fun isLoaded(): Boolean = loaded

    fun setLoaded(value: Boolean): Boolean {
        loaded = value
        return loaded
    }

    fun setFileName(name: String): String {
        fileName = name
        return fileName
    }

    fun getSpawnNpcList(): List<SpawnNpc> = spawnNpcList

    internal fun addSpawnNpc(spawnNpc: SpawnNpc) {
        spawnNpcList.add(spawnNpc)
    }

    /** `SpawnsNpc::startup` (src/creatures/npcs/spawns/spawn_npc.cpp:101). */
    fun startup() {
        if (!isLoaded() || isStarted()) {
            return
        }

        for (spawnNpc in spawnNpcList) {
            spawnNpc.startup()
        }

        setStarted(true)
    }

    /** `SpawnsNpc::clear` (src/creatures/npcs/spawns/spawn_npc.cpp:113). */
    fun clear() {
        for (spawnNpc in spawnNpcList) {
            spawnNpc.stopEvent()
        }
        spawnNpcList.clear()

        setLoaded(false)
        setStarted(false)
        fileName = ""
    }

    companion object {
        /** `SpawnsNpc::isInZone` (src/creatures/npcs/spawns/spawn_npc.cpp:148). */
        fun isInZone(centerPos: Position, radius: Int, pos: Position): Boolean {
            if (radius == -1) {
                return true
            }

            return pos.x >= centerPos.x - radius && pos.x <= centerPos.x + radius &&
                pos.y >= centerPos.y - radius && pos.y <= centerPos.y + radius
        }
    }
}
