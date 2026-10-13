package org.opentibia.canary.systems.mount

/**
 * Mount computations that Canary keeps inside `Game`/`ProtocolGame`.
 *
 * Extracted verbatim from the enclosing C++ methods so the port can test them
 * without the game/protocol globals:
 *  - `Game::resolveRandomMountClientId` (src/game/game.cpp:10041-10044)
 *  - `ProtocolGame::sendOutfitWindow` current-mount branch
 *    (src/server/network/protocol/protocolgame.cpp:9031-9044)
 *  - `ProtocolGame::sendOutfitWindow` mount list
 *    (src/server/network/protocol/protocolgame.cpp:9193-9221)
 */
object MountProtocol {

    /**
     * Current outfit mount fields of the outfit window (`sendOutfitWindow`).
     *
     * [lookMount] is the `Outfit_t::lookMount` the window advertises and
     * [mounted] the trailing "is mounted" flag.
     */
    data class OutfitWindowMount(
        val lookMount: Int,
        val mounted: Boolean,
    )

    /**
     * One `sendOutfitWindow` mount list entry: `uint16_t clientId`,
     * `std::string name`, the owned/store flag byte and, for store entries, the
     * trailing `uint32_t 0x00` price.
     */
    data class OutfitWindowMountEntry(
        val clientId: Int,
        val name: String,
        val flag: Int,
        val price: Long?,
    )

    /** `Game::resolveRandomMountClientId` (`game.cpp:10041-10044`). */
    fun resolveRandomMountClientId(mounts: Mounts, randomMountId: Int): Int {
        val randomMount = if (randomMountId != 0) mounts.getMountByID(randomMountId) else null
        return randomMount?.clientId ?: 0
    }

    /**
     * `ProtocolGame::sendOutfitWindow` current-mount branch
     * (`protocolgame.cpp:9031-9044`).
     *
     * When the outfit cannot wear a mount (support outfit or unsupported look
     * type) the window reports mount `0`, otherwise it advertises the player's
     * last mount and flags it as currently mounted when the outfit already
     * carries that mount's client id.
     */
    fun resolveOutfitWindowMount(
        mounts: Mounts,
        lastMount: Int,
        currentLookMount: Int,
        supportOutfit: Boolean,
        outfitSupportsMount: Boolean,
    ): OutfitWindowMount {
        var lookMount = currentLookMount
        var mounted = false

        if (!supportOutfit && outfitSupportsMount) {
            val currentMount = mounts.getMountByID(lastMount)
            if (currentMount != null) {
                mounted = currentLookMount == currentMount.clientId
                lookMount = currentMount.clientId
            }
        } else {
            lookMount = 0
        }

        return OutfitWindowMount(lookMount = lookMount, mounted = mounted)
    }

    /**
     * `ProtocolGame::sendOutfitWindow` mount list
     * (`protocolgame.cpp:9198-9221`): owned mounts are sent as free entries
     * (`0x00`), unowned `store` mounts as purchasable entries (`0x01` with a
     * `uint32_t 0x00` price); every other unowned mount is skipped.
     */
    fun buildOutfitWindowMountList(mounts: Mounts, hasMount: (Mount) -> Boolean): List<OutfitWindowMountEntry> {
        val entries = mutableListOf<OutfitWindowMountEntry>()
        for (mount in mounts.getMounts()) {
            if (hasMount(mount)) {
                entries.add(
                    OutfitWindowMountEntry(
                        clientId = mount.clientId,
                        name = mount.name,
                        flag = 0x00,
                        price = null,
                    ),
                )
            } else if (mount.type == "store") {
                entries.add(
                    OutfitWindowMountEntry(
                        clientId = mount.clientId,
                        name = mount.name,
                        flag = 0x01,
                        price = 0L,
                    ),
                )
            }
        }
        return entries
    }
}
