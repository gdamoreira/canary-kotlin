# Wave 55 Plan — Live Transport & Login Integration

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 sink-transport | `network/protocol/ProtocolSink.kt`, `creatures/players/PlayerSendSink.kt`, `network/GameHandler.kt` |
| A2 monster-registration | `game/Game.kt`, `creatures/monsters/MonsterPipelinesHost.kt` |
| A3 lua-delegation | `lua/bindings/core/PlayerFunctions.kt`, `lua/bindings/core/PlayerFunctionsExtra.kt` |
| A4 login-load | `network/GameHandler.kt`, `io/**` |

Note A1/A4 share `GameHandler.kt`; A1 does the sink install, A4 the login load. Coordinate by separate method regions; merge conflicts resolved by union.

## Tasks

- **A1** install `PlayerSendSink` on the live channel: make `NettyProtocolSink` (or a subclass) implement the 38 player senders over the real writers; `GameHandler` assigns it.
- **A2** `Game.internalPlaceCreature`/`removeCreature` call `monsterHost.register/unregister` (with `monsterTypeProvider`) so production pipelines activate.
- **A3** Lua bindings delegate achievement/animus operations to the composed `PlayerFeatures` components instead of duplicate maps.
- **A4** `GameHandler` login loads the player through `IOLoginData.loadPlayerById` (real inventory/skills/feature state) instead of the stub `Player`.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report wiring, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: live sink transport, monster registration wiring, Lua delegation, login load (V18). 669 suites / 7442 tests.
