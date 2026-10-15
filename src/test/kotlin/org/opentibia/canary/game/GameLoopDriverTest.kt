package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap

/**
 * Transcript tests for the `Game::start` loop registrations
 * (src/game/game.cpp:862-938): the maintenance/cycle event set with their
 * explicit `DispatcherLane` (`checkCreatures` stays on `WorldCommit` like the
 * C++ default, every other registration is pinned to `Maintenance`) and the
 * one-shot registration guard the composition-driven loop relies on.
 */
class GameLoopDriverTest {

    private class Seams(
        val game: Game,
        val scheduled: MutableList<Long>,
        val cycled: MutableList<Pair<Long, DispatcherLane>>,
    )

    private fun gameWithSeams(): Seams {
        val game = Game(GameMap(), GameDispatcher())
        val scheduled = mutableListOf<Long>()
        val cycled = mutableListOf<Pair<Long, DispatcherLane>>()
        game.scheduleEvent = { delay, _ ->
            scheduled.add(delay)
            1L
        }
        game.cycleEvent = { delay, lane, _ ->
            cycled.add(delay to lane)
            1L
        }
        game.getTimeNow = { 1704090600L }
        return Seams(game, scheduled, cycled)
    }

    @Test
    fun `start registers the maintenance and cycle events with the C++ delays and lanes`() {
        val seams = gameWithSeams()

        seams.game.start()

        assertThat(seams.scheduled).containsExactly(11_000L, 11_000L)
        assertThat(seams.cycled).containsExactly(
            10_000L to DispatcherLane.Maintenance,
            10_000L to DispatcherLane.Maintenance,
            1_000L to DispatcherLane.WorldCommit,
            600_000L to DispatcherLane.Maintenance,
            1_800_000L to DispatcherLane.Maintenance,
            600_000L to DispatcherLane.Maintenance,
        )
    }

    @Test
    fun `start is a one-shot registration across repeated boots`() {
        val seams = gameWithSeams()

        seams.game.start()
        seams.game.start()
        seams.game.start()

        assertThat(seams.scheduled).containsExactly(11_000L, 11_000L)
        assertThat(seams.cycled).hasSize(6)
    }

    @Test
    fun `start keeps the market cycle off when the refresh interval is zero`() {
        val seams = gameWithSeams()
        seams.game.marketRefreshPricesIntervalMinutes = 0

        seams.game.start()

        assertThat(seams.cycled.map { it.first })
            .containsExactly(10_000L, 10_000L, 1_000L, 600_000L, 600_000L)
        assertThat(seams.cycled.map { it.second })
            .containsExactly(
                DispatcherLane.Maintenance,
                DispatcherLane.Maintenance,
                DispatcherLane.WorldCommit,
                DispatcherLane.Maintenance,
                DispatcherLane.Maintenance,
            )
    }
}
