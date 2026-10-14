package org.opentibia.canary.creatures.players

/**
 * Player active-food state (src/creatures/players/player.cpp:5672-5690).
 *
 * Canary keeps `std::map<uint16_t, uint32_t> m_activeFoods` on `Player`
 * (player.hpp:1677); the state holder mirrors the port's `PlayerConcoctions`
 * composition seam. The client food-duration list is sent by
 * `ProtocolGame::sendCyclopediaCharacterMiscStats` (protocolgame.cpp:5761-5766).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerActiveFoods {

    /** `Player::m_activeFoods` (player.hpp:1677), keyed by item id. */
    private val activeFoods = mutableMapOf<Int, Int>()

    /** `Player::updateFood` (player.cpp:5672-5678). */
    fun updateFood(itemId: Int, timeLeft: Int) {
        if (timeLeft == 0) {
            activeFoods.remove(itemId)
        } else {
            activeFoods[itemId] = timeLeft
        }
    }

    /** `Player::getActiveFoods` (player.cpp:5680-5683). */
    fun getActiveFoods(): Map<Int, Int> = activeFoods.toMap()

    /** `Player::isFoodActive` (player.cpp:5685-5690). */
    fun isFoodActive(itemId: Int): Boolean {
        val timeLeft = activeFoods[itemId] ?: return false
        return timeLeft > 0
    }
}
