package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.players.pvp.PvpMode_t
import org.opentibia.canary.creatures.players.setPvpMode
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.MagicField
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.spells.WorldType

/**
 * Transcribes the Player walk queue and scheduler variants
 * (src/creatures/players/player.cpp):
 *
 * - `Player::onWalk` (player.cpp:2942-2961)
 * - `Player::setNextActionTask` / `setNextActionPushTask` /
 *   `setNextPotionActionTask` (player.cpp:3014-3070)
 * - `Player::goToFollowCreature` (player.cpp:6161-6171)
 * - `Player::canWalkthrough` / `canWalkthroughEx` (player.cpp:1424-1536)
 * - `Creature::startAutoWalk` (creature.cpp:326-340) and the `Creature::listWalkDir`
 *   queue (creature.hpp:872)
 *
 * The host `Player`/`Creature` live outside the `creatures/players` package, so every
 * field they own (`walkTask`, `lastFailedFollow`, the action-task events, the
 * map tile lookup, `ExpertPvp`'s world gate) is an injected constructor seam.
 */
class PlayerWalkQueueTest {

    private var now = 0L

    private data class Scheduled(
        val eventId: Long,
        val lane: DispatcherLane,
        val delayMs: Long,
        val context: String,
        val action: () -> Unit,
    )

    private class Sink {
        var feared = false
        var rooted = false
        var stepDuration = 0
        var nextActionAt: Long? = null
        var tile: Tile? = null
        val walkCallbacks = mutableListOf<Direction>()
        val superOnWalks = mutableListOf<Direction>()
        var inEventMovePush = false
        var pushWhenAttacking = false
        var cancelPushes = 0
        var idleResets = 0
        var scheduled = mutableListOf<Scheduled>()
        var stopped = mutableListOf<Long>()
        var failScheduling = false
        val cancels = mutableListOf<ReturnValue>()
        var followCreature: Creature? = null
        var hasFollowPath = false
        var superGoToFollows = 0
        var addEventWalks = mutableListOf<WalkStartPolicy>()
        var player: Player? = null
        var protectionLevel = 1
        var worldType = WorldType.PVP
        var expertPvpEnabled = false
        var ghost = false
        var familiar = false
    }

    private val sink = Sink()

    private fun newState(sink: Sink = this.sink) = PlayerWalkState(
        now = { now },
        getPosition = { Position(100, 100, 7) },
        hasFearedCondition = { sink.feared },
        resetIdleTime = { sink.idleResets++ },
        scheduleEvent = { lane, delay, context, action ->
            if (sink.failScheduling) {
                0L
            } else {
                val eventId = sink.scheduled.size + 100L
                sink.scheduled.add(Scheduled(eventId, lane, delay, context, action))
                eventId
            }
        },
        stopEvent = { sink.stopped.add(it) },
        sendCancelMessage = { sink.cancels.add(it) },
        addEventWalk = { sink.addEventWalks.add(it) },
        getPlayer = { sink.player },
        hasRootedCondition = { sink.rooted },
        getFollowCreature = { sink.followCreature },
        hasFollowPath = { sink.hasFollowPath },
        creatureGoToFollowCreature = { sink.superGoToFollows++ },
        getStepDuration = { sink.stepDuration },
        setNextAction = { sink.nextActionAt = it },
        getTile = { sink.tile },
        creatureOnWalk = { sink.superOnWalks.add(it) },
        playerOnWalkCallback = { sink.walkCallbacks.add(it) },
        inEventMovePush = { sink.inEventMovePush },
        pushWhenAttacking = { sink.pushWhenAttacking },
        cancelPush = { sink.cancelPushes++ },
        protectionLevel = { sink.protectionLevel },
        getWorldType = { sink.worldType },
        expertPvpEnabled = { sink.expertPvpEnabled },
        isInGhostMode = { sink.ghost },
        isFamiliar = { sink.familiar },
    )

    private fun newPlayer(id: Long, position: Position = Position(100, 100, 7), level: Int = 1) = Player(
        id = id,
        name = "Hero$id",
        maxHealth = 150,
        health = 150,
        position = position,
    ).apply { this.level = level }

    private fun fieldTile(position: Position, damage: Int): Tile = Tile(position).apply {
        setFlag(TileFlags.TILESTATE_MAGICFIELD.value)
        ground = MagicField(1497, type = fieldType(damage))
    }

    private fun fieldType(damage: Int): ItemType =
        ItemType(id = 1497, name = "fire field", type = ItemTypes_t.ITEM_TYPE_MAGICFIELD).apply {
            if (damage != 0) {
                conditionDamage = ConditionDamage(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_FIRE,
                ).apply { addDamage(1, 1000, -damage) }
            }
        }

    // --- setNextActionTask & variants (player.cpp:3014-3070) ----------------------

    @Test
    fun `setNextActionTask schedules on the PlayerAction lane and resets idle time`() {
        // player.cpp:3014-3036.
        var ran = 0
        val state = newState()
        state.setNextActionTask(PlayerTask(250, "action", { ran++ }))

        val scheduled = sink.scheduled.single()
        assertThat(scheduled.lane).isEqualTo(DispatcherLane.PlayerAction)
        assertThat(scheduled.delayMs).isEqualTo(250)
        assertThat(scheduled.context).isEqualTo("action")
        assertThat(sink.idleResets).isEqualTo(1)
        assertThat(sink.cancelPushes).isEqualTo(1)

        scheduled.action()
        assertThat(ran).isEqualTo(1)
    }

    @Test
    fun `setNextActionTask stops the previous event and can skip the idle reset`() {
        // player.cpp:3015-3018, 3033-3034.
        val state = newState()
        state.setNextActionTask(PlayerTask(0, "first", {}))
        val firstEvent = sink.scheduled.single().eventId

        state.setNextActionTask(PlayerTask(0, "second", {}), resetIdleTime = false)

        assertThat(sink.stopped).containsExactly(firstEvent)
        assertThat(sink.idleResets).isEqualTo(1)
    }

    @Test
    fun `setNextActionTask does not cancel push during an event move push`() {
        // player.cpp:3020-3022.
        sink.inEventMovePush = true
        val state = newState()

        state.setNextActionTask(PlayerTask(0, "action", {}))

        assertThat(sink.cancelPushes).isZero()
    }

    @Test
    fun `setNextActionTask does not cancel push when pushWhenAttacking is configured`() {
        // player.cpp:3020-3022: `!inEventMovePush && !PUSH_WHEN_ATTACKING`.
        sink.pushWhenAttacking = true
        val state = newState()

        state.setNextActionTask(null)

        assertThat(sink.cancelPushes).isZero()
    }

    @Test
    fun `setNextActionTask cancels push even for a null task`() {
        // player.cpp:3020-3022 runs before the task check.
        val state = newState()

        state.setNextActionTask(null)

        assertThat(sink.cancelPushes).isEqualTo(1)
        assertThat(sink.scheduled).isEmpty()
    }

    @Test
    fun `setNextActionTask cancels with NOTPOSSIBLE when scheduling fails`() {
        // player.cpp:3029-3031.
        sink.failScheduling = true
        val state = newState()

        state.setNextActionTask(PlayerTask(0, "action", {}))

        assertThat(sink.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
        assertThat(sink.idleResets).isZero()
    }

    @Test
    fun `setNextActionPushTask keeps its own event separate`() {
        // player.cpp:3038-3052: actionTaskEventPush is independent.
        val state = newState()
        state.setNextActionTask(PlayerTask(0, "action", {}))
        val actionEvent = sink.scheduled.single().eventId
        state.setNextActionPushTask(PlayerTask(0, "push", {}))
        val pushEvent = sink.scheduled.last().eventId

        state.setNextActionTask(PlayerTask(0, "action2", {}))

        assertThat(sink.stopped).containsExactly(actionEvent)
        assertThat(sink.scheduled.map { it.context }).containsExactly("action", "push", "action2")
        assertThat(pushEvent).isEqualTo(101L)
    }

    @Test
    fun `setNextActionPushTask replaces the previous push event`() {
        // player.cpp:3038-3041.
        val state = newState()
        state.setNextActionPushTask(PlayerTask(0, "push", {}))
        val firstPush = sink.scheduled.single().eventId

        state.setNextActionPushTask(PlayerTask(0, "push2", {}))

        assertThat(sink.stopped).containsExactly(firstPush)
    }

    @Test
    fun `setNextActionPushTask cancels with NOTPOSSIBLE when scheduling fails`() {
        // player.cpp:3049-3051.
        sink.failScheduling = true
        val state = newState()

        state.setNextActionPushTask(PlayerTask(0, "push", {}))

        assertThat(sink.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `setNextPotionActionTask cancels push and schedules on the action lane`() {
        // player.cpp:3054-3070.
        val state = newState()
        state.setNextPotionActionTask(PlayerTask(100, "potion", {}))

        assertThat(sink.cancelPushes).isEqualTo(1)
        assertThat(sink.scheduled.single().lane).isEqualTo(DispatcherLane.PlayerAction)
        assertThat(sink.scheduled.single().context).isEqualTo("potion")
    }

    @Test
    fun `setNextPotionActionTask cancels with NOTPOSSIBLE when scheduling fails`() {
        // player.cpp:3066-3068.
        sink.failScheduling = true
        val state = newState()

        state.setNextPotionActionTask(PlayerTask(0, "potion", {}))

        assertThat(sink.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    // --- Player::onWalk (player.cpp:2942-2961) -------------------------------------

    @Test
    fun `onWalk on a feared player defers the step over a damaging field`() {
        // player.cpp:2943-2953: reset the action task and re-arm nextAction.
        sink.feared = true
        sink.tile = fieldTile(Position(101, 100, 7), damage = 100)
        sink.stepDuration = 500
        val state = newState()
        state.setNextActionTask(PlayerTask(0, "action", {}))
        val pending = sink.scheduled.single().eventId
        now = 1_000

        state.onWalk(Direction.East)

        assertThat(sink.stopped).containsExactly(pending)
        assertThat(sink.nextActionAt).isEqualTo(1_500)
        assertThat(sink.superOnWalks).isEmpty()
        assertThat(sink.walkCallbacks).isEmpty()
    }

    @Test
    fun `onWalk on a feared player falls through for harmless fields`() {
        // player.cpp:2946-2952: blocking fields or zero damage continue.
        sink.feared = true
        sink.tile = fieldTile(Position(101, 100, 7), damage = 0)
        val state = newState()

        state.onWalk(Direction.East)

        assertThat(sink.nextActionAt).isNull()
        assertThat(sink.walkCallbacks).containsExactly(Direction.East)
    }

    @Test
    fun `onWalk without fear runs the creature walk and the player callback`() {
        // player.cpp:2956-2960.
        val state = newState()

        state.onWalk(Direction.North)

        assertThat(sink.superOnWalks).containsExactly(Direction.North)
        assertThat(sink.walkCallbacks).containsExactly(Direction.North)
    }

    @Test
    fun `onWalk clears the pending action task`() {
        // player.cpp:2957: setNextActionTask(nullptr).
        val state = newState()
        state.setNextActionTask(PlayerTask(0, "action", {}))
        val pending = sink.scheduled.single().eventId

        state.onWalk(Direction.West)

        assertThat(sink.stopped).containsExactly(pending)
        assertThat(sink.scheduled).hasSize(1)
    }

    // --- Player::goToFollowCreature (player.cpp:6161-6171) -------------------------

    @Test
    fun `goToFollowCreature skips while a walk task is pending`() {
        // player.cpp:6162-6164.
        val state = newState()
        state.setNextWalkActionTask(PlayerTask(0, "walk", {}))

        state.goToFollowCreature()

        assertThat(sink.superGoToFollows).isZero()
    }

    @Test
    fun `goToFollowCreature skips inside the two second failure backoff`() {
        // player.cpp:6163-6166: lastFailedFollow window.
        val state = newState()
        state.lastFailedFollow = 1_000
        now = 2_999

        state.goToFollowCreature()

        assertThat(sink.superGoToFollows).isZero()

        now = 3_000
        state.goToFollowCreature()
        assertThat(sink.superGoToFollows).isEqualTo(1)
    }

    @Test
    fun `goToFollowCreature records the failure when no path was found`() {
        // player.cpp:6168-6170.
        sink.followCreature = newPlayer(2)
        now = 9_000
        val state = newState()

        state.goToFollowCreature()

        assertThat(sink.superGoToFollows).isEqualTo(1)
        assertThat(state.lastFailedFollow).isEqualTo(9_000)

        sink.hasFollowPath = true
        state.lastFailedFollow = 0
        state.goToFollowCreature()
        assertThat(state.lastFailedFollow).isZero()
    }

    // --- Creature::startAutoWalk (creature.cpp:326-340) ----------------------------

    @Test
    fun `startAutoWalk replaces the queue and starts the walk event`() {
        // creature.cpp:327-339.
        val state = newState()

        state.startAutoWalk(listOf(Direction.East, Direction.North), startPolicy = WalkStartPolicy.ImmediateWhenReady)

        assertThat(state.listWalkDir).containsExactly(Direction.East, Direction.North)
        assertThat(sink.addEventWalks).containsExactly(WalkStartPolicy.ImmediateWhenReady)

        state.startAutoWalk(listOf(Direction.South))

        assertThat(state.listWalkDir).containsExactly(Direction.South)
        assertThat(sink.addEventWalks).hasSize(2)
    }

    @Test
    fun `startAutoWalk refuses rooted and feared creatures unless ignored`() {
        // creature.cpp:329-331.
        sink.rooted = true
        val state = newState()

        state.startAutoWalk(listOf(Direction.East))
        assertThat(sink.addEventWalks).isEmpty()
        assertThat(state.listWalkDir).isEmpty()

        state.startAutoWalk(listOf(Direction.East), ignoreConditions = true)
        assertThat(sink.addEventWalks).containsExactly(WalkStartPolicy.RespectDelay)

        sink.rooted = false
        sink.feared = true
        state.startAutoWalk(listOf(Direction.East), ignoreConditions = true)
        assertThat(sink.addEventWalks).hasSize(2)
    }

    @Test
    fun `startAutoWalk with an empty list does not start the event`() {
        // creature.cpp:335-337.
        val state = newState()

        state.startAutoWalk(emptyList())

        assertThat(state.listWalkDir).isEmpty()
        assertThat(sink.addEventWalks).isEmpty()
    }

    // --- Player::canWalkthrough (player.cpp:1424-1489) -----------------------------

    @Test
    fun `canWalkthrough allows access players and ghost creatures`() {
        // player.cpp:1425-1427.
        val walker = newPlayer(1)
        walker.group = Group(access = true)
        sink.player = walker
        val subject = newPlayer(2)
        val state = newState()

        assertThat(state.canWalkthrough(subject)).isTrue()

        walker.group = Group(access = false)
        sink.ghost = true
        assertThat(state.canWalkthrough(subject)).isTrue()
    }

    @Test
    fun `canWalkthrough allows familiars and no-pvp summons`() {
        // player.cpp:1433-1443.
        val monster = Monster(id = 3, name = "Summon", maxHealth = 10, health = 10, position = Position(100, 100, 7))
        val state = newState()

        sink.familiar = true
        assertThat(state.canWalkthrough(monster)).isTrue()

        sink.familiar = false
        sink.worldType = WorldType.NO_PVP
        monster.setMaster(newPlayer(4))
        assertThat(state.canWalkthrough(monster)).isTrue()

        sink.player = newPlayer(1)
        sink.worldType = WorldType.PVP
        assertThat(state.canWalkthrough(monster)).isFalse()
    }

    @Test
    fun `canWalkthrough rejects a player outside the legacy zone`() {
        // player.cpp:1465-1472.
        sink.player = newPlayer(1, level = 50)
        sink.protectionLevel = 1
        val subject = newPlayer(2, Position(101, 100, 7), level = 50)
        subject.setParent(Tile(Position(101, 100, 7)))
        val state = newState()

        assertThat(state.canWalkthrough(subject)).isFalse()
    }

    @Test
    fun `canWalkthrough throttles and then allows the same walkthrough target`() {
        // player.cpp:1480-1488: one retry per 2000ms at the same position.
        sink.player = newPlayer(1, level = 1)
        sink.protectionLevel = 1
        val subject = newPlayer(2, Position(101, 100, 7), level = 1)
        subject.setParent(Tile(Position(101, 100, 7)).apply { ground = MagicField(1497, type = ItemType(id = 1497, name = "field")) })
        val state = newState()
        now = 5_000

        assertThat(state.canWalkthrough(subject)).isFalse()
        assertThat(state.lastWalkthroughAttempt).isEqualTo(5_000)

        assertThat(state.canWalkthrough(subject)).isFalse()
        assertThat(state.lastWalkthroughPosition).isEqualTo(Position(101, 100, 7))

        assertThat(state.canWalkthrough(subject)).isTrue()
    }

    @Test
    fun `canWalkthrough rejects a target that moved since the last attempt`() {
        // player.cpp:1483-1487.
        sink.player = newPlayer(1, level = 1)
        sink.protectionLevel = 1
        val subject = newPlayer(2, Position(101, 100, 7), level = 1)
        subject.setParent(Tile(Position(101, 100, 7)).apply { ground = MagicField(1497, type = ItemType(id = 1497, name = "field")) })
        val state = newState()
        now = 5_000
        state.canWalkthrough(subject)
        state.canWalkthrough(subject)
        assertThat(state.canWalkthrough(subject)).isTrue()

        subject.setParent(
            Tile(Position(102, 100, 7)).apply {
                ground = MagicField(1497, type = ItemType(id = 1497, name = "field"))
            },
        )

        assertThat(state.canWalkthrough(subject)).isFalse()
        assertThat(state.lastWalkthroughPosition).isEqualTo(Position(102, 100, 7))
    }

    @Test
    fun `canWalkthrough requires a walk stack ground in the legacy zone`() {
        // player.cpp:1474-1477.
        sink.player = newPlayer(1, level = 1)
        sink.protectionLevel = 1
        val subject = newPlayer(2, Position(101, 100, 7), level = 1)
        subject.setParent(Tile(Position(101, 100, 7)).apply { ground = MagicField(1497, type = ItemType(id = 1497, name = "field").apply { walkStack = false }) })
        val state = newState()

        assertThat(state.canWalkthrough(subject)).isFalse()
    }

    @Test
    fun `canWalkthrough sends npcs on house tiles through`() {
        // player.cpp:1491: npc->getTile()->getHouse() != nullptr.
        val state = newState()
        val npc = Npc(id = 3, name = "Sam", position = Position(101, 100, 7))
        assertThat(state.canWalkthrough(npc)).isFalse()

        npc.setParent(Tile(Position(101, 100, 7)).apply { houseId = 1 })

        assertThat(state.canWalkthrough(npc)).isTrue()
    }

    @Test
    fun `canWalkthrough consults ExpertPvp when the legacy zone rejects`() {
        // player.cpp:1462-1469.
        val walker = newPlayer(1, level = 50)
        sink.player = walker
        sink.expertPvpEnabled = true
        val subject = newPlayer(2, Position(101, 100, 7), level = 50)
        subject.setParent(Tile(Position(101, 100, 7)))
        val state = newState()

        assertThat(state.canWalkthrough(subject)).isTrue()

        walker.setPvpMode(PvpMode_t.PVP_MODE_RED_FIST)

        assertThat(state.canWalkthrough(subject)).isFalse()
    }

    // --- Player::canWalkthroughEx (player.cpp:1492-1536) ---------------------------

    @Test
    fun `canWalkthroughEx only passes familiars`() {
        // player.cpp:1494-1503.
        val state = newState()
        val monster = Monster(id = 3, name = "Rat", maxHealth = 10, health = 10, position = Position(100, 100, 7))

        assertThat(state.canWalkthroughEx(monster)).isFalse()

        sink.familiar = true
        assertThat(state.canWalkthroughEx(monster)).isTrue()
    }

    @Test
    fun `canWalkthroughEx returns the legacy zone decision for players`() {
        // player.cpp:1507-1524.
        sink.player = newPlayer(1)
        val subject = newPlayer(2, Position(101, 100, 7), level = 50)
        val protectedTile = Tile(Position(101, 100, 7)).apply {
            setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        }
        subject.setParent(protectedTile)
        val state = newState()

        assertThat(state.canWalkthroughEx(subject)).isTrue()

        subject.setParent(Tile(Position(101, 100, 7)))

        assertThat(state.canWalkthroughEx(subject)).isFalse()
    }

    @Test
    fun `canWalkthroughEx requires house tiles for npcs`() {
        // player.cpp:1525-1531: HouseTile lookup.
        val state = newState()
        val npc = Npc(id = 3, name = "Sam", position = Position(101, 100, 7))

        assertThat(state.canWalkthroughEx(npc)).isFalse()

        npc.setParent(Tile(Position(101, 100, 7)).apply { houseId = 1 })

        assertThat(state.canWalkthroughEx(npc)).isTrue()
    }

    @Test
    fun `canWalkthroughEx allows access players`() {
        // player.cpp:1493-1495.
        val walker = newPlayer(1)
        walker.group = Group(access = true)
        sink.player = walker
        val state = newState()
        val monster = Monster(id = 3, name = "Rat", maxHealth = 10, health = 10, position = Position(100, 100, 7))

        assertThat(state.canWalkthroughEx(monster)).isTrue()
    }
}
