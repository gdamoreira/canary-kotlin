package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType

/**
 * Transcribes the Player light helpers
 * (src/creatures/players/player.cpp:6277-6305):
 *
 * - `Player::getCreatureLight` (player.cpp:6277-6282)
 * - `Player::updateItemsLight` (player.cpp:6284-6305)
 *
 * Canary keeps `itemsLight` on `Player` (player.hpp:1794) and `internalLight`
 * on `Creature` (creature.hpp:922); the class owns the `itemsLight` half and
 * receives the inventory slots, `internalLight` and `g_game().changeLight` as
 * seams (AGENTS.md 1.1).
 */
class PlayerLightStateTest {

    private val changeLights = mutableListOf<Unit>()

    private fun lamp(id: Int, level: Int, color: Int): Item =
        Item(id, count = 1, type = ItemType(id = id, name = "lamp", lightLevel = level, lightColor = color))

    private fun newState(
        slots: List<Item> = emptyList(),
        internalLight: LightInfo = LightInfo(),
    ) = PlayerLightState(
        slotItems = { slots },
        internalLight = { internalLight },
        changeLight = { changeLights.add(Unit) },
    )

    @Test
    fun `updateItemsLight keeps the highest level and notifies the game on change`() {
        val state = newState(slots = listOf(lamp(1, 3, 100), lamp(2, 8, 200), lamp(3, 5, 50)))

        state.updateItemsLight()

        assertThat(state.itemsLight.level).isEqualTo(8)
        assertThat(state.itemsLight.color).isEqualTo(200)
        assertThat(changeLights).hasSize(1)
    }

    @Test
    fun `updateItemsLight does not notify when the light is unchanged`() {
        val state = newState(slots = listOf(lamp(1, 8, 200)))

        state.updateItemsLight()
        state.updateItemsLight()

        assertThat(changeLights).hasSize(1)
    }

    @Test
    fun `updateItemsLight internal mode only refreshes the stored light`() {
        val state = newState(slots = listOf(lamp(1, 4, 111)))

        state.updateItemsLight(internal = true)

        assertThat(state.itemsLight.level).isEqualTo(4)
        assertThat(state.itemsLight.color).isEqualTo(111)
        assertThat(changeLights).isEmpty()
    }

    @Test
    fun `getCreatureLight prefers a stronger internal light`() {
        val state = newState(slots = listOf(lamp(1, 4, 111)))

        assertThat(state.getCreatureLight()).isEqualTo(LightInfo(level = 0, color = 215))

        state.updateItemsLight()
        assertThat(state.getCreatureLight()).isEqualTo(LightInfo(level = 4, color = 111))
    }

    @Test
    fun `getCreatureLight takes the internal light when strictly greater`() {
        val state = newState(slots = listOf(lamp(1, 4, 111)), internalLight = LightInfo(level = 5, color = 9))
        state.updateItemsLight()

        assertThat(state.getCreatureLight()).isEqualTo(LightInfo(level = 5, color = 9))
    }
}
