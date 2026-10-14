package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.pvp.PvpMode_t

/**
 * Player combat/targeting state helpers that only read/write state already
 * owned by the port's [Player] (src/creatures/players/player.cpp:6196-6232).
 *
 * They are extension functions because the port's `Player` lives in
 * `creatures/Creature.kt`, which this wave does not own; every accessed field
 * (`chaseMode`, `fightMode`, `secureMode`, `cancelNextWalk`, `pvpPlayer`) and
 * member (`getAttackedCreature`, `getFollowCreature`, `setFollowCreature`,
 * `sendSkills`) already exists there. `Player::sendStats()` is the only missing
 * seam and is injected at the call site per AGENTS.md 1.1.
 */

/**
 * `Player::setChaseMode` (player.cpp:6196-6213): flipping chase on starts
 * following the current target when nothing is followed yet; flipping it off
 * cancels the follow and latches `cancelNextWalk`.
 */
fun Player.setChaseMode(mode: Boolean) {
    val prevChaseMode = chaseMode == ChaseMode.Follow
    chaseMode = if (mode) ChaseMode.Follow else ChaseMode.StandStill

    val attackedCreature = getAttackedCreature()
    val followCreature = getFollowCreature()

    if (prevChaseMode != mode) {
        if (mode) {
            if (followCreature == null && attackedCreature != null) {
                // chase opponent
                setFollowCreature(attackedCreature)
            }
        } else if (attackedCreature != null) {
            setFollowCreature(null)
            cancelNextWalk = true
        }
    }
}

/**
 * `Player::setFightMode` (player.cpp:6215-6220). `Player::sendStats()` is the
 * injected protocol seam; `Player::sendSkills()` uses the player's own seam.
 */
fun Player.setFightMode(mode: FightMode, sendStats: () -> Unit = { this.sendStats() }) {
    fightMode = mode

    sendStats()
    sendSkills()
}

/** `Player::setSecureMode` (player.cpp:6222-6224). */
fun Player.setSecureMode(mode: Boolean) {
    secureMode = mode
}

/** `Player::setPvpMode` (player.cpp:6226-6228). */
fun Player.setPvpMode(mode: PvpMode_t) {
    pvpPlayer.setMode(mode)
}

/** `Player::getPvpMode` (player.cpp:6230-6232). */
fun Player.getPvpMode(): PvpMode_t = pvpPlayer.getMode()

/**
 * `Player::lastHitIsPlayer` (player.cpp:6747-6758, static): the killing blow
 * came from a player, or from a summon whose master is a player.
 */
fun lastHitIsPlayer(lastHitCreature: Creature?): Boolean {
    if (lastHitCreature == null) {
        return false
    }

    if (lastHitCreature is Player) {
        return true
    }

    val lastHitMaster = lastHitCreature.getMaster() ?: return false
    return lastHitMaster is Player
}
