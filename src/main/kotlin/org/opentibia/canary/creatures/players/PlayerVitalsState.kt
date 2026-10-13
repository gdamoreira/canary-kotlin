package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container

/**
 * Self-contained mirror of the Player soul/stamina/bank state
 * (src/creatures/players/player.cpp:4541-4543, 6773-6781, 8299-8305).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps `soul`, `staminaMinutes` and `bankBalance` directly on `Player`
 * (player.hpp:1726, 1817, 1848); the state is exposed as an injectable seam so
 * the host player can delegate without a global (AGENTS.md 1.1). The global
 * dependencies are constructor seams: [soulMax] replaces
 * `vocation->getSoulMax()` (vocation.hpp:49), [rateSoulRegen] replaces
 * `g_configManager().getFloat(RATE_SOUL_REGEN)` (configmanager.cpp:206) and
 * [sendStats] replaces `Player::sendStats()` (player.cpp:6780).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerVitalsState(
    /** `vocation->getSoulMax()` (vocation.hpp:49); `uint8_t`. */
    private val soulMax: () -> Int = { 100 },
    /** `g_configManager().getFloat(RATE_SOUL_REGEN)` (configmanager.cpp:206). */
    private val rateSoulRegen: Double = 1.0,
    /** `Player::sendStats()` (player.cpp:6780). */
    private val sendStats: () -> Unit = {},
    /** `Player::soul` (player.hpp:1848); `uint8_t`. */
    soul: Int = 0,
    /** `Player::staminaMinutes` (player.hpp:1817); `uint16_t`, default 2520. */
    staminaMinutes: Int = 2520,
    /** `Player::bankBalance` (player.hpp:1726); `uint64_t`. */
    bankBalance: Long = 0,
) {

    /** `Player::soul` (player.hpp:1848); only [changeSoul] mutates it. */
    @get:JvmName("getSoulValue")
    var soul: Int = soul and UINT8_MAX
        private set

    /**
     * `Player::staminaMinutes` (player.hpp:1817). The field is public in
     * Canary: `player:setStamina` writes it directly after clamping to 2520
     * (player_functions.cpp:2225-2233).
     */
    @get:JvmName("getStaminaMinutesValue")
    var staminaMinutes: Int = staminaMinutes and UINT16_MAX

    /** `Player::bankBalance` (player.hpp:1726); use [setBankBalance]. */
    @get:JvmName("getBankBalanceValue")
    var bankBalance: Long = bankBalance
        private set

    /** `Player::getSoul` (player.hpp:591-593). */
    fun getSoul(): Int = soul

    /**
     * `Player::changeSoul` (player.cpp:6773-6781): a positive change adds
     * `soulChange * RATE_SOUL_REGEN` truncated toward zero, capped by
     * `soulMax - soul`; a non-positive change clamps at zero. `sendStats()` runs
     * either way.
     */
    fun changeSoul(soulChange: Int) {
        if (soulChange > 0) {
            val scaled = (soulChange * rateSoulRegen).toInt()
            soul += minOf(scaled, soulMax() - soul)
        } else {
            soul = maxOf(0, soul + soulChange)
        }

        sendStats()
    }

    /** `Player::getStaminaMinutes` (player.cpp:4541-4543). */
    fun getStaminaMinutes(): Int = staminaMinutes

    /** `Player::getBankBalance` (player.cpp:8299-8301). */
    fun getBankBalance(): Long = bankBalance

    /** `Player::setBankBalance` (player.cpp:8303-8305). */
    fun setBankBalance(balance: Long) {
        bankBalance = balance
    }

    companion object {
        /** `std::numeric_limits<uint8_t>::max()` (player.hpp:1848). */
        private const val UINT8_MAX = 0xFF

        /** `std::numeric_limits<uint16_t>::max()` (player.hpp:1817). */
        private const val UINT16_MAX = 0xFFFF
    }
}

/**
 * `Player::getMoney` (player.cpp:1533-1555): recursively sums `Item::getWorth`
 * over the inventory, descending into every container and skipping the
 * container items themselves. The inventory accessor already lives on the
 * port's [Player] (`getInventoryItem` over `Slots_t`), so this is an extension
 * like the other stateless Player queries.
 */
fun Player.getMoney(): Long {
    var moneyCount = 0L

    fun countMoneyInContainer(container: Container) {
        for (item in container.items) {
            val nested = item as? Container
            if (nested != null) {
                countMoneyInContainer(nested)
            } else {
                moneyCount += item.getWorth()
            }
        }
    }

    for (slot in Slots_t.entries) {
        if (slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST) {
            continue
        }
        val item = getInventoryItem(slot) ?: continue

        val container = item as? Container
        if (container != null) {
            countMoneyInContainer(container)
        } else {
            moneyCount += item.getWorth()
        }
    }

    return moneyCount
}
