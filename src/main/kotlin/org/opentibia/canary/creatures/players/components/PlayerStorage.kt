package org.opentibia.canary.creatures.players.components

import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.systems.familiar.FamiliarEntry

/**
 * Single storage entry, mirroring `struct PlayerStorageRow`
 * (`player_storage.hpp:27-30`).
 *
 * PARITY NOTE: the C++ `uint32_t` key is narrowed to [Int], matching the
 * existing storage-key convention of the port (`PlayerMountState`,
 * `PlayerFamiliars`); Canary's reserved range tops out at 20,000,000 and the
 * game never addresses keys above `Int.MAX_VALUE`.
 */
data class PlayerStorageRow(
    val key: Int,
    val value: Int,
)

/**
 * Injected `Player` view for the storage component. Every member transcribes
 * the exact `Player` symbol `PlayerStorage` reaches through `m_player`
 * (player_storage.hpp:159), with the C++ line cited.
 */
interface StoragePlayer {
    /** `Player::getName()` — the unknown-reserved-key warning (player_storage.cpp:71). */
    fun getName(): String

    /**
     * `Player::outfits` (player.hpp:1686). The storage outfits branch appends
     * directly with `emplace_back` (player_storage.cpp:59-62), so the seam
     * exposes the mutable backing list rather than `Player::addOutfit`.
     */
    fun getOutfits(): MutableList<OutfitEntry>

    /**
     * `Player::familiars` (player.hpp:1687). The storage familiars branch
     * appends directly with `emplace_back` (player_storage.cpp:66), so the seam
     * exposes the mutable backing list rather than `PlayerFamiliars::addFamiliar`.
     */
    fun getFamiliars(): MutableList<FamiliarEntry>
}

/**
 * Mirror of Canary `PlayerStorage`
 * (`src/creatures/players/components/player_storage.hpp:35-185`,
 * player_storage.cpp:27-161).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). The
 * `Player &m_player` reference becomes [player] and the global services are
 * constructor-injected seams (AGENTS.md 1.1): [currentFrameTime] replaces
 * `g_dispatcher().getDispatcherCycle()`, [eventOnStorageUpdate] replaces
 * `g_events().eventOnStorageUpdate`, [playerOnStorageUpdateCallback] replaces
 * `g_callbacks().executeCallback(EventCallback_t::playerOnStorageUpdate, ...)`
 * and [logWarn] replaces `g_logger().warn`. Both callbacks are seams instead
 * of a port of the Lua event engine (AGENTS.md 1.4).
 *
 * PARITY NOTES:
 *  - The reserved ranges and [isStorageKeyInRange] transcribe
 *    `src/utils/const.hpp:42-77`, including the `(key - start) <= size`
 *    off-by-one that keeps `start + size` inside the range.
 *  - [storageMap], [modifiedKeys] and [removedKeys] are `std::map`/`std::set`
 *    equivalents (`sortedMapOf`/`sortedSetOf`), so [delta] yields upserts and
 *    deletions in ascending key order like C++.
 *  - `add` keeps a key in [removedKeys] when it is re-added after removal, so
 *    [delta] can report the same key as both an upsert and a deletion;
 *    `IOLoginDataSave::savePlayerStorage` deletes first and upserts second
 *    (iologindata_save_player.cpp:849-855), exactly like the port ordering.
 *  - `ingest` clears all state first and loads every row with
 *    `shouldStorageUpdate = true, shouldTrackModification = false`, so a reload
 *    is never marked dirty (player_storage.cpp:47-54).
 *  - `prepareForPersist`/`upsertKey` only write reserved-range keys whose value
 *    changed and never remove stale ones (player_storage.cpp:139-160).
 *  - Removing an outfit/familiar entry owner-side is not reflected by the
 *    storage; the reserved range is upsert-only in Canary.
 */
class PlayerStorage(
    /** `Player &m_player` (player_storage.hpp:159-160). */
    private val player: StoragePlayer,
    /** `g_dispatcher().getDispatcherCycle()` (player_storage.cpp:83). */
    private val currentFrameTime: () -> Long = { 0L },
    /** `g_events().eventOnStorageUpdate(...)` (player_storage.cpp:85). */
    private val eventOnStorageUpdate: (StoragePlayer, Int, Int, Int, Long) -> Unit = { _, _, _, _, _ -> },
    /** `g_callbacks().executeCallback(EventCallback_t::playerOnStorageUpdate, ...)` (player_storage.cpp:86). */
    private val playerOnStorageUpdateCallback: (StoragePlayer, Int, Int, Int, Long) -> Unit = { _, _, _, _, _ -> },
    /** `g_logger().warn` (player_storage.cpp:71). */
    private val logWarn: (String) -> Unit = {},
) {

    /**
     * `struct PlayerStorageDelta` (player_storage.hpp:92-98): pending upserts
     * (`std::map<uint32_t, int32_t>`) and deletions (`std::vector<uint32_t>`).
     */
    data class PlayerStorageDelta(
        val upserts: Map<Int, Int>,
        val deletions: List<Int>,
    )

    /** `std::map<uint32_t, int32_t> m_storageMap` (player_storage.hpp:168). */
    private val storageMap = sortedMapOf<Int, Int>()

    /** `std::set<uint32_t> m_modifiedKeys` (player_storage.hpp:176). */
    private val modifiedKeys = sortedSetOf<Int>()

    /** `std::set<uint32_t> m_removedKeys` (player_storage.hpp:184). */
    private val removedKeys = sortedSetOf<Int>()

    /** `PlayerStorage::ingest` (player_storage.cpp:47-54). */
    fun ingest(rows: List<PlayerStorageRow>) {
        storageMap.clear()
        modifiedKeys.clear()
        removedKeys.clear()
        for (row in rows) {
            add(row.key, row.value, true, false)
        }
    }

    /** `PlayerStorage::add` (player_storage.cpp:56-93). */
    fun add(
        key: Int,
        value: Int,
        shouldStorageUpdate: Boolean = false,
        shouldTrackModification: Boolean = true,
    ) {
        if (isStorageKeyInRange(key, PSTRG_RESERVED_RANGE_START, PSTRG_RESERVED_RANGE_SIZE)) {
            if (isStorageKeyInRange(key, PSTRG_OUTFITS_RANGE_START, PSTRG_OUTFITS_RANGE_SIZE)) {
                player.getOutfits().add(OutfitEntry(lookType = value shr 16, addons = value and 0xFF))
                return
            }
            if (isStorageKeyInRange(key, PSTRG_FAMILIARS_RANGE_START, PSTRG_FAMILIARS_RANGE_SIZE)) {
                player.getFamiliars().add(FamiliarEntry(value shr 16))
                return
            }

            if (!isInPassThrough(key)) {
                logWarn("Unknown reserved key: $key for player: ${player.getName()}")
            }
        }

        if (value != -1) {
            val oldValue = get(key)
            storageMap[key] = value

            if (shouldTrackModification) {
                modifiedKeys.add(key)
            }
            if (!shouldStorageUpdate) {
                val frameTime = currentFrameTime()
                eventOnStorageUpdate(player, key, value, oldValue, frameTime)
                playerOnStorageUpdateCallback(player, key, value, oldValue, frameTime)
            }
        } else {
            storageMap.remove(key)
            modifiedKeys.remove(key)
            removedKeys.add(key)
        }
    }

    /** `PlayerStorage::remove` (player_storage.cpp:110-119). */
    fun remove(key: Int): Boolean {
        if (!has(key)) {
            return false
        }

        storageMap.remove(key)
        modifiedKeys.remove(key)
        removedKeys.add(key)
        return true
    }

    /** `PlayerStorage::has` (player_storage.cpp:106-108). */
    fun has(key: Int): Boolean = storageMap.containsKey(key)

    /** `PlayerStorage::get` (player_storage.cpp:95-104); -1 when missing. */
    fun get(key: Int): Int = storageMap[key] ?: -1

    /** `PlayerStorage::prepareForPersist` (player_storage.cpp:121-123). */
    fun prepareForPersist() {
        getReservedRange()
    }

    /** `PlayerStorage::delta` (player_storage.cpp:125-132). */
    fun delta(): PlayerStorageDelta {
        val upserts = sortedMapOf<Int, Int>()
        for (key in modifiedKeys) {
            upserts[key] = storageMap.getValue(key)
        }
        return PlayerStorageDelta(upserts, removedKeys.toList())
    }

    /** `PlayerStorage::clearDirty` (player_storage.cpp:134-137). */
    fun clearDirty() {
        modifiedKeys.clear()
        removedKeys.clear()
    }

    /** `PlayerStorage::getStorageMap` (player_storage.hpp:113-115). */
    fun getStorageMap(): Map<Int, Int> = storageMap

    /** `PlayerStorage::getModifiedKeys` (player_storage.hpp:120-122). */
    fun getModifiedKeys(): Set<Int> = modifiedKeys

    /** `PlayerStorage::getRemovedKeys` (player_storage.hpp:127-129). */
    fun getRemovedKeys(): Set<Int> = removedKeys

    /** `PlayerStorage::getReservedRange` (player_storage.cpp:139-153). */
    private fun getReservedRange() {
        var outfitsKey = PSTRG_OUTFITS_RANGE_START
        for (entry in player.getOutfits()) {
            outfitsKey++
            upsertKey(outfitsKey, (entry.lookType shl 16) or entry.addons)
        }

        var familiarKey = PSTRG_FAMILIARS_RANGE_START
        for (entry in player.getFamiliars()) {
            familiarKey++
            upsertKey(familiarKey, entry.lookType shl 16)
        }
    }

    /** `PlayerStorage::upsertKey` (player_storage.cpp:155-160). */
    private fun upsertKey(key: Int, value: Int) {
        if (storageMap[key] != value) {
            storageMap[key] = value
            modifiedKeys.add(key)
        }
    }

    /** `isInPassThrough` (player_storage.cpp:36-41). */
    private fun isInPassThrough(key: Int): Boolean =
        PASS_THROUGH_RANGES.any { (start, size) -> isStorageKeyInRange(key, start, size) }

    companion object {
        // src/utils/const.hpp:42-71.
        const val PSTRG_RESERVED_RANGE_START = 10_000_000
        const val PSTRG_RESERVED_RANGE_SIZE = 10_000_000

        const val PSTRG_OUTFITS_RANGE_START = PSTRG_RESERVED_RANGE_START + 1000
        const val PSTRG_OUTFITS_RANGE_SIZE = 500

        const val PSTRG_MOUNTS_RANGE_START = PSTRG_RESERVED_RANGE_START + 2001
        const val PSTRG_MOUNTS_RANGE_SIZE = 10

        const val PSTRG_WING_RANGE_START = PSTRG_RESERVED_RANGE_START + 2012
        const val PSTRG_WING_RANGE_SIZE = 10
        const val PSTRG_WING_CURRENTWING = PSTRG_WING_RANGE_START + 10

        const val PSTRG_EFFECT_RANGE_START = PSTRG_RESERVED_RANGE_START + 2023
        const val PSTRG_EFFECT_RANGE_SIZE = 10
        const val PSTRG_EFFECT_CURRENTEFFECT = PSTRG_EFFECT_RANGE_START + 10

        const val PSTRG_AURA_RANGE_START = PSTRG_RESERVED_RANGE_START + 2034
        const val PSTRG_AURA_RANGE_SIZE = 10
        const val PSTRG_AURA_CURRENTAURA = PSTRG_AURA_RANGE_START + 10

        const val PSTRG_SHADER_RANGE_START = PSTRG_RESERVED_RANGE_START + 2045
        const val PSTRG_SHADER_RANGE_SIZE = 10

        const val PSTRG_FAMILIARS_RANGE_START = PSTRG_RESERVED_RANGE_START + 3000
        const val PSTRG_FAMILIARS_RANGE_SIZE = 500

        /** `kPassThroughRanges` (player_storage.cpp:28-34). */
        private val PASS_THROUGH_RANGES = listOf(
            PSTRG_MOUNTS_RANGE_START to PSTRG_MOUNTS_RANGE_SIZE,
            PSTRG_WING_RANGE_START to PSTRG_WING_RANGE_SIZE,
            PSTRG_EFFECT_RANGE_START to PSTRG_EFFECT_RANGE_SIZE,
            PSTRG_AURA_RANGE_START to PSTRG_AURA_RANGE_SIZE,
            PSTRG_SHADER_RANGE_START to PSTRG_SHADER_RANGE_SIZE,
        )

        /** `isStorageKeyInRange` (src/utils/const.hpp:75-77). */
        fun isStorageKeyInRange(key: Int, start: Int, size: Int): Boolean =
            key >= start && (key - start) <= size
    }
}
