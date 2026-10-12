package org.opentibia.canary.systems.offlinetraining

import org.opentibia.canary.network.protocol.offline.StartOfflineTrainingCommand

/**
 * Result of applying a [StartOfflineTrainingCommand] to an [OfflineTraining]
 * engine core.
 *
 *  - [state] is the (mutated) training state the command was applied to.
 *  - [applied] mirrors the Canary acceptance guard: `true` when the command
 *    carried one of the trainable skills and `setOfflineTrainingSkill` ran.
 */
data class OfflineTrainingApplyResult(
    val state: OfflineTraining,
    val applied: Boolean,
)

/**
 * Application service that applies a decoded [StartOfflineTrainingCommand] to
 * the offline-training engine core.
 *
 * This is the C++-parity decision that Canary performs when the offline-training
 * selection reaches
 * `Game::playerAnswerModalWindow` (src/game/game.cpp:11153):
 *
 *     if (choice == SKILL_FIST || choice == SKILL_SWORD || choice == SKILL_AXE
 *         || choice == SKILL_CLUB || choice == SKILL_DISTANCE
 *         || choice == SKILL_MAGLEVEL) {
 *         player->setOfflineTrainingSkill(static_cast<int8_t>(choice));
 *     }
 *
 * Only those six skills may start offline training; any other byte is rejected
 * and leaves the state (including a previously selected skill) untouched. The
 * bed/sleep and logout-request side effects stay in the Player/Lua layers and
 * are intentionally outside this service (AGENTS.md 1.4).
 */
object OfflineTrainingApplication {

    /**
     * The exact trainable-skill whitelist from `Game::playerAnswerModalWindow`
     * (src/game/game.cpp:11160).
     */
    private val TRAINABLE_SKILLS: Set<Skill> = setOf(
        Skill.SKILL_FIST,
        Skill.SKILL_SWORD,
        Skill.SKILL_AXE,
        Skill.SKILL_CLUB,
        Skill.SKILL_DISTANCE,
        Skill.SKILL_MAGLEVEL,
    )

    /**
     * Applies [command] to [state].
     *
     * When the command's action byte maps to a trainable skill, that skill is
     * stored via [OfflineTraining.setOfflineTrainingSkill] and the returned
     * result reports `applied = true`. Unknown or non-trainable actions are
     * ignored, mirroring Canary's guard.
     */
    fun apply(state: OfflineTraining, command: StartOfflineTrainingCommand): OfflineTrainingApplyResult {
        val skill = Skill.entries.firstOrNull { it.value == command.action }
        if (skill == null || skill !in TRAINABLE_SKILLS) {
            return OfflineTrainingApplyResult(state = state, applied = false)
        }

        state.setOfflineTrainingSkill(skill)
        return OfflineTrainingApplyResult(state = state, applied = true)
    }
}
