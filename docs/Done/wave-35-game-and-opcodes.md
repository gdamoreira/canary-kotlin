# Wave 35 Plan — `src/game/` + Protocol Opcode Completion

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/game/` section (10 rows) + `docs/missing-features.md` §4 (65 missing inbound opcodes, 133 missing outbound senders).

## Group A — `src/game/` section (4 tasks)

### Task A1 — Game class core (`feat/game-class-core`)
- **Canary sources:** `src/game/game.cpp/.hpp` (13,141 lines, ~352 methods).
- **Deliverable:** `game/Game.kt` core: creature placement/removal (`addCreature`/`removeCreature`/`placeCreature`), internal add/remove/move item, broadcast, world lights/sounds, auto-walk stop, feature dispatch seams.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `game` package; no Lua.

### Task A2 — Movement & modal (`feat/game-movement-modal`)
- **Canary sources:** `src/game/movement/position.cpp`, `movement/teleport.cpp`, `modal_window/*`.
- **Deliverable:** position direction/offset math + range rectangles parity, teleport effects/destination rules, modal answer routing + per-player modal tracking.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `game.movement`/`game.modal` (new files); no Lua.

### Task A3 — Scheduling (`feat/game-scheduling`)
- **Canary sources:** `src/game/scheduling/dispatcher*.cpp`, `events_scheduler.cpp`, `monster_compute_service.cpp`, `save_manager.cpp`.
- **Deliverable:** WDRR fairness/budgets/telemetry for the dispatcher, calendar/timed events scheduler, off-thread monster compute service, global save-all + shutdown flush.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `game.scheduling` (extend existing + new files); no Lua.

### Task A4 — Game reload (`feat/game-reload`)
- **Canary sources:** `src/game/functions/game_reload.cpp`.
- **Deliverable:** runtime reload surface (config/monsters/NPCs/items/scripts) with idempotent, generation-checked reload semantics.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `game` (new file); no Lua.

## Group B — missing inbound opcodes (4 tasks)

Each task: port the Canary parse function from `src/server/network/protocol/protocolgame.cpp` into a decode handler + apply state where the engine exists; add **one contiguous block of `when` branches immediately before `else -> false` in `GamePacketRouter.kt`**, prefixed `// Wave 35 batch <X>` — do not touch other parts of the router.

### Task B1 — `feat/opcodes-inbound-1`
`0x1D` playerReceivePingBack · `0x2A` parseCyclopediaMonsterTracker · `0x2B` parsePartyAnalyzerAction · `0x2C` parseLeaderFinderWindow · `0x2D` parseMemberFinderWindow · `0x2E` parseSetClientOptions · `0x32` parseExtendedOpcode · `0x38` parsePlayerTyping · `0x60` parseInventoryImbuements · `0x63` parseClientCheck · `0x69` playerStopAutoWalk · `0x76` parseCharacterTradeConfigurationAction · `0x77` parseHotkeyEquip · `0x79` parseLookInShop · `0x81` parseFriendSystemAction.

### Task B2 — `feat/opcodes-inbound-2`
`0x84` parseUseWithCreature · `0x85` parseRotateItem · `0x86` parseConfigureShowOffSocket · `0x89` parseTextWindow · `0x8A` parseHouseWindow · `0x8B` parseWrapableItem · `0x8E` parseJoinAggression · `0x97` playerRequestChannels · `0x98` parseOpenChannel · `0x99` parseCloseChannel · `0x9A` parseOpenPrivateChannel · `0x9D` parseGetTextForReport · `0x9E` playerCloseNpcChannel · `0x9F` parseSetMonsterPodium.

### Task B3 — `feat/opcodes-inbound-3`
`0xAA` playerCreatePrivateChannel · `0xAB` parseChannelInvite · `0xAC` parseChannelExclude · `0xAD` parseCyclopediaHouseAuction · `0xAE` parseSendBosstiary · `0xAF` parseSendBosstiarySlots · `0xB0` parseBosstiarySlot · `0xB1` parseHighscores · `0xB3` parseWeaponProficiency · `0xBA` parseTaskHuntingAction · `0xBE` playerCancelAttackAndFollow · `0xC0` parseForgeBrowseHistory · `0xC1` parseClientDetails · `0xC2` parseBossDifficultySelection.

### Task B4 — `feat/opcodes-inbound-4`
`0xC8` parseAimAtTarget · `0xC9` parseGetTransactionDetails · `0xCA` resendLivestreamViewerContainer · `0xCB` parseBrowseField · `0xCC` parseSeekInContainer · `0xCD` parseInspectionObject · `0xCE` parseInspectPlayer · `0xCF` sendBlessingWindow · `0xD2` playerRequestOutfit · `0xDB` parseCyclopediaMapAction · `0xDC` parseAddVip · `0xDD` parseRemoveVip · `0xDE` parseEditVip · `0xDF` parseVipGroupActions · `0xE5` parseCyclopediaCharacterInfo · `0xE6` parseBugReport · `0xE8` parseOfferDescription · `0xEB` parsePreyAction · `0xEC` parseSetHirelingName · `0xED` parseSendResourceBalance · `0xEE` parseGreet · `0xF3` parseGetObjectInfo.

## Group C — missing outbound senders (2 tasks)

Each task: port Canary `ProtocolGame::send*` methods into new writer files (do not edit shared writers); tests assert exact wire bytes.

### Task C1 — `feat/opcodes-outbound-1`
Trade, market, shop, house/auction, channels, highscores senders.

### Task C2 — `feat/opcodes-outbound-2`
VIP, cyclopedia, forge browse/history, prey, task hunting, bosstiary, stash, store/resource balance, imbuement, inspection, blessing window, typing/friends/hotkeys senders.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; §4 opcode lists updated with the newly handled opcodes.

## Post-wave (AGENTS.md §6)

Flip the `src/game/` rows and update §1/§3/§4 in `docs/missing-features.md`; update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.

## Completion

Completed: Group A (`src/game/` — Game core, movement/modal, scheduling, reload), Group B (all 64 remaining inbound opcodes), Group C (outbound trade/channel/house/VIP/store/inspection/blessing writers). All merged; `src/game/` section is now 9/10 IMPLEMENTED (game.cpp remainder documented).
