package org.opentibia.canary.systems.wheel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.FeatureFlags
import org.opentibia.canary.network.protocol.wheel.DestroyGemCommand
import org.opentibia.canary.network.protocol.wheel.ImproveGemGradeCommand
import org.opentibia.canary.network.protocol.wheel.RevealGemCommand
import org.opentibia.canary.network.protocol.wheel.SwitchGemDomainCommand

/**
 * Transcribes the gem-economy costs of Canary's `PlayerWheel` gem handlers
 * (src/creatures/players/components/wheel/player_wheel.cpp:1132-1532) through
 * an injected [WheelPlayer] inventory/economy seam:
 *  - reveal: `getGemRevealCost` gold plus the vocation gem item consumption
 *    (player_wheel.cpp:1132-1150);
 *  - switch domain: `getGemRotateCost` gold (player_wheel.cpp:1287-1301);
 *  - improve grade: `getLesserGradeCost` / `getGreaterGradeCost` gold and
 *    fragments (player_wheel.cpp:1448-1532);
 *  - destroy: `normal_random` fragment refunds (player_wheel.cpp:1212-1260).
 */
class WheelGemEconomyTest {

    private val lesserGemItemId = 1001
    private val regularGemItemId = 1002
    private val greaterGemItemId = 1003

    private fun cyclingRandom(): WheelRandom {
        var seed = 0
        return WheelRandom { min, max -> min + (seed++ % (max - min + 1)) }
    }

    private val normalRandom = RecordingNormalRandom()
    private val application = WheelApplication(random = cyclingRandom(), normalRandom = normalRandom)

    private fun wheel() = PlayerWheel(IOWheel.VOCATION_KNIGHT_CIP, level = 250)

    private fun player() = RecordingWheelPlayer(
        gemIds = mapOf(
            WheelGemQuality.Lesser to lesserGemItemId,
            WheelGemQuality.Regular to regularGemItemId,
            WheelGemQuality.Greater to greaterGemItemId,
        ),
    )

    @Test
    fun `wheel config transcribes the canary atelier feature flags`() {
        val config = WheelConfig.fromFeatureFlags(
            FeatureFlags(
                wheelAtelierRevealGreaterCost = 11,
                wheelAtelierRevealLesserCost = 12,
                wheelAtelierRevealRegularCost = 13,
                wheelAtelierRotateGreaterCost = 14,
                wheelAtelierRotateLesserCost = 15,
                wheelAtelierRotateRegularCost = 16,
            ),
        )

        assertThat(config.revealGreaterCost).isEqualTo(11)
        assertThat(config.revealLesserCost).isEqualTo(12)
        assertThat(config.revealRegularCost).isEqualTo(13)
        assertThat(config.rotateGreaterCost).isEqualTo(14)
        assertThat(config.rotateLesserCost).isEqualTo(15)
        assertThat(config.rotateRegularCost).isEqualTo(16)
    }

    @Test
    fun `reveal charges the lesser atelier cost and consumes the gem item`() {
        val state = wheel()
        val player = player().apply { items[lesserGemItemId] = 1 }

        val result = application.applyGemAction(state, RevealGemCommand(WheelGemQuality.Lesser), player)

        assertThat(result).isInstanceOf(WheelGemActionResult.Applied::class.java)
        assertThat(player.removedMoney).containsExactly(125_000L)
        assertThat(player.removedItems).containsExactly(Triple(lesserGemItemId, 1, false))
        assertThat(state.gems.revealedGems).hasSize(1)
    }

    @Test
    fun `reveal charges the regular atelier cost and consumes the gem item`() {
        val state = wheel()
        val player = player().apply { items[regularGemItemId] = 1 }

        val result = application.applyGemAction(state, RevealGemCommand(WheelGemQuality.Regular), player)

        assertThat(result).isInstanceOf(WheelGemActionResult.Applied::class.java)
        assertThat(player.removedMoney).containsExactly(1_000_000L)
        assertThat(player.removedItems).containsExactly(Triple(regularGemItemId, 1, false))
        assertThat(state.gems.revealedGems).hasSize(1)
    }

    @Test
    fun `reveal charges the greater atelier cost and consumes the gem item`() {
        val state = wheel()
        val player = player().apply { items[greaterGemItemId] = 1 }

        val result = application.applyGemAction(state, RevealGemCommand(WheelGemQuality.Greater), player)

        assertThat(result).isInstanceOf(WheelGemActionResult.Applied::class.java)
        assertThat(player.removedMoney).containsExactly(6_000_000L)
        assertThat(player.removedItems).containsExactly(Triple(greaterGemItemId, 1, false))
        assertThat(state.gems.revealedGems).hasSize(1)
    }

    @Test
    fun `reveal without the gem item is rejected and charges nothing`() {
        val state = wheel()
        val player = player()

        val result = application.applyGemAction(state, RevealGemCommand(WheelGemQuality.Lesser), player)

        assertThat(result).isEqualTo(
            WheelGemActionResult.Rejected(WheelGemAction.Reveal, WheelGemActionRejection.MISSING_GEM_ITEM),
        )
        assertThat(player.itemChecks).containsExactly(Triple(lesserGemItemId, 1, false))
        assertThat(player.removedMoney).isEmpty()
        assertThat(player.removedItems).isEmpty()
        assertThat(state.gems.revealedGems).isEmpty()
    }

    @Test
    fun `reveal without enough gold is rejected and consumes nothing`() {
        val state = wheel()
        val player = player().apply {
            items[lesserGemItemId] = 1
            money = 0L
        }

        val result = application.applyGemAction(state, RevealGemCommand(WheelGemQuality.Lesser), player)

        assertThat(result).isEqualTo(
            WheelGemActionResult.Rejected(WheelGemAction.Reveal, WheelGemActionRejection.NOT_ENOUGH_MONEY),
        )
        assertThat(player.removedItems).isEmpty()
        assertThat(player.items[lesserGemItemId]).isEqualTo(1)
        assertThat(state.gems.revealedGems).isEmpty()
    }

    @Test
    fun `switch domain charges the lesser rotate cost`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Green, quality = WheelGemQuality.Lesser))
        val player = player()

        val result = application.applyGemAction(state, SwitchGemDomainCommand(0), player)

        assertThat(result).isInstanceOf(WheelGemActionResult.Applied::class.java)
        assertThat(player.removedMoney).containsExactly(125_000L)
        assertThat(state.gems.revealedGems[0].affinity).isEqualTo(WheelGemAffinity.Red)
    }

    @Test
    fun `switch domain charges the regular rotate cost`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Green, quality = WheelGemQuality.Regular))
        val player = player()

        application.applyGemAction(state, SwitchGemDomainCommand(0), player)

        assertThat(player.removedMoney).containsExactly(250_000L)
    }

    @Test
    fun `switch domain charges the greater rotate cost`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Green, quality = WheelGemQuality.Greater))
        val player = player()

        application.applyGemAction(state, SwitchGemDomainCommand(0), player)

        assertThat(player.removedMoney).containsExactly(500_000L)
    }

    @Test
    fun `switch domain without enough gold is rejected without rotating`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(affinity = WheelGemAffinity.Green, quality = WheelGemQuality.Greater))
        val player = player().apply { money = 0L }

        val result = application.applyGemAction(state, SwitchGemDomainCommand(0), player)

        assertThat(result).isEqualTo(
            WheelGemActionResult.Rejected(WheelGemAction.SwitchDomain, WheelGemActionRejection.NOT_ENOUGH_MONEY),
        )
        assertThat(player.removedMoney).isEmpty()
        assertThat(state.gems.revealedGems[0].affinity).isEqualTo(WheelGemAffinity.Green)
    }

    @Test
    fun `improve lesser grade one charges two million gold and five lesser fragments`() {
        val state = wheel()
        val player = player().apply { items[WheelItemIds.ITEM_LESSER_FRAGMENT] = 5 }

        val result = application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Lesser, 0), player)

        assertThat(result).isInstanceOf(WheelGemActionResult.Applied::class.java)
        assertThat(player.itemChecks).containsExactly(Triple(WheelItemIds.ITEM_LESSER_FRAGMENT, 5, true))
        assertThat(player.removedMoney).containsExactly(2_000_000L)
        assertThat(player.removedItems).containsExactly(Triple(WheelItemIds.ITEM_LESSER_FRAGMENT, 5, true))
        assertThat(state.gems.basicGrades[0]).isEqualTo(1)
    }

    @Test
    fun `improve lesser grade two charges five million gold and fifteen lesser fragments`() {
        val state = wheel()
        state.gems.basicGrades[0] = 1
        val player = player().apply { items[WheelItemIds.ITEM_LESSER_FRAGMENT] = 15 }

        application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Lesser, 0), player)

        assertThat(player.removedMoney).containsExactly(5_000_000L)
        assertThat(player.removedItems).containsExactly(Triple(WheelItemIds.ITEM_LESSER_FRAGMENT, 15, true))
        assertThat(state.gems.basicGrades[0]).isEqualTo(2)
    }

    @Test
    fun `improve lesser grade three charges thirty million gold and thirty lesser fragments`() {
        val state = wheel()
        state.gems.basicGrades[0] = 2
        val player = player().apply { items[WheelItemIds.ITEM_LESSER_FRAGMENT] = 30 }

        application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Lesser, 0), player)

        assertThat(player.removedMoney).containsExactly(30_000_000L)
        assertThat(player.removedItems).containsExactly(Triple(WheelItemIds.ITEM_LESSER_FRAGMENT, 30, true))
        assertThat(state.gems.basicGrades[0]).isEqualTo(3)
    }

    @Test
    fun `improve greater grade one charges five million gold and five greater fragments`() {
        val state = wheel()
        val player = player().apply { items[WheelItemIds.ITEM_GREATER_FRAGMENT] = 5 }

        val result = application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Greater, 0), player)

        assertThat(result).isInstanceOf(WheelGemActionResult.Applied::class.java)
        assertThat(player.itemChecks).containsExactly(Triple(WheelItemIds.ITEM_GREATER_FRAGMENT, 5, true))
        assertThat(player.removedMoney).containsExactly(5_000_000L)
        assertThat(player.removedItems).containsExactly(Triple(WheelItemIds.ITEM_GREATER_FRAGMENT, 5, true))
        assertThat(state.gems.supremeGrades[0]).isEqualTo(1)
    }

    @Test
    fun `improve greater grade two charges twelve million gold and fifteen greater fragments`() {
        val state = wheel()
        state.gems.supremeGrades[0] = 1
        val player = player().apply { items[WheelItemIds.ITEM_GREATER_FRAGMENT] = 15 }

        application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Greater, 0), player)

        assertThat(player.removedMoney).containsExactly(12_000_000L)
        assertThat(player.removedItems).containsExactly(Triple(WheelItemIds.ITEM_GREATER_FRAGMENT, 15, true))
        assertThat(state.gems.supremeGrades[0]).isEqualTo(2)
    }

    @Test
    fun `improve greater grade three charges seventy five million gold and thirty greater fragments`() {
        val state = wheel()
        state.gems.supremeGrades[0] = 2
        val player = player().apply { items[WheelItemIds.ITEM_GREATER_FRAGMENT] = 30 }

        application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Greater, 0), player)

        assertThat(player.removedMoney).containsExactly(75_000_000L)
        assertThat(player.removedItems).containsExactly(Triple(WheelItemIds.ITEM_GREATER_FRAGMENT, 30, true))
        assertThat(state.gems.supremeGrades[0]).isEqualTo(3)
    }

    @Test
    fun `improve grade without the fragments is rejected and charges nothing`() {
        val state = wheel()
        val player = player()

        val result = application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Lesser, 0), player)

        assertThat(result).isEqualTo(
            WheelGemActionResult.Rejected(WheelGemAction.ImproveGrade, WheelGemActionRejection.NOT_ENOUGH_FRAGMENTS),
        )
        assertThat(player.itemChecks).containsExactly(Triple(WheelItemIds.ITEM_LESSER_FRAGMENT, 5, true))
        assertThat(player.removedMoney).isEmpty()
        assertThat(player.removedItems).isEmpty()
        assertThat(state.gems.basicGrades[0]).isZero()
    }

    @Test
    fun `improve grade without enough gold is rejected without consuming fragments`() {
        val state = wheel()
        val player = player().apply {
            items[WheelItemIds.ITEM_LESSER_FRAGMENT] = 5
            money = 0L
        }

        val result = application.applyGemAction(state, ImproveGemGradeCommand(WheelFragmentType.Lesser, 0), player)

        assertThat(result).isEqualTo(
            WheelGemActionResult.Rejected(WheelGemAction.ImproveGrade, WheelGemActionRejection.NOT_ENOUGH_MONEY),
        )
        assertThat(player.removedItems).isEmpty()
        assertThat(player.items[WheelItemIds.ITEM_LESSER_FRAGMENT]).isEqualTo(5)
        assertThat(state.gems.basicGrades[0]).isZero()
    }

    @Test
    fun `destroy of a lesser gem refunds one to five lesser fragments`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(quality = WheelGemQuality.Lesser))
        val player = player()

        val result = application.applyGemAction(state, DestroyGemCommand(0), player)

        assertThat(result).isInstanceOf(WheelGemActionResult.Applied::class.java)
        assertThat(normalRandom.calls).containsExactly(1 to 5)
        assertThat(player.addedItems).containsExactly(WheelItemIds.ITEM_LESSER_FRAGMENT to 1)
        assertThat(state.gems.revealedGems).isEmpty()
    }

    @Test
    fun `destroy of a regular gem refunds two to ten lesser fragments`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(quality = WheelGemQuality.Regular))
        val player = player()

        application.applyGemAction(state, DestroyGemCommand(0), player)

        assertThat(normalRandom.calls).containsExactly(2 to 10)
        assertThat(player.addedItems).containsExactly(WheelItemIds.ITEM_LESSER_FRAGMENT to 2)
        assertThat(state.gems.revealedGems).isEmpty()
    }

    @Test
    fun `destroy of a greater gem refunds one to five greater fragments`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(quality = WheelGemQuality.Greater))
        val player = player()

        application.applyGemAction(state, DestroyGemCommand(0), player)

        assertThat(normalRandom.calls).containsExactly(1 to 5)
        assertThat(player.addedItems).containsExactly(WheelItemIds.ITEM_GREATER_FRAGMENT to 1)
        assertThat(state.gems.revealedGems).isEmpty()
    }

    @Test
    fun `destroy of a locked gem is rejected without a refund`() {
        val state = wheel()
        state.gems.revealedGems.add(PlayerWheelGem(quality = WheelGemQuality.Greater, locked = true))
        val player = player()

        val result = application.applyGemAction(state, DestroyGemCommand(0), player)

        assertThat(result).isEqualTo(
            WheelGemActionResult.Rejected(WheelGemAction.Destroy, WheelGemActionRejection.GEM_LOCKED),
        )
        assertThat(normalRandom.calls).isEmpty()
        assertThat(player.addedItems).isEmpty()
        assertThat(state.gems.revealedGems).hasSize(1)
    }
}

private class RecordingNormalRandom : WheelNormalRandom {
    val calls = mutableListOf<Pair<Int, Int>>()

    override fun normalRandom(minNumber: Int, maxNumber: Int): Int {
        calls += minNumber to maxNumber
        return minNumber
    }
}

private class RecordingWheelPlayer(
    private val gemIds: Map<WheelGemQuality, Int>,
) : WheelPlayer {
    val items = mutableMapOf<Int, Int>()
    var money: Long = 1_000_000_000L
    val removedMoney = mutableListOf<Long>()
    val removedItems = mutableListOf<Triple<Int, Int, Boolean>>()
    val addedItems = mutableListOf<Pair<Int, Int>>()
    val itemChecks = mutableListOf<Triple<Int, Int, Boolean>>()

    override fun getWheelGemId(quality: WheelGemQuality): Int = gemIds.getValue(quality)

    override fun hasItemCountById(itemId: Int, count: Int, checkStash: Boolean): Boolean {
        itemChecks += Triple(itemId, count, checkStash)
        return (items[itemId] ?: 0) >= count
    }

    override fun removeItemCountById(itemId: Int, count: Int, removeFromStash: Boolean): Boolean {
        if ((items[itemId] ?: 0) < count) {
            return false
        }
        items[itemId] = items.getValue(itemId) - count
        removedItems += Triple(itemId, count, removeFromStash)
        return true
    }

    override fun removeMoney(amount: Long): Boolean {
        if (money < amount) {
            return false
        }
        money -= amount
        removedMoney += amount
        return true
    }

    override fun internalPlayerAddItem(itemId: Int, count: Int): Boolean {
        items[itemId] = (items[itemId] ?: 0) + count
        addedItems += itemId to count
        return true
    }
}
