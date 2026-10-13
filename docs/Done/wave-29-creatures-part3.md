# Wave 29 Plan — `src/creatures/` Part 3 (NPC runtime, PvP, Management, Stash/Vocations/Imbuements)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/creatures/` part 3.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `npcs/npc.cpp` (1,282) | PARTIAL | IMPLEMENTED — dialogue state machine, keywords/focus, walk-to, shop runtime |
| `npcs/npcs.cpp` | MISSING | IMPLEMENTED — NPC type registry |
| `npcs/spawns/spawn_npc.cpp` | MISSING | IMPLEMENTED (spawn engine done in wave 18 — flip) |
| `players/components/pvp/*` | MISSING | IMPLEMENTED — player PvP situations + expert PvP |
| `players/livestream/livestream.cpp` | MISSING | IMPLEMENTED |
| `players/management/ban.cpp` | MISSING | IMPLEMENTED |
| `players/management/waitlist.cpp` | MISSING | IMPLEMENTED |
| `players/storages/storages.cpp` (stash) | MISSING | IMPLEMENTED |
| `players/vocations/vocation.cpp` | PARTIAL | IMPLEMENTED — req formulas, promotion, gain caps |
| `players/imbuements/imbuements.cpp` | PARTIAL | IMPLEMENTED — scroll crafting + remaining runtime |

## Tasks

### Task 1 — NPC runtime + registry (`feat/npc-runtime`)
- **Canary sources:** `src/creatures/npcs/npc.cpp/.hpp`, `src/creatures/npcs/npcs.cpp/.hpp`.
- **Deliverable:** extend `creatures/Npc.kt` with the dialogue state machine (keywords/focus/player interaction), walk-to/queue, shop buy/sell runtime; add `creatures/npcs/Npcs.kt` type registry.
- **Tests:** dialogue flow, keyword matching, shop buy/sell, registry lookup.
- **Constraints:** `creatures/npcs` (+ additive `Npc.kt`); no Lua.

### Task 2 — PvP components (`feat/player-pvp`)
- **Canary sources:** `src/creatures/players/components/pvp/player_pvp.cpp/.hpp`, `expert_pvp.cpp/.hpp`.
- **Deliverable:** `creatures/players/pvp/PlayerPvp.kt` (situations, unjustified points, skull transitions) + `ExpertPvp.kt`.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/players/pvp` (new package); no Lua.

### Task 3 — Management (ban, waitlist, livestream) (`feat/player-management`)
- **Canary sources:** `src/creatures/players/management/ban.cpp/.hpp`, `waitlist.cpp/.hpp`, `src/creatures/players/livestream/livestream.cpp/.hpp`.
- **Deliverable:** `creatures/players/management/Ban.kt`, `Waitlist.kt`, `creatures/players/livestream/Livestream.kt`.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** new packages only; no Lua.

### Task 4 — Stash, vocations, imbuements completion (`feat/player-stash-vocations-imbuements`)
- **Canary sources:** `src/creatures/players/storages/storages.cpp/.hpp`, `src/creatures/players/vocations/vocation.cpp/.hpp`, `src/creatures/players/imbuements/imbuements.cpp/.hpp`.
- **Deliverable:** `creatures/players/storages/Stash.kt` (supply stash add/count/withdraw); complete `creatures/vocations/Vocation.kt` (req skill/mana formulas, promotion, gain caps); complete `systems/imbuement` (scroll crafting + remaining runtime).
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/players/storages`, `creatures/vocations`, `systems/imbuement`; no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip the covered rows to `IMPLEMENTED` in `docs/missing-features.md`; update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.

## Completion

Completed. NPC runtime + registry, PvP components, ban/waitlist/livestream, stash/vocations/imbuements. Rows flipped to IMPLEMENTED.
