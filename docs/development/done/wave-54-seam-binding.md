# Wave 54 Plan — Seam Binding & Composition Details

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 seam-binding | `creatures/players/**`, `game/Game.kt` |
| A2 composition-root-2 | `bootstrap/**`, `network/CanaryServer.kt`, `io/**` |
| A3 component-accessors | `creatures/PlayerFeatures.kt`, `creatures/players/**` |
| A4 monster-async-final | `creatures/monsters/**`, `game/scheduling/**` |

## Tasks

- **A1** bind the 38 `send*` seam call sites (each has a writer counterpart) so state changes emit frames: loot message, market leave, party icons, prey/task data, tile sends, team finder, marker, basic data, forge, depot, shop, bosstiary, creature sends, generic network message.
- **A2** `ServerBootstrap` config/buckets into `CanaryServer`; `MonsterPipelinesHost` production composition; `IOLoginData` `depotChests` persistence walk; `savePlayerGuard` shared transaction at the root.
- **A3** compose `achiev()`/`animusMastery()` components into `Player`/`PlayerFeatures`.
- **A4** `MonsterAsyncPipelines` private `safeCall` continuation hop; host lateness sinks → `observeInternalWork`.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: send-seam binding (38/38), composition root 2, component accessors (achievement/animusMastery), monster async final. 663 suites / 7424 tests.
