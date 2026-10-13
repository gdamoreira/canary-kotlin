# Canary C++ → Kotlin Port: Missing-Features Tracker

This document tracks **C++ code that is not yet transcribed to the Kotlin port**, so remaining work is visible and measurable. It complements `docs/game-play-features.md` (the 56-feature gameplay matrix).

- **Reference C++ repo:** `/Users/damoreira/.projects/canary` (`src/`)
- **Kotlin port:** `canary-kotlin/src/main/kotlin/org/opentibia/canary/`
- **Status legend:** `DONE` = faithful, tested Kotlin counterpart; `PARTIAL` = engine core exists, significant behavior missing; `MISSING` = no meaningful counterpart.

> Updated after every wave (AGENTS.md §6). Numbers are approximate and derived from file/line counts and method/registration counts.

---

## 1. Coverage Summary

| Dimension | Coverage | Notes |
|---|---|---|
| Tracked gameplay features (56) | **100%** engine scope | `docs/game-play-features.md`; residual parity gaps per feature |
| Inbound `ProtocolGame` opcodes | **~53%** (56 / ~105) | ~49 opcodes unhandled (see §5) |
| Outbound `ProtocolGame` senders | **~48%** (~87 / ~180) | self-appear, map description, window serializers, core stats/skills/inventory/container/creature/text senders, prey/task/bosstiary/imbuement/cyclopedia |
| Lua bindings (`src/lua/functions/*`) | **~18%** (~235 / 1,310) | player (306), monster-type (101), game (59), item-type (57) mostly missing |
| `Player` methods (`player.cpp`) | **~9%** (~70 / ~765) | inventory/skills/attributes/vocation/premium/conditions + component accessors |
| Monster engine (`monster.cpp`, 4k lines) | **~15%** | A* + spawn engines + targeting/loot ported |
| Map engine (`map.cpp`) | **~35%** | OTBM load + sectors + tiles + A*; no sight/canWalkTo/houses save |
| Persistence (`iologindata*`, `iomapserialize`) | **~30%** | vitals/level/pos + feature state (V3/V4) + inventory (V5) + depot/spells (V6); no VIP/houses |
| **Estimated full-parity completion** | **~40–45%** | weighted across the above; the 56 tracked features are engine-complete but the full Canary surface is far larger |

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
| `configmanager.*` | PARTIAL | ~370 config keys vs 8; reload, Lua OTC lists, typed getters |
| `config_enums.hpp` | PARTIAL | most keys absent from `FeatureFlags` |

### `src/creatures/`
| C++ | Status | Missing |
|---|---|---|
| `creature.cpp` (2,239 lines) | PARTIAL | conditions, outfit/light/speed, walk/step, onThink/onAttacked/onTarget/onDeath, skull/party shields, master/summon, spectators, icons |
| `combat/combat.cpp` | PARTIAL | full `getCombatDamage` matrix, callbacks, target validation, areas |
| `combat/condition.cpp` (2,955) | PARTIAL | drunk/muted/pacified/rooted, full attribute params, Player suppression, hex/taint |
| `combat/spells.cpp` (1,654) | PARTIAL | runes, groups/cooldowns, areas/effects, learn/forget, Lua callbacks |
| `appearance/outfit/outfit.cpp` | MISSING | outfit registry/XML, addons, colors |
| `appearance/mounts/mounts.cpp` | PARTIAL | outfit protocol, persistence, tame runtime |
| `appearance/attached_effects.cpp` | MISSING | auras/wings/shader registry |
| `interactions/chat.cpp` | PARTIAL | channel registry/invites, NPC/private channels, muted checks |
| `monsters/monster.cpp` (4,014) | PARTIAL | 150+ methods: targets, spells, dropLoot, death/corpse, defense, walk-back, despawn, forge/hazard/charm |
| `monsters/monster_pathfinding.cpp` | MISSING | A* follow path, stuck detection |
| `monsters/monster_combat_intention.cpp` | MISSING | combat intention scheduling |
| `monsters/monster_relevance.cpp` | MISSING | relevance capture |
| `monsters/monster_targeting.cpp` | MISSING | target search |
| `monsters/monsters.cpp` | PARTIAL | full MonsterType schema, reload, loot engine |
| `monsters/spawns/spawn_monster.cpp` | PARTIAL | spawn blocks/XML, intervals, respawn/despawn |
| `npcs/npc.cpp` (1,282) | PARTIAL | dialogue state machine, keywords/focus, walk-to, shop runtime |
| `npcs/npcs.cpp`, `spawns/spawn_npc.cpp` | MISSING | NPC registry + spawn engine |
| `players/player.cpp` (13,456, ~765 methods) | PARTIAL | conditions, spells/cooldowns, PvP/skull, party/guild, VIP, blessings, stash, containers, wheel/forge/prey/task-hunting/cyclopedia, harmony/serene, ban/waitlist/livestream |
| `players/components/player_storage.cpp` | PARTIAL | delta/dirty/ingest lifecycle, reserved ranges |
| `players/components/player_achievement/title/badge/vip/cyclopedia/attached_effects` | MISSING | entire components |
| `players/components/pvp/*` | MISSING | expert PvP, unjustified points |
| `players/components/weapon_proficiency.cpp` | PARTIAL | gain-on-hit/kill runtime; perks applied in combat |
| `players/components/wheel/*` | PARTIAL | spell-table registration into spell registry |
| `players/grouping/party.cpp`, `team_finder.hpp` | MISSING | entire party system + team finder |
| `players/grouping/guild.cpp` | PARTIAL | ranks persistence, invites, wars, MOTD |
| `players/imbuements/imbuements.cpp` | PARTIAL | apply/clear/scroll, item stats, shrine window |
| `players/livestream`, `management/ban`, `management/waitlist` | MISSING | entire systems |
| `players/storages/storages.cpp` (stash) | MISSING | stash add/count/withdraw |
| `players/vocations/vocation.cpp` | PARTIAL | req skill/mana formulas, promotion |

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

## 3. Highest-Impact Gaps (ranked)

1. **`Player` domain model** — ~14 Kotlin methods vs ~765 C++ (`player.cpp`).
2. **No `Game` class** — `game.cpp` (13.1k lines, ~352 methods) has no counterpart.
3. **Inbound protocol opcodes** — ~70 unhandled (trade `0x7D–0x80`, follow `0xA2`, party `0xA3–0xA8`, useItemEx `0x83`, containers `0x75/0x87/0x88/0x95`, outfit `0xD3/D4`, imbuements `0xB2/0xD5–0xD7`, prey `0xEB`, task hunting `0xBA`, bosstiary `0xAE–0xB0`, VIP `0xDC–0xDF`, depot `0x28/0x29/0x92–0x95`, modal `0xF9`, quest `0xF0/F1`, quick loot `0x8F–0x91`, …).
4. **Outbound serialization** — ~180 `sendX` methods missing (stats, skills, inventory, containers, creatures, trade, market, shop, houses, VIP, channels, cyclopedia).
5. **Monster AI + pathfinding** — no A*, targeting, relevance, combat-intention; `monster.cpp` (4k lines) largely missing.
6. **Spawn engines** — monster/NPC XML spawns, blocks, respawn timers.
7. **Lua bindings** — ~150 vs 1,310 registrations.
8. **Full player persistence** — `iologindata*` covers only vitals + feature state.
9. **`iomapserialize.cpp`** — house items/tiles wholly missing.
10. **Map engine** — `mapcache`, navigation snapshots, `canWalkTo`/sight/path, house/spawn/zone loading.
11. **Player components** — VIP, titles, badges, achievements, cyclopedia, attached effects, PvP.
12. **Party system + team finder** — absent.
13. **Imbuements engine** — only slot decay.
14. **Prey / Task Hunting runtime** — data classes only.
15. **Ban / waitlist / livestream** — absent.
16. **Conditions completeness** — drunk/muted/pacified/rooted, full params.
17. **Spell engine completeness** — runes, groups/cooldowns, areas, learn/forget.
18. **Zones, events scheduler, game reload, Lua recvbyte modules, monster compute service** — missing.
19. **Items fundamentals** — `cylinder`, `bed`, `trashholder`, decay engine, full `Tile`/`Container`.
20. **NPC runtime** — dialogue state machine, keywords/focus, walk-to, shop runtime.
21. **KV engine** — replaced only by ad-hoc maps.
22. **Protobuf appearances + attached effects** — unported.
23. **Database async layer** — `DatabaseTasks`, transactions/retry/backup.
24. **Utils** — `tools.cpp` (2,326 lines) vs 28 Kotlin.
25. **Server edge infrastructure** — webhook, protocol profiles, session hints, transport variants.

---

## 4. Protocol Opcode Coverage

**Handled inbound (24):** `0x14`, `0x1E`, `0x61`, `0x62`, `0x65–0x68`, `0x6A–0x6D`, `0x6F–0x72`, `0x74`, `0x78`, `0x7A–0x7C`, `0x82`, `0x8C`, `0x8D`, `0x96`, `0x9C` (parse-only), `0xA0`, `0xA1`, `0xBF`, `0xE1–0xE4`, `0xE7`, `0xF4–0xF8`, `0xFF`.

**Unhandled (significant):** see §3 item 3.

**Outbound handled:** `0x0A` self-appear, `0x64` map description, login/disconnect, wheel/forge/bestiary/market/NPC-shop window serializers.

---

## 5. Maintenance

After each wave (AGENTS.md §6), update the coverage table in §1, the affected rows in §2, and the ranked list in §3. Keep `docs/game-play-features.md`, `README.md`, and `CHANGELOG.md` in sync.
