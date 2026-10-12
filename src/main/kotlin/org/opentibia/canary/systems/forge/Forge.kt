package org.opentibia.canary.systems.forge

import kotlin.math.max
import kotlin.math.min

/**
 * Mirror of Canary's `TierInfo` (src/items/items_classification.hpp:12-17).
 */
data class TierInfo(
    val corePrice: Int = 0,
    val regularPrice: Long = 0,
    val convergenceFusionPrice: Long = 0,
    val convergenceTransferPrice: Long = 0,
)

/**
 * Mirror of Canary class `ItemClassification`
 * (src/items/items_classification.hpp:20-37). The tier price table itself is
 * authored in Lua (data/scripts/systems/item_tiers.lua) and registered through
 * `Game.createItemClassification`; this type only stores the engine values.
 */
class ItemClassification(val id: Int = 0) {
    val tiers: MutableMap<Int, TierInfo> = sortedMapOf()

    fun addTier(
        tierId: Int,
        corePrice: Int,
        regularPrice: Long,
        convergenceFusionPrice: Long,
        convergenceTransferPrice: Long,
    ) {
        tiers[tierId] = TierInfo(corePrice, regularPrice, convergenceFusionPrice, convergenceTransferPrice)
    }
}

/**
 * Result of a `forgeResourceConversion` attempt, mirroring the `ForgeHistory`
 * cost/gained pair written by Canary
 * (src/creatures/players/player.cpp:11709-11789).
 */
data class ForgeConversionResult(val cost: Long, val gained: Long)

/**
 * Deterministic engine core of the Exaltation Forge: the player dust ledger and
 * the fusion/resource-conversion math.
 *
 * Sources:
 *  - dust ledger: src/creatures/players/player.cpp:11825-11866
 *  - fusion success + bonus: src/game/game.cpp:11193-11208, src/utils/tools.cpp:1847-1882
 *  - fusion/transfer costs: src/creatures/players/player.cpp:11275-11320,11578-11604
 *  - tier promotion: src/creatures/players/player.cpp:11375-11491
 *  - resource conversion: src/creatures/players/player.cpp:11709-11789
 */
class Forge(private val config: ForgeConfig = ForgeConfig()) {

    private var forgeDusts: Long = 0
    private var forgeDustLevel: Long = 0

    // ---- Dust ledger (player.cpp:11825-11866) ----

    fun setForgeDusts(amount: Long) {
        forgeDusts = amount
    }

    fun addForgeDusts(amount: Long) {
        forgeDusts += amount
    }

    fun removeForgeDusts(amount: Long) {
        forgeDusts = max(0L, forgeDusts - amount)
    }

    fun getForgeDusts(): Long = forgeDusts

    fun addForgeDustLevel(amount: Long) {
        forgeDustLevel += amount
    }

    fun removeForgeDustLevel(amount: Long) {
        forgeDustLevel = max(0L, forgeDustLevel - amount)
    }

    fun getForgeDustLevel(): Long = forgeDustLevel

    // ---- Bonus table (tools.cpp:1847-1882) ----

    fun forgeBonus(number: Int): Int = when {
        number >= 9975 -> 7 // Gain two tiers
        number >= 9950 -> 6 // Second item retained with increased tier
        number >= 9550 -> 5 // Second item retained with unchanged tier
        number >= 9525 -> 4 // Second item retained with decreased tier
        number >= 9500 -> 3 // Gold not consumed
        number >= 9000 -> 2 // Cores not consumed
        number >= 7400 -> 1 // Dust not consumed
        else -> 0 // None
    }

    // ---- Success chance (game.cpp:11193-11203) ----

    fun finalSuccessRate(usedCore: Boolean, forgeChance: Int? = null): Int {
        var baseSuccess = config.baseSuccessRate
        if (forgeChance != null) {
            baseSuccess = min(100, max(0, baseSuccess + forgeChance - 100))
        }
        val coreSuccess = if (usedCore) config.bonusSuccessRate else 0
        return baseSuccess + coreSuccess
    }

    fun rollSuccess(roll: Int, finalRate: Int): Boolean = roll <= finalRate

    /**
     * src/creatures/players/player.cpp:11477 — `uniform_random(1, 100)` against
     * the configured tier-loss reduction (or a guaranteed loss).
     */
    fun isTierLost(roll: Int, reduceTierLoss: Boolean): Boolean =
        roll <= if (reduceTierLoss) config.tierLossReduction else 100

    // ---- Dust costs (player.cpp:11275,11373,11578) ----

    fun fusionDustCost(convergence: Boolean): Long =
        (if (convergence) config.convergenceFusionDustCost else config.fusionDustCost).toLong()

    fun transferDustCost(convergence: Boolean): Long =
        (if (convergence) config.convergenceTransferDustCost else config.transferDustCost).toLong()

    // ---- Gold/core costs from the classification table ----

    /** player.cpp:11302-11308 (convergence) and 11440-11445 / 11513-11518 (regular). */
    fun fusionGoldCost(classification: ItemClassification, targetTier: Int, convergence: Boolean): Long {
        val tierInfo = classification.tiers[targetTier] ?: return 0
        return if (convergence) tierInfo.convergenceFusionPrice else tierInfo.regularPrice
    }

    /** player.cpp:11594-11604 — toTier is the donor tier for convergence, donorTier - 1 otherwise. */
    fun transferGoldCost(classification: ItemClassification, donorTier: Int, convergence: Boolean): Long {
        if (convergence) {
            val toTier = donorTier
            return classification.tiers[toTier]?.convergenceTransferPrice ?: 0
        }
        return classification.tiers[donorTier]?.regularPrice ?: 0
    }

    fun transferCoreCost(classification: ItemClassification, donorTier: Int, convergence: Boolean): Int {
        val toTier = if (convergence) donorTier else donorTier - 1
        return classification.tiers[toTier]?.corePrice ?: 0
    }

    // ---- Consumption rules (player.cpp:11279,11286,11294) ----

    fun shouldSpendFusionDust(success: Boolean, convergence: Boolean, bonus: Int): Boolean =
        convergence || !success || bonus != 1

    fun shouldSpendFusionCores(success: Boolean, convergence: Boolean, bonus: Int): Boolean =
        !convergence && (!success || bonus != 2)

    fun shouldSpendFusionGold(success: Boolean, convergence: Boolean, bonus: Int): Boolean =
        convergence || !success || bonus != 3

    // ---- Tier promotion (player.cpp:11375-11491) ----

    fun convergenceResultTier(tier: Int): Int = tier + 1

    /**
     * player.cpp:11420-11464: the result item starts at tier + 1; bonus 7 can
     * push it to tier + 2 when `tier + 2 <= classificationId`.
     */
    fun successFirstTier(tier: Int, bonus: Int, classificationId: Int): Int =
        if (bonus == 7 && tier + 2 <= classificationId) tier + 2 else tier + 1

    /**
     * player.cpp:11457-11475: returns the retained second-item tier, or null
     * when Canary consumes the second item (bonus not in 4/5/6/8).
     */
    fun successSecondTier(tier: Int, bonus: Int): Int? = when (bonus) {
        4 -> if (tier > 0) tier - 1 else 0
        5 -> tier
        6 -> tier + 1
        8 -> tier
        else -> null
    }

    /**
     * player.cpp:11476-11491: on failure the second item drops one tier when
     * lost, or is consumed when it was already tier 0; otherwise it is retained.
     */
    fun failureSecondTier(tier: Int, tierLost: Boolean): Int? =
        if (!tierLost) tier else if (tier >= 1) tier - 1 else null

    /** player.cpp:11491 — failure replaces the forge bonus with 0 (lost) or 8 (retained). */
    fun failureBonus(tierLost: Boolean): Int = if (tierLost) 0 else 8

    // ---- Resource conversion (player.cpp:11709-11789) ----

    /** DUSTTOSLIVERS: player.cpp:11715-11735. Cost is costOneSliver * sliverAmount, gained is 3. */
    fun dustToSlivers(): ForgeConversionResult? {
        val cost = (config.costOneSliver * config.sliverAmount).toLong()
        if (cost > forgeDusts) {
            return null
        }
        setForgeDusts(forgeDusts - cost)
        return ForgeConversionResult(cost = cost, gained = 3)
    }

    /** SLIVERSTOCORES: player.cpp:11736-11763. Cost is the configured core cost, gained is 1 core. */
    fun sliversToCores(sliverCount: Long): ForgeConversionResult? {
        val cost = config.coreCost.toLong()
        if (cost > sliverCount) {
            return null
        }
        return ForgeConversionResult(cost = cost, gained = 1)
    }

    /** INCREASELIMIT: player.cpp:11764-11783. */
    fun increaseLimit(): ForgeConversionResult? {
        val dustLevel = forgeDustLevel
        if (dustLevel >= config.maxDust) {
            return null
        }
        val upgradeCost = dustLevel - FORGE_DUST_LIMIT_OFFSET
        if (upgradeCost > forgeDusts) {
            return null
        }
        removeForgeDusts(upgradeCost)
        addForgeDustLevel(1)
        return ForgeConversionResult(cost = upgradeCost, gained = dustLevel)
    }
}
