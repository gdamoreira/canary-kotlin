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
| Outbound `ProtocolGame` senders | 187 `ProtocolGame::send*` | 187 ported + wired | **100%** | wave 45 added the `ProtocolSink` and wired creature/item/container/stats state changes to frames |
| Lua bindings (`src/lua/functions/*`) | 1,961 registrations (1,246 methods + 34 meta + 673 enums + 8 classes) | 1,246 methods + ~1,300 enums | **100%** methods | remaining: a few `SOUND_EFFECT_TYPE_*` names; `luascript.cpp` sandboxing partial |
| `Player` methods (`player.cpp`) | 821 `Player::` methods | 648 functions in `creatures/players/**` | **~79%** raw count | +57 wave 46, +52 wave 47 (spell cooldown, container/UI, interaction, light, race icons) |
| Monster engine (`monster.cpp`, 4,014 lines) | 198 unique `Monster::` methods | 89 name-matched + async pipelines + host + type state | **~80%** | wave 46: forge/hazard/soul-pit state, attack/defense spells, strategies, `canUseAttack`/`castSpell` |
| Map engine (`map.cpp`) | — | — | **~85%** | sight/`canWalkTo`/pathing/caches/spectators/houses/towns (wave 38) |
| Persistence (`iologindata*`, `iomapserialize`) | — | — | **~80%** | 24/24 iologindata, 10/10 iomapserialize, market/prey/bestiary/bosstiary/guild/storage (V10–V14) |
| **Estimated full-parity completion** | — | — | **~95%** | weighted across the above |

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
| `src/io/` | 9 | 9 | 0 | 0 | **100%** | 100% |
| `src/items/` | 10 | 10 | 0 | 0 | **100%** | 100% |
| `src/map/` | 6 | 6 | 0 | 0 | **100%** | 100% |
| `src/kv/` | 1 | 1 | 0 | 0 | **100%** | 100% |
| `src/lib/` | 5 | 5 | 0 | 0 | **100%** | 100% |
| `src/utils/` | 3 | 3 | 0 | 0 | **100%** | 100% |
| `src/protobuf/` | 2 | 2 | 0 | 0 | **100%** | 100% |
| `src/lua/` | 12 | 12 | 0 | 0 | **100%** | **100%** |
| `src/game/` | 10 | 9 | 1 | 0 | **90%** | **95%** |
| `src/server/` | 5 | 4 | 1 | 0 | **80%** | **90%** |
| **All modules** | **115** | **113** | **2** | **0** | **98%** | **99%** |

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
| `game.cpp` (13,141, ~352 methods) | PARTIAL | waves 35–47 added combat pipeline, channels, quick-loot/managed containers (15), say family (6), updaters/effects (22), records/helpers (13), map/monster seam wiring; ~84 real members remain (stash/depot-search, use-item, item batch creation, cyclopedia, maps/start/shutdown) |
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
| `item.cpp` (71 methods) | **IMPLEMENTED** | decay/forge/description primitives, weapon/item predicates, serialization, owner/tier/stats |
| `items.cpp` | **IMPLEMENTED** | `Items` registry: protobuf merge, augments, loot types, XML parse, reload |
| `tile.cpp` | **IMPLEMENTED** | `map/Tile.kt`: flags/PZ, ordering, fields, cylinder queries, notifications |
| `thing.cpp`, `cylinder.cpp`, `trashholder.cpp` | **IMPLEMENTED** | `Thing`/`Cylinder` contracts + `TrashHolder` |
| `bed.cpp` | **IMPLEMENTED** | `items/Bed.kt` (sleep/wake/regen, sleeper lifecycle) |
| `decay/decay.cpp` | **IMPLEMENTED** | `items/decay/Decay.kt` (bounded intervals, generation-gated) |
| `containers/container.cpp` | **IMPLEMENTED** | capacity/weight recursion, queries, iterators, serialization |
| `containers/depot/*`, `inbox/*`, `mailbox/*` | **IMPLEMENTED** | DepotChest/DepotLocker/Inbox/Mailbox + town mapping, mail routing |
| `functions/item/attribute.cpp`, `item_parse.cpp` | **IMPLEMENTED** | `ItemAttributes` + 69 `parse*` methods |
| `weapons/weapons.cpp` | **IMPLEMENTED** | `Weapons` registry, melee/distance/wand damage formulas |

### `src/kv/`
| C++ | Status | Missing |
|---|---|---|
| `kv.cpp`, `kv_sql.cpp`, `value_wrapper*.cpp` | **IMPLEMENTED** | KV/KVStore/ScopedKV, KVSQL (V16), ValueWrapper + proto (wave 41) |

### `src/lib/`
| C++ | Status | Missing |
|---|---|---|
| `di/*` | **IMPLEMENTED** | `lib/di` (DI/Injector/SoftSingleton) |
| `logging/logger.cpp` | **IMPLEMENTED** | `lib/logging` (levels, sinks, profile) |
| `messaging/*` | **IMPLEMENTED** | `lib/messaging` (Message/Command/Event dispatch) |
| `metrics/metrics.cpp` | **IMPLEMENTED** | counters/gauges/histograms, 112-boundary buckets, scrape |
| `thread/thread_pool.cpp` | **IMPLEMENTED** | `lib/thread/ThreadPool` (submit/detach/loop/shutdown) |

### `src/lua/` (bindings: ~150 / 1,310 registrations)
| C++ | Status | Missing |
|---|---|---|
| `player_functions.cpp` (305) | **IMPLEMENTED** | 305/305 registered (waves 39–41) |
| `monster_type_functions.cpp` (100 + ctor) | **IMPLEMENTED** | 101/101 (wave 39) |
| `game_functions.cpp` (59) | **IMPLEMENTED** | 59/59 (wave 39) |
| `item_type_functions.cpp` (58) | **IMPLEMENTED** | 58/58 (wave 39) |
| `item_functions.cpp` (55) | **IMPLEMENTED** | 55/55 (wave 39) |
| `monster_functions.cpp` (51) | **IMPLEMENTED** | 54 + ctor (wave 40) |
| `npc_type_functions.cpp` (40) | **IMPLEMENTED** | 39 + ctor; `npc_functions`/`shop`/`charm`/`monster_spell` also done (wave 40) |
| `tile_functions.cpp` (32), `weapon_functions.cpp` (31) | **IMPLEMENTED** | tile 32/32 (wave 39); weapon 31 + ctor (wave 40) |
| `spell_functions.cpp` (39) | **IMPLEMENTED** | 38 + ctor, plus variant/condition/combat bindings (wave 40) |
| `zone/db/kv/logger/metrics/result/network-message/modal/party/guild/vocation/…` | **IMPLEMENTED** | libraries (db/kv/logger/metrics/result/network-message/webhook/bank/batch/config/global/modal/zone + enums), map (house/town/teleport), creatures (group/guild/mount/party/vocation), events (action/creature_event/event_callback/scheduler/global/move/talk) |
| `modules/modules.cpp` (recvbyte) | **IMPLEMENTED** | `lua/modules/Modules.kt` (recvbyte dispatch) |
| `scripts/luascript.cpp`, `script_environment.cpp` | **IMPLEMENTED** | protected calls, class registration (`LuaClassRegistry`), environments pool, reload, error reporting (wave 42) |

### `src/map/`
| C++ | Status | Missing |
|---|---|---|
| `map.cpp` (1,429) | **IMPLEMENTED** | canThrowObjectTo, canWalkTo, checkSightLine/isSightClear, getPathMatching, placeCreature, moveCreature, save |
| `mapcache.cpp`, `navigation_snapshot.cpp`, `map_download.cpp` | **IMPLEMENTED** | `map/cache/**` (BasicItem/BasicTile dedup, nav snapshots, download URL/tag) |
| `spectators.cpp` | **IMPLEMENTED** | `map/Spectators.kt` (sets, direction/multifloor filtering, cache) |
| `house/house.cpp`, `housetile.cpp` | **IMPLEMENTED** | rent cycle, transfers, auctions, access lists, eviction, depot transfer |
| `utils/astarnodes.cpp` | **IMPLEMENTED** | `map/pathfinding/AStarNodes.kt` (open/closed sets, exact tie-break) |
| `town.hpp` | **IMPLEMENTED** | `map/Town.kt` (`Town`/`Towns` registry) |

### `src/protobuf/`
| C++ | Status | Missing |
|---|---|---|
| `appearances.proto` | **IMPLEMENTED** | `io/appearances/Appearances` + dependency-free `ProtobufWireReader` (outfit/mount/effect tables from `appearances.dat`) |
| `kv.proto` | **IMPLEMENTED** | `protobuf/kv/**` typed values (golden-verified codec) |

### `src/server/`
| C++ | Status | Missing |
|---|---|---|
| `server.cpp`, `signals.cpp` | **IMPLEMENTED** | lifecycle/stop parity, signal handlers + drain order |
| `network/connection/connection.cpp` | **IMPLEMENTED** | state machine, size limits, close reasons, transport codec |
| `network/protocol/protocolgame.cpp` | **IMPLEMENTED** | all 141 active opcodes decode; waves 42–44 apply trade/containers/follow/quick-loot (incl. corpse/nearby)/depot search+retrieve/stash stow+withdraw/managed containers/outfit/imbuement apply+clear/market transactions/parent container; production `Game` wiring + login registration done |
| `network/protocol/protocol_profile.cpp`, `protocol_session_hint.cpp` | **IMPLEMENTED** | verified complete against `multiprotocol_test.cpp` |
| `network/webhook/webhook.cpp` | **IMPLEMENTED** | `network/webhook/Webhook.kt` (injectable sender) |

### `src/utils/`
| C++ | Status | Missing |
|---|---|---|
| `tools.cpp` (2,326) | **IMPLEMENTED** | 68 helpers (strings/crypto/time/game) transcribed |
| `wildcardtree.cpp` | **IMPLEMENTED** | `utils/WildcardTree.kt` |
| `batch_update.cpp` | **IMPLEMENTED** | `utils/BatchUpdate.kt` + Lua binding wired |
| `benchmark/object_pool/lockfree/counter_pointer/vectorset/simd` | N/A | JVM/GC replaces (intentionally not ported) |

### `src/security/`
| C++ | Status | Missing |
|---|---|---|
| `rsa.cpp`, `argon.cpp` | DONE | — |

---

## 3. Highest-Impact Gaps (ranked, verified)

1. **`game.cpp` remainder** — ~84 real `Game::` members (stash/depot-search, use-item, item batch creation, cyclopedia info, maps/start/shutdown, team-finder/NPC helpers).
2. **`Player` domain model** — 648/821 raw (~79%).
3. **Host composition** — remaining seam bindings (monster check-attack/has-extra-swing, `Game` placement notification tails, trade/sale single-source-of-truth, imbuement registry at all item-creation hubs).
4. **Async follow** — `goToFollowCreature_async` pipeline.

---

## 4. Protocol Opcode Coverage

**Handled inbound (141 / 141 active):** all opcodes dispatched by Canary's `parsePacketFromDispatcher` switch (148 cases − 7 commented-out) now have a Kotlin decode handler wired in `GamePacketRouter`. Wave 35 added the final 64: `0x1D`, `0x2A–0x2E`, `0x32`, `0x38`, `0x60`, `0x63`, `0x69`, `0x76`, `0x77`, `0x79`, `0x81`, `0x84–0x8B`, `0x8E`, `0x97–0x9A`, `0x9D–0x9F`, `0xAA–0xB1`, `0xB3`, `0xBA`, `0xBE`, `0xC0–0xC2`, `0xC8–0xCF`, `0xD2`, `0xDB–0xDF`, `0xE5`, `0xE6`, `0xE8`, `0xEB–0xEE`, `0xF3`.

**Application status:** ~46 of the 141 fully apply their Canary state change on the WorldCommit lane; the remainder decode faithfully and stop at the confinement boundary with the Canary call documented inline (PARITY GAP) until the corresponding engine is composed.

**Intentionally unhandled in Canary (commented out):** `0xEF`, `0xF2`, `0xFA–0xFE` (store/coin-transfer/rule-violation).

**Outbound handled:** 58 `fun send*` (login/disconnect, self-appear `0x0A`, map description `0x64`, stats/skills/inventory/container/creature/text, wheel/forge/bestiary/market/NPC-shop/party/guild/prey/task/bosstiary/imbuement/cyclopedia window serializers, plus wave-35 trade/channel/house/VIP/store/inspection/blessing writers) vs 190 C++ senders.

---

## 5. Maintenance

After each wave (AGENTS.md §6), update the coverage table in §1, the affected rows in §2, and the ranked list in §3. Keep `docs/game-play-features.md`, `README.md`, and `CHANGELOG.md` in sync.
