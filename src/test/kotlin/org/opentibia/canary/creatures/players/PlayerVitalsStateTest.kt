package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ITEM_CRYSTAL_COIN
import org.opentibia.canary.items.ITEM_GOLD_COIN
import org.opentibia.canary.items.ITEM_PLATINUM_COIN
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player soul/stamina/bank state
 * (src/creatures/players/player.cpp:4541-4543, 6773-6781, 8299-8305):
 *
 * - `Player::getSoul` (player.hpp:591-593)
 * - `Player::changeSoul` (player.cpp:6773-6781)
 * - `Player::getStaminaMinutes` (player.cpp:4541-4543)
 * - `Player::getBankBalance` / `setBankBalance` (player.cpp:8299-8305)
 *
 * `Player::staminaMinutes` defaults to 2520 (player.hpp:1817) and
 * `player:setStamina` clamps to `min(2520, stamina)` before writing the field
 * (src/lua/functions/creatures/player/player_functions.cpp:2225-2233).
 * `vocation->getSoulMax()` (vocation.hpp:49) and
 * `RATE_SOUL_REGEN` (configmanager.cpp:206, default 1.0) are injected seams.
 */
class PlayerVitalsStateTest {

    private var statsSent = 0

    private fun newState(
        soul: Int = 0,
        soulMax: Int = 100,
        rate: Double = 1.0,
        staminaMinutes: Int = 2520,
        bankBalance: Long = 0,
    ) = PlayerVitalsState(
        soulMax = { soulMax },
        rateSoulRegen = rate,
        sendStats = { statsSent++ },
        soul = soul,
        staminaMinutes = staminaMinutes,
        bankBalance = bankBalance,
    )

    @Test
    fun `defaults mirror the Canary player fields`() {
        // player.hpp:1817 (2520) and the zero-initialized soul/bankBalance
        // (player.hpp:1726, 1848).
        val state = newState()

        assertThat(state.getSoul()).isZero()
        assertThat(state.getStaminaMinutes()).isEqualTo(2520)
        assertThat(state.getBankBalance()).isZero()
    }

    @Test
    fun `changeSoul regenerates scaled by RATE_SOUL_REGEN and caps at soulMax`() {
        // Player::changeSoul (player.cpp:6773-6778).
        val state = newState(soul = 10, soulMax = 100, rate = 1.5)

        state.changeSoul(10)
        assertThat(state.getSoul()).isEqualTo(25)

        state.changeSoul(100)
        assertThat(state.getSoul()).isEqualTo(100)
        assertThat(statsSent).isEqualTo(2)
    }

    @Test
    fun `changeSoul truncates the scaled regeneration like the C++ float cast`() {
        // std::min<int32_t>(soulChange * rate, ...) truncates toward zero.
        val state = newState(soul = 0, soulMax = 100, rate = 1.5)

        state.changeSoul(3)

        assertThat(state.getSoul()).isEqualTo(4)
    }

    @Test
    fun `changeSoul clamps negative changes at zero`() {
        // player.cpp:6779-6781.
        val state = newState(soul = 30)

        state.changeSoul(-10)
        assertThat(state.getSoul()).isEqualTo(20)

        state.changeSoul(-100)
        assertThat(state.getSoul()).isZero()
    }

    @Test
    fun `changeSoul pulls a soul above soulMax back down`() {
        // A vocation change can leave soul above the new max; the min() delta is
        // then negative (player.cpp:6775-6777).
        val state = newState(soul = 200, soulMax = 100)

        state.changeSoul(1)

        assertThat(state.getSoul()).isEqualTo(100)
    }

    @Test
    fun `stamina minutes are a uint16 field`() {
        // player.hpp:1817 `uint16_t staminaMinutes`.
        val state = newState(staminaMinutes = 70000)

        assertThat(state.getStaminaMinutes()).isEqualTo(70000 and 0xFFFF)
    }

    @Test
    fun `bank balance round trips`() {
        // Player::getBankBalance / setBankBalance (player.cpp:8299-8305).
        val state = newState(bankBalance = 123_456_789L)

        assertThat(state.getBankBalance()).isEqualTo(123_456_789L)

        state.setBankBalance(42)
        assertThat(state.getBankBalance()).isEqualTo(42)
    }

    @Test
    fun `getMoney counts carried and nested coins`() {
        // Player::getMoney (player.cpp:1533-1555) over Item::getWorth
        // (src/items/item.cpp).
        val player = Player(
            id = 12,
            name = "Money Hero",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        )
        player.addItem(Item(ITEM_GOLD_COIN, count = 50), Slots_t.CONST_SLOT_LEFT)

        val backpack = Container(id = 1988, capacity = 20)
        backpack.addItem(Item(ITEM_PLATINUM_COIN, count = 3))
        val nested = Container(id = 2000, capacity = 20)
        nested.addItem(Item(ITEM_CRYSTAL_COIN, count = 2))
        backpack.addItem(nested)
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getMoney()).isEqualTo(50 + 300 + 20_000)
    }
}
