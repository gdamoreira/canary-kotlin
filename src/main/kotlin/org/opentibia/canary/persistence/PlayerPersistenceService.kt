package org.opentibia.canary.persistence

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFeatures
import org.opentibia.canary.database.FeatureStateRepository

/**
 * Coordinates the player persistence flow for the advanced-feature state.
 *
 * Canary loads the per-player systems from the character row while building the
 * `Player` in `IOLoginData::loadPlayer`
 * (`src/io/functions/iologindata_load_player.cpp`) and flushes them in
 * `IOLoginData::savePlayer` -> `IOLoginDataSave::savePlayerSystems`
 * (`src/io/functions/iologindata_save_player.cpp:861`). This service is the
 * Kotlin seam for both directions: the login path calls [load] and the
 * save-all/shutdown path calls [save]. Field conversion stays in
 * [PlayerFeatureStateLoader]/[PlayerFeatureStateMapper]; this class only wires
 * the repository to the live [PlayerFeatures] holder.
 */
class PlayerPersistenceService(
    featureRepository: FeatureStateRepository,
    private val loader: PlayerFeatureStateLoader = PlayerFeatureStateLoader(featureRepository),
) {

    /** Applies persisted feature state to [player] on login. Returns whether any existed. */
    fun load(player: Player): Boolean = loader.loadInto(player.id, player.features)

    /** Applies persisted feature state for [playerId] onto [features]. */
    fun load(playerId: Long, features: PlayerFeatures): Boolean = loader.loadInto(playerId, features)

    /** Persists the current feature state of [player]. */
    fun save(player: Player) {
        loader.save(player.id, player.features)
    }

    /** Persists the current state of [features] for [playerId]. */
    fun save(playerId: Long, features: PlayerFeatures) {
        loader.save(playerId, features)
    }
}
