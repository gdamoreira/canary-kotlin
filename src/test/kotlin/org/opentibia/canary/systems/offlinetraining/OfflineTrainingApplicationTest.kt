package org.opentibia.canary.systems.offlinetraining

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.protocol.offline.StartOfflineTrainingCommand

/**
 * Transcript tests for applying a parsed offline-training start command to the
 * offline-training engine core.
 *
 * Source of truth: ../.projects/canary
 *  - src/game/game.cpp:11153 `Game::playerAnswerModalWindow` (offline training
 *    branch): accepts SKILL_FIST/SKILL_SWORD/SKILL_AXE/SKILL_CLUB/
 *    SKILL_DISTANCE/SKILL_MAGLEVEL and calls `setOfflineTrainingSkill(choice)`.
 *  - src/creatures/players/player.cpp:8295 `Player::setOfflineTrainingSkill`.
 */
class OfflineTrainingApplicationTest {

    private fun newTraining() = OfflineTraining(NoopVocation)

    @Test
    fun `trainable skill action selects the offline training skill`() {
        val training = newTraining()

        val result = OfflineTrainingApplication.apply(
            training,
            StartOfflineTrainingCommand(action = Skill.SKILL_SWORD.value),
        )

        assertThat(result.applied).isTrue()
        assertThat(result.state).isSameAs(training)
        assertThat(training.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_SWORD)
    }

    @Test
    fun `every canary trainable skill is accepted`() {
        val trainable = listOf(
            Skill.SKILL_FIST,
            Skill.SKILL_SWORD,
            Skill.SKILL_AXE,
            Skill.SKILL_CLUB,
            Skill.SKILL_DISTANCE,
            Skill.SKILL_MAGLEVEL,
        )

        for (skill in trainable) {
            val training = newTraining()
            val result = OfflineTrainingApplication.apply(
                training,
                StartOfflineTrainingCommand(action = skill.value),
            )

            assertThat(result.applied).isTrue()
            assertThat(training.getOfflineTrainingSkill()).isEqualTo(skill)
        }
    }

    @Test
    fun `non trainable skills are rejected and leave the state untouched`() {
        val rejected = listOf(Skill.SKILL_SHIELD, Skill.SKILL_FISHING, Skill.SKILL_CRITICAL_HIT_CHANCE)

        for (skill in rejected) {
            val training = newTraining()
            val result = OfflineTrainingApplication.apply(
                training,
                StartOfflineTrainingCommand(action = skill.value),
            )

            assertThat(result.applied).isFalse()
            assertThat(training.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_NONE)
        }
    }

    @Test
    fun `unknown action byte is rejected`() {
        val training = newTraining()

        val result = OfflineTrainingApplication.apply(
            training,
            StartOfflineTrainingCommand(action = 255),
        )

        assertThat(result.applied).isFalse()
        assertThat(training.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_NONE)
    }

    @Test
    fun `rejected action preserves an already selected skill`() {
        val training = newTraining()
        training.setOfflineTrainingSkill(Skill.SKILL_AXE)

        val result = OfflineTrainingApplication.apply(
            training,
            StartOfflineTrainingCommand(action = Skill.SKILL_SHIELD.value),
        )

        assertThat(result.applied).isFalse()
        assertThat(training.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_AXE)
    }

    /**
     * Test double for `Vocation::getReqSkillTries` / `Vocation::getReqMana`
     * (src/creatures/players/vocations/vocation.cpp:265,295). Selecting the
     * offline-training skill does not consult the vocation, so the values are
     * irrelevant to these tests.
     */
    private object NoopVocation : VocationRequirements {
        override fun getReqSkillTries(skill: Skill, level: Int): Long = 0
        override fun getReqMana(magLevel: Int): Long = 0
    }
}
