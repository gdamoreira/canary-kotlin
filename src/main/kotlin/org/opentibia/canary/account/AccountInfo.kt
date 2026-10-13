package org.opentibia.canary.account

/**
 * Mirror of Canary `enum AccountType : uint8_t`
 * (src/enums/account_type.hpp:16-24); enumerator names and values are preserved.
 */
enum class AccountType(val value: Int) {
    ACCOUNT_TYPE_NONE(0),
    ACCOUNT_TYPE_NORMAL(1),
    ACCOUNT_TYPE_TUTOR(2),
    ACCOUNT_TYPE_SENIORTUTOR(3),
    ACCOUNT_TYPE_GAMEMASTER(4),
    ACCOUNT_TYPE_COMMUNITYMANAGER(5),
    ACCOUNT_TYPE_GOD(6),
}

/**
 * Mirror of Canary `enum class AccountErrors_t : uint8_t`
 * (src/enums/account_errors.hpp:16-23). Returned by the account storage
 * operations exactly as in `Account::load/save/getCoins/addCoins/removeCoins`.
 */
enum class AccountErrors(val value: Int) {
    Ok(0),
    Storage(1),
    RemoveCoins(2),
    InvalidLastDay(3),
    LoadingAccount(4),
    NotInitialized(5),
}

/** Mirror of Canary `enum class CoinType : uint8_t` (src/enums/account_coins.hpp:17-20). */
enum class CoinType(val value: Int) {
    Normal(1),
    Tournament(2),
    Transferable(3),
}

/** Mirror of Canary `enum class CoinTransactionType : uint8_t` (src/enums/account_coins.hpp:12-15). */
enum class CoinTransactionType(val value: Int) {
    Add(1),
    Remove(2),
}

/**
 * Result of the two-coin removal. Canary models this with the `AccountErrors_t`
 * return plus the `primaryCoinsRemoved`/`secondaryCoinsRemoved` out-parameters of
 * `AccountRepository::removeCoins` (src/account/account_repository.hpp:40-48);
 * Kotlin folds the out-parameters into this record.
 */
data class RemoveCoinsResult(
    val error: AccountErrors,
    val primaryCoinsRemoved: UInt = 0u,
    val secondaryCoinsRemoved: UInt = 0u,
)

/**
 * Mirror of Canary `struct AccountInfo` (src/account/account_info.hpp:19-32).
 *
 * It is the mutable payload exchanged with the [AccountRepository] seam:
 * `loadByID`/`loadByEmailOrName`/`loadBySession` fill it and `save` consumes it,
 * exactly like the C++ `std::unique_ptr<AccountInfo>` out-parameter.
 */
class AccountInfo(
    var id: Long = 0,
    var premiumRemainingDays: Int = 0,
    var premiumLastDay: Long = 0,
    var accountType: AccountType = AccountType.ACCOUNT_TYPE_NONE,
    var players: MutableMap<String, ULong> = mutableMapOf(),
    var oldProtocol: Boolean = false,
    var sessionExpires: Long = 0,
    var premiumDaysPurchased: Int = 0,
    var creationTime: Long = 0,
    var houseBidId: Int = 0,
)
