package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position

/**
 * Transcribes the Canary monster walking surface:
 * - `Monster::getNextStep` (src/creatures/monsters/monster.cpp:2443-2492)
 * - `Monster::doFollowCreature` (src/creatures/monsters/monster.cpp:2530-2550)
 * - `Monster::getDistanceStep` (src/creatures/monsters/monster.cpp:2632-3127)
 * - `Monster::canWalkTo` (src/creatures/monsters/monster.cpp:3211-3226)
 * - `Monster::pushItem`/`pushItems`/`pushCreature`/`pushCreatures`
 *   (src/creatures/monsters/monster.cpp:2312-2441)
 * - `Monster::getPushItemLocationOptions` (src/creatures/monsters/monster.cpp:3949-3970)
 * - `Creature::onWalk` (src/creatures/creature.cpp:291-301)
 */
class MonsterWalkTest {

    private val pos = Position(100, 100, 7)

    private fun walkable(vararg directions: Direction): (Position, Direction) -> Boolean =
        { _, direction -> directions.contains(direction) }

    // --- Monster::getPushItemLocationOptions ---

    @Test
    fun `push item location options match the direction table`() {
        assertThat(MonsterWalk.getPushItemLocationOptions(Direction.East)).containsExactly(0 to -1, 0 to 1)
        assertThat(MonsterWalk.getPushItemLocationOptions(Direction.West)).containsExactly(0 to -1, 0 to 1)
        assertThat(MonsterWalk.getPushItemLocationOptions(Direction.North)).containsExactly(-1 to 0, 1 to 0)
        assertThat(MonsterWalk.getPushItemLocationOptions(Direction.South)).containsExactly(-1 to 0, 1 to 0)
        assertThat(MonsterWalk.getPushItemLocationOptions(Direction.NorthWest)).containsExactly(0 to -1, -1 to 0)
        assertThat(MonsterWalk.getPushItemLocationOptions(Direction.NorthEast)).containsExactly(0 to -1, 1 to 0)
        assertThat(MonsterWalk.getPushItemLocationOptions(Direction.SouthWest)).containsExactly(0 to 1, -1 to 0)
        assertThat(MonsterWalk.getPushItemLocationOptions(Direction.SouthEast)).containsExactly(0 to 1, 1 to 0)
    }

    // --- Monster::canWalkTo ---

    @Test
    fun `canWalkTo requires the destination in spawn range and a walkable tile`() {
        assertThat(
            MonsterWalk.canWalkTo(pos, Direction.East, { true }, { true })
        ).isTrue()

        assertThat(
            MonsterWalk.canWalkTo(pos, Direction.East, { false }, { true })
        ).isFalse()

        assertThat(
            MonsterWalk.canWalkTo(pos, Direction.East, { true }, { false })
        ).isFalse()
    }

    @Test
    fun `canWalkTo moves the probed position by the direction`() {
        val inspected = mutableListOf<Position>()

        MonsterWalk.canWalkTo(pos, Direction.SouthEast, { true }, { inspected.add(it); true })

        assertThat(inspected).containsExactly(Position(101, 101, 7))
    }

    // --- Creature::onWalk ---

    @Test
    fun `onWalk lets a drunk creature stumble and shout`() {
        val spoken = mutableListOf<String>()

        val result = MonsterWalk.onWalk(Direction.North, hasDrunkCondition = true, roll = { 2 }, say = { spoken.add(it) })

        assertThat(result).isEqualTo(Direction.South)
        assertThat(spoken).containsExactly("Hicks!")
    }

    @Test
    fun `onWalk keeps the direction on the diagonal mask boundary but still shouts`() {
        val spoken = mutableListOf<String>()

        val result = MonsterWalk.onWalk(Direction.North, hasDrunkCondition = true, roll = { 4 }, say = { spoken.add(it) })

        assertThat(result).isEqualTo(Direction.North)
        assertThat(spoken).containsExactly("Hicks!")
    }

    @Test
    fun `onWalk ignores rolls above the diagonal mask and the sober creature`() {
        val spoken = mutableListOf<String>()

        assertThat(MonsterWalk.onWalk(Direction.East, hasDrunkCondition = true, roll = { 5 }, say = { spoken.add(it) }))
            .isEqualTo(Direction.East)
        assertThat(MonsterWalk.onWalk(Direction.East, hasDrunkCondition = false, roll = { 1 }, say = { spoken.add(it) }))
            .isEqualTo(Direction.East)
        assertThat(spoken).isEmpty()
    }

    // --- Monster::pushItem ---

    private fun pushRequest(
        items: List<PushItemFacts> = emptyList(),
        tilePosition: Position = pos,
        tileHasHouse: Boolean = false,
        direction: Direction = Direction.East,
        canThrowObjectTo: (Position, Position) -> Boolean = { _, _ -> true },
        moveItem: (PushItemFacts, Position) -> Boolean = { _, _ -> false },
        removeItem: (PushItemFacts) -> Boolean = { false },
    ) = MonsterPushItemsRequest(
        items = items,
        tilePosition = tilePosition,
        tileHasHouse = tileHasHouse,
        nextDirection = direction,
        canThrowObjectTo = canThrowObjectTo,
        moveItem = moveItem,
        removeItem = removeItem,
    )

    @Test
    fun `pushItem rejects a missing source tile and a house tile`() {
        val item = PushItemFacts(fromPosition = pos)

        assertThat(MonsterWalk.pushItem(null, Direction.East)).isFalse()
        assertThat(MonsterWalk.pushItem(item, Direction.East, { _, _ -> true }, { _, _ -> true })).isTrue()
        assertThat(
            MonsterWalk.pushItem(
                PushItemFacts(fromPosition = null),
                Direction.East,
                { _, _ -> true },
                { _, _ -> true },
            )
        ).isFalse()
        assertThat(
            MonsterWalk.pushItem(
                PushItemFacts(fromPosition = pos, tileHasHouse = true),
                Direction.East,
                { _, _ -> true },
                { _, _ -> true },
            )
        ).isFalse()
    }

    @Test
    fun `pushItem tries the side drop positions in order and stops at the first move`() {
        val item = PushItemFacts(fromPosition = Position(50, 50, 7))
        val attempted = mutableListOf<Position>()
        var thrown = 0

        val pushed = MonsterWalk.pushItem(
            item = item,
            direction = Direction.North,
            canThrowObjectTo = { _, _ -> thrown++; true },
            moveItem = { _, to -> attempted.add(to); to.x == 51 },
        )

        assertThat(pushed).isTrue()
        assertThat(attempted).containsExactly(Position(49, 50, 7), Position(51, 50, 7))
        assertThat(thrown).isEqualTo(2)
    }

    // --- Monster::pushItems ---

    @Test
    fun `pushItems skips without a house or items`() {
        var moved = 0

        MonsterWalk.pushItems(pushRequest(tileHasHouse = true, moveItem = { _, _ -> moved++; true }))
        MonsterWalk.pushItems(pushRequest(moveItem = { _, _ -> moved++; true }))

        assertThat(moved).isZero()
    }

    @Test
    fun `pushItems walks the list in reverse and counts a successful move`() {
        val bottom = PushItemFacts(id = 1, fromPosition = pos, hasBlockSolid = true)
        val top = PushItemFacts(id = 2, fromPosition = pos, hasBlockPath = true)
        val order = mutableListOf<Int>()

        MonsterWalk.pushItems(
            pushRequest(
                items = listOf(bottom, top),
                moveItem = { item, _ -> order.add(item.id); true },
            )
        )

        assertThat(order).containsExactly(2, 1)
    }

    @Test
    fun `pushItems ignores items that are not movable, not moving-blocking or on another tile`() {
        val items = listOf(
            PushItemFacts(id = 1, fromPosition = pos, isMovable = false, hasBlockPath = true),
            PushItemFacts(id = 2, fromPosition = pos, canBeMoved = false, hasBlockSolid = true),
            PushItemFacts(id = 3, fromPosition = pos),
            PushItemFacts(id = 4, fromPosition = pos, hasBlockPath = true, onTile = false),
        )
        val touched = mutableListOf<Int>()

        MonsterWalk.pushItems(pushRequest(items = items, moveItem = { item, _ -> touched.add(item.id); true }))

        assertThat(touched).isEmpty()
    }

    @Test
    fun `pushItems falls back to removing blocked items and emits the poff effect`() {
        val items = listOf(
            PushItemFacts(id = 1, fromPosition = pos, hasBlockPath = true),
            PushItemFacts(id = 2, fromPosition = pos, hasBlockSolid = true),
        )
        val removed = mutableListOf<Int>()
        val effects = mutableListOf<Pair<Position, Int>>()

        MonsterWalk.pushItems(
            MonsterPushItemsRequest(
                items = items,
                tilePosition = pos,
                tileHasHouse = false,
                nextDirection = Direction.East,
                canThrowObjectTo = { _, _ -> true },
                moveItem = { _, _ -> false },
                removeItem = { item -> removed.add(item.id); true },
                addMagicEffect = { position, effect -> effects.add(position to effect) },
            )
        )

        assertThat(removed).containsExactly(2, 1)
        assertThat(effects).containsExactly(pos to MonsterBehavior.CONST_ME_POFF)
    }

    @Test
    fun `pushItems refuses to remove corpses`() {
        val items = listOf(PushItemFacts(id = 1, fromPosition = pos, hasBlockPath = true, isCorpse = true))
        var removed = 0

        MonsterWalk.pushItems(pushRequest(items = items, removeItem = { removed++; true }))

        assertThat(removed).isZero()
    }

    @Test
    fun `pushItems caps the moves at twenty and the removals at ten`() {
        val items = (1..25).map { PushItemFacts(id = it, fromPosition = pos, hasBlockPath = true) }
        var moved = 0
        var removed = 0

        MonsterWalk.pushItems(
            pushRequest(
                items = items,
                moveItem = { _, _ -> moved++; true },
                removeItem = { removed++; true },
            )
        )

        assertThat(moved).isEqualTo(20)
        assertThat(removed).isEqualTo(5)
    }

    // --- Monster::pushCreature(s) ---

    private fun pushed(id: Long, pushable: Boolean = true, health: Int = 100) =
        PushedCreature(id = id, position = pos, isMonster = true, isPushable = pushable, health = health)

    @Test
    fun `pushCreature tries the shuffled cardinal directions`() {
        val creature = pushed(1)
        val attempted = mutableListOf<Direction>()

        val pushed = MonsterWalk.pushCreature(
            creature = creature,
            position = pos,
            shuffleDirections = { },
            destinationHasBlockPath = { false },
            moveCreature = { _, direction -> attempted.add(direction); direction == Direction.East },
        )

        assertThat(pushed).isTrue()
        assertThat(attempted).containsExactly(Direction.North, Direction.West, Direction.East)
    }

    @Test
    fun `pushCreature skips blocked destination tiles`() {
        val creature = pushed(1)
        val attempted = mutableListOf<Direction>()

        val pushed = MonsterWalk.pushCreature(
            creature = creature,
            position = pos,
            shuffleDirections = { },
            destinationHasBlockPath = { it.y == pos.y - 1 },
            moveCreature = { _, direction -> attempted.add(direction); true },
        )

        assertThat(pushed).isTrue()
        assertThat(attempted).containsExactly(Direction.West)
    }

    @Test
    fun `pushCreatures pushes each monster once then kills the unresolvable ones`() {
        val first = pushed(1)
        val second = pushed(2)
        val third = pushed(3, pushable = false)
        val healthChanges = mutableListOf<Pair<Long, Int>>()
        val lootFlags = mutableListOf<Long>()
        val effects = mutableListOf<Int>()

        MonsterWalk.pushCreatures(
            MonsterPushCreaturesRequest(
                tilePosition = pos,
                creatures = listOf(first, second, third),
                shuffleDirections = { },
                destinationHasBlockPath = { false },
                moveCreature = { creature, _ -> creature.id == 2L },
                changeHealth = { creature, change -> healthChanges.add(creature.id to change) },
                setDropLoot = { creature, loot -> if (loot) lootFlags.add(creature.id) },
                addMagicEffect = { _, effect -> effects.add(effect) },
            )
        )

        // reverse order: third is not pushable; second is pushed; first kills after a failed push
        assertThat(healthChanges).containsExactly(1L to -100)
        assertThat(lootFlags).containsExactly(1L)
        assertThat(effects).containsExactly(MonsterBehavior.CONST_ME_BLOCKHIT)
    }

    // --- Monster::doFollowCreature ---

    @Test
    fun `doFollowCreature marks the pathfinding flag on a found step`() {
        val state = MonsterBehaviorState().apply { randomStepping = true }

        val result = MonsterWalk.doFollowCreature(
            state = state,
            creatureStep = { MonsterStepResult(found = true, direction = Direction.North, flags = 0) },
            followedIsAttacked = true,
            isFleeing = false,
            staticAttackChance = 95,
            getDanceStep = { _, _ -> Direction.South },
        )

        assertThat(result.found).isTrue()
        assertThat(result.direction).isEqualTo(Direction.North)
        assertThat(result.flags and CylinderFlags.FLAG_PATHFINDING).isEqualTo(CylinderFlags.FLAG_PATHFINDING)
        assertThat(state.randomStepping).isFalse()
    }

    @Test
    fun `doFollowCreature dances at attack range when the static chance roll fails`() {
        val state = MonsterBehaviorState().apply { ignoreFieldDamage = true }
        val danceArguments = mutableListOf<Pair<Boolean, Boolean>>()

        val result = MonsterWalk.doFollowCreature(
            state = state,
            creatureStep = { MonsterStepResult(found = false, direction = null) },
            followedIsAttacked = true,
            isFleeing = false,
            staticAttackChance = 50,
            nextRandom = { _, _ -> 51 },
            getDanceStep = { keepAttack, keepDistance ->
                danceArguments.add(keepAttack to keepDistance)
                Direction.East
            },
        )

        assertThat(result.found).isTrue()
        assertThat(result.direction).isEqualTo(Direction.East)
        assertThat(danceArguments).containsExactly(true to true)
        assertThat(state.ignoreFieldDamage).isFalse()
    }

    @Test
    fun `doFollowCreature does not dance when the static chance holds`() {
        var danced = 0

        val result = MonsterWalk.doFollowCreature(
            state = MonsterBehaviorState(),
            creatureStep = { MonsterStepResult(found = false, direction = null) },
            followedIsAttacked = true,
            isFleeing = false,
            staticAttackChance = 95,
            nextRandom = { _, _ -> 95 },
            getDanceStep = { _, _ -> danced++; Direction.East },
        )

        assertThat(result.found).isFalse()
        assertThat(danced).isZero()
    }

    @Test
    fun `doFollowCreature fleeing dances without keeping attack or distance`() {
        val danceArguments = mutableListOf<Pair<Boolean, Boolean>>()

        val result = MonsterWalk.doFollowCreature(
            state = MonsterBehaviorState(),
            creatureStep = { MonsterStepResult(found = false, direction = null) },
            followedIsAttacked = true,
            isFleeing = true,
            staticAttackChance = 95,
            nextRandom = { _, _ -> 1 },
            getDanceStep = { keepAttack, keepDistance ->
                danceArguments.add(keepAttack to keepDistance)
                Direction.West
            },
        )

        assertThat(result.found).isTrue()
        assertThat(result.direction).isEqualTo(Direction.West)
        assertThat(danceArguments).containsExactly(false to false)
    }

    @Test
    fun `doFollowCreature skips dancing when the followed creature is not the target`() {
        var danced = 0

        val result = MonsterWalk.doFollowCreature(
            state = MonsterBehaviorState(),
            creatureStep = { MonsterStepResult(found = false, direction = null) },
            followedIsAttacked = false,
            isFleeing = false,
            staticAttackChance = 0,
            nextRandom = { _, _ -> 100 },
            getDanceStep = { _, _ -> danced++; Direction.East },
        )

        assertThat(result.found).isFalse()
        assertThat(danced).isZero()
    }

    // --- Monster::getNextStep ---

    private fun nextStep(
        isIdle: Boolean = false,
        health: Int = 100,
        hasFollowCreature: Boolean = false,
        hasFollowPath: Boolean = false,
        isWalkingBack: Boolean = false,
        canPushItems: Boolean = false,
        canPushCreatures: Boolean = false,
        doFollowCreature: (Int) -> MonsterStepResult = { MonsterStepResult(found = true, direction = Direction.North, flags = it) },
        doWalkBack: (Int) -> MonsterStepResult = { MonsterStepResult(found = true, direction = Direction.West, flags = it) },
        doRandomStep: () -> MonsterStepResult = { MonsterStepResult(found = true, direction = Direction.East) },
        isMovementCommit: Boolean = false,
        tileExists: (Position) -> Boolean = { true },
        pushItems: (Position, Direction) -> Unit = { _, _ -> },
        pushCreatures: (Position) -> Unit = { },
        enqueueCreaturePush: (Position) -> Boolean = { true },
        onEventWalkCleared: () -> Unit = { },
        onForceUpdateFollowPath: () -> Unit = { },
    ) = MonsterWalk.getNextStep(
        isIdle = isIdle,
        health = health,
        hasFollowCreature = hasFollowCreature,
        hasFollowPath = hasFollowPath,
        isWalkingBack = isWalkingBack,
        canPushItems = canPushItems,
        canPushCreatures = canPushCreatures,
        position = pos,
        doFollowCreature = doFollowCreature,
        doWalkBack = doWalkBack,
        doRandomStep = doRandomStep,
        isMovementCommit = isMovementCommit,
        tileExists = tileExists,
        pushItems = pushItems,
        pushCreatures = pushCreatures,
        enqueueCreaturePush = enqueueCreaturePush,
        onEventWalkCleared = onEventWalkCleared,
        onForceUpdateFollowPath = onForceUpdateFollowPath,
    )

    @Test
    fun `getNextStep stops an idle or dead monster and clears its walk event`() {
        var cleared = 0

        val idle = nextStep(isIdle = true, onEventWalkCleared = { cleared++ })
        val dead = nextStep(health = 0, onEventWalkCleared = { cleared++ })

        assertThat(idle.found).isFalse()
        assertThat(dead.found).isFalse()
        assertThat(cleared).isEqualTo(2)
    }

    @Test
    fun `getNextStep picks follow walk back and random in priority order`() {
        val follow = nextStep(hasFollowCreature = true, hasFollowPath = true)
        assertThat(follow.direction).isEqualTo(Direction.North)

        val walkBack = nextStep(hasFollowCreature = true, hasFollowPath = false, isWalkingBack = true)
        assertThat(walkBack.direction).isEqualTo(Direction.West)

        val random = nextStep(hasFollowCreature = true, hasFollowPath = false, isWalkingBack = false)
        assertThat(random.direction).isEqualTo(Direction.East)
    }

    @Test
    fun `getNextStep pushes items and creatures at the destination`() {
        val itemPushes = mutableListOf<Pair<Position, Direction>>()
        val creaturePushes = mutableListOf<Position>()

        val step = nextStep(
            hasFollowCreature = true,
            hasFollowPath = true,
            canPushItems = true,
            canPushCreatures = true,
            isMovementCommit = true,
            pushItems = { position, direction -> itemPushes.add(position to direction) },
            pushCreatures = { position -> creaturePushes.add(position) },
        )

        assertThat(step.found).isTrue()
        assertThat(itemPushes).containsExactly(Position(100, 99, 7) to Direction.North)
        assertThat(creaturePushes).containsExactly(Position(100, 99, 7))
    }

    @Test
    fun `getNextStep skips the pushes when the destination tile is missing`() {
        var pushed = 0

        val step = nextStep(
            canPushItems = true,
            canPushCreatures = true,
            tileExists = { false },
            pushItems = { _, _ -> pushed++ },
            pushCreatures = { pushed++ },
        )

        assertThat(step.found).isTrue()
        assertThat(pushed).isZero()
    }

    @Test
    fun `getNextStep defers the creature push and fails the step when the queue rejects it`() {
        val deferred = mutableListOf<Position>()
        var forced = 0

        val rejected = nextStep(
            hasFollowCreature = true,
            hasFollowPath = true,
            canPushCreatures = true,
            isMovementCommit = false,
            enqueueCreaturePush = { position -> deferred.add(position); false },
            onForceUpdateFollowPath = { forced++ },
        )

        assertThat(rejected.found).isFalse()
        assertThat(deferred).containsExactly(Position(100, 99, 7))
        assertThat(forced).isEqualTo(1)

        val accepted = nextStep(
            hasFollowCreature = true,
            hasFollowPath = true,
            canPushCreatures = true,
            isMovementCommit = false,
            enqueueCreaturePush = { true },
        )
        assertThat(accepted.found).isTrue()
    }

    @Test
    fun `getNextStep never pushes on a failed step`() {
        var pushed = 0

        val step = nextStep(
            canPushItems = true,
            canPushCreatures = true,
            isMovementCommit = true,
            doFollowCreature = { MonsterStepResult(found = false, flags = it) },
            hasFollowCreature = true,
            hasFollowPath = true,
            pushItems = { _, _ -> pushed++ },
            pushCreatures = { pushed++ },
        )

        assertThat(step.found).isFalse()
        assertThat(pushed).isZero()
    }

    // --- Monster::getDistanceStep ---

    private fun distanceStep(
        state: MonsterBehaviorState = MonsterBehaviorState(),
        creaturePos: Position,
        targetPos: Position,
        targetDistance: Int = 1,
        flee: Boolean = false,
        walkable: (Position, Direction) -> Boolean = { _, _ -> true },
        isSightClear: Boolean = true,
        booleanRandom: () -> Boolean = { false },
        getRandomStep: (Position) -> Direction? = { null },
    ) = MonsterWalk.getDistanceStep(
        state = state,
        creaturePos = creaturePos,
        targetPos = targetPos,
        targetDistance = targetDistance,
        flee = flee,
        canWalkTo = walkable,
        isSightClear = { _, _ -> isSightClear },
        booleanRandom = booleanRandom,
        getRandomStep = getRandomStep,
    )

    @Test
    fun `getDistanceStep defers to A star beyond range or without sight`() {
        assertThat(
            distanceStep(creaturePos = pos, targetPos = Position(110, 100, 7), targetDistance = 1).handled
        ).isFalse()

        assertThat(
            distanceStep(creaturePos = pos, targetPos = Position(101, 100, 7), targetDistance = 1, isSightClear = false).handled
        ).isFalse()

        val atRange = distanceStep(creaturePos = pos, targetPos = Position(101, 100, 7), targetDistance = 1)
        assertThat(atRange.handled).isTrue()
        assertThat(atRange.direction).isNull()
    }

    @Test
    fun `getDistanceStep slows down when the target is adjacent and speeds up otherwise`() {
        val close = MonsterBehaviorState()
        distanceStep(close, pos, Position(101, 101, 7), targetDistance = 5, flee = true)
        assertThat(close.stepDuration).isEqualTo(1)
        distanceStep(close, pos, Position(101, 101, 7), targetDistance = 5, flee = true)
        assertThat(close.stepDuration).isEqualTo(2)
        distanceStep(close, pos, Position(101, 101, 7), targetDistance = 5, flee = true)
        assertThat(close.stepDuration).isEqualTo(2)

        val far = MonsterBehaviorState().apply { stepDuration = 1 }
        distanceStep(far, pos, Position(105, 100, 7), targetDistance = 5, flee = true)
        assertThat(far.stepDuration).isZero()
    }

    @Test
    fun `getDistanceStep takes a random step when the target stands on the monster`() {
        val random = distanceStep(
            creaturePos = pos,
            targetPos = pos,
            flee = true,
            getRandomStep = { Direction.SouthEast },
        )
        assertThat(random.handled).isTrue()
        assertThat(random.direction).isEqualTo(Direction.SouthEast)

        val stuck = distanceStep(creaturePos = pos, targetPos = pos, flee = true)
        assertThat(stuck.handled).isFalse()
    }

    // diagonal: attacked creature to the NW (offsetx >= 1, offsety >= 1)
    private val nw = Position(90, 90, 7)

    @Test
    fun `getDistanceStep escapes a NW attacker to the South or East`() {
        val south = distanceStep(creaturePos = pos, targetPos = nw, flee = true, walkable = walkable(Direction.South))
        assertThat(south.handled).isTrue()
        assertThat(south.direction).isEqualTo(Direction.South)

        val east = distanceStep(creaturePos = pos, targetPos = nw, flee = true, walkable = walkable(Direction.East))
        assertThat(east.direction).isEqualTo(Direction.East)

        val bothFalse = distanceStep(
            creaturePos = pos,
            targetPos = nw,
            flee = true,
            walkable = walkable(Direction.South, Direction.East),
            booleanRandom = { false },
        )
        assertThat(bothFalse.direction).isEqualTo(Direction.East)

        val bothTrue = distanceStep(
            creaturePos = pos,
            targetPos = nw,
            flee = true,
            walkable = walkable(Direction.South, Direction.East),
            booleanRandom = { true },
        )
        assertThat(bothTrue.direction).isEqualTo(Direction.South)
    }

    @Test
    fun `getDistanceStep escapes a NW attacker diagonally or sideways`() {
        val diagonal = distanceStep(
            creaturePos = pos,
            targetPos = nw,
            flee = true,
            walkable = walkable(Direction.SouthEast),
        )
        assertThat(diagonal.direction).isEqualTo(Direction.SouthEast)

        val west = distanceStep(
            creaturePos = pos,
            targetPos = nw,
            flee = true,
            walkable = walkable(Direction.West, Direction.SouthWest),
        )
        assertThat(west.direction).isEqualTo(Direction.West)

        val north = distanceStep(
            creaturePos = pos,
            targetPos = nw,
            flee = true,
            walkable = walkable(Direction.North, Direction.NorthEast),
        )
        assertThat(north.direction).isEqualTo(Direction.North)

        val cornered = distanceStep(creaturePos = pos, targetPos = nw, flee = true, walkable = walkable())
        assertThat(cornered.handled).isTrue()
        assertThat(cornered.direction).isNull()
    }

    @Test
    fun `getDistanceStep fleeing towards the attacker when cornered as NW`() {
        val north = distanceStep(
            creaturePos = pos,
            targetPos = nw,
            flee = true,
            walkable = walkable(Direction.North, Direction.West),
            booleanRandom = { true },
        )
        assertThat(north.direction).isEqualTo(Direction.North)

        val west = distanceStep(
            creaturePos = pos,
            targetPos = nw,
            flee = true,
            walkable = walkable(Direction.North, Direction.West),
            booleanRandom = { false },
        )
        assertThat(west.direction).isEqualTo(Direction.West)
    }

    // diagonal: attacked creature to the SE (offsetx <= -1, offsety <= -1)
    private val se = Position(110, 110, 7)

    @Test
    fun `getDistanceStep escapes a SE attacker to the West or North`() {
        val bothTrue = distanceStep(
            creaturePos = pos,
            targetPos = se,
            flee = true,
            walkable = walkable(Direction.West, Direction.North),
            booleanRandom = { true },
        )
        assertThat(bothTrue.direction).isEqualTo(Direction.West)

        val bothFalse = distanceStep(
            creaturePos = pos,
            targetPos = se,
            flee = true,
            walkable = walkable(Direction.West, Direction.North),
            booleanRandom = { false },
        )
        assertThat(bothFalse.direction).isEqualTo(Direction.North)

        val northWest = distanceStep(
            creaturePos = pos,
            targetPos = se,
            flee = true,
            walkable = walkable(Direction.NorthWest),
        )
        assertThat(northWest.direction).isEqualTo(Direction.NorthWest)
    }

    @Test
    fun `getDistanceStep escapes a SE attacker diagonally or towards it when cornered`() {
        val south = distanceStep(
            creaturePos = pos,
            targetPos = se,
            flee = true,
            walkable = walkable(Direction.South, Direction.SouthWest),
        )
        assertThat(south.direction).isEqualTo(Direction.South)

        val east = distanceStep(
            creaturePos = pos,
            targetPos = Position(99, 90, 7),
            flee = true,
            walkable = walkable(Direction.East),
        )
        assertThat(east.direction).isEqualTo(Direction.East)

        val towardsTrue = distanceStep(
            creaturePos = pos,
            targetPos = se,
            flee = true,
            walkable = walkable(Direction.South, Direction.East),
            booleanRandom = { true },
        )
        assertThat(towardsTrue.direction).isEqualTo(Direction.South)

        val towardsFalse = distanceStep(
            creaturePos = pos,
            targetPos = se,
            flee = true,
            walkable = walkable(Direction.South, Direction.East),
            booleanRandom = { false },
        )
        assertThat(towardsFalse.direction).isEqualTo(Direction.East)
    }

    // diagonal: attacked creature to the SW (offsetx >= 1, offsety <= -1)
    private val sw = Position(90, 110, 7)

    @Test
    fun `getDistanceStep escapes a SW attacker to the North or East`() {
        val north = distanceStep(
            creaturePos = pos,
            targetPos = sw,
            flee = true,
            walkable = walkable(Direction.North, Direction.East),
            booleanRandom = { true },
        )
        assertThat(north.direction).isEqualTo(Direction.North)

        val east = distanceStep(
            creaturePos = pos,
            targetPos = sw,
            flee = true,
            walkable = walkable(Direction.North, Direction.East),
            booleanRandom = { false },
        )
        assertThat(east.direction).isEqualTo(Direction.East)

        val northEast = distanceStep(
            creaturePos = pos,
            targetPos = sw,
            flee = true,
            walkable = walkable(Direction.NorthEast),
        )
        assertThat(northEast.direction).isEqualTo(Direction.NorthEast)
    }

    @Test
    fun `getDistanceStep escapes a SW attacker sideways or towards it when cornered`() {
        val west = distanceStep(
            creaturePos = pos,
            targetPos = sw,
            flee = true,
            walkable = walkable(Direction.West, Direction.NorthWest),
        )
        assertThat(west.direction).isEqualTo(Direction.West)

        val south = distanceStep(
            creaturePos = pos,
            targetPos = sw,
            flee = true,
            walkable = walkable(Direction.South, Direction.SouthEast),
        )
        assertThat(south.direction).isEqualTo(Direction.South)

        val towards = distanceStep(
            creaturePos = pos,
            targetPos = sw,
            flee = true,
            walkable = walkable(Direction.South, Direction.West),
            booleanRandom = { true },
        )
        assertThat(towards.direction).isEqualTo(Direction.South)
    }

    // diagonal: attacked creature to the NE (offsetx <= -1, offsety >= 1)
    private val ne = Position(110, 90, 7)

    @Test
    fun `getDistanceStep escapes a NE attacker to the West or South`() {
        val west = distanceStep(
            creaturePos = pos,
            targetPos = ne,
            flee = true,
            walkable = walkable(Direction.West, Direction.South),
            booleanRandom = { true },
        )
        assertThat(west.direction).isEqualTo(Direction.West)

        val south = distanceStep(
            creaturePos = pos,
            targetPos = ne,
            flee = true,
            walkable = walkable(Direction.West, Direction.South),
            booleanRandom = { false },
        )
        assertThat(south.direction).isEqualTo(Direction.South)

        val southWest = distanceStep(
            creaturePos = pos,
            targetPos = ne,
            flee = true,
            walkable = walkable(Direction.SouthWest),
        )
        assertThat(southWest.direction).isEqualTo(Direction.SouthWest)
    }

    @Test
    fun `getDistanceStep escapes a NE attacker sideways or towards it when cornered`() {
        val east = distanceStep(
            creaturePos = pos,
            targetPos = ne,
            flee = true,
            walkable = walkable(Direction.East, Direction.SouthEast),
        )
        assertThat(east.direction).isEqualTo(Direction.East)

        val north = distanceStep(
            creaturePos = pos,
            targetPos = ne,
            flee = true,
            walkable = walkable(Direction.North, Direction.NorthWest),
        )
        assertThat(north.direction).isEqualTo(Direction.North)

        val towards = distanceStep(
            creaturePos = pos,
            targetPos = ne,
            flee = true,
            walkable = walkable(Direction.North, Direction.East),
            booleanRandom = { false },
        )
        assertThat(towards.direction).isEqualTo(Direction.East)
    }

    // straight-line: attacker to the north (dy > dx)
    @Test
    fun `getDistanceStep backs away from a northern attacker`() {
        val target = Position(101, 90, 7)

        val south = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.South),
        )
        assertThat(south.direction).isEqualTo(Direction.South)

        val diagonal = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.SouthWest),
        )
        assertThat(diagonal.direction).isEqualTo(Direction.SouthWest)

        val pair = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.SouthWest, Direction.SouthEast),
            booleanRandom = { false },
        )
        assertThat(pair.direction).isEqualTo(Direction.SouthEast)
    }

    @Test
    fun `getDistanceStep picks the horizontal escape away from a northern attacker`() {
        val target = Position(101, 90, 7)
        val east = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.East),
        )
        assertThat(east.direction).isEqualTo(Direction.East)

        val west = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.West, Direction.East),
        )
        assertThat(west.direction).isEqualTo(Direction.West)

        val aligned = distanceStep(
            creaturePos = pos,
            targetPos = Position(100, 90, 7),
            flee = true,
            walkable = walkable(Direction.West, Direction.East),
            booleanRandom = { true },
        )
        assertThat(aligned.direction).isEqualTo(Direction.West)
    }

    @Test
    fun `getDistanceStep flees towards a northern attacker only when cornered`() {
        val target = Position(101, 90, 7)
        val north = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.North),
        )
        assertThat(north.direction).isEqualTo(Direction.North)
    }

    // straight-line: attacker to the south (dy > dx)
    @Test
    fun `getDistanceStep backs away from a southern attacker`() {
        val target = Position(101, 110, 7)

        val north = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.North),
        )
        assertThat(north.direction).isEqualTo(Direction.North)

        val diagonal = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.NorthWest),
        )
        assertThat(diagonal.direction).isEqualTo(Direction.NorthWest)

        val fleeSouth = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.South),
        )
        assertThat(fleeSouth.direction).isEqualTo(Direction.South)
    }

    // straight-line: attacker to the west (dx >= dy)
    @Test
    fun `getDistanceStep backs away from a western attacker`() {
        val target = Position(90, 101, 7)

        val east = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.East),
        )
        assertThat(east.direction).isEqualTo(Direction.East)

        val diagonal = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.SouthEast),
        )
        assertThat(diagonal.direction).isEqualTo(Direction.SouthEast)

        val vertical = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.South),
        )
        assertThat(vertical.direction).isEqualTo(Direction.South)

        val fleeWest = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.West),
        )
        assertThat(fleeWest.direction).isEqualTo(Direction.West)
    }

    // straight-line: attacker to the east (dx >= dy)
    @Test
    fun `getDistanceStep backs away from an eastern attacker`() {
        val target = Position(110, 101, 7)

        val west = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.West),
        )
        assertThat(west.direction).isEqualTo(Direction.West)

        val diagonal = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.NorthWest),
        )
        assertThat(diagonal.direction).isEqualTo(Direction.NorthWest)

        val fleeEast = distanceStep(
            creaturePos = pos,
            targetPos = target,
            flee = true,
            walkable = walkable(Direction.East),
        )
        assertThat(fleeEast.direction).isEqualTo(Direction.East)
    }

    @Test
    fun `getDistanceStep returns handled without a step when fully blocked`() {
        val result = distanceStep(
            creaturePos = pos,
            targetPos = Position(101, 90, 7),
            flee = true,
            walkable = walkable(),
        )

        assertThat(result.handled).isTrue()
        assertThat(result.direction).isNull()
    }
}
