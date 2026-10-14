package org.opentibia.canary.systems.forge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.io.xml.XmlItemLoader
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.forge.ForgeEnterCommand

/**
 * Transcript tests proving [ForgeApplication] fusion/transfer costs resolve from
 * the [ItemClassifications] store wired into [PlayerForgeAdapter], mirroring:
 *
 *  - the `g_game().getItemsClassifications()` loops in `Player::forgeFuseItems`
 *    (src/creatures/players/player.cpp:11297) and `Player::forgeTransferItemTier`
 *    (src/creatures/players/player.cpp:11589)
 *  - classification 4 price table of data/scripts/systems/item_tiers.lua
 *
 * Source of truth: ../.projects/canary
 */
class ForgeClassificationCostsTest {

    private class Wallet(var amount: Long) {
        fun get(): Long = amount

        fun remove(value: Long): Boolean {
            if (amount < value) {
                return false
            }
            amount -= value
            return true
        }
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun tieredItem(itemId: Int, tier: Int, count: Int = 1): Item =
        Item.create(itemId, count).apply { setAttribute(ItemAttribute.TIER, tier.toLong()) }

    private fun classification4(): ItemClassification = ItemClassification(4).apply {
        addTier(1, 1, 8_000_000, 55_000_000, 65_000_000)
        addTier(2, 2, 20_000_000, 110_000_000, 165_000_000)
        addTier(3, 5, 40_000_000, 170_000_000, 375_000_000)
    }

    private fun registry(): ItemClassifications =
        ItemClassifications().apply { addClassification(classification4()) }

    private fun fusionCommand(
        tier: Int,
        convergence: Boolean = false,
        usedCore: Boolean = false,
    ) = ForgeEnterCommand(
        actionType = ForgeAction_t.FUSION,
        convergence = convergence,
        firstItem = 100,
        tier = tier,
        secondItem = 100,
        usedCore = usedCore,
        reduceTierLoss = false,
    )

    private fun transferCommand(tier: Int, convergence: Boolean = false) = ForgeEnterCommand(
        actionType = ForgeAction_t.TRANSFER,
        convergence = convergence,
        firstItem = 100,
        tier = tier,
        secondItem = 200,
        usedCore = false,
        reduceTierLoss = false,
    )

    private fun application(): ForgeApplication = ForgeApplication(ForgeRandom { minNumber, _ -> minNumber })

    @Test
    fun `the adapter exposes the registry store through getItemsClassifications`() {
        val store = registry()

        val adapter = PlayerForgeAdapter(hero(), itemClassifications = store)

        assertThat(adapter.getItemsClassifications()).isEqualTo(store.getAll())
    }

    @Test
    fun `fusion resolves the regular gold cost from the registry classification table`() {
        val player = hero()
        player.addItem(tieredItem(100, 1))
        player.addItem(tieredItem(100, 1))
        val wallet = Wallet(200_000_000)
        val adapter = PlayerForgeAdapter(
            player = player,
            itemClassifications = registry(),
            itemClassificationOf = { if (it == 100) 4 else 0 },
            money = wallet::get,
            moneyRemover = wallet::remove,
        )
        val forge = Forge()
        forge.setForgeDusts(200)

        val result = application().apply(forge, adapter, fusionCommand(tier = 1))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 0,
                coreCount = 0,
                resultTier = 2,
                secondItemTier = null,
                dustCost = 100,
                coresCost = 0,
                goldCost = 20_000_000,
            ),
        )
        assertThat(wallet.amount).isEqualTo(180_000_000)
    }

    @Test
    fun `fusion resolves the gold cost from the XML-loaded item type classification`() {
        val itemTypes = ItemTypeRegistry()
        XmlItemLoader.load(
            """
            <items>
                <item id="100" name="Sabre">
                    <attribute key="upgradeclassification" value="4"/>
                </item>
            </items>
            """.trimIndent(),
            itemTypes,
        )
        val player = hero()
        player.addItem(tieredItem(100, 1))
        player.addItem(tieredItem(100, 1))
        val wallet = Wallet(200_000_000)
        val adapter = PlayerForgeAdapter(
            player = player,
            itemClassifications = registry(),
            itemRegistry = itemTypes,
            money = wallet::get,
            moneyRemover = wallet::remove,
        )
        val forge = Forge()
        forge.setForgeDusts(200)

        val result = application().apply(forge, adapter, fusionCommand(tier = 1))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = false,
                bonus = 0,
                coreCount = 0,
                resultTier = 2,
                secondItemTier = null,
                dustCost = 100,
                coresCost = 0,
                goldCost = 20_000_000,
            ),
        )
        assertThat(wallet.amount).isEqualTo(180_000_000)
    }

    @Test
    fun `convergence fusion resolves the convergence gold cost from the registry`() {
        val player = hero()
        player.addItem(tieredItem(100, 1))
        player.addItem(tieredItem(100, 1))
        player.addItem(Item.create(ForgeItemIds.ITEM_FORGE_CORE, 1))
        val wallet = Wallet(200_000_000)
        val adapter = PlayerForgeAdapter(
            player = player,
            itemClassifications = registry(),
            itemClassificationOf = { if (it == 100) 4 else 0 },
            money = wallet::get,
            moneyRemover = wallet::remove,
        )
        val forge = Forge()
        forge.setForgeDusts(200)

        val result = application().apply(forge, adapter, fusionCommand(tier = 1, convergence = true, usedCore = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Fusion(
                success = true,
                convergence = true,
                bonus = 0,
                coreCount = 1,
                resultTier = 2,
                secondItemTier = null,
                dustCost = 130,
                coresCost = 0,
                goldCost = 110_000_000,
            ),
        )
        assertThat(wallet.amount).isEqualTo(90_000_000)
        assertThat(forge.getForgeDusts()).isEqualTo(70)
    }

    @Test
    fun `transfer resolves the regular gold and core costs from the registry`() {
        val player = hero()
        player.addItem(tieredItem(100, 2))
        player.addItem(tieredItem(200, 0))
        player.addItem(Item.create(ForgeItemIds.ITEM_FORGE_CORE, 1))
        val wallet = Wallet(200_000_000)
        val adapter = PlayerForgeAdapter(
            player = player,
            itemClassifications = registry(),
            itemClassificationOf = { if (it == 100 || it == 200) 4 else 0 },
            money = wallet::get,
            moneyRemover = wallet::remove,
        )
        val forge = Forge()
        forge.setForgeDusts(200)

        val result = application().apply(forge, adapter, transferCommand(tier = 2))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Transfer(
                convergence = false,
                resultTier = 1,
                dustCost = 100,
                coresCost = 1,
                goldCost = 20_000_000,
            ),
        )
        assertThat(wallet.amount).isEqualTo(180_000_000)
    }

    @Test
    fun `convergence transfer resolves the convergence gold and core costs from the registry`() {
        val player = hero()
        player.addItem(tieredItem(100, 2))
        player.addItem(tieredItem(200, 0))
        player.addItem(Item.create(ForgeItemIds.ITEM_FORGE_CORE, 2))
        val wallet = Wallet(200_000_000)
        val adapter = PlayerForgeAdapter(
            player = player,
            itemClassifications = registry(),
            itemClassificationOf = { if (it == 100 || it == 200) 4 else 0 },
            money = wallet::get,
            moneyRemover = wallet::remove,
        )
        val forge = Forge()
        forge.setForgeDusts(200)

        val result = application().apply(forge, adapter, transferCommand(tier = 2, convergence = true))

        assertThat(result).isEqualTo(
            ForgeApplyResult.Transfer(
                convergence = true,
                resultTier = 2,
                dustCost = 160,
                coresCost = 2,
                goldCost = 165_000_000,
            ),
        )
        assertThat(wallet.amount).isEqualTo(35_000_000)
        assertThat(forge.getForgeDusts()).isEqualTo(40)
    }
}
