package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType

/**
 * Transcribes the Player specialized-magic / magic-shield capacity pipeline
 * (src/creatures/players/player.cpp:7606-7687):
 *
 * - `Player::getSpecializedMagicLevel` (player.cpp:7606-7629)
 * - `Player::setSpecializedMagicLevel` (player.cpp:7631-7633)
 * - `Player::getMagicShieldCapacityFlat` (player.cpp:7635-7656)
 * - `Player::setMagicShieldCapacityFlat` (player.cpp:7658-7660)
 * - `Player::getMagicShieldCapacityPercent` (player.cpp:7662-7683)
 * - `Player::setMagicShieldCapacityPercent` (player.cpp:7685-7687)
 *
 * `m_wheelPlayer.getSpecializedMagic`, `m_weaponProficiency.getSpecializedMagic`
 * and `g_game().transformItem` live outside this package and are constructor
 * seams per AGENTS.md 1.1.
 */
class PlayerMagicAffinityStateTest {

    private val fire = CombatType.FIREDAMAGE

    private fun abilityItem(id: Int, specialized: Int, flat: Int, percent: Int): Item {
        val type = ItemType(id = id, name = "Affinity $id")
        type.abilities = Abilities().apply {
            specializedMagicLevel[fire.ordinal] = specialized
            magicShieldCapacityFlat = flat
            magicShieldCapacityPercent = percent
        }
        return Item(id, 1, type)
    }

    private fun newState(
        items: List<Item> = emptyList(),
        chargeCount: Int = 0,
        onTransform: (Item) -> Unit = {},
    ) = PlayerMagicAffinityState(
        getEquippedItems = { items },
        wheelSpecializedMagic = { combat -> if (combat == fire) 2 else 0 },
        proficiencySpecializedMagic = { combat -> if (combat == fire) 3 else 0 },
        getCharges = { chargeCount },
        transformItemCharges = onTransform,
    )

    @Test
    fun `getSpecializedMagicLevel sums the base, wheel, proficiency and item values`() {
        // Player::getSpecializedMagicLevel (player.cpp:7606-7629).
        val state = newState(items = listOf(abilityItem(1, specialized = 4, flat = 0, percent = 0)))

        assertThat(state.getSpecializedMagicLevel(fire, useCharges = false)).isEqualTo(9)
        assertThat(state.getSpecializedMagicLevel(CombatType.ICEDAMAGE, useCharges = false)).isZero()
    }

    @Test
    fun `getSpecializedMagicLevel consumes charges when requested`() {
        // player.cpp:7620-7625: charged items transform when `useCharges` is set.
        val item = abilityItem(1, specialized = 4, flat = 0, percent = 0)
        var transformed = 0
        val state = newState(items = listOf(item), chargeCount = 5) { transformed++ }

        assertThat(state.getSpecializedMagicLevel(fire, useCharges = true)).isEqualTo(9)
        assertThat(transformed).isEqualTo(1)

        assertThat(state.getSpecializedMagicLevel(fire, useCharges = false)).isEqualTo(9)
        assertThat(transformed).isEqualTo(1)
    }

    @Test
    fun `getSpecializedMagicLevel ignores zero and negative item values`() {
        // player.cpp:7618: only `specialized_magic_level > 0` is applied.
        val state = newState(items = listOf(abilityItem(1, specialized = -5, flat = 0, percent = 0)))

        assertThat(state.getSpecializedMagicLevel(fire, useCharges = false)).isEqualTo(5)
    }

    @Test
    fun `setSpecializedMagicLevel clamps the running total at zero`() {
        // Player::setSpecializedMagicLevel (player.cpp:7631-7633).
        val state = newState()

        state.setSpecializedMagicLevel(fire, 5)
        assertThat(state.getSpecializedMagicLevel(fire, useCharges = false)).isEqualTo(10)

        state.setSpecializedMagicLevel(fire, -100)
        assertThat(state.getSpecializedMagicLevel(fire, useCharges = false)).isEqualTo(5)
    }

    @Test
    fun `magic shield capacity flat adds the stored and item values`() {
        // Player::getMagicShieldCapacityFlat / set (player.cpp:7635-7660).
        val state = newState(items = listOf(abilityItem(1, specialized = 0, flat = 7, percent = 0)))

        assertThat(state.getMagicShieldCapacityFlat(useCharges = false)).isEqualTo(7)

        state.setMagicShieldCapacityFlat(-2)
        assertThat(state.getMagicShieldCapacityFlat(useCharges = false)).isEqualTo(5)
    }

    @Test
    fun `magic shield capacity percent adds the stored and item values`() {
        // Player::getMagicShieldCapacityPercent / set (player.cpp:7662-7687).
        val state = newState(items = listOf(abilityItem(1, specialized = 0, flat = 0, percent = 9)))

        assertThat(state.getMagicShieldCapacityPercent(useCharges = false)).isEqualTo(9)

        state.setMagicShieldCapacityPercent(1)
        assertThat(state.getMagicShieldCapacityPercent(useCharges = false)).isEqualTo(10)
    }

    @Test
    fun `magic shield capacity consumes charges when requested`() {
        // player.cpp:7650-7655, 7677-7682.
        val item = abilityItem(1, specialized = 0, flat = 7, percent = 9)
        var transformed = 0
        val state = newState(items = listOf(item), chargeCount = 3) { transformed++ }

        state.getMagicShieldCapacityFlat(useCharges = true)
        state.getMagicShieldCapacityPercent(useCharges = true)

        assertThat(transformed).isEqualTo(2)
    }
}
