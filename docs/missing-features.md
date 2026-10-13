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
| Inbound `ProtocolGame` opcodes | 141 active (148 cases − 7 commented-out) | 141 decoded | **100%** | ~46/141 (**~33%**) fully applied; the rest decode + WorldCommit confinement boundary (engine state machine unapplied) |
| Outbound `ProtocolGame` senders | 190 `ProtocolGame::send*` | 58 `fun send*` | **~31%** | trade/market/shop/channel/house/VIP/store/cyclopedia/inspection writers added in wave 35 |
| Lua bindings (`src/lua/functions/*`) | 1,961 registrations (1,246 methods + 34 meta + 673 enums + 8 classes) | 167 `registerMethod` | **~13%** methods | 0 enum/class registrations in Kotlin |
| `Player` methods (`player.cpp`) | 821 `Player::` methods | ~409 functions in `creatures/players/**` | **~50%** raw count | not a strict 1:1 mapping; components included |
| Monster engine (`monster.cpp`, 4,014 lines) | — | 57 functions + `MonsterAi` + `MonsterPathfinder` | **~20%** | A* + spawn engines + targeting/loot/intention/relevance |
| Map engine (`map.cpp`) | — | — | **~35%** | OTBM load + sectors + tiles + A*; no sight/`canWalkTo`/houses save |
| Persistence (`iologindata*`, `iomapserialize`) | — | — | **~80%** | 24/24 iologindata, 10/10 iomapserialize, market/prey/bestiary/bosstiary/guild/storage (V10–V14) |
| **Estimated full-parity completion** | — | — | **~55–60%** | weighted across the above |

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
| `src/game/` | 10 | 9 | 1 | 0 | **90%** | **95%** |
| `src/io/` | 9 | 9 | 0 | 0 | **100%** | **100%** |
| `src/items/` | 10 | 0 | 7 | 3 | 0% | **35%** |
| `src/map/` | 6 | 0 | 4 | 2 | 0% | **33%** |
| `src/server/` | 5 | 0 | 3 | 2 | 0% | **30%** |
| `src/lua/` | 12 | 0 | 5 | 7 | 0% | **21%** |
| `src/utils/` | 3 | 0 | 1 | 2 | 0% | **17%** |
| `src/lib/` | 5 | 0 | 1 | 4 | 0% | **10%** |
| `src/kv/` | 1 | 0 | 0 | 1 | 0% | **0%** |
| **All modules** | **116** | **72** | **18** | **26** | **62%** | **70%** |

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
| `game.cpp` (13,141, ~352 methods) | PARTIAL | core `Game` class ported (placement/removal, internal item add/remove/move, broadcast, lights/sounds, `playerStopAutoWalk`); remaining: trade/shop/party/guild Game methods, channels, houses/auctions, VIP, highscores, cyclopedia, reports, game loop |
| `functions/game_reload.cpp` | **IMPLEMENTED** | `game/reload/GameReload.kt` (generation-checked reload, `Reload_t`) |
| `modal_window/*` | **IMPLEMENTED** | `game/modal/ModalWindow.kt` (per-player tracking + answer routing) |
| `movement/position.cpp` | **IMPLEMENTED** | `game/movement/Position.kt` (offsets/distances/rectangles/directions) |
| `movement/teleport.cpp` | **IMPLEMENTED** | `game/movement/Teleport.kt` (dest rules, loop check, effects) |
| `scheduling/dispatcher*.cpp` | **IMPLEMENTED** | WDRR/budgets/telemetry/policy + lane-aware passes |
| `scheduling/events_scheduler.cpp` | **IMPLEMENTED** | `game/scheduling/EventsScheduler.kt` (calendar, rate compounding) |
| `scheduling/monster_compute_service.cpp` | **IMPLEMENTED** | `game/scheduling/MonsterComputeService.kt` |
| `scheduling/save_manager.cpp` | **IMPLEMENTED** | save-all phases + idempotent shutdown flush |
| `zones/zone.cpp` | **IMPLEMENTED** | `game/zones/` (Zone engine, wave 23) |

### `src/io/`
| C++ | Status | Missing |
|---|---|---|
| `iologindata.cpp` (24 methods) | **IMPLEMENTED** | 24/24: auth/GUID/bank, load/save player + online-player data, inventory/depot/inbox/kills/stash/rewards/storage, VIP entries+groups (V10) |
| `iomapserialize.cpp` (10 methods) | **IMPLEMENTED** | 10/10: house items/info load+save, guards, container/item/tile serialization (V11) |
| `iomap.cpp` | **IMPLEMENTED** | houses/monsters/npcs/zones XML nodes, extended tile attributes, OTBM house/zone tiles |
| `iomarket.cpp` (19 methods) | **IMPLEMENTED** | 18/18: offers/history/statistics/expiration, escrow seam (V12) |
| `ioprey.cpp` | **IMPLEMENTED** | prey + task-hunting persistence, premium slot lock, cooldown promotion (V12) |
| `io_bosstiary.cpp` | **IMPLEMENTED** | levels/loot/boss points, boosted boss, slots/removeTimes/tracker (V13) |
| `iobestiary.cpp` | **IMPLEMENTED** | charms/echoes/maxima/bits, kill counts, tracker, animus mastery persistence (V13) |
| `ioguild.cpp` | **IMPLEMENTED** | `IOGuild` (load/save/getGuildIdByName/getWarList) over V9 tables |
| `player_storage_repository*.cpp` | **IMPLEMENTED** | typed repo: load/deleteKeys/bulk upsert with exact semantics (V14) |

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

1. **Outbound serialization** — 132 of 190 `ProtocolGame::send*` methods have no Kotlin counterpart (remaining creature/map senders, cyclopedia computation frames).
2. **Lua bindings** — 167 of 1,246 method registrations (**~13%**); 0 of 673 enum and 8 class registrations.
3. **`Player` domain model** — ~409 Kotlin functions vs 821 `Player::` methods (~50% raw); many C++ methods still unported.
4. **Inbound opcode *application*** — all 141 active opcodes decode, but ~95 still stop at the WorldCommit confinement boundary because their engine (trade session, shop, houses, channels, VIP composition, inspection, cyclopedia frames) is unapplied.
5. **`src/items/` fundamentals** — `cylinder`, `bed`, `trashholder`, decay engine, full `Tile`/`Container` behaviors.
6. **Map engine** — `mapcache`, navigation snapshots, `canWalkTo`/sight/path, house/spawn/zone loading.
7. **`game.cpp` remainder** — trade/shop/party/guild Game methods, houses/auctions, VIP, highscores, reports, game loop.
8. **`src/server/`** — webhook, protocol profiles, session hints, transport variants.
9. **`src/lib/`** — DI container, logger depth, messaging, metrics, thread pool.
10. **`src/kv/`** — SQL-backed KV engine replaced only by ad-hoc maps.
11. **`src/utils/`** — `tools.cpp` (2,326 lines) vs 28 Kotlin lines.
12. **`src/protobuf/`** — `kv.proto` unported (appearances done).

---

## 4. Protocol Opcode Coverage

**Handled inbound (141 / 141 active):** all opcodes dispatched by Canary's `parsePacketFromDispatcher` switch (148 cases − 7 commented-out) now have a Kotlin decode handler wired in `GamePacketRouter`. Wave 35 added the final 64: `0x1D`, `0x2A–0x2E`, `0x32`, `0x38`, `0x60`, `0x63`, `0x69`, `0x76`, `0x77`, `0x79`, `0x81`, `0x84–0x8B`, `0x8E`, `0x97–0x9A`, `0x9D–0x9F`, `0xAA–0xB1`, `0xB3`, `0xBA`, `0xBE`, `0xC0–0xC2`, `0xC8–0xCF`, `0xD2`, `0xDB–0xDF`, `0xE5`, `0xE6`, `0xE8`, `0xEB–0xEE`, `0xF3`.

**Application status:** ~46 of the 141 fully apply their Canary state change on the WorldCommit lane; the remainder decode faithfully and stop at the confinement boundary with the Canary call documented inline (PARITY GAP) until the corresponding engine is composed.

**Intentionally unhandled in Canary (commented out):** `0xEF`, `0xF2`, `0xFA–0xFE` (store/coin-transfer/rule-violation).

**Outbound handled:** 58 `fun send*` (login/disconnect, self-appear `0x0A`, map description `0x64`, stats/skills/inventory/container/creature/text, wheel/forge/bestiary/market/NPC-shop/party/guild/prey/task/bosstiary/imbuement/cyclopedia window serializers, plus wave-35 trade/channel/house/VIP/store/inspection/blessing writers) vs 190 C++ senders.

---

## 5. Maintenance

After each wave (AGENTS.md §6), update the coverage table in §1, the affected rows in §2, and the ranked list in §3. Keep `docs/game-play-features.md`, `README.md`, and `CHANGELOG.md` in sync.
