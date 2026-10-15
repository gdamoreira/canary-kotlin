package org.opentibia.canary.systems.wheel

import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatPlayer

/**
 * Production host adapter that binds the `CombatPlayer` beam-mastery seam to a
 * live [PlayerWheel].
 *
 * In Canary `Combat::CombatFunc` reaches the caster's wheel through
 * `casterPlayer->wheel().getBeamAffectedTotal(tmpDamage)` (combat.cpp:1579-1580)
 * and `casterPlayer->wheel().updateBeamMasteryDamage(tmpDamage,
 * beamAffectedTotal, beamAffectedCurrent)` (combat.cpp:1614-1617). The port
 * replaces the raw `Player` with the [CombatPlayer] seam, so the wheel surface
 * is layered over whatever player view the host already built: every other
 * member delegates to [delegate] verbatim, and only the two beam members
 * transcribe the `PlayerWheel` calls.
 *
 * [wheelProvider] resolves the owner's [`Player::wheel()`, player.cpp:
 * 12749-12751] per call, so a player without a wheel (or a replaced owner)
 * keeps the interface's inert defaults (AGENTS.md deferred-callback lifetime
 * safety: no references are retained between calls).
 *
 * CALL-SITE NEED: `LuaCombatWorld.playerOf` (lua/bindings/LuaCombatWorld.kt)
 * must wrap its `LuaCombatPlayer` with this adapter, bound to
 * `{ player.features.wheel }`, so the beam seam is live for the Lua combat
 * entry points (the lua package is owned outside this wave).
 */
class WheelBeamCombatPlayer(
    private val delegate: CombatPlayer,
    private val wheelProvider: () -> PlayerWheel?,
) : CombatPlayer by delegate {

    /** `PlayerWheel::getBeamAffectedTotal(tmpDamage)` (`combat.cpp:1579`). */
    override fun getBeamAffectedTotal(damage: CombatDamage): Int =
        wheelProvider()?.getBeamAffectedTotal(damage) ?: 0

    /** `PlayerWheel::updateBeamMasteryDamage(...)` (`combat.cpp:1614-1617`). */
    override fun updateBeamMasteryDamage(
        damage: CombatDamage,
        beamAffectedTotal: Int,
        beamAffectedCurrent: Int,
    ): Pair<Int, Int> =
        wheelProvider()?.updateBeamMasteryDamage(damage, beamAffectedTotal, beamAffectedCurrent)
            ?: (beamAffectedTotal to beamAffectedCurrent)
}
