package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.getNextPosition
import org.opentibia.canary.map.Position
import kotlin.math.abs
import kotlin.math.max

/**
 * `Monster::getNextStep` / `Creature::getNextStep` out state
 * (src/creatures/monsters/monster.cpp:2443-2492). [direction] is
 * `DIRECTION_NONE` as `null`.
 */
class MonsterStepResult(
    val found: Boolean,
    val direction: Direction? = null,
    val flags: Int = 0,
)

/**
 * `Monster::getDistanceStep` result (src/creatures/monsters/monster.cpp:2632-3127):
 * [handled] is the C++ `bool` return (false lets the A* calculate) and
 * [direction] is `DIRECTION_NONE` as `null`.
 */
class DistanceStepResult(
    val handled: Boolean,
    val direction: Direction? = null,
)

/**
 * Item facts of the `Monster::pushItems` scan (src/creatures/monsters/monster.cpp:2340-2385):
 * [fromPosition] is `item->getTile()->getPosition()`, [onTile] `/.../ item->getTile() != tile`,
 * and the flags mirror `hasProperty`/`canBeMoved`/`isCorpse`.
 */
class PushItemFacts(
    val id: Int = 0,
    val fromPosition: Position? = null,
    val tileHasHouse: Boolean = false,
    val onTile: Boolean = true,
    val isMovable: Boolean = true,
    val canBeMoved: Boolean = true,
    val hasBlockPath: Boolean = false,
    val hasBlockSolid: Boolean = false,
    val isCorpse: Boolean = false,
)

/** `Monster::pushItems` inputs (src/creatures/monsters/monster.cpp:2340-2385). */
class MonsterPushItemsRequest(
    val items: List<PushItemFacts> = emptyList(),
    val tilePosition: Position = Position(0, 0, 0),
    val tileHasHouse: Boolean = false,
    val nextDirection: Direction,
    val canThrowObjectTo: (Position, Position) -> Boolean = { _, _ -> true },
    val moveItem: (PushItemFacts, Position) -> Boolean = { _, _ -> false },
    val removeItem: (PushItemFacts) -> Boolean = { false },
    val addMagicEffect: (Position, Int) -> Unit = { _, _ -> },
)

/** Creature facts of the `Monster::pushCreatures` scan (monster.cpp:2405-2441). */
class PushedCreature(
    val id: Long = 0,
    val position: Position = Position(0, 0, 0),
    val isMonster: Boolean = false,
    val isPushable: Boolean = false,
    val health: Int = 1,
)

/** `Monster::pushCreatures` inputs (src/creatures/monsters/monster.cpp:2405-2441). */
class MonsterPushCreaturesRequest(
    val tilePosition: Position = Position(0, 0, 0),
    val creatures: List<PushedCreature> = emptyList(),
    val shuffleDirections: (MutableList<Direction>) -> Unit = { it.shuffle() },
    val destinationHasBlockPath: (Position) -> Boolean = { false },
    val moveCreature: (PushedCreature, Direction) -> Boolean = { _, _ -> false },
    val changeHealth: (PushedCreature, Int) -> Unit = { _, _ -> },
    val setDropLoot: (PushedCreature, Boolean) -> Unit = { _, _ -> },
    val addMagicEffect: (Position, Int) -> Unit = { _, _ -> },
)

/**
 * Port of the `Monster` walking rules:
 * - `Monster::getNextStep` (monster.cpp:2443-2492) over `Creature::getNextStep`
 *   (creature.cpp:310-319): follow > walk-back > random step, then the optional
 *   item/creature pushes at the destination.
 * - `Monster::doFollowCreature` (monster.cpp:2530-2550) target dancing.
 * - `Monster::getDistanceStep` (monster.cpp:2632-3127) melee approach/retreat.
 * - `Monster::canWalkTo` (monster.cpp:3211-3226).
 * - `Monster::pushItem(s)`/`pushCreature(s)` (monster.cpp:2312-2441).
 * - `Monster::getPushItemLocationOptions` (monster.cpp:3949-3970).
 * - `Creature::onWalk` (creature.cpp:291-301) drunk stumbling.
 *
 * PARITY GAPS: `Creature::getNextStep`'s path queue
 * (`listWalkDir`/`onWalk` integration) and `Map::queryAdd`/`internalMoveItem`
 * stay behind injected seams; `Monster::pushItems` models the down-item scan
 * through [PushItemFacts] instead of the live tile.
 */
object MonsterWalk {
    /** `DIRECTION_DIAGONAL_MASK` (src/game/movement/position.hpp:18). */
    const val DIRECTION_DIAGONAL_MASK = 4

    /** Number of item moves before `Monster::pushItems` switches to removal. */
    private const val MAX_ITEM_MOVES = 20

    /** Number of item removals before `Monster::pushItems` gives up. */
    private const val MAX_ITEM_REMOVALS = 10

    /** `Monster::getPushItemLocationOptions` (monster.cpp:3949-3970). */
    fun getPushItemLocationOptions(direction: Direction): List<Pair<Int, Int>> = when (direction) {
        Direction.West, Direction.East -> listOf(0 to -1, 0 to 1)
        Direction.North, Direction.South -> listOf(-1 to 0, 1 to 0)
        Direction.NorthWest -> listOf(0 to -1, -1 to 0)
        Direction.NorthEast -> listOf(0 to -1, 1 to 0)
        Direction.SouthWest -> listOf(0 to 1, -1 to 0)
        Direction.SouthEast -> listOf(0 to 1, 1 to 0)
    }

    /**
     * `Monster::canWalkTo` (monster.cpp:3211-3226): the destination must stay in
     * the spawn range and its tile must be walkable (`getTopVisibleCreature` is
     * the monster itself and `queryAdd` passes with
     * `FLAG_PATHFINDING | FLAG_IGNOREFIELDDAMAGE`).
     */
    fun canWalkTo(
        pos: Position,
        moveDirection: Direction,
        isInSpawnRange: (Position) -> Boolean,
        isTileWalkable: (Position) -> Boolean,
    ): Boolean {
        val destination = getNextPosition(moveDirection, pos)
        if (!isInSpawnRange(destination)) {
            return false
        }
        return isTileWalkable(destination)
    }

    /** `Monster::isTargetNearby` (src/creatures/monsters/monster.cpp:3128-3130). */
    fun isTargetNearby(state: MonsterBehaviorState): Boolean = state.stepDuration >= 1

    /**
     * `Creature::onWalk` (creature.cpp:291-301): a drunk creature rolls
     * `uniform_random(0, 60)`; a roll at or below the diagonal mask shouts
     * "Hicks!" and a roll strictly below it replaces the step direction with the
     * rolled cardinal direction. [roll] returns `uniform_random(0, 60)`.
     */
    fun onWalk(
        dir: Direction,
        hasDrunkCondition: Boolean,
        roll: () -> Int,
        say: (String) -> Unit = {},
    ): Direction {
        if (!hasDrunkCondition) {
            return dir
        }

        val value = roll()
        if (value > DIRECTION_DIAGONAL_MASK) {
            return dir
        }

        say("Hicks!")
        if (value < DIRECTION_DIAGONAL_MASK) {
            return Direction.entries[value]
        }
        return dir
    }

    /**
     * `Monster::pushItem` (monster.cpp:2312-2338): tries the two side drop
     * positions of [getPushItemLocationOptions] and moves the item to the first
     * one that is throwable-to and movable.
     */
    fun pushItem(
        item: PushItemFacts?,
        direction: Direction,
        canThrowObjectTo: (Position, Position) -> Boolean = { _, _ -> true },
        moveItem: (PushItemFacts, Position) -> Boolean = { _, _ -> false },
    ): Boolean {
        if (item == null) {
            return false
        }

        val fromPos = item.fromPosition ?: return false
        if (item.tileHasHouse) {
            return false
        }

        for ((dx, dy) in getPushItemLocationOptions(direction)) {
            val toPos = Position(fromPos.x + dx, fromPos.y + dy, fromPos.z)
            if (canThrowObjectTo(fromPos, toPos) && moveItem(item, toPos)) {
                return true
            }
        }
        return false
    }

    /**
     * `Monster::pushItems` (monster.cpp:2340-2385): scans the down items in
     * reverse, moving at most [MAX_ITEM_MOVES] movable path/solid blockers and
     * removing at most [MAX_ITEM_REMOVALS] non-corpse leftovers; any removal
     * emits the poff effect.
     */
    fun pushItems(request: MonsterPushItemsRequest) {
        if (request.tileHasHouse || request.items.isEmpty()) {
            return
        }

        var moveCount = 0
        var removeCount = 0

        for (index in request.items.indices.reversed()) {
            val item = request.items[index]
            if (!item.isMovable || !item.canBeMoved) {
                continue
            }
            if (!item.onTile) {
                continue
            }
            if (!item.hasBlockPath && !item.hasBlockSolid) {
                continue
            }

            if (moveCount < MAX_ITEM_MOVES && pushItem(item, request.nextDirection, request.canThrowObjectTo, request.moveItem)) {
                moveCount++
            } else if (removeCount < MAX_ITEM_REMOVALS && !item.isCorpse && request.removeItem(item)) {
                removeCount++
            }
        }

        if (removeCount > 0) {
            request.addMagicEffect(request.tilePosition, MonsterBehavior.CONST_ME_POFF)
        }
    }

    /**
     * `Monster::pushCreature` (monster.cpp:2387-2403): shuffles the fixed
     * `{NORTH, WEST, EAST, SOUTH}` list and moves the creature in the first
     * direction whose destination tile does not block the path.
     */
    fun pushCreature(
        creature: PushedCreature,
        position: Position,
        shuffleDirections: (MutableList<Direction>) -> Unit = { it.shuffle() },
        destinationHasBlockPath: (Position) -> Boolean = { false },
        moveCreature: (PushedCreature, Direction) -> Boolean = { _, _ -> false },
    ): Boolean {
        val dirList = mutableListOf(Direction.North, Direction.West, Direction.East, Direction.South)
        shuffleDirections(dirList)

        for (direction in dirList) {
            val tryPos = getNextPosition(direction, position)
            if (!destinationHasBlockPath(tryPos) && moveCreature(creature, direction)) {
                return true
            }
        }
        return false
    }

    /**
     * `Monster::pushCreatures` (monster.cpp:2405-2441): scans the creatures in
     * reverse, pushing each pushable monster at most once and killing the ones
     * that cannot be pushed; any kill emits the block-hit effect.
     */
    fun pushCreatures(request: MonsterPushCreaturesRequest) {
        var killedCount = 0
        var lastPushedMonster: PushedCreature? = null

        for (index in request.creatures.indices.reversed()) {
            val creature = request.creatures[index]
            if (!creature.isMonster || !creature.isPushable) {
                continue
            }

            if (creature !== lastPushedMonster &&
                pushCreature(
                    creature = creature,
                    position = creature.position,
                    shuffleDirections = request.shuffleDirections,
                    destinationHasBlockPath = request.destinationHasBlockPath,
                    moveCreature = request.moveCreature,
                )
            ) {
                lastPushedMonster = creature
                continue
            }

            request.changeHealth(creature, -creature.health)
            request.setDropLoot(creature, true)
            killedCount++
        }

        if (killedCount > 0) {
            request.addMagicEffect(request.tilePosition, MonsterBehavior.CONST_ME_BLOCKHIT)
        }
    }

    /**
     * `Monster::doFollowCreature` (monster.cpp:2530-2550): stops random stepping,
     * walks the queued creature step (with the pathfinding flag), otherwise
     * clears the ignore-field-damage latch and, when the followed creature is
     * the target, dances (fleeing keeps neither attack nor distance).
     */
    fun doFollowCreature(
        state: MonsterBehaviorState,
        creatureStep: () -> MonsterStepResult,
        followedIsAttacked: Boolean,
        isFleeing: Boolean,
        staticAttackChance: Int,
        nextRandom: (Int, Int) -> Int = { min, _ -> min },
        getDanceStep: (keepAttack: Boolean, keepDistance: Boolean) -> Direction? = { _, _ -> null },
    ): MonsterStepResult {
        state.randomStepping = false
        val step = creatureStep()
        if (step.found) {
            return MonsterStepResult(true, step.direction, step.flags or CylinderFlags.FLAG_PATHFINDING)
        }

        if (state.ignoreFieldDamage) {
            state.ignoreFieldDamage = false
        }

        if (followedIsAttacked) {
            if (isFleeing) {
                val direction = getDanceStep(false, false)
                return MonsterStepResult(direction != null, direction, step.flags)
            } else if (staticAttackChance < nextRandom(1, 100)) {
                val direction = getDanceStep(true, true)
                return MonsterStepResult(direction != null, direction, step.flags)
            }
        }

        return MonsterStepResult(false, step.direction, step.flags)
    }

    /**
     * `Monster::getNextStep` (monster.cpp:2443-2492): idle/dead monsters clear
     * their walk event; otherwise the step comes from follow path, walk-back or
     * the random step, and only a found step may push items/creatures at the
     * destination. A successful deferred creature push keeps the step; a
     * rejected queue entry forces a follow-path update and fails the step.
     */
    fun getNextStep(
        isIdle: Boolean,
        health: Int,
        hasFollowCreature: Boolean,
        hasFollowPath: Boolean,
        isWalkingBack: Boolean,
        canPushItems: Boolean,
        canPushCreatures: Boolean,
        position: Position,
        doFollowCreature: (Int) -> MonsterStepResult,
        doWalkBack: (Int) -> MonsterStepResult,
        doRandomStep: () -> MonsterStepResult,
        isMovementCommit: Boolean,
        tileExists: (Position) -> Boolean = { true },
        pushItems: (Position, Direction) -> Unit = { _, _ -> },
        pushCreatures: (Position) -> Unit = { },
        enqueueCreaturePush: (Position) -> Boolean = { true },
        onEventWalkCleared: () -> Unit = {},
        onForceUpdateFollowPath: () -> Unit = {},
    ): MonsterStepResult {
        if (isIdle || health <= 0) {
            // we dont have anyone watching might aswell stop walking
            onEventWalkCleared()
            return MonsterStepResult(found = false)
        }

        val step = when {
            hasFollowCreature && hasFollowPath -> doFollowCreature(0)
            isWalkingBack -> doWalkBack(0)
            else -> doRandomStep()
        }

        var result = step.found
        val nextDirection = step.direction
        val flags = step.flags

        if (!result || (!canPushItems && !canPushCreatures) || nextDirection == null) {
            return MonsterStepResult(result, nextDirection, flags)
        }

        val pos = getNextPosition(nextDirection, position)
        if (!tileExists(pos)) {
            return MonsterStepResult(result, nextDirection, flags)
        }

        if (canPushItems) {
            pushItems(pos, nextDirection)
        }

        if (!canPushCreatures) {
            return MonsterStepResult(result, nextDirection, flags)
        }

        if (isMovementCommit) {
            pushCreatures(pos)
            return MonsterStepResult(result, nextDirection, flags)
        }

        if (!enqueueCreaturePush(pos)) {
            onForceUpdateFollowPath()
            result = false
        }

        return MonsterStepResult(result, nextDirection, flags)
    }

    /**
     * `Monster::getDistanceStep` (monster.cpp:2632-3127): the hand-written
     * melee approach/retreat. Returns unhandled when the A* should calculate
     * (out of range without flee, or unclear sight); at exactly the target
     * distance it reports handled without a step (the dance step takes over).
     * The step duration is slowed while the target is adjacent and relaxed
     * otherwise.
     */
    fun getDistanceStep(
        state: MonsterBehaviorState,
        creaturePos: Position,
        targetPos: Position,
        targetDistance: Int,
        flee: Boolean,
        canWalkTo: (Position, Direction) -> Boolean,
        isSightClear: (Position, Position) -> Boolean = { _, _ -> true },
        booleanRandom: () -> Boolean = { false },
        getRandomStep: (Position) -> Direction? = { null },
    ): DistanceStepResult {
        val dx = abs(creaturePos.x - targetPos.x)
        val dy = abs(creaturePos.y - targetPos.y)

        val distance = max(dx, dy)
        if (!flee && (distance > targetDistance || !isSightClear(creaturePos, targetPos))) {
            return DistanceStepResult(handled = false) // let the A* calculate it
        } else if (!flee && distance == targetDistance) {
            // we don't really care here, a dancestep will take care of dancing in that position
            return DistanceStepResult(handled = true)
        }

        val offsetx = creaturePos.x - targetPos.x
        val offsety = creaturePos.y - targetPos.y

        if (dx <= 1 && dy <= 1) {
            // target is near: slow down the movement (as a monster)
            if (state.stepDuration < 2) {
                state.stepDuration++
            }
        } else if (state.stepDuration > 0) {
            state.stepDuration--
        }

        if (offsetx == 0 && offsety == 0) {
            // player is "on" the monster: random step and let the rest be handled later
            val direction = getRandomStep(creaturePos)
            return DistanceStepResult(direction != null, direction)
        }

        if (dx == dy) {
            when {
                offsetx >= 1 && offsety >= 1 -> {
                    // target is NW: escape to SE, S or E
                    val south = canWalkTo(creaturePos, Direction.South)
                    val east = canWalkTo(creaturePos, Direction.East)
                    if (south && east) {
                        return DistanceStepResult(true, if (booleanRandom()) Direction.South else Direction.East)
                    } else if (south) {
                        return DistanceStepResult(true, Direction.South)
                    } else if (east) {
                        return DistanceStepResult(true, Direction.East)
                    } else if (canWalkTo(creaturePos, Direction.SouthEast)) {
                        return DistanceStepResult(true, Direction.SouthEast)
                    }

                    val north = canWalkTo(creaturePos, Direction.North)
                    val west = canWalkTo(creaturePos, Direction.West)
                    if (flee) {
                        if (north && west) {
                            return DistanceStepResult(true, if (booleanRandom()) Direction.North else Direction.West)
                        } else if (north) {
                            return DistanceStepResult(true, Direction.North)
                        } else if (west) {
                            return DistanceStepResult(true, Direction.West)
                        }
                    }

                    if (west && canWalkTo(creaturePos, Direction.SouthWest)) {
                        return DistanceStepResult(true, Direction.West)
                    } else if (north && canWalkTo(creaturePos, Direction.NorthEast)) {
                        return DistanceStepResult(true, Direction.North)
                    }
                    return DistanceStepResult(handled = true)
                }

                offsetx <= -1 && offsety <= -1 -> {
                    // target is SE: escape to NW, W or N
                    val west = canWalkTo(creaturePos, Direction.West)
                    val north = canWalkTo(creaturePos, Direction.North)
                    if (west && north) {
                        return DistanceStepResult(true, if (booleanRandom()) Direction.West else Direction.North)
                    } else if (west) {
                        return DistanceStepResult(true, Direction.West)
                    } else if (north) {
                        return DistanceStepResult(true, Direction.North)
                    } else if (canWalkTo(creaturePos, Direction.NorthWest)) {
                        return DistanceStepResult(true, Direction.NorthWest)
                    }

                    val south = canWalkTo(creaturePos, Direction.South)
                    val east = canWalkTo(creaturePos, Direction.East)
                    if (flee) {
                        if (south && east) {
                            return DistanceStepResult(true, if (booleanRandom()) Direction.South else Direction.East)
                        } else if (south) {
                            return DistanceStepResult(true, Direction.South)
                        } else if (east) {
                            return DistanceStepResult(true, Direction.East)
                        }
                    }

                    if (south && canWalkTo(creaturePos, Direction.SouthWest)) {
                        return DistanceStepResult(true, Direction.South)
                    } else if (east && canWalkTo(creaturePos, Direction.NorthEast)) {
                        return DistanceStepResult(true, Direction.East)
                    }
                    return DistanceStepResult(handled = true)
                }

                offsetx >= 1 && offsety <= -1 -> {
                    // target is SW: escape to NE, N or E
                    val north = canWalkTo(creaturePos, Direction.North)
                    val east = canWalkTo(creaturePos, Direction.East)
                    if (north && east) {
                        return DistanceStepResult(true, if (booleanRandom()) Direction.North else Direction.East)
                    } else if (north) {
                        return DistanceStepResult(true, Direction.North)
                    } else if (east) {
                        return DistanceStepResult(true, Direction.East)
                    } else if (canWalkTo(creaturePos, Direction.NorthEast)) {
                        return DistanceStepResult(true, Direction.NorthEast)
                    }

                    val south = canWalkTo(creaturePos, Direction.South)
                    val west = canWalkTo(creaturePos, Direction.West)
                    if (flee) {
                        if (south && west) {
                            return DistanceStepResult(true, if (booleanRandom()) Direction.South else Direction.West)
                        } else if (south) {
                            return DistanceStepResult(true, Direction.South)
                        } else if (west) {
                            return DistanceStepResult(true, Direction.West)
                        }
                    }

                    if (west && canWalkTo(creaturePos, Direction.NorthWest)) {
                        return DistanceStepResult(true, Direction.West)
                    } else if (south && canWalkTo(creaturePos, Direction.SouthEast)) {
                        return DistanceStepResult(true, Direction.South)
                    }
                    return DistanceStepResult(handled = true)
                }

                offsetx <= -1 && offsety >= 1 -> {
                    // target is NE: escape to SW, S or W
                    val west = canWalkTo(creaturePos, Direction.West)
                    val south = canWalkTo(creaturePos, Direction.South)
                    if (west && south) {
                        return DistanceStepResult(true, if (booleanRandom()) Direction.West else Direction.South)
                    } else if (west) {
                        return DistanceStepResult(true, Direction.West)
                    } else if (south) {
                        return DistanceStepResult(true, Direction.South)
                    } else if (canWalkTo(creaturePos, Direction.SouthWest)) {
                        return DistanceStepResult(true, Direction.SouthWest)
                    }

                    val north = canWalkTo(creaturePos, Direction.North)
                    val east = canWalkTo(creaturePos, Direction.East)
                    if (flee) {
                        if (north && east) {
                            return DistanceStepResult(true, if (booleanRandom()) Direction.North else Direction.East)
                        } else if (north) {
                            return DistanceStepResult(true, Direction.North)
                        } else if (east) {
                            return DistanceStepResult(true, Direction.East)
                        }
                    }

                    if (east && canWalkTo(creaturePos, Direction.SouthEast)) {
                        return DistanceStepResult(true, Direction.East)
                    } else if (north && canWalkTo(creaturePos, Direction.NorthWest)) {
                        return DistanceStepResult(true, Direction.North)
                    }
                    return DistanceStepResult(handled = true)
                }
            }
        }

        // The target is not diagonal: decide which side it is on and escape.
        if (dy > dx) {
            val playerDir = if (offsety < 0) Direction.South else Direction.North
            when (playerDir) {
                Direction.North -> {
                    if (canWalkTo(creaturePos, Direction.South)) {
                        return DistanceStepResult(true, Direction.South)
                    }

                    val west = canWalkTo(creaturePos, Direction.West)
                    val east = canWalkTo(creaturePos, Direction.East)
                    if (west && east && offsetx == 0) {
                        return DistanceStepResult(true, if (booleanRandom()) Direction.West else Direction.East)
                    } else if (west && offsetx <= 0) {
                        return DistanceStepResult(true, Direction.West)
                    } else if (east && offsetx >= 0) {
                        return DistanceStepResult(true, Direction.East)
                    }

                    if (flee) {
                        if (west && east) {
                            return DistanceStepResult(true, if (booleanRandom()) Direction.West else Direction.East)
                        } else if (west) {
                            return DistanceStepResult(true, Direction.West)
                        } else if (east) {
                            return DistanceStepResult(true, Direction.East)
                        }
                    }

                    val southWest = canWalkTo(creaturePos, Direction.SouthWest)
                    val southEast = canWalkTo(creaturePos, Direction.SouthEast)
                    if (southWest || southEast) {
                        val direction = when {
                            southWest && southEast -> if (booleanRandom()) Direction.SouthWest else Direction.SouthEast
                            west -> Direction.West
                            southWest -> Direction.SouthWest
                            east -> Direction.East
                            else -> Direction.SouthEast
                        }
                        return DistanceStepResult(true, direction)
                    }

                    if (flee && canWalkTo(creaturePos, Direction.North)) {
                        // towards player, yea
                        return DistanceStepResult(true, Direction.North)
                    }
                }

                Direction.South -> {
                    if (canWalkTo(creaturePos, Direction.North)) {
                        return DistanceStepResult(true, Direction.North)
                    }

                    val west = canWalkTo(creaturePos, Direction.West)
                    val east = canWalkTo(creaturePos, Direction.East)
                    if (west && east && offsetx == 0) {
                        return DistanceStepResult(true, if (booleanRandom()) Direction.West else Direction.East)
                    } else if (west && offsetx <= 0) {
                        return DistanceStepResult(true, Direction.West)
                    } else if (east && offsetx >= 0) {
                        return DistanceStepResult(true, Direction.East)
                    }

                    if (flee) {
                        if (west && east) {
                            return DistanceStepResult(true, if (booleanRandom()) Direction.West else Direction.East)
                        } else if (west) {
                            return DistanceStepResult(true, Direction.West)
                        } else if (east) {
                            return DistanceStepResult(true, Direction.East)
                        }
                    }

                    val northWest = canWalkTo(creaturePos, Direction.NorthWest)
                    val northEast = canWalkTo(creaturePos, Direction.NorthEast)
                    if (northWest || northEast) {
                        val direction = when {
                            northWest && northEast -> if (booleanRandom()) Direction.NorthWest else Direction.NorthEast
                            west -> Direction.West
                            northWest -> Direction.NorthWest
                            east -> Direction.East
                            else -> Direction.NorthEast
                        }
                        return DistanceStepResult(true, direction)
                    }

                    if (flee && canWalkTo(creaturePos, Direction.South)) {
                        // towards player, yea
                        return DistanceStepResult(true, Direction.South)
                    }
                }

                else -> Unit
            }
        } else {
            val playerDir = if (offsetx < 0) Direction.East else Direction.West
            when (playerDir) {
                Direction.West -> {
                    if (canWalkTo(creaturePos, Direction.East)) {
                        return DistanceStepResult(true, Direction.East)
                    }

                    val north = canWalkTo(creaturePos, Direction.North)
                    val south = canWalkTo(creaturePos, Direction.South)
                    if (north && south && offsety == 0) {
                        return DistanceStepResult(true, if (booleanRandom()) Direction.North else Direction.South)
                    } else if (north && offsety <= 0) {
                        return DistanceStepResult(true, Direction.North)
                    } else if (south && offsety >= 0) {
                        return DistanceStepResult(true, Direction.South)
                    }

                    if (flee) {
                        if (north && south) {
                            return DistanceStepResult(true, if (booleanRandom()) Direction.North else Direction.South)
                        } else if (north) {
                            return DistanceStepResult(true, Direction.North)
                        } else if (south) {
                            return DistanceStepResult(true, Direction.South)
                        }
                    }

                    val southEast = canWalkTo(creaturePos, Direction.SouthEast)
                    val northEast = canWalkTo(creaturePos, Direction.NorthEast)
                    if (southEast || northEast) {
                        val direction = when {
                            southEast && northEast -> if (booleanRandom()) Direction.SouthEast else Direction.NorthEast
                            south -> Direction.South
                            southEast -> Direction.SouthEast
                            north -> Direction.North
                            else -> Direction.NorthEast
                        }
                        return DistanceStepResult(true, direction)
                    }

                    if (flee && canWalkTo(creaturePos, Direction.West)) {
                        // towards player, yea
                        return DistanceStepResult(true, Direction.West)
                    }
                }

                Direction.East -> {
                    if (canWalkTo(creaturePos, Direction.West)) {
                        return DistanceStepResult(true, Direction.West)
                    }

                    val north = canWalkTo(creaturePos, Direction.North)
                    val south = canWalkTo(creaturePos, Direction.South)
                    if (north && south && offsety == 0) {
                        return DistanceStepResult(true, if (booleanRandom()) Direction.North else Direction.South)
                    } else if (north && offsety <= 0) {
                        return DistanceStepResult(true, Direction.North)
                    } else if (south && offsety >= 0) {
                        return DistanceStepResult(true, Direction.South)
                    }

                    if (flee) {
                        if (north && south) {
                            return DistanceStepResult(true, if (booleanRandom()) Direction.North else Direction.South)
                        } else if (north) {
                            return DistanceStepResult(true, Direction.North)
                        } else if (south) {
                            return DistanceStepResult(true, Direction.South)
                        }
                    }

                    val northWest = canWalkTo(creaturePos, Direction.NorthWest)
                    val southWest = canWalkTo(creaturePos, Direction.SouthWest)
                    if (northWest || southWest) {
                        val direction = when {
                            northWest && southWest -> if (booleanRandom()) Direction.NorthWest else Direction.SouthWest
                            north -> Direction.North
                            northWest -> Direction.NorthWest
                            south -> Direction.South
                            else -> Direction.SouthWest
                        }
                        return DistanceStepResult(true, direction)
                    }

                    if (flee && canWalkTo(creaturePos, Direction.East)) {
                        // towards player, yea
                        return DistanceStepResult(true, Direction.East)
                    }
                }

                else -> Unit
            }
        }

        return DistanceStepResult(handled = true)
    }
}
