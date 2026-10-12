package org.opentibia.canary.systems.hireling

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.readTibiaString

/**
 * Mirror of Canary's `AccessHouseLevel_t` (`src/map/map_definitions.hpp`).
 */
enum class AccessHouseLevel(val value: Int) {
    HOUSE_NOT_INVITED(0),
    HOUSE_GUEST(1),
    HOUSE_SUBOWNER(2),
    HOUSE_OWNER(3)
}

/**
 * Result of the opcode 0xEC name-set payload read by
 * `ProtocolGame::parseSetHirelingName`. Canary reads and discards these fields
 * (a name string followed by two `uint32_t` values); they are surfaced here so
 * the parse order and width are testable.
 */
data class SetHirelingName(
    val name: String,
    val value1: Long,
    val value2: Long
)

/**
 * Engine core of the House Hirelings subsystem.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/player.cpp (Player::canSpeakWithHireling)
 *  - src/creatures/creatures_definitions.hpp (SPEECHBUBBLE_HIRELING)
 *  - src/server/network/protocol/protocolgame.cpp (ProtocolGame::parseSetHirelingName, 0xEC)
 *  - src/utils/utils_definitions.hpp (ItemID_t::HIRELING_LAMP)
 */
object Hireling {

    /** Mirrors `SPEECHBUBBLE_HIRELING` (`src/creatures/creatures_definitions.hpp`). */
    const val SPEECHBUBBLE_HIRELING = 7

    /** Mirrors `ItemID_t::HIRELING_LAMP` (`src/utils/utils_definitions.hpp`). */
    const val HIRELING_LAMP = 29432

    /**
     * Mirrors `Player::canSpeakWithHireling(uint8_t speechbubble)`.
     *
     * A hireling speech bubble is rejected when the player has no house on their
     * tile (`houseAccessLevel == null`) or their access level is
     * `HOUSE_NOT_INVITED`. Any other speech bubble is always allowed.
     */
    fun canSpeakWithHireling(speechBubble: Int, houseAccessLevel: AccessHouseLevel?): Boolean {
        if (speechBubble == SPEECHBUBBLE_HIRELING &&
            (houseAccessLevel == null || houseAccessLevel == AccessHouseLevel.HOUSE_NOT_INVITED)
        ) {
            return false
        }

        return true
    }

    /**
     * Mirrors `ProtocolGame::parseSetHirelingName` (opcode 0xEC): reads a
     * length-prefixed string followed by two `uint32_t` values.
     */
    fun parseSetHirelingName(msg: ByteBuf): SetHirelingName {
        val name = msg.readTibiaString()
        val value1 = msg.readUnsignedIntLE()
        val value2 = msg.readUnsignedIntLE()
        return SetHirelingName(name, value1, value2)
    }
}
