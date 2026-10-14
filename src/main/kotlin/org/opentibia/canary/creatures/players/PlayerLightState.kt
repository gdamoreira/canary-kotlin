package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.items.Item

/**
 * Self-contained mirror of the Player light state
 * (src/creatures/players/player.cpp:6277-6305).
 *
 * Canary keeps `Player::itemsLight` (player.hpp:1794) beside
 * `Creature::internalLight` (creature.hpp:922); the class owns the items half
 * and receives the inventory slot contents, the internal light and the
 * `g_game().changeLight` side effect as constructor seams (AGENTS.md 1.1).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe.
 */
class PlayerLightState(
    /** The `Player::inventory` slot items (player.hpp:1763). */
    private val slotItems: () -> Collection<Item> = { emptyList() },
    /** `Creature::internalLight` (creature.hpp:922). */
    private val internalLight: () -> LightInfo = { LightInfo() },
    /** `g_game().changeLight(player)` (player.cpp:6300). */
    private val changeLight: () -> Unit = {},
) {

    /** `Player::itemsLight` (player.hpp:1794). */
    var itemsLight: LightInfo = LightInfo()
        private set

    /** `Player::getCreatureLight` (player.cpp:6277-6282). */
    fun getCreatureLight(): LightInfo {
        val internal = internalLight()
        return if (internal.level > itemsLight.level) internal else itemsLight
    }

    /**
     * `Player::updateItemsLight(internal)` (player.cpp:6284-6305): the
     * strongest inventory light wins, the stored value changes only on a
     * level/color difference, and a non-internal update notifies the game.
     */
    fun updateItemsLight(internal: Boolean = false) {
        var maxLight = LightInfo()

        for (item in slotItems()) {
            val curLight = item.getLightInfo()
            if (curLight.level > maxLight.level) {
                maxLight = curLight
            }
        }

        if (itemsLight.level != maxLight.level || itemsLight.color != maxLight.color) {
            itemsLight = maxLight

            if (!internal) {
                changeLight()
            }
        }
    }
}
