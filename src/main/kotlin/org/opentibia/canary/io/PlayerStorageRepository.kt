package org.opentibia.canary.io

import org.opentibia.canary.creatures.players.components.PlayerStorageRow

/**
 * Ports `IPlayerStorageRepository` (`src/io/player_storage_repository.hpp:20-46`):
 * the persistence contract consumed by `IOLoginDataLoad::loadPlayerStorageMap`
 * (src/io/functions/iologindata_load_player.cpp:759-767) and
 * `IOLoginDataSave::savePlayerStorage`
 * (src/io/functions/iologindata_save_player.cpp:837-857).
 *
 * PARITY NOTES:
 *  - The C++ `uint32_t playerId` is [Long] because `players.id` is a `BIGINT`
 *    in this schema; keys and values stay [Int], matching [PlayerStorageRow]
 *    and the component port's narrowing of Canary's `uint32_t` keys.
 *  - The C++ `g_playerStorageRepository()` accessor is replaced by constructor
 *    injection (AGENTS.md 1.1); the Db implementation is
 *    [DbPlayerStorageRepository].
 */
interface PlayerStorageRepository {

    /**
     * Loads all storage rows for [playerId].
     *
     * `IPlayerStorageRepository::load` (player_storage_repository.hpp:29):
     * empty when the player has no rows.
     */
    fun load(playerId: Long): List<PlayerStorageRow>

    /**
     * Deletes a batch of storage [keys] for [playerId].
     *
     * `IPlayerStorageRepository::deleteKeys` (player_storage_repository.hpp:37):
     * an empty batch succeeds without touching the database.
     */
    fun deleteKeys(playerId: Long, keys: List<Int>): Boolean

    /**
     * Inserts or updates a batch of key/value pairs for [playerId].
     *
     * `IPlayerStorageRepository::upsert` (player_storage_repository.hpp:45):
     * an empty batch succeeds without touching the database, existing keys are
     * overwritten and keys missing from [kv] are left untouched.
     */
    fun upsert(playerId: Long, kv: Map<Int, Int>): Boolean
}
