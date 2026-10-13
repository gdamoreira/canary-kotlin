package org.opentibia.canary.map

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import java.util.Collections
import java.util.IdentityHashMap

/** `SECTOR_SIZE` (`src/map/map_const.hpp:23`). */
private const val SECTOR_SIZE = 16

/** `SECTOR_MASK` (`src/map/map_const.hpp:24`). */
private const val SECTOR_MASK = SECTOR_SIZE - 1

/** `MAP_MAX_LAYERS` (`src/map/map_const.hpp:17`). */
private const val MAP_MAX_LAYERS = 16

/**
 * Port of `SpectatorsCache` (`src/map/spectators.hpp:21-36`): per-center query
 * ranges plus four optional floor snapshots (`creatures`, `monsters`, `npcs`,
 * `players`), each of which distinguishes the `multiFloor` snapshot from the
 * single-floor one queried without `multifloor`.
 */
class SpectatorsCache {
    var minRangeX: Int = 0
    var maxRangeX: Int = 0
    var minRangeY: Int = 0
    var maxRangeY: Int = 0

    val creatures = FloorData()
    val monsters = FloorData()
    val npcs = FloorData()
    val players = FloorData()

    /** `SpectatorsCache::FloorData` (`src/map/spectators.hpp:22-25`). */
    class FloorData {
        var floor: MutableList<Creature>? = null
        var multiFloor: MutableList<Creature>? = null
    }
}

/**
 * The `MapSector` surface consumed by `Spectators::getSpectators`
 * (`src/map/utils/mapsector.hpp:88-152`, `src/map/utils/mapsector.cpp:49-102`):
 * the flat `creature_list` plus the per-species lists and the `sectorS`/
 * `sectorE` neighbour links used to walk the viewport.
 *
 * CROSS-FILE NEED: the existing `MapSector`/`GameMap` (`MapGrid.kt:21-116`)
 * model 32x32 tile-only sectors and track no creatures, species lists or
 * neighbour links. Wiring the world must implement [SpectatorMapAccess] with
 * Canary's 16x16 sector semantics and keep these lists current via
 * [addCreature]/[removeCreature] on creature placement/removal.
 */
class SpectatorSector {
    /** `MapSector::creature_list` (`src/map/utils/mapsector.hpp:139`). */
    val creatureList: MutableList<Creature> = mutableListOf()

    /** `MapSector::player_list` (`src/map/utils/mapsector.hpp:140`). */
    val playerList: MutableList<Creature> = mutableListOf()

    /** `MapSector::monster_list` (`src/map/utils/mapsector.hpp:141`). */
    val monsterList: MutableList<Creature> = mutableListOf()

    /** `MapSector::npc_list` (`src/map/utils/mapsector.hpp:142`). */
    val npcList: MutableList<Creature> = mutableListOf()

    /** `MapSector::sectorS` (`src/map/utils/mapsector.hpp:136`). */
    var sectorS: SpectatorSector? = null

    /** `MapSector::sectorE` (`src/map/utils/mapsector.hpp:137`). */
    var sectorE: SpectatorSector? = null

    /** `MapSector::addCreature` (`src/map/utils/mapsector.cpp:49-58`). */
    fun addCreature(creature: Creature) {
        creatureList.add(creature)
        when (creature) {
            is Player -> playerList.add(creature)
            is Monster -> monsterList.add(creature)
            is Npc -> npcList.add(creature)
        }
    }

    /** `MapSector::removeCreature` (`src/map/utils/mapsector.cpp:60-102`); the missing-entry logging is dropped. */
    fun removeCreature(creature: Creature) {
        if (!removeFromList(creatureList, creature)) {
            return
        }

        when (creature) {
            is Player -> removeFromList(playerList, creature)
            is Monster -> removeFromList(monsterList, creature)
            is Npc -> removeFromList(npcList, creature)
        }
    }

    /** `std::ranges::find` + swap-with-last erase used by both removal loops. */
    private fun removeFromList(list: MutableList<Creature>, creature: Creature): Boolean {
        val index = list.indexOfFirst { it === creature }
        if (index < 0) {
            return false
        }
        list[index] = list.last()
        list.removeAt(list.size - 1)
        return true
    }
}

/**
 * Integration seam replacing the `g_game().map` global read by
 * `Spectators::getSpectators` (`src/map/spectators.cpp:169-199`) through
 * `Map::getMapSector` (`src/map/mapcache.hpp:174-182`).
 */
fun interface SpectatorMapAccess {
    fun getMapSector(x: Int, y: Int): SpectatorSector?
}

/**
 * Port of `class Spectators` (`src/map/spectators.cpp:36-326`,
 * `src/map/spectators.hpp:38-129`).
 *
 * Snapshots own strong creature references and are built by `find`/`filter`/
 * `excludeMaster`/`excludePlayerMaster`/`insert`/`insertAll`. Canary's static
 * `spectatorsCache` global (`spectators.cpp:15`) is kept in the companion so
 * [clearCache] and the `useCache` path share state across instances exactly
 * like the C++ static member.
 *
 * The `g_game().map` lookup is constructor-injected as [mapAccess] per
 * AGENTS.md 1.1; `Spectators()` callers in Canary become
 * `Spectators(mapAccess)`.
 */
class Spectators(
    private val mapAccess: SpectatorMapAccess,
) : Iterable<Creature> {
    /** `Spectators::creatures` (`src/map/spectators.hpp:128`). */
    private val creatures = mutableListOf<Creature>()

    /** `Spectators::find<T>` (`src/map/spectators.hpp:42-49`), the typed form of the flag overload. */
    inline fun <reified T : Creature> find(
        centerPos: Position,
        multifloor: Boolean = false,
        minRangeX: Int = 0,
        maxRangeX: Int = 0,
        minRangeY: Int = 0,
        maxRangeY: Int = 0,
        useCache: Boolean = true,
    ): Spectators = snapshot().find(
        centerPos,
        multifloor,
        T::class == Player::class,
        T::class == Monster::class,
        T::class == Npc::class,
        minRangeX,
        maxRangeX,
        minRangeY,
        maxRangeY,
        useCache,
    )

    /** `Spectators::filter<T>` (`src/map/spectators.hpp:51-58`), the typed form of the flag overload. */
    inline fun <reified T : Creature> filter(): Spectators =
        filter(T::class == Player::class, T::class == Monster::class, T::class == Npc::class)

    /**
     * Copy used to preserve Canary's value semantics: the public typed `find`
     * returns `find(...)` by value (`spectators.hpp:48`), so the receiver is
     * never mutated by a query.
     */
    @PublishedApi
    internal fun snapshot(): Spectators {
        val copy = Spectators(mapAccess)
        copy.creatures.addAll(creatures)
        return copy
    }

    /** `Spectators::insert` (`src/map/spectators.cpp:40-45`). */
    fun insert(creature: Creature?): Spectators {
        if (creature != null) {
            creatures.add(creature)
        }
        return this
    }

    /**
     * `Spectators::insertAll` (`src/map/spectators.cpp:47-82`). The copy/move
     * overload pair collapses to one append because Kotlin lists carry
     * references and no observable move state; merged snapshots are
     * deduplicated by identity exactly like `deduplicateCreatureSnapshot`.
     */
    fun insertAll(list: List<Creature>): Spectators {
        if (list.isNotEmpty()) {
            val hasValue = creatures.isNotEmpty()
            creatures.addAll(list)
            if (hasValue) {
                deduplicateCreatureSnapshot()
            }
        }
        return this
    }

    /** `Spectators::join` (`src/map/spectators.hpp:86-88`). */
    fun join(anotherSpectators: Spectators): Spectators =
        snapshot().insertAll(anotherSpectators.creatures)

    /** `Spectators::contains` (`src/map/spectators.hpp:90-92`); `shared_ptr` equality is identity. */
    fun contains(creature: Creature?): Boolean = creatures.any { it === creature }

    /** `Spectators::erase` (`src/map/spectators.hpp:94-96`). */
    fun erase(creature: Creature?): Boolean = creatures.removeAll { it === creature }

    /** `Spectators::empty` (`src/map/spectators.hpp:98-100`). */
    fun empty(): Boolean = creatures.isEmpty()

    /** `Spectators::size` (`src/map/spectators.hpp:102-104`). */
    fun size(): Int = creatures.size

    /** `Spectators::begin`/`end` (`src/map/spectators.hpp:106-112`). */
    override fun iterator(): Iterator<Creature> = creatures.iterator()

    /** `Spectators::data` (`src/map/spectators.hpp:114-116`). */
    fun data(): List<Creature> = creatures

    /** `Spectators::excludeMaster` (`src/map/spectators.cpp:276-291`). */
    fun excludeMaster(): Spectators {
        val specs = Spectators(mapAccess)
        for (creature in creatures) {
            if (creature is Monster && creature.getMaster() == null) {
                specs.insert(creature)
            }
        }
        return specs
    }

    /** `Spectators::excludePlayerMaster` (`src/map/spectators.cpp:293-309`). */
    fun excludePlayerMaster(): Spectators {
        val specs = Spectators(mapAccess)
        for (creature in creatures) {
            val master = creature.getMaster()
            if ((creature is Monster && master == null) || master == null || master !is Player) {
                specs.insert(creature)
            }
        }
        return specs
    }

    /** `Spectators::filter(bool, bool, bool)` (`src/map/spectators.cpp:311-326`). */
    @PublishedApi
    internal fun filter(onlyPlayers: Boolean, onlyMonsters: Boolean, onlyNpcs: Boolean): Spectators {
        val specs = Spectators(mapAccess)
        for (creature in creatures) {
            if (onlyPlayers && creature is Player) {
                specs.insert(creature)
            } else if (onlyMonsters && creature is Monster) {
                specs.insert(creature)
            } else if (onlyNpcs && creature is Npc) {
                specs.insert(creature)
            }
        }
        return specs
    }

    /**
     * `Spectators::find(...)` (`src/map/spectators.cpp:205-274`): resolves the
     * zero-means-viewport ranges, consults [SpectatorsCache] and either serves
     * the cached snapshot, widens/re-queries, or fills the cache from
     * [getSpectators]. Returns `this` like the C++ private overload (the
     * public typed wrapper copied it first).
     */
    @PublishedApi
    internal fun find(
        centerPos: Position,
        multifloor: Boolean,
        onlyPlayers: Boolean,
        onlyMonsters: Boolean,
        onlyNpcs: Boolean,
        minRangeX: Int,
        maxRangeX: Int,
        minRangeY: Int,
        maxRangeY: Int,
        useCache: Boolean,
    ): Spectators {
        var rangeMinX = if (minRangeX == 0) -Creature.MAP_MAX_VIEW_PORT_X else -minRangeX
        var rangeMaxX = if (maxRangeX == 0) Creature.MAP_MAX_VIEW_PORT_X else maxRangeX
        var rangeMinY = if (minRangeY == 0) -Creature.MAP_MAX_VIEW_PORT_Y else -minRangeY
        var rangeMaxY = if (maxRangeY == 0) Creature.MAP_MAX_VIEW_PORT_Y else maxRangeY

        if (!useCache) {
            insertAll(
                getSpectators(
                    centerPos,
                    multifloor,
                    onlyPlayers,
                    onlyMonsters,
                    onlyNpcs,
                    rangeMinX,
                    rangeMaxX,
                    rangeMinY,
                    rangeMaxY,
                ),
            )
            return this
        }

        val cached = spectatorsCache[centerPos]
        if (cached != null) {
            if (rangeMinX < cached.minRangeX || rangeMaxX > cached.maxRangeX ||
                rangeMinY < cached.minRangeY || rangeMaxY > cached.maxRangeY
            ) {
                // recache with new range
                cached.minRangeX = minOf(rangeMinX, cached.minRangeX)
                rangeMinX = cached.minRangeX
                cached.minRangeY = minOf(rangeMinY, cached.minRangeY)
                rangeMinY = cached.minRangeY
                cached.maxRangeX = maxOf(rangeMaxX, cached.maxRangeX)
                rangeMaxX = cached.maxRangeX
                cached.maxRangeY = maxOf(rangeMaxY, cached.maxRangeY)
                rangeMaxY = cached.maxRangeY
            } else {
                val checkDistance = rangeMinX != cached.minRangeX || rangeMaxX != cached.maxRangeX ||
                    rangeMinY != cached.minRangeY || rangeMaxY != cached.maxRangeY

                if (onlyPlayers || onlyMonsters || onlyNpcs) {
                    val creaturesCache = when {
                        onlyPlayers -> cached.players
                        onlyMonsters -> cached.monsters
                        else -> cached.npcs
                    }

                    // check players/monsters/npcs cache
                    if (
                        checkCache(
                            creaturesCache,
                            onlyPlayers,
                            onlyMonsters,
                            onlyNpcs,
                            centerPos,
                            checkDistance,
                            multifloor,
                            rangeMinX,
                            rangeMaxX,
                            rangeMinY,
                            rangeMaxY,
                        )
                    ) {
                        return this
                    }

                    // if there is no players/monsters/npcs cache, look for
                    // players/monsters/npcs in the creatures cache.
                    if (
                        checkCache(
                            cached.creatures,
                            onlyPlayers,
                            onlyMonsters,
                            onlyNpcs,
                            centerPos,
                            true,
                            multifloor,
                            rangeMinX,
                            rangeMaxX,
                            rangeMinY,
                            rangeMaxY,
                        )
                    ) {
                        return this
                    }
                    // All Creatures
                } else if (
                    checkCache(
                        cached.creatures,
                        false,
                        false,
                        false,
                        centerPos,
                        checkDistance,
                        multifloor,
                        rangeMinX,
                        rangeMaxX,
                        rangeMinY,
                        rangeMaxY,
                    )
                ) {
                    return this
                }
            }
        }

        val spectators = getSpectators(
            centerPos,
            multifloor,
            onlyPlayers,
            onlyMonsters,
            onlyNpcs,
            rangeMinX,
            rangeMaxX,
            rangeMinY,
            rangeMaxY,
        )

        // It is necessary to create the cache even if no spectators is found,
        // so that there is no future query.
        val cache = if (cached != null) {
            cached
        } else {
            val created = SpectatorsCache()
            created.minRangeX = rangeMinX
            created.maxRangeX = rangeMaxX
            created.minRangeY = rangeMinY
            created.maxRangeY = rangeMaxY
            spectatorsCache[centerPos] = created
            created
        }
        val creaturesCache = when {
            onlyPlayers -> cache.players
            onlyMonsters -> cache.monsters
            onlyNpcs -> cache.npcs
            else -> cache.creatures
        }
        val creatureList = (if (multifloor) creaturesCache.multiFloor else creaturesCache.floor)
            ?: mutableListOf<Creature>().also {
                if (multifloor) {
                    creaturesCache.multiFloor = it
                } else {
                    creaturesCache.floor = it
                }
            }
        creatureList.clear()

        if (spectators.isNotEmpty()) {
            creatureList.addAll(spectators)
            insertAll(spectators)
        }

        return this
    }

    /**
     * `Spectators::checkCache` (`src/map/spectators.cpp:84-119`). Returns
     * `false` when the requested floor snapshot was never built, otherwise
     * merges it (distance-filtered when requested) and returns `true`.
     */
    private fun checkCache(
        specData: SpectatorsCache.FloorData,
        onlyPlayers: Boolean,
        onlyMonsters: Boolean,
        onlyNpcs: Boolean,
        centerPos: Position,
        checkDistance: Boolean,
        multifloor: Boolean,
        minRangeX: Int,
        maxRangeX: Int,
        minRangeY: Int,
        maxRangeY: Int,
    ): Boolean {
        val list = if (multifloor || specData.floor == null) specData.multiFloor else specData.floor

        if (list == null) {
            return false
        }

        var distanceCheck = checkDistance
        if (!multifloor && specData.floor == null) {
            // Force check the distance of creatures as we only need to pick up
            // creatures from the Floor(centerPos.z)
            distanceCheck = true
        }

        if (distanceCheck) {
            val spectators = mutableListOf<Creature>()
            for (creature in list) {
                val specPos = creature.position
                if ((centerPos.x - specPos.x >= minRangeX &&
                        centerPos.y - specPos.y >= minRangeY &&
                        centerPos.x - specPos.x <= maxRangeX &&
                        centerPos.y - specPos.y <= maxRangeY &&
                        (multifloor || specPos.z == centerPos.z) &&
                        ((onlyPlayers && creature is Player) ||
                            (onlyMonsters && creature is Monster) ||
                            (onlyNpcs && creature is Npc))) ||
                    (!onlyPlayers && !onlyMonsters && !onlyNpcs)
                ) {
                    spectators.add(creature)
                }
            }
            insertAll(spectators)
        } else {
            insertAll(list)
        }

        return true
    }

    /**
     * `Spectators::getSpectators` (`src/map/spectators.cpp:121-203`): computes
     * the multifloor z-range, the per-floor offset-shifted viewport rectangle,
     * then walks every 16x16 sector overlapping it and collects creatures from
     * the requested species list.
     */
    private fun getSpectators(
        centerPos: Position,
        multifloor: Boolean = false,
        onlyPlayers: Boolean = false,
        onlyMonsters: Boolean = false,
        onlyNpcs: Boolean = false,
        minRangeX: Int = 0,
        maxRangeX: Int = 0,
        minRangeY: Int = 0,
        maxRangeY: Int = 0,
    ): MutableList<Creature> {
        var minRangeZ = centerPos.z
        var maxRangeZ = centerPos.z

        if (multifloor) {
            if (centerPos.z > Creature.MAP_INIT_SURFACE_LAYER) {
                minRangeZ = maxOf(centerPos.z - Creature.MAP_LAYER_VIEW_LIMIT, 0)
                maxRangeZ = minOf(centerPos.z + Creature.MAP_LAYER_VIEW_LIMIT, MAP_MAX_LAYERS - 1)
            } else if (centerPos.z == Creature.MAP_INIT_SURFACE_LAYER - 1) {
                minRangeZ = 0
                maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER - 1 + Creature.MAP_LAYER_VIEW_LIMIT
            } else if (centerPos.z == Creature.MAP_INIT_SURFACE_LAYER) {
                minRangeZ = 0
                maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER + Creature.MAP_LAYER_VIEW_LIMIT
            } else {
                minRangeZ = 0
                maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER
            }
        }

        val minY = centerPos.y + minRangeY
        val minX = centerPos.x + minRangeX
        val maxY = centerPos.y + maxRangeY
        val maxX = centerPos.x + maxRangeX

        val width = maxX - minX
        val height = maxY - minY
        val depth = maxRangeZ - minRangeZ

        val minOffset = centerPos.z - maxRangeZ
        val x1 = minOf(0xFFFF, maxOf(0, minX + minOffset))
        val y1 = minOf(0xFFFF, maxOf(0, minY + minOffset))

        val maxOffset = centerPos.z - minRangeZ
        val x2 = minOf(0xFFFF, maxOf(0, maxX + maxOffset))
        val y2 = minOf(0xFFFF, maxOf(0, maxY + maxOffset))

        val startX1 = x1 - (x1 and SECTOR_MASK)
        val startY1 = y1 - (y1 and SECTOR_MASK)
        val endX2 = x2 - (x2 and SECTOR_MASK)
        val endY2 = y2 - (y2 and SECTOR_MASK)

        val spectators = mutableListOf<Creature>()

        var sectorS = mapAccess.getMapSector(startX1, startY1)
        var ny = startY1
        while (ny <= endY2) {
            var sectorE = sectorS
            var nx = startX1
            while (nx <= endX2) {
                if (sectorE != null) {
                    val nodeList = when {
                        onlyPlayers -> sectorE.playerList
                        onlyMonsters -> sectorE.monsterList
                        onlyNpcs -> sectorE.npcList
                        else -> sectorE.creatureList
                    }

                    for (creature in nodeList) {
                        val cpos = creature.position
                        if ((cpos.z - minRangeZ).toUInt() <= depth.toUInt()) {
                            val offsetZ = centerPos.z - cpos.z
                            if ((cpos.x - offsetZ - minX).toUInt() <= width.toUInt() &&
                                (cpos.y - offsetZ - minY).toUInt() <= height.toUInt()
                            ) {
                                spectators.add(creature)
                            }
                        }
                    }
                    sectorE = sectorE.sectorE
                } else {
                    sectorE = mapAccess.getMapSector(nx + SECTOR_SIZE, ny)
                }
                nx += SECTOR_SIZE
            }

            sectorS = if (sectorS != null) sectorS.sectorS else mapAccess.getMapSector(startX1, ny + SECTOR_SIZE)
            ny += SECTOR_SIZE
        }

        return spectators
    }

    /** `deduplicateCreatureSnapshot` (`src/map/spectators.cpp:18-33`): first occurrence wins, by identity. */
    private fun deduplicateCreatureSnapshot() {
        val seenCreatures = Collections.newSetFromMap(IdentityHashMap<Creature, Boolean>())
        val uniqueCreatures = ArrayList<Creature>(creatures.size)
        for (creature in creatures) {
            if (seenCreatures.add(creature)) {
                uniqueCreatures.add(creature)
            }
        }
        creatures.clear()
        creatures.addAll(uniqueCreatures)
    }

    companion object {
        /** `Spectators::spectatorsCache` (`src/map/spectators.cpp:15`). */
        private val spectatorsCache = mutableMapOf<Position, SpectatorsCache>()

        /** `Spectators::clearCache` (`src/map/spectators.cpp:36-38`). */
        fun clearCache() {
            spectatorsCache.clear()
        }
    }
}
