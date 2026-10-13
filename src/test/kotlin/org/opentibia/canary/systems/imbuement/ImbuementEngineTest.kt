package org.opentibia.canary.systems.imbuement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat

/**
 * Transcript tests for the imbuement engine.
 *
 * Expectations are transcribed from Canary's
 * src/creatures/players/imbuements/imbuements.cpp (`checkImbuementDecay`,
 * `canDecayImbuement`, `startImbuementDecay`, `stopImbuementDecay`),
 * src/items/item.cpp (`getImbuementInfo`, `setImbuement`, `getFreeImbuementSlot`,
 * `canAddImbuement`, `hasImbuementCategoryId`), src/items/item.hpp
 * (`decayImbuementTime`, `clearImbuement`, `hasImbuementType`) and
 * src/creatures/players/player.cpp (`applyScrollImbuement`, `onApplyImbuement`,
 * `onClearImbuement`, `clearAllImbuements`, `addItemImbuementStats`,
 * `removeItemImbuementStats`).
 */
class ImbuementEngineTest {

    private val imbuements = Imbuements().apply {
        assertThat(loadFromXml(TEST_XML)).isTrue()
    }
    private val engine = ImbuementEngine(imbuements)

    private fun byName(name: String, base: Int): Imbuement =
        imbuements.imbuementMap.values.single { it.name == name && it.baseId == base }

    private fun strike(tier: Int): Imbuement = byName("Strike", tier)

    private fun ImbuementItem.equipTo(player: FakeImbuementPlayer) {
        parent = player
        holdingPlayer = player
    }

    @Test
    fun `setImbuement packs duration and id and getImbuementInfo decodes them`() {
        val item = ImbuementItem(id = 2400, imbuementSlot = 1)
        val strike = strike(1)

        item.setImbuement(0, strike.id, 72000)

        assertThat(item.getImbuementAttribute(0)).isEqualTo((72000L shl 8) or strike.id.toLong())
        val info = engine.getImbuementInfo(item, 0)
        assertThat(info?.imbuement).isEqualTo(strike)
        assertThat(info?.duration).isEqualTo(72000)
    }

    @Test
    fun `setImbuement with zero duration stores zero and reports no imbuement`() {
        val item = ImbuementItem(id = 2400, imbuementSlot = 1)

        item.setImbuement(0, strike(1).id, 0)

        assertThat(item.getImbuementAttribute(0)).isEqualTo(0L)
        assertThat(engine.getImbuementInfo(item, 0)).isNull()
    }

    @Test
    fun `getImbuementInfo ignores slots without a custom attribute`() {
        val item = ImbuementItem(id = 2400, imbuementSlot = 1)

        assertThat(item.hasImbuementAttribute(0)).isFalse()
        assertThat(engine.getImbuementInfo(item, 0)).isNull()
    }

    @Test
    fun `getFreeImbuementSlot returns the first empty slot`() {
        val item = ImbuementItem(id = 2400, imbuementSlot = 3)
        item.setImbuement(0, strike(1).id, 72000)
        item.setImbuement(2, strike(2).id, 72000)

        assertThat(engine.getFreeImbuementSlot(item)).isEqualTo(1)
        assertThat(engine.hasImbuements(item)).isTrue()

        item.setImbuement(1, strike(3).id, 72000)
        assertThat(engine.getFreeImbuementSlot(item)).isEqualTo(-1)
    }

    @Test
    fun `getFreeImbuementSlot is minus one for an item without slots`() {
        val item = ImbuementItem(id = 2400)

        assertThat(engine.getFreeImbuementSlot(item)).isEqualTo(-1)
        assertThat(engine.hasImbuements(item)).isFalse()
    }

    @Test
    fun `applyScrollImbuement stores the base duration and consumes the scroll`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 2,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()
        item.equipTo(player)
        val scroll = ImbuementItem(id = 51742)

        engine.applyScrollImbuement(item, scroll, player)

        val info = engine.getImbuementInfo(item, 0)
        assertThat(info?.imbuement).isEqualTo(strike(2))
        assertThat(info?.duration).isEqualTo(72000)
        assertThat(player.internalRemovedItems.map { it.first }).containsExactly(scroll)
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_DAMAGE]).isEqualTo(2500)
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_CHANCE]).isEqualTo(1000)
    }

    @Test
    fun `applyScrollImbuement uses the first free slot`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 2,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()
        item.setImbuement(0, byName("Swiftness", 1).id, 72000)

        engine.applyScrollImbuement(item, ImbuementItem(id = 51742), player)

        assertThat(engine.getImbuementInfo(item, 1)?.imbuement).isEqualTo(strike(2))
    }

    @Test
    fun `applyScrollImbuement refuses a tier above the item imbuement type`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 2,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 2)
        )
        val player = FakeImbuementPlayer()

        engine.applyScrollImbuement(item, ImbuementItem(id = 51462), player)

        assertThat(engine.hasImbuements(item)).isFalse()
        assertThat(player.internalRemovedItems).isEmpty()
        assertThat(player.resultMessages).containsExactly("This object cannot be imbued with that scroll.")
    }

    @Test
    fun `applyScrollImbuement refuses a category the item already has`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 2,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()
        item.setImbuement(0, strike(1).id, 72000)

        engine.applyScrollImbuement(item, ImbuementItem(id = 51742), player)

        assertThat(engine.getImbuementInfo(item, 1)).isNull()
        assertThat(player.internalRemovedItems).isEmpty()
        assertThat(player.resultMessages).containsExactly("This item already has this imbuement type.")
    }

    @Test
    fun `applyScrollImbuement ignores an unknown scroll item`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()

        engine.applyScrollImbuement(item, ImbuementItem(id = 9999), player)

        assertThat(engine.hasImbuements(item)).isFalse()
        assertThat(player.internalRemovedItems).isEmpty()
        assertThat(player.resultMessages).isEmpty()
    }

    @Test
    fun `applyScrollImbuement does not apply stats when the item is not equipped`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()

        engine.applyScrollImbuement(item, ImbuementItem(id = 51742), player)

        assertThat(engine.getImbuementInfo(item, 0)?.imbuement).isEqualTo(strike(2))
        assertThat(player.skillBonuses).isEmpty()
    }

    @Test
    fun `applyScrollImbuement aborts when the scroll cannot be removed`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(internalRemoveItemResult = false)

        engine.applyScrollImbuement(item, ImbuementItem(id = 51742), player)

        assertThat(engine.hasImbuements(item)).isFalse()
    }

    @Test
    fun `onApplyImbuement charges the base price and consumes the materials`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(money = 10000)
        item.equipTo(player)
        player.itemCounts[11444] = 20

        engine.onApplyImbuement(strike(1), item, 0, player)

        assertThat(engine.getImbuementInfo(item, 0)?.duration).isEqualTo(72000)
        assertThat(player.removedMoney).containsExactly(5000)
        assertThat(player.removedItemTypes).containsExactly(11444 to 20)
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_DAMAGE]).isEqualTo(1500)
    }

    @Test
    fun `onApplyImbuement refuses when the materials are missing`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(money = 10000)

        engine.onApplyImbuement(strike(1), item, 0, player)

        assertThat(engine.hasImbuements(item)).isFalse()
        assertThat(player.removedMoney).isEmpty()
        assertThat(player.resultMessages).containsExactly("You don't have all necessary items.")
    }

    @Test
    fun `onApplyImbuement refuses when the player cannot pay`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(money = 10, removeMoneyResult = false)
        player.itemCounts[11444] = 20

        engine.onApplyImbuement(strike(1), item, 0, player)

        assertThat(engine.hasImbuements(item)).isFalse()
        assertThat(player.removedItemTypes).isEmpty()
        assertThat(player.resultMessages).containsExactly("You don't have 5000 gold coins.")
    }

    @Test
    fun `onApplyImbuement withdraws materials missing from the inventory from the stash`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(money = 10000)
        player.stashCounts[11444] = 20

        engine.onApplyImbuement(strike(1), item, 0, player)

        assertThat(player.removedItemTypes).isEmpty()
        assertThat(player.withdrawnItems).containsExactly(11444 to 20)
        assertThat(player.textMessages).containsExactly("Using 20x item 11444 from your stash. ")
        assertThat(engine.getImbuementInfo(item, 0)?.imbuement).isEqualTo(strike(1))
    }

    @Test
    fun `onApplyImbuement refuses an invalid slot selection`() {
        val item = ImbuementItem(id = 2400, imbuementSlot = 1)
        val player = FakeImbuementPlayer()

        engine.onApplyImbuement(strike(1), item, 1, player)

        assertThat(engine.hasImbuements(item)).isFalse()
        assertThat(player.resultMessages).containsExactly("Invalid slot selection.")
    }

    @Test
    fun `onClearImbuement charges the remove cost and clears the slot`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(money = 20000)
        item.equipTo(player)
        item.setImbuement(0, strike(1).id, 72000)
        engine.addItemImbuementStats(strike(1), player)

        engine.onClearImbuement(item, 0, player)

        assertThat(player.removedMoney).containsExactly(15000)
        assertThat(engine.getImbuementInfo(item, 0)).isNull()
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_DAMAGE]).isEqualTo(0)
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_CHANCE]).isEqualTo(0)
        assertThat(player.trackerUpdates).isEqualTo(1)
    }

    @Test
    fun `onClearImbuement keeps the imbuement when the player cannot pay`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(money = 10, removeMoneyResult = false)
        item.equipTo(player)
        item.setImbuement(0, strike(1).id, 72000)

        engine.onClearImbuement(item, 0, player)

        assertThat(engine.getImbuementInfo(item, 0)?.imbuement).isEqualTo(strike(1))
        assertThat(player.resultMessages).containsExactly("You don't have 15000 gold coins.")
        assertThat(player.trackerUpdates).isEqualTo(1)
    }

    @Test
    fun `onClearImbuement reports an empty slot`() {
        val item = ImbuementItem(id = 2400, imbuementSlot = 1)
        val player = FakeImbuementPlayer()

        engine.onClearImbuement(item, 0, player)

        assertThat(player.resultMessages)
            .containsExactly("An error occurred, please reopen imbuement window.")
    }

    @Test
    fun `clearAllImbuements clears every slot and removes the stats`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 2,
            imbuementTypes = mapOf(
                ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3,
                ImbuementTypes_t.IMBUEMENT_INCREASE_SPEED to 3
            )
        )
        val player = FakeImbuementPlayer()
        item.equipTo(player)
        item.setImbuement(0, strike(1).id, 72000)
        item.setImbuement(1, byName("Swiftness", 1).id, 72000)
        engine.addItemImbuementStats(strike(1), player)
        engine.addItemImbuementStats(byName("Swiftness", 1), player)

        val result = engine.clearAllImbuements(item, player)

        assertThat(result).isTrue()
        assertThat(engine.hasImbuements(item)).isFalse()
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_DAMAGE]).isEqualTo(0)
        assertThat(player.speedChanges).containsExactly(10, -10)
    }

    @Test
    fun `clearAllImbuements clears slots without removing stats when the item is not equipped`() {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()
        item.setImbuement(0, strike(1).id, 72000)

        val result = engine.clearAllImbuements(item, player)

        assertThat(result).isTrue()
        assertThat(engine.hasImbuements(item)).isFalse()
        assertThat(player.skillBonuses).isEmpty()
    }

    @Test
    fun `clearAllImbuements refuses items without imbuements or slots`() {
        val player = FakeImbuementPlayer()
        val empty = ImbuementItem(id = 2400, imbuementSlot = 1)
        val noSlots = ImbuementItem(id = 2400)

        assertThat(engine.clearAllImbuements(empty, player)).isFalse()
        assertThat(engine.clearAllImbuements(noSlots, player)).isFalse()
        assertThat(player.textMessages).containsExactly("Sorry, not possible.", "Sorry, not possible.")
    }

    @Test
    fun `addItemImbuementStats applies skill stat speed and capacity bonuses`() {
        val player = FakeImbuementPlayer(capacity = 200)

        engine.addItemImbuementStats(byName("Vampirism", 1), player)
        engine.addItemImbuementStats(byName("Epiphany", 1), player)
        engine.addItemImbuementStats(byName("Swiftness", 3), player)
        engine.addItemImbuementStats(byName("Featherweight", 2), player)

        assertThat(player.skillBonuses[Skill.SKILL_LIFE_LEECH_AMOUNT]).isEqualTo(500)
        assertThat(player.skillBonuses[Skill.SKILL_LIFE_LEECH_CHANCE]).isEqualTo(100)
        assertThat(player.statBonuses[Stat.STAT_MAGICPOINTS]).isEqualTo(1)
        assertThat(player.speedChanges).containsExactly(30)
        assertThat(player.bonusCapacity).isEqualTo(16)
        assertThat(player.statsSent).isEqualTo(3)
        assertThat(player.skillsSent).isEqualTo(3)
    }

    @Test
    fun `removeItemImbuementStats negates the bonuses and zeroes the capacity`() {
        val player = FakeImbuementPlayer(capacity = 200)

        engine.removeItemImbuementStats(byName("Vampirism", 1), player)
        engine.removeItemImbuementStats(byName("Epiphany", 1), player)
        engine.removeItemImbuementStats(byName("Swiftness", 3), player)
        engine.removeItemImbuementStats(byName("Featherweight", 2), player)

        assertThat(player.skillBonuses[Skill.SKILL_LIFE_LEECH_AMOUNT]).isEqualTo(-500)
        assertThat(player.skillBonuses[Skill.SKILL_LIFE_LEECH_CHANCE]).isEqualTo(-100)
        assertThat(player.statBonuses[Stat.STAT_MAGICPOINTS]).isEqualTo(-1)
        assertThat(player.speedChanges).containsExactly(-30)
        assertThat(player.bonusCapacity).isEqualTo(0)
        assertThat(player.statsSent).isEqualTo(3)
        assertThat(player.skillsSent).isEqualTo(3)
    }

    @Test
    fun `an imbuement without bonuses sends no stat updates`() {
        val player = FakeImbuementPlayer()

        engine.addItemImbuementStats(byName("Blank", 1), player)

        assertThat(player.skillBonuses).isEmpty()
        assertThat(player.statBonuses).isEmpty()
        assertThat(player.speedChanges).isEmpty()
        assertThat(player.bonusCapacity).isEqualTo(0)
        assertThat(player.statsSent).isZero()
        assertThat(player.skillsSent).isZero()
    }

    @Test
    fun `checkImbuementDecay subtracts the elapsed seconds from the duration`() {
        var now = 1_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engine = ImbuementEngine(imbuements, decay)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()
        item.equipTo(player)
        item.setImbuement(0, strike(1).id, 100)
        decay.startImbuementDecay(item)

        now += 5_000
        decay.checkImbuementDecay()

        assertThat(engine.getImbuementInfo(item, 0)?.duration).isEqualTo(95)
    }

    @Test
    fun `checkImbuementDecay removes the stats when the duration reaches zero`() {
        var now = 1_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engine = ImbuementEngine(imbuements, decay)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()
        item.equipTo(player)
        item.setImbuement(0, strike(1).id, 10)
        engine.addItemImbuementStats(strike(1), player)
        decay.startImbuementDecay(item)

        now += 10_000
        decay.checkImbuementDecay()

        assertThat(engine.getImbuementInfo(item, 0)).isNull()
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_DAMAGE]).isEqualTo(0)
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_CHANCE]).isEqualTo(0)
    }

    @Test
    fun `checkImbuementDecay skips aggressive imbuements in a protection zone`() {
        var now = 1_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engine = ImbuementEngine(imbuements, decay)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(inProtectionZone = true)
        item.equipTo(player)
        item.setImbuement(0, strike(1).id, 100)
        decay.startImbuementDecay(item)

        now += 5_000
        decay.checkImbuementDecay()

        assertThat(engine.getImbuementInfo(item, 0)?.duration).isEqualTo(100)
    }

    @Test
    fun `checkImbuementDecay skips aggressive imbuements outside a fight`() {
        var now = 1_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engine = ImbuementEngine(imbuements, decay)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer(inFight = false)
        item.equipTo(player)
        item.setImbuement(0, strike(1).id, 100)
        decay.startImbuementDecay(item)

        now += 5_000
        decay.checkImbuementDecay()

        assertThat(engine.getImbuementInfo(item, 0)?.duration).isEqualTo(100)
    }

    @Test
    fun `checkImbuementDecay skips aggressive imbuements stored in a container`() {
        var now = 1_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engine = ImbuementEngine(imbuements, decay)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()
        item.parent = Any()
        item.parentIsContainer = true
        item.holdingPlayer = player
        item.setImbuement(0, strike(1).id, 100)
        decay.startImbuementDecay(item)

        now += 5_000
        decay.checkImbuementDecay()

        assertThat(engine.getImbuementInfo(item, 0)?.duration).isEqualTo(100)
    }

    @Test
    fun `checkImbuementDecay decays non aggressive imbuements while equipped and idle`() {
        var now = 1_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engine = ImbuementEngine(imbuements, decay)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_INCREASE_SPEED to 3)
        )
        val player = FakeImbuementPlayer(inFight = false)
        item.equipTo(player)
        item.setImbuement(0, byName("Swiftness", 1).id, 100)
        decay.startImbuementDecay(item)

        now += 5_000
        decay.checkImbuementDecay()

        assertThat(engine.getImbuementInfo(item, 0)?.duration).isEqualTo(95)
    }

    @Test
    fun `checkImbuementDecay skips non aggressive imbuements in another container`() {
        var now = 1_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engine = ImbuementEngine(imbuements, decay)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_INCREASE_SPEED to 3)
        )
        val player = FakeImbuementPlayer(inFight = false)
        item.parent = Any()
        item.holdingPlayer = player
        item.setImbuement(0, byName("Swiftness", 1).id, 100)
        decay.startImbuementDecay(item)

        now += 5_000
        decay.checkImbuementDecay()

        assertThat(engine.getImbuementInfo(item, 0)?.duration).isEqualTo(100)
    }

    @Test
    fun `checkImbuementDecay forgets items without a holding player`() {
        var now = 1_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engine = ImbuementEngine(imbuements, decay)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        item.setImbuement(0, strike(1).id, 100)
        decay.startImbuementDecay(item)

        now += 5_000
        decay.checkImbuementDecay()

        assertThat(decay.trackedItemCount).isZero()
        assertThat(engine.getImbuementInfo(item, 0)?.duration).isEqualTo(100)
    }

    @Test
    fun `startImbuementDecay tracks one entry per item and stop removes it`() {
        val decay = ImbuementDecay(imbuements)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        item.setImbuement(0, strike(1).id, 100)

        decay.startImbuementDecay(item)
        decay.startImbuementDecay(item)
        assertThat(decay.trackedItemCount).isEqualTo(1)

        decay.stopImbuementDecay(item)
        assertThat(decay.trackedItemCount).isZero()
    }

    @Test
    fun `startImbuementDecay ignores items without imbuements`() {
        val decay = ImbuementDecay(imbuements)
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )

        decay.startImbuementDecay(item)

        assertThat(decay.trackedItemCount).isZero()
    }
}

private const val TEST_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <base id="2" name="Intricate" price="30000" protectionPrice="30000" percent="70" removecost="15000" duration="72000"/>
    <base id="3" name="Powerful" price="200000" protectionPrice="50000" percent="50" removecost="15000" duration="72000"/>

    <category id="1" name="Life Leech" agressive="1"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <category id="10" name="Increase Speed" agressive="0"/>
    <category id="16" name="Skillboost (Magic Level)" agressive="1"/>
    <category id="17" name="Increase Capacity" agressive="0"/>

    <imbuement name="Vampirism" base="1" category="1" iconid="46" premium="0" storage="0">
        <attribute key="description" value="Converts 5% of damage to HP with a chance of 100%."/>
        <attribute key="effect" type="skill" value="lifeleech" bonus="500" chance="100"/>
        <attribute key="item" value="9685" count="25"/>
    </imbuement>
    <imbuement name="Strike" base="1" category="3" iconid="1" premium="0" storage="0">
        <attribute key="description" value="Raises crit hit damage by 15% and crit hit chance by 10%."/>
        <attribute key="effect" type="skill" value="critical" bonus="1500" chance="1000"/>
        <attribute key="item" value="11444" count="20"/>
    </imbuement>
    <imbuement name="Strike" base="2" category="3" iconid="1" premium="1" storage="0">
        <attribute key="description" value="Raises crit hit damage by 25% and crit hit chance by 10%."/>
        <attribute key="effect" type="skill" value="critical" bonus="2500" chance="1000"/>
        <attribute key="item" value="11444" count="20"/>
        <attribute key="scroll" value="51742"/>
    </imbuement>
    <imbuement name="Strike" base="3" category="3" iconid="1" premium="1" storage="0">
        <attribute key="description" value="Raises crit hit damage by 50% and crit hit chance by 10%."/>
        <attribute key="effect" type="skill" value="critical" bonus="5000" chance="1000"/>
        <attribute key="item" value="11444" count="20"/>
        <attribute key="scroll" value="51462"/>
    </imbuement>
    <imbuement name="Swiftness" base="1" category="10" iconid="73" premium="0" storage="0">
        <attribute key="description" value="Raises walking speed by 10."/>
        <attribute key="effect" type="speed" value="10"/>
        <attribute key="item" value="17458" count="15"/>
    </imbuement>
    <imbuement name="Swiftness" base="3" category="10" iconid="73" premium="1" storage="0">
        <attribute key="description" value="Raises walking speed by 30."/>
        <attribute key="effect" type="speed" value="30"/>
        <attribute key="item" value="17458" count="15"/>
        <attribute key="scroll" value="51463"/>
    </imbuement>
    <imbuement name="Epiphany" base="1" category="16" iconid="64" premium="0" storage="0">
        <attribute key="description" value="Raises magic level by 1."/>
        <attribute key="effect" type="skill" value="magicpoints" bonus="1"/>
        <attribute key="item" value="9635" count="25"/>
    </imbuement>
    <imbuement name="Featherweight" base="1" category="17" iconid="76" premium="0" storage="0">
        <attribute key="description" value="Raises capacity by 3."/>
        <attribute key="effect" type="capacity" value="3"/>
        <attribute key="item" value="25694" count="20"/>
    </imbuement>
    <imbuement name="Featherweight" base="2" category="17" iconid="76" premium="1" storage="0">
        <attribute key="description" value="Raises capacity by 8."/>
        <attribute key="effect" type="capacity" value="8"/>
        <attribute key="item" value="25694" count="20"/>
        <attribute key="item" value="25702" count="10"/>
        <attribute key="scroll" value="51732"/>
    </imbuement>
    <imbuement name="Blank" base="1" category="3" iconid="2" premium="0" storage="0">
        <attribute key="description" value="No effect."/>
        <attribute key="item" value="1" count="1"/>
    </imbuement>
</imbuements>
"""
