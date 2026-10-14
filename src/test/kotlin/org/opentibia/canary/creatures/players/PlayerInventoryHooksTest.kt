package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcribes the inventory equip/unequip sweeps
 * (src/creatures/players/player.cpp:12427-12446):
 *
 * - `Player::onEquipInventory` (player.cpp:12427-12435)
 * - `Player::onDeEquipInventory` (player.cpp:12437-12446)
 *
 * `Item::startDecaying` (item.cpp:3501-3541) and the `g_moveEvents()`
 * equip/unequip dispatches are Lua move-event layer seams per AGENTS.md 1.1
 * (see IOMapSerialize.kt:51 for the same `startDecaying` seam).
 */
class PlayerInventoryHooksTest {

    private fun newPlayer(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun newItem(id: Int): Item = Item(id, 1, ItemType(id = id, name = "Item$id"))

    @Test
    fun `onEquipInventory decays and equips every occupied slot`() {
        // player.cpp:12427-12435: `CONST_SLOT_FIRST..CONST_SLOT_LAST`.
        val player = newPlayer()
        val head = newItem(2457)
        val ammo = newItem(2544)
        assertThat(player.addItem(head, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(ammo, Slots_t.CONST_SLOT_AMMO)).isEqualTo(ReturnValue.NOERROR)

        val decays = mutableListOf<Int>()
        val equips = mutableListOf<Pair<Slots_t, Boolean>>()

        player.onEquipInventory(
            startDecaying = { item -> decays.add(item.id) },
            onPlayerEquip = { _, _, slot, isRemove -> equips.add(slot to isRemove) },
        )

        assertThat(decays).containsExactly(2457, 2544)
        assertThat(equips).containsExactly(
            Slots_t.CONST_SLOT_HEAD to false,
            Slots_t.CONST_SLOT_AMMO to false,
        )
    }

    @Test
    fun `onEquipInventory is inert without items`() {
        // `if (item)` guard (12430).
        val player = newPlayer()
        var calls = 0

        player.onEquipInventory(
            startDecaying = { calls++ },
            onPlayerEquip = { _, _, _, _ -> calls++ },
        )

        assertThat(calls).isZero()
    }

    @Test
    fun `onDeEquipInventory dispatches every occupied slot`() {
        // player.cpp:12437-12446.
        val player = newPlayer()
        val legs = newItem(2464)
        assertThat(player.addItem(legs, Slots_t.CONST_SLOT_LEGS)).isEqualTo(ReturnValue.NOERROR)

        val deEquips = mutableListOf<Slots_t>()
        player.onDeEquipInventory(
            onPlayerDeEquip = { _, item, slot ->
                assertThat(item.id).isEqualTo(2464)
                deEquips.add(slot)
                0
            },
        )

        assertThat(deEquips).containsExactly(Slots_t.CONST_SLOT_LEGS)
    }

    @Test
    fun `onDeEquipInventory continues on a zero move-event result`() {
        // `if (... == 0) continue;` (12441-12443).
        val player = newPlayer()
        val ring = newItem(2179)
        assertThat(player.addItem(ring, Slots_t.CONST_SLOT_RING)).isEqualTo(ReturnValue.NOERROR)

        val deEquips = mutableListOf<Slots_t>()
        player.onDeEquipInventory(
            onPlayerDeEquip = { _, _, slot ->
                deEquips.add(slot)
                0
            },
        )

        assertThat(deEquips).containsExactly(Slots_t.CONST_SLOT_RING)
    }
}
