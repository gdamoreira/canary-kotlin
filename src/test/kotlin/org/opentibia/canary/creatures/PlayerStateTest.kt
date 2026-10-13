package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

/**
 * Transcribes Canary's basic `Player` state accessors:
 *
 * - `Creature::skull` / `getSkull` / `setSkull` (creature.hpp:168-174,
 *   creature.cpp:100-103) and the `Player::getSkull` override
 *   (src/creatures/players/player.cpp:7116-7121)
 * - `Player::setParty` / `getParty` (player.cpp:1038-1049)
 * - `Player::getBaseMagicLevel` / `getMagicLevel` / `setVarStats`
 *   (player.hpp:585-587, player.cpp:7420-7427, 1251-1253)
 * - `Player::getWeaponId` / `getWeapon` (player.cpp:396-464)
 */
class PlayerStateTest {

    private fun newPlayer() = Player(
        id = 9,
        name = "State Hero",
        maxHealth = 150,
        health = 150,
        maxMana = 50,
        mana = 50,
        position = Position(100, 100, 7),
    )

    @Test
    fun `skull enum matches Canary values`() {
        // Skulls_t (src/utils/utils_definitions.hpp:507-515).
        assertThat(Skulls_t.SKULL_NONE.value).isEqualTo(0)
        assertThat(Skulls_t.SKULL_YELLOW.value).isEqualTo(1)
        assertThat(Skulls_t.SKULL_GREEN.value).isEqualTo(2)
        assertThat(Skulls_t.SKULL_WHITE.value).isEqualTo(3)
        assertThat(Skulls_t.SKULL_RED.value).isEqualTo(4)
        assertThat(Skulls_t.SKULL_BLACK.value).isEqualTo(5)
        assertThat(Skulls_t.SKULL_ORANGE.value).isEqualTo(6)
    }

    @Test
    fun `new player has no skull and setSkull updates it`() {
        // Creature::skull defaults to SKULL_NONE (creature.hpp:925).
        val player = newPlayer()

        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)

        player.setSkull(Skulls_t.SKULL_RED)

        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_RED)
    }

    @Test
    fun `NotGainInFight suppresses the visible skull`() {
        // Player::getSkull (player.cpp:7116-7121): the flag forces SKULL_NONE.
        val player = newPlayer()
        player.setSkull(Skulls_t.SKULL_BLACK)

        player.setFlag(PlayerFlags_t.NotGainInFight)

        assertThat(player.hasFlag(PlayerFlags_t.NotGainInFight)).isTrue()
        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)

        player.removeFlag(PlayerFlags_t.NotGainInFight)

        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_BLACK)
    }

    @Test
    fun `party defaults to null and setParty stores the membership`() {
        // Player::m_party (player.hpp:1767), setParty/getParty (player.cpp:1038-1049).
        val player = newPlayer()

        assertThat(player.getParty()).isNull()

        val party = Party()
        player.setParty(party)

        assertThat(player.getParty()).isSameAs(party)

        player.setParty(null)

        assertThat(player.getParty()).isNull()
    }

    @Test
    fun `new player has base magic level zero`() {
        // Player::magLevel default 0 (player.hpp:1787).
        val player = newPlayer()

        assertThat(player.getBaseMagicLevel()).isEqualTo(0)
        assertThat(player.getMagicLevel()).isEqualTo(0)
    }

    @Test
    fun `var stats add to the base magic level and clamp at zero`() {
        // Player::getMagicLevel (player.cpp:7420-7427) composes the loyalty/base
        // level with varStats[STAT_MAGICPOINTS] and clamps with std::max(0, ...);
        // setVarStats accumulates modifiers (player.cpp:1251-1253).
        val player = newPlayer()
        player.magLevel = 3

        player.setVarStats(Stat.STAT_MAGICPOINTS, 5)

        assertThat(player.getBaseMagicLevel()).isEqualTo(3)
        assertThat(player.getMagicLevel()).isEqualTo(8)

        player.setVarStats(Stat.STAT_MAGICPOINTS, -10)

        assertThat(player.getMagicLevel()).isEqualTo(0)
    }

    @Test
    fun `getWeaponId is zero when nothing is equipped`() {
        // Player::getWeaponId returns 0 without a weapon (player.cpp:396-403).
        val player = newPlayer()

        assertThat(player.getWeaponId()).isEqualTo(0)
        assertThat(player.getWeapon()).isNull()
    }

    @Test
    fun `getWeaponId returns the equipped left hand weapon`() {
        val player = newPlayer()
        player.weaponTypeProvider = { id -> if (id == 2376) WeaponType_t.WEAPON_SWORD else WeaponType_t.WEAPON_NONE }
        player.addItem(Item(2376), Slots_t.CONST_SLOT_LEFT)

        assertThat(player.getWeaponId()).isEqualTo(2376)
        assertThat(player.getWeapon(Slots_t.CONST_SLOT_LEFT)?.id).isEqualTo(2376)
    }

    @Test
    fun `getWeapon skips shields and ammo and falls back to the right hand`() {
        // Player::getWeapon(slot, ignoreAmmo) filters WEAPON_NONE/SHIELD/AMMO
        // (player.cpp:405-424) and getWeapon tries left then right (453-464).
        val player = newPlayer()
        val shield = Item(2515)
        val sword = Item(2376)
        player.weaponTypeProvider = { id ->
            when (id) {
                2515 -> WeaponType_t.WEAPON_SHIELD
                2544 -> WeaponType_t.WEAPON_AMMO
                2376 -> WeaponType_t.WEAPON_SWORD
                else -> WeaponType_t.WEAPON_NONE
            }
        }
        player.addItem(shield, Slots_t.CONST_SLOT_LEFT)

        assertThat(player.getWeaponId()).isEqualTo(0)
        assertThat(player.getWeapon(Slots_t.CONST_SLOT_LEFT)).isNull()

        player.addItem(sword, Slots_t.CONST_SLOT_RIGHT)

        assertThat(player.getWeaponId()).isEqualTo(2376)
        assertThat(player.getWeapon(Slots_t.CONST_SLOT_RIGHT)).isSameAs(sword)
    }
}
