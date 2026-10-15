package org.opentibia.canary.creatures.grouping

import org.opentibia.canary.creatures.PlayerFlags_t

/**
 * `struct Group` (src/creatures/players/grouping/groups.hpp:14-22).
 *
 * Full field parity with the C++ struct (groups.hpp:15-21):
 *  - `std::string name`                    -> [name]
 *  - `std::array<bool, FlagLast> flags`    -> [flags] (the raised `PlayerFlags_t` set)
 *  - `uint32_t maxDepotItems`              -> [maxDepotItems]
 *  - `uint32_t maxVipEntries`              -> [maxVipEntries]
 *  - `uint16_t id`                         -> [id]
 *  - `bool access`                         -> [access]
 *
 * [flags] is mutable exactly like the C++ array: `Player::hasFlag` reads it
 * (`group->flags[flag]`, player.cpp:7853-7855) and `Player::setFlag`/
 * `Player::removeFlag` (player.cpp:7857-7863) raise/clear entries on the shared
 * instance the registry handed out. `Player` holds one of these and
 * `Player::isAccessPlayer()` reads [access] (player.cpp:7524); the Lua `Group`
 * metatable (GroupFunctions) reads every field through the injected
 * `groups.getGroup(id)` seam.
 */
data class Group(
    val name: String = "",
    val id: Int = 0,
    val access: Boolean = false,
    val flags: MutableSet<PlayerFlags_t> = mutableSetOf(),
    val maxDepotItems: Int = 0,
    val maxVipEntries: Int = 0,
)
