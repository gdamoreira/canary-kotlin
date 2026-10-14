package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player

/**
 * `OperatingSystem_t` (`src/creatures/creatures_definitions.hpp:737-756`).
 *
 * The client-reported operating system read by `Player::getOperatingSystem`
 * (player.cpp:1010-1012) and written by `Player::setOperatingSystem`
 * (player.cpp:1014-1016).
 */
enum class OperatingSystem_t(val value: Int) {
    CLIENTOS_NONE(0),
    CLIENTOS_LINUX(1),
    CLIENTOS_WINDOWS(2),
    CLIENTOS_FLASH(3),
    CLIENTOS_NEW_LINUX(4),
    CLIENTOS_NEW_WINDOWS(5),
    CLIENTOS_NEW_MAC(6),
    CLIENTOS_OTCLIENT_LINUX(10),
    CLIENTOS_OTCLIENT_WINDOWS(11),
    CLIENTOS_OTCLIENT_MAC(12),

    // by default OTCv8 uses CLIENTOS_WINDOWS for backward compatibility
    // for correct value enable g_game.enableFeature(GameExtendedOpcode)
    // in modules/game_features/features.lua
    CLIENTOS_OTCLIENTV8_LINUX(20),
    CLIENTOS_OTCLIENTV8_WINDOWS(21),
    CLIENTOS_OTCLIENTV8_MAC(22),
    CLIENTOS_OTCLIENTV8_ANDROID(23),
    CLIENTOS_OTCLIENTV8_IOS(24),
    CLIENTOS_OTCLIENTV8_WEB(25),
}

/**
 * Self-contained mirror of the Player client/session accessors
 * (src/creatures/players/player.cpp):
 *
 * - `Player::getOperatingSystem` / `setOperatingSystem` (1010-1016)
 * - `Player::isOldProtocol` (1018-1020)
 * - `Player::getProtocolVersion` (1022-1030)
 * - `Player::isPlayerGroup` (7528-7530)
 * - `Player::getFirstID` / `getLastID` and the static ranges (216-223)
 *
 * `Player::client` is a `ProtocolGame` pointer owned by the network layer, so
 * its `oldProtocol` flag and `getVersion()` value are injected seams
 * (AGENTS.md 1.1). `Player::isPlayerGroup` reads `group->id`; the port keeps
 * the conservative `false` when no group is wired, where Canary dereferences
 * the pointer unconditionally (PARITY GAP).
 */
class PlayerClientState(
    /** The host `Player` whose `group` the accessors read. */
    private val player: Player,
    /** `Client::oldProtocol` (`player.cpp:1019`). */
    private val clientOldProtocol: () -> Boolean = { false },
    /** `Client::getVersion()` (`player.cpp:1027`). */
    private val clientVersion: () -> Int = { 0 },
) {

    /** `Player::operatingSystem` (player.hpp:1854); default `CLIENTOS_NONE`. */
    private var operatingSystem: OperatingSystem_t = OperatingSystem_t.CLIENTOS_NONE

    /** `Player::getOperatingSystem` (player.cpp:1010-1012). */
    fun getOperatingSystem(): OperatingSystem_t = operatingSystem

    /** `Player::setOperatingSystem` (player.cpp:1014-1016). */
    fun setOperatingSystem(clientos: OperatingSystem_t) {
        operatingSystem = clientos
    }

    /** `Player::isOldProtocol` (player.cpp:1018-1020): `client && client->oldProtocol`. */
    fun isOldProtocol(): Boolean = clientOldProtocol()

    /**
     * `Player::getProtocolVersion` (player.cpp:1022-1030): the client version,
     * or 0 when no client is attached (the default seam).
     */
    fun getProtocolVersion(): Int = clientVersion()

    /**
     * `Player::isPlayerGroup` (player.cpp:7528-7530):
     * `group->id <= GROUP_TYPE_SENIORTUTOR` (account_group_type.hpp:20).
     */
    fun isPlayerGroup(): Boolean {
        val group = player.group ?: return false
        return group.id <= GROUP_TYPE_SENIORTUTOR
    }

    companion object {
        /** `GroupType::GROUP_TYPE_SENIORTUTOR` (account_group_type.hpp:20). */
        const val GROUP_TYPE_SENIORTUTOR = 3

        /** `Player::playerFirstID` (player.cpp:216). */
        const val playerFirstID = 0x10000000

        /** `Player::playerLastID` (player.cpp:217). */
        const val playerLastID = 0x50000000

        /** `Player::getFirstID` (player.cpp:218-220). */
        fun getFirstID(): Int = playerFirstID

        /** `Player::getLastID` (player.cpp:221-223). */
        fun getLastID(): Int = playerLastID
    }
}
