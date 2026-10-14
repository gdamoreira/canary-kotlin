package org.opentibia.canary.systems.forge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.protocol.forge.ForgeEnterCommand

/**
 * Transcript tests for [ForgeApplication].
 *
 * Source of truth: ../.projects/canary
 *  - src/server/network/protocol/protocolgame.cpp:7398 `parseForgeEnter` dispatch
 *  - src/game/game.cpp:11178-11239 `playerForgeFuseItems` /
 *    `playerForgeTransferItemTier` / `playerForgeResourceConversion`
 *  - src/creatures/players/player.cpp:11246-11789 `forgeFuseItems`,
 *    `forgeTransferItemTier`, `forgeResourceConversion`
 *  - src/creatures/players/player.cpp:6013 `getForgeItemFromId`
 *  - src/creatures/players/player.cpp:1561 `getForgeSliversAndCores`
 */
class ForgeApplicationTest {

    private class QueueRandom(values: List<Int>) : ForgeRandom {
        private val queue = ArrayDeque(values)
        val calls = mutableListOf<Pair<Int, Int>>()

        override fun uniformRandom(minNumber: Int, maxNumber: Int): Int {
            calls.add(minNumber to maxNumber)
            return queue.removeFirst()
        }
    }

    private class FakeForgePlayer(
        var freeSlots: Int = 1,
        var wallet: Long = 0,
        var bank: Long = 0,
        var slivers: Long = 0,
        var cores: Long = 0,
        var failAdds: Boolean = false,
        var failRemoves: Boolean = false,
    ) : ForgePlayer {
        val items = mutableListOf<ForgeItem>()
        val classifications = mutableListOf<ItemClassification>()
        val itemClassifications = mutableMapOf<Int, Int>()

        override fun getFreeBackpackSlots(): Int = freeSlots

        override fun getForgeItemFromId(itemId: Int, tier: Int, exclude: ForgeItem?): ForgeItem? =
            items.firstOrNull { it !== exclude && !it.hasImbuements && it.id == itemId && it.tier == tier }

        override fun getItemsClassifications(): List<ItemClassification> = classifications

        override fun getForgeSliversAndCores(): Pair<Long, Long> = slivers to cores

        override fun hasItemCountById(itemId: Int, count: Int, checkStash: Boolean): Boolean = when (itemId) {
            ForgeItemIds.ITEM_FORGE_CORE -> cores >= count
            ForgeItemIds.ITEM_FORGE_SLIVER -> slivers >= count
            else -> false
        }

        override fun removeItemCountById(itemId: Int, count: Int): Boolean = when (itemId) {
            ForgeItemIds.ITEM_FORGE_CORE -> if (cores >= count) {
                cores -= count
                true
            } else {
                false
            }

            ForgeItemIds.ITEM_FORGE_SLIVER -> if (slivers >= count) {
                slivers -= count
                true
            } else {
                false
            }

            else -> false
        }

        override fun getMoney(): Long = wallet

        override fun getBankBalance(): Long = bank

        override fun removeMoney(amount: Long): Boolean {
            if (amount == 0L) {
                return true
            }
            if (wallet + bank < amount) {
                return false
            }
            val fromMoney = minOf(wallet, amount)
            wallet -= fromMoney
            bank -= amount - fromMoney
            return true
        }

        override fun createItem(itemId: Int, count: Int): ForgeItem =
            ForgeItem(id = itemId, count = count, classification = itemClassifications[itemId] ?: 0)

        override fun internalAddItem(item: ForgeItem): Boolean {
            if (failAdds) {
                return false
            }
            when (item.id) {
                ForgeItemIds.ITEM_FORGE_CORE -> cores += item.count
                ForgeItemIds.ITEM_FORGE_SLIVER -> slivers += item.count
                else -> items.add(item)
            }
            return true
        }

        override fun internalRemoveItem(item: ForgeItem, count: Int): Boolean =
            if (failRemoves) false else items.remove(item)
    }

    private fun addFusionItems(player: FakeForgePlayer, itemId: Int = 100, tier: Int = 1, classification: Int = 4) {
        player.itemClassifications[itemId] = classification
        player.items.add(ForgeItem(id = itemId, tier = tier, classification = classification))
        player.items.add(ForgeItem(id = itemId, tier = tier, classification = classification))
    }

    private fun classification4(): ItemClassification = ItemClassification(4).apply {
        addTier(
            tierId = 1,
            corePrice = 1,
            regularPrice = 8_000_000,
            convergenceFusionPrice = 55_000_000,
            convergenceTransferPrice = 65_000_000,
        )
        addTier(
            tierId = 2,
            corePrice = 2,
            regularPrice = 20_000_000,
            convergenceFusionPrice = 110_000_000,
            convergenceTransferPrice = 165_000_000,
        )
        addTier(
            tierId = 3,
            corePrice = 3,
            regularPrice = 35_000_000,
            convergenceFusionPrice = 165_000_000,
            convergenceTransferPrice = 275_000_000,
        )
        addTier(
            tierId = 4,
            corePrice = 4,
            regularPrice = 55_000_000,
            convergenceFusionPrice = 220_000_000,
            convergenceTransferPrice = 385_000_000,
        )
    }

    private fun fusionCommand(
        firstItem: Int = 100,
        tier: Int = 1,
        secondItem: Int = 100,
        convergence: Boolean = false,
        usedCore: Boolean = false,
        reduceTierLoss: Boolean = false,
    ) = ForgeEnterCommand(
        actionType = ForgeAction_t.FUSION,
        convergence = convergence,
        firstItem = firstItem,
        tier = tier,
        secondItem = secondItem,
        usedCore = usedCore,
        reduceTierLoss = reduceTierLoss,
    )

    @Test
    fun `fusion success promotes the first item and spends dust cores and gold`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val random = QueueRandom(listOf(50, 0))
        val application = ForgeApplication(random)

        val result = application.apply(forge, player, fusionCommand(usedCore = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 0,
                coreCount = 1,
                resultTier = 2,
                secondItemTier = null,
                dustCost = 100,
                coresCost = 1,
                goldCost = 20_000_000,
            ),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(100)
        assertThat(player.cores).isEqualTo(0)
        assertThat(player.wallet).isEqualTo(10_000_000)
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 2)
    }

    @Test
    fun `fusion failure keeps the first item and spends dust cores and gold`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val random = QueueRandom(listOf(100, 0, 51))
        val application = ForgeApplication(random)

        val result = application.apply(forge, player, fusionCommand(usedCore = false, reduceTierLoss = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = false,
                convergence = false,
                bonus = 8,
                coreCount = 1,
                resultTier = 1,
                secondItemTier = 1,
                dustCost = 100,
                coresCost = 1,
                goldCost = 20_000_000,
            ),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(100)
        assertThat(player.cores).isEqualTo(0)
        assertThat(player.wallet).isEqualTo(10_000_000)
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 1, 100 to 1)
    }

    @Test
    fun `fusion failure with tier loss consumes a tier zero second item`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1)
        player.classifications.add(classification4())
        addFusionItems(player, tier = 0)
        val forge = Forge()
        forge.setForgeDusts(200)
        val random = QueueRandom(listOf(100, 0, 50))
        val application = ForgeApplication(random)

        val result = application.apply(forge, player, fusionCommand(tier = 0, reduceTierLoss = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = false,
                convergence = false,
                bonus = 0,
                coreCount = 1,
                resultTier = 0,
                secondItemTier = null,
                dustCost = 100,
                coresCost = 1,
                goldCost = 8_000_000,
            ),
        )
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 0)
    }

    @Test
    fun `fusion rejects a full backpack but still rolls success and bonus`() {
        val player = FakeForgePlayer(freeSlots = 0)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        val random = QueueRandom(listOf(50, 0))
        val application = ForgeApplication(random)

        val result = application.apply(forge, player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.NOT_ENOUGH_ROOM),
        )
        assertThat(random.calls).containsExactly(1 to 100, 0 to 10000)
    }

    @Test
    fun `fusion bonus 1 skips the dust cost`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 7400)))

        val result = application.apply(forge, player, fusionCommand(usedCore = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 1,
                coreCount = 1,
                resultTier = 2,
                secondItemTier = null,
                dustCost = 0,
                coresCost = 1,
                goldCost = 20_000_000,
            ),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(200)
    }

    @Test
    fun `fusion bonus 2 skips the core cost`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 9000)))

        val result = application.apply(forge, player, fusionCommand(usedCore = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 2,
                coreCount = 1,
                resultTier = 2,
                secondItemTier = null,
                dustCost = 100,
                coresCost = 0,
                goldCost = 20_000_000,
            ),
        )
        assertThat(player.cores).isEqualTo(1)
    }

    @Test
    fun `fusion bonus 3 skips the gold cost and the classification lookup`() {
        val player = FakeForgePlayer(wallet = 0, cores = 1)
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 9500)))

        val result = application.apply(forge, player, fusionCommand(usedCore = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 3,
                coreCount = 1,
                resultTier = 2,
                secondItemTier = null,
                dustCost = 100,
                coresCost = 1,
                goldCost = 0,
            ),
        )
        assertThat(player.wallet).isEqualTo(0)
    }

    @Test
    fun `fusion bonus 4 keeps the second item one tier lower`() {
        val player = FakeForgePlayer(wallet = 30_000_000)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 9525)))

        val result = application.apply(forge, player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 4,
                coreCount = 0,
                resultTier = 2,
                secondItemTier = 0,
                dustCost = 100,
                coresCost = 0,
                goldCost = 20_000_000,
            ),
        )
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 2, 100 to 0)
    }

    @Test
    fun `fusion bonus 5 keeps the second item unchanged`() {
        val player = FakeForgePlayer(wallet = 30_000_000)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 9550)))

        val result = application.apply(forge, player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 5,
                coreCount = 0,
                resultTier = 2,
                secondItemTier = 1,
                dustCost = 100,
                coresCost = 0,
                goldCost = 20_000_000,
            ),
        )
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 2, 100 to 1)
    }

    @Test
    fun `fusion bonus 6 keeps the second item one tier higher`() {
        val player = FakeForgePlayer(wallet = 30_000_000)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 9950)))

        val result = application.apply(forge, player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 6,
                coreCount = 0,
                resultTier = 2,
                secondItemTier = 2,
                dustCost = 100,
                coresCost = 0,
                goldCost = 20_000_000,
            ),
        )
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 2, 100 to 2)
    }

    @Test
    fun `fusion bonus 7 promotes the first item two tiers when inside the classification`() {
        val player = FakeForgePlayer(wallet = 40_000_000)
        player.classifications.add(classification4())
        addFusionItems(player, tier = 2)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 9975)))

        val result = application.apply(forge, player, fusionCommand(tier = 2))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 7,
                coreCount = 0,
                resultTier = 4,
                secondItemTier = null,
                dustCost = 100,
                coresCost = 0,
                goldCost = 35_000_000,
            ),
        )
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 4)
    }

    @Test
    fun `fusion bonus 7 caps the first item at the classification tier`() {
        val player = FakeForgePlayer(wallet = 60_000_000)
        player.classifications.add(classification4())
        addFusionItems(player, tier = 3)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 10000)))

        val result = application.apply(forge, player, fusionCommand(tier = 3))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 7,
                coreCount = 0,
                resultTier = 4,
                secondItemTier = null,
                dustCost = 100,
                coresCost = 0,
                goldCost = 55_000_000,
            ),
        )
    }

    @Test
    fun `fusion convergence promotes the first item and spends dust and gold`() {
        val player = FakeForgePlayer(wallet = 200_000_000)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(100, 0)))

        val result = application.apply(forge, player, fusionCommand(convergence = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = false,
                convergence = true,
                bonus = 0,
                coreCount = 0,
                resultTier = 2,
                secondItemTier = null,
                dustCost = 130,
                coresCost = 0,
                goldCost = 110_000_000,
            ),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(70)
        assertThat(player.wallet).isEqualTo(90_000_000)
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 2)
    }

    @Test
    fun `fusion rejects a missing first item`() {
        val player = FakeForgePlayer()
        player.classifications.add(classification4())
        player.items.add(ForgeItem(id = 100, tier = 0, classification = 4))
        val application = ForgeApplication(QueueRandom(listOf(50, 0)))

        val result = application.apply(Forge(), player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.FIRST_ITEM_NOT_FOUND),
        )
    }

    @Test
    fun `fusion rejects a missing second item`() {
        val player = FakeForgePlayer()
        player.classifications.add(classification4())
        player.items.add(ForgeItem(id = 100, tier = 1, classification = 4))
        val application = ForgeApplication(QueueRandom(listOf(50, 0)))

        val result = application.apply(Forge(), player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.SECOND_ITEM_NOT_FOUND),
        )
    }

    @Test
    fun `fusion rejects insufficient dust`() {
        val player = FakeForgePlayer()
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(99)
        val application = ForgeApplication(QueueRandom(listOf(50, 0)))

        val result = application.apply(forge, player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.NOT_ENOUGH_DUST),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(99)
    }

    @Test
    fun `fusion rejects insufficient cores`() {
        val player = FakeForgePlayer(cores = 0)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 0)))

        val result = application.apply(forge, player, fusionCommand(usedCore = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.NOT_ENOUGH_CORES),
        )
    }

    @Test
    fun `fusion rejects an unknown classification`() {
        val player = FakeForgePlayer()
        addFusionItems(player, classification = 9)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 0)))

        val result = application.apply(forge, player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.CLASSIFICATION_NOT_FOUND),
        )
    }

    @Test
    fun `fusion rejects a missing target tier`() {
        val player = FakeForgePlayer(wallet = 100_000_000)
        player.classifications.add(classification4())
        addFusionItems(player, tier = 4)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 0)))

        val result = application.apply(forge, player, fusionCommand(tier = 4))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.TIER_NOT_FOUND),
        )
    }

    @Test
    fun `fusion rejects insufficient money`() {
        val player = FakeForgePlayer(wallet = 0, bank = 0)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 0)))

        val result = application.apply(forge, player, fusionCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.NOT_ENOUGH_MONEY),
        )
    }

    private fun transferCommand(
        donorItem: Int = 100,
        tier: Int = 2,
        receiveItem: Int = 200,
        convergence: Boolean = false,
    ) = ForgeEnterCommand(
        actionType = ForgeAction_t.TRANSFER,
        convergence = convergence,
        firstItem = donorItem,
        tier = tier,
        secondItem = receiveItem,
    )

    private fun addTransferItems(
        player: FakeForgePlayer,
        donorTier: Int = 2,
        donorClassification: Int = 4,
        receiveClassification: Int = 4,
    ) {
        player.itemClassifications[100] = donorClassification
        player.itemClassifications[200] = receiveClassification
        player.items.add(ForgeItem(id = 100, tier = donorTier, classification = donorClassification))
        player.items.add(ForgeItem(id = 200, tier = 0, classification = receiveClassification))
    }

    @Test
    fun `transfer lowers the donor tier by one and spends dust cores and gold`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1)
        player.classifications.add(classification4())
        addTransferItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, transferCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Transfer(
                convergence = false,
                resultTier = 1,
                dustCost = 100,
                coresCost = 1,
                goldCost = 20_000_000,
            ),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(100)
        assertThat(player.cores).isEqualTo(0)
        assertThat(player.wallet).isEqualTo(10_000_000)
        assertThat(player.items.map { it.id to it.tier }).containsExactly(200 to 1)
    }

    @Test
    fun `convergence transfer keeps the donor tier and spends more`() {
        val player = FakeForgePlayer(wallet = 200_000_000, cores = 2)
        player.classifications.add(classification4())
        addTransferItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, transferCommand(convergence = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Transfer(
                convergence = true,
                resultTier = 2,
                dustCost = 160,
                coresCost = 2,
                goldCost = 165_000_000,
            ),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(40)
        assertThat(player.cores).isEqualTo(0)
        assertThat(player.wallet).isEqualTo(35_000_000)
        assertThat(player.items.map { it.id to it.tier }).containsExactly(200 to 2)
    }

    @Test
    fun `transfer rejects a tier zero donor without convergence`() {
        val player = FakeForgePlayer()
        player.classifications.add(classification4())
        addTransferItems(player, donorTier = 0)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(Forge(), player, transferCommand(tier = 0))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.DONOR_TIER_ZERO),
        )
    }

    @Test
    fun `transfer rejects mismatched classifications`() {
        val player = FakeForgePlayer()
        player.classifications.add(classification4())
        addTransferItems(player, receiveClassification = 5)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(Forge(), player, transferCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.CLASSIFICATION_MISMATCH),
        )
    }

    @Test
    fun `transfer rejects a missing donor item`() {
        val player = FakeForgePlayer()
        player.classifications.add(classification4())
        player.items.add(ForgeItem(id = 200, tier = 0, classification = 4))
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(Forge(), player, transferCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.DONOR_ITEM_NOT_FOUND),
        )
    }

    @Test
    fun `transfer rejects a missing receive item`() {
        val player = FakeForgePlayer()
        player.classifications.add(classification4())
        player.items.add(ForgeItem(id = 100, tier = 2, classification = 4))
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(Forge(), player, transferCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.RECEIVE_ITEM_NOT_FOUND),
        )
    }

    @Test
    fun `transfer rejects insufficient dust`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1)
        player.classifications.add(classification4())
        addTransferItems(player)
        val forge = Forge()
        forge.setForgeDusts(99)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, transferCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.NOT_ENOUGH_DUST),
        )
    }

    @Test
    fun `transfer rejects insufficient cores`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 0)
        player.classifications.add(classification4())
        addTransferItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, transferCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.NOT_ENOUGH_CORES),
        )
    }

    @Test
    fun `transfer rejects insufficient money`() {
        val player = FakeForgePlayer(wallet = 0, cores = 1)
        player.classifications.add(classification4())
        addTransferItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, transferCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.NOT_ENOUGH_MONEY),
        )
    }

    @Test
    fun `transfer rejects a missing result tier`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1)
        player.classifications.add(classification4())
        addTransferItems(player, donorTier = 1)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, transferCommand(tier = 1))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.TIER_NOT_FOUND),
        )
    }

    private fun conversionCommand(actionType: ForgeAction_t) = ForgeEnterCommand(actionType = actionType)

    @Test
    fun `dust to slivers spends dust and grants slivers`() {
        val player = FakeForgePlayer(slivers = 0)
        val forge = Forge()
        forge.setForgeDusts(60)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, conversionCommand(ForgeAction_t.DUSTTOSLIVERS))

        assertThat(result).isEqualTo(ForgeApplyResult.Conversion(ForgeAction_t.DUSTTOSLIVERS, 60, 3))
        assertThat(forge.getForgeDusts()).isEqualTo(0)
        assertThat(player.slivers).isEqualTo(3)
    }

    @Test
    fun `dust to slivers rejects insufficient dust`() {
        val player = FakeForgePlayer(slivers = 0)
        val forge = Forge()
        forge.setForgeDusts(59)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, conversionCommand(ForgeAction_t.DUSTTOSLIVERS))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.DUSTTOSLIVERS, ForgeRejection.NOT_ENOUGH_DUST),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(59)
        assertThat(player.slivers).isEqualTo(0)
    }

    @Test
    fun `slivers to cores spends slivers and grants a core`() {
        val player = FakeForgePlayer(slivers = 50, cores = 0)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(Forge(), player, conversionCommand(ForgeAction_t.SLIVERSTOCORES))

        assertThat(result).isEqualTo(ForgeApplyResult.Conversion(ForgeAction_t.SLIVERSTOCORES, 50, 1))
        assertThat(player.slivers).isEqualTo(0)
        assertThat(player.cores).isEqualTo(1)
    }

    @Test
    fun `slivers to cores rejects insufficient slivers`() {
        val player = FakeForgePlayer(slivers = 49, cores = 0)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(Forge(), player, conversionCommand(ForgeAction_t.SLIVERSTOCORES))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.SLIVERSTOCORES, ForgeRejection.NOT_ENOUGH_SLIVERS),
        )
        assertThat(player.slivers).isEqualTo(49)
        assertThat(player.cores).isEqualTo(0)
    }

    @Test
    fun `increase limit spends level minus seventy five dust`() {
        val player = FakeForgePlayer()
        val forge = Forge()
        forge.setForgeDusts(100)
        forge.addForgeDustLevel(100)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, conversionCommand(ForgeAction_t.INCREASELIMIT))

        assertThat(result).isEqualTo(ForgeApplyResult.Conversion(ForgeAction_t.INCREASELIMIT, 25, 100))
        assertThat(forge.getForgeDusts()).isEqualTo(75)
        assertThat(forge.getForgeDustLevel()).isEqualTo(101)
    }

    @Test
    fun `increase limit rejects at the max dust cap`() {
        val player = FakeForgePlayer()
        val forge = Forge()
        forge.setForgeDusts(1000)
        forge.addForgeDustLevel(225)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, conversionCommand(ForgeAction_t.INCREASELIMIT))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.INCREASELIMIT, ForgeRejection.MAX_DUST_LEVEL_REACHED),
        )
        assertThat(forge.getForgeDustLevel()).isEqualTo(225)
    }

    @Test
    fun `increase limit rejects insufficient dust`() {
        val player = FakeForgePlayer()
        val forge = Forge()
        forge.setForgeDusts(4)
        forge.addForgeDustLevel(80)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, conversionCommand(ForgeAction_t.INCREASELIMIT))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.INCREASELIMIT, ForgeRejection.NOT_ENOUGH_DUST),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(4)
        assertThat(forge.getForgeDustLevel()).isEqualTo(80)
    }

    @Test
    fun `fusion aborts when the first result item cannot be added`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1, failAdds = true)
        player.classifications.add(classification4())
        addFusionItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(listOf(50, 0)))

        val result = application.apply(forge, player, fusionCommand(usedCore = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.FUSION, ForgeRejection.INTERNAL_ERROR),
        )
        assertThat(player.items).isEmpty()
        assertThat(forge.getForgeDusts()).isEqualTo(200)
        assertThat(player.cores).isEqualTo(1)
        assertThat(player.wallet).isEqualTo(30_000_000)
    }

    @Test
    fun `transfer aborts when the donor cannot be removed`() {
        val player = FakeForgePlayer(wallet = 30_000_000, cores = 1, failRemoves = true)
        player.classifications.add(classification4())
        addTransferItems(player)
        val forge = Forge()
        forge.setForgeDusts(200)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, transferCommand())

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.TRANSFER, ForgeRejection.INTERNAL_ERROR),
        )
        assertThat(player.items.map { it.id to it.tier }).containsExactly(100 to 2, 200 to 0)
        assertThat(forge.getForgeDusts()).isEqualTo(200)
        assertThat(player.cores).isEqualTo(1)
        assertThat(player.wallet).isEqualTo(30_000_000)
    }

    @Test
    fun `dust to slivers aborts when the sliver item cannot be added`() {
        val player = FakeForgePlayer(failAdds = true)
        val forge = Forge()
        forge.setForgeDusts(60)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(forge, player, conversionCommand(ForgeAction_t.DUSTTOSLIVERS))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.DUSTTOSLIVERS, ForgeRejection.INTERNAL_ERROR),
        )
        assertThat(forge.getForgeDusts()).isEqualTo(60)
        assertThat(player.slivers).isEqualTo(0)
    }

    @Test
    fun `slivers to cores aborts when the core item cannot be added`() {
        val player = FakeForgePlayer(slivers = 50, failAdds = true)
        val application = ForgeApplication(QueueRandom(emptyList()))

        val result = application.apply(Forge(), player, conversionCommand(ForgeAction_t.SLIVERSTOCORES))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Failure(ForgeAction_t.SLIVERSTOCORES, ForgeRejection.INTERNAL_ERROR),
        )
        assertThat(player.slivers).isEqualTo(0)
        assertThat(player.cores).isEqualTo(0)
    }
}
