# Wave 45 Plan — Host Wiring & Final Depth

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 game-remainder-final | `game/Game.kt` |
| A2 monster-host-wiring | `creatures/monsters/**`, `game/scheduling/MonsterComputeService.kt` |
| A3 player-host-wiring | `creatures/Creature.kt` |
| A4 sender-wiring | `network/protocol/ProtocolSink.kt` (new), `game/**` sink call sites |
| A5 runtime-composition | `creatures/players/PlayerFunctions` stash unification, `items/Item.kt` imbuement attrs, `network/GameHandler.kt` |
| A6 player-depth-2 | `creatures/players/**` |

## Tasks

- **A1** remaining `game.cpp`: `playerSetShowOffSocket`, fiendish forge events (`makeFiendishMonster`/`removeForgeMonster`/`updateForgeableMonsters`/`checkForgeEventId`), wheel combat application (`applyWheelOfDestinyHealing`/`applyWheelOfDestinyEffectsToDamage`), `getIOWheel`, remaining handlers.
- **A2** host wiring: instantiate the async pipelines in `Monster`/`Game.checkCreatures` (`trySchedulePostThink`), wire `updateTargetList` spectator scan and `onCreatureAppear`/`onRemoveCreature` observation call sites.
- **A3** host wiring: `Player` overrides for `death`/`onWalk`/`goToFollowCreature`, expose skills/`levelPercent`/`magLevelPercent`/`manaSpent`/`damageMap`/`skillLoss`/`lastLogout`, wire the walk queue to the walk event, `setNextAction*Task` scheduler variants.
- **A4** wire the ported senders into state-change paths via a `ProtocolSink` seam (creature say/move/health/outfit, stats/skills/inventory/container, text messages, cancel messages) and call it from `Game`/`Player`/`Map` paths.
- **A5** runtime composition: unify the stash (`PlayerFunctions` map vs `Player.stash`), market escrow inbox, item imbuement attribute store, `Game`/`ImbuementEngine` composition into the production context.
- **A6** `Player` depth: continue toward 821 raw methods (remaining families: stats/record, condition params, charm/blessing stacks, mount helpers, guild/war helpers, misc accessors).

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: game.cpp remainder batch, monster host wiring, player host wiring, ProtocolSink sender wiring, runtime composition (stash/imbuements/market escrow), Player depth 2. 572 suites / 6284 tests.
