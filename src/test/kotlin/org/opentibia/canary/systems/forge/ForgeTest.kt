package org.opentibia.canary.systems.forge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.FeatureFlags

/**
 * Transcript tests for the Exaltation Forge engine core. Expected values are
 * transcribed from Canary:
 *  - src/enums/forge_conversion.hpp
 *  - src/utils/utils_definitions.hpp (ITEM_FORGE_*)
 *  - src/config/configmanager.cpp:258-273 (FORGE_* defaults)
 *  - src/utils/tools.cpp:1847-1882 (forgeBonus)
 *  - src/game/game.cpp:11193-11208 (success rate + bonus roll)
 *  - src/creatures/players/player.cpp:11246-11866 (dust ledger, costs, conversion)
 */
class ForgeTest {

    @Test
    fun `forge action values match canary`() {
        assertThat(ForgeAction_t.FUSION.value).isEqualTo(0)
        assertThat(ForgeAction_t.TRANSFER.value).isEqualTo(1)
        assertThat(ForgeAction_t.DUSTTOSLIVERS.value).isEqualTo(2)
        assertThat(ForgeAction_t.SLIVERSTOCORES.value).isEqualTo(3)
        assertThat(ForgeAction_t.INCREASELIMIT.value).isEqualTo(4)
    }

    @Test
    fun `forge item ids match canary`() {
        assertThat(ForgeItemIds.ITEM_FORGE_SLIVER).isEqualTo(37109)
        assertThat(ForgeItemIds.ITEM_FORGE_CORE).isEqualTo(37110)
        assertThat(ForgeItemIds.ITEM_EXALTATION_CHEST).isEqualTo(37561)
    }

    @Test
    fun `forge config defaults match canary configmanager`() {
        val config = ForgeConfig()
        assertThat(config.amountMultiplier).isEqualTo(3.0)
        assertThat(config.baseSuccessRate).isEqualTo(50)
        assertThat(config.bonusSuccessRate).isEqualTo(15)
        assertThat(config.convergenceFusionDustCost).isEqualTo(130)
        assertThat(config.convergenceTransferDustCost).isEqualTo(160)
        assertThat(config.coreCost).isEqualTo(50)
        assertThat(config.costOneSliver).isEqualTo(20)
        assertThat(config.fusionDustCost).isEqualTo(100)
        assertThat(config.maxDust).isEqualTo(225)
        assertThat(config.maxItemTier).isEqualTo(10)
        assertThat(config.maxSlivers).isEqualTo(7)
        assertThat(config.minSlivers).isEqualTo(3)
        assertThat(config.sliverAmount).isEqualTo(3)
        assertThat(config.tierLossReduction).isEqualTo(50)
        assertThat(config.transferDustCost).isEqualTo(100)
    }

    @Test
    fun `forge config from feature flags drives success costs and cap`() {
        val config = ForgeConfig.fromFeatureFlags(
            FeatureFlags(
                forgeBaseSuccessRate = 40,
                forgeBonusSuccessRate = 10,
                forgeFusionDustCost = 77,
                forgeMaxDust = 100,
            )
        )
        val forge = Forge(config)

        assertThat(forge.finalSuccessRate(usedCore = false)).isEqualTo(40)
        assertThat(forge.finalSuccessRate(usedCore = true)).isEqualTo(50)
        assertThat(forge.fusionDustCost(convergence = false)).isEqualTo(77)

        forge.addForgeDustLevel(100)
        assertThat(forge.increaseLimit()).isNull()
    }

    @Test
    fun `dust ledger add set and remove clamp at zero`() {
        val forge = Forge()
        assertThat(forge.getForgeDusts()).isEqualTo(0)

        forge.addForgeDusts(120)
        assertThat(forge.getForgeDusts()).isEqualTo(120)

        forge.removeForgeDusts(100)
        assertThat(forge.getForgeDusts()).isEqualTo(20)

        forge.removeForgeDusts(1000)
        assertThat(forge.getForgeDusts()).isEqualTo(0)

        forge.setForgeDusts(225)
        assertThat(forge.getForgeDusts()).isEqualTo(225)
    }

    @Test
    fun `dust level add and remove clamp at zero`() {
        val forge = Forge()
        forge.addForgeDustLevel(80)
        assertThat(forge.getForgeDustLevel()).isEqualTo(80)

        forge.removeForgeDustLevel(30)
        assertThat(forge.getForgeDustLevel()).isEqualTo(50)

        forge.removeForgeDustLevel(500)
        assertThat(forge.getForgeDustLevel()).isEqualTo(0)
    }

    @Test
    fun `forge bonus follows canary thresholds`() {
        val forge = Forge()
        assertThat(forge.forgeBonus(7399)).isEqualTo(0)
        assertThat(forge.forgeBonus(7400)).isEqualTo(1)
        assertThat(forge.forgeBonus(8999)).isEqualTo(1)
        assertThat(forge.forgeBonus(9000)).isEqualTo(2)
        assertThat(forge.forgeBonus(9499)).isEqualTo(2)
        assertThat(forge.forgeBonus(9500)).isEqualTo(3)
        assertThat(forge.forgeBonus(9524)).isEqualTo(3)
        assertThat(forge.forgeBonus(9525)).isEqualTo(4)
        assertThat(forge.forgeBonus(9549)).isEqualTo(4)
        assertThat(forge.forgeBonus(9550)).isEqualTo(5)
        assertThat(forge.forgeBonus(9949)).isEqualTo(5)
        assertThat(forge.forgeBonus(9950)).isEqualTo(6)
        assertThat(forge.forgeBonus(9974)).isEqualTo(6)
        assertThat(forge.forgeBonus(9975)).isEqualTo(7)
        assertThat(forge.forgeBonus(10000)).isEqualTo(7)
    }

    @Test
    fun `final success rate adds core bonus and clamps event chance`() {
        val forge = Forge()
        assertThat(forge.finalSuccessRate(usedCore = false)).isEqualTo(50)
        assertThat(forge.finalSuccessRate(usedCore = true)).isEqualTo(65)
        assertThat(forge.finalSuccessRate(usedCore = false, forgeChance = 100)).isEqualTo(50)
        assertThat(forge.finalSuccessRate(usedCore = false, forgeChance = 0)).isEqualTo(0)
        assertThat(forge.finalSuccessRate(usedCore = true, forgeChance = 0)).isEqualTo(15)
        assertThat(forge.finalSuccessRate(usedCore = false, forgeChance = 200)).isEqualTo(100)
        assertThat(forge.finalSuccessRate(usedCore = true, forgeChance = 200)).isEqualTo(115)
    }

    @Test
    fun `success roll uses one to hundred`() {
        val forge = Forge()
        assertThat(forge.rollSuccess(50, 50)).isTrue()
        assertThat(forge.rollSuccess(51, 50)).isFalse()
        assertThat(forge.rollSuccess(65, 65)).isTrue()
        assertThat(forge.rollSuccess(100, 100)).isTrue()
    }

    @Test
    fun `tier loss roll honours reduction`() {
        val forge = Forge()
        assertThat(forge.isTierLost(50, reduceTierLoss = true)).isTrue()
        assertThat(forge.isTierLost(51, reduceTierLoss = true)).isFalse()
        assertThat(forge.isTierLost(100, reduceTierLoss = false)).isTrue()
    }

    @Test
    fun `fusion dust cost depends on convergence`() {
        val forge = Forge()
        assertThat(forge.fusionDustCost(convergence = false)).isEqualTo(100)
        assertThat(forge.fusionDustCost(convergence = true)).isEqualTo(130)
        assertThat(forge.transferDustCost(convergence = false)).isEqualTo(100)
        assertThat(forge.transferDustCost(convergence = true)).isEqualTo(160)
    }

    @Test
    fun `fusion gold cost reads classification target tier price`() {
        val forge = Forge()
        val classification4 = classification4()

        assertThat(forge.fusionGoldCost(classification4, targetTier = 1, convergence = false)).isEqualTo(8000000)
        assertThat(forge.fusionGoldCost(classification4, targetTier = 1, convergence = true)).isEqualTo(55000000)
        assertThat(forge.fusionGoldCost(classification4, targetTier = 2, convergence = false)).isEqualTo(20000000)
    }

    @Test
    fun `transfer gold and core cost read classification tier price`() {
        val forge = Forge()
        val classification4 = classification4()

        assertThat(forge.transferGoldCost(classification4, donorTier = 2, convergence = false)).isEqualTo(20000000)
        assertThat(forge.transferGoldCost(classification4, donorTier = 2, convergence = true)).isEqualTo(165000000)
        assertThat(forge.transferCoreCost(classification4, donorTier = 2, convergence = false)).isEqualTo(1)
        assertThat(forge.transferCoreCost(classification4, donorTier = 2, convergence = true)).isEqualTo(2)
    }

    @Test
    fun `fusion tier promotion follows bonus`() {
        val forge = Forge()
        assertThat(forge.convergenceResultTier(3)).isEqualTo(4)
        assertThat(forge.successFirstTier(tier = 3, bonus = 0, classificationId = 4)).isEqualTo(4)
        assertThat(forge.successFirstTier(tier = 2, bonus = 7, classificationId = 4)).isEqualTo(4)
        assertThat(forge.successFirstTier(tier = 3, bonus = 7, classificationId = 4)).isEqualTo(4)
        assertThat(forge.successSecondTier(tier = 3, bonus = 4)).isEqualTo(2)
        assertThat(forge.successSecondTier(tier = 3, bonus = 5)).isEqualTo(3)
        assertThat(forge.successSecondTier(tier = 3, bonus = 6)).isEqualTo(4)
        assertThat(forge.successSecondTier(tier = 3, bonus = 0)).isNull()
    }

    @Test
    fun `failure tier promotion and bonus`() {
        val forge = Forge()
        assertThat(forge.failureSecondTier(tier = 3, tierLost = true)).isEqualTo(2)
        assertThat(forge.failureSecondTier(tier = 0, tierLost = true)).isNull()
        assertThat(forge.failureSecondTier(tier = 3, tierLost = false)).isEqualTo(3)
        assertThat(forge.failureBonus(tierLost = true)).isEqualTo(0)
        assertThat(forge.failureBonus(tierLost = false)).isEqualTo(8)
    }

    @Test
    fun `fusion consumption rules follow bonus`() {
        val forge = Forge()
        assertThat(forge.shouldSpendFusionDust(success = true, convergence = false, bonus = 1)).isFalse()
        assertThat(forge.shouldSpendFusionDust(success = true, convergence = false, bonus = 0)).isTrue()
        assertThat(forge.shouldSpendFusionDust(success = false, convergence = false, bonus = 1)).isTrue()
        assertThat(forge.shouldSpendFusionDust(success = true, convergence = true, bonus = 1)).isTrue()

        assertThat(forge.shouldSpendFusionCores(success = true, convergence = false, bonus = 2)).isFalse()
        assertThat(forge.shouldSpendFusionCores(success = true, convergence = false, bonus = 0)).isTrue()
        assertThat(forge.shouldSpendFusionCores(success = false, convergence = false, bonus = 2)).isTrue()
        assertThat(forge.shouldSpendFusionCores(success = true, convergence = true, bonus = 0)).isFalse()

        assertThat(forge.shouldSpendFusionGold(success = true, convergence = false, bonus = 3)).isFalse()
        assertThat(forge.shouldSpendFusionGold(success = true, convergence = false, bonus = 0)).isTrue()
        assertThat(forge.shouldSpendFusionGold(success = false, convergence = false, bonus = 3)).isTrue()
        assertThat(forge.shouldSpendFusionGold(success = true, convergence = true, bonus = 3)).isTrue()
    }

    @Test
    fun `dust to slivers conversion costs sixty dust and gains three`() {
        val forge = Forge()
        forge.setForgeDusts(60)
        val result = forge.dustToSlivers()
        assertThat(result).isNotNull
        assertThat(result!!.cost).isEqualTo(60)
        assertThat(result.gained).isEqualTo(3)
        assertThat(forge.getForgeDusts()).isEqualTo(0)

        forge.setForgeDusts(59)
        assertThat(forge.dustToSlivers()).isNull()
    }

    @Test
    fun `slivers to cores conversion costs fifty slivers and gains one core`() {
        val forge = Forge()
        val result = forge.sliversToCores(50)
        assertThat(result).isNotNull
        assertThat(result!!.cost).isEqualTo(50)
        assertThat(result.gained).isEqualTo(1)
        assertThat(forge.sliversToCores(49)).isNull()
    }

    @Test
    fun `increase limit spends level minus seventy five dust`() {
        val forge = Forge()
        forge.setForgeDusts(100)
        forge.addForgeDustLevel(100)

        val result = forge.increaseLimit()
        assertThat(result).isNotNull
        assertThat(result!!.cost).isEqualTo(25)
        assertThat(result.gained).isEqualTo(100)
        assertThat(forge.getForgeDusts()).isEqualTo(75)
        assertThat(forge.getForgeDustLevel()).isEqualTo(101)
    }

    @Test
    fun `increase limit respects max dust cap`() {
        val forge = Forge()
        forge.setForgeDusts(1000)
        forge.addForgeDustLevel(225)
        assertThat(forge.increaseLimit()).isNull()
    }

    @Test
    fun `forge history add de-duplicates createdAt`() {
        val history = PlayerForgeHistory(playerGuid = 42)
        history.add(ForgeHistory(id = 1, createdAt = 1000))
        history.add(ForgeHistory(id = 2, createdAt = 1000))
        history.add(ForgeHistory(id = 3, createdAt = 1001))
        assertThat(history.get().map { it.createdAt }).containsExactly(1000, 1001, 1002)
    }

    @Test
    fun `forge history remove deletes by id and tracks removal`() {
        val history = PlayerForgeHistory(playerGuid = 42)
        history.add(ForgeHistory(id = 1, createdAt = 1000))
        history.add(ForgeHistory(id = 2, createdAt = 2000))
        history.remove(1)
        assertThat(history.get().map { it.id }).containsExactly(2)
    }

    private fun classification4(): ItemClassification = ItemClassification(4).apply {
        addTier(
            tierId = 1,
            corePrice = 1,
            regularPrice = 8000000,
            convergenceFusionPrice = 55000000,
            convergenceTransferPrice = 65000000,
        )
        addTier(
            tierId = 2,
            corePrice = 2,
            regularPrice = 20000000,
            convergenceFusionPrice = 110000000,
            convergenceTransferPrice = 165000000,
        )
    }
}
