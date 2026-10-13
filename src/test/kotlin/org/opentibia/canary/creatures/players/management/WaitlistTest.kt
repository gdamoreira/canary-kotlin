package org.opentibia.canary.creatures.players.management

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `WaitingList`
 * (src/creatures/players/management/waitlist.hpp:12-46, waitlist.cpp:18-134).
 */
class WaitlistTest {

    private fun player(
        id: Long,
        premium: Boolean = false,
        accountType: AccountType = AccountType.ACCOUNT_TYPE_NORMAL,
        canAlwaysLogin: Boolean = false,
    ): Player {
        val player = Player(
            id = id,
            name = "Player$id",
            maxHealth = 100,
            health = 100,
            position = Position(0, 0, 0),
        )
        player.premium = premium
        player.account = Account(id = id, accountType = accountType)
        if (canAlwaysLogin) {
            player.setFlag(PlayerFlags_t.CanAlwaysLogin)
        }
        return player
    }

    private class Fixture(maxPlayers: Int) {
        var playersOnline = 0
        var now = 0L
        val logs = mutableListOf<String>()
        val list = WaitingList(
            maxPlayers = { maxPlayers },
            playersOnline = { playersOnline },
            now = { now },
            logDebug = { logs.add(it) },
        )
    }

    @Test
    fun `getTime maps slots to the retry seconds`() {
        assertThat(WaitingList.getTime(0)).isEqualTo(5)
        assertThat(WaitingList.getTime(4)).isEqualTo(5)
        assertThat(WaitingList.getTime(5)).isEqualTo(10)
        assertThat(WaitingList.getTime(9)).isEqualTo(10)
        assertThat(WaitingList.getTime(10)).isEqualTo(20)
        assertThat(WaitingList.getTime(19)).isEqualTo(20)
        assertThat(WaitingList.getTime(20)).isEqualTo(60)
        assertThat(WaitingList.getTime(49)).isEqualTo(60)
        assertThat(WaitingList.getTime(50)).isEqualTo(120)
    }

    @Test
    fun `clientLogin admits a CanAlwaysLogin player while the server is full`() {
        val fixture = Fixture(maxPlayers = 1)
        fixture.playersOnline = 1
        val flagged = player(1, canAlwaysLogin = true)

        assertThat(fixture.list.clientLogin(flagged)).isTrue()
        assertThat(fixture.list.getClientSlot(flagged)).isZero()

        // The flagged login never entered the queue: a normal player still gets slot 1.
        val normal = player(2)
        assertThat(fixture.list.clientLogin(normal)).isFalse()
        assertThat(fixture.list.getClientSlot(normal)).isEqualTo(1)
    }

    @Test
    fun `clientLogin admits gamemaster accounts and queues tutors`() {
        val fixture = Fixture(maxPlayers = 1)
        fixture.playersOnline = 1

        assertThat(fixture.list.clientLogin(player(1, accountType = AccountType.ACCOUNT_TYPE_GAMEMASTER))).isTrue()

        val tutor = player(2, accountType = AccountType.ACCOUNT_TYPE_TUTOR)
        assertThat(fixture.list.clientLogin(tutor)).isFalse()
        assertThat(fixture.list.getClientSlot(tutor)).isEqualTo(1)
    }

    @Test
    fun `clientLogin admits when the server has free capacity`() {
        val fixture = Fixture(maxPlayers = 10)
        fixture.playersOnline = 5
        val player = player(1)

        assertThat(fixture.list.clientLogin(player)).isTrue()
        assertThat(fixture.list.getClientSlot(player)).isZero()
    }

    @Test
    fun `clientLogin admits when maxPlayers is zero`() {
        val fixture = Fixture(maxPlayers = 0)
        fixture.playersOnline = 100

        assertThat(fixture.list.clientLogin(player(1))).isTrue()
    }

    @Test
    fun `clientLogin queues normal players when the server is full`() {
        val fixture = Fixture(maxPlayers = 1)
        fixture.playersOnline = 1
        val first = player(1)
        val second = player(2)

        assertThat(fixture.list.clientLogin(first)).isFalse()
        assertThat(fixture.list.getClientSlot(first)).isEqualTo(1)

        assertThat(fixture.list.clientLogin(second)).isFalse()
        assertThat(fixture.list.getClientSlot(second)).isEqualTo(2)
    }

    @Test
    fun `clientLogin promotes a queued player when capacity frees`() {
        val fixture = Fixture(maxPlayers = 1)
        fixture.playersOnline = 1
        val player = player(1)
        assertThat(fixture.list.clientLogin(player)).isFalse()

        fixture.playersOnline = 0

        assertThat(fixture.list.clientLogin(player)).isTrue()
        assertThat(fixture.list.getClientSlot(player)).isZero()
    }

    @Test
    fun `premium players queue in the priority list ahead of free players`() {
        val fixture = Fixture(maxPlayers = 1)
        fixture.playersOnline = 1
        val free = player(1)
        val premium = player(2, premium = true)

        assertThat(fixture.list.clientLogin(free)).isFalse()
        assertThat(fixture.list.clientLogin(premium)).isFalse()

        assertThat(fixture.list.getClientSlot(premium)).isEqualTo(1)
        assertThat(fixture.list.getClientSlot(free)).isEqualTo(2)
    }

    @Test
    fun `re-queuing a player keeps a single entry`() {
        val fixture = Fixture(maxPlayers = 1)
        fixture.playersOnline = 1
        val player = player(1)

        assertThat(fixture.list.clientLogin(player)).isFalse()
        assertThat(fixture.list.clientLogin(player)).isFalse()
        assertThat(fixture.list.getClientSlot(player)).isEqualTo(1)

        val second = player(2)
        assertThat(fixture.list.clientLogin(second)).isFalse()
        assertThat(fixture.list.getClientSlot(second)).isEqualTo(2)
    }

    @Test
    fun `expired waiters are dropped before admitting a newcomer`() {
        val fixture = Fixture(maxPlayers = 2)
        fixture.playersOnline = 2
        val expired = player(1)
        assertThat(fixture.list.clientLogin(expired)).isFalse()
        assertThat(fixture.list.getClientSlot(expired)).isEqualTo(1)

        fixture.now = 20_001
        fixture.playersOnline = 1
        val newcomer = player(2)

        assertThat(fixture.list.clientLogin(newcomer)).isTrue()
        assertThat(fixture.list.getClientSlot(expired)).isZero()
        assertThat(fixture.list.getClientSlot(newcomer)).isZero()
    }

    @Test
    fun `a waiter survives until its timeout lapses`() {
        val fixture = Fixture(maxPlayers = 2)
        fixture.playersOnline = 2
        val first = player(1)
        assertThat(fixture.list.clientLogin(first)).isFalse()

        fixture.now = 19_999
        val second = player(2)
        assertThat(fixture.list.clientLogin(second)).isFalse()
        assertThat(fixture.list.getClientSlot(first)).isEqualTo(1)
        assertThat(fixture.list.getClientSlot(second)).isEqualTo(2)

        fixture.now = 20_000
        val third = player(3)
        assertThat(fixture.list.clientLogin(third)).isFalse()
        assertThat(fixture.list.getClientSlot(first)).isZero()
        assertThat(fixture.list.getClientSlot(second)).isEqualTo(1)
        assertThat(fixture.list.getClientSlot(third)).isEqualTo(2)
    }

    @Test
    fun `admission removes the queued entry from its owning list`() {
        val fixture = Fixture(maxPlayers = 1)
        fixture.playersOnline = 1
        val premium = player(1, premium = true)
        val free = player(2)

        assertThat(fixture.list.clientLogin(premium)).isFalse()
        assertThat(fixture.list.clientLogin(free)).isFalse()
        assertThat(fixture.list.getClientSlot(free)).isEqualTo(2)

        fixture.playersOnline = 0
        assertThat(fixture.list.clientLogin(premium)).isTrue()
        assertThat(fixture.list.getClientSlot(premium)).isZero()
        assertThat(fixture.list.getClientSlot(free)).isEqualTo(1)
        assertThat(fixture.list.clientLogin(free)).isTrue()
    }

    @Test
    fun `getClientSlot returns zero for an unknown player`() {
        val fixture = Fixture(maxPlayers = 1)

        assertThat(fixture.list.getClientSlot(player(1))).isZero()
    }

    @Test
    fun `cleanupList logs the remaining wait time`() {
        val fixture = Fixture(maxPlayers = 1)
        fixture.playersOnline = 1
        val queued = player(1)
        assertThat(fixture.list.clientLogin(queued)).isFalse()

        fixture.now = 5_000
        fixture.list.clientLogin(player(2))

        assertThat(fixture.logs).containsExactly("[cleanupList] time: 15000")
    }
}
