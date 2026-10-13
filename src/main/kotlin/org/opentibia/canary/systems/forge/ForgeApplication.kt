package org.opentibia.canary.systems.forge

import org.opentibia.canary.network.protocol.forge.ForgeEnterCommand

/**
 * Mirror of Canary's `uniform_random` (src/utils/tools.hpp:90), inclusive on
 * both bounds.
 */
fun interface ForgeRandom {
    fun uniformRandom(minNumber: Int, maxNumber: Int): Int
}

/**
 * Mirror of the `Item` state the forge command handlers read and write
 * (src/items/item.hpp: `getID`, `getTier`, `setTier`, `getItemCount`,
 * `getClassification`, `hasImbuements`).
 */
data class ForgeItem(
    val id: Int,
    var tier: Int = 0,
    var count: Int = 1,
    val classification: Int = 0,
    val hasImbuements: Boolean = false,
)

/**
 * Dependency-injected replacement for the Player/Game inventory and economy
 * calls the forge command handlers perform, so the application service owns no
 * real inventory. Each member mirrors a Canary call site:
 *
 *  - [getFreeBackpackSlots] / [getForgeItemFromId] / [getForgeSliversAndCores]:
 *    src/creatures/players/player.cpp:6013,1561,1307.
 *  - [getItemsClassifications]: the `g_game().getItemsClassifications()` loop
 *    in `Player::forgeFuseItems`/`forgeTransferItemTier`
 *    (src/creatures/players/player.cpp:11297,11589).
 *  - [hasItemCountById] / [removeItemCountById]:
 *    src/creatures/players/player.hpp:655,659.
 *  - [getMoney] / [getBankBalance] / [removeMoney]: `g_game().removeMoney(...)`
 *    (src/game/game.cpp:3299, `useBank = true`).
 *  - [createItem] / [internalAddItem] / [internalRemoveItem]: `Item::CreateItem`
 *    and `g_game().internalAddItem/internalRemoveItem`; the exaltation chest
 *    container these items are routed through in Canary is not modelled.
 */
interface ForgePlayer {
    fun getFreeBackpackSlots(): Int
    fun getForgeItemFromId(itemId: Int, tier: Int, exclude: ForgeItem? = null): ForgeItem?
    fun getItemsClassifications(): List<ItemClassification>
    fun getForgeSliversAndCores(): Pair<Long, Long>
    fun hasItemCountById(itemId: Int, count: Int, checkStash: Boolean): Boolean
    fun removeItemCountById(itemId: Int, count: Int): Boolean
    fun getMoney(): Long
    fun getBankBalance(): Long
    fun removeMoney(amount: Long): Boolean
    fun createItem(itemId: Int, count: Int): ForgeItem
    fun internalAddItem(item: ForgeItem): Boolean
    fun internalRemoveItem(item: ForgeItem, count: Int): Boolean
}

/**
 * Outcome of applying one [ForgeEnterCommand], mirroring the state Canary's
 * `Player::forgeFuseItems` / `forgeTransferItemTier` / `forgeResourceConversion`
 * leave behind and the costs they charge.
 */
sealed interface ForgeApplyResult {
    val actionType: ForgeAction_t

    /**
     * Fusion outcome. [success] is the rolled value Canary stores in
     * `ForgeHistory.success`; a convergence fusion promotes the item to
     * `tier + 1` regardless of that roll, exactly like the C++ branch.
     * [resultTier] is the resulting first-item tier, [secondItemTier] the
     * retained second-item tier or `null` when it was consumed.
     */
    data class Fusion(
        override val actionType: ForgeAction_t = ForgeAction_t.FUSION,
        val success: Boolean,
        val convergence: Boolean,
        val bonus: Int,
        val coreCount: Int,
        val resultTier: Int,
        val secondItemTier: Int?,
        val dustCost: Long,
        val coresCost: Long,
        val goldCost: Long,
    ) : ForgeApplyResult

    /** Transfer outcome; [resultTier] is the receiver's new tier. */
    data class Transfer(
        override val actionType: ForgeAction_t = ForgeAction_t.TRANSFER,
        val convergence: Boolean,
        val resultTier: Int,
        val dustCost: Long,
        val coresCost: Long,
        val goldCost: Long,
    ) : ForgeApplyResult

    /** Resource conversion outcome, mirroring the `ForgeHistory` cost/gained pair. */
    data class Conversion(
        override val actionType: ForgeAction_t,
        val cost: Long,
        val gained: Long,
    ) : ForgeApplyResult

    /**
     * The request was refused. All reasons except [ForgeRejection.NOT_ENOUGH_ROOM]
     * make Canary send the forge error `RETURNVALUE_CONTACTADMINISTRATOR`
     * (src/creatures/players/player.cpp:11246-11789); the specific reason only
     * transcribes the distinguishing guard and log message.
     */
    data class Failure(
        override val actionType: ForgeAction_t,
        val reason: ForgeRejection,
    ) : ForgeApplyResult
}

enum class ForgeRejection {
    /** `getFreeBackpackSlots() == 0` (player.cpp:11247) — `RETURNVALUE_NOTENOUGHROOM`. */
    NOT_ENOUGH_ROOM,
    FIRST_ITEM_NOT_FOUND,
    SECOND_ITEM_NOT_FOUND,
    DONOR_ITEM_NOT_FOUND,
    RECEIVE_ITEM_NOT_FOUND,
    DONOR_TIER_ZERO,
    CLASSIFICATION_MISMATCH,
    CLASSIFICATION_NOT_FOUND,
    TIER_NOT_FOUND,
    NOT_ENOUGH_DUST,
    NOT_ENOUGH_CORES,
    NOT_ENOUGH_MONEY,
    NOT_ENOUGH_SLIVERS,
    MAX_DUST_LEVEL_REACHED,

    /**
     * A seam mutation failed after pre-validation (`internalAddItem` /
     * `internalRemoveItem` / `removeItemCountById` / `removeMoney`), mirroring
     * Canary's `returnValue != RETURNVALUE_NOERROR` abort branches. As in Canary,
     * mutations performed before the failure are not rolled back.
     */
    INTERNAL_ERROR,
}

/**
 * Applies a decoded [ForgeEnterCommand] to [forge] and [player], transcribing the
 * Canary command flow:
 *
 *  - `ProtocolGame::parseForgeEnter` (protocolgame.cpp:7398) dispatch;
 *  - `Game::playerForgeFuseItems` (game.cpp:11178) success/bonus rolls;
 *  - `Player::forgeFuseItems` (player.cpp:11246);
 *  - `Player::forgeTransferItemTier` (player.cpp:11542);
 *  - `Player::forgeResourceConversion` (player.cpp:11709).
 *
 * This service is a thin application layer over the existing [Forge] math and
 * owns no state of its own.
 */
class ForgeApplication(
    private val random: ForgeRandom = ForgeRandom { minNumber, _ -> minNumber },
) {

    /**
     * @param forgeChance mirrors the `eventscheduler` `forge-chance` KV entry
     *   (src/game/game.cpp:11194); when present the base success rate is
     *   adjusted and clamped before the core bonus is added.
     */
    fun apply(
        forge: Forge,
        player: ForgePlayer,
        command: ForgeEnterCommand,
        forgeChance: Int? = null,
    ): ForgeApplyResult = when (command.actionType) {
        ForgeAction_t.FUSION -> applyFusion(forge, player, command, forgeChance)
        ForgeAction_t.TRANSFER -> applyTransfer(forge, player, command)
        ForgeAction_t.DUSTTOSLIVERS -> applyDustToSlivers(forge, player, command)
        ForgeAction_t.SLIVERSTOCORES -> applySliversToCores(forge, player, command)
        ForgeAction_t.INCREASELIMIT -> applyIncreaseLimit(forge, command)
    }

    /**
     * `Game::playerForgeFuseItems` (game.cpp:11178) rolls success and bonus
     * before `Player::forgeFuseItems` (player.cpp:11246) validates resources, so
     * a rejected fusion still consumes both rolls. Pre-validation runs before
     * any item, dust, core or gold mutation.
     */
    private fun applyFusion(
        forge: Forge,
        player: ForgePlayer,
        command: ForgeEnterCommand,
        forgeChance: Int?,
    ): ForgeApplyResult {
        val convergence = command.convergence
        val coreCount = (if (command.usedCore) 1 else 0) + (if (command.reduceTierLoss) 1 else 0)

        val success = forge.rollSuccess(
            random.uniformRandom(1, 100),
            forge.finalSuccessRate(command.usedCore, forgeChance),
        )
        val chance = random.uniformRandom(0, 10000)
        val rolledBonus = if (convergence) 0 else forge.forgeBonus(chance)

        if (player.getFreeBackpackSlots() == 0) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_ROOM)
        }

        val firstItem = player.getForgeItemFromId(command.firstItem, command.tier)
            ?: return ForgeApplyResult.Failure(command.actionType, ForgeRejection.FIRST_ITEM_NOT_FOUND)
        val secondItem = player.getForgeItemFromId(command.secondItem, command.tier, firstItem)
            ?: return ForgeApplyResult.Failure(command.actionType, ForgeRejection.SECOND_ITEM_NOT_FOUND)

        val dustCost = forge.fusionDustCost(convergence)
        if (forge.shouldSpendFusionDust(success, convergence, rolledBonus) && forge.getForgeDusts() < dustCost) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_DUST)
        }

        if (forge.shouldSpendFusionCores(success, convergence, rolledBonus) && coreCount != 0
            && !player.hasItemCountById(ForgeItemIds.ITEM_FORGE_CORE, coreCount, true)
        ) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_CORES)
        }

        val spendsGold = forge.shouldSpendFusionGold(success, convergence, rolledBonus)
        val classification = player.getItemsClassifications().firstOrNull { it.id == firstItem.classification }
        if (spendsGold) {
            if (classification == null) {
                return ForgeApplyResult.Failure(command.actionType, ForgeRejection.CLASSIFICATION_NOT_FOUND)
            }
            if (!classification.tiers.containsKey(command.tier + 1)) {
                return ForgeApplyResult.Failure(command.actionType, ForgeRejection.TIER_NOT_FOUND)
            }
            val goldCheck = forge.fusionGoldCost(classification, command.tier + 1, convergence)
            if (player.getMoney() + player.getBankBalance() < goldCheck) {
                return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_MONEY)
            }
        }

        if (!player.internalRemoveItem(firstItem, 1)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }
        if (!player.internalRemoveItem(secondItem, 1)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }

        val firstResult = player.createItem(command.firstItem, 1)
        firstResult.tier = command.tier
        if (!player.internalAddItem(firstResult)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }

        if (convergence) {
            firstResult.tier = forge.convergenceResultTier(command.tier)
            forge.removeForgeDusts(dustCost)
            val goldCost = forge.fusionGoldCost(checkNotNull(classification), command.tier + 1, true)
            if (!player.removeMoney(goldCost)) {
                return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
            }
            return ForgeApplyResult.Fusion(
                success = success,
                convergence = true,
                bonus = 0,
                coreCount = coreCount,
                resultTier = firstResult.tier,
                secondItemTier = null,
                dustCost = dustCost,
                coresCost = 0,
                goldCost = goldCost,
            )
        }

        val secondResult = player.createItem(command.secondItem, 1)
        secondResult.tier = command.tier
        if (!player.internalAddItem(secondResult)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }

        var dustSpent = 0L
        var coresSpent = 0L
        var goldSpent = 0L
        val bonus: Int
        val secondTier: Int?

        if (success) {
            firstResult.tier = forge.successFirstTier(command.tier, rolledBonus, firstItem.classification)
            if (rolledBonus != 1) {
                forge.removeForgeDusts(dustCost)
                dustSpent = dustCost
            }
            if (rolledBonus != 2 && coreCount != 0) {
                if (!player.removeItemCountById(ForgeItemIds.ITEM_FORGE_CORE, coreCount)) {
                    return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
                }
                coresSpent = coreCount.toLong()
            }
            if (rolledBonus != 3) {
                val goldCost = forge.fusionGoldCost(checkNotNull(classification), command.tier + 1, false)
                if (!player.removeMoney(goldCost)) {
                    return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
                }
                goldSpent = goldCost
            }

            secondTier = forge.successSecondTier(command.tier, rolledBonus)
            bonus = rolledBonus
        } else {
            val tierLost = forge.isTierLost(random.uniformRandom(1, 100), command.reduceTierLoss)
            secondTier = forge.failureSecondTier(command.tier, tierLost)
            bonus = forge.failureBonus(tierLost)

            forge.removeForgeDusts(dustCost)
            dustSpent = dustCost
            if (coreCount != 0) {
                if (!player.removeItemCountById(ForgeItemIds.ITEM_FORGE_CORE, coreCount)) {
                    return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
                }
                coresSpent = coreCount.toLong()
            }
            val goldCost = forge.fusionGoldCost(checkNotNull(classification), command.tier + 1, false)
            if (!player.removeMoney(goldCost)) {
                return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
            }
            goldSpent = goldCost
        }

        if (secondTier == null) {
            if (!player.internalRemoveItem(secondResult, 1)) {
                return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
            }
        } else {
            secondResult.tier = secondTier
        }

        return ForgeApplyResult.Fusion(
            success = success,
            convergence = false,
            bonus = bonus,
            coreCount = coreCount,
            resultTier = firstResult.tier,
            secondItemTier = secondTier,
            dustCost = dustSpent,
            coresCost = coresSpent,
            goldCost = goldSpent,
        )
    }

    /**
     * `Player::forgeTransferItemTier` (player.cpp:11542): the donor must exist at
     * the submitted tier and the receiver at tier 0 with the same classification.
     * Dust, core and gold costs are read from the classification table
     * (`toTier = convergence ? donorTier : donorTier - 1`, player.cpp:11594-11604)
     * and every resource is pre-validated before the inventory is mutated.
     */
    private fun applyTransfer(
        forge: Forge,
        player: ForgePlayer,
        command: ForgeEnterCommand,
    ): ForgeApplyResult {
        val convergence = command.convergence

        if (player.getFreeBackpackSlots() == 0) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_ROOM)
        }

        val donor = player.getForgeItemFromId(command.firstItem, command.tier)
            ?: return ForgeApplyResult.Failure(command.actionType, ForgeRejection.DONOR_ITEM_NOT_FOUND)
        if (!convergence && donor.tier == 0) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.DONOR_TIER_ZERO)
        }

        val receive = player.getForgeItemFromId(command.secondItem, 0, donor)
            ?: return ForgeApplyResult.Failure(command.actionType, ForgeRejection.RECEIVE_ITEM_NOT_FOUND)
        if (donor.classification != receive.classification) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.CLASSIFICATION_MISMATCH)
        }

        val dustCost = forge.transferDustCost(convergence)
        if (forge.getForgeDusts() < dustCost) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_DUST)
        }

        val classification = player.getItemsClassifications().firstOrNull { it.id == donor.classification }
            ?: return ForgeApplyResult.Failure(command.actionType, ForgeRejection.CLASSIFICATION_NOT_FOUND)
        val toTier = if (convergence) donor.tier else donor.tier - 1
        if (!classification.tiers.containsKey(toTier) || !classification.tiers.containsKey(donor.tier)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.TIER_NOT_FOUND)
        }

        val goldCost = forge.transferGoldCost(classification, donor.tier, convergence)
        val coresCost = forge.transferCoreCost(classification, donor.tier, convergence)
        if (!player.hasItemCountById(ForgeItemIds.ITEM_FORGE_CORE, coresCost, true)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_CORES)
        }
        if (player.getMoney() + player.getBankBalance() < goldCost) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_MONEY)
        }

        if (!player.internalRemoveItem(donor, 1)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }
        if (!player.internalRemoveItem(receive, 1)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }

        val newReceive = player.createItem(command.secondItem, 1)
        newReceive.tier = if (convergence) command.tier else command.tier - 1
        if (!player.internalAddItem(newReceive)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }

        forge.removeForgeDusts(dustCost)
        if (!player.removeItemCountById(ForgeItemIds.ITEM_FORGE_CORE, coresCost)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }
        if (!player.removeMoney(goldCost)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }

        return ForgeApplyResult.Transfer(
            convergence = convergence,
            resultTier = newReceive.tier,
            dustCost = dustCost,
            coresCost = coresCost.toLong(),
            goldCost = goldCost,
        )
    }

    /**
     * `DUSTTOSLIVERS` branch of `Player::forgeResourceConversion`
     * (player.cpp:11715-11735): charge `FORGE_COST_ONE_SLIVER *
     * FORGE_SLIVER_AMOUNT` dust and hand out `FORGE_SLIVER_AMOUNT` slivers.
     * Like Canary, the sliver item is granted before the dust is debited, so a
     * failing grant leaves the dust untouched.
     */
    private fun applyDustToSlivers(
        forge: Forge,
        player: ForgePlayer,
        command: ForgeEnterCommand,
    ): ForgeApplyResult {
        val dustCost = (forge.config.costOneSliver * forge.config.sliverAmount).toLong()
        if (dustCost > forge.getForgeDusts()) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_DUST)
        }

        val slivers = player.createItem(ForgeItemIds.ITEM_FORGE_SLIVER, forge.config.sliverAmount)
        if (!player.internalAddItem(slivers)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }

        val conversion = forge.dustToSlivers()
            ?: return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_DUST)
        return ForgeApplyResult.Conversion(command.actionType, conversion.cost, conversion.gained)
    }

    /**
     * `SLIVERSTOCORES` branch of `Player::forgeResourceConversion`
     * (player.cpp:11736-11763): the inventory+stash sliver count must cover
     * `FORGE_CORE_COST`, then that many slivers are removed and one core is
     * granted. The count comes from [ForgePlayer.getForgeSliversAndCores], as in
     * Canary.
     */
    private fun applySliversToCores(
        forge: Forge,
        player: ForgePlayer,
        command: ForgeEnterCommand,
    ): ForgeApplyResult {
        val (sliverCount, _) = player.getForgeSliversAndCores()
        val conversion = forge.sliversToCores(sliverCount)
            ?: return ForgeApplyResult.Failure(command.actionType, ForgeRejection.NOT_ENOUGH_SLIVERS)

        if (!player.removeItemCountById(ForgeItemIds.ITEM_FORGE_SLIVER, conversion.cost.toInt())) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }
        val core = player.createItem(ForgeItemIds.ITEM_FORGE_CORE, 1)
        if (!player.internalAddItem(core)) {
            return ForgeApplyResult.Failure(command.actionType, ForgeRejection.INTERNAL_ERROR)
        }

        return ForgeApplyResult.Conversion(command.actionType, conversion.cost, conversion.gained)
    }

    /**
     * `INCREASELIMIT` (else) branch of `Player::forgeResourceConversion`
     * (player.cpp:11764-11783): raises the dust level by one for
     * `dustLevel - FORGE_DUST_LIMIT_OFFSET` dust, refused at `FORGE_MAX_DUST`.
     * [Forge.increaseLimit] owns both mutations.
     */
    private fun applyIncreaseLimit(forge: Forge, command: ForgeEnterCommand): ForgeApplyResult {
        val conversion = forge.increaseLimit()
            ?: return ForgeApplyResult.Failure(
                command.actionType,
                if (forge.getForgeDustLevel() >= forge.config.maxDust) {
                    ForgeRejection.MAX_DUST_LEVEL_REACHED
                } else {
                    ForgeRejection.NOT_ENOUGH_DUST
                },
            )

        return ForgeApplyResult.Conversion(command.actionType, conversion.cost, conversion.gained)
    }
}
