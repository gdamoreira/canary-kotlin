# Wave 52 Plan — Composition One-Liners, Persistence Guard, Dispatcher Depth

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 player-depth-9 | `creatures/players/**` |
| A2 composition-liners | `game/Game.kt`, `network/protocol/GamePacketRouter.kt`, `config/**` |
| A3 persistence-guard | `io/**` |
| A4 dispatcher-depth | `game/scheduling/**`, `creatures/Creature.kt` |

## Tasks

- **A1** remaining ~88 `Player::` methods toward 821 raw.
- **A2** the documented one-liners: `Game.playerSay`→`removeMessageBuffer`, aim-at-target router branch (0xC8), `Game.transformItem` for charged items, `maxMessageBuffer` from config, world-shared `muteCountMap`, non-monster creature async buckets at login/placement.
- **A3** `savePlayerGuard` calling the live inventory/depot/reward/inbox walks; depot/reward/inbox `Player` sinks.
- **A4** dispatcher depth: serial-lane admission reservation, adaptive budget refresh loop (config keys, 250ms), `Creature.safeCall` barrier continuation, `observeInternalWork` gating.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.
