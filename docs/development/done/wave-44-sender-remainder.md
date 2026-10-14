# Wave 44 Plan — Sender Remainder, Application, Walk/Death, Monster Async

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 senders-remainder | `network/protocol/party/**`, `network/protocol/podium/**`, `network/protocol/depot/**`, `network/protocol/container/**`, `network/protocol/login/**` (new) |
| A2 opcode-application-3 | handler files under `network/protocol/**` |
| A3 game-market-forge | `game/Game.kt` |
| A4 player-walk-death | `creatures/players/**` |
| A5 monster-async | `creatures/monsters/**` |
| A6 lua-class-migration | `lua/runtime/UserdataRegistry.kt`, `lua/bindings/**` |

## Tasks

- **A1** remaining 45 senders: cyclopedia-character (12), party (7), podium (3), depot (3), container items 0x70/0x71/0x72 (3), login/OTC (3), loot (2), team-finder (2), outfit-window (2), forge/gem/livestream/emblem/kill-tracker/updateCreature/loginChallenge (8).
- **A2** finish opcode application: stash deposit/withdraw (0x28) + retrieve (0x29), corpse/nearby loot (0x8F), imbuement apply/clear (0xD5/0xD6), market create/cancel/accept (0xF6–0xF8), parent container open (0x95).
- **A3** `game.cpp`: `playerCreateMarketOffer`/`playerCancelMarketOffer`/`playerAcceptMarketOffer`/`updatePlayerSaleItems`, `loadItemsPrice`, `playerForgeFuseItems`, wheel handlers, `playerNpcGreet`, `playerAnswerModalWindow`, `playerSetMonsterPodium`/`playerRotatePodium`.
- **A4** `Player::death` + walk queue (`startAutoWalk`/`stopWalk`/`addListWalkDir`/`onWalk`/`goToFollowCreature`/`canWalkthrough`) + scheduler variants (`setNextActionTask`/`setNextActionPushTask`/`setNextPotionActionTask`).
- **A5** monster async pipelines: compute/intention generations, post-think scheduler, movement-AI refresh, spectator/visibility bookkeeping (replace seams with real implementations).
- **A6** adopt `LuaClassRegistry` in the userdata/binding layer; port remaining `SOUND_EFFECT_TYPE_*` ambient/music enum names.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§4, `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: all 187 senders, full opcode application, game.cpp market/forge/wheel/podium, Player::death + walk queue, monster async pipelines, Lua class registry migration. 554 suites / 6079 tests.
