package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FindPathParams
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.systems.rewardchest.ReturnValue
import kotlin.math.abs

/**
 * Transcripts of the `Map` methods ported from `src/map/map.cpp`:
 * `canThrowObjectTo` (816-843), `checkSightLine` (845-948),
 * `isSightClear` (950-998), `canWalkTo` (1000-1007), `getPathMatching`
 * (1009-1196), `placeCreature` (559-677), `moveCreature` (679-814) and
 * `save` (262-273).
 */
class GameMapTest {

    private val types = ItemTypeRegistry().apply {
        register(ItemType(100, "ground", isGroundTile = true))
        register(ItemType(101, "projectile blocker", blockSolid = true, blockProjectile = true, isPickupable = false))
        register(ItemType(102, "solid item", blockSolid = true, isPickupable = false))
    }

    private fun item(id: Int): Item = Item(id, type = types.get(id))

    private fun GameMap.withGroundTile(position: Position): Tile {
        val tile = getOrCreateTile(position)
        tile.internalAddThing(item(100))
        return tile
    }

    private fun GameMap.withBlockerTile(position: Position): Tile {
        val tile = getOrCreateTile(position)
        tile.internalAddThing(item(100))
        tile.internalAddThing(item(101))
        return tile
    }

    private fun player(id: Long, position: Position): Player =
        Player(id = id, name = "P$id", maxHealth = 100, health = 100, position = position)

    private fun GameMap.fillTiles(minX: Int, maxX: Int, minY: Int, maxY: Int, z: Int) {
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                withGroundTile(Position(x, y, z))
            }
        }
    }

    // --- Map::canThrowObjectTo (src/map/map.cpp:816-843) ----------------------

    @Test
    fun `canThrowObjectTo rejects crossing between underground and surface`() {
        val map = GameMap()

        assertThat(map.canThrowObjectTo(Position(100, 100, 8), Position(100, 100, 7), GameMap.SightLine_NoCheck)).isFalse()
        assertThat(map.canThrowObjectTo(Position(100, 100, 7), Position(100, 100, 8), GameMap.SightLine_NoCheck)).isFalse()
        assertThat(map.canThrowObjectTo(Position(100, 100, 7), Position(100, 100, 9), GameMap.SightLine_NoCheck)).isFalse()
    }

    @Test
    fun `canThrowObjectTo accepts the same floor and at most two floors apart`() {
        val map = GameMap()

        assertThat(map.canThrowObjectTo(Position(100, 100, 7), Position(100, 100, 7), GameMap.SightLine_NoCheck)).isTrue()
        assertThat(map.canThrowObjectTo(Position(100, 100, 8), Position(100, 100, 10), GameMap.SightLine_NoCheck)).isTrue()
        assertThat(map.canThrowObjectTo(Position(100, 100, 8), Position(100, 100, 11), GameMap.SightLine_NoCheck)).isFalse()
    }

    @Test
    fun `canThrowObjectTo discounts the z distance from the horizontal ranges`() {
        val map = GameMap()

        assertThat(map.canThrowObjectTo(Position(100, 100, 8), Position(111, 100, 10), GameMap.SightLine_NoCheck)).isFalse()
        assertThat(
            map.canThrowObjectTo(Position(100, 100, 8), Position(111, 100, 10), GameMap.SightLine_NoCheck, rangex = 9),
        ).isTrue()

        assertThat(map.canThrowObjectTo(Position(100, 100, 8), Position(100, 109, 10), GameMap.SightLine_NoCheck)).isFalse()
        assertThat(
            map.canThrowObjectTo(Position(100, 100, 8), Position(100, 109, 10), GameMap.SightLine_NoCheck, rangey = 7),
        ).isTrue()
    }

    @Test
    fun `canThrowObjectTo only walks the sight line when the flag is set`() {
        val map = GameMap()
        map.withGroundTile(Position(100, 100, 7))
        map.withBlockerTile(Position(101, 100, 7))
        map.withGroundTile(Position(102, 100, 7))
        // Ground below the source stops the "throw above obstacle" fallback, so
        // the blocked direct line is authoritative.
        map.withGroundTile(Position(100, 100, 6))

        assertThat(map.canThrowObjectTo(Position(100, 100, 7), Position(102, 100, 7), GameMap.SightLine_NoCheck)).isTrue()
        assertThat(
            map.canThrowObjectTo(Position(100, 100, 7), Position(102, 100, 7), GameMap.SightLine_CheckSightLine),
        ).isFalse()
    }

    @Test
    fun `isSightClear falls back to the floor below when the direct line is blocked`() {
        val map = GameMap()
        map.withGroundTile(Position(100, 100, 7))
        map.withBlockerTile(Position(101, 100, 7))
        map.withGroundTile(Position(102, 100, 7))

        // Same floor, no tile below: map.cpp:972-996 checks the z - 1 corridor
        // instead of failing, and it is clear.
        assertThat(map.isSightClear(Position(100, 100, 7), Position(102, 100, 7), false)).isTrue()

        // A floor below the source rejects the fallback.
        map.withGroundTile(Position(100, 100, 6))
        assertThat(map.isSightClear(Position(100, 100, 7), Position(102, 100, 7), false)).isFalse()
    }

    @Test
    fun `canThrowObjectTo treats the floor check bit as same-floor only`() {
        val map = GameMap()

        assertThat(
            map.canThrowObjectTo(Position(100, 100, 7), Position(100, 100, 8), GameMap.SightLine_CheckSightLineAndFloor),
        ).isFalse()
        assertThat(
            map.canThrowObjectTo(Position(100, 100, 7), Position(100, 100, 7), GameMap.SightLine_CheckSightLineAndFloor),
        ).isTrue()
    }

    // --- Map::isSightClear (src/map/map.cpp:950-998) ---------------------------

    @Test
    fun `isSightClear ignores the line for the same tile`() {
        val map = GameMap()
        map.withBlockerTile(Position(100, 100, 7))

        assertThat(map.isSightClear(Position(100, 100, 7), Position(100, 100, 7), false)).isTrue()
    }

    @Test
    fun `isSightClear can throw one floor up over an empty column`() {
        val map = GameMap()

        assertThat(map.isSightClear(Position(100, 100, 7), Position(100, 100, 8), false)).isTrue()
    }

    @Test
    fun `isSightClear blocks one floor up when the lower floor has ground`() {
        val map = GameMap()
        map.withGroundTile(Position(100, 100, 7))

        assertThat(map.isSightClear(Position(100, 100, 7), Position(100, 100, 8), false)).isFalse()
    }

    @Test
    fun `isSightClear can throw one floor down into an empty column`() {
        val map = GameMap()

        assertThat(map.isSightClear(Position(100, 100, 7), Position(100, 100, 6), false)).isTrue()
    }

    @Test
    fun `isSightClear blocks throwing down onto ground`() {
        val map = GameMap()
        map.withGroundTile(Position(100, 100, 6))

        assertThat(map.isSightClear(Position(100, 100, 7), Position(100, 100, 6), false)).isFalse()
    }

    @Test
    fun `isSightClear rejects more than one floor down`() {
        val map = GameMap()

        assertThat(map.isSightClear(Position(100, 100, 8), Position(100, 100, 6), false)).isFalse()
    }

    // --- Map::checkSightLine (src/map/map.cpp:845-948) -------------------------

    @Test
    fun `checkSightLine returns true for the same tile`() {
        val map = GameMap()

        assertThat(map.checkSightLine(Position(100, 100, 7), Position(100, 100, 7))).isTrue()
    }

    @Test
    fun `checkSightLine ignores the destination tile`() {
        val map = GameMap()
        map.withBlockerTile(Position(101, 100, 7))

        assertThat(map.checkSightLine(Position(100, 100, 7), Position(101, 100, 7))).isTrue()
    }

    @Test
    fun `checkSightLine rejects a horizontal blocker`() {
        val map = GameMap()
        map.withBlockerTile(Position(101, 100, 7))

        assertThat(map.checkSightLine(Position(100, 100, 7), Position(102, 100, 7))).isFalse()
    }

    @Test
    fun `checkSightLine rejects a vertical blocker`() {
        val map = GameMap()
        map.withBlockerTile(Position(100, 101, 7))

        assertThat(map.checkSightLine(Position(100, 100, 7), Position(100, 102, 7))).isFalse()
    }

    @Test
    fun `checkSightLine rejects a blocker on the exact diagonal`() {
        val map = GameMap()
        map.withBlockerTile(Position(102, 102, 7))

        assertThat(map.checkSightLine(Position(100, 100, 7), Position(105, 105, 7))).isFalse()
    }

    @Test
    fun `checkSightLine follows the Xiaolin Wu tiles for a shallow diagonal`() {
        val blocked = GameMap()
        blocked.withBlockerTile(Position(103, 101, 7))
        assertThat(blocked.checkSightLine(Position(100, 100, 7), Position(105, 103, 7))).isFalse()

        val notVisited = GameMap()
        notVisited.withBlockerTile(Position(102, 100, 7))
        assertThat(notVisited.checkSightLine(Position(100, 100, 7), Position(105, 103, 7))).isTrue()
    }

    // --- Map::canWalkTo (src/map/map.cpp:1000-1007) ---------------------------

    @Test
    fun `canWalkTo rejects a null or removed creature`() {
        val map = GameMap()
        map.withGroundTile(Position(100, 100, 7))

        assertThat(map.canWalkTo(null, Position(101, 100, 7))).isNull()

        val removed = player(1, Position(100, 100, 7))
        removed.setRemoved()
        assertThat(map.canWalkTo(removed, Position(101, 100, 7))).isNull()
    }

    @Test
    fun `canWalkTo returns the creature tile without querying it`() {
        val map = GameMap()
        val tile = map.withGroundTile(Position(100, 100, 7))
        val walker = player(1, Position(100, 100, 7))
        tile.addThing(walker)

        assertThat(map.canWalkTo(walker, Position(100, 100, 7))).isSameAs(tile)
    }

    @Test
    fun `canWalkTo returns the target tile when it is walkable`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val target = map.withGroundTile(Position(101, 100, 7))
        val walker = player(1, Position(100, 100, 7))
        from.addThing(walker)

        assertThat(map.canWalkTo(walker, Position(101, 100, 7))).isSameAs(target)
    }

    @Test
    fun `canWalkTo rejects a missing tile and a creature-occupied tile`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val occupied = map.withGroundTile(Position(101, 100, 7))
        val walker = player(1, Position(100, 100, 7))
        from.addThing(walker)
        occupied.addThing(player(2, Position(101, 100, 7)))

        assertThat(map.canWalkTo(walker, Position(105, 100, 7))).isNull()
        assertThat(map.canWalkTo(walker, Position(101, 100, 7))).isNull()
    }

    @Test
    fun `canWalkTo rejects floor change tiles for pathfinding`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val stairs = map.withGroundTile(Position(101, 100, 7))
        stairs.setFlag(TileFlags.TILESTATE_FLOORCHANGE.value)
        val walker = player(1, Position(100, 100, 7))
        from.addThing(walker)

        assertThat(map.canWalkTo(walker, Position(101, 100, 7))).isNull()
    }

    // --- Map::getPathMatching (src/map/map.cpp:1009-1196) ---------------------

    @Test
    fun `getPathMatching walks a straight corridor`() {
        val map = GameMap()
        map.fillTiles(100, 110, 100, 100, 7)
        val start = Position(100, 100, 7)
        val creature = player(1, start)
        map.getTile(start)!!.addThing(creature)

        val dirList = mutableListOf<Direction>()
        val fpp = FindPathParams().apply {
            minTargetDist = 1
            maxTargetDist = 1
        }
        val condition = FrozenPathingConditionCall(Position(105, 100, 7), map)

        assertThat(map.getPathMatching(creature, Position(105, 100, 7), dirList, condition, fpp)).isTrue()
        assertThat(dirList).containsExactly(Direction.East, Direction.East, Direction.East, Direction.East)
    }

    @Test
    fun `getPathMatching without a creature starts from the given position`() {
        val map = GameMap()
        map.fillTiles(100, 110, 100, 100, 7)

        val dirList = mutableListOf<Direction>()
        val fpp = FindPathParams().apply {
            minTargetDist = 1
            maxTargetDist = 1
        }
        val condition = FrozenPathingConditionCall(Position(103, 100, 7), map)

        assertThat(map.getPathMatching(Position(100, 100, 7), dirList, condition, fpp)).isTrue()
        assertThat(dirList).containsExactly(Direction.East, Direction.East)
    }

    @Test
    fun `getPathMatching rejects a removed creature`() {
        val map = GameMap()
        map.fillTiles(100, 110, 100, 100, 7)
        val start = Position(100, 100, 7)
        val creature = player(1, start)
        map.getTile(start)!!.addThing(creature)
        creature.setRemoved()

        val dirList = mutableListOf<Direction>()
        val fpp = FindPathParams().apply {
            minTargetDist = 1
            maxTargetDist = 1
        }
        val condition = FrozenPathingConditionCall(Position(103, 100, 7), map)

        assertThat(map.getPathMatching(creature, Position(103, 100, 7), dirList, condition, fpp)).isFalse()
        assertThat(dirList).isEmpty()
    }

    @Test
    fun `getPathMatching fails when the target is walled off`() {
        val map = GameMap()
        map.fillTiles(100, 102, 98, 102, 7)
        for (y in 98..102) {
            map.getOrCreateTile(Position(103, y, 7))
        }
        for (x in 104..105) {
            for (y in 98..102) {
                map.getOrCreateTile(Position(x, y, 7))
            }
        }
        val start = Position(100, 100, 7)
        val creature = player(1, start)
        map.getTile(start)!!.addThing(creature)

        val dirList = mutableListOf<Direction>()
        val fpp = FindPathParams().apply {
            minTargetDist = 1
            maxTargetDist = 1
        }
        val condition = FrozenPathingConditionCall(Position(105, 100, 7), map)

        assertThat(map.getPathMatching(creature, Position(105, 100, 7), dirList, condition, fpp)).isFalse()
        assertThat(dirList).isEmpty()
    }

    @Test
    fun `getPathMatching routes around blocked tiles`() {
        val map = GameMap()
        map.fillTiles(100, 108, 99, 101, 7)
        map.getOrCreateTile(Position(102, 100, 7)).setFlag(TileFlags.TILESTATE_BLOCKSOLID.value)
        map.getOrCreateTile(Position(103, 100, 7)).setFlag(TileFlags.TILESTATE_BLOCKSOLID.value)
        val start = Position(100, 100, 7)
        val target = Position(105, 100, 7)
        val creature = player(1, start)
        map.getTile(start)!!.addThing(creature)

        val dirList = mutableListOf<Direction>()
        val fpp = FindPathParams().apply {
            minTargetDist = 1
            maxTargetDist = 1
        }
        val condition = FrozenPathingConditionCall(target, map)

        assertThat(map.getPathMatching(creature, target, dirList, condition, fpp)).isTrue()
        assertThat(dirList).hasSize(5)

        val visited = mutableListOf<Position>()
        var pos = start
        for (direction in dirList.reversed()) {
            pos = direction.offset(pos)
            visited.add(pos)
        }
        assertThat(visited).doesNotContain(Position(102, 100, 7), Position(103, 100, 7))
        assertThat(maxOf(abs(pos.x - target.x), abs(pos.y - target.y))).isEqualTo(1)
    }

    // --- Map::placeCreature (src/map/map.cpp:559-677) --------------------------

    @Test
    fun `placeCreature uses the center tile when it is walkable`() {
        val map = GameMap()
        val center = map.withGroundTile(Position(100, 100, 7))
        val creature = player(1, Position(0, 0, 7))

        assertThat(map.placeCreature(Position(100, 100, 7), creature)).isTrue()
        assertThat(creature.getTile()).isSameAs(center)
        assertThat(center.getCreatures()).containsExactly(creature)
        assertThat(creature.position).isEqualTo(Position(100, 100, 7))
    }

    @Test
    fun `placeCreature returns false without any tile`() {
        val map = GameMap()
        val creature = player(1, Position(0, 0, 7))

        assertThat(map.placeCreature(Position(100, 100, 7), creature)).isFalse()
        assertThat(creature.getTile()).isNull()
    }

    @Test
    fun `placeCreature falls back to a surrounding tile when the center is blocked`() {
        val map = GameMap()
        val center = map.withGroundTile(Position(100, 100, 7))
        center.internalAddThing(item(102))

        val neighbors = mutableListOf<Position>()
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) {
                    continue
                }
                val pos = Position(100 + dx, 100 + dy, 7)
                neighbors.add(pos)
                map.withGroundTile(pos)
            }
        }

        val creature = player(1, Position(0, 0, 7))
        assertThat(map.placeCreature(Position(100, 100, 7), creature)).isTrue()
        assertThat(neighbors).contains(creature.position)
        assertThat(map.getTile(creature.position)).isSameAs(creature.getTile())
        assertThat(center.getCreatures()).isEmpty()
    }

    @Test
    fun `placeCreature keeps the center protection zone during the fallback`() {
        val map = GameMap()
        val center = map.withGroundTile(Position(100, 100, 7))
        center.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        center.internalAddThing(item(102))
        val outsidePz = map.withGroundTile(Position(101, 100, 7))

        val creature = player(1, Position(0, 0, 7))
        assertThat(map.placeCreature(Position(100, 100, 7), creature)).isFalse()

        outsidePz.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        assertThat(map.placeCreature(Position(100, 100, 7), creature)).isTrue()
        assertThat(creature.getTile()).isSameAs(outsidePz)
    }

    @Test
    fun `placeCreature never falls back onto a teleport tile`() {
        val map = GameMap()
        val center = map.withGroundTile(Position(100, 100, 7))
        center.internalAddThing(item(102))
        val teleport = map.withGroundTile(Position(101, 100, 7))
        teleport.setFlag(TileFlags.TILESTATE_TELEPORT.value)

        val creature = player(1, Position(0, 0, 7))
        assertThat(map.placeCreature(Position(100, 100, 7), creature)).isFalse()
        assertThat(creature.getTile()).isNull()
    }

    @Test
    fun `placeCreature forceLogin bypasses the blocked center tile`() {
        val map = GameMap()
        val center = map.withGroundTile(Position(100, 100, 7))
        center.internalAddThing(item(102))
        val creature = player(1, Position(0, 0, 7))

        assertThat(map.placeCreature(Position(100, 100, 7), creature, forceLogin = true)).isTrue()
        assertThat(creature.getTile()).isSameAs(center)
        assertThat(creature.position).isEqualTo(Position(100, 100, 7))
    }

    @Test
    fun `placeCreature extended search requires sight from the center`() {
        val blocked = GameMap()
        val centerBlocked = blocked.withGroundTile(Position(100, 100, 7))
        centerBlocked.internalAddThing(item(102))
        // Ground below the center stops the isSightClear floor-below fallback.
        blocked.withGroundTile(Position(100, 100, 6))
        blocked.withBlockerTile(Position(100, 99, 7))
        blocked.withGroundTile(Position(100, 98, 7))

        assertThat(blocked.placeCreature(Position(100, 100, 7), player(1, Position(0, 0, 7)), extendedPos = true)).isFalse()

        val clear = GameMap()
        val centerClear = clear.withGroundTile(Position(100, 100, 7))
        centerClear.internalAddThing(item(102))
        val far = clear.withGroundTile(Position(100, 98, 7))

        assertThat(clear.placeCreature(Position(100, 100, 7), player(2, Position(0, 0, 7)), extendedPos = true)).isTrue()
        assertThat(clear.getTile(Position(100, 98, 7))).isSameAs(far)
        assertThat(far.getCreatures()).hasSize(1)
    }

    // --- Map::moveCreature (src/map/map.cpp:679-814) ---------------------------

    @Test
    fun `moveCreature moves the creature to the new tile and faces the step`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val to = map.withGroundTile(Position(101, 100, 7))
        val creature = player(1, Position(100, 100, 7))
        from.addThing(creature)

        map.moveCreature(creature, to)

        assertThat(from.getCreatures()).isEmpty()
        assertThat(to.getCreatures()).containsExactly(creature)
        assertThat(creature.getTile()).isSameAs(to)
        assertThat(creature.position).isEqualTo(Position(101, 100, 7))
        assertThat(creature.direction).isEqualTo(Direction.East)
    }

    @Test
    fun `moveCreature applies the x axis last on diagonal steps`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val to = map.withGroundTile(Position(101, 101, 7))
        val creature = player(1, Position(100, 100, 7))
        from.addThing(creature)

        map.moveCreature(creature, to)

        assertThat(creature.direction).isEqualTo(Direction.East)
    }

    @Test
    fun `moveCreature ignores null, removed and same-tile moves`() {
        val map = GameMap()
        val tile = map.withGroundTile(Position(100, 100, 7))
        val creature = player(1, Position(100, 100, 7))
        tile.addThing(creature)

        map.moveCreature(null, tile)
        map.moveCreature(creature, null)
        map.moveCreature(creature, tile)
        assertThat(creature.getTile()).isSameAs(tile)

        creature.setRemoved()
        map.moveCreature(creature, map.withGroundTile(Position(101, 100, 7)))
        assertThat(creature.getTile()).isSameAs(tile)
    }

    @Test
    fun `moveCreature stops on a vetoed zone transition`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val to = map.withGroundTile(Position(101, 100, 7))
        val creature = player(1, Position(100, 100, 7))
        from.addThing(creature)
        map.beforeCreatureZoneChange = { _, _, _ -> ReturnValue.NOTPOSSIBLE }

        map.moveCreature(creature, to)

        assertThat(creature.getTile()).isSameAs(from)
        assertThat(to.getCreatures()).isEmpty()
    }

    @Test
    fun `moveCreature reports the zone transition after the move`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val to = map.withGroundTile(Position(101, 100, 7))
        val leaving = map.zones.addZone("leaving")!!
        from.addZone(leaving)
        val creature = player(1, Position(100, 100, 7))
        from.addThing(creature)

        var seenFrom: Set<Zone>? = null
        var seenTo: Set<Zone>? = null
        map.afterCreatureZoneChange = { _, fromZones, toZones ->
            seenFrom = fromZones
            seenTo = toZones
        }

        map.moveCreature(creature, to)

        assertThat(seenFrom).containsExactly(leaving)
        assertThat(seenTo).isEmpty()
    }

    @Test
    fun `moveCreature runs the post-move events through the dispatcher seam`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val to = map.withGroundTile(Position(101, 100, 7))
        val creature = player(1, Position(100, 100, 7))
        from.addThing(creature)

        var deferred: (() -> Unit)? = null
        var afterRuns = 0
        map.afterCreatureZoneChange = { _, _, _ -> afterRuns++ }
        map.scheduleMoveEvents = { deferred = it }

        map.moveCreature(creature, to)

        // The move itself commits before the deferred events run.
        assertThat(creature.getTile()).isSameAs(to)
        assertThat(afterRuns).isZero()
        assertThat(deferred != null).isTrue()
        deferred!!.invoke()
        assertThat(afterRuns).isEqualTo(1)
    }

    @Test
    fun `moveCreature skips the facing update for non-adjacent moves`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val to = map.withGroundTile(Position(103, 100, 7))
        val creature = player(1, Position(100, 100, 7))
        from.addThing(creature)
        creature.direction = Direction.North

        map.moveCreature(creature, to)

        assertThat(creature.direction).isEqualTo(Direction.North)
        assertThat(creature.getTile()).isSameAs(to)
    }

    @Test
    fun `moveCreature forceTeleport keeps the facing`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val to = map.withGroundTile(Position(101, 100, 7))
        val creature = player(1, Position(100, 100, 7))
        from.addThing(creature)
        creature.direction = Direction.North

        map.moveCreature(creature, to, forceTeleport = true)

        assertThat(creature.direction).isEqualTo(Direction.North)
    }

    // --- Map::save (src/map/map.cpp:262-273) -----------------------------------

    @Test
    fun `save fails without a configured serializer`() {
        assertThat(GameMap().save()).isFalse()
    }

    @Test
    fun `save retries six times when house items fail`() {
        val map = GameMap()
        var infoCalls = 0
        var itemCalls = 0
        map.saveHouseInfo = {
            infoCalls++
            true
        }
        map.saveHouseItems = {
            itemCalls++
            false
        }

        assertThat(map.save()).isFalse()
        assertThat(infoCalls).isEqualTo(6)
        assertThat(itemCalls).isEqualTo(6)
    }

    @Test
    fun `save never calls house items before house info succeeds`() {
        val map = GameMap()
        var infoCalls = 0
        var itemCalls = 0
        map.saveHouseInfo = {
            infoCalls++
            false
        }
        map.saveHouseItems = {
            itemCalls++
            true
        }

        assertThat(map.save()).isFalse()
        assertThat(infoCalls).isEqualTo(6)
        assertThat(itemCalls).isZero()
    }

    @Test
    fun `save returns true once house info and items both succeed`() {
        val map = GameMap()
        var infoCalls = 0
        var itemCalls = 0
        map.saveHouseInfo = {
            infoCalls++
            infoCalls > 2
        }
        map.saveHouseItems = {
            itemCalls++
            true
        }

        assertThat(map.save()).isTrue()
        assertThat(infoCalls).isEqualTo(3)
        assertThat(itemCalls).isEqualTo(1)
    }
}
