package org.opentibia.canary.creatures.grouping

/**
 * `struct Group` (src/creatures/players/grouping/groups.hpp:14).
 *
 * Only the fields the port currently resolves are modelled; `Player` holds one
 * of these and `Player::isAccessPlayer()` reads [access] (player.cpp:7524).
 */
data class Group(
    val name: String = "",
    val id: Int = 0,
    val access: Boolean = false,
)
