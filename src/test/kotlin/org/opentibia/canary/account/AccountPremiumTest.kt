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
}
