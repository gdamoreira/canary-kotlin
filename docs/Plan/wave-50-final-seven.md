# Wave 50 Plan — Final 7 Game Members, Player Depth 7, Nested Inventory

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 game-last-7 | `game/Game.kt` |
| A2 player-depth-7 | `creatures/players/**` |
| A3 host-composition-3 | `creatures/monsters/**`, `creatures/PlayerFeatures.kt` |
| A4 nested-inventory-persistence | `io/**` |

## Tasks

- **A1** the final 7 `game.cpp` members: `combatBlockHit`, `playerCyclopediaCharacterInfo`, `loadMap`/`loadMainMap`/`loadCustomMaps`, `loadAppearanceProtobuf`, `loadBoostedCreature` (port the real bodies with injected seams where the map/appearances are io-owned).
- **A2** continue `Player::` depth toward 821.
- **A3** host composition remainder: bind `asyncTaskDispatcher`/`onExecuteAsyncTasksHost` in `MonsterPipelinesHost.register`; compose the player state holders (combat bonuses, active foods, message buffer) into `PlayerFeatures`.
- **A4** nested inventory persistence: add `pid`/`sid` to `player_inventory_items` (**V17**) and materialize nested inventory containers in `IOLoginData` (mirroring the depot/inbox tree rebuild).

## Rules

- §1.1 parity, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.
