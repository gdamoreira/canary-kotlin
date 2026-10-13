package org.opentibia.canary.account

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the Canary `Account` state surface:
 *  - `AccountInfo` (src/account/account_info.hpp:19-32)
 *  - `AccountType` (src/enums/account_type.hpp:16-24)
 *  - `AccountErrors_t` (src/enums/account_errors.hpp:16-23)
 *  - `Account::getID/getDescriptor` (src/account/account.cpp:184-190)
 *  - `Account::setAccountType/getAccountType` (src/account/account.cpp:234-241)
 *  - `Account::getAccountPlayers` (src/account/account.cpp:271-276)
 *  - `Account::setProtocolCompat/getProtocolCompat` (src/account/account.cpp:278-283)
 *  - `Account::getAccountAgeInDays` (src/account/account.cpp:315-317)
 *  - `Account::setHouseBidId/getHouseBidId` (src/account/account.cpp:323-328)
 */
class AccountStateTest {

    private val now = 1_700_000_000L

    @Test
    fun `account type enum values match canary`() {
        assertThat(AccountType.ACCOUNT_TYPE_NONE.value).isEqualTo(0)
        assertThat(AccountType.ACCOUNT_TYPE_NORMAL.value).isEqualTo(1)
        assertThat(AccountType.ACCOUNT_TYPE_TUTOR.value).isEqualTo(2)
        assertThat(AccountType.ACCOUNT_TYPE_SENIORTUTOR.value).isEqualTo(3)
        assertThat(AccountType.ACCOUNT_TYPE_GAMEMASTER.value).isEqualTo(4)
        assertThat(AccountType.ACCOUNT_TYPE_COMMUNITYMANAGER.value).isEqualTo(5)
        assertThat(AccountType.ACCOUNT_TYPE_GOD.value).isEqualTo(6)
    }

    @Test
    fun `account errors enum values match canary`() {
        assertThat(AccountErrors.Ok.value).isEqualTo(0)
        assertThat(AccountErrors.Storage.value).isEqualTo(1)
        assertThat(AccountErrors.RemoveCoins.value).isEqualTo(2)
        assertThat(AccountErrors.InvalidLastDay.value).isEqualTo(3)
        assertThat(AccountErrors.LoadingAccount.value).isEqualTo(4)
        assertThat(AccountErrors.NotInitialized.value).isEqualTo(5)
    }

    @Test
    fun `account info defaults mirror canary`() {
        val info = AccountInfo()

        assertThat(info.id).isZero()
        assertThat(info.premiumRemainingDays).isZero()
        assertThat(info.premiumLastDay).isZero()
        assertThat(info.accountType).isEqualTo(AccountType.ACCOUNT_TYPE_NONE)
        assertThat(info.players).isEmpty()
        assertThat(info.oldProtocol).isFalse()
        assertThat(info.sessionExpires).isZero()
        assertThat(info.premiumDaysPurchased).isZero()
        assertThat(info.creationTime).isZero()
        assertThat(info.houseBidId).isZero()
    }

    @Test
    fun `new accounts default to the normal account type`() {
        assertThat(Account().getAccountType()).isEqualTo(AccountType.ACCOUNT_TYPE_NORMAL)
    }

    @Test
    fun `set account type stores the type and returns ok`() {
        val account = Account(id = 7, name = "hero")

        assertThat(account.setAccountType(AccountType.ACCOUNT_TYPE_GOD)).isEqualTo(AccountErrors.Ok)

        assertThat(account.getAccountType()).isEqualTo(AccountType.ACCOUNT_TYPE_GOD)
    }

    @Test
    fun `get id and descriptor expose the constructor values`() {
        val account = Account(id = 7, name = "hero")

        assertThat(account.getID()).isEqualTo(7)
        assertThat(account.getDescriptor()).isEqualTo("hero")
    }

    @Test
    fun `house bid defaults to zero and stores the set value`() {
        val account = Account(id = 7, name = "hero")

        assertThat(account.getHouseBidId()).isZero()

        account.setHouseBidId(42)

        assertThat(account.getHouseBidId()).isEqualTo(42)
    }

    @Test
    fun `protocol compat defaults to false and toggles`() {
        val account = Account(id = 7, name = "hero")

        assertThat(account.getProtocolCompat()).isFalse()

        account.setProtocolCompat(true)

        assertThat(account.getProtocolCompat()).isTrue()
    }

    @Test
    fun `account age in days truncates partial days`() {
        val account = Account(creationTime = now - (3 * 86400L + 100))

        assertThat(account.getAccountAgeInDays(now)).isEqualTo(3)
    }

    @Test
    fun `account age is zero for an account created now`() {
        val account = Account(creationTime = now)

        assertThat(account.getAccountAgeInDays(now)).isZero()
    }

    @Test
    fun `account players require a loaded account`() {
        val account = Account(id = 7, name = "hero")

        assertThat(account.getAccountPlayers())
            .isEqualTo(emptyMap<String, ULong>() to AccountErrors.NotInitialized)
    }

    @Test
    fun `account players are exposed after load`() {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(
                id = 7,
                players = mutableMapOf("Knight Hero" to 1uL, "Sorcerer Master" to 2uL),
            )
        }
        val account = Account(id = 7, name = "hero", repository = repository)
        assertThat(account.load()).isEqualTo(AccountErrors.Ok)

        val (players, error) = account.getAccountPlayers()

        assertThat(error).isEqualTo(AccountErrors.Ok)
        assertThat(players).containsExactlyInAnyOrderEntriesOf(
            mapOf("Knight Hero" to 1uL, "Sorcerer Master" to 2uL)
        )
    }
}
