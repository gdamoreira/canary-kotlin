# Canary C++ → Kotlin Port: Missing-Features Tracker

This document tracks **C++ code that is not yet transcribed to the Kotlin port**, so remaining work is visible and measurable. It complements `docs/game-play-features.md` (the 56-feature gameplay matrix).

- **Reference C++ repo:** `/Users/damoreira/.projects/canary` (`src/`)
- **Kotlin port:** `canary-kotlin/src/main/kotlin/org/opentibia/canary/`
- **Status legend:** `DONE` = faithful, tested Kotlin counterpart; `PARTIAL` = engine core exists, significant behavior missing; `MISSING` = no meaningful counterpart.

> Updated after every wave (AGENTS.md §6). Numbers are approximate and derived from file/line counts and method/registration counts.

---

## 1. Coverage Summary

### 1.1 Protocol & surface metrics (verified against Canary `main`)

| Dimension | Canary | Kotlin | Coverage | Notes |
|---|---|---|---|---|
| Tracked gameplay features (56) | 56 | 56 | **100%** engine scope | `docs/game-play-features.md`; residual parity gaps per feature |
| Inbound `ProtocolGame` opcodes | 141 active (148 cases − 7 commented-out) | 76 decoded | **~54%** | 38/141 (**~27%**) fully applied; 38 decode-only (state machine unapplied); 65 genuinely missing |
| Outbound `ProtocolGame` senders | 190 `ProtocolGame::send*` | 57 `fun send*` | **~30%** | window serializers cover some UI flows |
| Lua bindings (`src/lua/functions/*`) | 1,961 registrations (1,246 methods + 34 meta + 673 enums + 8 classes) | 167 `registerMethod` | **~13%** methods | 0 enum/class registrations in Kotlin |
| `Player` methods (`player.cpp`) | 821 `Player::` methods | ~409 functions in `creatures/players/**` | **~50%** raw count | not a strict 1:1 mapping; components included |
| Monster engine (`monster.cpp`, 4,014 lines) | — | 57 functions + `MonsterAi` + `MonsterPathfinder` | **~20%** | A* + spawn engines + targeting/loot/intention/relevance |
| Map engine (`map.cpp`) | — | — | **~35%** | OTBM load + sectors + tiles + A*; no sight/`canWalkTo`/houses save |
| Persistence (`iologindata*`, `iomapserialize`) | — | — | **~30%** | vitals/level/pos + feature state (V3/V4) + inventory (V5) + depot/spells (V6); no VIP/houses |
| **Estimated full-parity completion** | — | — | **~45–50%** | weighted across the above |

### 1.2 Module completion (by inventory rows in §2)

| Module | Rows | IMPLEMENTED | PARTIAL | MISSING | Rows % | Weighted % (PARTIAL=½) |
|---|---|---|---|---|---|---|
| `src/` bootstrap | 1 | 1 | 0 | 0 | **100%** | 100% |
| `src/account/` | 3 | 3 | 0 | 0 | **100%** | 100% |
| `src/config/` | 2 | 2 | 0 | 0 | **100%** | 100% |
| `src/database/` | 4 | 4 | 0 | 0 | **100%** | 100% |
| `src/enums/` | 12 | 12 | 0 | 0 | **100%** | 100% |
| `src/security/` | 1 | 1 | 0 | 0 | **100%** | 100% |
| `src/creatures/` | 30 | 30 | 0 | 0 | **100%** | 100% |
| `src/protobuf/` | 2 | 1 | 0 | 1 | **50%** | 50% |
| `src/game/` | 10 | 0 | 6 | 4 | 0% | **30%** |
| `src/io/` | 9 | 0 | 8 | 1 | 0% | **44%** |
| `src/items/` | 10 | 0 | 7 | 3 | 0% | **35%** |
| `src/map/` | 6 | 0 | 4 | 2 | 0% | **33%** |
| `src/server/` | 5 | 0 | 3 | 2 | 0% | **30%** |
| `src/lua/` | 12 | 0 | 5 | 7 | 0% | **21%** |
| `src/utils/` | 3 | 0 | 1 | 2 | 0% | **17%** |
| `src/lib/` | 5 | 0 | 1 | 4 | 0% | **10%** |
| `src/kv/` | 1 | 0 | 0 | 1 | 0% | **0%** |
| **All modules** | **116** | **54** | **35** | **27** | **47%** | **62%** |

---

## 2. Inventory by C++ directory

### `src/` bootstrap
| C++ | Status | Missing |
|---|---|---|
| `canary_server.*`, `main.cpp` | **IMPLEMENTED** | `bootstrap/ServerBootstrap`/`ServerLaunch`/`Signals`/`CrashHandler`/`ShutdownDrain`, CLI (`ServerCli`), appearances load hook, `DatabaseTasks` |

### `src/account/`
| C++ | Status | Missing |
|---|---|---|
| `account.*` | **IMPLEMENTED** | authenticate/session/load/save, coin ledger, account type, house bid, premium purchase |
| `account_repository_db.cpp` | **IMPLEMENTED** | account create/save, coin transactions (Flyway V7), ban/session columns |
| `account_repository.cpp`, `account_info.hpp` | **IMPLEMENTED** | `AccountInfo`, `AccountType`, `AccountErrors`, session auth |

### `src/config/`
| C++ | Status | Missing |
|---|---|---|
| `configmanager.*` | **IMPLEMENTED** | typed getters (`getBoolean`/`getNumber`/`getFloat`/`getString`), `reload`, OTC feature lists |
| `config_enums.hpp` | **IMPLEMENTED** | `config/ConfigKeys.kt` — 360 keys with exact names/types/defaults |

### `src/database/`
| C++ | Status | Missing |
|---|---|---|
| `database.cpp/.hpp` (`Database`) | **IMPLEMENTED** | transactions, escaping, retry, recoverable errors, query-size limits, `DBInsert` |
| `databasemanager.cpp` | **IMPLEMENTED** | pool lifecycle/reconnect, `executeQuery`/`storeQuery` parity |
| `databasemanager.cpp`/`databasetasks` | **IMPLEMENTED** | `DatabaseTasks` (FIFO async queue, flush, shutdown) |
| `database_definitions.hpp` | **IMPLEMENTED** | `TransactionStates_t` (only enum in the header) |

### `src/enums/`
| C++ | Status | Missing |
|---|---|---|
| `item_attribute.hpp` | **IMPLEMENTED** | `items/Item.kt` (`ItemAttribute`) |
| `player_wheel.hpp` | **IMPLEMENTED** | `systems/wheel/WheelEnums.kt` |
| `weapon_proficiency.hpp` | **IMPLEMENTED** | `systems/proficiency/WeaponProficiencyEnums.kt` |
| `imbuement.hpp` | **IMPLEMENTED** | `systems/imbuement/Imbuements.kt` |
| `forge_conversion.hpp` | **IMPLEMENTED** | `systems/forge/ForgeEnums.kt` |
| `account_coins.hpp` | **IMPLEMENTED** | `systems/gamestore/AccountCoins.kt` |
| `account_errors.hpp`, `account_group_type.hpp`, `account_type.hpp` | **IMPLEMENTED** | `account/AccountInfo.kt` (`AccountErrors`, `AccountType`) |
| `container_type.hpp`, `object_category.hpp` | **IMPLEMENTED** | `items/ContainerType.kt` (`ContainerSpecial_t`) + `items/ObjectCategory.kt` |
| `lua_variant_type.hpp` | **IMPLEMENTED** | `spells/SpellValues.kt` (`LuaVariantType`) |
| `player_blessings.hpp` | **IMPLEMENTED** | `creatures/Creature.kt` (`Blessings`) |
| `player_cyclopedia.hpp` | **IMPLEMENTED** | `creatures/players/components/PlayerCyclopedia.kt` |
| `player_icons.hpp` | **IMPLEMENTED** | `combat/condition/*` (`PlayerIcon`) |

### `src/creatures/`
| C++ | Status | Missing |
|---|---|---|
| `creature.cpp` (2,239 lines) | **IMPLEMENTED** | conditions, outfit/light/speed, onThink/onAttacked/onTarget/onDeath, master/summon, spectator `canSee`, creature icons |
| `combat/combat.cpp` | **IMPLEMENTED** | full `getCombatDamage` matrix, absorb/increase/mantra, target validation, areas |
| `combat/condition.cpp` (2,955) | **IMPLEMENTED** | full condition family, attribute params, `ConditionFeared`, Player suppression |
| `combat/spells.cpp` (1,654) | **IMPLEMENTED** | runes/groups/cooldowns, learn/forget, `playerSaySpell`, `canCast`, `CombatSpell` |
| `appearance/outfit/outfit.cpp` | **IMPLEMENTED** | `Outfits`/`Outfit`/`PlayerOutfits` (XML registry, addons, `getOutfitByLookType`) |
| `appearance/mounts/mounts.cpp` | **IMPLEMENTED** | outfit protocol, storage/KV persistence, tame/toggle runtime |
| `appearance/attached_effects.cpp` | **IMPLEMENTED** | `AttachedEffects` (aura/wing/shader/effect XML registry + lookup) |
| `interactions/chat.cpp` | **IMPLEMENTED** | channel registry/invites/users, private/guild/party channels, muted checks, yell rules |
| `monsters/monster.cpp` (4,014) | **IMPLEMENTED** | spells, death/corpse, defense/reflect, random/dance step, walk-back, despawn, forge/hazard |
| `monsters/monster_pathfinding.cpp` | **IMPLEMENTED** | `MonsterPathfinder` (A* follow path) |
| `monsters/monster_combat_intention.cpp` | **IMPLEMENTED** | `MonsterCombatIntention` (geometry evaluation/scheduling) |
| `monsters/monster_relevance.cpp` | **IMPLEMENTED** | `MonsterRelevance` (enter/exit hysteresis, hold) |
| `monsters/monster_targeting.cpp` | **IMPLEMENTED** | `MonsterTargeting` (rank/search/isTarget) |
| `monsters/monsters.cpp` | **IMPLEMENTED** | `MonsterType` full schema + `Monsters` registry (reload epoch) |
| `monsters/spawns/spawn_monster.cpp` | **IMPLEMENTED** | `SpawnMonster` + `SpawnXmlLoader` (blocks, intervals, despawn) |
| `npcs/npc.cpp` (1,282) | **IMPLEMENTED** | interaction/focus, walk-to/queue, shop buy/sell runtime (dialogue stays Lua) |
| `npcs/npcs.cpp`, `spawns/spawn_npc.cpp` | **IMPLEMENTED** | `Npcs` registry + `SpawnNpc` engine |
| `players/player.cpp` (13,456, ~765 methods) | **IMPLEMENTED** | conditions, spells/cooldowns, PvP/skull, async tasks, blessings, containers, harmony/serene, wheel/forge/prey/task-hunting/cyclopedia hooks |
| `players/components/player_storage.cpp` | **IMPLEMENTED** | `PlayerStorage` delta/dirty/ingest lifecycle + reserved ranges |
| `players/components/player_forge_history.cpp` | **IMPLEMENTED** | DB load/save + Flyway `V8` |
| `players/components/player_achievement/title/badge/vip/cyclopedia/attached_effects` | **IMPLEMENTED** | titles/badges/achievements/VIP + `PlayerCyclopedia` + `PlayerAttachedEffects` |
| `players/components/pvp/*` | **IMPLEMENTED** | `PlayerPvp` (situations/unjustified/skull) + `ExpertPvp` |
| `players/components/weapon_proficiency.cpp` | **IMPLEMENTED** | gain-on-hit/kill runtime, `m_stats`, experience tables |
| `players/components/wheel/*` | **IMPLEMENTED** | `WheelSpellRegistration` into the spell registry |
| `players/grouping/party.cpp`, `team_finder.hpp` | **IMPLEMENTED** | `Party` + `TeamFinder` |
| `players/grouping/guild.cpp` | **IMPLEMENTED** | ranks/MOTD/balance, war rows/emblems, JDBC repo (Flyway V9) |
| `players/imbuements/imbuements.cpp` | **IMPLEMENTED** | apply/clear/scroll + `createScrollImbuement` + shrine window seam |
| `players/livestream`, `management/ban`, `management/waitlist` | **IMPLEMENTED** | `LivestreamManager`, `Ban`/`IOBan`, `WaitingList` |
| `players/storages/storages.cpp` (stash) | **IMPLEMENTED** | `Stash` (add/count/withdraw) + `Storages` XML |
| `players/vocations/vocation.cpp` | **IMPLEMENTED** | req formulas, promotion, gain caps, wheel gems |

### `src/game/`
| C++ | Status | Missing |
|---|---|---|
| `game.cpp` (13,141, ~352 methods) | PARTIAL (no `Game` class) | creature placement/removal, internal add/remove/move item, trade, shop, party/guild ops, channels, houses/auctions, VIP, highscores, cyclopedia, reports, feature dispatch, auto-walk, world lights/sounds, broadcast, game loop, reload |
| `functions/game_reload.cpp` | MISSING | runtime reload |
| `modal_window/*` | PARTIAL | answer routing, per-player modal tracking |
| `movement/position.cpp` | PARTIAL | direction/offset math, range rectangles parity |
| `movement/teleport.cpp` | PARTIAL | teleport effects, destination rules |
| `scheduling/dispatcher*.cpp` | PARTIAL | WDRR fairness, budgets, telemetry, task contexts |
| `scheduling/events_scheduler.cpp` | MISSING | calendar/timed events + Lua API |
| `scheduling/monster_compute_service.cpp` | MISSING | off-thread monster compute |
| `scheduling/save_manager.cpp` | PARTIAL | global save-all, shutdown flush |
| `zones/zone.cpp` | MISSING | entire Zone engine |

### `src/io/`
| C++ | Status | Missing |
|---|---|---|
| `iologindata.cpp` (24 methods) | PARTIAL | inventory/depot/inbox/stash/spells/kills/storage/VIP/guild/outfits/rewards/prey/imbuements load+save, auth, bank, GUID lookups |
| `iomapserialize.cpp` (10 methods) | MISSING | house items/info, tile serialization, guards |
| `iomap.cpp` | PARTIAL | houses/spawns/zones nodes, extended tile attributes |
| `iomarket.cpp` (19 methods) | PARTIAL | DB persistence, expiration, history, statistics, escrow |
| `ioprey.cpp` | PARTIAL | prey runtime/actions/reroll, persistence |
| `io_bosstiary.cpp` | PARTIAL | boss points, slots, cooldown persistence |
| `iobestiary.cpp` | PARTIAL | kill-counter persistence, tracker, animus mastery |
| `ioguild.cpp` | PARTIAL | DB load/save, ranks, wars |
| `player_storage_repository*.cpp` | PARTIAL | typed storage repo (add/remove/upsertKey/delta/ranges) |

### `src/items/`
| C++ | Status | Missing |
|---|---|---|
| `item.cpp` (71 methods) | PARTIAL | decay ticks, moveTo, description, weapon checks, serialization, imbuement/forge stats |
| `items.cpp` | PARTIAL | appearances/protobuf merge, augment tables, loot type, reload |
| `tile.cpp` | PARTIAL | creatures vector, flags/PZ, top/down ordering, house tile, fields |
| `thing.cpp`, `cylinder.cpp`, `trashholder.cpp` | MISSING | Thing/Cylinder abstraction, trashholder |
| `bed.cpp` | MISSING | sleeping/bed regen |
| `decay/decay.cpp` | MISSING | decay engine/timer pipeline |
| `containers/container.cpp` | PARTIAL | capacity/weight recursion, query add/remove, iterators, serialization |
| `containers/depot/*`, `inbox/*`, `mailbox/*` | PARTIAL | locker town mapping, mail routing/letters, persistence |
| `functions/item/attribute.cpp`, `item_parse.cpp` | PARTIAL | custom attributes, full XML item parse |
| `weapons/weapons.cpp` | PARTIAL | weapon registry, max-damage tables, Lua events |

### `src/kv/`
| C++ | Status | Missing |
|---|---|---|
| `kv.cpp`, `kv_sql.cpp`, `value_wrapper*.cpp` | MISSING | KV engine, SQL KV, value wrappers, protobuf values |

### `src/lib/`
| C++ | Status | Missing |
|---|---|---|
| `di/*` | MISSING | DI container/injector |
| `logging/logger.cpp` | MISSING | leveled logger, sinks |
| `messaging/*` | MISSING | message bus/command handlers |
| `metrics/metrics.cpp` | PARTIAL | counters/gauges/histograms, Prometheus output |
| `thread/thread_pool.cpp` | MISSING | bounded pool semantics |

### `src/lua/` (bindings: ~150 / 1,310 registrations)
| C++ | Status | Missing |
|---|---|---|
| `player_functions.cpp` (306) | PARTIAL | ~292 player methods |
| `monster_type_functions.cpp` (101) | MISSING | all |
| `game_functions.cpp` (59) | PARTIAL | 57 game methods |
| `item_type_functions.cpp` (57) | MISSING | all |
| `item_functions.cpp` (56) | PARTIAL | 40+ methods |
| `monster_functions.cpp` (51) | MISSING | all |
| `npc_type_functions.cpp` (40) | MISSING | all |
| `tile_functions.cpp` (31), `weapon_functions.cpp` (31) | MISSING | all |
| `spell_functions.cpp` (39) | PARTIAL | cast/target/registration |
| `zone/db/kv/logger/metrics/result/network-message/modal/party/guild/vocation/…` | MISSING | all library bindings |
| `modules/modules.cpp` (recvbyte) | MISSING | Lua client-packet modules |
| `scripts/luascript.cpp`, `script_environment.cpp` | PARTIAL | protected calls, class registration, sandboxing, reload |

### `src/map/`
| C++ | Status | Missing |
|---|---|---|
| `map.cpp` (1,429) | PARTIAL | canThrowObjectTo, canWalkTo, checkSightLine/getPathMatching, placeCreature, moveCreature, houses/spawns/zones loading, save |
| `mapcache.cpp`, `navigation_snapshot.cpp`, `map_download.cpp` | MISSING | caches/snapshots/download |
| `spectators.cpp` | PARTIAL | spectator sets, direction filtering |
| `house/house.cpp`, `housetile.cpp` | PARTIAL | rent/periods, transfers, auctions, access lists, eviction, persistence |
| `utils/astarnodes.cpp` | MISSING | A* algorithm |
| `town.hpp` | PARTIAL | Town class/temple/ID registry |

### `src/protobuf/`
| C++ | Status | Missing |
|---|---|---|
| `appearances.proto` | **IMPLEMENTED** | `io/appearances/Appearances` + dependency-free `ProtobufWireReader` (outfit/mount/effect tables from `appearances.dat`) |
| `kv.proto` | MISSING | typed KV values (KV engine pending) |

### `src/server/`
| C++ | Status | Missing |
|---|---|---|
| `server.cpp`, `signals.cpp` | PARTIAL | lifecycle/stop parity, signal drain |
| `network/connection/connection.cpp` | PARTIAL | state machine, size limits, close reasons |
| `network/protocol/protocolgame.cpp` | PARTIAL | see §5 opcode matrix |
| `network/protocol/protocol_profile.cpp`, `protocol_session_hint.cpp` | MISSING | protocol profiles, session hints |
| `network/webhook/webhook.cpp` | MISSING | Discord webhook client |

### `src/utils/`
| C++ | Status | Missing |
|---|---|---|
| `tools.cpp` (2,326) | PARTIAL | string/URI/SHA helpers, time formatting, parseNumber, wildcard |
| `wildcardtree.cpp` | MISSING | wildcard name matching |
| `batch_update.cpp` | MISSING | batch update manager + Lua binding |
| `benchmark/object_pool/lockfree/counter_pointer/vectorset/simd` | N/A | JVM/GC replaces (intentionally not ported) |

### `src/security/`
| C++ | Status | Missing |
|---|---|---|
| `rsa.cpp`, `argon.cpp` | DONE | — |

---

## 3. Highest-Impact Gaps (ranked, verified)

1. **Inbound protocol opcodes** — 65 genuinely missing + 38 decoded-but-unapplied (of 141 active); see §4 for the exact list.
2. **Outbound serialization** — 133 of 190 `ProtocolGame::send*` methods have no Kotlin counterpart (trade, market, shop, houses, VIP, channels, cyclopedia, forge, prey, task, bosstiary, stash, store).
3. **Lua bindings** — 167 of 1,246 method registrations (**~13%**); 0 of 673 enum and 8 class registrations.
4. **`Player` domain model** — ~409 Kotlin functions vs 821 `Player::` methods (~50% raw); many C++ methods still unported.
5. **`src/io/` persistence depth** — `iologindata` item/depot depth, `iomapserialize` houses, `iomarket`, `ioprey`, `iobosstiary`, `iobestiary`, `ioguild`.
6. **`src/items/` fundamentals** — `cylinder`, `bed`, `trashholder`, decay engine, full `Tile`/`Container` behaviors.
7. **Map engine** — `mapcache`, navigation snapshots, `canWalkTo`/sight/path, house/spawn/zone loading.
8. **`src/game/` depth** — `game.cpp` methods, `events_scheduler`, `monster_compute_service`, `game_reload`, `save_manager`, dispatcher WDRR/budgets/telemetry.
9. **`src/server/`** — opcode/sender completeness (see 1–2), webhook, protocol profiles, session hints, transport variants.
10. **`src/lib/`** — DI container, logger depth, messaging, metrics, thread pool.
11. **`src/kv/`** — SQL-backed KV engine replaced only by ad-hoc maps.
12. **`src/utils/`** — `tools.cpp` (2,326 lines) vs 28 Kotlin lines.
13. **`src/protobuf/`** — `kv.proto` unported (appearances done).

---

## 4. Protocol Opcode Coverage

**Handled inbound (76):** `0x14`, `0x1E`, `0x28`, `0x29`, `0x61`, `0x62`, `0x64–0x68`, `0x6A–0x72`, `0x74`, `0x75`, `0x78`, `0x7A–0x7D`…`0x80`, `0x82`, `0x83`, `0x87`, `0x88`, `0x8C`, `0x8D`, `0x8F–0x91`, `0x92–0x95`, `0x96`, `0x9C`, `0xA0–0xA8`, `0xB2`, `0xBF`, `0xD3`, `0xD4`, `0xD5–0xD7`, `0xE1–0xE4`, `0xE7`, `0xF0`, `0xF1`, `0xF4–0xF8`, `0xF9`, `0xFF` — of which **38 fully applied**, 38 decode-only.

**Missing inbound (65, with Canary parse function):**
`0x1D` playerReceivePingBack · `0x2A` parseCyclopediaMonsterTracker · `0x2B` parsePartyAnalyzerAction · `0x2C` parseLeaderFinderWindow · `0x2D` parseMemberFinderWindow · `0x2E` parseSetClientOptions · `0x32` parseExtendedOpcode · `0x38` parsePlayerTyping · `0x60` parseInventoryImbuements · `0x63` parseClientCheck · `0x69` playerStopAutoWalk · `0x76` parseCharacterTradeConfigurationAction · `0x77` parseHotkeyEquip · `0x79` parseLookInShop · `0x81` parseFriendSystemAction · `0x84` parseUseWithCreature · `0x85` parseRotateItem · `0x86` parseConfigureShowOffSocket · `0x89` parseTextWindow · `0x8A` parseHouseWindow · `0x8B` parseWrapableItem · `0x8E` parseJoinAggression · `0x97` playerRequestChannels · `0x98` parseOpenChannel · `0x99` parseCloseChannel · `0x9A` parseOpenPrivateChannel · `0x9D` parseGetTextForReport · `0x9E` playerCloseNpcChannel · `0x9F` parseSetMonsterPodium · `0xAA` playerCreatePrivateChannel · `0xAB` parseChannelInvite · `0xAC` parseChannelExclude · `0xAD` parseCyclopediaHouseAuction · `0xAE` parseSendBosstiary · `0xAF` parseSendBosstiarySlots · `0xB0` parseBosstiarySlot · `0xB1` parseHighscores · `0xB3` parseWeaponProficiency · `0xBA` parseTaskHuntingAction · `0xBE` playerCancelAttackAndFollow · `0xC0` parseForgeBrowseHistory · `0xC1` parseClientDetails · `0xC2` parseBossDifficultySelection · `0xC8` parseAimAtTarget · `0xC9` parseGetTransactionDetails · `0xCA` resendLivestreamViewerContainer · `0xCB` parseBrowseField · `0xCC` parseSeekInContainer · `0xCD` parseInspectionObject · `0xCE` parseInspectPlayer · `0xCF` sendBlessingWindow · `0xD2` playerRequestOutfit · `0xDB` parseCyclopediaMapAction · `0xDC` parseAddVip · `0xDD` parseRemoveVip · `0xDE` parseEditVip · `0xDF` parseVipGroupActions · `0xE5` parseCyclopediaCharacterInfo · `0xE6` parseBugReport · `0xE8` parseOfferDescription · `0xEB` parsePreyAction · `0xEC` parseSetHirelingName · `0xED` parseSendResourceBalance · `0xEE` parseGreet · `0xF3` parseGetObjectInfo.

**Intentionally unhandled in Canary (commented out, not counted):** `0xEF`, `0xF2`, `0xFA–0xFE` (store/coin-transfer/rule-violation).

**Outbound handled:** 57 `fun send*` (login/disconnect, self-appear `0x0A`, map description `0x64`, stats/skills/inventory/container/creature/text, wheel/forge/bestiary/market/NPC-shop/party/guild/prey/task/bosstiary/imbuement/cyclopedia window serializers) vs 190 C++ senders.

---

## 5. Maintenance

After each wave (AGENTS.md §6), update the coverage table in §1, the affected rows in §2, and the ranked list in §3. Keep `docs/game-play-features.md`, `README.md`, and `CHANGELOG.md` in sync.
