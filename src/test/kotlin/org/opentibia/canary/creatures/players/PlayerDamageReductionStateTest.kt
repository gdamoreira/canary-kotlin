package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.imbuement.Imbuement
import org.opentibia.canary.systems.imbuement.ImbuementInfo
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcribes the Player elemental damage-reduction pipeline
 * (src/creatures/players/player.cpp:750-752, 5739-5801):
 *
 * - `Player::getMitigation` (player.cpp:750-752)
 * - `Player::getFinalDamageReduction` (player.cpp:5739-5751)
 * - `Player::calculateDamageReductionFromEquipedItems` (player.cpp:5753-5760)
 * - `Player::calculateDamageReductionFromItem` (player.cpp:5762-5767)
 * - `Player::updateDamageReductionFromItemImbuement` (player.cpp:5769-5782)
 * - `Player::updateDamageReductionFromItemAbility` (player.cpp:5784-5797)
 * - `Player::calculateDamageReduction` (player.cpp:5799-5801)
 *
 * The item-type ability table is read from the port's [ItemType], and the
 * imbuement slot lookup (`Item::getImbuementInfo`, src/items/item.cpp:472) and
 * the wheel mitigation (`PlayerWheel::calculateMitigation`) are constructor
 * seams because both live outside this package (AGENTS.md 1.1).
 */
class PlayerDamageReductionStateTest {

    private fun newPlayer() = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun absorbItem(id: Int, combat: CombatType, percent: Int): Item {
        val type = ItemType(id = id, name = "Absorb $id")
        type.abilities = Abilities().apply { absorbPercent[combat.ordinal] = percent.toShort() }
        return Item(id, 1, type)
    }

    @Test
    fun `calculateDamageReduction applies the resistance multiplicatively`() {
        // Player::calculateDamageReduction (player.cpp:5799-5801).
        val player = newPlayer()

        assertThat(player.calculateDamageReduction(0.0, 50)).isEqualTo(50.0)
        assertThat(player.calculateDamageReduction(50.0, 50)).isEqualTo(75.0)
        assertThat(player.calculateDamageReduction(0.0, -20)).isEqualTo(-20.0)
    }

    @Test
    fun `updateDamageReductionFromItemAbility reads the item type absorb table`() {
        // Player::updateDamageReductionFromItemAbility (player.cpp:5784-5797).
        val player = newPlayer()
        val fire = CombatType.FIREDAMAGE
        val first = DoubleArray(CombatType.COUNT.value)
        val second = DoubleArray(CombatType.COUNT.value)

        player.updateDamageReductionFromItemAbility(first, absorbItem(1, fire, 30), fire.ordinal)
        assertThat(first[fire.ordinal]).isEqualTo(30.0)

        player.updateDamageReductionFromItemAbility(second, absorbItem(2, fire, 30), fire.ordinal)
        player.updateDamageReductionFromItemAbility(second, absorbItem(3, fire, 30), fire.ordinal)
        assertThat(second[fire.ordinal]).isEqualTo(51.0)
    }

    @Test
    fun `updateDamageReductionFromItemAbility ignores items without abilities`() {
        // player.cpp:5791: `if (itemType.abilities)`.
        val player = newPlayer()
        val array = DoubleArray(CombatType.COUNT.value)

        player.updateDamageReductionFromItemAbility(array, Item(1, 1, ItemType(1, "Plain")), CombatType.FIREDAMAGE.ordinal)

        assertThat(array).containsOnly(0.0)
    }

    @Test
    fun `updateDamageReductionFromItemImbuement walks the imbuement slots`() {
        // Player::updateDamageReductionFromItemImbuement (player.cpp:5769-5782).
        val player = newPlayer()
        val type = ItemType(id = 1, name = "Imbued Sword").apply { imbuementSlot = 1 }
        val item = Item(1, 1, type)
        val imbuement = Imbuement(7, 1).apply { absorbPercent[CombatType.FIREDAMAGE.ordinal] = 20 }
        var resolvedSlots = 0

        val array = DoubleArray(CombatType.COUNT.value)
        player.updateDamageReductionFromItemImbuement(array, item, CombatType.FIREDAMAGE.ordinal) { _, slot ->
            resolvedSlots++
            if (slot == 0) ImbuementInfo(imbuement, 3600) else null
        }

        assertThat(resolvedSlots).isEqualTo(item.getImbuementSlot())
        assertThat(array[CombatType.FIREDAMAGE.ordinal]).isEqualTo(20.0)
    }

    @Test
    fun `calculateDamageReductionFromItem combines abilities and imbuements per combat index`() {
        // Player::calculateDamageReductionFromItem (player.cpp:5762-5767).
        val player = newPlayer()
        val fire = CombatType.FIREDAMAGE
        val item = absorbItem(1, fire, 10)
        val array = DoubleArray(CombatType.COUNT.value)

        player.calculateDamageReductionFromItem(array, item)

        assertThat(array[fire.ordinal]).isEqualTo(10.0)
    }

    @Test
    fun `calculateDamageReductionFromEquipedItems walks every inventory slot`() {
        // Player::calculateDamageReductionFromEquipedItems (player.cpp:5753-5760).
        val player = newPlayer()
        val fire = CombatType.FIREDAMAGE
        assertThat(player.addItem(absorbItem(1, fire, 10), Slots_t.CONST_SLOT_HEAD))
            .isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(absorbItem(2, fire, 10), Slots_t.CONST_SLOT_ARMOR))
            .isEqualTo(ReturnValue.NOERROR)

        val array = DoubleArray(CombatType.COUNT.value)
        player.calculateDamageReductionFromEquipedItems(array)

        assertThat(array[fire.ordinal]).isEqualTo(19.0)
    }

    @Test
    fun `getFinalDamageReduction floors and clamps every combat index`() {
        // Player::getFinalDamageReduction (player.cpp:5739-5751).
        val player = newPlayer()
        assertThat(player.addItem(absorbItem(1, CombatType.FIREDAMAGE, 150), Slots_t.CONST_SLOT_HEAD))
            .isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(absorbItem(2, CombatType.EARTHDAMAGE, -150), Slots_t.CONST_SLOT_ARMOR))
            .isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(absorbItem(3, CombatType.ENERGYDAMAGE, 20), Slots_t.CONST_SLOT_LEGS))
            .isEqualTo(ReturnValue.NOERROR)

        val reduction = player.getFinalDamageReduction()

        assertThat(reduction).hasSize(CombatType.COUNT.value)
        assertThat(reduction[CombatType.FIREDAMAGE.ordinal]).isEqualTo(100.0)
        assertThat(reduction[CombatType.EARTHDAMAGE.ordinal]).isEqualTo(-100.0)
        assertThat(reduction[CombatType.ENERGYDAMAGE.ordinal]).isEqualTo(20.0)
        assertThat(reduction[CombatType.PHYSICALDAMAGE.ordinal]).isZero()
    }

    @Test
    fun `getMitigation delegates to the wheel mitigation seam`() {
        // Player::getMitigation (player.cpp:750-752).
        val player = newPlayer()

        assertThat(player.getMitigation()).isZero()
        assertThat(player.getMitigation { 3.5f }).isEqualTo(3.5f)
    }
}
