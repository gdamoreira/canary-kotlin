# Wave 33 Plan — `src/database/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/database/` section (newly added to `docs/missing-features.md`). Goal: turn the `PARTIAL` rows into `IMPLEMENTED`.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `database.cpp/.hpp` (`Database`) | PARTIAL | IMPLEMENTED — transactions, escaping, retry, recoverable errors, query size limits |
| `databasemanager.cpp` | PARTIAL | IMPLEMENTED — pool lifecycle/reconnect, `executeQuery`/`storeQuery` parity |
| `database_definitions.hpp` | PARTIAL | IMPLEMENTED — protocol/result enums |

## Tasks

### Task 1 — Database core (`feat/database-core`)
- **Canary sources:** `src/database/database.cpp/.hpp`.
- **Deliverable:** `database/Database.kt`: connection/statement helpers with transactions, SQL escaping, retry on recoverable errors, and query-size limits, over an injected `DataSource`.
- **Tests:** transcribe real Canary behavior (transaction commit/rollback, escaping, retry).
- **Constraints:** `database` (new file); no Lua.

### Task 2 — Database manager (`feat/database-manager`)
- **Canary sources:** `src/database/databasemanager.cpp/.hpp`.
- **Deliverable:** extend `database/DatabaseConnectionFactory.kt` with pool lifecycle/reconnect and the `executeQuery`/`storeQuery` parity surface.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `database`; no Lua.

### Task 3 — Database definitions (`feat/database-definitions`)
- **Canary sources:** `src/database/database_definitions.hpp`.
- **Deliverable:** `database/DatabaseDefinitions.kt` with the protocol/result enums (`DatabaseErrors_t`, `DBResult_t`, etc.) and exact values.
- **Tests:** enum value parity.
- **Constraints:** `database` (new file); no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip the `src/database/` rows to `IMPLEMENTED` in `docs/missing-features.md`; update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.

## Completion

Completed. Database core (transactions/escaping/retry/size limits), manager (pool lifecycle + executeQuery/storeQuery parity), and definitions. Rows flipped.
