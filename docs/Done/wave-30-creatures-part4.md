# Wave 30 Plan — `src/creatures/` Part 4 (Player runtime 1, Storage, Forge History, Combat)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/creatures/` part 4.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `players/player.cpp` chunk 1 | PARTIAL | IMPLEMENTED — conditions integration, spell/cooldown integration, skull/PvP integration, async task queue |
| `players/components/player_storage.cpp` | PARTIAL | IMPLEMENTED — delta/dirty/ingest lifecycle, reserved ranges |
| `players/components/player_forge_history.cpp` | PARTIAL | IMPLEMENTED — DB load/save |
| `combat/combat.cpp` | PARTIAL | IMPLEMENTED — full `getCombatDamage` matrix, callbacks, target validation, areas |

## Tasks

### Task 1 — Player runtime chunk 1 (`feat/player-runtime-1`)
- **Canary sources:** `src/creatures/players/player.cpp/.hpp`.
- **Deliverable:** extend `creatures/Creature.kt` (`Player`) with: condition integration (`addCondition`/`onAddCombatCondition`/suppression), learned spells + spell/group cooldowns, skull/PvP integration (PZ lock, in-fight condition), and the async task-queue basics.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures` (+ additive); no Lua.

### Task 2 — Player storage lifecycle (`feat/player-storage`)
- **Canary sources:** `src/creatures/players/components/player_storage.cpp/.hpp`.
- **Deliverable:** `creatures/players/components/PlayerStorage.kt` with the delta/dirty/ingest lifecycle and reserved-range handling.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/players/components` (new file); no Lua.

### Task 3 — Forge history persistence (`feat/player-forge-history`)
- **Canary sources:** `src/creatures/players/components/player_forge_history.cpp/.hpp`.
- **Deliverable:** complete `systems/forge/PlayerForgeHistory.kt` with DB load/save (reuse the repository patterns); injected repository seam.
- **Tests:** H2 round-trip.
- **Constraints:** `systems/forge`, `database`, `persistence`; no Lua.

### Task 4 — Combat completion (`feat/combat-complete`)
- **Canary sources:** `src/creatures/combat/combat.cpp/.hpp`.
- **Deliverable:** complete `combat/Combat.kt` with the full `getCombatDamage` matrix (weapon/level formulas, absorb/increase), target validation, and combat areas; event/callback seams.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `combat` (+ additive); no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip the covered rows to `IMPLEMENTED` in `docs/missing-features.md`; update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.

## Completion

Completed. Player runtime chunk 1 (conditions/spells/cooldowns/PvP/async tasks), `PlayerStorage`, forge-history persistence (V8), and combat completion. Rows flipped.
