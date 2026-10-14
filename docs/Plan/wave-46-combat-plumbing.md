# Wave 46 Plan — Combat Pipeline, Plumbing, Remaining Depth

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 game-completion | `game/Game.kt` |
| A2 player-depth-3 | `creatures/players/**` |
| A3 composition-plumbing | `bootstrap/**`, `network/GameHandler.kt`, `creatures/Player.kt` |
| A4 monster-type-state | `creatures/monsters/**` |
| A5 map-spectator-sectors | `map/**`, `network/protocol/map/**` |
| A6 imbuement-decay | `systems/imbuement/**`, `items/Item.kt` |

## Tasks

- **A1** `Game::` combat pipeline (`combatChangeHealth`, `applyHealthChange`, Gift-of-Life), remaining handlers (quick-loot/depot, stash, channels, cyclopedia, party/records), and the documented host one-liners (`checkCreatures → trySchedulePostThink`, walk seams bound to host methods).
- **A2** continue `Player::` depth toward 821 (condition params, charm/blessing stacks, mount/guild helpers, misc accessors).
- **A3** bootstrap plumbing: pass `itemTypes`/`ioMarket`/`imbuements` into `GameHandler` from `CanaryServer`/`ServerBootstrap`; add `Player.getImbuingItem()` accessor; bind `Game` walk seams to host methods.
- **A4** monster-type/forge state on `Monster`: `getForgeStack`, `canBeForgeMonster`, `setMonsterForgeClassification`, `configureForgeSystem`, `setTimeToChangeFiendish`, `clearFiendishStatus`, `attackSpells`/`castSpell`/`canUseAttack`/strategies.
- **A5** spectator sector lists (16×16) in the map access + `Map::moveCreature` spectator fanout so moves emit frames.
- **A6** imbuement decay scheduler tick + `Item` imbuement registry validation seam.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.
