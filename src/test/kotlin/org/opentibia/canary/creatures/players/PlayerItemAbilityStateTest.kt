package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Slots_t

/**
 * Transcribes the inventory item-ability flags
 * (src/creatures/players/player.cpp:344-350):
 *
 * - `Player::isItemAbilityEnabled` (player.cpp:344-346)
 * - `Player::setItemAbility` (player.cpp:348-350)
 *
 * Canary keeps `bool inventoryAbilities[CONST_SLOT_LAST + 1]` on `Player`
 * (player.hpp:1874); the state holder mirrors that array, indexed by the
 * [Slots_t] value.
 */
class PlayerItemAbilityStateTest {

    @Test
    fun `every slot starts disabled`() {
        // `bool inventoryAbilities[CONST_SLOT_LAST + 1] = {}` (player.hpp:1874).
        val state = PlayerItemAbilityState()
        assertThat(Slots_t.entries).allSatisfy { slot ->
            assertThat(state.isItemAbilityEnabled(slot)).isFalse()
        }
    }

    @Test
    fun `setItemAbility toggles a single slot independently`() {
        // Player::setItemAbility (player.cpp:348-350).
        val state = PlayerItemAbilityState()

        state.setItemAbility(Slots_t.CONST_SLOT_HEAD, true)
        assertThat(state.isItemAbilityEnabled(Slots_t.CONST_SLOT_HEAD)).isTrue()
        assertThat(state.isItemAbilityEnabled(Slots_t.CONST_SLOT_ARMOR)).isFalse()

        state.setItemAbility(Slots_t.CONST_SLOT_HEAD, false)
        assertThat(state.isItemAbilityEnabled(Slots_t.CONST_SLOT_HEAD)).isFalse()
    }

    @Test
    fun `the backpack and store-inbox slots are addressable`() {
        // `CONST_SLOT_LAST` (creatures_definitions.hpp:388) bounds the array.
        val state = PlayerItemAbilityState()

        state.setItemAbility(Slots_t.CONST_SLOT_BACKPACK, true)
        state.setItemAbility(Slots_t.CONST_SLOT_STORE_INBOX, true)

        assertThat(state.isItemAbilityEnabled(Slots_t.CONST_SLOT_BACKPACK)).isTrue()
        assertThat(state.isItemAbilityEnabled(Slots_t.CONST_SLOT_STORE_INBOX)).isTrue()
    }
}
