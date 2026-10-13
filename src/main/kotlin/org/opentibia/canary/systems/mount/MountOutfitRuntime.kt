package org.opentibia.canary.systems.mount

import org.opentibia.canary.combat.condition.Outfit

/**
 * Mount branch of `Game::playerChangeOutfit`
 * (src/game/game.cpp:7237-7302).
 *
 * Canary takes the outfit by value, so its mutations are local to the call;
 * the port returns them in [MountOutfitChange]. The trailing outfit-wear,
 * attached-effect and protocol serialization code of the C++ method is not
 * modelled here (it belongs to the outfit/attached-effects systems).
 *
 * PARITY GAPS:
 *  - `player->setDefaultOutfit(outfit)` / `internalCreatureChangeOutfit` are
 *    the caller's responsibility (this engine owns only mount state);
 *  - the `canWear` gate at `game.cpp:7304` is not part of the mount branch.
 */
class MountOutfitRuntime(
    private val mounts: Mounts,
    private val state: PlayerMountState,
    /** `Outfits::getInstance().getOutfitByLookType(player, lookType)` non-null (`game.cpp:7257`). */
    private val outfitExists: (lookType: Int) -> Boolean = { true },
    /** `g_game().outfitSupportsMount(lookType)` (`game.cpp:7264`). */
    private val outfitSupportsMount: (lookType: Int) -> Boolean = { true },
    /** `player->isWearingSupportOutfit()` (`game.cpp:7242`). */
    private val isWearingSupportOutfit: () -> Boolean = { false },
    /** `player->getTile()` non-null (`game.cpp:7281`). */
    private val hasTile: () -> Boolean = { true },
    /** `playerTile->hasFlag(TILESTATE_PROTECTIONZONE)` (`game.cpp:7285`). */
    private val isInProtectionZone: () -> Boolean = { false },
    /** `g_configManager().getBoolean(TOGGLE_MOUNT_IN_PZ)` (`game.cpp:7285`). */
    private val toggleMountInPz: Boolean = true,
    /** `g_game().changeSpeed(player, delta)` (`game.cpp:7295`). */
    private val changeSpeed: (delta: Int) -> Unit = {},
) {

    /**
     * Result of the mount branch: [lookMount] is the `outfit.lookMount` the
     * caller must apply, or `null` when Canary returned early (unknown mount,
     * unowned mount or missing tile) and the change must be aborted.
     * [isMountRandomized] carries the possibly-cleared random flag.
     */
    data class MountOutfitChange(
        val lookMount: Int?,
        val isMountRandomized: Int,
    )

    /** `Game::playerChangeOutfit(player, outfit, setMount, isMountRandomized)`. */
    fun playerChangeOutfit(outfit: Outfit, setMount: Boolean, isMountRandomized: Int): MountOutfitChange {
        var lookMount = outfit.lookMount
        var randomized = isMountRandomized

        if (isWearingSupportOutfit()) {
            lookMount = 0
            randomized = 0
        }

        state.setRandomMount(randomized)

        if (randomized != 0 && setMount && state.hasAnyMount()) {
            lookMount = MountProtocol.resolveRandomMountClientId(mounts, state.getRandomMountId())
            if (lookMount == 0) {
                randomized = 0
                state.setRandomMount(0)
            }
        }

        if (!outfitExists(outfit.lookType) || !setMount) {
            lookMount = 0
            randomized = 0
            state.setRandomMount(0)
        }

        if (lookMount != 0 && !outfitSupportsMount(outfit.lookType)) {
            lookMount = 0
            randomized = 0
            state.setRandomMount(0)
        }

        if (lookMount != 0) {
            val mount = mounts.getMountByClientID(lookMount)
                ?: return MountOutfitChange(lookMount = null, isMountRandomized = randomized)

            if (!state.hasMount(mount)) {
                return MountOutfitChange(lookMount = null, isMountRandomized = randomized)
            }

            if (!hasTile()) {
                return MountOutfitChange(lookMount = null, isMountRandomized = randomized)
            }

            val blockedInProtectionZone = !toggleMountInPz && isInProtectionZone()
            if (blockedInProtectionZone) {
                lookMount = 0
            } else {
                var deltaSpeedChange = mount.speed
                if (state.isMounted()) {
                    val previousMount = mounts.getMountByID(state.getCurrentMount())
                    if (previousMount != null) {
                        deltaSpeedChange -= previousMount.speed
                    }
                }
                changeSpeed(deltaSpeedChange)
            }

            state.setCurrentMount(mount.id)
        } else if (state.isMounted()) {
            state.dismount()
        }

        return MountOutfitChange(lookMount = lookMount, isMountRandomized = randomized)
    }
}
