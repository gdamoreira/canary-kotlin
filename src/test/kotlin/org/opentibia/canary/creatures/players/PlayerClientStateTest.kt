package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player client/session accessor family:
 *
 * - `Player::getOperatingSystem` / `setOperatingSystem` (player.cpp:1010-1016)
 * - `Player::isOldProtocol` (player.cpp:1018-1020)
 * - `Player::getProtocolVersion` (player.cpp:1022-1030)
 * - `Player::isPlayerGroup` (player.cpp:7528-7530)
 * - `Player::getFirstID` / `getLastID` (player.cpp:218-223) and the
 *   `playerFirstID` / `playerLastID` statics (player.cpp:216-217)
 *
 * `OperatingSystem_t` values come from
 * src/creatures/creatures_definitions.hpp:737-756, the `GroupType` ceiling
 * `GROUP_TYPE_SENIORTUTOR = 3` from src/enums/account_group_type.hpp:20.
 * The `client` member is a protocol/network object, so its `oldProtocol` and
 * `version` reads are injected seams (AGENTS.md 1.1).
 */
class PlayerClientStateTest {

    private fun newPlayer() = Player(
        id = 5,
        name = "Session Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun newState(
        player: Player = newPlayer(),
        oldProtocol: Boolean = false,
        version: Int = 0,
    ) = PlayerClientState(
        player = player,
        clientOldProtocol = { oldProtocol },
        clientVersion = { version },
    )

    @Test
    fun `operating system defaults to CLIENTOS_NONE and round trips`() {
        // player.hpp:1854 `OperatingSystem_t operatingSystem = CLIENTOS_NONE;`
        val state = newState()

        assertThat(state.getOperatingSystem()).isEqualTo(OperatingSystem_t.CLIENTOS_NONE)

        state.setOperatingSystem(OperatingSystem_t.CLIENTOS_OTCLIENT_WINDOWS)
        assertThat(state.getOperatingSystem()).isEqualTo(OperatingSystem_t.CLIENTOS_OTCLIENT_WINDOWS)
    }

    @Test
    fun `operating system enum mirrors the Canary values`() {
        assertThat(OperatingSystem_t.CLIENTOS_NONE.value).isZero()
        assertThat(OperatingSystem_t.CLIENTOS_LINUX.value).isEqualTo(1)
        assertThat(OperatingSystem_t.CLIENTOS_WINDOWS.value).isEqualTo(2)
        assertThat(OperatingSystem_t.CLIENTOS_FLASH.value).isEqualTo(3)
        assertThat(OperatingSystem_t.CLIENTOS_NEW_LINUX.value).isEqualTo(4)
        assertThat(OperatingSystem_t.CLIENTOS_NEW_WINDOWS.value).isEqualTo(5)
        assertThat(OperatingSystem_t.CLIENTOS_NEW_MAC.value).isEqualTo(6)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENT_LINUX.value).isEqualTo(10)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENT_WINDOWS.value).isEqualTo(11)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENT_MAC.value).isEqualTo(12)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENTV8_LINUX.value).isEqualTo(20)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENTV8_WINDOWS.value).isEqualTo(21)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENTV8_MAC.value).isEqualTo(22)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENTV8_ANDROID.value).isEqualTo(23)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENTV8_IOS.value).isEqualTo(24)
        assertThat(OperatingSystem_t.CLIENTOS_OTCLIENTV8_WEB.value).isEqualTo(25)
    }

    @Test
    fun `old protocol and version read the injected client`() {
        // player.cpp:1018-1030: `client && client->oldProtocol` and
        // `client ? client->getVersion() : 0`.
        assertThat(newState(oldProtocol = true, version = 1310).isOldProtocol()).isTrue()
        assertThat(newState(oldProtocol = false).isOldProtocol()).isFalse()
        assertThat(newState(version = 1310).getProtocolVersion()).isEqualTo(1310)
        assertThat(newState().getProtocolVersion()).isZero()
    }

    @Test
    fun `isPlayerGroup is true up to the senior tutor group id`() {
        // player.cpp:7528-7530: group->id <= GROUP_TYPE_SENIORTUTOR.
        val normal = newPlayer().apply { group = Group(id = 1) }
        val tutor = newPlayer().apply { group = Group(id = 2) }
        val seniorTutor = newPlayer().apply { group = Group(id = 3) }
        val gamemaster = newPlayer().apply { group = Group(id = 4) }
        val god = newPlayer().apply { group = Group(id = 6) }
        val noGroup = newPlayer()

        assertThat(newState(player = normal).isPlayerGroup()).isTrue()
        assertThat(newState(player = tutor).isPlayerGroup()).isTrue()
        assertThat(newState(player = seniorTutor).isPlayerGroup()).isTrue()
        assertThat(newState(player = gamemaster).isPlayerGroup()).isFalse()
        assertThat(newState(player = god).isPlayerGroup()).isFalse()
        // C++ dereferences `group` unconditionally; the port keeps the
        // conservative false for an unwired group (documented PARITY GAP).
        assertThat(newState(player = noGroup).isPlayerGroup()).isFalse()
    }

    @Test
    fun `player id range mirrors the static bounds`() {
        // player.cpp:216-223.
        assertThat(PlayerClientState.playerFirstID).isEqualTo(0x10000000)
        assertThat(PlayerClientState.playerLastID).isEqualTo(0x50000000)
        assertThat(PlayerClientState.getFirstID()).isEqualTo(0x10000000)
        assertThat(PlayerClientState.getLastID()).isEqualTo(0x50000000)
    }

    @Test
    fun `connection protection defaults false and round trips`() {
        // player.hpp:1889 `bool connProtected = false;` and
        // player.cpp:3479-3484.
        val state = newState()

        assertThat(state.isProtected()).isFalse()

        state.setProtection(true)
        assertThat(state.isProtected()).isTrue()

        state.setProtection(false)
        assertThat(state.isProtected()).isFalse()
    }
}
