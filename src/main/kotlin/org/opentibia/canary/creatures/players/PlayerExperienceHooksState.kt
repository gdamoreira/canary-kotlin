package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Party

/**
 * `Player::onGainExperience` (src/creatures/players/player.cpp:6709-6722): a
 * player blocked from experience gains nothing; a monster killed under an
 * active, enabled shared-experience party credits the party instead; otherwise
 * the `Creature::` base hook and [Player::gainExperience] run.
 *
 * `Player::onGainSharedExperience` (player.cpp:6724-6726) and
 * `Player::gainExperience` (player.cpp:6701-6707) already live on the host
 * (`creatures/Creature.kt:2969`, [PlayerExperienceState.gainExperience]).
 * `Creature::onGainExperience` is not ported and is a call-site seam, as are
 * the party predicates and the share sink per AGENTS.md 1.1.
 */
fun Player.onGainExperience(
    /** `gainExp` (player.cpp:6709). */
    gainExp: Long,
    /** `target` (player.cpp:6709). */
    target: Creature? = null,
    /** `m_party->isSharedExperienceActive()` (player.cpp:6713). */
    isSharedExperienceActive: (Party) -> Boolean = { it.isSharedExperienceActive() },
    /** `m_party->isSharedExperienceEnabled()` (player.cpp:6713). */
    isSharedExperienceEnabled: (Party) -> Boolean = { it.isSharedExperienceEnabled() },
    /** `m_party->shareExperience(gainExp, target)` (player.cpp:6714). */
    shareExperience: (Party, Long, Creature) -> Unit = { party, exp, creature -> party.shareExperience(exp, creature) },
    /** `Creature::onGainExperience(gainExp, target)` (creature.cpp). */
    creatureOnGainExperience: (Long, Creature?) -> Unit = { _, _ -> },
    /** `Player::gainExperience(gainExp, target)` (player.cpp:6701-6707). */
    gainExperience: (Long, Creature?) -> Unit = { _, _ -> },
) {
    if (hasFlag(PlayerFlags_t.NotGainExperience)) {
        return
    }

    val party = getParty()
    if (target != null && target.getPlayer() == null && party != null &&
        isSharedExperienceActive(party) && isSharedExperienceEnabled(party)
    ) {
        shareExperience(party, gainExp, target)
        return
    }

    creatureOnGainExperience(gainExp, target)
    gainExperience(gainExp, target)
}
