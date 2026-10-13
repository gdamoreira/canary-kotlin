package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Game` report handlers:
 * `Game::playerReportRuleViolationReport` (game.cpp:10183) and
 * `Game::playerReportBug` (game.cpp:10193). Both only dispatch the Lua event and
 * callback buckets, which this facade exposes as injected seams (AGENTS.md 1.4).
 */
class GameReportTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun player(id: Long, name: String): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center)

    @Test
    fun `playerReportBug dispatches the event and the callback with the payload`() {
        val game = newGame()
        val player = player(1, "Knight")
        game.addPlayer(player)
        val events = mutableListOf<Triple<Player, String, Position>>()
        val callbacks = mutableListOf<Triple<Player, String, Position>>()
        game.eventPlayerOnReportBug = { reporter, message, position, _ -> events.add(Triple(reporter, message, position)) }
        game.callbackPlayerOnReportBug = { reporter, message, position, _ -> callbacks.add(Triple(reporter, message, position)) }
        val reported = Position(1001, 1002, 7)

        game.playerReportBug(1, "Stuck on a rock", reported, category = 0)

        assertThat(events).containsExactly(Triple(player, "Stuck on a rock", reported))
        assertThat(callbacks).containsExactly(Triple(player, "Stuck on a rock", reported))
    }

    @Test
    fun `playerReportBug ignores an unknown player id`() {
        val game = newGame()
        var dispatched = 0
        game.eventPlayerOnReportBug = { _, _, _, _ -> dispatched++ }
        game.callbackPlayerOnReportBug = { _, _, _, _ -> dispatched++ }

        game.playerReportBug(99, "Stuck", center, category = 0)

        assertThat(dispatched).isZero()
    }

    @Test
    fun `playerReportRuleViolationReport dispatches the event and the callback`() {
        val game = newGame()
        val player = player(1, "Knight")
        game.addPlayer(player)
        val events = mutableListOf<List<Any?>>()
        val callbacks = mutableListOf<List<Any?>>()
        game.eventPlayerOnReportRuleViolation = { reporter, target, type, reason, comment, translation ->
            events.add(listOf(reporter, target, type, reason, comment, translation))
        }
        game.callbackPlayerOnReportRuleViolation = { reporter, target, type, reason, comment, translation ->
            callbacks.add(listOf(reporter, target, type, reason, comment, translation))
        }

        game.playerReportRuleViolationReport(1, "Cheater", reportType = 1, reportReason = 2, comment = "note", translation = "translated")

        val expected = listOf(player, "Cheater", 1, 2, "note", "translated")
        assertThat(events).containsExactly(expected)
        assertThat(callbacks).containsExactly(expected)
    }

    @Test
    fun `playerReportRuleViolationReport ignores an unknown player id`() {
        val game = newGame()
        var dispatched = 0
        game.eventPlayerOnReportRuleViolation = { _, _, _, _, _, _ -> dispatched++ }
        game.callbackPlayerOnReportRuleViolation = { _, _, _, _, _, _ -> dispatched++ }

        game.playerReportRuleViolationReport(99, "Cheater", 1, 2, "note", "translated")

        assertThat(dispatched).isZero()
    }
}
