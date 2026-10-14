# Wave 42 Plan — Close PARTIAL Rows + Outbound Sender Campaign

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 game-houses-auctions | `game/Game.kt` |
| A2 luascript-sandbox | `lua/runtime/**` (LuaScriptBridge, LuaEnvironment, ScriptEnvironment) |
| A3 opcode-application | `network/protocol/GamePacketRouter.kt` + handler files under `network/protocol/**` |
| A4 senders-creature-map | `network/protocol/creature/**` (new), `network/protocol/map/**` (new) |
| A5 senders-player-ui | `network/protocol/player/**` (new) |
| A6 senders-cyclopedia | `network/protocol/cyclopedia/**` (new) |

## Tasks

- **A1** `game.cpp` houses/auctions: `playerCyclopediaHouse*`, `processBankAuction`, `wrapItem`/`unwrapItem`, `setTransferPlayerHouseItems`, `transferHouseItemsToDepot`, async highscores DB, remaining misc families.
- **A2** `luascript.cpp`/`script_environment.cpp`: protected calls, class registration, sandboxing, reload parity.
- **A3** `protocolgame.cpp` opcode application: compose the existing engines into the decode-only handlers — trade (0x7D–0x80), containers (0x75/0x87/0x88/0x95), quick loot (0x8F–0x91), stash/depot (0x28/0x29/0x92–0x95), follow (0xA2), imbuements (0xB2/0xD5–0xD7), market (0xF4–0xF8), quest log (0xF0/F1), guild (0x9C), outfit (0xD3).
- **A4** remaining creature/map/tile senders (`sendCreature*`, `sendMap*`, `sendTile*`, `sendAddTileItem`/`sendUpdateTileItem`/`sendRemoveTileThing`, `sendDistanceShoot`, `sendMagicEffect`, `sendAnimatedText`).
- **A5** remaining player/UI senders (`sendStats` variants, `sendSkills`, `sendIcons`, `sendOpenContainer`, `sendCloseContainer`, `sendModalWindow`, `sendTutorial`, `sendMessagesDialog`, `sendTextWindow`, `sendHouseWindow`, `sendCreatureSay`, `sendChannelMessage`).
- **A6** cyclopedia computation frames (`addCyclopedia*`, `handleImbuementDamage`, `calculateAbsorbValues`, `getForgeSkillStat`, `getCipbia*`) + remaining forge/store senders.

## Rules

- §1.1 parity (port only real Canary `send*` methods and exact wire format), §1.2 WorldCommit confinement, §4 TDD (byte-exact where applicable), zero warnings.
- New writer files only; A3 owns the router.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Flip the 3 PARTIAL rows if closed; update §1–§4 (sender counts), `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: `luascript.cpp`/`script_environment.cpp` row flipped to IMPLEMENTED; game.cpp and protocolgame.cpp rows advanced (houses/auctions + opcode application); outbound sender campaign started (creature/map/tile/player-UI/chat/text/cyclopedia). 506 suites / 5359 tests.
