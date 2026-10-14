package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.systems.imbuement.ImbuementInfo
import kotlin.math.floor

/**
 * Player elemental damage-reduction helpers
 * (src/creatures/players/player.cpp:5739-5801) as extensions over the port's
 * [Player].
 *
 * The item-type ability table and the inventory already exist on the port, so
 * the only injected dependency is the imbuement slot resolution
 * (`Item::getImbuementInfo`, src/items/item.cpp:472), which reads the
 * imbuement registry owned by `systems/imbuement` and therefore cannot be
 * reached from this package. Unwired, no imbuement contributes absorption,
 * which matches an item with empty imbuement slots.
 */

/** `COMBAT_COUNT` (`src/creatures/creatures_definitions.hpp:847`). */
private const val COMBAT_COUNT = 14

/**
 * `Player::getFinalDamageReduction` (player.cpp:5739-5751): the per-combat
 * reduction, floored and clamped to `[-100, 100]`.
 */
fun Player.getFinalDamageReduction(): DoubleArray {
    val combatReductionArray = DoubleArray(COMBAT_COUNT)
    calculateDamageReductionFromEquipedItems(combatReductionArray)
    for (combatTypeIndex in 0 until COMBAT_COUNT) {
        combatReductionArray[combatTypeIndex] = floor(combatReductionArray[combatTypeIndex]).coerceIn(-100.0, 100.0)
    }
    return combatReductionArray
}

/**
 * `Player::calculateDamageReductionFromEquipedItems` (player.cpp:5753-5760):
 * walks every inventory slot from `CONST_SLOT_FIRST` to `CONST_SLOT_LAST`.
 */
fun Player.calculateDamageReductionFromEquipedItems(combatReductionArray: DoubleArray) {
    for (slot in Slots_t.entries) {
        if (slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST) {
            continue
        }
        val item = getInventoryItem(slot) ?: continue
        calculateDamageReductionFromItem(combatReductionArray, item)
    }
}

/** `Player::calculateDamageReductionFromItem` (player.cpp:5762-5767). */
fun Player.calculateDamageReductionFromItem(
    combatReductionArray: DoubleArray,
    item: Item,
    getImbuementInfo: (Item, Int) -> ImbuementInfo? = { _, _ -> null },
) {
    for (combatTypeIndex in 0 until COMBAT_COUNT) {
        updateDamageReductionFromItemImbuement(combatReductionArray, item, combatTypeIndex, getImbuementInfo)
        updateDamageReductionFromItemAbility(combatReductionArray, item, combatTypeIndex)
    }
}

/** `Player::updateDamageReductionFromItemImbuement` (player.cpp:5769-5782). */
fun Player.updateDamageReductionFromItemImbuement(
    combatReductionArray: DoubleArray,
    item: Item,
    combatTypeIndex: Int,
    getImbuementInfo: (Item, Int) -> ImbuementInfo? = { _, _ -> null },
) {
    for (imbueSlotId in 0 until item.getImbuementSlot()) {
        val imbuementInfo = getImbuementInfo(item, imbueSlotId) ?: continue
        val imbuementAbsorption = imbuementInfo.imbuement.absorbPercent[combatTypeIndex]
        if (imbuementAbsorption != 0) {
            combatReductionArray[combatTypeIndex] =
                calculateDamageReduction(combatReductionArray[combatTypeIndex], imbuementAbsorption)
        }
    }
}

/** `Player::updateDamageReductionFromItemAbility` (player.cpp:5784-5797). */
fun Player.updateDamageReductionFromItemAbility(
    combatReductionArray: DoubleArray,
    item: Item,
    combatTypeIndex: Int,
) {
    val abilities = item.type?.abilities ?: return
    val elementReduction = abilities.absorbPercent[combatTypeIndex].toInt()
    if (elementReduction != 0) {
        combatReductionArray[combatTypeIndex] =
            calculateDamageReduction(combatReductionArray[combatTypeIndex], elementReduction)
    }
}

/** `Player::calculateDamageReduction` (player.cpp:5799-5801). */
fun Player.calculateDamageReduction(currentTotal: Double, resistance: Int): Double =
    (100 - currentTotal) / 100.0 * resistance + currentTotal
