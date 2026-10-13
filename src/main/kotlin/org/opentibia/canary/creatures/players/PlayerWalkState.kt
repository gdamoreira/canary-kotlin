package org.opentibia.canary.creatures.players

import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

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
 * PARITY GAPS: `Player::canWalkthrough`/`canWalkthroughEx` (player.cpp:1424-1536)
 * need the tile/ExpertPvp world projection and stay with the movement
 * subsystem; `Player::onWalk` (player.cpp:2942-2961) needs the map field-item
 * lookup. Both are recorded as gaps in the wave report.
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
