package org.opentibia.canary.systems.forge

/**
 * Mirror of Canary's forge enums, item constants, and configuration defaults.
 *
 * Sources:
 *  - enum class ForgeAction_t: src/enums/forge_conversion.hpp:12-18
 *  - ITEM_FORGE_*: src/utils/utils_definitions.hpp:696-698
 *  - FORGE_* defaults: src/config/configmanager.cpp:179,258-273
 */

enum class ForgeAction_t(val value: Int) {
    FUSION(0),
    TRANSFER(1),
    DUSTTOSLIVERS(2),
    SLIVERSTOCORES(3),
    INCREASELIMIT(4),
}

object ForgeItemIds {
    const val ITEM_FORGE_SLIVER = 37109
    const val ITEM_FORGE_CORE = 37110
    const val ITEM_EXALTATION_CHEST = 37561
}

/**
 * Hardcoded offset used by the INCREASELIMIT conversion: the client shows
 * `dustLevel - 75` as the cost to raise the dust limit
 * (src/creatures/players/player.cpp:11772, src/server/network/protocol/protocolgame.cpp:7144).
 */
const val FORGE_DUST_LIMIT_OFFSET = 75

/**
 * Canary FORGE_* configuration values with their configured defaults
 * (src/config/configmanager.cpp:179,258-273).
 */
data class ForgeConfig(
    val amountMultiplier: Double = 3.0,
    val baseSuccessRate: Int = 50,
    val bonusSuccessRate: Int = 15,
    val convergenceFusionDustCost: Int = 130,
    val convergenceTransferDustCost: Int = 160,
    val coreCost: Int = 50,
    val costOneSliver: Int = 20,
    val fusionDustCost: Int = 100,
    val maxDust: Int = 225,
    val maxItemTier: Int = 10,
    val maxSlivers: Int = 7,
    val minSlivers: Int = 3,
    val sliverAmount: Int = 3,
    val tierLossReduction: Int = 50,
    val transferDustCost: Int = 100,
)
