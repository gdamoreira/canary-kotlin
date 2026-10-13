package org.opentibia.canary.creatures.players.management

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for `IOBan`
 * (src/creatures/players/management/ban.hpp:45-50, ban.cpp:80-143).
 */
class IOBanTest {

    private data class HistoryCall(
        val accountId: Long,
        val reason: String,
        val bannedAt: Long,
        val expiredAt: Long,
        val bannedBy: Long,
    )

    private class RecordingBanRepository(
        var accountBan: AccountBanRecord? = null,
        var ipBan: IpBanRecord? = null,
        var namelocked: Boolean = false,
    ) : BanRepository {
        var accountReads = 0
        var ipReads = 0
        val history = mutableListOf<HistoryCall>()
        val deletedAccountBans = mutableListOf<Long>()
        val deletedIpBans = mutableListOf<Long>()

        override fun getAccountBan(accountId: Long): AccountBanRecord? {
            accountReads++
            return accountBan
        }

        override fun addAccountBanHistory(
            accountId: Long,
            reason: String,
            bannedAt: Long,
            expiredAt: Long,
            bannedBy: Long,
        ) {
            history.add(HistoryCall(accountId, reason, bannedAt, expiredAt, bannedBy))
        }

        override fun deleteAccountBan(accountId: Long) {
            deletedAccountBans.add(accountId)
        }

        override fun getIpBan(clientIP: Long): IpBanRecord? {
            ipReads++
            return ipBan
        }

        override fun deleteIpBan(clientIP: Long) {
            deletedIpBans.add(clientIP)
        }

        override fun isPlayerNamelocked(playerId: Long): Boolean = namelocked
    }

    @Test
    fun `isAccountBanned fills the info for an active ban`() {
        val repository = RecordingBanRepository(
            accountBan = AccountBanRecord(
                reason = "cheating",
                expiresAt = 2_000L,
                bannedAt = 1_000L,
                bannedBy = 7L,
                bannedByName = "God",
            ),
        )
        val ioBan = IOBan(repository, now = { 1_500L })
        val info = BanInfo()

        assertThat(ioBan.isAccountBanned(42L, info)).isTrue()

        assertThat(info).isEqualTo(BanInfo(bannedBy = "God", reason = "cheating", expiresAt = 2_000L))
        assertThat(repository.history).isEmpty()
        assertThat(repository.deletedAccountBans).isEmpty()
    }

    @Test
    fun `isAccountBanned treats expiresAt zero as a permanent ban`() {
        val repository = RecordingBanRepository(
            accountBan = AccountBanRecord(reason = "permanent", expiresAt = 0L, bannedAt = 10L, bannedBy = 1L, bannedByName = "Admin"),
        )
        val ioBan = IOBan(repository, now = { 1_000_000L })
        val info = BanInfo()

        assertThat(ioBan.isAccountBanned(5L, info)).isTrue()

        assertThat(info.expiresAt).isEqualTo(0L)
        assertThat(info.reason).isEqualTo("permanent")
        assertThat(info.bannedBy).isEqualTo("Admin")
    }

    @Test
    fun `isAccountBanned keeps a ban that expires exactly now`() {
        val repository = RecordingBanRepository(
            accountBan = AccountBanRecord(reason = "edge", expiresAt = 1_000L, bannedAt = 0L, bannedBy = 0L, bannedByName = ""),
        )

        assertThat(IOBan(repository, now = { 1_000L }).isAccountBanned(5L, BanInfo())).isTrue()
        assertThat(repository.deletedAccountBans).isEmpty()
    }

    @Test
    fun `isAccountBanned moves an expired ban to history and deletes it`() {
        val repository = RecordingBanRepository(
            accountBan = AccountBanRecord(
                reason = "expired",
                expiresAt = 1_000L,
                bannedAt = 100L,
                bannedBy = 3L,
                bannedByName = "GM",
            ),
        )
        val ioBan = IOBan(repository, now = { 1_001L })
        val info = BanInfo()

        assertThat(ioBan.isAccountBanned(9L, info)).isFalse()

        assertThat(repository.history)
            .containsExactly(HistoryCall(accountId = 9L, reason = "expired", bannedAt = 100L, expiredAt = 1_000L, bannedBy = 3L))
        assertThat(repository.deletedAccountBans).containsExactly(9L)
        assertThat(info).isEqualTo(BanInfo())
    }

    @Test
    fun `isAccountBanned returns false without touching the repository when no row exists`() {
        val repository = RecordingBanRepository()
        val info = BanInfo()

        assertThat(IOBan(repository, now = { 1_000L }).isAccountBanned(42L, info)).isFalse()

        assertThat(repository.accountReads).isEqualTo(1)
        assertThat(repository.history).isEmpty()
        assertThat(repository.deletedAccountBans).isEmpty()
        assertThat(info).isEqualTo(BanInfo())
    }

    @Test
    fun `isIpBanned rejects ip zero without querying`() {
        val repository = RecordingBanRepository(ipBan = IpBanRecord(reason = "x", expiresAt = 0L, bannedByName = "GM"))

        assertThat(IOBan(repository, now = { 1_000L }).isIpBanned(0L, BanInfo())).isFalse()

        assertThat(repository.ipReads).isZero()
    }

    @Test
    fun `isIpBanned fills the info for an active ban`() {
        val repository = RecordingBanRepository(
            ipBan = IpBanRecord(reason = "flooding", expiresAt = 5_000L, bannedByName = "GM"),
        )
        val ioBan = IOBan(repository, now = { 4_000L })
        val info = BanInfo()

        assertThat(ioBan.isIpBanned(0x0A000001L, info)).isTrue()

        assertThat(info).isEqualTo(BanInfo(bannedBy = "GM", reason = "flooding", expiresAt = 5_000L))
        assertThat(repository.deletedIpBans).isEmpty()
    }

    @Test
    fun `isIpBanned deletes an expired ban`() {
        val repository = RecordingBanRepository(
            ipBan = IpBanRecord(reason = "flooding", expiresAt = 5_000L, bannedByName = "GM"),
        )
        val ioBan = IOBan(repository, now = { 5_001L })
        val info = BanInfo()

        assertThat(ioBan.isIpBanned(11L, info)).isFalse()

        assertThat(repository.deletedIpBans).containsExactly(11L)
        assertThat(info).isEqualTo(BanInfo())
    }

    @Test
    fun `isPlayerNamelocked delegates to the repository`() {
        assertThat(IOBan(RecordingBanRepository(namelocked = true), now = { 0L }).isPlayerNamelocked(7L)).isTrue()
        assertThat(IOBan(RecordingBanRepository(namelocked = false), now = { 0L }).isPlayerNamelocked(7L)).isFalse()
    }
}
