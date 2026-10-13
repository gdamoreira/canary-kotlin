package org.opentibia.canary.systems.mount

import org.opentibia.canary.creatures.players.components.PlayerStorage

/**
 * Injected view of `Player::getStorageValue`/`Player::addStorageValue`
 * (`src/creatures/players/player.cpp`; the storage component lives in
 * `src/creatures/players/components/player_storage.hpp`).
 *
 * Canary reaches the player's `PlayerStorage m_playerStorage`; per AGENTS.md 1.1
 * the storage is constructor-injected here so the mount state can be persisted
 * through the real `player_storage` table.
 */
interface MountStorage {
    /** `Player::getStorageValue(key)`; `-1` when the key is absent. */
    fun getStorageValue(key: Int): Int

    /** `Player::addStorageValue(key, value)`. */
    fun addStorageValue(key: Int, value: Int)

    /** `PlayerStorage::remove(key)` (only used by the owned-mount persistence seam). */
    fun removeStorageValue(key: Int)
}

/**
 * Default in-memory backing used when no player storage is wired.
 *
 * PARITY GAP: this holds the same key/value shape as `PlayerStorage` but does
 * not survive a restart; production wiring passes [PlayerStorageMountStorage].
 */
class InMemoryMountStorage : MountStorage {
    private val values = mutableMapOf<Int, Int>()

    override fun getStorageValue(key: Int): Int = values[key] ?: -1

    override fun addStorageValue(key: Int, value: Int) {
        values[key] = value
    }

    override fun removeStorageValue(key: Int) {
        values.remove(key)
    }
}

/** [MountStorage] adapter over the ported `PlayerStorage` component. */
class PlayerStorageMountStorage(
    private val storage: PlayerStorage,
) : MountStorage {
    override fun getStorageValue(key: Int): Int = storage.get(key)

    override fun addStorageValue(key: Int, value: Int) {
        storage.add(key, value)
    }

    override fun removeStorageValue(key: Int) {
        storage.remove(key)
    }
}

/**
 * Injected view of the `KV` player store used by `Player::getLastMount` and
 * `Player::toggleMount` (`kv()->get/set("last-mount")`,
 * `src/creatures/players/player.cpp:7923-8036`).
 *
 * The full `KV` component can hold typed values and scopes; the mount runtime
 * only uses the string-keyed integer subset.
 */
interface MountKeyValueStore {
    /** `KV::get(key)`; `null` when the key is absent. */
    fun get(key: String): Int?

    /** `KV::set(key, value)`. */
    fun set(key: String, value: Int)
}

/** In-memory [MountKeyValueStore] used when no KV store is wired. */
class InMemoryMountKeyValueStore : MountKeyValueStore {
    private val values = mutableMapOf<String, Int>()

    override fun get(key: String): Int? = values[key]

    override fun set(key: String, value: Int) {
        values[key] = value
    }
}
