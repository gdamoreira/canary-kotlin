# Wave 49 Plan — Final Game Members, Player Depth 6, Login Items

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 game-final | `game/Game.kt` |
| A2 player-depth-6 | `creatures/players/**` |
| A3 composition-final | `network/GameHandler.kt`, `creatures/Creature.kt` |
| A4 login-item-materialization | `io/**`, `database/ItemAttributeCodec.kt` |

## Tasks

- **A1** the last ~39 `game.cpp` members: `loadMap`/`loadMainMap`/`loadCustomMaps`/`start`/`loadMotdNum`, movement (`playerMoveItem`/`playerMoveCreature`/`playerMoveThing` + ByID), `playerEquipItem`/`playerWrapableItem`/`playerLookInShop`/`playerChangeOutfit`/`playerConfigureShowOffSocket`/`playerInspectItem`, reward chest ×2, big cyclopedia (`playerCyclopediaCharacterInfo`, list getters, `registerAchievement`), `combatBlockHit`/`applyCharmRune`, misc (`addMoney`, `updatePlayersOnline`, `playerCheckActivity`, `resetMonsters`, `createLuaItemsOnMap`, `loadAppearanceProtobuf`, `parsePlayerExtendedOpcode`, `playerDebugAssert`, `sendOfflineTrainingDialog`, `outfitSupportsMount`).
- **A2** continue `Player::` depth toward 821.
- **A3** composition roots: `GameHandler` imbuement-registry assignment at its `Item.create` sites; `Creature.kt` async-batch routing to `WorkerCompletion`.
- **A4** `IOLoginData` item materialization (`loadItems`) + `ATTR_CUSTOM` encode/decode in `ItemAttributeCodec` so imbuements/attributes round-trip through player persistence.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: game.cpp final batch (7 members left), player depth 6 (672/821), composition final, login item materialization + ATTR_CUSTOM codec. 625 suites / 7029 tests.
