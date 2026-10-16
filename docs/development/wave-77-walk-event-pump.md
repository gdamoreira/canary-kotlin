# Feature: Creature Walk Event Pump (Client Step Confirmation)

## 1. Explanation
The player walk is a queued, rate-limited event in Canary: `Game::playerMove`
starts an auto-walk, `Creature::addEventWalk` admits a `Creature::onCreatureWalk`
event on the dispatcher, and each tick consumes one queued step through
`Game::internalMoveCreature` → `Map::moveCreature`. `Map::moveCreature` is what
moves the creature between tiles, updates the sectors, fires the tile
notifications (`stepin`/`stepout` move events) and sends the spectator
`sendCreatureMove` (`0x6D`) confirmation that tells the client its predicted
step was accepted. The port handled walk opcodes through a direct-position
engine helper instead: the coordinates changed server-side but the map, the
notifications and the client confirmation never ran, so the client reverted
every step ("cannot walk, snaps back").

## 2. Impact
Without the pump no player can walk: every client-predicted step is unconfirmed
and rubber-bands on the next server map update. The same pump is the only path
that fires the datapack's `stepin`/`stepout` move events (teleport tiles, tile
actions) and it is the base for the follow system and the walk-delay/paralyze
timing (`getWalkDelay`, `getEventStepTicks`).

## 3. Functionality Affected
- Walk opcodes `0x65..0x68`, `0x6A..0x6D`: now route through
  `Game::playerMove` (game.cpp:4282) when a composed world exists.
- Stop auto-walk `0x69`: now calls `Game::playerStopAutoWalk` (game.cpp:4567).
- Click-to-move `0x64`: the client path parses through `parseAutoWalk`
  (protocolgame.cpp:2449-2470) into `Game::playerAutoWalk`; single-step paths
  start `ImmediateWhenReady`, multi-step paths `RespectDelay` (game.cpp:4472).
- Spot turns `0x6F..0x72`: route through `Game::playerTurn` (game.cpp:7187)
  when a world is composed (event/callback vetoes + spectator broadcast).
- Walk start policy: `Game::playerMove`/`forcePlayerMove` pass
  `ImmediateWhenReady` (game.cpp:4282/4296) so the first key-tap step commits
  without a full step duration of delay.
- Walk admission, step timing and cancellation (`Creature::cancelNextWalk`,
  `Player::stopWalk`), failed-step cancels (`sendCancelMessage` +
  `sendCancelWalk`), the `PlayerWalk` dispatcher lane.

## 4. Agent Implementation Details
- `creatures/Creature.kt`: `getStepDuration` (creature.cpp:1607, `SERVER_BEAT`
  rounding), `getWalkDelay` (creature.cpp:109), `getEventStepTicks` (1629),
  `addEventWalk` (342) and `onCreatureWalk` (219) — the walk pump; fields
  `cancelNextWalk` (creature.hpp:934) and `checkingWalkCreature` (942) moved to
  the base class; new virtuals `onWalkAborted`/`onWalkComplete`,
  `getNextStep`/`clearWalkQueue`/`isListWalkDirEmpty`; seams
  `internalMoveCreatureProvider` and the creature-level `scheduleEvent`.
- `creatures/players/PlayerWalkState.kt`: `clearWalkQueue()` reach; the
  walk-delay/step-duration providers default to the creature walk clock.
- `game/Game.kt`: `internalPlaceCreature` binds `scheduleEvent`, `stopEvent` and
  `internalMoveCreatureProvider` for every accepted creature.
- `network/protocol/MovePacketHandler.kt`: walks and `0x69` route through the
  composed `Game`; the direct engine step remains only as the decode-only
  fallback (no world composed).
- Concurrency: the pump schedules on `DispatcherLane.PlayerWalk`; every step and
  every tile/sector mutation stays confined to the dispatcher (AGENTS.md 1.2).

## 5. Test Reference
- `CanaryServerCompositionTest` — "walk packet moves the player through the map
  and confirms with sendCreatureMove": production composition, asserts tile
  membership plus the decrypted `0x6D` confirmation.
- `GameUseItemHandlersTest` — auto-walk continuation harness wired with the
  placement `scheduleEvent` binding.
- `PlayerHostWiringTest`, `PlayerWalkQueueTest`, `CreatureHostHooksTest` —
  admission seam and walk-delay wiring.
- `MovePacketHandlerTest` — click-to-move path parsing and the
  `Game::playerTurn` veto transcript.

## 6. Canary Original Code Reference
- **Source Files**: `src/creatures/creature.cpp`, `src/creatures/creature.hpp`,
  `src/game/game.cpp`, `src/creatures/players/player.cpp`,
  `src/map/map.cpp`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Creature::addEventWalk` (creature.cpp:342-383),
  `Creature::onCreatureWalk` (creature.cpp:219-289),
  `Creature::getEventStepTicks` (creature.cpp:1629-1637),
  `Creature::getWalkDelay` (creature.cpp:109-120),
  `Creature::getStepDuration` (creature.cpp:1607-1626),
  `Game::playerMove` (game.cpp:4282), `Game::internalMoveCreature`
  (game.cpp:1909/1963), `Map::moveCreature` (map.cpp:679-814),
  opcodes `0x65..0x6D` (`ProtocolGame::parseWalk`).

## Implementation Status: DELIVERED
The pump is live for the composed player walk: steps commit on the map, the
sectors and tile notifications run, and the client receives the `0x6D`
confirmation. Monster walk admission and the follow subsystem reuse the same
pump when their call sites are wired (tracked as residual gaps).
