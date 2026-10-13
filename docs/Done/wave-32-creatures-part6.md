# Wave 32 Plan — `src/creatures/` Part 6 (Player runtime 3 — feature hooks)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** Final `src/creatures/` row: `players/player.cpp`.

## Row being closed

| C++ | Current | Target |
|---|---|---|
| `players/player.cpp` (13,456, ~765 methods) | PARTIAL | IMPLEMENTED — wheel/forge/prey/task-hunting/cyclopedia hooks |

## Task

### Task 1 — Player feature hooks (`feat/player-runtime-3`)
- **Canary sources:** `src/creatures/players/player.cpp/.hpp` — the feature hook methods: wheel (`wheel()`, `getWheel*`, gem/dedication accessors), forge (`getForge*`, dust accessors), prey (`getPrey*`, bonus application), task hunting (`getTaskHunting*`, points), cyclopedia (`getCyclopedia*`), plus the remaining `Player` accessors that delegate to `PlayerFeatures`.
- **Deliverable:** extend `creatures/Creature.kt` (`Player`) with these hooks, delegating to the already-ported `PlayerFeatures` components; keep constructors compatible.
- **Tests:** transcribe real Canary behavior (accessors delegate correctly; prey/task bonuses applied).
- **Constraints:** `creatures` (+ additive) only; no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip `player.cpp` to `IMPLEMENTED` in `docs/missing-features.md` (the `src/creatures/` section is then fully IMPLEMENTED); update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.

## Completion

Completed. Player feature hooks (wheel/forge/prey/task-hunting/cyclopedia). The entire `src/creatures/` section is now IMPLEMENTED.
