# Feature: Relational Database Persistence (Exposed / SQL)

## 1. Explanation
Connects the in-memory persistence layer (`PlayerStorage`, `AccountRepository`) to a real relational database (MySQL/MariaDB/PostgreSQL/H2) via JetBrains Exposed (`src/database/`, `src/io/iologindata.cpp`). Defines tables for `accounts`, `players`, `player_items`, `player_storage`, `guilds`, `houses`, and `market_offers`.

## 2. Impact
Production data persistence. Allows external account managers (MyAAC, Znote AAC) to register accounts and characters, and ensures all player inventory items, stats, and positions survive server crashes and restarts.

## 3. Functionality Affected
- **Connection Pool**: HikariCP pooled database connections running non-blocking operations on IO/Virtual Threads.
- **Table Schemas**: Standard OpenTibia / Canary schema mapping.
- **Nested Item Tree Serialization**: Serializing player inventory slots (1..10) and nested backpacks with recursion depth and item attributes.

## 4. Agent Implementation Details
- Add `org.jetbrains.exposed:exposed-core`, `exposed-dao`, `exposed-jdbc`, and `com.zaxxer:HikariCP` to `build.gradle.kts`.
- Implement Exposed `Table` definitions: `AccountsTable`, `PlayersTable`, `PlayerItemsTable`.
- Implement `ExposedPlayerRepository.kt` and `ExposedAccountRepository.kt`.

## 5. Test Reference
- `org.opentibia.canary.database.ExposedDatabaseTest`: Tests running against an in-memory H2 database, persisting an account with multiple characters and reloading.
