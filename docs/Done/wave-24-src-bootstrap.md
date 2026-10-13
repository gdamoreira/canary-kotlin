# Wave 24 Plan — `src/` Bootstrap (CanaryServer, main, appearances, DB tasks)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** First section of `docs/missing-features.md` §2 (`src/` bootstrap). Goal: turn the `PARTIAL` rows in that section into `IMPLEMENTED`.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `canary_server.cpp/.hpp` | PARTIAL | IMPLEMENTED — appearance/protobuf loading, service bootstrap, DB tasks wiring, shutdown drain |
| `main.cpp` | PARTIAL | IMPLEMENTED — CLI/arg handling, signal-init sequence, crash handler |
| `src/protobuf/appearances.proto` | MISSING | IMPLEMENTED — appearance loading/tables |

## Tasks

### Task 1 — Service bootstrap + CLI/signals (`feat/server-bootstrap`)
- **Canary sources:** `src/canary_server.cpp/.hpp`, `src/main.cpp`, `src/signals.cpp`, `src/server/server.cpp`.
- **Deliverable:** `Main.kt`/`CanaryServer.kt`: CLI argument parsing (config path, data dir, db options), SIGINT/SIGTERM signal-init sequence, uncaught-exception crash handler, full service bootstrap (config → DB → map → scripting → appearance load → listeners), and an **idempotent shutdown drain** that stops producers/consumers before releasing retained globals (per AGENTS.md Static Ownership Lifetime Safety).
- **Tests:** CLI parsing; bootstrap order; graceful shutdown drains listeners/dispatcher/DB once (idempotent).
- **Constraints:** `Main.kt`, `network/CanaryServer.kt`, `config/**`, and new files; no Lua.

### Task 2 — Appearances protobuf loading (`feat/appearances-protobuf`)
- **Canary sources:** `src/protobuf/appearances.proto`, `src/items/items.cpp` (appearance load/merge), `src/creatures/appearance/outfit/outfit.cpp`.
- **Deliverable:** a dependency-free protobuf wire-format reader (varint/length-delimited) parsing `data/items/appearances.dat` into outfit/mount/effect tables (`Appearances.kt`) with the same look-up API Canary exposes.
- **Tests:** parse the real `data/items/appearances.dat`; assert expected object counts and spot-checked ids; malformed-input tolerance.
- **Constraints:** `io/appearances` (new package) + additive `items`; no Lua.

### Task 3 — Database tasks (`feat/database-tasks`)
- **Canary sources:** `src/database/databasetasks.cpp/.hpp`.
- **Deliverable:** `database/DatabaseTasks.kt`: an async DB task queue (submit, sequential execution, shutdown flush) mirroring Canary's `DatabaseTasks`, with an injected executor.
- **Tests:** submit/execute ordering, shutdown flush drains pending tasks, no tasks after shutdown.
- **Constraints:** `database` only; no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

## Post-wave (AGENTS.md §6)

Update `docs/missing-features.md` (flip the `src/` bootstrap + protobuf rows to `IMPLEMENTED` and refresh coverage), `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.

## Completion

Completed. `src/` bootstrap section implemented: service bootstrap + CLI/signals/crash handler + idempotent shutdown drain, appearances protobuf loader, and `DatabaseTasks`. The `src/` bootstrap and `appearances.proto` rows are now IMPLEMENTED in `docs/missing-features.md`.
