package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Slots_t

/**
 * Mirror of Canary's `bool inventoryAbilities[CONST_SLOT_LAST + 1]`
 * (`player.hpp:1874`) with the two accessors
 * (src/creatures/players/player.cpp:344-350):
 *
 * - `Player::isItemAbilityEnabled` (player.cpp:344-346)
 * - `Player::setItemAbility` (player.cpp:348-350)
 *
 * The array is indexed by the [Slots_t] value, exactly like the C++
 * `inventoryAbilities[slot]` accesses; `CONST_SLOT_LAST + 1` (player.hpp:1874)
 * is the size. The ability flags are the movement-script gate
 * `lua/creature/movement.cpp:543-655` and the protocol equipment gate
 * `protocolgame.cpp:314`.
 */
class PlayerItemAbilityState {

    /** `bool inventoryAbilities[CONST_SLOT_LAST + 1] = {}` (player.hpp:1874). */
    private val inventoryAbilities: BooleanArray = BooleanArray(Slots_t.LAST + 1)

    /** `Player::isItemAbilityEnabled` (player.cpp:344-346). */
    fun isItemAbilityEnabled(slot: Slots_t): Boolean = inventoryAbilities[slot.value]

    /** `Player::setItemAbility` (player.cpp:348-350). */
    fun setItemAbility(slot: Slots_t, enabled: Boolean) {
        inventoryAbilities[slot.value] = enabled
    }
}
