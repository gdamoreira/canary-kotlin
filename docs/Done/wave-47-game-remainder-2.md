# Wave 47 Plan — Game Remainder 2, Creature Hooks, Composition

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 game-remainder-2 | `game/Game.kt` |
| A2 creature-hooks | `creatures/Creature.kt` |
| A3 player-depth-4 | `creatures/players/**` |
| A4 imbuement-composition | `network/GameHandler.kt`, `systems/imbuement/**`, `items/Item.kt` |
| A5 lua-item-attributes | `lua/bindings/core/ItemFunctions.kt`, `lua/bindings/core/PlayerFunctions.kt` |

## Tasks

- **A1** remaining `game.cpp` (~145 real members): quick-loot/depot/stash handlers, `playerSay*`/`playerUseItem*`, cyclopedia info, records/online, state updaters, item creation/batch, team-finder/NPC/forge helpers. Also wire the seams now available: Game forge seams → `MonsterPipelinesHost.typeStateOf`/`MonsterForge`; `internalPlaceCreature` → `map.placeCreature`; `removeCreature` → `map.removeCreature`; `spectatorsOf` → `map` spectator query.
- **A2** `Creature::onCreatureMove` virtual + Player/Monster overrides; wire the remaining host state classes; `addCreatureHealth`/sink consistency.
- **A3** continue `Player::` depth toward 821.
- **A4** production imbuement composition: `GameHandler` builds `ImbuementEngine` with the dispatcher; assign `Item.imbuementRegistry` at the composition root.
- **A5** Lua item attributes: `ItemFunctions` custom-attribute methods and `player:applyImbuementScroll`/`clearAllImbuements` use the real stores now that they exist.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: game remainder 2 (~84 members left), creature hooks, player depth 4 (648/821), imbuement composition, Lua item attributes. Fixed a deterministic test race in ImbuementDecaySchedulerTest. 603 suites / 6666 tests.
