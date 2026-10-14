package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.items.Item

/**
 * Player capacity helpers (src/creatures/players/player.cpp:4557-4573,
 * 5441-5476) as extensions over the port's [Player].
 *
 * The port's `Player.capacity` is a `Double` (Canary `uint32_t`), so the
 * `uint32_t` saturations are kept as [UINT32_MAX_CAPACITY]. `Player::bonusCapacity`
 * (player.hpp) and the wheel `CAPACITY` stat are parameters because neither is
 * ported on the host yet. `Player::getFreeCapacity` is the existing port member
 * and is used unchanged by [hasCapacity].
 */

/** `std::numeric_limits<uint32_t>::max()` (player.cpp:5446, 5456, 5463). */
private const val UINT32_MAX_CAPACITY = 4_294_967_295.0

/** `Player::getBaseCapacity` (player.cpp:5441-5449). */
fun Player.getBaseCapacity(): Double {
    if (hasFlag(PlayerFlags_t.CannotPickupItem)) {
        return 0.0
    }
    if (hasFlag(PlayerFlags_t.HasInfiniteCapacity)) {
        return UINT32_MAX_CAPACITY
    }
    return capacity
}

/**
 * `Player::getCapacity` (player.cpp:5451-5459). `bonusCapacity` is the
 * `Player::bonusCapacity` field and `wheelCapacity` the raw
 * `m_wheelPlayer.getStat(WheelStat_t::CAPACITY)` value (multiplied by 100 here,
 * exactly like the C++ expression).
 */
fun Player.getCapacity(bonusCapacity: Double = 0.0, wheelCapacity: Int = 0): Double {
    if (hasFlag(PlayerFlags_t.CannotPickupItem)) {
        return 0.0
    }
    if (hasFlag(PlayerFlags_t.HasInfiniteCapacity)) {
        return UINT32_MAX_CAPACITY
    }
    return capacity + bonusCapacity + varStats[Stat.STAT_CAPACITY.value] + wheelCapacity * 100
}

/** `Player::getBonusCapacity` (player.cpp:5461-5466). */
fun Player.getBonusCapacity(bonusCapacity: Double = 0.0): Double {
    if (hasFlag(PlayerFlags_t.CannotPickupItem) || hasFlag(PlayerFlags_t.HasInfiniteCapacity)) {
        return UINT32_MAX_CAPACITY
    }
    return bonusCapacity
}

/**
 * `Player::hasCapacity` (player.cpp:4557-4573). The C++
 * `item->getTopParent().get() == this` check is the [carriedByPlayer] seam
 * because the port's `Player` is not a `Cylinder`; `hasInfiniteCapacity` and
 * determined base weights match the C++ `getWeight()`/`getBaseWeight()` units
 * (the port's base weight is in hundredths of an ounce).
 */
fun Player.hasCapacity(item: Item, count: Int, carriedByPlayer: Boolean = false): Boolean {
    if (hasFlag(PlayerFlags_t.CannotPickupItem)) {
        return false
    }

    if (hasFlag(PlayerFlags_t.HasInfiniteCapacity) || carriedByPlayer) {
        return true
    }

    val itemWeight = if (item.getContainer() != null) item.weight else item.getBaseWeight() / 100.0
    val totalWeight = if (item.isStackable()) itemWeight * count else itemWeight
    return totalWeight <= getFreeCapacity()
}
