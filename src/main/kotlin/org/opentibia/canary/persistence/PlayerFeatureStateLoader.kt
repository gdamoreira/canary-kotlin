package org.opentibia.canary.persistence

import org.opentibia.canary.creatures.PlayerFeatures
import org.opentibia.canary.database.FeatureStateRepository
import org.opentibia.canary.database.PlayerFeatureState

/**
 * Connects the advanced-feature persistence repository to the in-memory
 * [PlayerFeatures] holder.
 *
 * Canary loads these values while building the `Player` in
 * `IOLoginData::loadPlayer` (`src/io/functions/iologindata_load_player.cpp`) and
 * flushes them in `IOLoginData::savePlayer`
 * (`src/io/functions/iologindata_save_player.cpp`). This service is the Kotlin
 * seam for that load/save flow; field conversion lives in
 * [PlayerFeatureStateMapper].
 */
class PlayerFeatureStateLoader(private val repository: FeatureStateRepository) {

    /** Reads the persisted state for [playerId], or null when none exists. */
    fun load(playerId: Long): PlayerFeatureState? = repository.load(playerId)

    /**
     * Applies the persisted state for [playerId] onto [features]. Returns false
     * (leaving [features] untouched) when the player has no persisted state.
     */
    fun loadInto(playerId: Long, features: PlayerFeatures): Boolean {
        val state = repository.load(playerId) ?: return false
        PlayerFeatureStateMapper.applyTo(state, features)
        return true
    }

    /** Persists the current state of [features]. */
    fun save(features: PlayerFeatures) {
        repository.save(PlayerFeatureStateMapper.toFeatureState(features.playerId, features))
    }
}
