# Wave 56 Plan — Registry Injection, Spawn Path, Persistence Sub-Loads

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 registry-injection | `bootstrap/**`, `network/CanaryServer.kt` |
| A2 spawn-path | `creatures/spawns/**`, `network/CanaryServer.kt` (spawn region) |
| A3 persistence-subloads-1 | `io/**` |
| A4 persistence-subloads-2 | `io/**` (separate classes; coordinate via file names) |
| A5 lua-extra-ops | `lua/bindings/core/PlayerFunctionsExtra.kt` |

A1/A2 share `CanaryServer.kt` — separate regions; A3/A4 share `io/**` but different files (`IOLoginData` split by method region is risky; instruct A4 to add a separate `IOLoginDataExtraLoads.kt` helper to avoid conflicts).

## Tasks

- **A1** inject `ioLoginData`, a `Monsters`-backed `monsterTypeProvider`, and a `Vocations` registry from `ServerBootstrap` into `CanaryServer`/`GameHandler`.
- **A2** compose the production spawn path (`MonsterFactory`/spawn caller) using the existing spawn engine.
- **A3** `loadPlayer` sub-loads part 1: conditions, skull, guild, storage map, VIP, exiva restrictions.
- **A4** `loadPlayer` sub-loads part 2 (new helper file): bestiary charms, store inbox, reward chest, prey/task hunting.
- **A5** delegate the extra-ops badges/titles/concoction/OTCR Lua state to composed components.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push).

## Completion

Completed: registry injection, production spawn path, persistence sub-loads (V19), Lua extra-ops delegation. 675 suites / 7487 tests. Merge conflicts (CanaryServer monsters param, IOLoginData constructor) resolved by union.
