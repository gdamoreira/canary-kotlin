# Wave 48 Plan — Game Remainder 3, Player Depth 5, Composition

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 game-remainder-3 | `game/Game.kt` |
| A2 player-depth-5 | `creatures/players/**` |
| A3 monster-composition | `creatures/monsters/**`, `game/scheduling/**` |
| A4 item-registry-hub | `items/Item.kt`, `io/**` item-creation paths |

## Tasks

- **A1** remaining `game.cpp` (~84 real members): stash/depot-search Game methods, use-item handlers, item batch creation, cyclopedia info, maps/start/shutdown, team-finder/NPC helpers, `combatBlockHit`/`applyCharmRune` wiring.
- **A2** continue `Player::` depth + async follow (`goToFollowCreature_async` pipeline with generation safety).
- **A3** monster composition: bind `checkCreatureAttack`/`hasExtraSwing`, follow-path compute host wiring, post-think queue lane hops.
- **A4** assign `Item.imbuementRegistry` at every item-creation hub (map load, persistence, `Game.createItem`, loot) so registry validation is universal.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: game remainder 3 (~39 members left), player depth 5 + async follow (659/821), monster composition, item registry hubs. 617 suites / 6858 tests.
