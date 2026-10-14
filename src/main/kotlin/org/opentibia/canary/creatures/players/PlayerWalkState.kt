package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.pvp.ExpertPvp
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.getNextPosition
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * `Creature::WalkStartPolicy` (`src/creatures/creature.hpp:237-243`): whether
 * the first step honors the current walk delay or starts as soon as ready.
 */
enum class WalkStartPolicy {
    /** `WalkStartPolicy::RespectDelay` (creature.hpp:239). */
    RespectDelay,

    /** `WalkStartPolicy::ImmediateWhenReady` (creature.hpp:241). */
    ImmediateWhenReady,
}

/**
 * The port's stand-in for Canary's `Task` (`src/game/scheduling/task.hpp:29-140`).
 *
 * Canary builds one with `Player::createPlayerTask` (player.cpp:212-214); the
 * walk scheduler seam receives its delay/context/action separately, so only
 * those three fields are modelled.
 */
data class PlayerTask(
    val delayMs: Long,
    val context: String,
    val action: () -> Unit,
)

/**
 * Self-contained mirror of the Player walk/position state machine
 * (src/creatures/players/player.cpp:212-214, 897-903, 2983-3012, 3176-3196,
 * 6242-6271, 12589-12606).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps these fields directly on `Player` (player.hpp:1714, 1740, 1750, 1770,
 * 1792); the state is exposed as an injectable seam so the host player can
 * delegate without a global (AGENTS.md 1.1). Every external dependency is a
 * constructor seam: [now] replaces `OTSYS_TIME()`, [getPosition] replaces
 * `Creature::getPosition()`, [scheduleEvent]/[stopEvent] replace
 * `g_dispatcher().scheduleEvent`/`stopEvent` (dispatcher.hpp:144-145), and the
 * senders replace the protocol/client calls.
 *
 * This wave adds the Player walk queue (`onWalk`, `goToFollowCreature`,
 * `canWalkthrough`/`canWalkthroughEx`, the action/push/potion scheduler
 * variants) and ports `Creature::startAutoWalk` over the [listWalkDir] queue:
 * the tile lookup, the `ExpertPvp` world projection, the follow state and the
 * `Creature::` walk event scheduler are all injected seams.
 *
 * PARITY GAPS:
 *  - `Player::stopWalk` (player.cpp:6273-6275) already lives on the host
 *    `Player` (`creatures/Creature.kt:1411`) and is not duplicated here.
 *  - `Creature::addEventWalk`/`getEventStepTicks`/`onCreatureWalk` (the actual
 *    event pump, creature.cpp:342-425) and `Creature::getStepDuration` stay
 *    with the movement subsystem; [startAutoWalk]/[onWalk] receive them as
 *    seams.
 *  - `addListWalkDir` does not exist in this Canary revision; the `listWalkDir`
 *    queue (creature.hpp:872) is owned by this class and consumed by the host
 *    walk event.
 */
class PlayerWalkState(
    /** `OTSYS_TIME()` (`src/utils/time.hpp`); milliseconds. */
    private val now: () -> Long = { System.currentTimeMillis() },
    /** `Creature::getPosition()` (player.cpp:12590). */
    private val getPosition: () -> Position = { Position(0, 0, 0) },
    /** `Creature::hasCondition(CONDITION_PARALYZE)` (player.cpp:3177, 3189). */
    private val hasParalyze: () -> Boolean = { false },
    /** `Player::hasCondition(CONDITION_FEARED)` (player.cpp:6248). */
    private val hasFearedCondition: () -> Boolean = { false },
    /** `Creature::getWalkDelay()` (player.cpp:3191). */
    private val getWalkDelay: () -> Int = { 0 },
    /** `Player::sendCancelWalk()` (player.cpp:2127-2131). */
    private val sendCancelWalk: () -> Unit = {},
    /** `g_dispatcher().scheduleEvent` (dispatcher.hpp:144-145); 0 means rejected. */
    private val scheduleEvent: (lane: DispatcherLane, delayMs: Long, context: String, action: () -> Unit) -> Long =
        { _, _, _, _ -> 0L },
    /** `g_dispatcher().stopEvent` (player.cpp:2984, 2998). */
    private val stopEvent: (eventId: Long) -> Unit = {},
    /** `Creature::resetIdleTime()` (player.cpp:3009). */
    private val resetIdleTime: () -> Unit = {},
    /** `Player::sendCancelMessage` (player.cpp:3007, 6267, 12604). */
    private val sendCancelMessage: (ReturnValue) -> Unit = {},
    /** `Game::queuePlayerAutoWalk` (player.cpp:12594). */
    private val queuePlayerAutoWalk: (dirs: List<Direction>) -> Boolean = { false },
    /** `Creature::getPathTo(toPosition, listDir, 0, 1, true, true)` (player.cpp:12593); null = no path. */
    private val getPathTo: (toPosition: Position) -> List<Direction>? = { null },
    /** `CONDITION_FEARED` execution (player.cpp:6255-6257). */
    private val executeFearedCondition: () -> Unit = {},
    /** `g_logger().debug` (player.cpp:6254). */
    private val logDebug: (String) -> Unit = {},
    /** `Creature::addEventWalk` scheduler admission (creature.cpp:342-383). */
    private val addEventWalk: (WalkStartPolicy) -> Unit = {},
    /** `Player::getPlayer()` self view (player.cpp:6169, 1446). */
    private val getPlayer: () -> Player? = { null },
    /** `Creature::hasCondition(CONDITION_ROOTED)` (creature.cpp:331). */
    private val hasRootedCondition: () -> Boolean = { false },
    /** `Creature::getFollowCreature()` (creature.hpp:423-425). */
    private val getFollowCreature: () -> Creature? = { null },
    /** `Creature::hasFollowPath` (creature.hpp:929). */
    private val hasFollowPath: () -> Boolean = { false },
    /** `Creature::goToFollowCreature()` (creature.cpp:1116 extension). */
    private val creatureGoToFollowCreature: () -> Unit = {},
    /** `Creature::getStepDuration(dir)` (creature.cpp). */
    private val getStepDuration: (Direction) -> Int = { 0 },
    /** `Player::setNextAction` (player.cpp:3429-3433, [PlayerActionTimers]). */
    private val setNextAction: (Long) -> Unit = {},
    /** `g_game().map.getTile(pos)` (`src/game/game.cpp`). */
    private val getTile: (Position) -> Tile? = { null },
    /** `Creature::onWalk(dir)` (creature.cpp:293-301). */
    private val creatureOnWalk: (Direction) -> Unit = {},
    /** `g_callbacks().executeCallback(EventCallback_t::playerOnWalk, ...)` (player.cpp:2960). */
    private val playerOnWalkCallback: (Direction) -> Unit = {},
    /** `Player::inEventMovePush` (player.hpp). */
    private val inEventMovePush: () -> Boolean = { false },
    /** `g_configManager().getBoolean(PUSH_WHEN_ATTACKING)` (configmanager.cpp:125, default false). */
    private val pushWhenAttacking: () -> Boolean = { false },
    /** `Player::cancelPush()` (player.cpp:3016, 3058). */
    private val cancelPush: () -> Unit = {},
    /** `g_configManager().getNumber(PROTECTION_LEVEL)` (configmanager.cpp:276, default 1). */
    private val protectionLevel: () -> Int = { 1 },
    /** `g_game().getWorldType()` (game.cpp:926-928). */
    private val getWorldType: () -> WorldType = { WorldType.PVP },
    /** `ExpertPvp::isEnabled()` reading the configured `WORLD_TYPE` (expert_pvp.cpp:361-363). */
    private val expertPvpEnabled: () -> Boolean = { false },
    /** `Creature::isInGhostMode()` (player.hpp:567-569). */
    private val isInGhostMode: (Creature) -> Boolean = { false },
    /** `Monster::isFamiliar()` (monster.cpp:299). */
    private val isFamiliar: (Monster) -> Boolean = { false },
) {

    /** `Player::lastWalkthroughAttempt` (player.hpp:1740). */
    var lastWalkthroughAttempt: Long = 0
        private set

    /** `Player::lastWalkthroughPosition` (player.hpp:1714). */
    var lastWalkthroughPosition: Position = Position(0, 0, 0)
        private set

    /** `Player::lastWalking` (player.hpp:1750); the paralyze walk-exhaust window. */
    private var lastWalking = 0L

    /** `Player::nextStepEvent` (player.hpp:1791). */
    private var nextStepEvent = 0L

    /** `Player::walkTaskEvent` (player.hpp:1792). */
    private var walkTaskEvent = 0L

    /** `Player::walkTask` (player.hpp:1770); stored by `setNextWalkActionTask`. */
    private var walkTask: PlayerTask? = null

    /** `Player::actionTaskEvent` (player.hpp:1789). */
    private var actionTaskEvent = 0L

    /** `Player::actionTaskEventPush` (player.hpp:1790). */
    private var actionTaskEventPush = 0L

    /** `Player::actionPotionTaskEvent` (player.hpp). */
    private var actionPotionTaskEvent = 0L

    /** `Player::lastFailedFollow` (player.hpp); milliseconds. */
    var lastFailedFollow: Long = 0

    /** `Creature::listWalkDir` (creature.hpp:872) — the auto-walk step queue. */
    private val walkDirQueue = mutableListOf<Direction>()

    /** Read-only view of `Creature::listWalkDir` for the queue owner and tests. */
    val listWalkDir: List<Direction> get() = walkDirQueue

    /** `Player::setLastWalkthroughAttempt` (player.cpp:897-899). */
    fun setLastWalkthroughAttempt(walkthroughAttempt: Long) {
        lastWalkthroughAttempt = walkthroughAttempt
    }

    /** `Player::setLastWalkthroughPosition` (player.cpp:901-903). */
    fun setLastWalkthroughPosition(walkthroughPosition: Position) {
        lastWalkthroughPosition = walkthroughPosition
    }

    /**
     * `Player::walkExhausted` (player.cpp:3176-3182): only a running paralyze
     * condition can make the player walk-exhausted.
     */
    fun walkExhausted(): Boolean {
        if (hasParalyze()) {
            return lastWalking > now()
        }

        return false
    }

    /** `Player::setWalkExhaust` (player.cpp:3184-3186). */
    fun setWalkExhaust(value: Long) {
        lastWalking = now() + value
    }

    /** `Player::updateParalyzeWalkExhaust` (player.cpp:3188-3196). */
    fun updateParalyzeWalkExhaust() {
        if (!hasParalyze()) {
            return
        }

        val walkDelay = getWalkDelay()
        if (walkDelay > 0) {
            setWalkExhaust(walkDelay.toLong())
        }
    }

    /**
     * `Player::setNextWalkActionTask` (player.cpp:2983-2994). The C++ Task is
     * stored as-is (its lane/token are scheduler metadata) and only scheduled
     * when `onWalkComplete` fires.
     */
    fun setNextWalkActionTask(task: PlayerTask?) {
        if (walkTaskEvent != 0L) {
            stopEvent(walkTaskEvent)
            walkTaskEvent = 0
        }

        walkTask = task
    }

    /**
     * `Player::setNextWalkTask` (player.cpp:2996-3012): schedule on the
     * `PlayerWalk` lane, cancel the walk when the dispatcher rejects the event,
     * and reset the idle timer only for a live task.
     */
    fun setNextWalkTask(task: PlayerTask?) {
        if (nextStepEvent != 0L) {
            stopEvent(nextStepEvent)
            nextStepEvent = 0
        }

        if (task != null) {
            nextStepEvent = scheduleEvent(DispatcherLane.PlayerWalk, task.delayMs, task.context, task.action)
            if (nextStepEvent == 0L) {
                sendCancelWalk()
                return
            }
            resetIdleTime()
        }
    }

    /**
     * `Player::setNextActionTask` (player.cpp:3014-3036): stop the pending
     * action event, cancel a pending push unless inside an event move push or
     * configured to push while attacking, then schedule on the `PlayerAction`
     * lane and optionally reset the idle timer.
     */
    fun setNextActionTask(task: PlayerTask?, resetIdleTime: Boolean = true) {
        if (actionTaskEvent != 0L) {
            stopEvent(actionTaskEvent)
            actionTaskEvent = 0
        }

        if (!inEventMovePush() && !pushWhenAttacking()) {
            cancelPush()
        }

        if (task != null) {
            actionTaskEvent = scheduleEvent(DispatcherLane.PlayerAction, task.delayMs, task.context, task.action)
            if (actionTaskEvent == 0L) {
                sendCancelMessage(ReturnValue.NOTPOSSIBLE)
                return
            }
            if (resetIdleTime) {
                this.resetIdleTime()
            }
        }
    }

    /**
     * `Player::setNextActionPushTask` (player.cpp:3038-3052): the push action
     * owns its own dispatcher event and never touches the regular action task.
     */
    fun setNextActionPushTask(task: PlayerTask?) {
        if (actionTaskEventPush != 0L) {
            stopEvent(actionTaskEventPush)
            actionTaskEventPush = 0
        }

        if (task != null) {
            actionTaskEventPush = scheduleEvent(DispatcherLane.PlayerAction, task.delayMs, task.context, task.action)
            if (actionTaskEventPush == 0L) {
                sendCancelMessage(ReturnValue.NOTPOSSIBLE)
            }
        }
    }

    /**
     * `Player::setNextPotionActionTask` (player.cpp:3054-3070): always cancels a
     * pending push, then schedules on the `PlayerAction` lane.
     */
    fun setNextPotionActionTask(task: PlayerTask?) {
        if (actionPotionTaskEvent != 0L) {
            stopEvent(actionPotionTaskEvent)
            actionPotionTaskEvent = 0
        }

        cancelPush()

        if (task != null) {
            actionPotionTaskEvent = scheduleEvent(DispatcherLane.PlayerAction, task.delayMs, task.context, task.action)
            if (actionPotionTaskEvent == 0L) {
                sendCancelMessage(ReturnValue.NOTPOSSIBLE)
            }
        }
    }

    /**
     * `Player::goToFollowCreature` (player.cpp:6161-6171): refused while a walk
     * task is pending or inside the 2-second failure backoff; records the
     * failure time when the creature is followed without a path.
     */
    fun goToFollowCreature() {
        if (walkTask == null) {
            if ((now() - lastFailedFollow) < 2000) {
                return
            }

            creatureGoToFollowCreature()

            if (getFollowCreature() != null && !hasFollowPath()) {
                lastFailedFollow = now()
            }
        }
    }

    /**
     * `Player::onWalk` (player.cpp:2942-2961): a feared player stepping onto a
     * non-blocking damaging field delays the action instead of walking;
     * otherwise the creature walk runs, the pending action task is dropped and
     * the `playerOnWalk` callback fires.
     */
    fun onWalk(dir: Direction) {
        if (hasFearedCondition()) {
            val pos = getNextPosition(dir, getPosition())

            val tile = getTile(pos)
            if (tile != null) {
                val field = tile.getFieldItem()
                if (field != null && !field.isBlocking() && field.getDamage() != 0) {
                    setNextActionTask(null)
                    setNextAction(now() + getStepDuration(dir))
                    return
                }
            }
        }

        creatureOnWalk(dir)
        setNextActionTask(null)

        playerOnWalkCallback(dir)
    }

    /**
     * `Creature::getNextStep` (creature.cpp:315-324): pops the queued step
     * (`listWalkDir.back()`), runs [onWalk] for it and reports whether a step
     * was available. The host walk event consumes the queue through this.
     */
    fun getNextStep(): Direction? {
        val dir = walkDirQueue.removeLastOrNull() ?: return null
        onWalk(dir)
        return dir
    }

    /**
     * `Creature::startAutoWalk` (creature.cpp:326-340): the step queue is
     * replaced, rooted/feared creatures refuse the walk unless
     * [ignoreConditions] is set, and an empty queue never starts the event.
     */
    fun startAutoWalk(
        listDir: List<Direction>,
        ignoreConditions: Boolean = false,
        startPolicy: WalkStartPolicy = WalkStartPolicy.RespectDelay,
    ) {
        walkDirQueue.clear()

        if (!ignoreConditions && (hasRootedCondition() || hasFearedCondition())) {
            return
        }

        walkDirQueue.addAll(listDir)

        if (walkDirQueue.isEmpty()) {
            return
        }

        addEventWalk(startPolicy)
    }

    /**
     * `Player::canWalkthrough` (player.cpp:1424-1489): access players and ghost
     * creatures always pass; familiars (and, on no-PvP worlds, player summons)
     * always pass; players pass for one retry per 2 seconds inside the legacy
     * walkthrough zone; npcs need a house tile.
     */
    fun canWalkthrough(creature: Creature): Boolean {
        val walker = getPlayer()
        if (walker?.isAccessPlayer() == true || isInGhostMode(creature)) {
            return true
        }

        val player = creature as? Player
        val monster = creature as? Monster
        val npc = creature as? Npc
        var noPvpThroughAtSummon = false
        // Allow players to walk through summons in no pvp worlds
        if (getWorldType() == WorldType.NO_PVP) {
            val monsterMaster = monster?.getMaster()
            val monsterMasterPlayer = monsterMaster as? Player
            if (monsterMasterPlayer != null) {
                noPvpThroughAtSummon = true
            }
        }

        if (monster != null && (isFamiliar(monster) || noPvpThroughAtSummon)) {
            return true
        }

        if (player != null) {
            val playerTile = player.getTile()
            val legacyWalkthroughZone = playerTile != null &&
                (playerTile.hasFlag(TileFlags.TILESTATE_NOPVPZONE.value) ||
                    playerTile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value) ||
                    player.level <= protectionLevel() ||
                    getWorldType() == WorldType.NO_PVP)
            if (playerTile == null) {
                return false
            }

            if (!legacyWalkthroughZone && expertPvpEnabled() && walker != null) {
                val relation = ExpertPvp.classifyRelation(walker, player, getWorldType())
                val decision = ExpertPvp.canWalkThrough(relation.facts, true)
                if (decision.handled) {
                    return decision.canWalkThrough
                }
            }

            if (!legacyWalkthroughZone) {
                return false
            }

            val playerTileGround = playerTile.ground
            if (playerTileGround == null || playerTileGround.type?.walkStack != true) {
                return false
            }

            if ((now() - lastWalkthroughAttempt) > 2000) {
                setLastWalkthroughAttempt(now())
                return false
            }

            if (creature.position != lastWalkthroughPosition) {
                setLastWalkthroughPosition(creature.position)
                return false
            }

            setLastWalkthroughPosition(creature.position)
            return true
        } else if (npc != null) {
            // `tile->getHouse()` is only non-null for the C++ `HouseTile`; the
            // port's merged [Tile] marks that subclass with a non-zero houseId.
            val tile = npc.getTile()
            return tile != null && tile.isHouse()
        }

        return false
    }

    /**
     * `Player::canWalkthroughEx` (player.cpp:1492-1536): the const variant used
     * by pathfinding, without the retry throttle. Familiars pass, other monsters
     * never do, players return the legacy zone membership (plus the ExpertPvp
     * decision when the zone rejects), and npcs need a `HouseTile`.
     */
    fun canWalkthroughEx(creature: Creature): Boolean {
        val walker = getPlayer()
        if (walker?.isAccessPlayer() == true) {
            return true
        }

        val monster = creature as? Monster
        if (monster != null) {
            return isFamiliar(monster)
        }

        val player = creature as? Player
        val npc = creature as? Npc
        if (player != null) {
            val playerTile = player.getTile()
            val legacyWalkthroughZone = playerTile != null &&
                (playerTile.hasFlag(TileFlags.TILESTATE_NOPVPZONE.value) ||
                    playerTile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value) ||
                    player.level <= protectionLevel() ||
                    getWorldType() == WorldType.NO_PVP)
            if (playerTile != null && !legacyWalkthroughZone && expertPvpEnabled() && walker != null) {
                val relation = ExpertPvp.classifyRelation(walker, player, getWorldType())
                val decision = ExpertPvp.canWalkThrough(relation.facts, true)
                if (decision.handled) {
                    return decision.canWalkThrough
                }
            }

            return legacyWalkthroughZone
        } else if (npc != null) {
            val tile = npc.getTile()
            return tile != null && tile.isHouse()
        } else {
            return false
        }
    }

    /** `Player::onWalkAborted` (player.cpp:6242-6245). */
    fun onWalkAborted() {
        setNextWalkActionTask(null)
        sendCancelWalk()
    }

    /**
     * `Player::onWalkComplete` (player.cpp:6247-6271): run the feared condition
     * first, then schedule the stored walk action task on the `PlayerAction`
     * lane.
     */
    fun onWalkComplete() {
        if (hasFearedCondition()) {
            logDebug("[Player::onWalkComplete] Executing feared conditions as players completed it's walk.")
            executeFearedCondition()
        }

        val task = walkTask
        if (task != null) {
            walkTaskEvent = scheduleEvent(DispatcherLane.PlayerAction, task.delayMs, task.context, task.action)
            if (walkTaskEvent == 0L) {
                sendCancelMessage(ReturnValue.NOTPOSSIBLE)
            }
            walkTask = null
        }
    }

    /**
     * `Player::canAutoWalk` (player.cpp:12589-12606): returns false when the
     * destination is already within one square; otherwise queues the path and
     * arms the delayed action task, reporting `THEREISNOWAY` when no path
     * exists.
     */
    fun canAutoWalk(toPosition: Position, function: () -> Unit, delay: Long): Boolean {
        if (!getPosition().isWithinRange(toPosition, 1, 1)) {
            val listDir = getPathTo(toPosition)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(listDir)) {
                    return true
                }
                val task = createPlayerTask(delay, function, "Player::canAutoWalk")
                setNextWalkActionTask(task)
                return true
            } else {
                sendCancelMessage(ReturnValue.THEREISNOWAY)
                return true
            }
        }
        return false
    }

    /** `Player::createPlayerTask` (player.cpp:212-214). */
    fun createPlayerTask(delay: Long, f: () -> Unit, context: String): PlayerTask =
        PlayerTask(delayMs = delay, context = context, action = f)
}
