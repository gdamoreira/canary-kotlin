package org.opentibia.canary.systems.mount

/**
 * Self-contained mirror of the Player mount state machine
 * (src/creatures/players/player.cpp: getLastMount, getCurrentMount,
 * setCurrentMount, isMounted, hasAnyMount, getRandomMountId, toggleMount,
 * tameMount, untameMount, hasMount, dismount, isRandomMounted,
 * setRandomMount).
 *
 * The class is intentionally *not* Player.kt (per task constraints). External
 * engine services that Canary reaches through globals are injected as
 * constructor seams (AGENTS.md 1.1): `changeSpeed` replaces
 * `g_game().changeSpeed`, `isPremium`/`isAccessPlayer` replace the Account/group
 * checks, and the outfit predicates replace `Outfits::getInstance()` and
 * `defaultOutfit`. Storage follows Canary's reserved mount range exactly.
 *
 * Protocol side effects (`sendCancelMessage`, `sendOutfitWindow`) are out of
 * scope for the engine core; the corresponding branches still return `false`.
 */
class PlayerMountState(
    private val mounts: Mounts,
    private val changeSpeed: (delta: Int) -> Unit = {},
    private val isPremium: () -> Boolean = { true },
    private val isAccessPlayer: () -> Boolean = { false },
    private val lookType: () -> Int = { 0 },
    private val outfitSupportsMount: (lookType: Int) -> Boolean = { true },
    private val isWearingSupportOutfit: () -> Boolean = { false },
    private val hasOutfitCondition: () -> Boolean = { false },
    private val isInProtectionZone: () -> Boolean = { false },
    private val toggleMountInPz: Boolean = true,
    private val uniformRandom: (min: Int, max: Int) -> Int = { min, max ->
        min + kotlin.random.Random.nextInt(max - min + 1)
    },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val onOutfitChanged: () -> Unit = {}
) {

    private val storage = mutableMapOf<Int, Int>()
    private var lastMount = 0
    private var defaultOutfitLookMount = 0
    private var randomMount = 0
    private var wasMounted = false
    private var lastToggleMount = 0L

    /** Canary `Player::getLastMount`. */
    fun getLastMount(): Int {
        val value = getStorageValue(PSTRG_MOUNTS_CURRENTMOUNT)
        if (value > 0) {
            return value
        }
        return lastMount
    }

    /** Canary `Player::getCurrentMount`. */
    fun getCurrentMount(): Int {
        val value = getStorageValue(PSTRG_MOUNTS_CURRENTMOUNT)
        return if (value > 0) value else 0
    }

    /** Canary `Player::setCurrentMount`. */
    fun setCurrentMount(mount: Int) {
        addStorageValue(PSTRG_MOUNTS_CURRENTMOUNT, mount)
    }

    /** Canary `Player::isMounted` (`defaultOutfit.lookMount != 0`). */
    fun isMounted(): Boolean = defaultOutfitLookMount != 0

    /** Canary `Player::isRandomMounted`. */
    fun isRandomMounted(): Boolean = randomMount != 0

    /** Canary `Player::setRandomMount`. */
    fun setRandomMount(isMountRandomized: Int) {
        randomMount = isMountRandomized
    }

    /** Canary `Player::hasAnyMount`. */
    fun hasAnyMount(): Boolean = mounts.getMounts().any { hasMount(it) }

    /** Canary `Player::getRandomMountId`. */
    fun getRandomMountId(): Int {
        val playerMounts = mounts.getMounts().filter { hasMount(it) }.map { it.id }
        if (playerMounts.isEmpty()) {
            return 0
        }
        val randomIndex = uniformRandom(0, playerMounts.size - 1)
        if (randomIndex in playerMounts.indices) {
            return playerMounts[randomIndex]
        }
        return 0
    }

    /** Canary `Player::toggleMount`. */
    fun toggleMount(mount: Boolean): Boolean {
        if ((now() - lastToggleMount) < TOGGLE_MOUNT_COOLDOWN_MS && !wasMounted) {
            return false
        }

        if (isWearingSupportOutfit()) {
            return false
        }

        if (mount) {
            if (!outfitSupportsMount(lookType())) {
                return false
            }

            if (isMounted()) {
                return false
            }

            if (!toggleMountInPz && !isAccessPlayer() && isInProtectionZone()) {
                return false
            }

            var currentMountId = getLastMount()
            if (currentMountId == 0) {
                return false
            }

            if (isRandomMounted()) {
                currentMountId = getRandomMountId()
            }

            val currentMount = mounts.getMountByID(currentMountId) ?: return false

            if (!hasMount(currentMount)) {
                setCurrentMount(0)
                lastMount = 0
                return false
            }

            if (currentMount.premium && !isPremium()) {
                return false
            }

            if (hasOutfitCondition()) {
                return false
            }

            defaultOutfitLookMount = currentMount.clientId
            setCurrentMount(currentMount.id)
            lastMount = currentMount.id

            if (currentMount.speed != 0) {
                changeSpeed(currentMount.speed)
            }
        } else {
            if (!isMounted()) {
                return false
            }

            dismount()
        }

        onOutfitChanged()
        lastToggleMount = now()
        return true
    }

    /** Canary `Player::tameMount`. */
    fun tameMount(mountId: Int): Boolean {
        if (mounts.getMountByID(mountId) == null) {
            return false
        }

        val tmpMountId = (mountId - 1) and 0xFF
        val key = PSTRG_MOUNTS_RANGE_START + (tmpMountId / 31)

        val value = getStorageValue(key)
        val updated = if (value != -1) {
            value or (1 shl (tmpMountId % 31))
        } else {
            (1 shl (tmpMountId % 31))
        }

        addStorageValue(key, updated)
        return true
    }

    /** Canary `Player::untameMount`. */
    fun untameMount(mountId: Int): Boolean {
        if (mounts.getMountByID(mountId) == null) {
            return false
        }

        val tmpMountId = (mountId - 1) and 0xFF
        val key = PSTRG_MOUNTS_RANGE_START + (tmpMountId / 31)

        val value = getStorageValue(key)
        if (value == -1) {
            return true
        }

        addStorageValue(key, value and (1 shl (tmpMountId % 31)).inv())

        if (getCurrentMount() == mountId) {
            if (isMounted()) {
                dismount()
                onOutfitChanged()
            }

            setCurrentMount(0)
            lastMount = 0
        }

        return true
    }

    /** Canary `Player::hasMount`. */
    fun hasMount(mount: Mount): Boolean {
        if (isAccessPlayer()) {
            return true
        }

        if (mount.premium && !isPremium()) {
            return false
        }

        val tmpMountId = (mount.id - 1) and 0xFF

        val value = getStorageValue(PSTRG_MOUNTS_RANGE_START + (tmpMountId / 31))
        if (value == -1) {
            return false
        }

        return ((1 shl (tmpMountId % 31)) and value) != 0
    }

    /** Canary `Player::dismount`. */
    fun dismount() {
        val mount = mounts.getMountByID(getCurrentMount())
        if (mount != null && mount.speed > 0) {
            changeSpeed(-mount.speed)
        }

        defaultOutfitLookMount = 0
    }

    /**
     * Persistence seam: every owned mount id, decoded from the reserved mount
     * storage range exactly as [tameMount]/[hasMount] address it. Canary keeps
     * this state in `PlayerStorage`'s mount range and enumerates it there; this
     * accessor makes the same state readable without the definition registry.
     */
    fun getOwnedMounts(): Set<Int> {
        val result = linkedSetOf<Int>()
        for (storageIndex in 0 until PSTRG_MOUNTS_RANGE_SIZE) {
            val value = storage[PSTRG_MOUNTS_RANGE_START + storageIndex] ?: continue
            if (value == -1) {
                continue
            }
            for (bit in 0 until MOUNT_BITS_PER_STORAGE) {
                if ((value and (1 shl bit)) == 0) {
                    continue
                }
                val tmpMountId = storageIndex * MOUNT_BITS_PER_STORAGE + bit
                if (tmpMountId <= MAX_TMP_MOUNT_ID) {
                    result.add(tmpMountId + 1)
                }
            }
        }
        return result
    }

    /**
     * Persistence seam: replace the owned mount set in the reserved storage
     * range, packing bits the same way [tameMount]/[untameMount] do.
     */
    fun setOwnedMounts(mountIds: Set<Int>) {
        for (storageIndex in 0 until PSTRG_MOUNTS_RANGE_SIZE) {
            storage.remove(PSTRG_MOUNTS_RANGE_START + storageIndex)
        }
        for (mountId in mountIds) {
            val tmpMountId = (mountId - 1) and MAX_TMP_MOUNT_ID
            val key = PSTRG_MOUNTS_RANGE_START + (tmpMountId / MOUNT_BITS_PER_STORAGE)
            val value = storage[key] ?: 0
            storage[key] = value or (1 shl (tmpMountId % MOUNT_BITS_PER_STORAGE))
        }
    }

    private fun getStorageValue(key: Int): Int = storage[key] ?: -1

    private fun addStorageValue(key: Int, value: Int) {
        storage[key] = value
    }

    companion object {
        // src/utils/const.hpp: reserved player storage ranges.
        const val PSTRG_RESERVED_RANGE_START = 10_000_000
        const val PSTRG_MOUNTS_RANGE_START = PSTRG_RESERVED_RANGE_START + 2001 // 10002001
        const val PSTRG_MOUNTS_RANGE_SIZE = 10
        const val PSTRG_MOUNTS_CURRENTMOUNT = PSTRG_MOUNTS_RANGE_START + 10 // 10002011

        /** `Player::tameMount` packs 31 mount ids per reserved storage key. */
        private const val MOUNT_BITS_PER_STORAGE = 31
        private const val MAX_TMP_MOUNT_ID = 0xFF

        private const val TOGGLE_MOUNT_COOLDOWN_MS = 3000L
    }
}
