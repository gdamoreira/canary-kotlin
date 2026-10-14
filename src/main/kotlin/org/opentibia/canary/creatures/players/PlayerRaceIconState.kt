package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.CreatureIcon
import org.opentibia.canary.creatures.CreatureIconModifications_t

/**
 * Self-contained mirror of the Player race-icon overlay state
 * (src/creatures/players/player.cpp:8915-8971).
 *
 * Canary keeps `Player::m_raceIconOverlays`
 * (`std::map<uint16_t, std::set<CreatureIconModifications_t>>`, player.hpp:1930)
 * directly on `Player`; the class owns it and receives `Player::client` state
 * and the spectator lookup as constructor seams (AGENTS.md 1.1).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe.
 */
class PlayerRaceIconState(
    /** `Player::client != nullptr` (player.cpp:8964). */
    private val hasClient: () -> Boolean = { false },
    /** `Player::client->oldProtocol` (player.cpp:8964). */
    private val isOldProtocol: () -> Boolean = { false },
    /**
     * `Spectators().find<Creature>(...)` viewport lookup (player.cpp:8967);
     * the map dependency is a seam per AGENTS.md 1.1.
     */
    private val visibleCreatures: () -> List<Creature> = { emptyList() },
    /** `Player::sendCreatureIcon(creature)` (player.cpp:8968). */
    private val sendCreatureIcon: (Creature) -> Unit = {},
) {

    /** `Player::m_raceIconOverlays` (player.hpp:1930). */
    private val raceIconOverlays = linkedMapOf<Int, MutableSet<CreatureIconModifications_t>>()

    /**
     * `Player::setRaceIconOverlay(raceId, icon, enabled)` (player.cpp:8915-8932):
     * a zero race id or the `None` icon is rejected; enabling reports whether
     * the icon was newly inserted, disabling whether it existed.
     */
    fun setRaceIconOverlay(raceId: Int, icon: CreatureIconModifications_t, enabled: Boolean): Boolean {
        if (raceId == 0 || icon == CreatureIconModifications_t.None) {
            return false
        }

        if (enabled) {
            return raceIconOverlays.getOrPut(raceId) { linkedSetOf() }.add(icon)
        }

        val overlays = raceIconOverlays[raceId] ?: return false
        if (!overlays.remove(icon)) {
            return false
        }
        if (overlays.isEmpty()) {
            raceIconOverlays.remove(raceId)
        }
        return true
    }

    /**
     * `Player::clearRaceIconOverlays(icon)` (player.cpp:8934-8947): every race
     * loses the icon and empty races are pruned; reports whether anything
     * changed.
     */
    fun clearRaceIconOverlays(icon: CreatureIconModifications_t): Boolean {
        if (icon == CreatureIconModifications_t.None) {
            return false
        }

        var changed = false
        for (overlays in raceIconOverlays.values) {
            changed = overlays.remove(icon) || changed
        }
        raceIconOverlays.entries.removeIf { it.value.isEmpty() }
        return changed
    }

    /** `Player::getRaceIconOverlays(raceId)` (player.cpp:8949-8961). */
    fun getRaceIconOverlays(raceId: Int): List<CreatureIcon> =
        raceIconOverlays[raceId]?.map { CreatureIcon(it) } ?: emptyList()

    /**
     * `Player::refreshVisibleCreatureIcons()` (player.cpp:8963-8971): re-sends
     * the icon overlay for every visible creature on a modern client.
     */
    fun refreshVisibleCreatureIcons() {
        if (!hasClient() || isOldProtocol()) {
            return
        }

        for (creature in visibleCreatures()) {
            sendCreatureIcon(creature)
        }
    }
}
