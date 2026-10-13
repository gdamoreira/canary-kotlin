package org.opentibia.canary.systems.wheel

import org.opentibia.canary.config.FeatureFlags

/**
 * Canary `WHEEL_ATELIER_*` configuration values with their configured defaults
 * (src/config/configmanager.cpp:379-384).
 */
data class WheelConfig(
    val revealGreaterCost: Int = 6_000_000,
    val revealLesserCost: Int = 125_000,
    val revealRegularCost: Int = 1_000_000,
    val rotateGreaterCost: Int = 500_000,
    val rotateLesserCost: Int = 125_000,
    val rotateRegularCost: Int = 250_000,
) {
    companion object {
        /**
         * Builds the wheel atelier configuration from the Canary
         * `WHEEL_ATELIER_*` feature flags. The `FeatureFlags` defaults match
         * this type's defaults exactly.
         */
        fun fromFeatureFlags(flags: FeatureFlags): WheelConfig = WheelConfig(
            revealGreaterCost = flags.wheelAtelierRevealGreaterCost,
            revealLesserCost = flags.wheelAtelierRevealLesserCost,
            revealRegularCost = flags.wheelAtelierRevealRegularCost,
            rotateGreaterCost = flags.wheelAtelierRotateGreaterCost,
            rotateLesserCost = flags.wheelAtelierRotateLesserCost,
            rotateRegularCost = flags.wheelAtelierRotateRegularCost,
        )
    }
}

/**
 * Wheel fragment item ids `ITEM_LESSER_FRAGMENT` / `ITEM_GREATER_FRAGMENT`
 * (src/utils/utils_definitions.hpp:708-709).
 */
object WheelItemIds {
    const val ITEM_LESSER_FRAGMENT = 46625
    const val ITEM_GREATER_FRAGMENT = 46626
}

/**
 * Dependency-injected replacement for the Player/Game inventory and economy
 * calls the wheel gem handlers perform, so the application service owns no real
 * inventory. Each member mirrors a Canary call site:
 *
 *  - [getWheelGemId]: `Vocation::getWheelGemId` (vocation.cpp:405), the item id
 *    of the quality/vocation wheel gem consumed by `revealGem`; `0` when the
 *    vocation defines no such gem.
 *  - [hasItemCountById] / [removeItemCountById]: `Player::hasItemCountById`
 *    (player.hpp:655) and `Player::removeItemCountById` (player.hpp:659).
 *  - [removeMoney]: `Game::removeMoney(..., useBank = true)` (game.cpp:3299).
 *  - [internalPlayerAddItem]: `Item::CreateItem` plus
 *    `Game::internalPlayerAddItem` (player_wheel.cpp:1239-1240).
 */
interface WheelPlayer {
    fun getWheelGemId(quality: WheelGemQuality): Int

    fun hasItemCountById(itemId: Int, count: Int, checkStash: Boolean): Boolean

    fun removeItemCountById(itemId: Int, count: Int, removeFromStash: Boolean = true): Boolean

    fun removeMoney(amount: Long): Boolean

    fun internalPlayerAddItem(itemId: Int, count: Int): Boolean

    companion object {
        /**
         * Null-object economy used by callers that have not been wired to a
         * real inventory (the router constructs [WheelApplication] with no
         * player seam). Every check passes and no mutation is recorded, so the
         * pre-economy command-state behavior is preserved. Wiring a real
         * [WheelPlayer] into the packet router is a PARITY GAP.
         */
        val UNWIRED: WheelPlayer = object : WheelPlayer {
            override fun getWheelGemId(quality: WheelGemQuality): Int = UNWIRED_GEM_ITEM_ID

            override fun hasItemCountById(itemId: Int, count: Int, checkStash: Boolean): Boolean = true

            override fun removeItemCountById(itemId: Int, count: Int, removeFromStash: Boolean): Boolean = true

            override fun removeMoney(amount: Long): Boolean = true

            override fun internalPlayerAddItem(itemId: Int, count: Int): Boolean = true
        }

        /** Any non-zero id works: [UNWIRED] never blocks or removes items. */
        private const val UNWIRED_GEM_ITEM_ID = 1
    }
}
