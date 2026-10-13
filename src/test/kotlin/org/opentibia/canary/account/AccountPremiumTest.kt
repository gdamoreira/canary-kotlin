package org.opentibia.canary.account

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccountPremiumTest {

    private val now = 1_700_000_000L

    private fun account(lastDay: Long = 0) = Account(
        id = 1,
        name = "premium-hero",
        passwordHash = "hash",
        premiumLastDay = lastDay,
    )

    @Test
    fun `getPremiumRemainingDays derives whole days from the premium last day`() {
        val account = account(lastDay = now + 10 * 86400L + 3_600L)

        assertThat(account.getPremiumRemainingDays(now)).isEqualTo(10)
    }

    @Test
    fun `expired account reports zero remaining days`() {
        val account = account(lastDay = now - 1)

        assertThat(account.getPremiumRemainingDays(now)).isZero()
        assertThat(account.getPremiumLastDay()).isEqualTo(now - 1)
    }

    @Test
    fun `setPremiumDays stamps the last day and remaining days`() {
        val updated = account().setPremiumDays(30, now)

        assertThat(updated.premiumRemainingDays).isEqualTo(30)
        assertThat(updated.premiumLastDay).isEqualTo(now + 30 * 86400L)
        assertThat(updated.getPremiumRemainingDays(now)).isEqualTo(30)
    }

    @Test
    fun `setPremiumDays with no days clears premium`() {
        val updated = account(lastDay = now + 86400L).setPremiumDays(0, now)

        assertThat(updated.premiumRemainingDays).isZero()
        assertThat(updated.premiumLastDay).isZero()
    }

    @Test
    fun `new accounts report zero premium days purchased`() {
        assertThat(account().getPremiumDaysPurchased()).isZero()
    }

    @Test
    fun `addPremiumDays extends the remaining days and records the purchase`() {
        val account = Account(
            id = 1,
            name = "premium-hero",
            passwordHash = "hash",
            premiumRemainingDays = 10,
            premiumLastDay = now + 10 * 86400L + 3_600L,
        )

        account.addPremiumDays(5, now)

        assertThat(account.premiumRemainingDays).isEqualTo(15)
        assertThat(account.getPremiumLastDay()).isEqualTo(now + 15 * 86400L + 3_600L)
        assertThat(account.getPremiumDaysPurchased()).isEqualTo(5)
    }

    @Test
    fun `addPremiumDays starts from an expired premium and purchases the full amount`() {
        val account = Account(
            id = 1,
            name = "premium-hero",
            passwordHash = "hash",
            premiumLastDay = now - 1,
        )

        account.addPremiumDays(30, now)

        assertThat(account.premiumRemainingDays).isEqualTo(30)
        assertThat(account.getPremiumLastDay()).isEqualTo(now + 30 * 86400L)
        assertThat(account.getPremiumDaysPurchased()).isEqualTo(30)
    }

    @Test
    fun `addPremiumDays accumulates the purchased days`() {
        val account = Account(
            id = 1,
            name = "premium-hero",
            passwordHash = "hash",
            premiumRemainingDays = 10,
            premiumLastDay = now + 10 * 86400L,
            premiumDaysPurchased = 30,
        )

        account.addPremiumDays(5, now)

        assertThat(account.getPremiumDaysPurchased()).isEqualTo(35)
    }

    @Test
    fun `updatePremiumTime recomputes the remaining days and saves`() {
        val repository = FakeAccountRepository()
        val account = loadedAccount(repository, lastDay = now + 5 * 86400L, remainingDays = 10)

        account.updatePremiumTime(now)

        assertThat(account.premiumRemainingDays).isEqualTo(5)
        assertThat(repository.savedInfos).hasSize(1)
        assertThat(repository.savedInfos.single().premiumRemainingDays).isEqualTo(5)
    }

    @Test
    fun `updatePremiumTime keeps the fraction of the current premium day`() {
        val repository = FakeAccountRepository()
        val account = loadedAccount(repository, lastDay = now + 5 * 86400L + 3_600L, remainingDays = 10)

        account.updatePremiumTime(now)

        assertThat(account.premiumRemainingDays).isEqualTo(5)
        assertThat(account.getPremiumLastDay()).isEqualTo(now + 5 * 86400L + 3_600L)
    }

    @Test
    fun `updatePremiumTime clears an expired premium and saves`() {
        val repository = FakeAccountRepository()
        val account = loadedAccount(repository, lastDay = now - 1, remainingDays = 3)

        account.updatePremiumTime(now)

        assertThat(account.premiumRemainingDays).isZero()
        assertThat(account.getPremiumLastDay()).isZero()
        assertThat(repository.savedInfos).hasSize(1)
    }

    @Test
    fun `updatePremiumTime skips the save when the remaining days are unchanged`() {
        val repository = FakeAccountRepository()
        val account = loadedAccount(repository, lastDay = now + 5 * 86400L, remainingDays = 5)

        account.updatePremiumTime(now)

        assertThat(account.premiumRemainingDays).isEqualTo(5)
        assertThat(repository.savedInfos).isEmpty()
    }

    private fun loadedAccount(
        repository: FakeAccountRepository,
        lastDay: Long,
        remainingDays: Int,
    ): Account {
        repository.infoById[1] = AccountInfo(
            id = 1,
            premiumLastDay = lastDay,
            premiumRemainingDays = remainingDays,
        )
        return Account(id = 1, name = "premium-hero", passwordHash = "hash", repository = repository).apply {
            assertThat(load()).isEqualTo(AccountErrors.Ok)
        }
    }
}
