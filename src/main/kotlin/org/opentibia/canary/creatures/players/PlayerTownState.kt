package org.opentibia.canary.creatures.players

import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Town

/**
 * Self-contained mirror of the Player town/login-position state
 * (src/creatures/players/player.cpp:8307-8326; player.hpp:635-637, 1713).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps `town` and `loginPosition` directly on `Player` (player.hpp:1713,
 * 1771); the state is exposed as an injectable seam so the host player can
 * delegate without a global (AGENTS.md 1.1). [Town] is the port's
 * `map/Town.kt` mirror of `class Town`.
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerTownState(
    /** `Player::town` (player.hpp:1771). */
    town: Town? = null,
) {

    /** `Player::town` (player.hpp:1771). */
    @get:JvmName("getTownValue")
    @set:JvmName("setTownValue")
    var town: Town? = town
        private set

    /**
     * `Player::loginPosition` (player.hpp:1713). The C++ field is written
     * directly (`loginPosition = town->getTemplePosition()`, player.cpp:4040),
     * so the holder keeps the field publicly replaceable.
     */
    @get:JvmName("getLoginPositionValue")
    var loginPosition: Position = Position(0, 0, 0)

    /** `Player::getTown` (player.cpp:8307-8309). */
    fun getTown(): Town? = town

    /** `Player::setTown` (player.cpp:8310-8312). */
    fun setTown(newTown: Town?) {
        town = newTown
    }

    /**
     * `Player::getTemplePosition` (player.cpp:8319-8326): the town's temple, or
     * the static zero `Position` when no town is set.
     */
    fun getTemplePosition(): Position = town?.getTemplePosition() ?: Position(0, 0, 0)

    /** `Player::getLoginPosition` (player.hpp:635-637). */
    fun getLoginPosition(): Position = loginPosition
}
