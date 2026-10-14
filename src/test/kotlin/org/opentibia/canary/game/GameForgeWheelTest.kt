package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.wheel.DestroyGemCommand
import org.opentibia.canary.network.protocol.wheel.ImproveGemGradeCommand
import org.opentibia.canary.network.protocol.wheel.SaveWheelCommand
import org.opentibia.canary.network.protocol.wheel.ToggleGemLockCommand
import org.opentibia.canary.network.protocol.wheel.WheelSlotPoints
import org.opentibia.canary.systems.forge.Forge
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.forge.ForgeConfig
import org.opentibia.canary.systems.forge.ForgeRandom
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.wheel.PlayerWheelGem
import org.opentibia.canary.systems.wheel.WheelApplication
import org.opentibia.canary.systems.wheel.WheelFragmentType
import org.opentibia.canary.systems.wheel.WheelSlots

/**
 * Transcript tests for the forge/wheel `Game` handlers:
 *
 *  - `Game::playerForgeFuseItems` (game.cpp:11178)
 *  - `Game::playerOpenWheel` (game.cpp:11525)
 *  - `Game::playerSaveWheel` (game.cpp:11538)
 *  - `Game::playerWheelGemAction` (game.cpp:11554)
 */
class GameForgeWheelTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(
        forge: Forge = Forge(ForgeConfig(baseSuccessRate = 50, bonusSuccessRate = 15)),
        rolls: List<Int> = emptyList(),
    ): Game {
        var rollIndex = 0
        val random = ForgeRandom { _, _ -> rolls.getOrElse(rollIndex++) { 1 } }
        return Game(
            GameMap(),
            GameDispatcher(),
            forge = forge,
            forgeRandom = random,
            wheelApplication = WheelApplication(),
        )
    }

    private fun Game.player(id: Long, name: String): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also { addPlayer(it) }

    // ----- playerForgeFuseItems -----

    @Test
    fun `playerForgeFuseItems ignores an unknown player`() {
        val game = newGame()
        var fused = false
        game.forgeFuseItems = { _, _, _, _, _, _, _, _, _, _ -> fused = true }

        game.playerForgeFuseItems(404, ForgeAction_t.FUSION, 2160, 1, 2161, false, false, false)

        assertThat(fused).isFalse()
    }

    @Test
    fun `playerForgeFuseItems refuses an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Smith")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        var fused = false
        game.forgeFuseItems = { _, _, _, _, _, _, _, _, _, _ -> fused = true }

        game.playerForgeFuseItems(player.id, ForgeAction_t.FUSION, 2160, 1, 2161, false, false, false)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(fused).isFalse()
    }

    @Test
    fun `playerForgeFuseItems rolls the success and bonus before delegating`() {
        val game = newGame(rolls = listOf(66, 9001))
        val player = game.player(1, "Smith")
        data class Fusion(
            val action: ForgeAction_t,
            val firstItem: Int,
            val tier: Int,
            val secondItem: Int,
            val success: Boolean,
            val reduceTierLoss: Boolean,
            val convergence: Boolean,
            val bonus: Int,
            val coreCount: Int,
        )

        val fusions = mutableListOf<Fusion>()
        game.forgeFuseItems = { _, action, first, tier, second, success, reduce, convergence, bonus, coreCount ->
            fusions.add(Fusion(action, first, tier, second, success, reduce, convergence, bonus, coreCount))
        }

        game.playerForgeFuseItems(player.id, ForgeAction_t.FUSION, 2160, 3, 2161, usedCore = true, reduceTierLoss = true, convergence = false)

        assertThat(fusions).containsExactly(
            Fusion(ForgeAction_t.FUSION, 2160, 3, 2161, success = false, reduceTierLoss = true, convergence = false, bonus = 2, coreCount = 2),
        )
    }

    @Test
    fun `playerForgeFuseItems adds the core success rate to the roll ceiling`() {
        val game = newGame(rolls = listOf(65, 0))
        val player = game.player(1, "Smith")
        var success = false
        game.forgeFuseItems = { _, _, _, _, _, rolledSuccess, _, _, _, _ -> success = rolledSuccess }

        game.playerForgeFuseItems(player.id, ForgeAction_t.FUSION, 2160, 1, 2161, usedCore = true, reduceTierLoss = false, convergence = false)

        assertThat(success).isTrue()
    }

    @Test
    fun `playerForgeFuseItems applies the eventscheduler forge chance clamp`() {
        val game = newGame(rolls = listOf(100, 0))
        val player = game.player(1, "Smith")
        game.getForgeChance = { 200 }
        var success = false
        game.forgeFuseItems = { _, _, _, _, _, rolledSuccess, _, _, _, _ -> success = rolledSuccess }

        game.playerForgeFuseItems(player.id, ForgeAction_t.FUSION, 2160, 1, 2161, false, false, false)

        assertThat(success).isTrue()
    }

    @Test
    fun `playerForgeFuseItems clamps a negative forge chance to zero`() {
        val game = newGame(rolls = listOf(1, 0))
        val player = game.player(1, "Smith")
        game.getForgeChance = { -100 }
        var success = true
        game.forgeFuseItems = { _, _, _, _, _, rolledSuccess, _, _, _, _ -> success = rolledSuccess }

        game.playerForgeFuseItems(player.id, ForgeAction_t.FUSION, 2160, 1, 2161, false, false, false)

        assertThat(success).isFalse()
    }

    @Test
    fun `playerForgeFuseItems zeroes the bonus when converging`() {
        val game = newGame(rolls = listOf(1, 9975))
        val player = game.player(1, "Smith")
        var bonus = -1
        game.forgeFuseItems = { _, _, _, _, _, _, _, convergence, rolledBonus, _ ->
            assertThat(convergence).isTrue()
            bonus = rolledBonus
        }

        game.playerForgeFuseItems(player.id, ForgeAction_t.FUSION, 2160, 1, 2161, false, false, convergence = true)

        assertThat(bonus).isZero()
    }

    @Test
    fun `playerForgeFuseItems updates the exhausted state before delegating`() {
        val game = newGame()
        val player = game.player(1, "Smith")
        val calls = mutableListOf<String>()
        game.updateUIExhausted = { calls.add("exhausted") }
        game.forgeFuseItems = { _, _, _, _, _, _, _, _, _, _ -> calls.add("fuse") }

        game.playerForgeFuseItems(player.id, ForgeAction_t.FUSION, 2160, 1, 2161, false, false, false)

        assertThat(calls).containsExactly("exhausted", "fuse")
    }

    // ----- playerOpenWheel -----

    @Test
    fun `playerOpenWheel ignores an unknown player`() {
        val game = newGame()
        var opened = false
        game.sendOpenWheelWindow = { _, _ -> opened = true }

        game.playerOpenWheel(404, 404)

        assertThat(opened).isFalse()
    }

    @Test
    fun `playerOpenWheel rejects opening another player's wheel`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        var opened = false
        game.sendOpenWheelWindow = { _, _ -> opened = true }

        game.playerOpenWheel(player.id, 2)

        assertThat(opened).isFalse()
    }

    @Test
    fun `playerOpenWheel refuses an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        var opened = false
        game.sendOpenWheelWindow = { _, _ -> opened = true }

        game.playerOpenWheel(player.id, player.id)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(opened).isFalse()
    }

    @Test
    fun `playerOpenWheel sends the window and updates the exhausted state`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val calls = mutableListOf<String>()
        game.sendOpenWheelWindow = { _, ownerId -> calls.add("open:$ownerId") }
        game.updateUIExhausted = { calls.add("exhausted") }

        game.playerOpenWheel(player.id, player.id)

        assertThat(calls).containsExactly("open:1", "exhausted")
    }

    // ----- playerSaveWheel -----

    @Test
    fun `playerSaveWheel refuses an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSaveWheel(player.id, SaveWheelCommand(listOf(WheelSlotPoints(WheelSlots.SLOT_GREEN_50, 50)), emptyList()))

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(player.features.wheel.getPointsBySlotType(WheelSlots.SLOT_GREEN_50)).isZero()
    }

    @Test
    fun `playerSaveWheel applies the slot points and updates the exhausted state`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        player.level = 100
        val calls = mutableListOf<String>()
        game.updateUIExhausted = { calls.add("exhausted") }

        game.playerSaveWheel(
            player.id,
            SaveWheelCommand(listOf(WheelSlotPoints(WheelSlots.SLOT_GREEN_50, 50)), emptyList()),
        )

        assertThat(player.features.wheel.getPointsBySlotType(WheelSlots.SLOT_GREEN_50)).isEqualTo(50)
        assertThat(calls).containsExactly("exhausted")
    }

    // ----- playerWheelGemAction -----

    @Test
    fun `playerWheelGemAction ignores an unknown player`() {
        val game = newGame()

        game.playerWheelGemAction(404, ToggleGemLockCommand(0))
    }

    @Test
    fun `playerWheelGemAction refuses an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerWheelGemAction(player.id, ToggleGemLockCommand(0))

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
    }

    @Test
    fun `playerWheelGemAction dispatches the toggle lock command to the wheel`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        player.features.wheel.gems.revealedGems.add(PlayerWheelGem())

        game.playerWheelGemAction(player.id, ToggleGemLockCommand(0))

        assertThat(player.features.wheel.gems.revealedGems.first().locked).isTrue()
    }

    @Test
    fun `playerWheelGemAction ignores a destroy command for an unknown gem`() {
        val game = newGame()
        val player = game.player(1, "Hero")

        game.playerWheelGemAction(player.id, DestroyGemCommand(7))

        assertThat(player.features.wheel.gems.revealedGems).isEmpty()
    }

    @Test
    fun `playerWheelGemAction dispatches the improve grade command to the wheel`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val command = ImproveGemGradeCommand(WheelFragmentType.Lesser, 0)
        game.wheelPlayer = { _ ->
            object : org.opentibia.canary.systems.wheel.WheelPlayer {
                override fun getWheelGemId(quality: org.opentibia.canary.systems.wheel.WheelGemQuality): Int = 0
                override fun hasItemCountById(itemId: Int, count: Int, checkStash: Boolean): Boolean = true
                override fun removeItemCountById(itemId: Int, count: Int, removeFromStash: Boolean): Boolean = true
                override fun removeMoney(amount: Long): Boolean = true
                override fun internalPlayerAddItem(itemId: Int, count: Int): Boolean = true
            }
        }

        game.playerWheelGemAction(player.id, command)

        assertThat(player.features.wheel.gems.basicGrades[0]).isEqualTo(1)
    }

    @Test
    fun `playerWheelGemAction updates the exhausted state after the command`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        player.features.wheel.gems.revealedGems.add(PlayerWheelGem())
        val calls = mutableListOf<String>()
        game.updateUIExhausted = { calls.add("exhausted") }

        game.playerWheelGemAction(player.id, ToggleGemLockCommand(0))

        assertThat(calls).containsExactly("exhausted")
    }
}
