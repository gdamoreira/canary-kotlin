# Wave 25 Plan — `src/account/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** Second section of `docs/missing-features.md` §2 (`src/account/`). Goal: turn the `PARTIAL` rows in that section into `IMPLEMENTED`.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `account.cpp/.hpp` (`Account`) | PARTIAL | IMPLEMENTED — authenticate/session/load/save, account type, house bid, premium purchase, coin ledger methods |
| `account_repository_db.cpp` (`AccountRepositoryDB`) | PARTIAL | IMPLEMENTED — account create/save, coin transactions, ban/session columns |
| `account_repository.cpp`, `account_info.hpp` | PARTIAL | IMPLEMENTED — coin-transaction history, session auth fields |

## Tasks

### Task 1 — Account core + info (`feat/account-core`)
- **Canary sources:** `src/account/account.cpp/.hpp`, `src/account/account_info.hpp`, `src/enums/account_type.hpp`, `account_errors.hpp`.
- **Deliverable:** extend `account/Account.kt` with `authenticate`/`authenticatePassword`/`authenticateSession`, `load`/`reload`/`save`, `setAccountType`, `houseBid`, premium purchase days, and the coin ledger methods (`addCoins`/`removeCoins`/`registerCoinTransaction`); add `AccountInfo` and the account type/error enums.
- **Tests:** authentication paths, premium days, house bid, coin add/remove.
- **Constraints:** `account/**` (+ additive); no Lua.

### Task 2 — Account repository (`feat/account-repo`)
- **Canary sources:** `src/database/account_repository_db.cpp`, `src/database/account_repository.cpp`.
- **Deliverable:** extend `database/JdbcAccountRepository.kt` with account create/save, coin ledger persistence (`coins`/`coins_tournament`/`coins_transferable` + `coins_transactions`), and ban/session columns; add Flyway `V7__account_details.sql`.
- **Tests:** H2 round-trip (create/save account, coin transaction history, session fields).
- **Constraints:** `database`, `src/main/resources/db/migration`; no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

## Post-wave (AGENTS.md §6)

Flip the `src/account/` rows to `IMPLEMENTED` in `docs/missing-features.md`; update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.
