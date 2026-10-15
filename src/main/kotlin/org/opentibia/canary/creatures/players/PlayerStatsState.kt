package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.Player

/** `ItemId_t::ITEM_MALE_CORPSE` (src/utils/utils_definitions.hpp:626). */
const val ITEM_MALE_CORPSE = 4240

/** `ItemId_t::ITEM_FEMALE_CORPSE` (src/utils/utils_definitions.hpp:627). */
const val ITEM_FEMALE_CORPSE = 4247

/**
 * `Player::getDefaultStats` (player.cpp:1279-1290): the default-value switch
 * for `stats_t` (`STAT_MAXHITPOINTS` -> `healthMax`, `STAT_MAXMANAPOINTS` ->
 * `manaMax`, `STAT_MAGICPOINTS` -> `getBaseMagicLevel()`, everything else 0).
 */
fun Player.getDefaultStats(stat: Stat): Int = when (stat) {
    Stat.STAT_MAXHITPOINTS -> maxHealth
    Stat.STAT_MAXMANAPOINTS -> maxMana
    Stat.STAT_MAGICPOINTS -> getBaseMagicLevel()
    else -> 0
}
