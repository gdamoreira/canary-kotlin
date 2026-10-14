package org.opentibia.canary.map

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FindPathParams
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.spawns.SpawnsMonster
import org.opentibia.canary.creatures.spawns.SpawnsNpc
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.areInRange
import org.opentibia.canary.game.movement.getDistanceX
import org.opentibia.canary.game.movement.getDistanceY
import org.opentibia.canary.game.movement.getDistanceZ
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.game.zones.Zones
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.map.cache.MapCache
import org.opentibia.canary.map.cache.MapSector as CacheMapSector
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.systems.rewardchest.ReturnValue
import kotlin.math.abs

/**
 * Port of `class Map` (src/map/map.hpp:33-202, src/map/map.cpp).
 *
 * Like the C++ `class Map final : public MapCache`, the port extends
 * [MapCache] and inherits its 16x16 `MapSector` registry: every tile creation
 * resolves `getBestMapSector` (`MapCache::setBasicTile`, mapcache.cpp:397-404)
 * so the spectator sector lists and `sectorS`/`sectorE` viewport links are
 * available to [Spectators] through [getMapSector]. The spatial storage
 * (tiles, sectors) is ported; every method whose C++ body reaches a singleton
 * (`g_game()`, `g_dispatcher()`, spectators, item types) keeps that reach
 * behind a documented seam or a PARITY GAP.
 */
class GameMap(
    val houses: Houses = Houses(),
    /** `Map::towns` (src/map/map.hpp:168). */
    val towns: Towns = Towns(),
    val zones: Zones = Zones(),
) : MapCache(houses = houses, zones = { position -> zones.getZones(position) }), SpectatorMapAccess {

    /** `MapCache::getMapSector` (src/map/mapcache.hpp:174-182); also the [SpectatorMapAccess] seam. */
    override fun getMapSector(x: Int, y: Int): CacheMapSector? = super.getMapSector(x, y)

    /** `Map::spawnsMonster` (`src/map/map.hpp:178`), loaded by `IOMap::loadMonsters`. */
    var spawnsMonster: SpawnsMonster = SpawnsMonster()

    /** `Map::spawnsNpc` (`src/map/map.hpp:179`), loaded by `IOMap::loadNpcs`. */
    var spawnsNpc: SpawnsNpc = SpawnsNpc()

    /** `Map::monsterfile` (`src/map/map.hpp:158`), resolved by `IOMap::loadMonsters`. */
    var monsterfile: String = ""

    /** `Map::npcfile` (`src/map/map.hpp:159`), resolved by `IOMap::loadNpcs`. */
    var npcfile: String = ""

    /** `Map::housefile` (`src/map/map.hpp:160`), resolved by `IOMap::loadHouses`. */
    var housefile: String = ""

    /** `Map::zonesfile` (`src/map/map.hpp:161`), resolved by `IOMap::loadZones`. */
    var zonesfile: String = ""

    /** `Map::path` (`src/map/map.hpp:146`), the loaded OTBM file identifier. */
    var path: String = ""

    /**
     * `Game::beforeCreatureZoneChange` (src/game/game.cpp:12417) seam. The C++
     * body reaches the zone callbacks through the `g_game()` global; the port
     * injects the facade call instead.
     */
    var beforeCreatureZoneChange: (Creature, Set<Zone>, Set<Zone>) -> ReturnValue =
        { _, _, _ -> ReturnValue.NOERROR }

    /** `Game::afterCreatureZoneChange` (src/game/game.cpp:12447) seam. */
    var afterCreatureZoneChange: (Creature, Set<Zone>, Set<Zone>) -> Unit = { _, _, _ -> }

    /**
     * `g_dispatcher().context().isMovementCommit()` + `g_dispatcher().addEvent`
     * seam (src/map/map.cpp:799-806). The default runs the post-move events
     * inline, mirroring the non-movement-commit C++ branch.
     */
    var scheduleMoveEvents: ((() -> Unit) -> Unit) = { it() }

    /**
     * `IOMapSerialize::saveHouseInfo` (src/io/iomapserialize.cpp:391-401) seam
     * for `Map::save` (src/map/map.cpp:262-273). `null` behaves like a failing
     * save step.
     */
    var saveHouseInfo: (() -> Boolean)? = null

    /** `IOMapSerialize::saveHouseItems` (src/io/iomapserialize.cpp:82-92) seam. */
    var saveHouseItems: (() -> Boolean)? = null

    // Kotlin-only 32x32 tile chunking (`MapGrid.kt`). Canary stores tiles on
    // the 16x16 `MapSector` floors; the port keeps the pre-existing tile
    // storage and maintains the Canary sectors alongside for the spectator
    // lists and viewport links (PARITY GAP: `getTile`/`getOrCreateTile` do not
    // read through `Floor`).
    // Sector key: (z.toLong() shl 32) or ((sectorX.toLong() and 0xFFFF) shl 16) or (sectorY.toLong() and 0xFFFF)
    private val sectors = mutableMapOf<Long, MapSector>()

    private fun getSectorKey(sx: Int, sy: Int, sz: Int): Long {
        return (sz.toLong() shl 32) or ((sx.toLong() and 0xFFFFL) shl 16) or (sy.toLong() and 0xFFFFL)
    }

    fun getOrCreateTile(position: Position): Tile {
        // `MapCache::setBasicTile` (mapcache.cpp:397-404): materializing a tile
        // creates and links its 16x16 spectator sector.
        getBestMapSector(position.x, position.y)

        val sx = position.x shr 5
        val sy = position.y shr 5
        val key = getSectorKey(sx, sy, position.z)
        val sector = sectors.getOrPut(key) { MapSector(sx, sy, position.z) }
        val tile = sector.getOrCreateTile(position, position.x and 0x1F, position.y and 0x1F)
        tile.map = this
        return tile
    }

    fun getTile(position: Position): Tile? {
        val sx = position.x shr 5
        val sy = position.y shr 5
        val key = getSectorKey(sx, sy, position.z)
        val sector = sectors[key] ?: return null
        return sector.getTile(position.x and 0x1F, position.y and 0x1F)
    }

    fun getTilesInRange(center: Position, radiusX: Int, radiusY: Int): List<Tile> {
        val minX = center.x - radiusX
        val maxX = center.x + radiusX
        val minY = center.y - radiusY
        val maxY = center.y + radiusY

        val minSx = minX shr 5
        val maxSx = maxX shr 5
        val minSy = minY shr 5
        val maxSy = maxY shr 5

        val result = mutableListOf<Tile>()
        for (sx in minSx..maxSx) {
            for (sy in minSy..maxSy) {
                val sector = sectors[getSectorKey(sx, sy, center.z)] ?: continue
                for (tile in sector.getAllTiles()) {
                    if (tile.position.isWithinRange(center, radiusX, radiusY)) {
                        result.add(tile)
                    }
                }
            }
        }
        return result
    }

    /** `Map::save` (src/map/map.cpp:262-273). */
    fun save(): Boolean {
        var saved = false
        for (tries in 0 until 6) {
            if (saveHouseInfo?.invoke() == true) {
                saved = true
            }
            if (saved && saveHouseItems?.invoke() == true) {
                return true
            }
        }
        return false
    }

    /** `Map::placeCreature` (src/map/map.cpp:559-677). */
    fun placeCreature(
        centerPos: Position,
        creature: Creature?,
        extendedPos: Boolean = false,
        forceLogin: Boolean = false,
        centerTile: Tile? = null,
    ): Boolean {
        // `monster->ignoreFieldDamage` guard (map.cpp:566-568, 579-581, 644-646,
        // 659-661) is a PARITY GAP: the state lives on MonsterBehavior, not Creature.

        var tile = centerTile ?: getTile(centerPos)
        val placeInPZ: Boolean
        var foundTile: Boolean
        if (tile != null) {
            placeInPZ = tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
            val ret = tile.queryAdd(0, creature, 1, CylinderFlags.FLAG_IGNOREBLOCKITEM or CylinderFlags.FLAG_IGNOREFIELDDAMAGE)
            foundTile = forceLogin || ret == ReturnValue.NOERROR || ret == ReturnValue.PLAYERISNOTINVITED
        } else {
            placeInPZ = false
            foundTile = false
        }

        if (!foundTile) {
            val relList = if (extendedPos) EXTENDED_REL_LIST.toMutableList() else NORMAL_REL_LIST.toMutableList()

            if (extendedPos) {
                relList.subList(0, 4).shuffle()
                relList.subList(4, relList.size).shuffle()
            } else {
                relList.shuffle()
            }

            for ((xOffset, yOffset) in relList) {
                val tryPos = Position(centerPos.x + xOffset, centerPos.y + yOffset, centerPos.z)

                val tryTile = getTile(tryPos)
                if (tryTile == null || (placeInPZ && !tryTile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value))) {
                    continue
                }

                // Will never add the creature inside a teleport, avoiding infinite loop bug
                if (tryTile.hasFlag(TileFlags.TILESTATE_TELEPORT.value)) {
                    continue
                }

                if (tryTile.queryAdd(0, creature, 1, CylinderFlags.FLAG_IGNOREBLOCKITEM or CylinderFlags.FLAG_IGNOREFIELDDAMAGE) ==
                    ReturnValue.NOERROR
                ) {
                    if (!extendedPos || isSightClear(centerPos, tryPos, false)) {
                        foundTile = true
                        tile = tryTile
                        break
                    }
                }
            }

            if (!foundTile) {
                return false
            }
        }

        if (tile != null) {
            val result = tile.queryDestination(0, creature, null, 0)
            result.cylinder?.internalAddThing(creature)

            if (creature != null) {
                // `getMapSector(dest.x, dest.y)->addCreature(creature)` (map.cpp:674).
                val dest = result.cylinder?.position ?: tile.position
                val destSector = getMapSector(dest.x, dest.y) ?: getBestMapSector(dest.x, dest.y)
                destSector.addCreature(creature)

                // `Game::placeCreature` spectator fanout (game.cpp:1532-1541).
                // C++ splits the appear send between `Game::placeCreature` and
                // `Map::placeCreature`; the port folds the fanout into the map
                // layer because `Game.kt` is outside this work's ownership. The
                // `hasPlayerSpectators`/`addCreatureCheck`/`Creature::onPlacedCreature`
                // tail stays with `Game.placeCreature` (Game.kt:1371).
                for (spectator in Spectators(this).find<Creature>(creature.position, true)) {
                    (spectator as? Player)?.sendCreatureAppear(creature, creature.position, true)
                    spectator.onCreatureAppear(creature, true)
                }
            }
        }
        return true
    }

    /** `Map::moveCreature` (src/map/map.cpp:679-814). */
    fun moveCreature(creature: Creature?, newTile: Tile?, forceTeleport: Boolean = false) {
        if (creature == null || creature.isRemoved() || newTile == null) {
            return
        }

        val oldTile = creature.getTile() ?: return

        val oldPos = oldTile.position
        val newPos = newTile.position

        if (oldPos == newPos) {
            return
        }

        val fromZones = oldTile.getZones()
        val toZones = newTile.getZones()

        if (beforeCreatureZoneChange(creature, fromZones, toZones) != ReturnValue.NOERROR) {
            return
        }

        val teleport = forceTeleport || newTile.ground == null || !areInRange(oldPos, newPos, 1, 1, 0)

        // `Spectators spectators; find<Creature>(...)` (map.cpp:706-722). The
        // neighbouring-step query widens the leading viewport edge; teleports
        // merge the queries at both endpoints. The typed `find` returns the
        // snapshot by value (Spectators.snapshot), so every call rebinds.
        var spectators = Spectators(this)
        if (!teleport && oldPos.z == newPos.z) {
            var minRangeX = Creature.MAP_MAX_VIEW_PORT_X
            var maxRangeX = Creature.MAP_MAX_VIEW_PORT_X
            var minRangeY = Creature.MAP_MAX_VIEW_PORT_Y
            var maxRangeY = Creature.MAP_MAX_VIEW_PORT_Y

            if (oldPos.y > newPos.y) {
                ++minRangeY
            } else if (oldPos.y < newPos.y) {
                ++maxRangeY
            }

            if (oldPos.x < newPos.x) {
                ++maxRangeX
            } else if (oldPos.x > newPos.x) {
                ++minRangeX
            }

            spectators = spectators.find<Creature>(oldPos, true, minRangeX, maxRangeX, minRangeY, maxRangeY, false)
        } else {
            spectators = spectators.find<Creature>(oldPos, true, 0, 0, 0, 0, false)
            spectators = spectators.find<Creature>(newPos, true, 0, 0, 0, 0, false)
        }

        val playerSpectators = spectators.data().filterIsInstance<Player>()
        val oldStackPosVector = playerSpectators.map { player ->
            if (player.canSeeCreature(creature)) {
                oldTile.getClientIndexOfCreature(player, creature)
            } else {
                -1
            }
        }

        // remove the creature
        oldTile.removeThing(creature, 0)

        // Switch the node ownership (map.cpp:752-759)
        val oldSector = getMapSector(oldPos.x, oldPos.y)
        val newSector = getMapSector(newPos.x, newPos.y)
        if (oldSector !== newSector) {
            oldSector?.removeCreature(creature)
            newSector?.addCreature(creature)
        }

        // add the creature
        newTile.addThing(creature)

        if (!teleport) {
            if (oldPos.y > newPos.y) {
                creature.direction = Direction.North
            } else if (oldPos.y < newPos.y) {
                creature.direction = Direction.South
            }

            if (oldPos.x < newPos.x) {
                creature.direction = Direction.East
            } else if (oldPos.x > newPos.x) {
                creature.direction = Direction.West
            }
        }

        // send to client (map.cpp:778-786): the new stack position is resolved
        // after the tile insertion, the old one was captured before removal.
        var stackPosIndex = 0
        for (player in playerSpectators) {
            val stackpos = oldStackPosVector[stackPosIndex++]
            if (stackpos != -1) {
                player.sendCreatureMove(
                    creature,
                    newPos,
                    newTile.getClientIndexOfCreature(player, creature),
                    oldPos,
                    stackpos,
                    teleport,
                )
            }
        }

        // `spectator->onCreatureMove(creature, newTile, newPos, oldTile, oldPos, teleport)`
        // (map.cpp:788-790). The moving creature is part of its own spectator
        // snapshot, so its self-move branch (Creature.cpp:519-548) runs here.
        for (spectator in spectators) {
            spectator.onCreatureMove(creature, newTile, newPos, oldTile, oldPos, teleport)
        }

        // event method
        val events = {
            oldTile.postRemoveNotification(creature, newTile, 0)
            newTile.postAddNotification(creature, oldTile, 0)
            afterCreatureZoneChange(creature, oldTile.getZones(), newTile.getZones())
        }

        scheduleMoveEvents(events)

        // The forceTeleport player magic effects (map.cpp:808-813) are a PARITY GAP.
    }

    /**
     * Port of the tile/spectator block of `Game::removeCreature`
     * (src/game/game.cpp:1561-1593): resolves the tile spectators through the
     * 16x16 sector lists with `multifloor`, captures every player spectator's
     * client stack position, removes the creature from the tile (which drops
     * the sector entry through `Tile::removeCreature`, tile.cpp:1434-1441),
     * fans `Player::sendRemoveTileThing` out to those spectators and
     * `Creature::onRemoveCreature` to all of them.
     *
     * PARITY GAP: the creature-state tail of `Game::removeCreature`
     * (game.cpp:1595-1617: master clearing, `afterCreatureZoneChange`,
     * `Creature::setRemoved`, `removeCreatureCheck` and the summon recursion)
     * stays with the `Game` facade (`Game.removeCreature`, Game.kt:1405).
     */
    fun removeCreature(creature: Creature?, isLogout: Boolean = true): Boolean {
        if (creature == null || creature.isRemoved()) {
            return false
        }

        val tile = creature.getTile()
        if (tile != null) {
            val spectators = Spectators(this).find<Creature>(tile.position, true)
            val playerSpectators = spectators.data().filterIsInstance<Player>()
            val oldStackPosVector = playerSpectators.map { player ->
                if (player.canSeeCreature(creature)) {
                    tile.getClientIndexOfCreature(player, creature)
                } else {
                    -1
                }
            }

            tile.removeCreature(creature)

            // C++ sends unconditionally here, unlike the move fanout.
            var stackPosIndex = 0
            for (player in playerSpectators) {
                player.sendRemoveTileThing(tile.position, oldStackPosVector[stackPosIndex++])
            }

            for (spectator in spectators) {
                spectator.onRemoveCreature(creature, isLogout)
            }
        }

        return true
    }

    /** `Map::canThrowObjectTo` (src/map/map.cpp:816-843). */
    fun canThrowObjectTo(
        fromPos: Position,
        toPos: Position,
        lineOfSight: Int = SightLine_CheckSightLine,
        rangex: Int = MAP_MAX_CLIENT_VIEW_PORT_X,
        rangey: Int = MAP_MAX_CLIENT_VIEW_PORT_Y,
    ): Boolean {
        // z checks
        // underground 8->15
        // ground level and above 7->0
        if ((fromPos.z >= 8 && toPos.z <= MAP_INIT_SURFACE_LAYER) ||
            (toPos.z >= MAP_INIT_SURFACE_LAYER + 1 && fromPos.z <= MAP_INIT_SURFACE_LAYER)
        ) {
            return false
        }

        val deltaz = getDistanceZ(fromPos, toPos)
        if (deltaz > MAP_LAYER_VIEW_LIMIT) {
            return false
        }

        if ((getDistanceX(fromPos, toPos) - deltaz) > rangex) {
            return false
        }

        // distance checks
        if ((getDistanceY(fromPos, toPos) - deltaz) > rangey) {
            return false
        }

        if ((lineOfSight and SightLine_CheckSightLine) == 0) {
            return true
        }

        return isSightClear(fromPos, toPos, (lineOfSight and SightLine_FloorCheck) != 0)
    }

    /** `Map::checkSightLine` (src/map/map.cpp:845-948). */
    fun checkSightLine(start: Position, destination: Position): Boolean {
        if (start.x == destination.x && start.y == destination.y) {
            return true
        }

        var x = start.x
        var y = start.y
        var destinationX = destination.x
        var destinationY = destination.y
        val distanceX = getDistanceX(start, destination)
        val distanceY = getDistanceY(start, destination)

        if (start.y == destination.y) {
            // Horizontal line
            val delta = if (start.x < destination.x) 0x0001 else 0xFFFF
            var remaining = distanceX
            while (--remaining > 0) {
                x = (x + delta) and 0xFFFF

                val tile = getTile(Position(x, y, start.z))
                if (tile != null && tile.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE)) {
                    return false
                }
            }
        } else if (start.x == destination.x) {
            // Vertical line
            val delta = if (start.y < destination.y) 0x0001 else 0xFFFF
            var remaining = distanceY
            while (--remaining > 0) {
                y = (y + delta) and 0xFFFF

                val tile = getTile(Position(x, y, start.z))
                if (tile != null && tile.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE)) {
                    return false
                }
            }
        } else {
            // Xiaolin Wu's line algorithm - https://en.wikipedia.org/wiki/Xiaolin_Wu%27s_line_algorithm
            // based on Michael Abrash's implementation - https://www.amazon.com/gp/product/1576101746/102-5103244-8168911
            if (distanceY > distanceX) {
                val errorAdjustment = ((distanceX shl 16) / distanceY) and 0xFFFF
                var errorAccumulator = 0
                var deltaX = 0x0001

                if (y > destinationY) {
                    val swapX = x
                    val swapY = y
                    x = destinationX
                    y = destinationY
                    destinationX = swapX
                    destinationY = swapY
                }
                if (x > destinationX) {
                    deltaX = 0xFFFF
                    errorAccumulator = (errorAccumulator - errorAdjustment) and 0xFFFF
                }

                var remaining = distanceY
                while (--remaining > 0) {
                    var xIncrease = 0
                    val errorAccumulatorTemp = errorAccumulator
                    errorAccumulator = (errorAccumulator + errorAdjustment) and 0xFFFF
                    if (errorAccumulator <= errorAccumulatorTemp) {
                        xIncrease = deltaX
                    }

                    val tile = getTile(Position((x + xIncrease) and 0xFFFF, (y + 0x0001) and 0xFFFF, start.z))
                    if (tile != null && tile.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE)) {
                        return areInRange(Position(x, y, start.z), Position(destinationX, destinationY, start.z), 1, 1)
                    }

                    x = (x + xIncrease) and 0xFFFF
                    y = (y + 0x0001) and 0xFFFF
                }
            } else {
                val errorAdjustment = ((distanceY shl 16) / distanceX) and 0xFFFF
                var errorAccumulator = 0
                var deltaY = 0x0001

                if (x > destinationX) {
                    val swapX = x
                    val swapY = y
                    x = destinationX
                    y = destinationY
                    destinationX = swapX
                    destinationY = swapY
                }
                if (y > destinationY) {
                    deltaY = 0xFFFF
                    errorAccumulator = (errorAccumulator - errorAdjustment) and 0xFFFF
                }

                var remaining = distanceX
                while (--remaining > 0) {
                    var yIncrease = 0
                    val errorAccumulatorTemp = errorAccumulator
                    errorAccumulator = (errorAccumulator + errorAdjustment) and 0xFFFF
                    if (errorAccumulator <= errorAccumulatorTemp) {
                        yIncrease = deltaY
                    }

                    val tile = getTile(Position((x + 0x0001) and 0xFFFF, (y + yIncrease) and 0xFFFF, start.z))
                    if (tile != null && tile.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE)) {
                        return areInRange(Position(x, y, start.z), Position(destinationX, destinationY, start.z), 1, 1)
                    }

                    x = (x + 0x0001) and 0xFFFF
                    y = (y + yIncrease) and 0xFFFF
                }
            }
        }
        return true
    }

    /** `Map::isSightClear` (src/map/map.cpp:950-998). */
    fun isSightClear(fromPos: Position, toPos: Position, floorCheck: Boolean): Boolean {
        // Check if this sight line should be even possible
        if (floorCheck && fromPos.z != toPos.z) {
            return false
        }

        // Check if we even need to perform line checking
        if (fromPos.z == toPos.z && (areInRange(fromPos, toPos, 1, 1) || (!floorCheck && fromPos.z == 0))) {
            return true
        }

        // We can only throw one floor up
        if (fromPos.z > toPos.z && getDistanceZ(fromPos, toPos) > 1) {
            return false
        }

        // Perform check for current floor
        val sightClear = checkSightLine(fromPos, toPos)
        if (floorCheck || (fromPos.z == toPos.z && sightClear)) {
            return sightClear
        }

        var startZ: Int
        if (sightClear && fromPos.z <= toPos.z) {
            startZ = fromPos.z
        } else {
            // Check if we can throw above obstacle
            val tile = getTile(Position(fromPos.x, fromPos.y, fromPos.z - 1))
            if ((tile != null && (tile.ground != null || tile.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE))) ||
                !checkSightLine(Position(fromPos.x, fromPos.y, fromPos.z - 1), Position(toPos.x, toPos.y, toPos.z - 1))
            ) {
                return false
            }

            // We can throw above obstacle
            if (fromPos.z > toPos.z) {
                return true
            }

            startZ = (fromPos.z - 1) and 0xFF
        }

        // now we need to perform a jump between floors to see if everything is clear (literally)
        while (startZ != toPos.z) {
            val tile = getTile(Position(toPos.x, toPos.y, startZ))
            if (tile != null && (tile.ground != null || tile.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE))) {
                return false
            }
            startZ = (startZ + 1) and 0xFF
        }
        return true
    }

    /** `Map::canWalkTo` (src/map/map.cpp:1000-1007). */
    fun canWalkTo(creature: Creature?, pos: Position): Tile? {
        if (creature == null || creature.isRemoved()) {
            return null
        }

        return getPathfindingTile(creature, creature.getTile(), pos)
    }

    /** `Map::getPathMatching` (src/map/map.cpp:1009-1192). */
    fun getPathMatching(
        creature: Creature?,
        targetPos: Position,
        dirList: MutableList<Direction>,
        pathCondition: FrozenPathingConditionCall,
        fpp: FindPathParams,
    ): Boolean {
        val withoutCreature = creature == null
        if (!withoutCreature && creature.isRemoved()) {
            return false
        }

        var pos = if (withoutCreature) targetPos else creature.position
        var endPos = Position(0, 0, 0)

        val creatureTile = if (withoutCreature) null else creature.getTile()
        val startTile = if (withoutCreature || creatureTile == null) getTile(pos) else creatureTile
        val nodes = AStarNodes(pos.x, pos.y, getTileWalkCost(creature, startTile))

        val bestMatch = BestMatchDistance(0)

        val startPos = pos

        val pathTargetPos = if (withoutCreature) pathCondition.getTargetPos() else targetPos

        val startDistanceX = abs(pathTargetPos.x - pos.x)
        val startDistanceY = abs(pathTargetPos.y - pos.y)

        var found: AStarNode? = null
        do {
            val n = nodes.getBestNode()
            if (n == null) {
                if (found != null) {
                    break
                }
                return false
            }

            val x = n.x
            val y = n.y
            pos = Position(x, y, pos.z)
            if (pathCondition(startPos, pos, fpp, bestMatch)) {
                found = n
                endPos = pos
                if (bestMatch.value == 0) {
                    break
                }
            }

            val neighbors: Array<IntArray>
            val dirCount: Int
            val parent = n.parent
            if (parent != null) {
                val offsetX = parent.x - x
                val offsetY = parent.y - y
                val incomingDirection = when {
                    offsetY == 0 -> if (offsetX == -1) Direction.West else Direction.East
                    offsetX == 0 -> if (offsetY == -1) Direction.North else Direction.South
                    offsetY == -1 -> if (offsetX == -1) Direction.NorthWest else Direction.NorthEast
                    offsetX == -1 -> Direction.SouthWest
                    else -> Direction.SouthEast
                }
                neighbors = DIRECTION_NEIGHBORS[incomingDirection.ordinal]
                dirCount = 5
            } else {
                dirCount = 8
                neighbors = ALL_NEIGHBORS
            }

            val f = n.f
            for (i in 0 until dirCount) {
                val neighborX = x + neighbors[i][0]
                val neighborY = y + neighbors[i][1]
                pos = Position(neighborX, neighborY, pos.z)

                val neighborNode = nodes.getNodeByPosition(neighborX, neighborY)
                val extraCost: Int
                if (neighborNode != null) {
                    extraCost = neighborNode.c
                } else {
                    val tile = if (withoutCreature) {
                        getTile(pos)
                    } else {
                        getPathfindingTile(creature, creatureTile, pos)
                    }
                    if (tile == null) {
                        continue
                    }
                    extraCost = getTileWalkCost(creature, tile)
                }

                // The cost (g) for this neighbor
                val cost = AStarNodes.getMapWalkCost(n, pos)
                val newf = f + cost + extraCost
                if (neighborNode != null) {
                    if (neighborNode.f <= newf) {
                        // The node on the closed/open list is cheaper than this one
                        continue
                    }
                    neighborNode.f = newf
                    neighborNode.parent = n
                    nodes.openNode(neighborNode)
                } else {
                    // Does not exist in the open/closed list, create a new node
                    val dX = abs(pathTargetPos.x - neighborX)
                    val dY = abs(pathTargetPos.y - neighborY)
                    val heuristic = ((dX - startDistanceX) shl 3) + ((dY - startDistanceY) shl 3) + (maxOf(dX, dY) shl 3)
                    if (!nodes.createOpenNode(n, neighborX, neighborY, newf, heuristic, extraCost)) {
                        if (found != null) {
                            break
                        }
                        return false
                    }
                }
            }
            nodes.closeNode(n)
        } while (nodes.getClosedNodes() < 100)

        if (found == null) {
            return false
        }

        var previousX = endPos.x
        var previousY = endPos.y

        var current = found.parent
        while (current != null) {
            val currentX = current.x
            val currentY = current.y

            val dx = currentX - previousX
            val dy = currentY - previousY

            previousX = currentX
            previousY = currentY
            if (dx == 1) {
                when (dy) {
                    1 -> dirList.add(Direction.NorthWest)
                    -1 -> dirList.add(Direction.SouthWest)
                    else -> dirList.add(Direction.West)
                }
            } else if (dx == -1) {
                when (dy) {
                    1 -> dirList.add(Direction.NorthEast)
                    -1 -> dirList.add(Direction.SouthEast)
                    else -> dirList.add(Direction.East)
                }
            } else if (dy == 1) {
                dirList.add(Direction.North)
            } else if (dy == -1) {
                dirList.add(Direction.South)
            }
            current = current.parent
        }

        return true
    }

    /** `Map::getPathMatching` (src/map/map.cpp:1194-1196). */
    fun getPathMatching(
        creature: Creature,
        dirList: MutableList<Direction>,
        pathCondition: FrozenPathingConditionCall,
        fpp: FindPathParams,
    ): Boolean = getPathMatching(creature, creature.position, dirList, pathCondition, fpp)

    /** `Map::getPathMatching` (src/map/map.hpp:159-161). */
    fun getPathMatching(
        startPos: Position,
        dirList: MutableList<Direction>,
        pathCondition: FrozenPathingConditionCall,
        fpp: FindPathParams,
    ): Boolean = getPathMatching(null, startPos, dirList, pathCondition, fpp)

    /**
     * `AStarNodes::getTileWalkCost` (src/map/utils/astarnodes.cpp:279-297).
     *
     * The field-damage term is a PARITY GAP: `MagicField::getCombatType`,
     * `Creature::isImmune(CombatType)`, `Creature::hasCondition(CombatType)` and
     * `Monster::canWalkOnFieldType` are not modelled, and
     * `AStarNodes::getTileWalkCost` itself is owned by the pathfinding wave.
     */
    private fun getTileWalkCost(creature: Creature?, tile: Tile?): Int {
        var cost = 0

        if (creature != null && tile != null) {
            // Destroy creature cost
            if (tile.getTopVisibleCreature(creature) != null) {
                cost += MAP_NORMALWALKCOST * 4
            }
        }

        return cost
    }

    /**
     * `getPathfindingTile` (src/map/map.cpp:129-144, anonymous namespace).
     *
     * The C++ `MapCacheFloorCursor` hotpath hint is not modelled; tile lookups
     * go through [getTile].
     */
    private fun getPathfindingTile(creature: Creature, creatureTile: Tile?, pos: Position): Tile? {
        val tile = getTile(pos)
        if (creatureTile !== tile) {
            if (tile == null ||
                tile.queryAdd(0, creature, 1, CylinderFlags.FLAG_PATHFINDING or CylinderFlags.FLAG_IGNOREFIELDDAMAGE) !=
                ReturnValue.NOERROR
            ) {
                return null
            }
        }

        return tile
    }

    companion object {
        /** `SightLines_t` (src/map/map_definitions.hpp:33-38). */
        const val SightLine_NoCheck = 0
        const val SightLine_CheckSightLine = 1 shl 0
        const val SightLine_FloorCheck = 1 shl 1
        const val SightLine_CheckSightLineAndFloor = SightLine_CheckSightLine or SightLine_FloorCheck

        /** `MAP_MAX_CLIENT_VIEW_PORT_X` (src/map/map_const.hpp:12). */
        const val MAP_MAX_CLIENT_VIEW_PORT_X = 8

        /** `MAP_MAX_CLIENT_VIEW_PORT_Y` (src/map/map_const.hpp:13). */
        const val MAP_MAX_CLIENT_VIEW_PORT_Y = 6

        /** `MAP_INIT_SURFACE_LAYER` (src/map/map_const.hpp:18). */
        const val MAP_INIT_SURFACE_LAYER = 7

        /** `MAP_LAYER_VIEW_LIMIT` (src/map/map_const.hpp:19). */
        const val MAP_LAYER_VIEW_LIMIT = 2

        /** `AStarNodes::MAP_NORMALWALKCOST` (src/map/utils/astarnodes.hpp:38). */
        private const val MAP_NORMALWALKCOST = 10

        /** `Map::placeCreature` extended search ring (src/map/map.cpp:588-601). */
        private val EXTENDED_REL_LIST = listOf(
            0 to -2,
            -1 to -1, 0 to -1, 1 to -1,
            -2 to 0, -1 to 0, 1 to 0, 2 to 0,
            -1 to 1, 0 to 1, 1 to 1,
            0 to 2,
        )

        /** `Map::placeCreature` normal search ring (src/map/map.cpp:603-605). */
        private val NORMAL_REL_LIST = listOf(
            -1 to -1, 0 to -1, 1 to -1,
            -1 to 0, 1 to 0,
            -1 to 1, 0 to 1, 1 to 1,
        )
    }
}

/** `Map::getPathMatching` neighbor tables (src/map/map.cpp:1010-1023). */
private val ALL_NEIGHBORS: Array<IntArray> = arrayOf(
    intArrayOf(-1, 0),
    intArrayOf(0, 1),
    intArrayOf(1, 0),
    intArrayOf(0, -1),
    intArrayOf(-1, -1),
    intArrayOf(1, -1),
    intArrayOf(1, 1),
    intArrayOf(-1, 1),
)

/** Indexed by the [Direction] ordinal, exactly like the C++ `Direction` enum values. */
private val DIRECTION_NEIGHBORS: Array<Array<IntArray>> = arrayOf(
    arrayOf(intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(-1, 1)),
    arrayOf(intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(-1, 1)),
    arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(1, -1)),
    arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(1, -1), intArrayOf(1, 1)),
    arrayOf(intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(1, 1)),
    arrayOf(intArrayOf(-1, 0), intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(-1, 1)),
    arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(1, 1), intArrayOf(-1, 1)),
    arrayOf(intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(-1, -1), intArrayOf(1, 1), intArrayOf(-1, 1)),
)
