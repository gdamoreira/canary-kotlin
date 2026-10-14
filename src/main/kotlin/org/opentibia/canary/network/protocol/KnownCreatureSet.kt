package org.opentibia.canary.network.protocol

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player

/**
 * The per-session `ProtocolGame::knownCreatureSet`
 * (`src/server/network/protocol/protocolgame.hpp:620`) together with the
 * `ProtocolGame::checkCreatureAsKnown` cache rules
 * (`src/server/network/protocol/protocolgame.cpp:2334-2372`).
 *
 * `checkCreatureAsKnown` inserts the id and reports `known = false` on the
 * first sighting; a repeat reports `known = true` without touching the set.
 * Once the set exceeds 1300 entries the first known id that is not protected
 * is evicted and returned as `removedKnown`: a player is protected while it
 * shares [Player.getParty] with the observer, and any creature is protected
 * while it is visible. When every candidate is protected the "bad situation"
 * fallback removes the first entry (skipping the queried id) exactly like the
 * C++ `begin()`/`++it` branch.
 *
 * C++ iterates a `std::unordered_set`, whose order is unspecified; the port
 * keeps insertion order so the eviction choice is deterministic.
 *
 * [creatureById] is `g_game().getCreatureByID` and [canSee] is
 * `ProtocolGame::canSee(const std::shared_ptr<Creature>&)`, both injected per
 * AGENTS.md 1.1. The set is session state owned by the sink and mutated only
 * from the world-commit lane.
 */
class KnownCreatureSet(
    private val player: Player,
    private val creatureById: (Long) -> Creature? = { null },
    private val canSee: (Creature?) -> Boolean = { false },
) {

    private val knownCreatureSet = LinkedHashSet<Long>()

    /** `knownCreatureSet.contains(id)` (`protocolgame.cpp:4610`, 8353, ...). */
    fun contains(id: Long): Boolean = knownCreatureSet.contains(id)

    /**
     * `ProtocolGame::checkCreatureAsKnown(id, known, removedKnown)`
     * (protocolgame.cpp:2334-2372).
     */
    fun checkCreatureAsKnown(id: Long): KnownCreatureResult {
        if (!knownCreatureSet.add(id)) {
            return KnownCreatureResult(known = true, removedKnown = 0)
        }

        if (knownCreatureSet.size <= MAX_KNOWN_CREATURES) {
            return KnownCreatureResult(known = false, removedKnown = 0)
        }

        // Look for a creature to remove.
        val candidates = knownCreatureSet.iterator()
        while (candidates.hasNext()) {
            val knownId = candidates.next()
            if (knownId == id) {
                continue
            }
            // We need to protect party players from removing.
            val creature = creatureById(knownId)
            val checkPlayer = creature as? Player
            if (checkPlayer != null) {
                if (player.getParty() !== checkPlayer.getParty() && !canSee(creature)) {
                    candidates.remove()
                    return KnownCreatureResult(known = false, removedKnown = knownId)
                }
            } else if (!canSee(creature)) {
                candidates.remove()
                return KnownCreatureResult(known = false, removedKnown = knownId)
            }
        }

        // Bad situation. Let's just remove anyone.
        val fallback = knownCreatureSet.iterator()
        var removedKnown = fallback.next()
        if (removedKnown == id) {
            removedKnown = fallback.next()
        }
        knownCreatureSet.remove(removedKnown)
        return KnownCreatureResult(known = false, removedKnown = removedKnown)
    }

    private companion object {
        /** `knownCreatureSet.size() > 1300` cache bound (protocolgame.cpp:2341). */
        const val MAX_KNOWN_CREATURES = 1300
    }
}

/**
 * The `known`/`removedKnown` out parameters of
 * `ProtocolGame::checkCreatureAsKnown` (protocolgame.cpp:2334-2372).
 */
data class KnownCreatureResult(
    val known: Boolean,
    val removedKnown: Long,
)
