package org.opentibia.canary.game

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.IOBosstiary
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.prey.PreyAction
import org.opentibia.canary.systems.prey.PreyDataState
import org.opentibia.canary.systems.prey.PreyOption
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.taskhunting.PreyTaskAction
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState

/**
 * Transcript tests for the quest/forge/bosstiary/task-hunting `Game` handlers:
 *
 *  - `Game::playerShowQuestLog` (game.cpp:7391) / `Game::playerShowQuestLine` (game.cpp:7401)
 *  - `Game::playerPreyAction` (game.cpp:10218) / `Game::playerTaskHuntingAction` (game.cpp:10227)
 *  - `Game::playerBosstiarySlot` (game.cpp:11256)
 *  - `Game::playerForgeResourceConversion` (game.cpp:11226) /
 *    `Game::playerBrowseForgeHistory` (game.cpp:11241) /
 *    `Game::playerForgeTransferItemTier` (game.cpp:11211)
 */
class GameQuestForgeBosstiaryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var ioBosstiary: IOBosstiary

    private val center = Position(1000, 1000, 7)

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_game_bosstiary_${System.nanoTime()}")
        ioBosstiary = IOBosstiary(dataSource = dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newGame(): Game = Game(GameMap(), GameDispatcher(), ioBosstiary = ioBosstiary)

    private fun Game.player(id: Long, name: String): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also { addPlayer(it) }

    // ----- quest log -----

    @Test
    fun `playerShowQuestLog dispatches the event and callback`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val calls = mutableListOf<String>()
        game.eventPlayerOnRequestQuestLog = { calls.add("event") }
        game.callbackPlayerOnRequestQuestLog = { calls.add("callback") }

        game.playerShowQuestLog(player.id)

        assertThat(calls).containsExactly("event", "callback")
    }

    @Test
    fun `playerShowQuestLog ignores an unknown player`() {
        val game = newGame()
        var invoked = false
        game.eventPlayerOnRequestQuestLog = { invoked = true }

        game.playerShowQuestLog(404)

        assertThat(invoked).isFalse()
    }

    @Test
    fun `playerShowQuestLine dispatches the quest id to both hooks`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val events = mutableListOf<Int>()
        val callbacks = mutableListOf<Int>()
        game.eventPlayerOnRequestQuestLine = { _, questId -> events.add(questId) }
        game.callbackPlayerOnRequestQuestLine = { _, questId -> callbacks.add(questId) }

        game.playerShowQuestLine(player.id, 77)

        assertThat(events).containsExactly(77)
        assertThat(callbacks).containsExactly(77)
    }

    @Test
    fun `playerShowQuestLine ignores an unknown player`() {
        val game = newGame()
        var invoked = false
        game.eventPlayerOnRequestQuestLine = { _, _ -> invoked = true }

        game.playerShowQuestLine(404, 1)

        assertThat(invoked).isFalse()
    }

    // ----- prey -----

    @Test
    fun `playerPreyAction ignores an unknown player`() {
        val game = newGame()

        game.playerPreyAction(404, 0, PreyAction.ListAllCards.value, PreyOption.None.value, -1, 0)
    }

    @Test
    fun `playerPreyAction ignores an unknown action byte`() {
        val game = newGame()
        val player = game.player(1, "Hunter")
        player.initializePrey()
        player.addPreyCards(10)

        game.playerPreyAction(player.id, 0, 99, PreyOption.None.value, -1, 0)

        assertThat(player.features.prey.getPreyCards()).isEqualTo(10)
    }

    @Test
    fun `playerPreyAction delegates to the prey engine`() {
        val game = newGame()
        val player = game.player(1, "Hunter")
        player.initializePrey()
        player.addPreyCards(10)

        game.playerPreyAction(player.id, 0, PreyAction.ListAllCards.value, PreyOption.None.value, -1, 0)

        val slot = player.features.prey.getPreySlotById(0)
        assertThat(slot?.state).isEqualTo(PreyDataState.ListSelection)
        assertThat(slot?.selectedRaceId).isEqualTo(0)
        assertThat(player.features.prey.getPreyCards()).isEqualTo(5)
    }

    // ----- task hunting -----

    @Test
    fun `playerTaskHuntingAction ignores an unknown player`() {
        val game = newGame()

        game.playerTaskHuntingAction(404, 0, PreyTaskAction.ListAllCards.value, upgrade = false, raceId = 0)
    }

    @Test
    fun `playerTaskHuntingAction delegates to the task hunting engine`() {
        val game = newGame()
        val player = game.player(1, "Hunter")
        player.initializeTaskHunting()
        player.features.taskHunting.addPreyCards(10)

        game.playerTaskHuntingAction(player.id, 0, PreyTaskAction.ListAllCards.value, upgrade = false, raceId = 0)

        val slot = player.features.taskHunting.getTaskHuntingSlotById(0)
        assertThat(slot?.state).isEqualTo(PreyTaskDataState.ListSelection)
        assertThat(player.features.taskHunting.getPreyCards()).isEqualTo(5)
    }

    @Test
    fun `playerTaskHuntingAction ignores an unknown action byte`() {
        val game = newGame()
        val player = game.player(1, "Hunter")
        player.initializeTaskHunting()
        player.features.taskHunting.addPreyCards(10)

        game.playerTaskHuntingAction(player.id, 0, 99, upgrade = false, raceId = 0)

        assertThat(player.features.taskHunting.getPreyCards()).isEqualTo(10)
    }

    // ----- bosstiary -----

    @Test
    fun `playerBosstiarySlot ignores an unknown player`() {
        val game = newGame()
        var set = false
        game.setSlotBossId = { _, _, _ -> set = true }

        game.playerBosstiarySlot(404, 1, 0)

        assertThat(set).isFalse()
    }

    @Test
    fun `playerBosstiarySlot rejects an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Slayer")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        var set = false
        game.setSlotBossId = { _, _, _ -> set = true }

        game.playerBosstiarySlot(player.id, 1, 5)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(set).isFalse()
    }

    @Test
    fun `playerBosstiarySlot charges the removal price when clearing a non-boosted slot`() {
        val game = newGame()
        val player = game.player(1, "Slayer")
        ioBosstiary.setBossBoostedId(7)
        game.getSlotBossId = { _, _ -> 5L }
        game.getRemoveTimes = { 2 }
        val charges = mutableListOf<Triple<Long, Int, Boolean>>()
        game.removeMoney = { _, money, flags, useBalance -> charges.add(Triple(money, flags, useBalance)); true }
        var addRemoveTime = 0
        game.addRemoveTime = { addRemoveTime++ }
        val slotUpdates = mutableListOf<Pair<Int, Long>>()
        game.setSlotBossId = { _, slotId, bossId -> slotUpdates.add(slotId to bossId) }

        game.playerBosstiarySlot(player.id, 1, 0)

        assertThat(charges).containsExactly(Triple(100000L, 0, true))
        assertThat(addRemoveTime).isEqualTo(1)
        assertThat(slotUpdates).containsExactly(1 to 0L)
    }

    @Test
    fun `playerBosstiarySlot does not charge when the slot holds the boosted boss`() {
        val game = newGame()
        val player = game.player(1, "Slayer")
        ioBosstiary.setBossBoostedId(7)
        game.getSlotBossId = { _, _ -> 7L }
        game.getRemoveTimes = { 2 }
        var charged = false
        game.removeMoney = { _, _, _, _ -> charged = true; true }
        val slotUpdates = mutableListOf<Pair<Int, Long>>()
        game.setSlotBossId = { _, slotId, bossId -> slotUpdates.add(slotId to bossId) }

        game.playerBosstiarySlot(player.id, 1, 0)

        assertThat(charged).isFalse()
        assertThat(slotUpdates).containsExactly(1 to 0L)
    }

    @Test
    fun `playerBosstiarySlot stores a selected boss without clearing the slot`() {
        val game = newGame()
        val player = game.player(1, "Slayer")
        var charged = false
        game.removeMoney = { _, _, _, _ -> charged = true; true }
        val slotUpdates = mutableListOf<Pair<Int, Long>>()
        game.setSlotBossId = { _, slotId, bossId -> slotUpdates.add(slotId to bossId) }

        game.playerBosstiarySlot(player.id, 1, 42)

        assertThat(charged).isFalse()
        assertThat(slotUpdates).containsExactly(1 to 42L)
    }

    // ----- forge -----

    @Test
    fun `playerForgeResourceConversion rejects an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Smith")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        var converted = false
        game.forgeResourceConversion = { _, _ -> converted = true }

        game.playerForgeResourceConversion(player.id, ForgeAction_t.DUSTTOSLIVERS)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(converted).isFalse()
    }

    @Test
    fun `playerForgeResourceConversion updates the exhausted state and delegates`() {
        val game = newGame()
        val player = game.player(1, "Smith")
        val calls = mutableListOf<String>()
        game.updateUIExhausted = { calls.add("exhausted") }
        game.forgeResourceConversion = { _, action -> calls.add("convert:$action") }

        game.playerForgeResourceConversion(player.id, ForgeAction_t.SLIVERSTOCORES)

        assertThat(calls).containsExactly("exhausted", "convert:SLIVERSTOCORES")
    }

    @Test
    fun `playerBrowseForgeHistory updates the exhausted state and delegates the page`() {
        val game = newGame()
        val player = game.player(1, "Smith")
        val calls = mutableListOf<String>()
        game.updateUIExhausted = { calls.add("exhausted") }
        game.forgeHistory = { _, page -> calls.add("history:$page") }

        game.playerBrowseForgeHistory(player.id, 3)

        assertThat(calls).containsExactly("exhausted", "history:3")
    }

    @Test
    fun `playerForgeTransferItemTier delegates the transfer arguments`() {
        val game = newGame()
        val player = game.player(1, "Smith")
        data class Transfer(val action: ForgeAction_t, val donor: Int, val tier: Int, val receive: Int, val convergence: Boolean)

        val transfers = mutableListOf<Transfer>()
        game.forgeTransferItemTier = { _, action, donor, tier, receive, convergence ->
            transfers.add(Transfer(action, donor, tier, receive, convergence))
        }

        game.playerForgeTransferItemTier(player.id, ForgeAction_t.TRANSFER, 2160, 3, 2161, convergence = true)

        assertThat(transfers).containsExactly(Transfer(ForgeAction_t.TRANSFER, 2160, 3, 2161, true))
    }

    @Test
    fun `playerForgeTransferItemTier rejects an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Smith")
        game.isUIExhausted = { true }
        var transferred = false
        game.forgeTransferItemTier = { _, _, _, _, _, _ -> transferred = true }

        game.playerForgeTransferItemTier(player.id, ForgeAction_t.TRANSFER, 2160, 3, 2161, convergence = false)

        assertThat(transferred).isFalse()
    }
}
