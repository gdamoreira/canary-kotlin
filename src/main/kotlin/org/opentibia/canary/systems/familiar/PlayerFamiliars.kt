package org.opentibia.canary.systems.familiar

/**
 * Self-contained holder for a player's familiar state.
 *
 * Canary parity:
 * - `std::vector<FamiliarEntry> familiars` and `Player::canFamiliar/addFamiliar/removeFamiliar/getFamiliar`
 *   (src/creatures/players/player.cpp:6891-6958).
 * - `Player::setFamiliarLooktype` writes `defaultOutfit.lookFamiliarsType` (player.cpp:6956).
 * - `PlayerStorage::add` familiar branch and `PlayerStorage::getReservedRange` familiar key
 *   assignment (src/creatures/players/components/player_storage.cpp:65-68, 147-152) plus the
 *   `PSTRG_*` constants and `isStorageKeyInRange` from src/utils/const.hpp.
 *
 * Player-level attributes (`vocationId`, `isPremium`, `group->access`) are injected at
 * construction instead of being read from a global `Player`.
 */
class PlayerFamiliars(
    private val definitions: Familiars,
    private val vocationId: Int,
    private val isPremium: Boolean,
    private val hasAccess: Boolean = false
) {
    val familiars: MutableList<FamiliarEntry> = mutableListOf()

    var familiarLooktype: Int = 0
        private set

    fun canFamiliar(lookType: Int): Boolean {
        if (hasAccess) {
            return true
        }

        val familiar = definitions.getFamiliarByLookType(vocationId, lookType) ?: return false

        if (familiar.premium && !isPremium) {
            return false
        }

        if (familiar.unlocked) {
            return true
        }

        return familiars.any { it.lookType == lookType }
    }

    fun addFamiliar(lookType: Int) {
        if (familiars.none { it.lookType == lookType }) {
            familiars.add(FamiliarEntry(lookType))
        }
    }

    fun removeFamiliar(lookType: Int): Boolean {
        return familiars.removeAll { it.lookType == lookType }
    }

    fun getFamiliar(familiar: Familiar): Boolean {
        if (hasAccess) {
            return true
        }

        if (familiar.premium && !isPremium) {
            return false
        }

        if (familiars.any { it.lookType == familiar.lookType }) {
            return true
        }

        if (!familiar.unlocked) {
            return false
        }

        return true
    }

    fun setFamiliarLooktype(familiarLooktype: Int) {
        this.familiarLooktype = familiarLooktype
    }

    /**
     * Mirrors the `PlayerStorage::add` familiar branch: a storage key inside the familiar
     * reserved range stores `value >> 16` as a familiar lookType and consumes the write.
     *
     * @return true when the key belonged to the familiar storage range and was handled.
     */
    fun addStorage(key: Int, value: Int): Boolean {
        if (!isStorageKeyInRange(key, PSTRG_FAMILIARS_RANGE_START, PSTRG_FAMILIARS_RANGE_SIZE)) {
            return false
        }

        familiars.add(FamiliarEntry(value shr 16))
        return true
    }

    /**
     * Mirrors `PlayerStorage::getReservedRange`: owned familiars are serialized as
     * `lookType << 16` under consecutive keys starting at `PSTRG_FAMILIARS_RANGE_START + 1`.
     */
    fun getReservedRange(): Map<Int, Int> {
        val result = LinkedHashMap<Int, Int>()
        var familiarKey = PSTRG_FAMILIARS_RANGE_START
        for (entry in familiars) {
            familiarKey++
            result[familiarKey] = entry.lookType shl 16
        }
        return result
    }

    companion object {
        const val PSTRG_RESERVED_RANGE_START = 10000000
        const val PSTRG_RESERVED_RANGE_SIZE = 10000000

        const val PSTRG_FAMILIARS_RANGE_START = PSTRG_RESERVED_RANGE_START + 3000
        const val PSTRG_FAMILIARS_RANGE_SIZE = 500

        /** Canary parity: `isStorageKeyInRange` (src/utils/const.hpp:75). */
        fun isStorageKeyInRange(key: Int, start: Int, size: Int): Boolean =
            key >= start && (key - start) <= size
    }
}
