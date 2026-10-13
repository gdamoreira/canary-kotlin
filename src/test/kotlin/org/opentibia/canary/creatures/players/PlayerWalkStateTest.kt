package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcribes the Player walk/position state
 * (src/creatures/players/player.cpp:212-214, 897-903, 2983-3012, 3176-3196,
 * 6242-6271, 12589-12606):
 *
 * - `Player::createPlayerTask` (player.cpp:212-214)
 * - `Player::setLastWalkthroughAttempt` / `setLastWalkthroughPosition`
 *   (player.cpp:897-903)
 * - `Player::setNextWalkActionTask` / `setNextWalkTask`
 *   (player.cpp:2983-3012)
 * - `Player::walkExhausted` / `setWalkExhaust` / `updateParalyzeWalkExhaust`
 *   (player.cpp:3176-3196)
 * - `Player::onWalkAborted` / `onWalkComplete` (player.cpp:6242-6271)
 * - `Player::canAutoWalk` (player.cpp:12589-12606)
 *
 * The dispatcher is the injected [PlayerWalkState] scheduler seam; the C++
 * `Task` (src/game/scheduling/task.hpp:29) is the port's [PlayerTask].
 */
class PlayerWalkStateTest {

    private var now = 0L

    private class WalkSink {
        var paralyze = false
        var feared = false
        var walkDelay = 0
        var cancelWalks = 0
        var idleResets = 0
        var fearedExecutions = 0
        val logs = mutableListOf<String>()
        val cancels = mutableListOf<ReturnValue>()
        var queueAccepted = true
        var path: List<Direction>? = null
        var queuedDirs: List<Direction>? = null
        val scheduled = mutableListOf<Scheduled>()
        val stopped = mutableListOf<Long>()
        var failScheduling = false
    }

    private data class Scheduled(
        val eventId: Long,
        val lane: DispatcherLane,
        val delayMs: Long,
        val context: String,
        val action: () -> Unit,
    )

    private fun newState(sink: WalkSink = WalkSink(), position: () -> Position = { Position(100, 100, 7) }) =
        PlayerWalkState(
            now = { now },
            getPosition = position,
            hasParalyze = { sink.paralyze },
            hasFearedCondition = { sink.feared },
            getWalkDelay = { sink.walkDelay },
            sendCancelWalk = { sink.cancelWalks++ },
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
            resetIdleTime = { sink.idleResets++ },
            sendCancelMessage = { sink.cancels.add(it) },
            queuePlayerAutoWalk = { dirs ->
                sink.queuedDirs = dirs
                sink.queueAccepted
            },
            getPathTo = { sink.path },
            executeFearedCondition = { sink.fearedExecutions++ },
            logDebug = { sink.logs.add(it) },
        )

    @Test
    fun `walk exhaustion requires the paralyze condition`() {
        // Player::walkExhausted (player.cpp:3176-3182).
        val sink = WalkSink()
        val state = newState(sink)
        now = 1_000
        state.setWalkExhaust(500)

        assertThat(state.walkExhausted()).isFalse()

        sink.paralyze = true
        assertThat(state.walkExhausted()).isTrue()

        now = 1_500
        assertThat(state.walkExhausted()).isFalse()
    }

    @Test
    fun `setWalkExhaust anchors the window at the current time`() {
        // Player::setWalkExhaust (player.cpp:3184-3186).
        val sink = WalkSink()
        sink.paralyze = true
        val state = newState(sink)
        now = 2_000
        state.setWalkExhaust(750)

        now = 2_749
        assertThat(state.walkExhausted()).isTrue()
        now = 2_750
        assertThat(state.walkExhausted()).isFalse()
    }

    @Test
    fun `updateParalyzeWalkExhaust refreshes only for a positive walk delay`() {
        // Player::updateParalyzeWalkExhaust (player.cpp:3188-3196).
        val sink = WalkSink()
        val state = newState(sink)
        now = 3_000

        state.updateParalyzeWalkExhaust()
        sink.paralyze = true
        state.updateParalyzeWalkExhaust()
        assertThat(state.walkExhausted()).isFalse()

        sink.walkDelay = 900
        state.updateParalyzeWalkExhaust()
        now = 3_899
        assertThat(state.walkExhausted()).isTrue()
    }

    @Test
    fun `walkthrough throttle state is stored`() {
        // Player::setLastWalkthroughAttempt (player.cpp:897-899) and
        // setLastWalkthroughPosition (player.cpp:901-903).
        val state = newState()
        now = 4_000
        state.setLastWalkthroughAttempt(now)
        state.setLastWalkthroughPosition(Position(101, 102, 7))

        assertThat(state.lastWalkthroughAttempt).isEqualTo(4_000)
        assertThat(state.lastWalkthroughPosition).isEqualTo(Position(101, 102, 7))
    }

    @Test
    fun `setNextWalkTask schedules on the PlayerWalk lane and resets idle time`() {
        // Player::setNextWalkTask (player.cpp:2996-3012).
        val sink = WalkSink()
        val state = newState(sink)
        var ran = 0
        state.setNextWalkTask(PlayerTask(delayMs = 0, context = "walk", action = { ran++ }))

        val scheduled = sink.scheduled.single()
        assertThat(scheduled.lane).isEqualTo(DispatcherLane.PlayerWalk)
        assertThat(scheduled.context).isEqualTo("walk")
        assertThat(sink.idleResets).isEqualTo(1)

        scheduled.action()
        assertThat(ran).isEqualTo(1)
    }

    @Test
    fun `setNextWalkTask stops the previous event`() {
        // player.cpp:2997-3000: the previous nextStepEvent is stopped.
        val sink = WalkSink()
        val state = newState(sink)
        state.setNextWalkTask(PlayerTask(0, "first", {}))
        val firstEvent = sink.scheduled.single().eventId

        state.setNextWalkTask(PlayerTask(0, "second", {}))

        assertThat(sink.stopped).containsExactly(firstEvent)
        assertThat(sink.scheduled).hasSize(2)
    }

    @Test
    fun `setNextWalkTask with null only stops the pending event`() {
        // Game::queuePlayerAutoWalk cancels through setNextWalkTask(nullptr)
        // (src/game/game.cpp:4490-4496).
        val sink = WalkSink()
        val state = newState(sink)
        state.setNextWalkTask(PlayerTask(0, "first", {}))
        val firstEvent = sink.scheduled.single().eventId

        state.setNextWalkTask(null)

        assertThat(sink.stopped).containsExactly(firstEvent)
        assertThat(sink.scheduled).hasSize(1)
        assertThat(sink.idleResets).isEqualTo(1)
    }

    @Test
    fun `setNextWalkTask cancels the walk when the dispatcher rejects the event`() {
        // player.cpp:3005-3008: scheduleEvent == 0 sends sendCancelWalk().
        val sink = WalkSink()
        sink.failScheduling = true
        val state = newState(sink)

        state.setNextWalkTask(PlayerTask(0, "walk", {}))

        assertThat(sink.cancelWalks).isEqualTo(1)
        assertThat(sink.idleResets).isZero()
    }

    @Test
    fun `setNextWalkActionTask stores the task without scheduling`() {
        // Player::setNextWalkActionTask (player.cpp:2983-2994).
        val sink = WalkSink()
        val state = newState(sink)
        state.setNextWalkActionTask(PlayerTask(250, "action", {}))

        assertThat(sink.scheduled).isEmpty()

        state.onWalkComplete()

        val scheduled = sink.scheduled.single()
        assertThat(scheduled.lane).isEqualTo(DispatcherLane.PlayerAction)
        assertThat(scheduled.delayMs).isEqualTo(250)
        assertThat(scheduled.context).isEqualTo("action")
    }

    @Test
    fun `setNextWalkActionTask replaces the stored task and stops its event`() {
        // player.cpp:2984-2990: walkTaskEvent is stopped before replacing.
        val sink = WalkSink()
        val state = newState(sink)
        state.setNextWalkActionTask(PlayerTask(0, "first", {}))
        state.onWalkComplete()
        val firstEvent = sink.scheduled.single().eventId

        state.setNextWalkActionTask(PlayerTask(0, "second", {}))

        assertThat(sink.stopped).containsExactly(firstEvent)
    }

    @Test
    fun `onWalkAborted clears the task and sends cancel walk`() {
        // Player::onWalkAborted (player.cpp:6242-6245).
        val sink = WalkSink()
        val state = newState(sink)
        state.setNextWalkActionTask(PlayerTask(0, "action", {}))
        state.onWalkComplete()
        sink.scheduled.clear()

        state.onWalkAborted()
        state.onWalkComplete()

        assertThat(sink.cancelWalks).isEqualTo(1)
        assertThat(sink.scheduled).isEmpty()
    }

    @Test
    fun `onWalkComplete schedules the stored action task`() {
        // Player::onWalkComplete (player.cpp:6260-6270).
        val sink = WalkSink()
        val state = newState(sink)
        var ran = 0
        state.setNextWalkActionTask(PlayerTask(0, "action", { ran++ }))

        state.onWalkComplete()

        assertThat(sink.fearedExecutions).isZero()
        assertThat(sink.logs).isEmpty()

        val scheduled = sink.scheduled.single()
        scheduled.action()
        assertThat(ran).isEqualTo(1)
    }

    @Test
    fun `onWalkComplete executes the feared condition when feared`() {
        // player.cpp:6248-6258: the feared condition is executed as the walk
        // completes.
        val sink = WalkSink()
        sink.feared = true
        val state = newState(sink)

        state.onWalkComplete()

        assertThat(sink.fearedExecutions).isEqualTo(1)
        assertThat(sink.logs.single())
            .isEqualTo("[Player::onWalkComplete] Executing feared conditions as players completed it's walk.")
    }

    @Test
    fun `onWalkComplete cancels with NOTPOSSIBLE when scheduling fails`() {
        // player.cpp:6266-6268.
        val sink = WalkSink()
        sink.failScheduling = true
        val state = newState(sink)
        state.setNextWalkActionTask(PlayerTask(0, "action", {}))

        state.onWalkComplete()

        assertThat(sink.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `canAutoWalk returns false when already in range`() {
        // Player::canAutoWalk (player.cpp:12589-12591).
        val sink = WalkSink()
        val state = newState(sink, position = { Position(100, 100, 7) })

        assertThat(state.canAutoWalk(Position(101, 101, 7), {}, 0)).isFalse()
        assertThat(sink.queuedDirs).isNull()
    }

    @Test
    fun `canAutoWalk queues the path and stores the delayed action task`() {
        // player.cpp:12592-12602: queuePlayerAutoWalk then createPlayerTask.
        val sink = WalkSink()
        sink.path = listOf(Direction.East, Direction.North)
        val state = newState(sink, position = { Position(100, 100, 7) })
        var walked = 0

        val handled = state.canAutoWalk(Position(105, 100, 7), { walked++ }, 300)

        assertThat(handled).isTrue()
        assertThat(sink.queuedDirs).containsExactly(Direction.East, Direction.North)

        state.onWalkComplete()
        val scheduled = sink.scheduled.single()
        assertThat(scheduled.lane).isEqualTo(DispatcherLane.PlayerAction)
        assertThat(scheduled.delayMs).isEqualTo(300)
        assertThat(scheduled.context).isEqualTo("Player::canAutoWalk")
        scheduled.action()
        assertThat(walked).isEqualTo(1)
    }

    @Test
    fun `canAutoWalk keeps handled when the queue rejects the walk`() {
        // player.cpp:12594-12596: a rejected queue returns true without a task.
        val sink = WalkSink()
        sink.path = listOf(Direction.East)
        sink.queueAccepted = false
        val state = newState(sink)

        assertThat(state.canAutoWalk(Position(105, 100, 7), {}, 0)).isTrue()

        state.onWalkComplete()
        assertThat(sink.scheduled).isEmpty()
    }

    @Test
    fun `canAutoWalk cancels with THEREISNOWAY when no path exists`() {
        // player.cpp:12603-12605.
        val sink = WalkSink()
        sink.path = null
        val state = newState(sink)

        assertThat(state.canAutoWalk(Position(105, 100, 7), {}, 0)).isTrue()

        assertThat(sink.cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `createPlayerTask mirrors the static Task factory`() {
        // Player::createPlayerTask (player.cpp:212-214).
        val state = newState()
        val task = state.createPlayerTask(500, {}, "context")

        assertThat(task.delayMs).isEqualTo(500)
        assertThat(task.context).isEqualTo("context")
    }
}
