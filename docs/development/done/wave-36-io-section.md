# Wave 36 Plan — `src/io/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/io/` section (9 rows: 8 PARTIAL, 1 MISSING). Goal: close every row.

## Migration budget (avoid Flyway version collisions across agents)

Existing migrations: V1–V9. Each agent uses its assigned versions only; if no schema change is needed, add none.

| Agent | Assigned migration versions |
|---|---|
| A1 iologindata | V10 |
| A2 map serialize | V11 |
| A3 market + prey | V12 |
| A4 bestiary/bosstiary/guild | V13 |
| A5 storage repo | V14 |

## Tasks

### Task A1 — `iologindata` depth (`feat/io-logindata`)
- **Canary:** `src/io/iologindata.cpp` (24 methods).
- **Deliverable:** extend the player load/save repository for inventory/depot/inbox/stash/spells/kills/storage/VIP/guild/outfits/rewards/prey/imbuements + auth, bank, GUID lookups; H2/PostgreSQL compatible.
- **Tests:** H2 round-trips transcribing Canary behavior.

### Task A2 — Map serialization (`feat/io-map-serialize`)
- **Canary:** `src/io/iomapserialize.cpp` (10 methods), `src/io/iomap.cpp` nodes.
- **Deliverable:** house items/info load+save, tile serialization guards, `iomap.cpp` houses/spawns/zones nodes + extended tile attributes.
- **Tests:** H2/XML round-trips transcribing Canary behavior.

### Task A3 — Market & prey persistence (`feat/io-market-prey`)
- **Canary:** `src/io/iomarket.cpp` (19 methods), `src/io/ioprey.cpp`.
- **Deliverable:** market DB persistence, expiration, history, statistics, escrow; prey runtime/actions/reroll persistence.
- **Tests:** H2 round-trips transcribing Canary behavior.

### Task A4 — Bestiary, bosstiary & guild persistence (`feat/io-bestiary-bosstiary-guild`)
- **Canary:** `src/io/iobestiary.cpp`, `src/io/io_bosstiary.cpp`, `src/io/ioguild.cpp`.
- **Deliverable:** kill-counter persistence/tracker/animus mastery, boss points/slots/cooldowns, guild DB load/save/ranks/wars.
- **Tests:** H2 round-trips transcribing Canary behavior.

### Task A5 — Typed player storage repository (`feat/io-storage-repo`)
- **Canary:** `src/io/player_storage_repository*.cpp`.
- **Deliverable:** typed storage repo (add/remove/`upsertKey`/delta/ranges) with the exact Canary key semantics.
- **Tests:** H2 round-trips transcribing Canary behavior.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; Flyway migration versions do not collide.

## Post-wave (AGENTS.md §6)

Flip all `src/io/` rows to `IMPLEMENTED`; update `docs/development/missing-features.md` §1–§3, `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

## Completion

Completed. All 9 `src/io/` rows are now IMPLEMENTED (iologindata 24/24, iomapserialize 10/10, iomap nodes, iomarket 18/18, ioprey, iobestiary, io_bosstiary, ioguild, player_storage_repository); migrations V10–V14.
