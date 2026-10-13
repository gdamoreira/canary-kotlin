# Wave 43 Plan — Wiring, Application, Senders, Depth

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 game-wiring | `network/GameHandler.kt`, `bootstrap/**`, `network/protocol/GamePacketRouter.kt` context only |
| A2 opcode-application-2 | handler files under `network/protocol/**` (except router context) |
| A3 senders-misc | `network/protocol/misc/**` (new) |
| A4 game-families | `game/Game.kt` |
| A5 player-depth | `creatures/players/**` |
| A6 monster-depth | `creatures/monsters/**` |

## Tasks

- **A1** Production wiring: `GameHandler`/`CanaryServer` own a `Game` instance and register login players; pass `game` into `GamePacketContext` so wave-42 application activates in production.
- **A2** Remaining opcode application: stash deposit/withdraw, quick-loot corpse/nearby paths, imbuement apply/clear, market browse/create/cancel/accept, outfit 0xD3 (`Player.changeOutfit` composition).
- **A3** Remaining misc senders: `sendReLoginWindow`, `sendBasicData`, `sendFYIBox`, `sendAllowBugReport`, `sendUnjustifiedPoints`, `sendOpenPvpSituations`, `sendChannelEvent`, `sendOpenPrivateChannel`, store/misc senders not yet present.
- **A4** `game.cpp` remaining families: movement/combat/targeting handlers, market Game methods, quest/forge/bosstiary/task-hunting Game handlers, remaining `player*` handlers.
- **A5** `Player` depth: port high-value remaining `Player::` methods (raw count ~50% → higher) that are not yet modelled.
- **A6** Monster engine depth: remaining `monster.cpp` behaviors (raw count parity).

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§4, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.
