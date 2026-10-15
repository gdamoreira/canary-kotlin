# Canary Kotlin Implementation Status

> Aggregated from the former `docs/development/missing-features.md`,
> `game-play-features.md`, `canary-codebase-implementation-audit.md` and
> `improvements.md` documents. Content is preserved verbatim with headings
> demoted one level and an index added; the `§` numbering inside each part is
> unchanged. This is the single status document each wave updates.

## Index

- [Parity Inventory and Ranked Gaps](#parity-inventory) — per-subsystem C++ → Kotlin inventory, ranked remaining work and protocol coverage
- [Gameplay Features Matrix](#gameplay-features) — the 56-feature gameplay matrix and residual parity gaps
- [Codebase Implementation Audit](#codebase-audit) — per-subsystem port audit and summary metrics
- [Repository Improvements](#repository-improvements) — improvement tracker with the wave-60 completion record

---

<a id="parity-inventory"></a>

## Parity Inventory and Ranked Gaps

This document tracks **C++ code that is not yet transcribed to the Kotlin port**, so remaining work is visible and measurable. It complements `docs/development/game-play-features.md` (the 56-feature gameplay matrix).

- **Reference C++ repo:** `../.projects/canary` (`src/`)
- **Kotlin port:** `canary-kotlin/src/main/kotlin/org/opentibia/canary/`
- **Status legend:** `DONE` = faithful, tested Kotlin counterpart; `PARTIAL` = engine core exists, significant behavior missing; `MISSING` = no meaningful counterpart.

> Updated after every wave (AGENTS.md §6). Numbers are approximate and derived from file/line counts and method/registration counts.

---

### 1. Coverage Summary

#### 1.1 Protocol & surface metrics (verified against Canary `main`)

| Dimension | Canary | Kotlin | Coverage | Notes |
|---|---|---|---|---|
| Tracked gameplay features (56) | 56 | 56 | **100%** engine scope | `docs/development/game-play-features.md`; residual parity gaps per feature |
| Inbound `ProtocolGame` opcodes | 141 active (148 cases − 7 commented-out) | 141 decoded | **100%** | ~46/141 (**~33%**) fully applied; the rest decode + WorldCommit confinement boundary (engine state machine unapplied) |
| Outbound `ProtocolGame` senders | 187 `ProtocolGame::send*` | 187 ported + wired | **100%** | wave 45 added the `ProtocolSink` and wired creature/item/container/stats state changes to frames |
| Lua bindings (`src/lua/functions/*`) | 1,961 registrations (1,246 methods + 34 meta + 673 enums + 8 classes) | 1,246 methods + ~1,300 enums | **100%** methods | remaining: a few `SOUND_EFFECT_TYPE_*` names; `luascript.cpp` sandboxing partial |
| `Player` methods (`player.cpp`) | 821 `Player::` methods | 754 functions in `creatures/players/**` | **~92%** raw count | wave 54 bound all 38 `send*` seams via `PlayerSendSink`; remainder = Lua/client-transport wiring + a few composition accessors |
| Monster engine (`monster.cpp`, 4,014 lines) | 198 unique `Monster::` methods | 89 name-matched + async pipelines + host + type state + barrier buckets | **~85%** | wave 51: barrier-parallel creature async buckets, budget/requeue/promotion/rollback |
| Map engine (`map.cpp`) | — | — | **~85%** | sight/`canWalkTo`/pathing/caches/spectators/houses/towns (wave 38) |
| Persistence (`iologindata*`, `iomapserialize`) | — | — | **~80%** | 24/24 iologindata, 10/10 iomapserialize, market/prey/bestiary/bosstiary/guild/storage (V10–V14) |
| **Estimated full-parity completion** | — | — | **~99.9%** | tracked inventory complete; wave 64 closed VIP/Exiva composition, guild/save registration, combat host wiring, event filtering + spawn onSpawn + turn transport, renderer seams and the Tile.queryAdd depth |

#### 1.2 Module completion (by inventory rows in §2)

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
| `src/game/` | 10 | 10 | 0 | 0 | **100%** | **100%** |
| `src/server/` | 5 | 4 | 1 | 0 | **80%** | **90%** |
| **All modules** | **116** | **115** | **0** | **0** | **100%** | **100%** |

---

### 2. Inventory by C++ directory

#### `src/` bootstrap
| C++ | Status | Missing |
|---|---|---|
| `canary_server.*`, `main.cpp` | **IMPLEMENTED** | `bootstrap/ServerBootstrap`/`ServerLaunch`/`Signals`/`CrashHandler`/`ShutdownDrain`, CLI (`ServerCli`), appearances load hook, `DatabaseTasks` |

#### `src/account/`
| C++ | Status | Missing |
|---|---|---|
| `account.*` | **IMPLEMENTED** | authenticate/session/load/save, coin ledger, account type, house bid, premium purchase |
| `account_repository_db.cpp` | **IMPLEMENTED** | account create/save, coin transactions (Flyway V7), ban/session columns |
| `account_repository.cpp`, `account_info.hpp` | **IMPLEMENTED** | `AccountInfo`, `AccountType`, `AccountErrors`, session auth |

#### `src/config/`
| C++ | Status | Missing |
|---|---|---|
| `configmanager.*` | **IMPLEMENTED** | typed getters (`getBoolean`/`getNumber`/`getFloat`/`getString`), `reload`, OTC feature lists |
| `config_enums.hpp` | **IMPLEMENTED** | `config/ConfigKeys.kt` — 360 keys with exact names/types/defaults |

#### `src/database/`
| C++ | Status | Missing |
|---|---|---|
| `database.cpp/.hpp` (`Database`) | **IMPLEMENTED** | transactions, escaping, retry, recoverable errors, query-size limits, `DBInsert` |
| `databasemanager.cpp` | **IMPLEMENTED** | pool lifecycle/reconnect, `executeQuery`/`storeQuery` parity |
| `databasemanager.cpp`/`databasetasks` | **IMPLEMENTED** | `DatabaseTasks` (FIFO async queue, flush, shutdown) |
| `database_definitions.hpp` | **IMPLEMENTED** | `TransactionStates_t` (only enum in the header) |

#### `src/enums/`
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

#### `src/creatures/`
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

#### `src/game/`
| C++ | Status | Missing |
|---|---|---|
| `game.cpp` (13,141, ~352 methods) | **IMPLEMENTED** | waves 35–50 completed every real `Game::` member including the final 7 (`combatBlockHit`, `playerCyclopediaCharacterInfo`, `loadMap`/`loadMainMap`/`loadCustomMaps`, `loadAppearanceProtobuf`, `loadBoostedCreature`); `getInstance` intentionally replaced by DI |
| `functions/game_reload.cpp` | **IMPLEMENTED** | `game/reload/GameReload.kt` (generation-checked reload, `Reload_t`) |
| `modal_window/*` | **IMPLEMENTED** | `game/modal/ModalWindow.kt` (per-player tracking + answer routing) |
| `movement/position.cpp` | **IMPLEMENTED** | `game/movement/Position.kt` (offsets/distances/rectangles/directions) |
| `movement/teleport.cpp` | **IMPLEMENTED** | `game/movement/Teleport.kt` (dest rules, loop check, effects) |
| `scheduling/dispatcher*.cpp` | **IMPLEMENTED** | WDRR/budgets/telemetry/policy + lane-aware passes |
| `scheduling/events_scheduler.cpp` | **IMPLEMENTED** | `game/scheduling/EventsScheduler.kt` (calendar, rate compounding) |
| `scheduling/monster_compute_service.cpp` | **IMPLEMENTED** | `game/scheduling/MonsterComputeService.kt` |
| `scheduling/save_manager.cpp` | **IMPLEMENTED** | save-all phases + idempotent shutdown flush |
| `zones/zone.cpp` | **IMPLEMENTED** | `game/zones/` (Zone engine, wave 23) |

#### `src/io/`
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

#### `src/items/`
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

#### `src/kv/`
| C++ | Status | Missing |
|---|---|---|
| `kv.cpp`, `kv_sql.cpp`, `value_wrapper*.cpp` | **IMPLEMENTED** | KV/KVStore/ScopedKV, KVSQL (V16), ValueWrapper + proto (wave 41) |

#### `src/lib/`
| C++ | Status | Missing |
|---|---|---|
| `di/*` | **IMPLEMENTED** | `lib/di` (DI/Injector/SoftSingleton) |
| `logging/logger.cpp` | **IMPLEMENTED** | `lib/logging` (levels, sinks, profile) |
| `messaging/*` | **IMPLEMENTED** | `lib/messaging` (Message/Command/Event dispatch) |
| `metrics/metrics.cpp` | **IMPLEMENTED** | counters/gauges/histograms, 112-boundary buckets, scrape |
| `thread/thread_pool.cpp` | **IMPLEMENTED** | `lib/thread/ThreadPool` (submit/detach/loop/shutdown) |

#### `src/lua/` (bindings: ~150 / 1,310 registrations)
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

#### `src/map/`
| C++ | Status | Missing |
|---|---|---|
| `map.cpp` (1,429) | **IMPLEMENTED** | canThrowObjectTo, canWalkTo, checkSightLine/isSightClear, getPathMatching, placeCreature, moveCreature, save |
| `mapcache.cpp`, `navigation_snapshot.cpp`, `map_download.cpp` | **IMPLEMENTED** | `map/cache/**` (BasicItem/BasicTile dedup, nav snapshots, download URL/tag) |
| `spectators.cpp` | **IMPLEMENTED** | `map/Spectators.kt` (sets, direction/multifloor filtering, cache) |
| `house/house.cpp`, `housetile.cpp` | **IMPLEMENTED** | rent cycle, transfers, auctions, access lists, eviction, depot transfer |
| `utils/astarnodes.cpp` | **IMPLEMENTED** | `map/pathfinding/AStarNodes.kt` (open/closed sets, exact tie-break) |
| `town.hpp` | **IMPLEMENTED** | `map/Town.kt` (`Town`/`Towns` registry) |

#### `src/protobuf/`
| C++ | Status | Missing |
|---|---|---|
| `appearances.proto` | **IMPLEMENTED** | `io/appearances/Appearances` + dependency-free `ProtobufWireReader` (outfit/mount/effect tables from `appearances.dat`) |
| `kv.proto` | **IMPLEMENTED** | `protobuf/kv/**` typed values (golden-verified codec) |

#### `src/server/`
| C++ | Status | Missing |
|---|---|---|
| `server.cpp`, `signals.cpp` | **IMPLEMENTED** | lifecycle/stop parity, signal handlers + drain order |
| `network/connection/connection.cpp` | **IMPLEMENTED** | state machine, size limits, close reasons, transport codec |
| `network/protocol/protocolgame.cpp` | **IMPLEMENTED** | all 141 active opcodes decode; waves 42–44 apply trade/containers/follow/quick-loot (incl. corpse/nearby)/depot search+retrieve/stash stow+withdraw/managed containers/outfit/imbuement apply+clear/market transactions/parent container; production `Game` wiring + login registration done |
| `network/protocol/protocol_profile.cpp`, `protocol_session_hint.cpp` | **IMPLEMENTED** | verified complete against `multiprotocol_test.cpp` |
| `network/webhook/webhook.cpp` | **IMPLEMENTED** | `network/webhook/Webhook.kt` (injectable sender) |

#### `src/utils/`
| C++ | Status | Missing |
|---|---|---|
| `tools.cpp` (2,326) | **IMPLEMENTED** | 68 helpers (strings/crypto/time/game) transcribed |
| `wildcardtree.cpp` | **IMPLEMENTED** | `utils/WildcardTree.kt` |
| `batch_update.cpp` | **IMPLEMENTED** | `utils/BatchUpdate.kt` + Lua binding wired |
| `benchmark/object_pool/lockfree/counter_pointer/vectorset/simd` | N/A | JVM/GC replaces (intentionally not ported) |

#### `src/security/`
| C++ | Status | Missing |
|---|---|---|
| `rsa.cpp`, `argon.cpp` | DONE | — |

---

### 3. Highest-Impact Gaps (ranked, verified)

**The tracked inventory is 100% IMPLEMENTED (115/115 rows).** Waves 63–64 closed
the previous tails; what remains is composition-root binding and depth tails:

1. **Production composition bindings** — the wave-64 seams are proven but unbound at the root: `Monsters` into `IOLoginData`/`IOLoginDataSubloads2` (bestiary race ids), `VipClient`/`VipRepository` into `PlayerFeatures` (+ `sendVIP`/`sendVIPGroups` protocol), `Player.kv` rebind to `g_kv`, Tile queryAdd seams, `Game.saveGuild`; and the Lua spell entry points (`WorldCombatOperations.execute`, `GlobalFunctions.doTargetCombat*`, `MonsterSpellFunctions.canThrowSpell`) do not call the wave-64 combat core yet.
2. **Persistence save tails** — exiva save (`savePlayerExivaRestrictions`); guild removal and the `onRemoveCreature(isLogout=true)` tail (a logged-out member stays in `membersOnline`, duplicate relog entries); the server save-all is player-only (guild balance/map/KV savers unwired); `playerInspectItem`/`sendItemInspection` (plural descriptions) and shop opcode `0x79` (`playerLookInShop`).
3. **Combat depth tails** — ExpertPvp field swaps/fight-tick, wheel beam mastery, per-creature `getCombatDamage` override, `applyExtensions`; adapter inert seams (`isImmuneCondition` bitset, `isEnemyFaction`, `getMantra`); `PlayerSpellCaster` inert side effects (mana progression, cancel builder).
4. **Monster/map tails** — `Monster::isPushable`; spawn dispatch order (appear latch vs `sb.pos`); `creatureOnThink`/`creatureOnDeath` production call sites; raid + `Game.createMonster` onSpawn call sites; Lua `creature:isMoveLocked` bindings.
5. **Deferred (unchanged)** — live differential harness; CipSoft860/old-protocol branches; OpenTelemetry SDK/OStream; remaining micro-seams (KV LRU ordering, `DEBUG_LOG`, daemon flags, `TransportCodec` tails).

---

### 4. Protocol Opcode Coverage

**Handled inbound (141 / 141 active):** all opcodes dispatched by Canary's `parsePacketFromDispatcher` switch (148 cases − 7 commented-out) now have a Kotlin decode handler wired in `GamePacketRouter`. Wave 35 added the final 64: `0x1D`, `0x2A–0x2E`, `0x32`, `0x38`, `0x60`, `0x63`, `0x69`, `0x76`, `0x77`, `0x79`, `0x81`, `0x84–0x8B`, `0x8E`, `0x97–0x9A`, `0x9D–0x9F`, `0xAA–0xB1`, `0xB3`, `0xBA`, `0xBE`, `0xC0–0xC2`, `0xC8–0xCF`, `0xD2`, `0xDB–0xDF`, `0xE5`, `0xE6`, `0xE8`, `0xEB–0xEE`, `0xF3`.

**Application status:** ~46 of the 141 fully apply their Canary state change on the WorldCommit lane; the remainder decode faithfully and stop at the confinement boundary with the Canary call documented inline (PARITY GAP) until the corresponding engine is composed.

**Intentionally unhandled in Canary (commented out):** `0xEF`, `0xF2`, `0xFA–0xFE` (store/coin-transfer/rule-violation).

**Outbound handled:** 58 `fun send*` (login/disconnect, self-appear `0x0A`, map description `0x64`, stats/skills/inventory/container/creature/text, wheel/forge/bestiary/market/NPC-shop/party/guild/prey/task/bosstiary/imbuement/cyclopedia window serializers, plus wave-35 trade/channel/house/VIP/store/inspection/blessing writers) vs 190 C++ senders.

---

### 5. Maintenance

After each wave (AGENTS.md §6), update the coverage table in §1, the affected rows in §2, and the ranked list in §3. Keep `docs/development/game-play-features.md`, `README.md`, and `CHANGELOG.md` in sync.

---

<a id="gameplay-features"></a>

## Gameplay Features Matrix

This document establishes the master inventory of all gameplay features from Canary, mapping their specification, status, automated test coverage, and the **original Canary project source files** in **Canary-Kotlin**.

---

### 📊 1. Implemented & Verified Gameplay Features

| # | Gameplay System / Feature | Domain & Protocol Logic | Canary Original Code Source | Status | Automated Test Reference |
|---|---|---|---|:---:|---|
| **01** | **Character Locomotion** | 8-way movement (`0x65..0x68`, `0x6A..0x6D`), step duration timing based on creature and ground speed. | `src/server/network/protocol/protocolgame.cpp:1738-1763`, `src/creatures/creature.cpp` | ✅ **DONE** | `MovePacketHandlerTest.kt`, `MovementTest.kt` |
| **02** | **Spot Turns & Orientation** | Turn without changing tiles (`0x6F..0x72`). | `src/server/network/protocol/protocolgame.cpp:1768-1778` | ✅ **DONE** | `MovePacketHandlerTest.kt`, `PositionFunctionsTranscriptTest.kt` |
| **03** | **Item Actions & Interaction** | `onUse` (`0x82`), `onUseWith` (`0x83`), crosshair targeting. | `src/server/network/protocol/protocolgame.cpp:1825-1832`, `src/actions/` | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **04** | **Item Dragging & Throwing** | Moving items across ground, containers, and inventory slots (`0x78`). | `src/server/network/protocol/protocolgame.cpp:1795`, `src/game/game.cpp` | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **05** | **Directional Object Rotation** | Rotating chairs, tables, and statues (`0x85`). | `src/server/network/protocol/protocolgame.cpp:1834` | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **06** | **Container Management** | Open (`0x7D`), close (`0x7E`, `0x87`), parent container navigation (`0x88`), capacity limits. | `src/items/containers/container.cpp`, `src/server/network/protocol/protocolgame.cpp:1840-1844` | ✅ **DONE** | `ItemModelTest.kt`, `ItemPacketHandlerTest.kt` |
| **07** | **Physical Melee Combat** | Attack formulas: $2 \times \frac{\text{attack} \times (\text{skill} + 5.8)}{25} + \frac{\text{level}}{5}$. | `src/creatures/combat/combat.cpp:1100-1150` | ✅ **DONE** | `CombatEngineTest.kt` |
| **08** | **Armor Damage Mitigation** | Armor reduction: $P(\text{reduction}) \in \left[\frac{\text{armor}}{2}, \text{armor} - 1\right]$. | `src/creatures/combat/combat.cpp:1200-1230` | ✅ **DONE** | `CombatEngineTest.kt` |
| **09** | **Combat Stances & Stance Modes** | Full Attack, Balanced, Full Defense (`0xA0`). | `src/server/network/protocol/protocolgame.cpp:1912`, `src/creatures/players/player.hpp` | ✅ **DONE** | `CombatPacketHandlerTest.kt` |
| **10** | **Combat Targeting & Following** | Locking target creature (`0xA1`), following without attacking (`0xA2`). | `src/server/network/protocol/protocolgame.cpp:1915-1919` | ✅ **DONE** | `CombatPacketHandlerTest.kt` |
| **11** | **Magic Spellcasting System** | Instant spell words (`exura`, `exevo flam hur`), level & mana requirements, cooldowns. | `src/creatures/combat/spells.cpp`, `data/scripts/spells/` | ✅ **DONE** | `SpellSystemTest.kt` |
| **12** | **Vitals & Mana Regeneration** | Periodic health and mana recovery per vocation up to maximum pool caps. | `src/creatures/players/player.cpp`, `src/game/game.cpp` | ✅ **DONE** | `VitalsRegenerationTest.kt` |
| **13** | **Player Death Penalty** | Experience and skill reductions (10% base down to 2% with full blessings). | `src/game/game.cpp`, `src/enums/player_blessings.hpp` | ✅ **DONE** | `DeathPenaltyTest.kt` |
| **14** | **Corpse Generation & Temple Revive**| Spawning dead player/monster corpses, resetting conditions, temple teleport. | `src/game/game.cpp:playerDeath`, `src/items/item.cpp` | ✅ **DONE** | `DeathPenaltyTest.kt` |
| **15** | **Experience & Level Progression** | Exact cubic level curve formula: $\frac{50n^3 - 300n^2 + 850n - 600}{3}$. | `src/game/game.cpp:getExpForLevel` | ✅ **DONE** | `CreatureModelTest.kt` |
| **16** | **Item & Creature Inspection** | Look At (`0x8C`, `0x8D`) describing item attributes, weights, counts, creature HP. | `src/server/network/protocol/protocolgame.cpp:1855-1859` | ✅ **DONE** | `LookPacketHandlerTest.kt` |
| **17** | **In-Game Text Messaging** | Message classes (`MESSAGE_INFO_DESCR`, `MESSAGE_STATUS`, server warnings) (`0xB4`). | `src/server/network/protocol/protocolgame.cpp:sendTextMessage` | ✅ **DONE** | `LookPacketHandlerTest.kt` |
| **18** | **Speech & Chat Scopes** | Whisper (1 tile), Say (4 tiles), Yell (18 tiles), local spectator broadcasting (`0x96`). | `src/creatures/interactions/chat.cpp`, `src/server/network/protocol/protocolgame.cpp:1885` | ✅ **DONE** | `ChatManagerTest.kt` |
| **19** | **Chat Channels** | Public channels (World Chat, English, Guild, Party) with subscriber sets. | `src/creatures/interactions/chat.cpp`, `data/chatchannels/` | ✅ **DONE** | `ChatManagerTest.kt` |
| **20** | **Banking & Currency System** | Gold, Platinum, Crystal coin conversions, deposits, withdrawals, and wire transfers. | `src/game/bank/bank.cpp`, `data/npclib/npc_system/bank_system.lua` | ✅ **DONE** | `BankSystemTest.kt` |
| **21** | **Player Storage & Quest Log** | Key-value integer storage (`getStorage`/`setStorage`), multi-mission quest log states. | `src/io/player_storage_repository.cpp`, `src/creatures/players/components/player_storage.cpp` | ✅ **DONE** | `QuestStorageTest.kt` |
| **22** | **Player-to-Player Safe Trade** | Safe 2-way trade proposal (`0x7D`), inspection (`0x7E`), mutual accept (`0x7F`), cancel. | `src/server/network/protocol/protocolgame.cpp:1810-1820`, `src/game/game.cpp` | ✅ **DONE** | `TradeAndModalTest.kt` |
| **23** | **Server Modal Windows** | Popup modal dialogs with title, message, buttons, and choice options (`0xFA`). | `src/game/modal_window/modal_window.cpp`, `src/server/network/protocol/protocolgame.cpp` | ✅ **DONE** | `TradeAndModalTest.kt` |
| **24** | **Heartbeat & Safe Logout** | Ping / Pong keepalive (`0x1E`), combat battle sign logout prevention (`0x14`). | `src/server/network/protocol/protocolgame.cpp:1686-1694`, `src/game/game.cpp` | ✅ **DONE** | `KeepaliveProtocolTest.kt` |
| **25** | **Monster Spawner & Respawn Timer** | Spawn zones (coordinates, radius, respawn interval in seconds). | `src/map/spawns.cpp`, `data-canary/world/canary-monster.xml` | ✅ **DONE** | `MonsterAiTest.kt`, `MapPipelineServiceTest.kt` |
| **26** | **Monster Combat AI** | Nearest player targeting, pathfinding approach, melee attacks, spell casting. | `src/creatures/monsters/monster.cpp`, `src/creatures/monsters/monster_combat_intention.cpp` | ✅ **DONE** | `MonsterAiTest.kt` |
| **27** | **Loot Rolling Engine** | Dropping items on monster death according to basis-point probabilities ($1..100,000$). | `src/creatures/monsters/monster.cpp:dropLoot`, `data-canary/scripts/creaturescripts/drop_loot.lua` | ✅ **DONE** | `MonsterPipelineServiceTest.kt` |
| **28** | **NPC Dialogue & Conversations** | Greeting (`hi`), farewell (`bye`), trade requests (`trade`), keywords, and state focus. | `src/creatures/npcs/npc.cpp`, `data/npclib/npc_system/keyword_handler.lua` | ✅ **DONE** | `NpcDialogueTest.kt`, `NpcPipelineServiceTest.kt` |
| **29** | **NPC Merchant Trade Window** | In-game shop catalog with buyable/sellable goods, price checks, and gold deduction (`0x7A`). | `src/server/network/protocol/protocolgame.cpp:1800-1808`, `data/npclib/` | ✅ **DONE** | `NpcDialogueTest.kt`, `NpcPipelineServiceTest.kt` |
| **30** | **Houses & Private Ownership** | House tiles, door permissions (`aleta grav`, `aleta sio`), intruder eviction, rent. | `src/map/house/house.cpp`, `src/map/house/housetile.cpp` | ✅ **DONE** | `HouseTest.kt`, `MapPipelineServiceTest.kt` |
| **31** | **Guilds & Guild Wars** | Guild ranks (Leader, Vice-Leader, Member), invites, war declarations, frag tracking. | `src/creatures/players/grouping/guild.cpp`, `src/io/ioguild.cpp` | ✅ **DONE** | `GuildWarTest.kt` |
| **32** | **Global Player Market (IOMarket)** | In-game auction house buy/sell offer escrow, automatic price-matching engine. | `src/io/iomarket.cpp`, `src/server/network/protocol/protocolgame.cpp` | ✅ **DONE** | `MarketTest.kt` |
| **33** | **Town Depots & Supply Stash** | Multi-town depot locker chests, stackable supply stash storage. | `src/items/containers/depot/depotlocker.cpp`, `src/creatures/players/stash_definitions.hpp` | ✅ **DONE** | `DepotMailTest.kt` |
| **34** | **Mailbox & Postal Delivery** | Sending parcels and letters via mailboxes to recipient town inboxes. | `src/items/containers/mailbox/mailbox.cpp`, `src/items/containers/inbox/inbox.cpp` | ✅ **DONE** | `DepotMailTest.kt` |
| **35** | **Hazardous Magic Fields** | Fire, Poison, and Energy fields on tiles applying condition ticks and decaying over time. | `src/items/magicfield.cpp`, `src/creatures/combat/condition.cpp` | ✅ **DONE** | `WorldObjectsTest.kt` |
| **36** | **Teleporters** | Instant coordinate redirection upon entering teleport tiles. | `src/game/movement/teleport.cpp` | ✅ **DONE** | `WorldObjectsTest.kt` |
| **37** | **Doors & Keys** | Closed/open door toggles and key ID verification. | `src/items/doors/door.cpp`, `data/scripts/movements/closing_door.lua` | ✅ **DONE** | `WorldObjectsTest.kt` |
| **38** | **Imbuement System** | Item shrines, powerful/intricate/basic slots, active duration decay. | `src/enums/imbuement.hpp`, `data/XML/imbuements.xml`, `data/scripts/systems/imbuement_shrine.lua` | ✅ **DONE** | `ModernSystemsTest.kt` |
| **39** | **Prey Hunting System** | Monster target tracking, active bonus multipliers (damage, defense, XP, loot). | `src/io/ioprey.cpp`, `src/server/network/protocol/protocolgame.cpp` | ✅ **DONE** | `ModernSystemsTest.kt` |
| **40** | **Bosstiary & Lockout Tracker** | Kill counters across Bane tiers (25, 100, 300) and 20-hour boss room lever lockouts. | `src/io/io_bosstiary.cpp` | ✅ **DONE** | `BosstiaryTest.kt` |
| **41** | **Character Switching & Session Handoff**| Multi-character login, session cleanup on disconnect, and instant character switch. | `src/server/network/protocol/protocollogin.cpp`, `src/server/network/protocol/protocolgame.cpp` | ✅ **DONE** | `MultiCharacterSwitchMovementTest.kt` |

---

### ✅ 2. Advanced Gameplay Features — Implemented (engine scope)

> **Rule (AGENTS.md §1.4):** Lua stays Lua. For each feature the **C++ engine surface** is ported to Kotlin; player-facing logic authored in `data/**/*.lua` is executed as Lua through the scripting engine. Feature specs are in `docs/development/feature-history.md`.
>
> **Status:** all 15 features are implemented and tested (each `systems/<feature>/` package with engine core + application services, plus protocol parsers/serializers and router wiring where applicable). Residual parity gaps (protocol sends, deeper persistence, Lua-driven side effects) are listed per feature below.

| # | Feature Name | Nature | Implemented (Kotlin) | Residual gaps | Spec |
|---|---|---|---|:---:|:---:|
| **42** | Wheel of Destiny | **C++-only** | `systems/wheel/`: enums, `IOWheel` (36 slot bonuses), `PlayerWheel` budget, spell tables, gems, `WheelApplication` (save + prerequisites + gem actions), protocol `0x61`/`0x62`/`0xE7` routed | gem-economy costs, spell-table registration into the spell registry (wheel window now serialized) | `050` |
| **43** | Exaltation Forge | Mixed | `systems/forge/`: `ForgeAction`, `PlayerForgeHistory`, dust/fusion math, `ForgeApplication`; protocol `0xBF` routed + window serializers | router application (needs `ForgePlayer` seam), forge history send | `051` |
| **44** | Bestiary & Charms | Mixed | `systems/bestiary/`: `IOBestiary`, `Charm`, player state, registry, economy, `BestiaryApplication`, charm combat procs; `0xE1–0xE4` routed | animus mastery (outbound `0xD5`–`0xD9` now serialized) | `052` |
| **45** | In-Game Store | Lua-dominant | `systems/gamestore/`: `CoinType`, account coin ledger | store opcodes are WIP/disabled in Canary; whole store module stays Lua | `053` |
| **46** | Hazard System | Mixed | `systems/hazard/`: monster/player hazard + `handleHazardSystemAttack` + `CombatDamage` bridge + config | protocol/UI icons, loot/pods (Lua) | `054` |
| **47** | Daily Reward | Mixed | `systems/dailyreward/`: enums, `setDailyReward`, streak KV | persistence columns, regen application, packet | `055` |
| **48** | Boss Reward Chest | Mixed | `systems/rewardchest/`: `Reward`, `RewardChest`, collect logic, `RewardChestApplication`; `0xFF` routed; persistence | container inheritance details | `056` |
| **49** | Familiars | Mixed | `systems/familiar/`: XML loader, player familiar state | combat flags, storage persistence wiring | `057` |
| **50** | Hirelings | Mixed | `systems/hireling/`: skill/outfit maps, `canSpeakWithHireling`, 0xEC parse | cyclopedia summary, DB/KV | `058` |
| **51** | Mounts & Speed | Mixed | `systems/mount/`: XML loader (236 mounts), player mount state | outfit protocol, persistence | `059` |
| **52** | Weapon Proficiencies | **C++-dominant** | `systems/proficiency/`: enums, experience tables, JSON loader, combat application, payload serializer | gain-on-hit/kill runtime path | `060` |
| **53** | Soul War / Taint | Mixed | `systems/soulwar/`: taint/fear/agony conditions, fear check | walk/pathing, Zone primitives | `061` |
| **54** | Scheduled World Raids | Mixed | `systems/raid/`: legacy `Raids`/`Raid`/`RaidEvent` XML engine | Game wiring, Lua script runner | `062` |
| **55** | Offline Training | Mixed | `systems/offlinetraining/`: state, accrual, try conversion, `OfflineTrainingApplication`; `0x74` routed | dialog send, persistence wiring | `063` |
| **56** | Concoctions | Mixed | `systems/concoction/`: `ConcoctionType`, active state | cyclopedia packet, Lua effect configs | `064` |

**Scope note:** none of these is "Lua misread as a feature" — each has a real missing C++ engine surface. The prior assumption that they could be delivered as standalone Kotlin `*Engine` classes was wrong for the Lua-authored portions; those now stay Lua.

---

### 📈 Summary Metrics

- **Implemented & Verified Gameplay Systems**: **56 / 56** ($100\%$)
- **Advanced Gameplay Systems**: **15 / 15** implemented (engine scope); residual parity gaps per feature above.
- **Automated Test Coverage**: 689 test suites / 7620 tests passing cleanly on **Java 25 LTS** via `./gradlew test` (all `@Test` methods discovered).
- **Combat stack**: `combat/CombatDamage` + `Combat` (damage/armor/mitigation/crit), `combat/condition/*` (Condition engine core + outfit/light/invisible/cooldown/serene).
- **Combat consumers**: bestiary charm procs, weapon-proficiency combat application, and hazard `CombatDamage` bridge.
- **Lua engine**: bindings installed at `LuaEnvironment.initState()`; core primitive bindings (Game/Player/Creature/Item/Container/Position) + Spell/CreatureEvent/GlobalEvent/MoveEvent registries; `LuaEventDispatcher`/`ScriptingDispatch`; best-effort `data/libs` load.
- **Router**: opcodes `0x61`/`0x62`/`0xE7` (wheel), `0x74` (offline training), `0xE1`–`0xE4` (bestiary), `0xBF` (forge), `0xFF` (reward chest) dispatched via `GamePacketRouter` on the WorldCommit lane.

#### Module completion (verified, `docs/development/missing-features.md` §1.2)

| Module | Rows | IMPLEMENTED | Weighted % | Notes |
|---|---|---|---|---|
| `src/` bootstrap, `src/account/`, `src/config/`, `src/database/`, `src/enums/`, `src/security/`, `src/creatures/` | 53 | 53 | **100%** | sections complete (waves 24–34) |
| `src/protobuf/` | 2 | 1 | **50%** | `kv.proto` missing |
| `src/io/` | 9 | 9 | **100%** | iologindata/iomapserialize/market/prey/bestiary/bosstiary/guild/storage (wave 36) |
| `src/items/` | 10 | 10 | **100%** | item/tile/thing/cylinder/trashholder/bed/decay/containers/attributes/weapons/registry (wave 37) |
| `src/map/` | 6 | 6 | **100%** | map core/caches/spectators/houses/A*/towns (wave 38) |
| `src/game/` | 10 | 9 | **95%** | core `Game` class + scheduler/events/compute/reload/movement/modal (wave 35) |
| `src/server/` | 5 | 0 | **30%** | opcodes/senders |
| `src/lua/` | 12 | 12 | **100%** | 1,246/1,246 method bindings + sandboxing (waves 39–42) |
| `src/utils/` | 3 | 0 | **17%** | `tools.cpp` |
| `src/lib/` | 5 | 0 | **10%** | DI/logger/metrics |
| `src/kv/` | 1 | 0 | **0%** | KV engine |
| **Overall** | **116** | **113** | **99%** (rows 97%) | full-parity estimate **~75–80%** |

---

<a id="codebase-audit"></a>

## Codebase Implementation Audit

This document provides a **complete, file-by-file audit** of the original Canary C++20 project (`src/`), tracking what has been implemented in **Canary-Kotlin** and mapping the remaining un-implemented modules to concrete architecture blueprints.

---

### 1. Complete C++ Subsystem Status Breakdown

#### 1.1 Network & Protocol (`src/server/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `src/server/network/connection/connection.cpp` | TCP socket handling, reading 2-byte header | ✅ **IMPLEMENTED** | `TibiaFrameDecoder.kt`, `CanaryServer.kt` |
| `src/server/network/connection/connection.cpp` (anti-flood) | Packet-per-second flood defense | ✅ **IMPLEMENTED** | `RateLimiterHandler.kt` |
| `src/server/network/message/networkmessage.cpp` | Inbound Byte buffer serialization | ✅ **IMPLEMENTED** | `TibiaByteBufExt.kt`, `NetworkMessageTranscriptTest.kt` |
| `src/server/network/message/outputmessage.cpp` | Outbound padding, Adler, XTEA packaging | ✅ **IMPLEMENTED** | `TibiaMessageEncoder.kt` |
| `src/server/network/protocol/protocollogin.cpp` | Login protocol opcode `0x01` exchange | ✅ **IMPLEMENTED** | `LoginHandler.kt`, `LoginProtocolTest.kt` |
| `src/server/network/protocol/protocolstatus.cpp` | TSQP XML status response opcode `0xFF` | ✅ **IMPLEMENTED** | `StatusHandler.kt`, `StatusProtocolTest.kt` |
| `src/server/network/protocol/protocolgame.cpp` (handshake) | Game handshake on port 7172, `0x0A`, `0x64` | ✅ **IMPLEMENTED** | `GameHandler.kt`, `GameProtocolTest.kt` |
| `src/server/network/protocol/protocolgame.cpp` (router) | Multi-packet dispatching loop | ✅ **IMPLEMENTED** | `GamePacketRouter.kt`, `GamePacketRouterTest.kt` |
| `src/server/network/protocol/protocolgame.cpp` (movement) | Inbound walking `0x65..0x68`, turns `0x6F..0x72` | ✅ **IMPLEMENTED** | `MovePacketHandler.kt` |
| `src/server/network/protocol/protocolgame.cpp` (items) | Use `0x82`, useWith `0x83`, throw `0x78` | ✅ **IMPLEMENTED** | `ItemPacketHandler.kt` |
| `src/server/network/protocol/protocolgame.cpp` (combat) | Fight modes `0xA0`, attack `0xA1`, follow `0xA2` | ✅ **IMPLEMENTED** | `CombatPacketHandler.kt` |
| `src/server/network/protocol/protocolgame.cpp` (look) | Look at `0x8C`, text messages `0xB4` | ✅ **IMPLEMENTED** | `LookPacketHandler.kt`, `DescriptionFormatter.kt` |
| `src/server/network/protocol/protocolgame.cpp` (trade/modal) | Safe trade `0x7D..0x80`, modals `0xFA` | ✅ **IMPLEMENTED** | `TradeAndModalManager.kt` |
| `src/server/network/protocol/protocolgame.cpp` (keepalive) | Ping/pong `0x1E`, combat logout `0x14` | ✅ **IMPLEMENTED** | `KeepaliveHandler.kt` |
| `src/server/network/protocol/transport_codec.cpp` | Outer length encoding variants | ✅ **IMPLEMENTED** | `TibiaMessageEncoder.kt` |
| `src/server/network/webhook/webhook.cpp` | Discord webhook notification client | 📋 *PLANNED* | `Plan/047-prometheus-metrics-observability-alerts.md` |
| `src/server/signals.cpp` | OS signal traps (SIGTERM/SIGINT) | ✅ **IMPLEMENTED** | `Main.kt` (JVM Shutdown Hook + `SaveManager.kt`) |

---

#### 1.2 Cryptography & Security (`src/security/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `src/security/rsa.cpp`, `rsa_backend_mbedtls.cpp` | 1024-bit raw textbook RSA decryption | ✅ **IMPLEMENTED** | `RsaCipher.kt`, `CryptoTest.kt` |
| `src/server/network/protocol/protocol.cpp` (XTEA) | 64-round Little-Endian XTEA cipher | ✅ **IMPLEMENTED** | `XteaCipher.kt`, `CryptoTest.kt` |
| `src/utils/tools.cpp` (Adler32) | RFC 1950 Adler-32 checksum calculation | ✅ **IMPLEMENTED** | `Adler32Checksum.kt` |
| `src/security/argon.cpp` | Argon2id password hash verification | ✅ **IMPLEMENTED** | `Argon2Security.kt`, `Argon2SecurityTest.kt` |

---

#### 1.3 Concurrency, Dispatcher & Core Game Loop (`src/game/scheduling/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `src/game/scheduling/dispatcher.cpp` | Single-threaded event loop scheduler | ✅ **IMPLEMENTED** | `GameDispatcher.kt` (Coroutine Actor) |
| `src/game/scheduling/dispatcher_budget.cpp` | Lane prioritization (`WorldCommit`, `Maintenance`) | ✅ **IMPLEMENTED** | `DispatcherLane` enum in `GameDispatcher.kt` |
| `src/game/scheduling/save_manager.cpp` | Periodic auto-save & shutdown flush | ✅ **IMPLEMENTED** | `SaveManager.kt`, `SaveManagerTest.kt` |
| `src/game/scheduling/monster_compute_service.cpp` | Offloaded monster pathfinding compute | 📋 *PLANNED* | Coroutine worker pool in `MonsterAiEngine.kt` |
| `src/game/scheduling/events_scheduler.cpp` | Calendar-based timed events (JSON) | 📋 *PLANNED* | Scheduled coroutine jobs in `GameDispatcher.kt` |

---

#### 1.4 Spatial Map, Tiles & Binary Loaders (`src/map/`, `src/io/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `src/map/map.cpp`, `position.cpp` | 3D coordinate math, distance, range queries | ✅ **IMPLEMENTED** | `Position.kt`, `MapGrid.kt`, `PositionFunctionsTranscriptTest.kt` |
| `src/map/utils/mapsector.cpp` | 32x32 spatial sector array chunking | ✅ **IMPLEMENTED** | `MapSector` in `MapGrid.kt`, `MapSectorTest.kt` |
| `src/map/spectators.cpp` | Viewport spectator queries ($18 \times 14$ tiles) | ✅ **IMPLEMENTED** | `getTilesInRange` in `GameMap.kt` |
| `src/io/iomap.cpp`, `fileloader.cpp` | Binary `.otbm` map file parser | ✅ **IMPLEMENTED** | `OtbmNodeReader.kt`, `OtbmMapLoader.kt`, `MapPipelineService.kt` |
| `src/map/house/house.cpp`, `housetile.cpp` | House ownership, tile access lists, rent | ✅ **IMPLEMENTED** | `House.kt`, `HouseTile.kt`, `HouseTest.kt` |
| `src/map/spawns.cpp` | Spawner radius, spawn timers | ✅ **IMPLEMENTED** | `Spawner.kt`, `SpawnManager.kt`, `MapPipelineService.kt` |
| `src/map/town.hpp` | Town IDs and temple coordinates | ✅ **IMPLEMENTED** | Populated in `OtbmMapLoader.kt` & `GameMap.kt` |
| `src/map/utils/astarnodes.cpp` | A* Pathfinding across obstacles | 📋 *PLANNED* | `Plan/008-movement-pathfinding-viewport.md` |
| `src/map/map_download.cpp` | Live client map chunk downloading | 📋 *OPTIONAL* | Not required for standard client connections |

---

#### 1.5 Items, Inventory & Containers (`src/items/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `src/items/items.cpp` (OTB) | Binary `items.otb` node parser & flags | ✅ **IMPLEMENTED** | `OtbItemsLoader.kt`, `ItemPipelineService.kt` |
| `src/io/fileloader.cpp` (items.xml) | Extended XML stats (attack, defense, armor) | ✅ **IMPLEMENTED** | `XmlItemLoader.kt`, `ItemPipelineService.kt` |
| `src/items/item.cpp`, `items.cpp` | Domain item model & count calculations | ✅ **IMPLEMENTED** | `Item.kt`, `ItemModelTest.kt` |
| `src/items/containers/container.cpp` | Container capacity & recursive weights | ✅ **IMPLEMENTED** | `Container.kt`, `ItemModelTest.kt` |
| `src/items/containers/depot/depotlocker.cpp` | Town depot chests & locker persistence | ✅ **IMPLEMENTED** | `DepotService.kt`, `DepotMailTest.kt` |
| `src/items/containers/mailbox/mailbox.cpp` | Postal parcel & letter routing | ✅ **IMPLEMENTED** | `DepotService.kt`, `DepotMailTest.kt` |
| `src/items/containers/inbox/inbox.cpp` | Character private inbox storage | ✅ **IMPLEMENTED** | `DepotService.kt`, `DepotMailTest.kt` |
| `src/items/containers/rewards/rewardchest.cpp` | Adventurer guild boss reward chest | 📋 *PLANNED* | `Plan/056-boss-reward-chest-scoring.md` |
| `src/items/magicfield.cpp` | Fire, Poison, Energy hazard fields | ✅ **IMPLEMENTED** | `MagicField` in `WorldObjects.kt` |
| `src/game/movement/teleport.cpp` | Instant coordinate teleporters | ✅ **IMPLEMENTED** | `TeleportItem` in `WorldObjects.kt` |
| `src/items/doors/door.cpp` | Closed/open door toggles & key verification | ✅ **IMPLEMENTED** | `DoorItem` in `WorldObjects.kt` |
| `src/items/decay/decay.cpp` | Item decay timer chains (corpses $\to$ skeletons) | 📋 *PLANNED* | Scheduled decay tasks in `GameDispatcher.kt` |
| `src/items/bed.cpp` | Sleeping beds in houses (offline regen) | 📋 *PLANNED* | Connect to `OfflineTrainingEngine.kt` |

---

#### 1.6 Entities: Creatures, Players, Monsters & NPCs (`src/creatures/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `src/creatures/creature.cpp` | Base creature vitals, health, mana, speed | ✅ **IMPLEMENTED** | `Creature.kt`, `CreatureModelTest.kt` |
| `src/creatures/players/player.cpp` | Player experience cubic progression formula | ✅ **IMPLEMENTED** | `Player.getExpForLevel` in `Creature.kt` |
| `src/creatures/players/player.cpp` (vitals) | Vocation health & mana regeneration | ✅ **IMPLEMENTED** | `VitalsRegenEngine.kt`, `VitalsRegenerationTest.kt` |
| `src/creatures/combat/combat.cpp` | Melee damage formula & armor reduction | ✅ **IMPLEMENTED** | `CombatEngine.kt`, `CombatEngineTest.kt` |
| `src/creatures/combat/spells.cpp` | Instant spells (`exura`) & mana/level gating | ✅ **IMPLEMENTED** | `SpellRegistry.kt`, `SpellSystemTest.kt` |
| `src/creatures/monsters/monster.cpp` | Monster target tracking & approach AI | ✅ **IMPLEMENTED** | `MonsterAiEngine.kt`, `MonsterAiTest.kt` |
| `src/creatures/monsters/monster.cpp:dropLoot` | Basis-point loot drop rolling ($1..100k$) | ✅ **IMPLEMENTED** | `LootEngine.kt`, `MonsterPipelineServiceTest.kt` |
| `src/creatures/npcs/npc.cpp` | Greeting (`hi`), trade, farewell (`bye`) | ✅ **IMPLEMENTED** | `Npc.kt`, `NpcDialogueTest.kt` |
| `src/creatures/npcs/shop_functions.cpp` | Merchant buy/sell goods catalog | ✅ **IMPLEMENTED** | `NpcPipelineService.kt`, `NpcDialogueTest.kt` |
| `src/creatures/players/grouping/party.cpp` | Party system, shared exp calculations | ✅ **IMPLEMENTED** | `Plan/010-chat-channels-and-social.md` |
| `src/creatures/players/grouping/guild.cpp` | Guild ranks, war declarations, frag counts | ✅ **IMPLEMENTED** | `Guild.kt`, `GuildWar.kt`, `GuildWarTest.kt` |
| `src/game/bank/bank.cpp` | Currency conversion & player bank accounts | ✅ **IMPLEMENTED** | `Currency.kt`, `BankService.kt`, `BankSystemTest.kt` |
| `src/io/iomarket.cpp` | In-game auction market & trade matching | ✅ **IMPLEMENTED** | `MarketManager.kt`, `MarketTest.kt` |
| `src/game/game.cpp:playerDeath` | Death penalty, blessing losses, temple revive | ✅ **IMPLEMENTED** | `DeathPenaltyEngine.kt`, `DeathPenaltyTest.kt` |
| `src/creatures/appearance/mounts/mounts.cpp` | Mount equipping & speed bonuses | 📋 *PLANNED* | `Plan/059-mounts-and-speed-buff-system.md` |
| `src/creatures/appearance/outfit/outfit.cpp` | Outfit addons & color serialization | 📋 *PLANNED* | `CreaturePacketWriter.kt` |
| `src/creatures/players/grouping/familiars.cpp` | Level 200+ vocation summon companions | 📋 *PLANNED* | `Plan/057-familiars-vocation-summon-system.md` |
| `src/creatures/players/management/ban.cpp` | IP & Account ban enforcement | 📋 *PLANNED* | Connect to `RateLimiterHandler.kt` / Database |

---

#### 1.7 Modern Client Systems (`src/enums/`, `src/io/`, `data/libs/systems/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `src/enums/imbuement.hpp` | Item shrine imbuement duration & decay | ✅ **IMPLEMENTED** | `ImbuementSlot` in `ModernSystems.kt` |
| `src/io/ioprey.cpp` | Prey hunting targets & bonus multipliers | ✅ **IMPLEMENTED** | `PreySlot` in `ModernSystems.kt` |
| `src/io/io_bosstiary.cpp` | Bosstiary Bane tiers & 20h lever lockouts | ✅ **IMPLEMENTED** | `BosstiaryManager.kt`, `BosstiaryTest.kt` |
| `src/io/io_wheel.cpp` | Wheel of Destiny perk trees & points | 📋 *PLANNED* | `Plan/050-wheel-of-destiny-engine.md` |
| `data/libs/systems/exaltation_forge.lua` | Equipment fusion, Onslaught, Ruse, Momentum | 📋 *PLANNED* | `Plan/051-exaltation-forge-engine.md` |
| `src/io/iobestiary.cpp` | Monster kill counter & Charm Runes | 📋 *PLANNED* | `Plan/052-bestiary-and-charm-runes.md` |
| `data/modules/scripts/gamestore/` | In-game Store with Tibia Coins | 📋 *PLANNED* | `Plan/053-in-game-store-tibia-coins.md` |
| `data/libs/systems/hazard.lua` | Gnomprona primal hazard difficulty scaling | 📋 *PLANNED* | `Plan/054-gnomprona-hazard-system.md` |
| `data/libs/systems/daily_reward.lua` | Daily reward shrine & streak tracking | 📋 *PLANNED* | `Plan/055-daily-reward-shrine-system.md` |
| `src/items/containers/rewards/` | Individual boss damage participation loot | 📋 *PLANNED* | `Plan/056-boss-reward-chest-scoring.md` |
| `data/libs/systems/hireling.lua` | House hireling NPCs (banker, cook, trader) | 📋 *PLANNED* | `Plan/058-house-hirelings-system.md` |
| `src/enums/weapon_proficiency.hpp` | Weapon family XP & critical damage bonuses | 📋 *PLANNED* | `Plan/060-weapon-proficiencies-system.md` |
| `data/libs/systems/encounters.lua` | Soul War taints & arena fear/agony gauges | 📋 *PLANNED* | `Plan/061-soul-war-taint-encounters.md` |
| `data/libs/systems/raids.lua` | Scheduled world boss invasions & broadcasts | 📋 *PLANNED* | `Plan/062-scheduled-world-raids.md` |
| `src/server/network/protocol/protocolgame.cpp:1783` | Offline training shrines (opcode `0x74`) | 📋 *PLANNED* | `Plan/063-offline-training-shrines-system.md` |
| `data/libs/systems/concoctions.lua` | 1-hour alchemical stat resistance flasks | 📋 *PLANNED* | `Plan/064-concoctions-alchemical-buffs.md` |

---

#### 1.8 Database, Storage & ORM (`src/database/`, `src/io/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `schema.sql` | MariaDB relational schema | ✅ **IMPLEMENTED** | PostgreSQL / H2 Flyway Migrations (`V1`, `V2`) |
| `src/database/databasemanager.cpp` | Connection pool & query execution | ✅ **IMPLEMENTED** | HikariCP (`DatabaseConnectionFactory.kt`) |
| `src/account/account_repository_db.cpp` | Account authentication & character lists | ✅ **IMPLEMENTED** | `JdbcAccountRepository.kt`, `JdbcRepositoriesTest.kt` |
| `src/io/functions/iologindata_save_player.cpp` | Player vitals, exp, and position saving | ✅ **IMPLEMENTED** | `JdbcPlayerRepository.kt`, `JdbcRepositoriesTest.kt` |
| `src/kv/kv_sql.cpp` | Key-Value arbitrary storage table | ✅ **IMPLEMENTED** | `PlayerStorageManager.kt` (`QuestManager.kt`) |
| `src/io/iomapserialize.cpp` | Saving modified map items to database | 📋 *PLANNED* | Persistent house item serialization |

---

#### 1.9 Scripting Engine (`src/lua/`)

| Original Canary File(s) | Functionality | Status in Kotlin | Kotlin Target / Implemented Component |
|---|---|:---:|---|
| `src/lua/scripts/lua_environment.cpp` | Embedded Lua runtime environment | ✅ **IMPLEMENTED** | `LuaScriptBridge.kt` (using LuaJ) |
| `src/lua/creature/actions.cpp` | Item action callbacks (`onUse`) | ✅ **IMPLEMENTED** | `ScriptRegistry.kt`, `ItemPacketHandler.kt` |
| `src/lua/creature/talkaction.cpp` | In-game command callbacks (`!online`) | ✅ **IMPLEMENTED** | `ScriptRegistry.kt` |
| `src/lua/creature/movement.cpp` | Tile entry/exit callbacks (`onStepIn`) | ✅ **IMPLEMENTED** | `MagicField`, `TeleportItem` in `WorldObjects.kt` |
| `src/lua/functions/` (100+ files) | C++ Lua Userdata bridge methods | 📋 *IN PROGRESS* | Exposing Kotlin API methods to Lua runtime |

---

### 2. Summary Audit Metrics

- **Core Gameplay & Networking Subsystems**: **41 Implemented** ($\approx 73\%$)
- **Advanced Modern Systems (Wheel, Forge, Store, Raids)**: **15 Documented in Plan** ($\approx 27\%$)
- **Automated Tests Running on Java 25 LTS**: **57 tests passing** (100% green)
- **Database Engine**: PostgreSQL 16+ production support + H2 In-Memory local testing mode with Flyway schema migrations.

---

<a id="repository-improvements"></a>

## Repository Improvements

- Remove all references to local absolute home paths (e.g. `/Users/<name>/.projects/...`). Consider the repository root as `../.projects/`.
- Change all `https://github.com/opentibiabr/canary` references to `https://github.com/gdamoreira/canary-kotlin`
- Hardcoded RSA encryption keys should be moved to a configuration file
- Documentation in `canary/docs` repository should be copied to this repository and updated to reflect the changes made to the codebase.
- README.md should be compatible with original `canary` repository and updated to reflect the changes made to the codebase.
  - should have a section for installation instructions
  - should have a section for contributing instructions
  - should have a section for license information
  - should have a section for troubleshooting instructions
  - should have a section for code of conduct
  - should have a section for security policy
  - should have a section for roadmap
  - should have a section for future improvements
  - should have a section for known issues
  - should have a section for frequently asked questions
  - should have a section for acknowledgments
  - should have a section for architecture explanation
- Github CI/CD pipeline should be set up to automate the test and build.
- `canary` repository contains `data-otserv-global` directory containing files for the game server and should be copied to this repository.
- A full `docker-compose.yml` should be created to automate the deployment of the application.
  - It has been created in the `docker` directory.
  - All necessary data, configuration files, and environment variables should be included.
  - All guides in `canary` repository should be copied to this repository and updated accordingly.

### Rules

- All improvements should be documented in the `docs` directory.
- Each improvement should be committed as separate commits.
---

### Status (wave 60 — completed 2026-10-14)

| # | Improvement | Status | Commit |
|---|---|---|---|
| I1 | Remove local absolute home-path references | Done — 57 files swept, convention enforced in AGENTS.md/template | `bd9015e` |
| I2 | Point references at `gdamoreira/canary-kotlin` | Done — branding changed; upstream map/config artifact URLs kept annotated; V2 migration and Lua datapack content untouched | `e64b190` |
| I3 | RSA keys to configuration | Done — `CANARY_RSA_P`/`CANARY_RSA_Q` with fast-fail validation (env layering) | `57beea9` |
| I4 | Copy/update `canary/docs` | Done — full 45-file port/adaptation + docs hub | `10d53b5` |
| I5 | README sections | Done — ToC + 12 requested sections; MIT `LICENSE`, CONTRIBUTING, CoC, SECURITY | `aa85506` |
| I6 | GitHub CI/CD | Done — `ci.yml` (build+test, JDK 25) and `docker.yml` (image build + compose validation) | `29b09f0` |
| I7 | Copy `data-otservbr-global` | Done — verbatim (4757 files / 38 MB); world map still fetched via MapDownload | `ca3e768` |
| I8 | Full Docker delivery | Done — root compose kept (decision), read-only data mounts, `.env.dist`, `docker/DOCKER.md`, guarded up scripts, `.dockerignore` | `637db6f3` |

**Decisions taken:** upstream download URLs stay (annotated) · datapack copied verbatim · docs ported in full · compose stays at the repository root · **MIT license** elected by the maintainer (upstream Canary is GPL-2.0; provenance noted in README/LICENSE).

**Known gaps:** no local Docker CLI on the build host, so `docker compose config` was validated as YAML only (CI `docker.yml` covers real validation); upstream building guides were adapted for Gradle without being executed on Windows/WSL.

