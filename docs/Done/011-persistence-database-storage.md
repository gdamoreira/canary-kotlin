# Feature: Persistence & Database (Exposed / SQL & JSON Storage)

## 1. Explanation
Handles database persistence for player accounts, characters, items, storage key-values, houses, bans, and guilds. Canary uses MySQL/MariaDB with prepared statements; in Kotlin, this maps to an asynchronous or pooled ORM/DAO layer such as JetBrains Exposed, Ktorm, or jOOQ.

## 2. Impact
Data integrity, server crash resilience, character save/load operations, and web integration (e.g., MyAAC, Gesior).

## 3. Functionality Affected
- **Account & Player Persistence**: Load on login, save on logout, periodic auto-save.
- **Player Items & Depot Serialization**: Recursive serialization of nested containers and item attributes.
- **House Ownership & Items**: Persistence of house tiles, door permissions, and rents.
- **Server Shutdown & Crash Recovery**: Atomic transaction commit on server stop.

## 4. Agent Implementation Details
- Integrate JetBrains Exposed or HikariCP + SQL connection pool with virtual threads.
- Define schemas for `accounts`, `players`, `player_items`, `player_depotitems`, `player_storage`, `houses`.
- Implement non-blocking asynchronous save worker dispatched off the main game tick loop.
- Support JSON fallback repository for local/testing development.

## 5. Test Reference
- `org.opentibia.canary.database.PlayerPersistenceTest`: Save player with nested backpack items, reload into new instance, assert identity and properties.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
