package org.opentibia.canary.creatures.grouping

/**
 * `Party` (`src/creatures/players/grouping/party.hpp`).
 *
 * Only the identity of a party is modelled for the `Player::getParty` /
 * `Player::setParty` seam (player.cpp:1038-1049); member, leader, invite and
 * shared-experience state arrive with the party subsystem. The file lives in
 * `creatures/grouping` to mirror the Canary path
 * `src/creatures/players/grouping/party.hpp`.
 */
class Party
