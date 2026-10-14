# Wave 53 Plan — Composition Root, Persistence, Dispatcher Polish

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 player-final-depth | `creatures/players/**` |
| A2 composition-root-final | `network/CanaryServer.kt`, `network/GameHandler.kt`, `creatures/Creature.kt`, `creatures/monsters/MonsterPipelinesHost.kt` |
| A3 persistence-final | `io/**` |
| A4 dispatcher-final | `game/scheduling/**` |

## Tasks

- **A1** remaining `Player::` methods (mostly `send*` seams already covered by writers — port any that are real logic; report the seam-only remainder).
- **A2** composition root: world-shared `muteCountMap`/`transformItemCharges`, shared async buckets into `MonsterPipelinesHost`, `CanaryServer` config/telemetry wiring, `Player::depotChests` accessor.
- **A3** `savePlayerGuard` stash/bestiary/prey/task-hunting live saves; shared transaction where feasible.
- **A4** dispatcher polish: periodic latency logging, monster lateness → `observeInternalWork`, `safeCall` resumption context.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: composition root final, player final depth (754/821), persistence final (stash/bestiary/prey/task + shared transaction), dispatcher final (telemetry + context). 657 suites / 7327 tests.
