package org.opentibia.canary.systems.wheel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.protocol.wheel.DestroyGemCommand
import org.opentibia.canary.network.protocol.wheel.ImproveGemGradeCommand
import org.opentibia.canary.network.protocol.wheel.RevealGemCommand
import org.opentibia.canary.network.protocol.wheel.SaveWheelCommand
import org.opentibia.canary.network.protocol.wheel.SwitchGemDomainCommand
import org.opentibia.canary.network.protocol.wheel.ToggleGemLockCommand
import org.opentibia.canary.network.protocol.wheel.WheelSlotPoints

/**
 * Transcribes the validation results of Canary's
 * `PlayerWheel::saveSlotPointsOnPressSaveButton` / `checkSavePointsBySlotType`
 * (src/creatures/players/components/wheel/player_wheel.cpp:1611-1724) and the
 * gem action handlers (`destroyGem`, `revealGem`, `switchGemDomain`,
 * `toggleGemLock`, `improveGemGrade`).
 */
class WheelApplicationTest {

    private fun cyclingRandom(): WheelRandom {
        var seed = 0
        return WheelRandom { min, max -> min + (seed++ % (max - min + 1)) }
    }

    private val application = WheelApplication(random = cyclingRandom())

    private fun wheel(level: Int = 250, vocationCipId: Int = IOWheel.VOCATION_KNIGHT_CIP) =
        PlayerWheel(vocationCipId, level = level)

    private fun save(vararg points: Pair<WheelSlots, Int>) =
        SaveWheelCommand(points.map { WheelSlotPoints(it.first, it.second) }, emptyList())

    @Test
    fun `valid save applies points within the budget`() {
        val state = wheel(level = 100)
        val result = application.applySave(state, save(WheelSlots.SLOT_GREEN_50 to 50))

        assertThat(result).isInstanceOf(WheelSaveResult.Success::class.java)
        result as WheelSaveResult.Success
        assertThat(result.applied).containsEntry(WheelSlots.SLOT_GREEN_50, 50)
        assertThat(result.unsatisfied).isEmpty()
        assertThat(state.getPointsBySlotType(WheelSlots.SLOT_GREEN_50)).isEqualTo(50)
        assertThat(state.getUnusedPoints()).isZero()
    }

    @Test
    fun `save above the max points per slot is rejected without applying anything`() {
        val state = wheel(level = 100)
        val result = application.applySave(state, save(WheelSlots.SLOT_GREEN_50 to 51))

        assertThat(result).isEqualTo(
            WheelSaveResult.Failure(
                WheelSaveRejection.MAX_POINTS_PER_SLOT_EXCEEDED,
                WheelSlots.SLOT_GREEN_50,
            ),
        )
        assertThat(state.getPointsBySlotType(WheelSlots.SLOT_GREEN_50)).isZero()
    }

    @Test
    fun `save above the point budget leaves the slot unapplied`() {
        val state = wheel(level = 100)
        val result = application.applySave(
            state,
            save(
                WheelSlots.SLOT_GREEN_50 to 50,
                WheelSlots.SLOT_GREEN_TOP_75 to 75,
            ),
        )

        assertThat(result).isInstanceOf(WheelSaveResult.Success::class.java)
        result as WheelSaveResult.Success
        assertThat(result.applied).containsEntry(WheelSlots.SLOT_GREEN_50, 50)
        assertThat(result.unsatisfied).containsExactly(WheelSlots.SLOT_GREEN_TOP_75)
        assertThat(state.getPointsBySlotType(WheelSlots.SLOT_GREEN_TOP_75)).isZero()
    }

    @Test
    fun `destroy removes an unlocked gem`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Green))

        val result = application.applyGemAction(state, DestroyGemCommand(0))

        assertThat(result).isInstanceOf(WheelGemActionResult.Applied::class.java)
        assertThat(result.action).isEqualTo(WheelGemAction.Destroy)
        assertThat(state.gems.revealedGems).isEmpty()
    }

    @Test
    fun `destroy of a locked gem is rejected`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Green, locked = true))

        val result = application.applyGemAction(state, DestroyGemCommand(0))

        assertThat(result).isEqualTo(WheelGemActionResult.Rejected(WheelGemAction.Destroy, WheelGemActionRejection.GEM_LOCKED))
        assertThat(state.gems.revealedGems).hasSize(1)
    }

    @Test
    fun `destroy of a missing gem is rejected`() {
        val state = wheel()
        val result = application.applyGemAction(state, DestroyGemCommand(3))
        assertThat(result).isEqualTo(WheelGemActionResult.Rejected(WheelGemAction.Destroy, WheelGemActionRejection.NO_SUCH_GEM))
    }

    @Test
    fun `toggle lock flips the gem lock`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Green))

        application.applyGemAction(state, ToggleGemLockCommand(0))
        assertThat(state.gems.revealedGems[0].locked).isTrue()

        application.applyGemAction(state, ToggleGemLockCommand(0))
        assertThat(state.gems.revealedGems[0].locked).isFalse()
    }

    @Test
    fun `switch domain rotates the affinity`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Green))

        application.applyGemAction(state, SwitchGemDomainCommand(0))

        assertThat(state.gems.revealedGems[0].affinity).isEqualTo(WheelGemAffinity.Red)
    }

    @Test
    fun `switch domain of a locked gem is rejected`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Red, locked = true))

        val result = application.applyGemAction(state, SwitchGemDomainCommand(0))

        assertThat(result).isEqualTo(WheelGemActionResult.Rejected(WheelGemAction.SwitchDomain, WheelGemActionRejection.GEM_LOCKED))
        assertThat(state.gems.revealedGems[0].affinity).isEqualTo(WheelGemAffinity.Red)
    }

    @Test
    fun `improve grade raises the basic grade`() {
        val state = wheel()

        val result = application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Lesser, 0))

        assertThat(result.action).isEqualTo(WheelGemAction.ImproveGrade)
        assertThat(state.gems.basicGrades[0]).isEqualTo(1)
    }

    @Test
    fun `improve grade beyond the maximum is rejected`() {
        val state = wheel()
        state.gems.basicGrades[0] = 3

        val result = application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Lesser, 0))

        assertThat(result).isEqualTo(
            WheelGemActionResult.Rejected(WheelGemAction.ImproveGrade, WheelGemActionRejection.MAX_GRADE_REACHED),
        )
        assertThat(state.gems.basicGrades[0]).isEqualTo(3)
    }

    @Test
    fun `reveal adds a gem of the requested quality`() {
        val state = wheel()

        val result = application.applyGemAction(state, RevealGemCommand(WheelGemQuality.Regular))

        assertThat(result.action).isEqualTo(WheelGemAction.Reveal)
        assertThat(state.gems.revealedGems).hasSize(1)
        assertThat(state.gems.revealedGems[0].quality).isEqualTo(WheelGemQuality.Regular)
        assertThat(state.gems.revealedGems[0].basicModifier2).isNotNull()
    }
}
