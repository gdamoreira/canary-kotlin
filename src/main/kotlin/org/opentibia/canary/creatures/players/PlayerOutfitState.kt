package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.appearance.outfit.Outfit
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry

/**
 * Self-contained mirror of the Player outfit state
 * (src/creatures/players/player.cpp:6783-6890).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps the `std::vector<OutfitEntry> outfits` directly on `Player`
 * (player.hpp:1686); the state is exposed as an injectable seam so the host
 * player can delegate without a global (AGENTS.md 1.1). Every external
 * dependency is a constructor seam: [isAccessPlayer] replaces `group->access`,
 * [isPremium] replaces `Player::isPremium()`, [isLookTypeRegistered] replaces
 * `g_game().isLookTypeRegistered` (game.hpp:667), [getOutfitByLookType]
 * replaces `Outfits::getInstance().getOutfitByLookType` (outfit.cpp:85) and
 * [logWarn] replaces `g_logger().warn` (player.cpp:6785).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerOutfitState(
    /** `Player::isAccessPlayer()` / `group->access` (player.cpp:6790, 6865). */
    private val isAccessPlayer: () -> Boolean = { false },
    /** `Player::isPremium()` (player.cpp:6799, 6870). */
    private val isPremium: () -> Boolean = { false },
    /** `g_configManager().getBoolean(WARN_UNSAFE_SCRIPTS)` (configmanager.cpp:425). */
    private val warnUnsafeScripts: Boolean = true,
    /** `g_game().isLookTypeRegistered(lookType)` (game.hpp:667). */
    private val isLookTypeRegistered: (lookType: Int) -> Boolean = { true },
    /** `Outfits::getInstance().getOutfitByLookType(player, lookType)` (outfit.cpp:85). */
    private val getOutfitByLookType: (lookType: Int) -> Outfit? = { null },
    /** `g_logger().warn` (player.cpp:6785). */
    private val logWarn: (message: String) -> Unit = {},
) {

    /** `Player::outfits` (player.hpp:1686); insertion order, merge in place. */
    private val outfits = mutableListOf<OutfitEntry>()

    /** `Player::getOutfits` (player.hpp:1564-1566). */
    fun getOutfits(): List<OutfitEntry> = outfits

    /**
     * `Player::canWear` (player.cpp:6783-6806): registration gate, access
     * bypass, registry/premium checks, then the unlocked-with-no-addons fast
     * path and the owned-addon bitmask check.
     */
    fun canWear(lookType: Int, addons: Int): Boolean {
        if (warnUnsafeScripts && lookType != 0 && !isLookTypeRegistered(lookType)) {
            logWarn(
                "[Player::canWear] An unregistered creature looktype type with id '$lookType' " +
                    "was blocked to prevent client crash.",
            )
            return false
        }

        if (isAccessPlayer()) {
            return true
        }

        val outfit = getOutfitByLookType(lookType) ?: return false

        if (outfit.premium && !isPremium()) {
            return false
        }

        if (outfit.unlocked && addons == 0) {
            return true
        }

        for (outfitEntry in outfits) {
            if (outfitEntry.lookType != lookType) {
                continue
            }
            return (outfitEntry.addons and addons) == addons
        }
        return false
    }

    /** `Player::addOutfit` (player.cpp:6833-6841). */
    fun addOutfit(lookType: Int, addons: Int) {
        val type = lookType and UINT16_MAX
        val mask = addons and UINT8_MAX
        for (outfitEntry in outfits) {
            if (outfitEntry.lookType == type) {
                outfitEntry.addons = outfitEntry.addons or mask
                return
            }
        }
        outfits.add(OutfitEntry(type, mask))
    }

    /** `Player::removeOutfit` (player.cpp:6843-6852). */
    fun removeOutfit(lookType: Int): Boolean {
        val iterator = outfits.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.lookType == (lookType and UINT16_MAX)) {
                iterator.remove()
                return true
            }
        }
        return false
    }

    /** `Player::removeOutfitAddon` (player.cpp:6854-6862). */
    fun removeOutfitAddon(lookType: Int, addons: Int): Boolean {
        val type = lookType and UINT16_MAX
        val mask = addons and UINT8_MAX
        for (outfitEntry in outfits) {
            if (outfitEntry.lookType == type) {
                outfitEntry.addons = outfitEntry.addons and mask.inv() and UINT8_MAX
                return true
            }
        }
        return false
    }

    /**
     * `Player::getOutfitAddons` (player.cpp:6864-6890). The C++ `bool` return
     * plus `uint8_t& addons` out-parameter is folded into a nullable return:
     * `null` is `false`, otherwise the owned addon mask (access players get 3).
     */
    fun getOutfitAddons(outfit: Outfit): Int? {
        if (isAccessPlayer()) {
            return 3
        }

        if (outfit.premium && !isPremium()) {
            return null
        }

        for (outfitEntry in outfits) {
            if (outfitEntry.lookType != outfit.lookType) {
                continue
            }
            return outfitEntry.addons
        }

        if (!outfit.unlocked) {
            return null
        }

        return 0
    }

    companion object {
        /** `std::numeric_limits<uint8_t>::max()` (outfit.hpp:17). */
        private const val UINT8_MAX = 0xFF

        /** `std::numeric_limits<uint16_t>::max()` (outfit.hpp:18). */
        private const val UINT16_MAX = 0xFFFF
    }
}
