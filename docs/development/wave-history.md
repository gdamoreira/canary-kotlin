# Canary Kotlin Wave History (20–60)

> Aggregated from the former `docs/development/done/` wave plan documents. Each
> wave entry keeps its original tasks, file ownership, completion notes and
> Canary C++ references; headings are demoted one level and an index added.
> New wave documents are created directly in `docs/development/` (see
> `AGENTS.md` §6).

## Index

- [Wave 20 — Party, VIP, Inbound Opcodes, Inventory Persistence](#wave-20)
- [Wave 21 — Player Components, Imbuements, Prey/Task Hunting, More Opcodes](#wave-21)
- [Wave 22 — Monster Runtime, Lua Monster Bindings, More Senders, Deeper Persistence](#wave-22)
- [Wave 23 — Lua Item/Tile Bindings, Zone Engine, Spell Engine, Server Edge](#wave-23)
- [Wave 24 — `src/` Bootstrap (CanaryServer, main, appearances, DB tasks)](#wave-24)
- [Wave 25 — `src/account/` Section](#wave-25)
- [Wave 26 — `src/config/` Section](#wave-26)
- [Wave 27 — `src/creatures/` Part 1 (Creature core, Outfits, Attached Effects, Chat)](#wave-27)
- [Wave 28 — `src/creatures/` Part 2 (Conditions, Monster Runtime, MonsterType)](#wave-28)
- [Wave 29 — `src/creatures/` Part 3 (NPC runtime, PvP, Management, Stash/Vocations/Imbuements)](#wave-29)
- [Wave 30 — `src/creatures/` Part 4 (Player runtime 1, Storage, Forge History, Combat)](#wave-30)
- [Wave 31 — `src/creatures/` Part 5 (Player runtime 2, Spells/Mounts, Team Finder/Guild, Components)](#wave-31)
- [Wave 32 — `src/creatures/` Part 6 (Player runtime 3 — feature hooks)](#wave-32)
- [Wave 33 — `src/database/` Section](#wave-33)
- [Wave 34 — `src/enums/` Section](#wave-34)
- [Wave 35 — `src/game/` + Protocol Opcode Completion](#wave-35)
- [Wave 36 — `src/io/` Section](#wave-36)
- [Wave 37 — `src/items/` Section](#wave-37)
- [Wave 38 — `src/map/` Section](#wave-38)
- [Wave 39 — `src/lua/` Part 1 (core bindings)](#wave-39)
- [Wave 40 — `src/lua/` Part 2](#wave-40)
- [Wave 41 — Small Sections Sweep + Lua Remainder + `game.cpp`](#wave-41)
- [Wave 42 — Close PARTIAL Rows + Outbound Sender Campaign](#wave-42)
- [Wave 43 — Wiring, Application, Senders, Depth](#wave-43)
- [Wave 44 — Sender Remainder, Application, Walk/Death, Monster Async](#wave-44)
- [Wave 45 — Host Wiring & Final Depth](#wave-45)
- [Wave 46 — Combat Pipeline, Plumbing, Remaining Depth](#wave-46)
- [Wave 47 — Game Remainder 2, Creature Hooks, Composition](#wave-47)
- [Wave 48 — Game Remainder 3, Player Depth 5, Composition](#wave-48)
- [Wave 49 — Final Game Members, Player Depth 6, Login Items](#wave-49)
- [Wave 50 — Final 7 Game Members, Player Depth 7, Nested Inventory](#wave-50)
- [Wave 51 — Depth Closure (Player, Host Bindings, Persistence, Barrier Lanes)](#wave-51)
- [Wave 52 — Composition One-Liners, Persistence Guard, Dispatcher Depth](#wave-52)
- [Wave 53 — Composition Root, Persistence, Dispatcher Polish](#wave-53)
- [Wave 54 — Seam Binding & Composition Details](#wave-54)
- [Wave 55 — Live Transport & Login Integration](#wave-55)
- [Wave 56 — Registry Injection, Spawn Path, Persistence Sub-Loads](#wave-56)
- [Wave 57 — Final Depth Polish](#wave-57)
- [Wave 58 — Combat Spell Actor, Host/Lua Unification, Known-Creature Set, Codec Fidelity](#wave-58)
- [Wave 59 — Event Dispatch, Spell Registry, Condition Save, Visibility](#wave-59)
- [Wave 60 — Repository Improvements (`docs/development/implementation-status.md`)](#wave-60)

---

<a id="wave-20"></a>

## Wave 20 — Party, VIP, Inbound Opcodes, Inventory Persistence

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after).

### Goal

Advance the highest-impact gaps from `docs/development/implementation-status.md`: the party and VIP player components, a batch of remaining inbound protocol opcodes, and full player inventory persistence.

### Tasks

#### Task 1 — Party system (`feat/party-system`)
- **Canary sources:** `src/creatures/players/grouping/party.cpp`, `party.hpp`.
- **Deliverable:** `creatures/grouping/Party.kt` extended from the identity-only stub to a faithful party: leader/members, invite/join/leave/revoke, shared-experience basics, party shields flag, `getPartyMembers`. Constructor-injected; no singleton.
- **Tests:** invite/join/leave/revoke, leader transfer on leave, member count, shared-exp split.
- **Constraints:** only `creatures/grouping` (+ additive `Creature.kt`); no Lua.

#### Task 2 — VIP system (`feat/vip-system`)
- **Canary sources:** `src/creatures/players/components/player_vip.cpp`, `player_vip.hpp`.
- **Deliverable:** `creatures/players/vip/PlayerVip.kt` (VIP entries/groups, add/remove/edit, group management) + a VIP protocol serializer if straightforward.
- **Tests:** add/remove entries, group assignment, icon/status.
- **Constraints:** only `creatures/players/vip` (+ `network/protocol` additive); no Lua.

#### Task 3 — Inbound opcode batch (`feat/inbound-opcodes-batch`)
- **Canary sources:** `src/server/network/protocol/protocolgame.cpp` — `parseTrade` (`0x7D`–`0x80`), `parseFollow` (`0xA2`), `parseContainer*` (`0x75`/`0x87`/`0x88`/`0x95`), `parseOutfit`/mount (`0xD3`/`0xD4`), `parseUseItemEx` (`0x83`).
- **Deliverable:** decoders for these opcodes into DTOs, routed through `GamePacketRouter` on the WorldCommit lane (parse + confinement dispatch where no engine action exists yet).
- **Tests:** `Unpooled`/`EmbeddedChannel` decode + router recognition.
- **Constraints:** `network/protocol` only; no Lua.

#### Task 4 — Inventory persistence (`feat/persistence-inventory`)
- **Canary sources:** `src/io/iologindata_load_player.cpp` / `iologindata_save_player.cpp` — inventory/depot item serialization; `src/items/functions/item/attribute.cpp`.
- **Deliverable:** a Flyway `V5__player_items.sql` (item table with attributes) + a JDBC repository that serializes/deserializes a player's inventory items (id, count, attributes, slot), with the item-attribute blob mirroring Canary's serialization.
- **Tests:** H2 round-trip of an inventory with stackable + attributed items.
- **Constraints:** `database` + `persistence` + `src/main/resources/db/migration`; no Lua.

### Verification

- `./gradlew clean test` green, zero Kotlin warnings.
- Each task's tests transcribed from the cited Canary behavior.

### Post-wave (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Party system, VIP component, 11 inbound opcodes, and inventory persistence (Flyway V5) implemented and merged; full suite green. See `docs/development/implementation-status.md` for updated coverage.

---

<a id="wave-21"></a>

## Wave 21 — Player Components, Imbuements, Prey/Task Hunting, More Opcodes

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).

### Goal

Continue closing the highest-impact gaps in `docs/development/implementation-status.md`: missing player components, the imbuement engine, the prey/task-hunting runtime, and a second batch of inbound opcodes.

### Tasks

#### Task 1 — Player components: titles, badges, achievements (`feat/player-components`)
- **Canary sources:** `src/creatures/players/components/player_title.cpp`, `player_badge.cpp`, `player_achievement.cpp`.
- **Deliverable:** `creatures/players/components/` Kotlin components: `PlayerTitles` (title id/KV unlock, current title), `PlayerBadges`, `PlayerAchievements` (points, unlock, progress) — self-contained, injected KV/repository seam.
- **Tests:** unlock/select title, badge grant, achievement points/progress.
- **Constraints:** `creatures/players/components` (+ additive); no Lua.

#### Task 2 — Imbuement engine (`feat/imbuements-engine`)
- **Canary sources:** `src/creatures/players/imbuements/imbuements.cpp/.hpp`, `src/enums/imbuement.hpp`.
- **Deliverable:** `systems/imbuement/ImbuementEngine.kt`: apply/clear/scroll imbuements to an item (slot tiers, duration decay, stat application), plus the imbuement definition registry from `data/XML/imbuements.xml`.
- **Tests:** apply basic/intricate/powerful, duration decay, clear, stat bonuses.
- **Constraints:** `systems/imbuement` (+ additive items); no Lua.

#### Task 3 — Prey + Task Hunting runtime (`feat/prey-task-runtime`)
- **Canary sources:** `src/io/ioprey.cpp`, `src/creatures/players/…` prey/task-hunting, `src/game/game.cpp` prey handlers.
- **Deliverable:** `systems/prey/PreyEngine.kt` (slot state, reroll/select, bonus application, time-left) and `systems/taskhunting/TaskHuntingEngine.kt` (task list, points, kill progress) faithful to Canary.
- **Tests:** prey select/reroll/bonus, task progress/points.
- **Constraints:** `systems/prey`, `systems/taskhunting` (+ additive); no Lua.

#### Task 4 — Inbound opcode batch 2 (`feat/inbound-opcodes-batch-2`)
- **Canary sources:** `src/server/network/protocol/protocolgame.cpp` — party `0xA3–0xA8`, imbuements `0xB2`/`0xD5–0xD7`, depot/stash `0x28/0x29/0x92–0x95`, quick loot `0x8F–0x91`, modal answer `0xF9`, quest log `0xF0/F1`.
- **Deliverable:** decoders into DTOs + `GamePacketRouter` wiring (parse + WorldCommit confinement).
- **Tests:** `Unpooled`/`EmbeddedChannel` decode + router recognition.
- **Constraints:** `network/protocol` only; no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

### Post-wave (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Player components (titles/badges/achievements), imbuement engine, prey + task-hunting engines, and inbound opcode batch 2 implemented and merged; full suite green. See `docs/development/implementation-status.md` for updated coverage.

---

<a id="wave-22"></a>

## Wave 22 — Monster Runtime, Lua Monster Bindings, More Senders, Deeper Persistence

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).

### Goal

Close more high-impact gaps: the monster targeting/loot runtime, monster Lua bindings, remaining outbound senders, and depot/inbox/spells persistence.

### Tasks

#### Task 1 — Monster targeting + loot runtime (`feat/monster-targeting-loot`)
- **Canary sources:** `src/creatures/monsters/monster_targeting.cpp`, `src/creatures/monsters/monster.cpp` (`searchTarget`, `dropLoot`, `death`/corpse, `canSee`, `isTarget`), `src/creatures/monsters/monster_relevance.cpp`.
- **Deliverable:** `creatures/monsters/MonsterTargeting.kt` (target search: nearest/friend/attackable, distance + visibility) and `creatures/monsters/MonsterLoot.kt` (`dropLoot` over the monster's loot items with Canary's probability/`lootItems` semantics).
- **Tests:** target selection rules; loot rolls (basis points, unique rolls, corpse owner).
- **Constraints:** `creatures/monsters` (+ additive); no Lua.

#### Task 2 — Monster Lua bindings (`feat/lua-monster-bindings`)
- **Canary sources:** `src/lua/functions/creatures/monster/monster_functions.cpp` (51), `monster_type_functions.cpp` (101), `loot_functions.cpp` (18), `monster_spell_functions.cpp` (23).
- **Deliverable:** a substantial subset of monster/monster-type/loot Lua methods registered with exact Canary names, delegating to seams.
- **Tests:** real Lua chunks via `LuaEnvironment` + install.
- **Constraints:** `lua/bindings` (+ additive); no Lua gameplay ported.

#### Task 3 — Outbound senders batch 3 (`feat/outbound-senders-3`)
- **Canary sources:** `src/server/network/protocol/protocolgame.cpp` — prey (`0xE7–0xE9`), task hunting (`0xBB`), bosstiary (`0x61/0x62/0xAE–0xB0`), cyclopedia (`0xE5/0xDB`), imbuement window, VIP (`0xD2/0xD4` if not already), quest log window.
- **Deliverable:** serializers for a solid subset in exact Canary order/endianness.
- **Tests:** `Unpooled` byte-exact.
- **Constraints:** `network/protocol` only; no Lua.

#### Task 4 — Depot/inbox/spells persistence (`feat/persistence-depth-2`)
- **Canary sources:** `src/io/iologindata_*_player.cpp` (depot/inbox item serialization, player spells), `src/items/containers/depot/*`, `inbox/*`.
- **Deliverable:** Flyway `V6__player_depot_spells.sql` + a JDBC repository persisting depot/inbox items (reusing the V5 item attribute codec) and the player's learned spells.
- **Tests:** H2 round-trip (Flyway V1–V6).
- **Constraints:** `database`, `persistence`, `src/main/resources/db/migration`; no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

### Post-wave (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Monster targeting + loot engine, monster Lua bindings, outbound senders batch 3, and depot/inbox/spells persistence (Flyway V6) implemented and merged; full suite green.

---

<a id="wave-23"></a>

## Wave 23 — Lua Item/Tile Bindings, Zone Engine, Spell Engine, Server Edge

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).

### Goal

Close further high-impact gaps: item/tile/container Lua bindings, the Zone engine, the spell engine (runes/groups/cooldowns/learn-forget), and server edge infrastructure (webhook, protocol profile).

### Tasks

#### Task 1 — Lua item/tile/container/position bindings (`feat/lua-item-tile-bindings`)
- **Canary sources:** `src/lua/functions/items/item_type_functions.cpp` (57), `container_functions.cpp` (17), `src/lua/functions/map/tile_functions.cpp` (31), `position_functions.cpp` (14), `house_functions.cpp` (26), `town_functions.cpp` (4).
- **Deliverable:** `ItemTypeFunctions`, `TileFunctions`, extended `ContainerFunctions`/`PositionFunctions` registering exact Canary names on the right metatables, delegating to seams.
- **Tests:** real Lua chunks via `LuaEnvironment` + install.
- **Constraints:** `lua/bindings` (+ additive `WorldLuaOperations`); no Lua gameplay.

#### Task 2 — Zone engine (`feat/zone-engine`)
- **Canary sources:** `src/game/zones/zone.cpp/.hpp`.
- **Deliverable:** `game/zones/Zone.kt` + `Zones.kt`: zone definitions (area/flags), `addZone`/`removeZone`, `isInZone(pos, zoneId)`, `refreshZones`, and the zone flag checks used by combat/movement.
- **Tests:** zone membership, flag checks, refresh.
- **Constraints:** `game/zones` (+ additive); no Lua.

#### Task 3 — Spell engine completion (`feat/spell-engine`)
- **Canary sources:** `src/creatures/combat/spells.cpp/.hpp` (`Spell`/`RuneSpell`: rune conjure/use, spell groups/cooldowns, areas, learn/forget, spell book).
- **Deliverable:** extend `spells/SpellRegistry.kt` with rune conjuring, spell groups + per-group cooldowns, area/effect metadata, and learn/forget/spell-book state.
- **Tests:** rune conjure, group cooldown, learn/forget, area data.
- **Constraints:** `spells` (+ additive); no Lua.

#### Task 4 — Server edge infrastructure (`feat/server-edge`)
- **Canary sources:** `src/server/network/webhook/webhook.cpp`, `src/server/network/protocol/protocol_profile.cpp`, `protocol_session_hint.cpp`.
- **Deliverable:** `network/webhook/Webhook.kt` (Discord webhook POST payload builder + sender seam), `network/protocol/ProtocolProfile.kt` (profile detection/feature flags), `SessionHint.kt`.
- **Tests:** webhook payload shape, profile feature flags, session-hint handling.
- **Constraints:** `network` (+ additive); no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

### Post-wave (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Lua item/tile/container/position bindings, Zone engine, spell engine (runes/groups/cooldowns/spell book), and server edge (webhook/protocol profile/session hint) implemented and merged; full suite green.

---

<a id="wave-24"></a>

## Wave 24 — `src/` Bootstrap (CanaryServer, main, appearances, DB tasks)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** First section of `docs/development/implementation-status.md` §2 (`src/` bootstrap). Goal: turn the `PARTIAL` rows in that section into `IMPLEMENTED`.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `canary_server.cpp/.hpp` | PARTIAL | IMPLEMENTED — appearance/protobuf loading, service bootstrap, DB tasks wiring, shutdown drain |
| `main.cpp` | PARTIAL | IMPLEMENTED — CLI/arg handling, signal-init sequence, crash handler |
| `src/protobuf/appearances.proto` | MISSING | IMPLEMENTED — appearance loading/tables |

### Tasks

#### Task 1 — Service bootstrap + CLI/signals (`feat/server-bootstrap`)
- **Canary sources:** `src/canary_server.cpp/.hpp`, `src/main.cpp`, `src/signals.cpp`, `src/server/server.cpp`.
- **Deliverable:** `Main.kt`/`CanaryServer.kt`: CLI argument parsing (config path, data dir, db options), SIGINT/SIGTERM signal-init sequence, uncaught-exception crash handler, full service bootstrap (config → DB → map → scripting → appearance load → listeners), and an **idempotent shutdown drain** that stops producers/consumers before releasing retained globals (per AGENTS.md Static Ownership Lifetime Safety).
- **Tests:** CLI parsing; bootstrap order; graceful shutdown drains listeners/dispatcher/DB once (idempotent).
- **Constraints:** `Main.kt`, `network/CanaryServer.kt`, `config/**`, and new files; no Lua.

#### Task 2 — Appearances protobuf loading (`feat/appearances-protobuf`)
- **Canary sources:** `src/protobuf/appearances.proto`, `src/items/items.cpp` (appearance load/merge), `src/creatures/appearance/outfit/outfit.cpp`.
- **Deliverable:** a dependency-free protobuf wire-format reader (varint/length-delimited) parsing `data/items/appearances.dat` into outfit/mount/effect tables (`Appearances.kt`) with the same look-up API Canary exposes.
- **Tests:** parse the real `data/items/appearances.dat`; assert expected object counts and spot-checked ids; malformed-input tolerance.
- **Constraints:** `io/appearances` (new package) + additive `items`; no Lua.

#### Task 3 — Database tasks (`feat/database-tasks`)
- **Canary sources:** `src/database/databasetasks.cpp/.hpp`.
- **Deliverable:** `database/DatabaseTasks.kt`: an async DB task queue (submit, sequential execution, shutdown flush) mirroring Canary's `DatabaseTasks`, with an injected executor.
- **Tests:** submit/execute ordering, shutdown flush drains pending tasks, no tasks after shutdown.
- **Constraints:** `database` only; no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

### Post-wave (AGENTS.md §6)

Update `docs/development/implementation-status.md` (flip the `src/` bootstrap + protobuf rows to `IMPLEMENTED` and refresh coverage), `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. `src/` bootstrap section implemented: service bootstrap + CLI/signals/crash handler + idempotent shutdown drain, appearances protobuf loader, and `DatabaseTasks`. The `src/` bootstrap and `appearances.proto` rows are now IMPLEMENTED in `docs/development/implementation-status.md`.

---

<a id="wave-25"></a>

## Wave 25 — `src/account/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** Second section of `docs/development/implementation-status.md` §2 (`src/account/`). Goal: turn the `PARTIAL` rows in that section into `IMPLEMENTED`.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `account.cpp/.hpp` (`Account`) | PARTIAL | IMPLEMENTED — authenticate/session/load/save, account type, house bid, premium purchase, coin ledger methods |
| `account_repository_db.cpp` (`AccountRepositoryDB`) | PARTIAL | IMPLEMENTED — account create/save, coin transactions, ban/session columns |
| `account_repository.cpp`, `account_info.hpp` | PARTIAL | IMPLEMENTED — coin-transaction history, session auth fields |

### Tasks

#### Task 1 — Account core + info (`feat/account-core`)
- **Canary sources:** `src/account/account.cpp/.hpp`, `src/account/account_info.hpp`, `src/enums/account_type.hpp`, `account_errors.hpp`.
- **Deliverable:** extend `account/Account.kt` with `authenticate`/`authenticatePassword`/`authenticateSession`, `load`/`reload`/`save`, `setAccountType`, `houseBid`, premium purchase days, and the coin ledger methods (`addCoins`/`removeCoins`/`registerCoinTransaction`); add `AccountInfo` and the account type/error enums.
- **Tests:** authentication paths, premium days, house bid, coin add/remove.
- **Constraints:** `account/**` (+ additive); no Lua.

#### Task 2 — Account repository (`feat/account-repo`)
- **Canary sources:** `src/database/account_repository_db.cpp`, `src/database/account_repository.cpp`.
- **Deliverable:** extend `database/JdbcAccountRepository.kt` with account create/save, coin ledger persistence (`coins`/`coins_tournament`/`coins_transferable` + `coins_transactions`), and ban/session columns; add Flyway `V7__account_details.sql`.
- **Tests:** H2 round-trip (create/save account, coin transaction history, session fields).
- **Constraints:** `database`, `src/main/resources/db/migration`; no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

### Post-wave (AGENTS.md §6)

Flip the `src/account/` rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. `src/account/` section implemented: Account auth/session/load/save/premium/house-bid/coins, `AccountInfo`/`AccountType`/`AccountErrors`, and the account repository (create/save, coin ledger + transactions, ban/session columns, Flyway V7). Rows flipped to IMPLEMENTED.

---

<a id="wave-26"></a>

## Wave 26 — `src/config/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** Third section of `docs/development/implementation-status.md` §2 (`src/config/`). Goal: turn the `PARTIAL` rows into `IMPLEMENTED`.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `configmanager.cpp/.hpp` (`ConfigManager`) | PARTIAL | IMPLEMENTED — full typed getters, reload, OTC feature lists |
| `config_enums.hpp` | PARTIAL | IMPLEMENTED — full config key surface with Canary defaults |

### Tasks

#### Task 1 — Config key surface (`feat/config-enums`)
- **Canary sources:** `src/config/config_enums.hpp`.
- **Deliverable:** `config/ConfigKeys.kt` — the full Canary config key surface: every `Boolean_config`/`Integer_config`/`Float_config`/`String_config` key with its exact name and default value, plus a key-type enum. New file only.
- **Tests:** spot-check defaults across each type; key-name parity with the C++.
- **Constraints:** `config` (new file) only; no Lua.

#### Task 2 — Typed ConfigManager (`feat/config-manager`)
- **Canary sources:** `src/config/configmanager.cpp/.hpp`.
- **Deliverable:** extend `config/ConfigManager.kt` with typed getters (`getBoolean`/`getNumber`/`getFloat`/`getString`), `reload`, and the Lua OTC feature lists; resolve from an injected key map + env/properties with Canary precedence.
- **Tests:** typed getter resolution/precedence; reload; OTC feature list parsing.
- **Constraints:** `config` only; no Lua gameplay.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

### Post-wave (AGENTS.md §6)

Flip the `src/config/` rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. `src/config/` section implemented: `ConfigKeys` (360 keys, exact names/types/defaults) and typed `ConfigManager` getters + `reload` + OTC feature lists. Rows flipped to IMPLEMENTED.

---

<a id="wave-27"></a>

## Wave 27 — `src/creatures/` Part 1 (Creature core, Outfits, Attached Effects, Chat)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** Fourth section of `docs/development/implementation-status.md` §2 (`src/creatures/`, part 1). Goal: turn the covered `PARTIAL`/`MISSING` rows into `IMPLEMENTED`.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `creature.cpp` (2,239 lines) | PARTIAL | IMPLEMENTED — conditions (done), outfit/light/speed, walk/step, onThink/onAttacked/onTarget/onDeath, master/summon, spectator visibility, icons |
| `appearance/outfit/outfit.cpp` | MISSING | IMPLEMENTED — outfit registry + XML, addons, colors |
| `appearance/attached_effects.cpp` | MISSING | IMPLEMENTED — auras/wings/shader/effects registry + XML |
| `interactions/chat.cpp` | PARTIAL | IMPLEMENTED — channel registry/invites/users, NPC/private channels, muted checks, yell rules |

### Tasks

#### Task 1 — Creature core (`feat/creature-core`)
- **Canary sources:** `src/creatures/creature.cpp/.hpp`.
- **Deliverable:** extend `creatures/Creature.kt` with the remaining state/methods: outfit + light + speed, `onThink`/`onAttacked`/`onTarget`/`onDeath`, master/summon linkage, spectator visibility (`canSee`), and creature icons.
- **Tests:** transcribe real Canary behavior (light/outfit state, onAttacked/onTarget hooks, master/summon, canSee).
- **Constraints:** `creatures` (+ additive); no Lua.

#### Task 2 — Outfit registry (`feat/outfit-registry`)
- **Canary sources:** `src/creatures/appearance/outfit/outfit.cpp/.hpp`.
- **Deliverable:** `creatures/appearance/outfit/Outfits.kt` — outfit registry loaded from `data/XML/outfits.xml`, addons, colors, `getOutfitByLookType`.
- **Tests:** XML load counts, lookup by look type, addon/color handling.
- **Constraints:** `creatures/appearance/outfit` (new package); no Lua.

#### Task 3 — Attached effects (`feat/attached-effects`)
- **Canary sources:** `src/creatures/appearance/attached_effects/attached_effects.cpp/.hpp`.
- **Deliverable:** `creatures/appearance/effects/AttachedEffects.kt` — auras/wings/shader/effects registry + XML loader.
- **Tests:** XML load, lookup, effect classification.
- **Constraints:** `creatures/appearance/effects` (new package); no Lua.

#### Task 4 — Chat channels (`feat/chat-channels`)
- **Canary sources:** `src/creatures/interactions/chat.cpp/.hpp`.
- **Deliverable:** extend `chat/ChatManager.kt` with the channel registry (public/private/guild/party), invites/users, muted checks, and yell rules.
- **Tests:** channel create/join/leave, invite/remove, muted checks, yell scope.
- **Constraints:** `chat` (+ additive); no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

### Post-wave (AGENTS.md §6)

Flip the covered `src/creatures/` rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. `src/creatures/` part 1: Creature core (outfit/light/speed, hooks, master/summon, canSee, icons), Outfit registry, Attached Effects registry, Chat channels. Rows flipped to IMPLEMENTED.

---

<a id="wave-28"></a>

## Wave 28 — `src/creatures/` Part 2 (Conditions, Monster Runtime, MonsterType)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/creatures/` part 2. Goal: turn the covered `PARTIAL`/`MISSING` rows into `IMPLEMENTED`.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `combat/condition.cpp` | PARTIAL | IMPLEMENTED — drunk/muted/channelmuted/pacified/rooted, full attribute params, Player suppression |
| `monsters/monster.cpp` | PARTIAL | IMPLEMENTED — spells, death/corpse, defense/reflect, walk-back, despawn |
| `monsters/monster_combat_intention.cpp` | MISSING | IMPLEMENTED — combat intention scheduling |
| `monsters/monster_relevance.cpp` | MISSING | IMPLEMENTED — relevance capture |
| `monsters/monsters.cpp` | PARTIAL | IMPLEMENTED — full MonsterType schema, reload |
| `monsters/monster_pathfinding.cpp` | MISSING | IMPLEMENTED (A* done in wave 18 — flip) |
| `monsters/monster_targeting.cpp` | MISSING | IMPLEMENTED (targeting done in wave 22 — flip) |
| `monsters/spawns/spawn_monster.cpp` | PARTIAL | IMPLEMENTED (spawn engine done in wave 18 — flip) |

### Tasks

#### Task 1 — Conditions completion (`feat/condition-complete`)
- **Canary sources:** `src/creatures/combat/condition.cpp/.hpp`.
- **Deliverable:** add the remaining condition classes (drunk, muted, channel-muted, pacified, rooted) and the full attribute param set; wire them into the factory.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `combat/condition` (+ additive); no Lua.

#### Task 2 — Monster runtime (`feat/monster-complete`)
- **Canary sources:** `src/creatures/monsters/monster.cpp/.hpp` (the remaining ~150 methods not yet ported).
- **Deliverable:** extend `creatures/MonsterAi.kt` / add `creatures/monsters/MonsterBehavior.kt` with spells, death/corpse, defense/reflect, walk-back, and despawn logic.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures` (+ additive `MonsterAi.kt`); no Lua.

#### Task 3 — Combat intention + relevance (`feat/monster-intention-relevance`)
- **Canary sources:** `src/creatures/monsters/monster_combat_intention.cpp`, `monster_relevance.cpp`.
- **Deliverable:** new files `creatures/monsters/MonsterCombatIntention.kt` and `creatures/monsters/MonsterRelevance.kt` (do NOT edit `MonsterAi.kt`).
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/monsters` (new files only); no Lua.

#### Task 4 — MonsterType schema (`feat/monstertype-schema`)
- **Canary sources:** `src/creatures/monsters/monsters.cpp/.hpp` (the `MonsterType`/`MonsterInfo` schema) and the Lua monster registration it consumes.
- **Deliverable:** `creatures/monsters/MonsterType.kt` with the full schema (attacks, defenses, elements, immunities, flags, voices, summons, races) + reload; self-contained (Lua stays Lua).
- **Tests:** transcribe the schema fields/defaults.
- **Constraints:** `creatures/monsters` (new file only); no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip the covered `src/creatures/` rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Conditions completed, monster runtime (spells/death/defense/walk/despawn), combat intention + relevance, and full MonsterType schema. Rows flipped to IMPLEMENTED.

---

<a id="wave-29"></a>

## Wave 29 — `src/creatures/` Part 3 (NPC runtime, PvP, Management, Stash/Vocations/Imbuements)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/creatures/` part 3.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `npcs/npc.cpp` (1,282) | PARTIAL | IMPLEMENTED — dialogue state machine, keywords/focus, walk-to, shop runtime |
| `npcs/npcs.cpp` | MISSING | IMPLEMENTED — NPC type registry |
| `npcs/spawns/spawn_npc.cpp` | MISSING | IMPLEMENTED (spawn engine done in wave 18 — flip) |
| `players/components/pvp/*` | MISSING | IMPLEMENTED — player PvP situations + expert PvP |
| `players/livestream/livestream.cpp` | MISSING | IMPLEMENTED |
| `players/management/ban.cpp` | MISSING | IMPLEMENTED |
| `players/management/waitlist.cpp` | MISSING | IMPLEMENTED |
| `players/storages/storages.cpp` (stash) | MISSING | IMPLEMENTED |
| `players/vocations/vocation.cpp` | PARTIAL | IMPLEMENTED — req formulas, promotion, gain caps |
| `players/imbuements/imbuements.cpp` | PARTIAL | IMPLEMENTED — scroll crafting + remaining runtime |

### Tasks

#### Task 1 — NPC runtime + registry (`feat/npc-runtime`)
- **Canary sources:** `src/creatures/npcs/npc.cpp/.hpp`, `src/creatures/npcs/npcs.cpp/.hpp`.
- **Deliverable:** extend `creatures/Npc.kt` with the dialogue state machine (keywords/focus/player interaction), walk-to/queue, shop buy/sell runtime; add `creatures/npcs/Npcs.kt` type registry.
- **Tests:** dialogue flow, keyword matching, shop buy/sell, registry lookup.
- **Constraints:** `creatures/npcs` (+ additive `Npc.kt`); no Lua.

#### Task 2 — PvP components (`feat/player-pvp`)
- **Canary sources:** `src/creatures/players/components/pvp/player_pvp.cpp/.hpp`, `expert_pvp.cpp/.hpp`.
- **Deliverable:** `creatures/players/pvp/PlayerPvp.kt` (situations, unjustified points, skull transitions) + `ExpertPvp.kt`.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/players/pvp` (new package); no Lua.

#### Task 3 — Management (ban, waitlist, livestream) (`feat/player-management`)
- **Canary sources:** `src/creatures/players/management/ban.cpp/.hpp`, `waitlist.cpp/.hpp`, `src/creatures/players/livestream/livestream.cpp/.hpp`.
- **Deliverable:** `creatures/players/management/Ban.kt`, `Waitlist.kt`, `creatures/players/livestream/Livestream.kt`.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** new packages only; no Lua.

#### Task 4 — Stash, vocations, imbuements completion (`feat/player-stash-vocations-imbuements`)
- **Canary sources:** `src/creatures/players/storages/storages.cpp/.hpp`, `src/creatures/players/vocations/vocation.cpp/.hpp`, `src/creatures/players/imbuements/imbuements.cpp/.hpp`.
- **Deliverable:** `creatures/players/storages/Stash.kt` (supply stash add/count/withdraw); complete `creatures/vocations/Vocation.kt` (req skill/mana formulas, promotion, gain caps); complete `systems/imbuement` (scroll crafting + remaining runtime).
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/players/storages`, `creatures/vocations`, `systems/imbuement`; no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip the covered rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. NPC runtime + registry, PvP components, ban/waitlist/livestream, stash/vocations/imbuements. Rows flipped to IMPLEMENTED.

---

<a id="wave-30"></a>

## Wave 30 — `src/creatures/` Part 4 (Player runtime 1, Storage, Forge History, Combat)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/creatures/` part 4.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `players/player.cpp` chunk 1 | PARTIAL | IMPLEMENTED — conditions integration, spell/cooldown integration, skull/PvP integration, async task queue |
| `players/components/player_storage.cpp` | PARTIAL | IMPLEMENTED — delta/dirty/ingest lifecycle, reserved ranges |
| `players/components/player_forge_history.cpp` | PARTIAL | IMPLEMENTED — DB load/save |
| `combat/combat.cpp` | PARTIAL | IMPLEMENTED — full `getCombatDamage` matrix, callbacks, target validation, areas |

### Tasks

#### Task 1 — Player runtime chunk 1 (`feat/player-runtime-1`)
- **Canary sources:** `src/creatures/players/player.cpp/.hpp`.
- **Deliverable:** extend `creatures/Creature.kt` (`Player`) with: condition integration (`addCondition`/`onAddCombatCondition`/suppression), learned spells + spell/group cooldowns, skull/PvP integration (PZ lock, in-fight condition), and the async task-queue basics.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures` (+ additive); no Lua.

#### Task 2 — Player storage lifecycle (`feat/player-storage`)
- **Canary sources:** `src/creatures/players/components/player_storage.cpp/.hpp`.
- **Deliverable:** `creatures/players/components/PlayerStorage.kt` with the delta/dirty/ingest lifecycle and reserved-range handling.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/players/components` (new file); no Lua.

#### Task 3 — Forge history persistence (`feat/player-forge-history`)
- **Canary sources:** `src/creatures/players/components/player_forge_history.cpp/.hpp`.
- **Deliverable:** complete `systems/forge/PlayerForgeHistory.kt` with DB load/save (reuse the repository patterns); injected repository seam.
- **Tests:** H2 round-trip.
- **Constraints:** `systems/forge`, `database`, `persistence`; no Lua.

#### Task 4 — Combat completion (`feat/combat-complete`)
- **Canary sources:** `src/creatures/combat/combat.cpp/.hpp`.
- **Deliverable:** complete `combat/Combat.kt` with the full `getCombatDamage` matrix (weapon/level formulas, absorb/increase), target validation, and combat areas; event/callback seams.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `combat` (+ additive); no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip the covered rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Player runtime chunk 1 (conditions/spells/cooldowns/PvP/async tasks), `PlayerStorage`, forge-history persistence (V8), and combat completion. Rows flipped.

---

<a id="wave-31"></a>

## Wave 31 — `src/creatures/` Part 5 (Player runtime 2, Spells/Mounts, Team Finder/Guild, Components)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/creatures/` part 5.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `players/player.cpp` chunk 2 | PARTIAL | IMPLEMENTED — blessings, container integration, harmony/serene |
| `combat/spells.cpp` | PARTIAL | IMPLEMENTED — `playerSaySpell`, `InstantSpell::canCast`, `CombatSpell` |
| `appearance/mounts/mounts.cpp` | PARTIAL | IMPLEMENTED — outfit protocol, persistence, tame runtime |
| `players/grouping/party.cpp`, `team_finder.hpp` | MISSING | IMPLEMENTED — team finder (party done) |
| `players/grouping/guild.cpp` | PARTIAL | IMPLEMENTED — ranks persistence, invites, wars, MOTD |
| `players/components/player_cyclopedia/attached_effects` | MISSING | IMPLEMENTED — cyclopedia + attached-effects components |
| `players/components/weapon_proficiency.cpp` | PARTIAL | IMPLEMENTED — gain-on-hit/kill runtime |
| `players/components/wheel/*` | PARTIAL | IMPLEMENTED — spell-table registration into the spell registry |

### Tasks

#### Task 1 — Player runtime chunk 2 (`feat/player-runtime-2`)
- **Canary sources:** `src/creatures/players/player.cpp/.hpp` — blessings, container integration, harmony/serene.
- **Deliverable:** extend `creatures/Creature.kt` (`Player`) with blessing state/`getLostPercent` interactions, container integration (`getContainer`/inventory container ops), and harmony/serene state.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures` (+ additive) only; no Lua.

#### Task 2 — Spells + mounts completion (`feat/spells-mounts-complete`)
- **Canary sources:** `src/creatures/combat/spells.cpp` (`playerSaySpell`, `InstantSpell::canCast`, `CombatSpell`), `src/creatures/appearance/mounts/mounts.cpp` (outfit protocol, persistence, tame runtime).
- **Deliverable:** complete `spells/*` and `systems/mount/*` for the remaining behavior.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `spells`, `systems/mount`; no Lua.

#### Task 3 — Team finder + guild (`feat/team-finder-guild`)
- **Canary sources:** `src/creatures/players/grouping/team_finder.hpp`, `guild.cpp/.hpp`.
- **Deliverable:** `creatures/grouping/TeamFinder.kt` and complete `social/guild/Guild.kt` (ranks persistence, invites, wars, MOTD).
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/grouping`, `social/guild`; no Lua.

#### Task 4 — Cyclopedia/attached-effects components + WP gain + wheel spell-table (`feat/player-components-2`)
- **Canary sources:** `src/creatures/players/components/player_cyclopedia.cpp`, `player_attached_effects.cpp`, `weapon_proficiency.cpp` (gain-on-hit/kill), `wheel/player_wheel.cpp` (spell-table registration).
- **Deliverable:** `creatures/players/components/PlayerCyclopedia.kt`, `PlayerAttachedEffects.kt`, the weapon-proficiency gain runtime, and the wheel spell-table registration into `spells` (standalone components; do NOT edit `Creature.kt`).
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/players/components`, `systems/proficiency`, `systems/wheel`; no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip the covered rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Player runtime chunk 2 (blessings/containers/harmony), spells+mounts completion, team finder + guild, and cyclopedia/attached-effects components + WP gain + wheel spell-table. Rows flipped.

---

<a id="wave-32"></a>

## Wave 32 — `src/creatures/` Part 6 (Player runtime 3 — feature hooks)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** Final `src/creatures/` row: `players/player.cpp`.

### Row being closed

| C++ | Current | Target |
|---|---|---|
| `players/player.cpp` (13,456, ~765 methods) | PARTIAL | IMPLEMENTED — wheel/forge/prey/task-hunting/cyclopedia hooks |

### Task

#### Task 1 — Player feature hooks (`feat/player-runtime-3`)
- **Canary sources:** `src/creatures/players/player.cpp/.hpp` — the feature hook methods: wheel (`wheel()`, `getWheel*`, gem/dedication accessors), forge (`getForge*`, dust accessors), prey (`getPrey*`, bonus application), task hunting (`getTaskHunting*`, points), cyclopedia (`getCyclopedia*`), plus the remaining `Player` accessors that delegate to `PlayerFeatures`.
- **Deliverable:** extend `creatures/Creature.kt` (`Player`) with these hooks, delegating to the already-ported `PlayerFeatures` components; keep constructors compatible.
- **Tests:** transcribe real Canary behavior (accessors delegate correctly; prey/task bonuses applied).
- **Constraints:** `creatures` (+ additive) only; no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip `player.cpp` to `IMPLEMENTED` in `docs/development/implementation-status.md` (the `src/creatures/` section is then fully IMPLEMENTED); update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Player feature hooks (wheel/forge/prey/task-hunting/cyclopedia). The entire `src/creatures/` section is now IMPLEMENTED.

---

<a id="wave-33"></a>

## Wave 33 — `src/database/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/database/` section (newly added to `docs/development/implementation-status.md`). Goal: turn the `PARTIAL` rows into `IMPLEMENTED`.

### Rows being closed

| C++ | Current | Target |
|---|---|---|
| `database.cpp/.hpp` (`Database`) | PARTIAL | IMPLEMENTED — transactions, escaping, retry, recoverable errors, query size limits |
| `databasemanager.cpp` | PARTIAL | IMPLEMENTED — pool lifecycle/reconnect, `executeQuery`/`storeQuery` parity |
| `database_definitions.hpp` | PARTIAL | IMPLEMENTED — protocol/result enums |

### Tasks

#### Task 1 — Database core (`feat/database-core`)
- **Canary sources:** `src/database/database.cpp/.hpp`.
- **Deliverable:** `database/Database.kt`: connection/statement helpers with transactions, SQL escaping, retry on recoverable errors, and query-size limits, over an injected `DataSource`.
- **Tests:** transcribe real Canary behavior (transaction commit/rollback, escaping, retry).
- **Constraints:** `database` (new file); no Lua.

#### Task 2 — Database manager (`feat/database-manager`)
- **Canary sources:** `src/database/databasemanager.cpp/.hpp`.
- **Deliverable:** extend `database/DatabaseConnectionFactory.kt` with pool lifecycle/reconnect and the `executeQuery`/`storeQuery` parity surface.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `database`; no Lua.

#### Task 3 — Database definitions (`feat/database-definitions`)
- **Canary sources:** `src/database/database_definitions.hpp`.
- **Deliverable:** `database/DatabaseDefinitions.kt` with the protocol/result enums (`DatabaseErrors_t`, `DBResult_t`, etc.) and exact values.
- **Tests:** enum value parity.
- **Constraints:** `database` (new file); no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip the `src/database/` rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. Database core (transactions/escaping/retry/size limits), manager (pool lifecycle + executeQuery/storeQuery parity), and definitions. Rows flipped.

---

<a id="wave-34"></a>

## Wave 34 — `src/enums/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/enums/` section (newly added to `docs/development/implementation-status.md`). All rows are already IMPLEMENTED except `container_type.hpp`/`object_category.hpp`.

### Row being closed

| C++ | Current | Target |
|---|---|---|
| `container_type.hpp`, `object_category.hpp` | MISSING | IMPLEMENTED — container/object category enums with exact Canary values |

### Task

#### Task 1 — Container/object categories (`feat/container-object-categories`)
- **Canary sources:** `src/enums/container_type.hpp`, `src/enums/object_category.hpp`.
- **Deliverable:** `items/ContainerType.kt` (`ContainerType_t`) and `items/ObjectCategory.kt` (`ObjectCategory_t`) with the exact enum names/values.
- **Tests:** enum value parity.
- **Constraints:** `items` (new files); no Lua.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip the `src/enums/` rows to `IMPLEMENTED` in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. `ContainerSpecial_t` + `ObjectCategory_t` ported. The `src/enums/` section is now fully IMPLEMENTED.

---

<a id="wave-35"></a>

## Wave 35 — `src/game/` + Protocol Opcode Completion

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/game/` section (10 rows) + `docs/development/implementation-status.md` §4 (65 missing inbound opcodes, 133 missing outbound senders).

### Group A — `src/game/` section (4 tasks)

#### Task A1 — Game class core (`feat/game-class-core`)
- **Canary sources:** `src/game/game.cpp/.hpp` (13,141 lines, ~352 methods).
- **Deliverable:** `game/Game.kt` core: creature placement/removal (`addCreature`/`removeCreature`/`placeCreature`), internal add/remove/move item, broadcast, world lights/sounds, auto-walk stop, feature dispatch seams.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `game` package; no Lua.

#### Task A2 — Movement & modal (`feat/game-movement-modal`)
- **Canary sources:** `src/game/movement/position.cpp`, `movement/teleport.cpp`, `modal_window/*`.
- **Deliverable:** position direction/offset math + range rectangles parity, teleport effects/destination rules, modal answer routing + per-player modal tracking.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `game.movement`/`game.modal` (new files); no Lua.

#### Task A3 — Scheduling (`feat/game-scheduling`)
- **Canary sources:** `src/game/scheduling/dispatcher*.cpp`, `events_scheduler.cpp`, `monster_compute_service.cpp`, `save_manager.cpp`.
- **Deliverable:** WDRR fairness/budgets/telemetry for the dispatcher, calendar/timed events scheduler, off-thread monster compute service, global save-all + shutdown flush.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `game.scheduling` (extend existing + new files); no Lua.

#### Task A4 — Game reload (`feat/game-reload`)
- **Canary sources:** `src/game/functions/game_reload.cpp`.
- **Deliverable:** runtime reload surface (config/monsters/NPCs/items/scripts) with idempotent, generation-checked reload semantics.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `game` (new file); no Lua.

### Group B — missing inbound opcodes (4 tasks)

Each task: port the Canary parse function from `src/server/network/protocol/protocolgame.cpp` into a decode handler + apply state where the engine exists; add **one contiguous block of `when` branches immediately before `else -> false` in `GamePacketRouter.kt`**, prefixed `// Wave 35 batch <X>` — do not touch other parts of the router.

#### Task B1 — `feat/opcodes-inbound-1`
`0x1D` playerReceivePingBack · `0x2A` parseCyclopediaMonsterTracker · `0x2B` parsePartyAnalyzerAction · `0x2C` parseLeaderFinderWindow · `0x2D` parseMemberFinderWindow · `0x2E` parseSetClientOptions · `0x32` parseExtendedOpcode · `0x38` parsePlayerTyping · `0x60` parseInventoryImbuements · `0x63` parseClientCheck · `0x69` playerStopAutoWalk · `0x76` parseCharacterTradeConfigurationAction · `0x77` parseHotkeyEquip · `0x79` parseLookInShop · `0x81` parseFriendSystemAction.

#### Task B2 — `feat/opcodes-inbound-2`
`0x84` parseUseWithCreature · `0x85` parseRotateItem · `0x86` parseConfigureShowOffSocket · `0x89` parseTextWindow · `0x8A` parseHouseWindow · `0x8B` parseWrapableItem · `0x8E` parseJoinAggression · `0x97` playerRequestChannels · `0x98` parseOpenChannel · `0x99` parseCloseChannel · `0x9A` parseOpenPrivateChannel · `0x9D` parseGetTextForReport · `0x9E` playerCloseNpcChannel · `0x9F` parseSetMonsterPodium.

#### Task B3 — `feat/opcodes-inbound-3`
`0xAA` playerCreatePrivateChannel · `0xAB` parseChannelInvite · `0xAC` parseChannelExclude · `0xAD` parseCyclopediaHouseAuction · `0xAE` parseSendBosstiary · `0xAF` parseSendBosstiarySlots · `0xB0` parseBosstiarySlot · `0xB1` parseHighscores · `0xB3` parseWeaponProficiency · `0xBA` parseTaskHuntingAction · `0xBE` playerCancelAttackAndFollow · `0xC0` parseForgeBrowseHistory · `0xC1` parseClientDetails · `0xC2` parseBossDifficultySelection.

#### Task B4 — `feat/opcodes-inbound-4`
`0xC8` parseAimAtTarget · `0xC9` parseGetTransactionDetails · `0xCA` resendLivestreamViewerContainer · `0xCB` parseBrowseField · `0xCC` parseSeekInContainer · `0xCD` parseInspectionObject · `0xCE` parseInspectPlayer · `0xCF` sendBlessingWindow · `0xD2` playerRequestOutfit · `0xDB` parseCyclopediaMapAction · `0xDC` parseAddVip · `0xDD` parseRemoveVip · `0xDE` parseEditVip · `0xDF` parseVipGroupActions · `0xE5` parseCyclopediaCharacterInfo · `0xE6` parseBugReport · `0xE8` parseOfferDescription · `0xEB` parsePreyAction · `0xEC` parseSetHirelingName · `0xED` parseSendResourceBalance · `0xEE` parseGreet · `0xF3` parseGetObjectInfo.

### Group C — missing outbound senders (2 tasks)

Each task: port Canary `ProtocolGame::send*` methods into new writer files (do not edit shared writers); tests assert exact wire bytes.

#### Task C1 — `feat/opcodes-outbound-1`
Trade, market, shop, house/auction, channels, highscores senders.

#### Task C2 — `feat/opcodes-outbound-2`
VIP, cyclopedia, forge browse/history, prey, task hunting, bosstiary, stash, store/resource balance, imbuement, inspection, blessing window, typing/friends/hotkeys senders.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; §4 opcode lists updated with the newly handled opcodes.

### Post-wave (AGENTS.md §6)

Flip the `src/game/` rows and update §1/§3/§4 in `docs/development/implementation-status.md`; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed: Group A (`src/game/` — Game core, movement/modal, scheduling, reload), Group B (all 64 remaining inbound opcodes), Group C (outbound trade/channel/house/VIP/store/inspection/blessing writers). All merged; `src/game/` section is now 9/10 IMPLEMENTED (game.cpp remainder documented).

---

<a id="wave-36"></a>

## Wave 36 — `src/io/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/io/` section (9 rows: 8 PARTIAL, 1 MISSING). Goal: close every row.

### Migration budget (avoid Flyway version collisions across agents)

Existing migrations: V1–V9. Each agent uses its assigned versions only; if no schema change is needed, add none.

| Agent | Assigned migration versions |
|---|---|
| A1 iologindata | V10 |
| A2 map serialize | V11 |
| A3 market + prey | V12 |
| A4 bestiary/bosstiary/guild | V13 |
| A5 storage repo | V14 |

### Tasks

#### Task A1 — `iologindata` depth (`feat/io-logindata`)
- **Canary:** `src/io/iologindata.cpp` (24 methods).
- **Deliverable:** extend the player load/save repository for inventory/depot/inbox/stash/spells/kills/storage/VIP/guild/outfits/rewards/prey/imbuements + auth, bank, GUID lookups; H2/PostgreSQL compatible.
- **Tests:** H2 round-trips transcribing Canary behavior.

#### Task A2 — Map serialization (`feat/io-map-serialize`)
- **Canary:** `src/io/iomapserialize.cpp` (10 methods), `src/io/iomap.cpp` nodes.
- **Deliverable:** house items/info load+save, tile serialization guards, `iomap.cpp` houses/spawns/zones nodes + extended tile attributes.
- **Tests:** H2/XML round-trips transcribing Canary behavior.

#### Task A3 — Market & prey persistence (`feat/io-market-prey`)
- **Canary:** `src/io/iomarket.cpp` (19 methods), `src/io/ioprey.cpp`.
- **Deliverable:** market DB persistence, expiration, history, statistics, escrow; prey runtime/actions/reroll persistence.
- **Tests:** H2 round-trips transcribing Canary behavior.

#### Task A4 — Bestiary, bosstiary & guild persistence (`feat/io-bestiary-bosstiary-guild`)
- **Canary:** `src/io/iobestiary.cpp`, `src/io/io_bosstiary.cpp`, `src/io/ioguild.cpp`.
- **Deliverable:** kill-counter persistence/tracker/animus mastery, boss points/slots/cooldowns, guild DB load/save/ranks/wars.
- **Tests:** H2 round-trips transcribing Canary behavior.

#### Task A5 — Typed player storage repository (`feat/io-storage-repo`)
- **Canary:** `src/io/player_storage_repository*.cpp`.
- **Deliverable:** typed storage repo (add/remove/`upsertKey`/delta/ranges) with the exact Canary key semantics.
- **Tests:** H2 round-trips transcribing Canary behavior.

### Verification

`./gradlew clean test` green, zero Kotlin warnings; Flyway migration versions do not collide.

### Post-wave (AGENTS.md §6)

Flip all `src/io/` rows to `IMPLEMENTED`; update `docs/development/implementation-status.md` §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. All 9 `src/io/` rows are now IMPLEMENTED (iologindata 24/24, iomapserialize 10/10, iomap nodes, iomarket 18/18, ioprey, iobestiary, io_bosstiary, ioguild, player_storage_repository); migrations V10–V14.

---

<a id="wave-37"></a>

## Wave 37 — `src/items/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/items/` section (10 rows: 7 PARTIAL, 3 MISSING).

### Tasks

#### Task A1 — Item core (`feat/items-item-core`)
- **Canary:** `src/items/item.cpp` (71 methods).
- **Deliverable:** decay ticks, `moveTo`, `getDescription`, weapon checks, serialization, imbuement/forge stats on `Item`/`ItemType`.
- **Tests:** transcribe real Canary behavior.

#### Task A2 — Tile & thing/cylinder/trashholder (`feat/items-tile-thing`)
- **Canary:** `src/items/tile.cpp`, `thing.cpp`, `cylinder.cpp`, `trashholder.cpp`.
- **Deliverable:** Thing/Cylinder abstraction, trashholder, tile creatures vector, flags/PZ, top/down ordering, house tile, fields.
- **Tests:** transcribe real Canary behavior.

#### Task A3 — Decay + bed (`feat/items-decay-bed`)
- **Canary:** `src/items/decay/decay.cpp`, `src/items/bed.cpp`.
- **Deliverable:** decay engine/timer pipeline (bounded intervals, stale-callback safe), bed sleeping/regen.
- **Tests:** transcribe real Canary behavior; destroyed/replaced owner coverage.

#### Task A4 — Containers + depot/inbox/mailbox (`feat/items-containers`)
- **Canary:** `src/items/containers/container.cpp`, `containers/depot/*`, `inbox/*`, `mailbox/*`.
- **Deliverable:** capacity/weight recursion, query add/remove, iterators, serialization; locker town mapping, mail routing/letters.
- **Tests:** transcribe real Canary behavior; persistence via assigned migration if needed: **V15**.

#### Task A5 — Item functions + weapons (`feat/items-functions-weapons`)
- **Canary:** `src/items/functions/item/attribute.cpp`, `item_parse.cpp`, `src/items/weapons/weapons.cpp`.
- **Deliverable:** custom attributes, full XML item parse, weapon registry/max-damage tables.
- **Tests:** transcribe real Canary behavior.

#### Task A6 — Items registry depth (`feat/items-registry`)
- **Canary:** `src/items/items.cpp`.
- **Deliverable:** appearances/protobuf merge, augment tables, loot type, reload seam.
- **Tests:** transcribe real Canary behavior.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip all `src/items/` rows to `IMPLEMENTED`; update `docs/development/implementation-status.md` §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. All 10 `src/items/` rows IMPLEMENTED. The 6 parallel branches were integrated semantically (Item.kt 6-way merge) and the full suite is green at 395 suites / 4154 tests.

---

<a id="wave-38"></a>

## Wave 38 — `src/map/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/map/` section (6 rows: 4 PARTIAL, 2 MISSING).

### File ownership (avoid cross-agent conflicts)

| Agent | Owns (may edit/create) |
|---|---|
| A1 map-core | `map/GameMap.kt`, `map/MapGrid.kt` |
| A2 map-cache | `map/cache/**` (new) |
| A3 spectators | `map/Spectators.kt` (new) |
| A4 house | `map/house/**` |
| A5 astar-town | `map/pathfinding/AStarNodes.kt`, `map/Town.kt` (new) |

Do not edit another agent's files; document cross-file needs in the report.

### Tasks

#### Task A1 — Map core (`feat/map-core`)
- **Canary:** `src/map/map.cpp` (1,429 lines).
- **Deliverable:** `canThrowObjectTo`, `canWalkTo`, `checkSightLine`, `getPathMatching`, `placeCreature`, `moveCreature`, `save` (houses/spawns/zones loading already landed in wave 36 via `IOMap`).
- **Tests:** transcribe real Canary behavior.

#### Task A2 — Map caches & navigation (`feat/map-cache`)
- **Canary:** `src/map/mapcache.cpp`, `src/map/navigation_snapshot.cpp`, `src/map/map_download.cpp`.
- **Deliverable:** map caches, navigation snapshots, map download.
- **Tests:** transcribe real Canary behavior.

#### Task A3 — Spectators (`feat/map-spectators`)
- **Canary:** `src/map/spectators.cpp`.
- **Deliverable:** spectator sets, direction filtering, the full `getSpectators` variants.
- **Tests:** transcribe real Canary behavior.

#### Task A4 — Houses (`feat/map-house`)
- **Canary:** `src/map/house/house.cpp`, `housetile.cpp`.
- **Deliverable:** rent/periods, transfers, auctions, access lists, eviction, persistence.
- **Tests:** transcribe real Canary behavior; migration only if needed: **V15**.

#### Task A5 — A* nodes & towns (`feat/map-astar-town`)
- **Canary:** `src/map/utils/astarnodes.cpp`, `src/map/town.hpp`.
- **Deliverable:** `AStarNodes` (Canary's exact node/closed-set semantics), `Town` class/temple/ID registry.
- **Tests:** transcribe real Canary behavior.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Flip all `src/map/` rows to `IMPLEMENTED`; update `docs/development/implementation-status.md` §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Completed. All 6 `src/map/` rows IMPLEMENTED (map core, caches/navigation, spectators, houses, AStarNodes, towns); full suite green at 410 suites / 4395 tests.

---

<a id="wave-39"></a>

## Wave 39 — `src/lua/` Part 1 (core bindings)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/lua/` section — 167/1,246 method registrations. This wave covers the largest files; further waves continue the section.

### Architecture

Bindings live in `lua/bindings/**` as an operations interface + `install(env, operations)` per area, wired from `lua/runtime/WorldLuaOperations.kt` (`install` block, lines ~120–135). Each binding method corresponds 1:1 to a Canary `*Functions::lua*` in `src/lua/functions/`.

### File ownership

| Agent | Owns |
|---|---|
| A1 lua-player-1 | `bindings/core/PlayerFunctions.kt` |
| A2 lua-player-2 | `bindings/core/PlayerSocialFunctions.kt` (new) + one install line in `WorldLuaOperations.kt` |
| A3 lua-game-item | `bindings/core/GameFunctions.kt`, `bindings/core/ItemFunctions.kt` |
| A4 lua-monstertype | `bindings/MonsterTypeFunctions.kt` |
| A5 lua-itemtype-tile | `bindings/core/ItemTypeFunctions.kt`, `bindings/core/TileFunctions.kt` |

### Tasks

#### A1 — Player bindings 1 (`feat/lua-player-1`)
Canary `src/lua/functions/creatures/player/player_functions.cpp`: vitals/attributes, inventory, money, conditions, skills, outfit basics.

#### A2 — Player bindings 2 (`feat/lua-player-2`)
Same C++ file: social, party/guild, storage, modal windows, UI sends, house/account, premium, deaths/kills.

#### A3 — Game + item bindings (`feat/lua-game-item`)
Canary `src/lua/functions/core/game/game_functions.cpp` (59) + `src/lua/functions/items/item_functions.cpp` (56).

#### A4 — MonsterType bindings (`feat/lua-monstertype`)
Canary `src/lua/functions/creatures/monster/monster_type_functions.cpp` (101).

#### A5 — ItemType + Tile bindings (`feat/lua-itemtype-tile`)
Canary `src/lua/functions/items/item_type_functions.cpp` (57) + `src/lua/functions/map/tile_functions.cpp` (31).

### Rules for all agents

- Port **only** methods that exist in the C++ file; exact names/arg order; `lua_pushnil` guards preserved.
- Unwired engines become documented operation-interface seams (never invented behavior).
- TDD: a LuaJ test per batch calling the binding through `LuaEnvironment` (RED first).
- Build: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test -Dorg.gradle.jvmargs=-Xmx2g -Dkotlin.daemon.jvmargs=-Xmx2g --tests "*Functions*" --console=plain` then full suite once; zero warnings.
- Commit on your branch (no merge/push). Report exactly which Canary methods are ported and which remain.

### Verification

`./gradlew clean test` green, zero Kotlin warnings.

### Post-wave (AGENTS.md §6)

Update the `src/lua/` row counts in `docs/development/implementation-status.md` (partial progress is recorded honestly); update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

### Completion

Part 1 completed: monster_type 101/101, game 59/59, item 55/55, item_type 58/58, tile 32/32, player 114/305. The `src/lua/` section continues in wave 40 (player remainder, monster/npc/weapon/spell functions, library bindings, modules).

---

<a id="wave-40"></a>

## Wave 40 — `src/lua/` Part 2

**Status:** In progress
**Process:** AGENTS.md §6. Tracking: `src/lua/` section (~420/1,246 methods after wave 39).

### File ownership

| Agent | Owns |
|---|---|
| A1 lua-player-3 | `bindings/core/PlayerFunctions.kt` |
| A2 lua-player-4 | `bindings/core/PlayerFunctionsExtra.kt` (new) |
| A3 lua-monster-npc | `bindings/MonsterFunctions.kt`, `bindings/NpcFunctions.kt` (new), `bindings/NpcTypeFunctions.kt` (new) |
| A4 lua-weapon-spell | `bindings/WeaponFunctions.kt` (new), `bindings/SpellFunctions.kt` (new) |
| A5 lua-libraries | `bindings/library/**` (new) |
| A6 lua-map-items | `bindings/map/**` (new), `bindings/items/**` (new) |
| A7 lua-creatures | `bindings/creatures/**` (new) |
| A8 lua-events | `bindings/events/**` (new) |

Each agent adds its own install line(s) in `lua/runtime/WorldLuaOperations.kt` (one contiguous block each, marked `// Wave 40 <agent>`); conflicts are resolved by union.

### Tasks

- **A1** `player_functions.cpp` batch 3 (~95): combat/targeting, spells/runes, death/health, walk/position, misc.
- **A2** `player_functions.cpp` batch 4 (~96): remaining groups not in batch 1–3.
- **A3** `monster_functions.cpp` (51), `npc_functions.cpp`, `npc_type_functions.cpp` (40), `shop_functions.cpp`, `charm_functions.cpp`, `monster_spell_functions.cpp`.
- **A4** `weapon_functions.cpp` (31), `spell_functions.cpp` (39), `variant_functions.cpp`, `condition_functions.cpp`, `combat_functions.cpp`.
- **A5** `core/libs/*` (db, kv, logger, metrics, result), `core/network/*` (network_message, webhook), `core/game/*` remaining (bank, batch_update, config, global, modal_window, zone), `lua_enums.cpp`.
- **A6** `map/*` (house, position, teleport, town), `items/*` remaining (container, imbuement, item_classification).
- **A7** `creatures/player/*` remaining (group, guild, mount, party, vocation).
- **A8** `events/*` remaining (action, creature_event, event_callback, events_scheduler, global_event, move_event, talk_action) — verify against existing ActionBindings/CreatureEventBindings/GlobalEventBindings/MoveEventBindings/TalkActionBindings and port only the missing.

### Rules

- Port only real Canary registrations; exact names/arg order; `lua_pushnil` guards.
- TDD: LuaJ tests through `LuaEnvironment` per batch (RED first).
- Build: `./gradlew test --tests "*Functions*"` then full suite once; zero warnings.
- Commit on your branch (no merge/push); report exact methods ported/remaining and gaps.

### Post-wave (§6)

Update the `src/lua/` rows and §1–§3; update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Part 2 completed: player 277/305, monster/npc/weapon/spell/combat complete, libraries complete, map/items complete, creatures complete, events complete. Remaining lua: ~28 player methods, `modules` recvbyte, some enum groups.

---

<a id="wave-41"></a>

## Wave 41 — Small Sections Sweep + Lua Remainder + `game.cpp`

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 kv-engine | `kv/**` (new), `protobuf/kv/**` (new) |
| A2 lib-core | `lib/**` (new) |
| A3 utils-core | `utils/**` |
| A4 server-edge | `server/**`, `network/webhook/**` (new) |
| A5 lua-final | `lua/bindings/core/PlayerFunctions.kt`, `lua/modules/**` (new) |
| A6 game-remainder | `game/Game.kt` |

### Tasks

- **A1** `kv.cpp`, `kv_sql.cpp`, `value_wrapper*.cpp` + `kv.proto` typed values: SQL-backed KV engine with the exact scoped-key semantics; migration only if needed: **V16**.
- **A2** `lib/di/*` (DI container/injector), `lib/logging/logger.cpp` (leveled logger + sinks), `lib/messaging/*` (message bus/command handlers), extend metrics (counters/gauges/histograms + Prometheus), `lib/thread/thread_pool.cpp` (bounded pool semantics).
- **A3** `utils/tools.cpp` (string/URI/SHA helpers, time formatting, parseNumber, wildcard), `wildcardtree.cpp`, `batch_update.cpp` (+ its Lua binding seam).
- **A4** `server.cpp`/`signals.cpp` lifecycle/stop parity + signal drain, `network/connection/connection.cpp` state machine/size limits/close reasons, `protocol_profile.cpp`, `protocol_session_hint.cpp`, `network/webhook/webhook.cpp` (Discord webhook client).
- **A5** finish `player_functions.cpp` (~28 remaining), `modules/modules.cpp` (recvbyte Lua client-packet modules), remaining `lua_enums` groups.
- **A6** `game.cpp` remainder: `playerRequestTrade`/`playerAcceptTrade`/`playerCloseTrade`/`playerLookInTrade`, shop (`playerBuyItem`/`playerSellItem`/`playerCloseShop`), party family (`playerInviteToParty`…), guild Game methods, houses/auctions, VIP, highscores, reports, game loop (`checkCreatures`, periodic save).

### Rules

- §1.1 parity (no invented methods), §4 TDD, zero warnings.
- Build: focused tests then full suite once per agent.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Flip the `kv`/`lib`/`utils`/`protobuf`/`server` rows and update `src/lua/` + `game.cpp` rows; update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: kv, lib, utils, protobuf (kv.proto), server edge, lua remainder (player 305/305, modules, enums), and the game.cpp remainder (trade/shop/party/guild/VIP/reports/highscores/checkCreatures). All sections are now IMPLEMENTED except 3 documented PARTIAL rows (game.cpp remainder, protocolgame application, luascript/script_environment).

---

<a id="wave-42"></a>

## Wave 42 — Close PARTIAL Rows + Outbound Sender Campaign

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 game-houses-auctions | `game/Game.kt` |
| A2 luascript-sandbox | `lua/runtime/**` (LuaScriptBridge, LuaEnvironment, ScriptEnvironment) |
| A3 opcode-application | `network/protocol/GamePacketRouter.kt` + handler files under `network/protocol/**` |
| A4 senders-creature-map | `network/protocol/creature/**` (new), `network/protocol/map/**` (new) |
| A5 senders-player-ui | `network/protocol/player/**` (new) |
| A6 senders-cyclopedia | `network/protocol/cyclopedia/**` (new) |

### Tasks

- **A1** `game.cpp` houses/auctions: `playerCyclopediaHouse*`, `processBankAuction`, `wrapItem`/`unwrapItem`, `setTransferPlayerHouseItems`, `transferHouseItemsToDepot`, async highscores DB, remaining misc families.
- **A2** `luascript.cpp`/`script_environment.cpp`: protected calls, class registration, sandboxing, reload parity.
- **A3** `protocolgame.cpp` opcode application: compose the existing engines into the decode-only handlers — trade (0x7D–0x80), containers (0x75/0x87/0x88/0x95), quick loot (0x8F–0x91), stash/depot (0x28/0x29/0x92–0x95), follow (0xA2), imbuements (0xB2/0xD5–0xD7), market (0xF4–0xF8), quest log (0xF0/F1), guild (0x9C), outfit (0xD3).
- **A4** remaining creature/map/tile senders (`sendCreature*`, `sendMap*`, `sendTile*`, `sendAddTileItem`/`sendUpdateTileItem`/`sendRemoveTileThing`, `sendDistanceShoot`, `sendMagicEffect`, `sendAnimatedText`).
- **A5** remaining player/UI senders (`sendStats` variants, `sendSkills`, `sendIcons`, `sendOpenContainer`, `sendCloseContainer`, `sendModalWindow`, `sendTutorial`, `sendMessagesDialog`, `sendTextWindow`, `sendHouseWindow`, `sendCreatureSay`, `sendChannelMessage`).
- **A6** cyclopedia computation frames (`addCyclopedia*`, `handleImbuementDamage`, `calculateAbsorbValues`, `getForgeSkillStat`, `getCipbia*`) + remaining forge/store senders.

### Rules

- §1.1 parity (port only real Canary `send*` methods and exact wire format), §1.2 WorldCommit confinement, §4 TDD (byte-exact where applicable), zero warnings.
- New writer files only; A3 owns the router.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Flip the 3 PARTIAL rows if closed; update §1–§4 (sender counts), `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: `luascript.cpp`/`script_environment.cpp` row flipped to IMPLEMENTED; game.cpp and protocolgame.cpp rows advanced (houses/auctions + opcode application); outbound sender campaign started (creature/map/tile/player-UI/chat/text/cyclopedia). 506 suites / 5359 tests.

---

<a id="wave-43"></a>

## Wave 43 — Wiring, Application, Senders, Depth

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 game-wiring | `network/GameHandler.kt`, `bootstrap/**`, `network/protocol/GamePacketRouter.kt` context only |
| A2 opcode-application-2 | handler files under `network/protocol/**` (except router context) |
| A3 senders-misc | `network/protocol/misc/**` (new) |
| A4 game-families | `game/Game.kt` |
| A5 player-depth | `creatures/players/**` |
| A6 monster-depth | `creatures/monsters/**` |

### Tasks

- **A1** Production wiring: `GameHandler`/`CanaryServer` own a `Game` instance and register login players; pass `game` into `GamePacketContext` so wave-42 application activates in production.
- **A2** Remaining opcode application: stash deposit/withdraw, quick-loot corpse/nearby paths, imbuement apply/clear, market browse/create/cancel/accept, outfit 0xD3 (`Player.changeOutfit` composition).
- **A3** Remaining misc senders: `sendReLoginWindow`, `sendBasicData`, `sendFYIBox`, `sendAllowBugReport`, `sendUnjustifiedPoints`, `sendOpenPvpSituations`, `sendChannelEvent`, `sendOpenPrivateChannel`, store/misc senders not yet present.
- **A4** `game.cpp` remaining families: movement/combat/targeting handlers, market Game methods, quest/forge/bosstiary/task-hunting Game handlers, remaining `player*` handlers.
- **A5** `Player` depth: port high-value remaining `Player::` methods (raw count ~50% → higher) that are not yet modelled.
- **A6** Monster engine depth: remaining `monster.cpp` behaviors (raw count parity).

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§4, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: production Game wiring, 4 more opcode groups applied, 34 misc senders (142/187 total), 41 game.cpp family methods, 68 Player methods (479/821), monster targeting/combat/walk/summon/respawn (89 name-matched). 527 suites / 5745 tests.

---

<a id="wave-44"></a>

## Wave 44 — Sender Remainder, Application, Walk/Death, Monster Async

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 senders-remainder | `network/protocol/party/**`, `network/protocol/podium/**`, `network/protocol/depot/**`, `network/protocol/container/**`, `network/protocol/login/**` (new) |
| A2 opcode-application-3 | handler files under `network/protocol/**` |
| A3 game-market-forge | `game/Game.kt` |
| A4 player-walk-death | `creatures/players/**` |
| A5 monster-async | `creatures/monsters/**` |
| A6 lua-class-migration | `lua/runtime/UserdataRegistry.kt`, `lua/bindings/**` |

### Tasks

- **A1** remaining 45 senders: cyclopedia-character (12), party (7), podium (3), depot (3), container items 0x70/0x71/0x72 (3), login/OTC (3), loot (2), team-finder (2), outfit-window (2), forge/gem/livestream/emblem/kill-tracker/updateCreature/loginChallenge (8).
- **A2** finish opcode application: stash deposit/withdraw (0x28) + retrieve (0x29), corpse/nearby loot (0x8F), imbuement apply/clear (0xD5/0xD6), market create/cancel/accept (0xF6–0xF8), parent container open (0x95).
- **A3** `game.cpp`: `playerCreateMarketOffer`/`playerCancelMarketOffer`/`playerAcceptMarketOffer`/`updatePlayerSaleItems`, `loadItemsPrice`, `playerForgeFuseItems`, wheel handlers, `playerNpcGreet`, `playerAnswerModalWindow`, `playerSetMonsterPodium`/`playerRotatePodium`.
- **A4** `Player::death` + walk queue (`startAutoWalk`/`stopWalk`/`addListWalkDir`/`onWalk`/`goToFollowCreature`/`canWalkthrough`) + scheduler variants (`setNextActionTask`/`setNextActionPushTask`/`setNextPotionActionTask`).
- **A5** monster async pipelines: compute/intention generations, post-think scheduler, movement-AI refresh, spectator/visibility bookkeeping (replace seams with real implementations).
- **A6** adopt `LuaClassRegistry` in the userdata/binding layer; port remaining `SOUND_EFFECT_TYPE_*` ambient/music enum names.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§4, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: all 187 senders, full opcode application, game.cpp market/forge/wheel/podium, Player::death + walk queue, monster async pipelines, Lua class registry migration. 554 suites / 6079 tests.

---

<a id="wave-45"></a>

## Wave 45 — Host Wiring & Final Depth

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 game-remainder-final | `game/Game.kt` |
| A2 monster-host-wiring | `creatures/monsters/**`, `game/scheduling/MonsterComputeService.kt` |
| A3 player-host-wiring | `creatures/Creature.kt` |
| A4 sender-wiring | `network/protocol/ProtocolSink.kt` (new), `game/**` sink call sites |
| A5 runtime-composition | `creatures/players/PlayerFunctions` stash unification, `items/Item.kt` imbuement attrs, `network/GameHandler.kt` |
| A6 player-depth-2 | `creatures/players/**` |

### Tasks

- **A1** remaining `game.cpp`: `playerSetShowOffSocket`, fiendish forge events (`makeFiendishMonster`/`removeForgeMonster`/`updateForgeableMonsters`/`checkForgeEventId`), wheel combat application (`applyWheelOfDestinyHealing`/`applyWheelOfDestinyEffectsToDamage`), `getIOWheel`, remaining handlers.
- **A2** host wiring: instantiate the async pipelines in `Monster`/`Game.checkCreatures` (`trySchedulePostThink`), wire `updateTargetList` spectator scan and `onCreatureAppear`/`onRemoveCreature` observation call sites.
- **A3** host wiring: `Player` overrides for `death`/`onWalk`/`goToFollowCreature`, expose skills/`levelPercent`/`magLevelPercent`/`manaSpent`/`damageMap`/`skillLoss`/`lastLogout`, wire the walk queue to the walk event, `setNextAction*Task` scheduler variants.
- **A4** wire the ported senders into state-change paths via a `ProtocolSink` seam (creature say/move/health/outfit, stats/skills/inventory/container, text messages, cancel messages) and call it from `Game`/`Player`/`Map` paths.
- **A5** runtime composition: unify the stash (`PlayerFunctions` map vs `Player.stash`), market escrow inbox, item imbuement attribute store, `Game`/`ImbuementEngine` composition into the production context.
- **A6** `Player` depth: continue toward 821 raw methods (remaining families: stats/record, condition params, charm/blessing stacks, mount helpers, guild/war helpers, misc accessors).

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: game.cpp remainder batch, monster host wiring, player host wiring, ProtocolSink sender wiring, runtime composition (stash/imbuements/market escrow), Player depth 2. 572 suites / 6284 tests.

---

<a id="wave-46"></a>

## Wave 46 — Combat Pipeline, Plumbing, Remaining Depth

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 game-completion | `game/Game.kt` |
| A2 player-depth-3 | `creatures/players/**` |
| A3 composition-plumbing | `bootstrap/**`, `network/GameHandler.kt`, `creatures/Player.kt` |
| A4 monster-type-state | `creatures/monsters/**` |
| A5 map-spectator-sectors | `map/**`, `network/protocol/map/**` |
| A6 imbuement-decay | `systems/imbuement/**`, `items/Item.kt` |

### Tasks

- **A1** `Game::` combat pipeline (`combatChangeHealth`, `applyHealthChange`, Gift-of-Life), remaining handlers (quick-loot/depot, stash, channels, cyclopedia, party/records), and the documented host one-liners (`checkCreatures → trySchedulePostThink`, walk seams bound to host methods).
- **A2** continue `Player::` depth toward 821 (condition params, charm/blessing stacks, mount/guild helpers, misc accessors).
- **A3** bootstrap plumbing: pass `itemTypes`/`ioMarket`/`imbuements` into `GameHandler` from `CanaryServer`/`ServerBootstrap`; add `Player.getImbuingItem()` accessor; bind `Game` walk seams to host methods.
- **A4** monster-type/forge state on `Monster`: `getForgeStack`, `canBeForgeMonster`, `setMonsterForgeClassification`, `configureForgeSystem`, `setTimeToChangeFiendish`, `clearFiendishStatus`, `attackSpells`/`castSpell`/`canUseAttack`/strategies.
- **A5** spectator sector lists (16×16) in the map access + `Map::moveCreature` spectator fanout so moves emit frames.
- **A6** imbuement decay scheduler tick + `Item` imbuement registry validation seam.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: combat pipeline, player depth 3 (596/821), composition plumbing, monster type state, map spectator sectors, imbuement decay. 587 suites / 6473 tests.

---

<a id="wave-47"></a>

## Wave 47 — Game Remainder 2, Creature Hooks, Composition

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 game-remainder-2 | `game/Game.kt` |
| A2 creature-hooks | `creatures/Creature.kt` |
| A3 player-depth-4 | `creatures/players/**` |
| A4 imbuement-composition | `network/GameHandler.kt`, `systems/imbuement/**`, `items/Item.kt` |
| A5 lua-item-attributes | `lua/bindings/core/ItemFunctions.kt`, `lua/bindings/core/PlayerFunctions.kt` |

### Tasks

- **A1** remaining `game.cpp` (~145 real members): quick-loot/depot/stash handlers, `playerSay*`/`playerUseItem*`, cyclopedia info, records/online, state updaters, item creation/batch, team-finder/NPC/forge helpers. Also wire the seams now available: Game forge seams → `MonsterPipelinesHost.typeStateOf`/`MonsterForge`; `internalPlaceCreature` → `map.placeCreature`; `removeCreature` → `map.removeCreature`; `spectatorsOf` → `map` spectator query.
- **A2** `Creature::onCreatureMove` virtual + Player/Monster overrides; wire the remaining host state classes; `addCreatureHealth`/sink consistency.
- **A3** continue `Player::` depth toward 821.
- **A4** production imbuement composition: `GameHandler` builds `ImbuementEngine` with the dispatcher; assign `Item.imbuementRegistry` at the composition root.
- **A5** Lua item attributes: `ItemFunctions` custom-attribute methods and `player:applyImbuementScroll`/`clearAllImbuements` use the real stores now that they exist.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: game remainder 2 (~84 members left), creature hooks, player depth 4 (648/821), imbuement composition, Lua item attributes. Fixed a deterministic test race in ImbuementDecaySchedulerTest. 603 suites / 6666 tests.

---

<a id="wave-48"></a>

## Wave 48 — Game Remainder 3, Player Depth 5, Composition

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 game-remainder-3 | `game/Game.kt` |
| A2 player-depth-5 | `creatures/players/**` |
| A3 monster-composition | `creatures/monsters/**`, `game/scheduling/**` |
| A4 item-registry-hub | `items/Item.kt`, `io/**` item-creation paths |

### Tasks

- **A1** remaining `game.cpp` (~84 real members): stash/depot-search Game methods, use-item handlers, item batch creation, cyclopedia info, maps/start/shutdown, team-finder/NPC helpers, `combatBlockHit`/`applyCharmRune` wiring.
- **A2** continue `Player::` depth + async follow (`goToFollowCreature_async` pipeline with generation safety).
- **A3** monster composition: bind `checkCreatureAttack`/`hasExtraSwing`, follow-path compute host wiring, post-think queue lane hops.
- **A4** assign `Item.imbuementRegistry` at every item-creation hub (map load, persistence, `Game.createItem`, loot) so registry validation is universal.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: game remainder 3 (~39 members left), player depth 5 + async follow (659/821), monster composition, item registry hubs. 617 suites / 6858 tests.

---

<a id="wave-49"></a>

## Wave 49 — Final Game Members, Player Depth 6, Login Items

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 game-final | `game/Game.kt` |
| A2 player-depth-6 | `creatures/players/**` |
| A3 composition-final | `network/GameHandler.kt`, `creatures/Creature.kt` |
| A4 login-item-materialization | `io/**`, `database/ItemAttributeCodec.kt` |

### Tasks

- **A1** the last ~39 `game.cpp` members: `loadMap`/`loadMainMap`/`loadCustomMaps`/`start`/`loadMotdNum`, movement (`playerMoveItem`/`playerMoveCreature`/`playerMoveThing` + ByID), `playerEquipItem`/`playerWrapableItem`/`playerLookInShop`/`playerChangeOutfit`/`playerConfigureShowOffSocket`/`playerInspectItem`, reward chest ×2, big cyclopedia (`playerCyclopediaCharacterInfo`, list getters, `registerAchievement`), `combatBlockHit`/`applyCharmRune`, misc (`addMoney`, `updatePlayersOnline`, `playerCheckActivity`, `resetMonsters`, `createLuaItemsOnMap`, `loadAppearanceProtobuf`, `parsePlayerExtendedOpcode`, `playerDebugAssert`, `sendOfflineTrainingDialog`, `outfitSupportsMount`).
- **A2** continue `Player::` depth toward 821.
- **A3** composition roots: `GameHandler` imbuement-registry assignment at its `Item.create` sites; `Creature.kt` async-batch routing to `WorkerCompletion`.
- **A4** `IOLoginData` item materialization (`loadItems`) + `ATTR_CUSTOM` encode/decode in `ItemAttributeCodec` so imbuements/attributes round-trip through player persistence.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: game.cpp final batch (7 members left), player depth 6 (672/821), composition final, login item materialization + ATTR_CUSTOM codec. 625 suites / 7029 tests.

---

<a id="wave-50"></a>

## Wave 50 — Final 7 Game Members, Player Depth 7, Nested Inventory

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 game-last-7 | `game/Game.kt` |
| A2 player-depth-7 | `creatures/players/**` |
| A3 host-composition-3 | `creatures/monsters/**`, `creatures/PlayerFeatures.kt` |
| A4 nested-inventory-persistence | `io/**` |

### Tasks

- **A1** the final 7 `game.cpp` members: `combatBlockHit`, `playerCyclopediaCharacterInfo`, `loadMap`/`loadMainMap`/`loadCustomMaps`, `loadAppearanceProtobuf`, `loadBoostedCreature` (port the real bodies with injected seams where the map/appearances are io-owned).
- **A2** continue `Player::` depth toward 821.
- **A3** host composition remainder: bind `asyncTaskDispatcher`/`onExecuteAsyncTasksHost` in `MonsterPipelinesHost.register`; compose the player state holders (combat bonuses, active foods, message buffer) into `PlayerFeatures`.
- **A4** nested inventory persistence: add `pid`/`sid` to `player_inventory_items` (**V17**) and materialize nested inventory containers in `IOLoginData` (mirroring the depot/inbox tree rebuild).

### Rules

- §1.1 parity, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: all 7 final `Game::` members (game.cpp row IMPLEMENTED), player depth 7 (710/821), host composition 3, nested inventory persistence (V17). The tracked inventory is 100% IMPLEMENTED (115/115 rows). 635 suites / 7156 tests.

---

<a id="wave-51"></a>

## Wave 51 — Depth Closure (Player, Host Bindings, Persistence, Barrier Lanes)

**Status:** In progress
**Process:** AGENTS.md §6. The tracked inventory is 100% IMPLEMENTED; this wave closes depth/composition gaps.

### File ownership

| Agent | Owns |
|---|---|
| A1 player-depth-8 | `creatures/players/**` |
| A2 host-bindings | `creatures/Creature.kt`, `network/protocol/GamePacketRouter.kt` (cyclopedia delegation only) |
| A3 persistence-depth-2 | `io/**` |
| A4 barrier-lanes | `game/scheduling/**`, `creatures/monsters/**` |

### Tasks

- **A1** remaining ~111 `Player::` methods toward 821 raw.
- **A2** host bindings: compose the player state holders (item abilities, boss points, cyclopedia trackers, aim-at-target, magic affinity) into the host `Player`/`PlayerFeatures`; `Game.playerSay`→`removeMessageBuffer`; delegate the cyclopedia router branch to `Game.playerCyclopediaCharacterInfo`; imbuement adapter stats (`setVarSkill`/`setBonusCapacity`/`sendStats`).
- **A3** persistence depth: live inventory save walk (`savePlayerItem` row builder from `inventory[slot]` + BFS `sid` assignment) and `OPENCONTAINER` re-arming from `getOpenContainers`.
- **A4** barrier-lane execution for creature async tasks (barrier-parallel buckets, per-slice budget, rejection rollback) replacing the WorldCommit mapping.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: player depth 8 (733/821), host bindings, persistence depth (live save walk + OPENCONTAINER), barrier lanes. 643 suites / 7237 tests.

---

<a id="wave-52"></a>

## Wave 52 — Composition One-Liners, Persistence Guard, Dispatcher Depth

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 player-depth-9 | `creatures/players/**` |
| A2 composition-liners | `game/Game.kt`, `network/protocol/GamePacketRouter.kt`, `config/**` |
| A3 persistence-guard | `io/**` |
| A4 dispatcher-depth | `game/scheduling/**`, `creatures/Creature.kt` |

### Tasks

- **A1** remaining ~88 `Player::` methods toward 821 raw.
- **A2** the documented one-liners: `Game.playerSay`→`removeMessageBuffer`, aim-at-target router branch (0xC8), `Game.transformItem` for charged items, `maxMessageBuffer` from config, world-shared `muteCountMap`, non-monster creature async buckets at login/placement.
- **A3** `savePlayerGuard` calling the live inventory/depot/reward/inbox walks; depot/reward/inbox `Player` sinks.
- **A4** dispatcher depth: serial-lane admission reservation, adaptive budget refresh loop (config keys, 250ms), `Creature.safeCall` barrier continuation, `observeInternalWork` gating.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: composition one-liners, player depth 9 (747/821), persistence guard, dispatcher depth. Fixed the imbuement scheduler test race. 652 suites / 7289 tests.

---

<a id="wave-53"></a>

## Wave 53 — Composition Root, Persistence, Dispatcher Polish

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 player-final-depth | `creatures/players/**` |
| A2 composition-root-final | `network/CanaryServer.kt`, `network/GameHandler.kt`, `creatures/Creature.kt`, `creatures/monsters/MonsterPipelinesHost.kt` |
| A3 persistence-final | `io/**` |
| A4 dispatcher-final | `game/scheduling/**` |

### Tasks

- **A1** remaining `Player::` methods (mostly `send*` seams already covered by writers — port any that are real logic; report the seam-only remainder).
- **A2** composition root: world-shared `muteCountMap`/`transformItemCharges`, shared async buckets into `MonsterPipelinesHost`, `CanaryServer` config/telemetry wiring, `Player::depotChests` accessor.
- **A3** `savePlayerGuard` stash/bestiary/prey/task-hunting live saves; shared transaction where feasible.
- **A4** dispatcher polish: periodic latency logging, monster lateness → `observeInternalWork`, `safeCall` resumption context.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: composition root final, player final depth (754/821), persistence final (stash/bestiary/prey/task + shared transaction), dispatcher final (telemetry + context). 657 suites / 7327 tests.

---

<a id="wave-54"></a>

## Wave 54 — Seam Binding & Composition Details

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 seam-binding | `creatures/players/**`, `game/Game.kt` |
| A2 composition-root-2 | `bootstrap/**`, `network/CanaryServer.kt`, `io/**` |
| A3 component-accessors | `creatures/PlayerFeatures.kt`, `creatures/players/**` |
| A4 monster-async-final | `creatures/monsters/**`, `game/scheduling/**` |

### Tasks

- **A1** bind the 38 `send*` seam call sites (each has a writer counterpart) so state changes emit frames: loot message, market leave, party icons, prey/task data, tile sends, team finder, marker, basic data, forge, depot, shop, bosstiary, creature sends, generic network message.
- **A2** `ServerBootstrap` config/buckets into `CanaryServer`; `MonsterPipelinesHost` production composition; `IOLoginData` `depotChests` persistence walk; `savePlayerGuard` shared transaction at the root.
- **A3** compose `achiev()`/`animusMastery()` components into `Player`/`PlayerFeatures`.
- **A4** `MonsterAsyncPipelines` private `safeCall` continuation hop; host lateness sinks → `observeInternalWork`.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: send-seam binding (38/38), composition root 2, component accessors (achievement/animusMastery), monster async final. 663 suites / 7424 tests.

---

<a id="wave-55"></a>

## Wave 55 — Live Transport & Login Integration

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 sink-transport | `network/protocol/ProtocolSink.kt`, `creatures/players/PlayerSendSink.kt`, `network/GameHandler.kt` |
| A2 monster-registration | `game/Game.kt`, `creatures/monsters/MonsterPipelinesHost.kt` |
| A3 lua-delegation | `lua/bindings/core/PlayerFunctions.kt`, `lua/bindings/core/PlayerFunctionsExtra.kt` |
| A4 login-load | `network/GameHandler.kt`, `io/**` |

Note A1/A4 share `GameHandler.kt`; A1 does the sink install, A4 the login load. Coordinate by separate method regions; merge conflicts resolved by union.

### Tasks

- **A1** install `PlayerSendSink` on the live channel: make `NettyProtocolSink` (or a subclass) implement the 38 player senders over the real writers; `GameHandler` assigns it.
- **A2** `Game.internalPlaceCreature`/`removeCreature` call `monsterHost.register/unregister` (with `monsterTypeProvider`) so production pipelines activate.
- **A3** Lua bindings delegate achievement/animus operations to the composed `PlayerFeatures` components instead of duplicate maps.
- **A4** `GameHandler` login loads the player through `IOLoginData.loadPlayerById` (real inventory/skills/feature state) instead of the stub `Player`.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report wiring, test count, gradle result, gaps.

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: live sink transport, monster registration wiring, Lua delegation, login load (V18). 669 suites / 7442 tests.

---

<a id="wave-56"></a>

## Wave 56 — Registry Injection, Spawn Path, Persistence Sub-Loads

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 registry-injection | `bootstrap/**`, `network/CanaryServer.kt` |
| A2 spawn-path | `creatures/spawns/**`, `network/CanaryServer.kt` (spawn region) |
| A3 persistence-subloads-1 | `io/**` |
| A4 persistence-subloads-2 | `io/**` (separate classes; coordinate via file names) |
| A5 lua-extra-ops | `lua/bindings/core/PlayerFunctionsExtra.kt` |

A1/A2 share `CanaryServer.kt` — separate regions; A3/A4 share `io/**` but different files (`IOLoginData` split by method region is risky; instruct A4 to add a separate `IOLoginDataExtraLoads.kt` helper to avoid conflicts).

### Tasks

- **A1** inject `ioLoginData`, a `Monsters`-backed `monsterTypeProvider`, and a `Vocations` registry from `ServerBootstrap` into `CanaryServer`/`GameHandler`.
- **A2** compose the production spawn path (`MonsterFactory`/spawn caller) using the existing spawn engine.
- **A3** `loadPlayer` sub-loads part 1: conditions, skull, guild, storage map, VIP, exiva restrictions.
- **A4** `loadPlayer` sub-loads part 2 (new helper file): bestiary charms, store inbox, reward chest, prey/task hunting.
- **A5** delegate the extra-ops badges/titles/concoction/OTCR Lua state to composed components.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push).

### Completion

Completed: registry injection, production spawn path, persistence sub-loads (V19), Lua extra-ops delegation. 675 suites / 7487 tests. Merge conflicts (CanaryServer monsters param, IOLoginData constructor) resolved by union.

---

<a id="wave-57"></a>

## Wave 57 — Final Depth Polish

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 monster-type-depth | `bootstrap/**`, `creatures/monsters/**`, `lua/**` (monster-type registration path) |
| A2 subloads-polish | `io/**` |
| A3 wheel-apis | `systems/wheel/**` |
| A4 storage-composition | `creatures/PlayerFeatures.kt`, `creatures/players/**` |

### Tasks

- **A1** populate the booted `Monsters` registry from the datapack: run the Lua monster-type registration path at boot (or parse the data pack's type definitions) so `g_monsters()` is non-empty; deepen the XML mapping where feasible.
- **A2** outfit sub-load + `Reward`-as-`Container` shape parity.
- **A3** `PlayerWheel` upgrade/additional-area/target/duration/`unlockScroll`/`onThink` APIs (the Lua extra-ops gaps).
- **A4** `Player::m_storage` host composition (PlayerStorage over outfits/familiars), host title/badge player reads, `AnimusMastery` blob persistence wiring.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push).

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: datapack monster-type registration at boot (67/67), outfit sub-load + Reward Container shape, PlayerWheel APIs, player storage/animus composition. 682 suites / 7556 tests.

---

<a id="wave-58"></a>

## Wave 58 — Combat Spell Actor, Host/Lua Unification, Known-Creature Set, Codec Fidelity

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 combat-spell-actor | `lua/bindings/MonsterSpellFunctions.kt`, `lua/bindings/MonsterTypeFunctions.kt`, `creatures/monsters/**`, `bootstrap/**` (monster boot region) |
| A2 host-lua-unification | `creatures/Creature.kt`, `lua/runtime/WorldLuaOperations.kt`, `lua/bindings/core/PlayerFunctions.kt`, `io/**` (animus blob call site) |
| A3 known-creature-set | `network/protocol/**`, `creatures/players/PlayerSendSink.kt` |
| A4 codec-fidelity | `io/**`, `database/ItemAttributeCodec.kt`, `resources/db/migration/**` |

### Tasks

- **A1** port the `CombatSpell` actor for monster attack/defense blocks (resolve `sb.spell` into the ported `MonsterSpell`/spell model), nested `childLoot` in loot blocks, and capture inline `mtype.onSpawn`/`onThink` callbacks for live monsters.
- **A2** host `Player::storage()`/`getOutfits`/`getBankBalance` accessors; make the Lua storage/outfit shadow maps delegate to the composed `PlayerFeatures` state; wire the animus blob persistence call sites.
- **A3** session known-creature set (`checkCreatureAsKnown`, 1300-entry with canSee rules) so party/creature sends emit known-creature frames instead of fallbacks.
- **A4** condition codec fidelity (persist flat BUFFS/ABSORBS/INCREASES + PERSISTENT) and the `lookmount*` outfit columns (migration V20).

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push).

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: CombatSpell actor + nested loot + inline callbacks, host/Lua unification (storage/outfits/animus), known-creature set, condition codec + lookmount schema (V20). 685 suites / 7577 tests.


> **Postscript (2026-10-15):** three of wave 58's four branches
> (`host-lua-unification`, `known-creature-set`, `codec-fidelity`) silently
> failed to merge at the time while the summary above recorded them as done.
> They were recovered and merged during wave 63 (`4589ca3b`, `4b11d72e` and the
> host-lua merge) after an ancestry audit — the recorded completion claims are
> accurate again as of that recovery.
---

<a id="wave-59"></a>

## Wave 59 — Event Dispatch, Spell Registry, Condition Save, Visibility

**Status:** In progress
**Process:** AGENTS.md §6.

### File ownership

| Agent | Owns |
|---|---|
| A1 monster-event-dispatch | `creatures/monsters/**`, `bootstrap/**` (boot region), `lua/bindings/core/LuaEnums.kt` |
| A2 spell-registry-actor | `lua/bindings/SpellBindings.kt`, `lua/bindings/MonsterSpellFunctions.kt`, `spells/**` |
| A3 condition-save | `io/**`, `resources/db/migration/**` (V21) |
| A4 visibility-ghost | `creatures/Creature.kt`, `network/protocol/creature/**`, `network/protocol/PlayerSendSink.kt` |

### Tasks

- **A1** `registerEvent` pushes registered events to already-spawned monsters; dispatch `onAppear`/`onDisappear`/`onMove`/`onSay`/`onPlayerAttack`; load `data/libs/functions/revscriptsys.lua` at boot; register `MONSTERS_EVENT_*` Lua enums.
- **A2** wire `sb.spell = g_spells().getSpellByName(name)` (compiled spell registry hit) in `deserializeSpell`.
- **A3** port the condition save path (`iologindata_save_player.cpp:279-291`); single-column Canary animus schema; bind the storage persistence call sites in the composition root.
- **A4** `Player::canSeeCreature` override (ghost + access, invisibility); `sendCreatureSquare` canSee gate; `sendCreatureAppear` stackpos→`sendUpdateTile` fallback.

### Rules

- §1.1 parity, §1.2 WorldCommit confinement, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push).

### Post-wave (§6)

Update §1–§3, `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

### Completion

Completed: monster event dispatch (14), spell registry hit, condition save + V21 animus column, visibility/ghost parity. 689 suites / 7620 tests.

---

<a id="wave-60"></a>

## Wave 60 — Repository Improvements (`docs/development/implementation-status.md`)

**Status:** Completed (2026-10-14) — all 8 improvements merged and pushed
**Decisions (user):** I2 keep upstream download URLs annotated · I7 copy datapack verbatim · I4 full 45-file port · I8 keep root compose + add docker/ guides · I5 **MIT license** (user-elected; upstream Canary is GPL-2.0 — provenance noted in README acknowledgments) · Q1-Q7 resolved as listed
**Source of requirements:** `docs/development/implementation-status.md` (user-authored)
**Baseline:** `main` @ `cf6496d`; 689 suites / 7620 tests green; zero Kotlin warnings
**Rules from implementation-status.md:** every improvement documented in `docs/`; **each improvement committed as a separate commit**

---

### 0. Summary of the 8 improvements

| # | Improvement | Scope | Commit type |
|---|---|---|---|
| I1 | Remove local absolute path references | 59 tracked files | docs/chore |
| I2 | Point project references at `gdamoreira/canary-kotlin` | 10 tracked files + 1 binary | chore |
| I3 | Move hardcoded RSA keys to configuration | 4 source files + tests | feat |
| I4 | Port and adapt `canary/docs` → `docs/` | 45 upstream files → curated port | docs |
| I5 | README parity sections (12 requested) | `README.md` | docs |
| I6 | GitHub CI/CD pipeline | new `.github/workflows/` | ci |
| I7 | Copy `data-otservbr-global` datapack | ~38 MB | data |
| I8 | Full `docker/` delivery + guides | compose, env, guides | build |

Execution order = above (I1/I2 first: they rewrite files I4/I5 add; I7/I8 late: heaviest artifacts).
All items are **sequential on `main`** (they overlap files; parallel worktrees would conflict).

---

### I1 — Remove local absolute path references

**Requirement:** "Remove all reference to local files `/Users/<name>` for example. Consider the root path as `../.projects/`."

**Evidence (measured):** 59 tracked files contain the literal home prefix; ~60 occurrences.

- `AGENTS.md` (§5.1 template: "Canary Repository Root: `/Users/<name>/.projects/canary`")
- `docs/development/TEMPLATE_FEATURE.md` (same template)
- `docs/development/done/050-…` – `docs/development/done/064-…` (15 feature docs)
- `docs/development/implementation-status.md`, `docs/development/implementation-status.md`
- `src/main`: `systems/hireling/Hireling.kt`, `systems/hireling/HirelingRegistry.kt`, `systems/rewardchest/{ReturnValue,Reward,RewardChest}.kt` (KDoc "Source:" lines)
- ~35 test files (KDoc "Canary source of truth / Source of truth / Source" lines)

**Patterns seen:** bare root (`…/.projects/canary`), root + `src/...:line` refs, template field, prose sentences.

**Task:**
1. Mechanical sweep: `/Users/<name>/.projects/canary` → `../.projects/canary`, preserving subpaths and `:line` suffixes.
2. Update `AGENTS.md` §5.1 and `docs/development/TEMPLATE_FEATURE.md` so the **convention is relative going forward** (prevents recurrence).
3. Reword the `docs/development/implementation-status.md` directive line to use `/Users/<name>` placeholder (self-referential literal otherwise defeats the check).

**Validation:**
- `git grep -nE "Users/[A-Za-z0-9._-]+"` → **0 matches**.
- `./gradlew clean test` green (KDoc-only change, still gate it).

**Commit:** `chore(docs): use relative ../.projects paths for Canary source references`

---

### I2 — Point project references at `gdamoreira/canary-kotlin`

**Requirement:** "Change all `https://github.com/opentibiabr/canary` references to `https://github.com/gdamoreira/canary-kotlin`."

**Evidence (measured, `git grep -l opentibiabr`):**

| File | Occurrences | Kind | Decision needed |
|---|---|---|---|
| `README.md` | 2 | badge + link | Branding → change |
| `MapDownload.kt` | 3 | **functional download URLs** (`config.lua.dist` raw @ v3.6.1/main; map release base) | Q1 |
| `MapDownloadTest.kt` | 7 | test fixtures for same URLs | follows Q1 |
| `StatusHandler.kt` | 2 | XML `url=` + owner email | Branding → change |
| `Webhook.kt` | 1 | user-agent string | Branding → change |
| `V2__seed_test_accounts.sql` | 1 | `test@opentibiabr.com` seed email | Low impact → change |
| `OpcodeApplicationMarketRouterTest.kt` | 1 | test string | Check context → change |
| `data-canary/scripts/actions/objects/world_board.lua` | 2 | datapack Lua text (`github.com/opentibiabr` org URL, donation link) | **Gameplay content** → Q1 |
| `data-canary/world/canary.otbm` | 1 (binary) | string inside the map binary | Do not edit binary → leave |
| `docs/development/implementation-status.md` | 1 | the directive itself | Leave |

**Decision (Q1):** functional map/config download URLs in `MapDownload.kt` fetch artifacts that only exist on the upstream repo. Pointing them at the fork breaks downloads unless the fork hosts releases. Options: keep upstream for the *data source* with an explicit comment, or fork-all + publish artifacts.

**Task:** change branding/docs/tests per Q1; keep upstream only where it is a live artifact source and then annotate with a comment explaining why.

**Validation:** `git grep -n opentibiabr` equals the reviewed allow-list; `MapDownloadTest` + `StatusHandler` tests green.

**Commit:** `chore(repo): point project references at gdamoreira/canary-kotlin`

---

### I3 — Move hardcoded RSA keys to configuration

**Requirement:** "Hardcoded RSA encryption keys should be moved to a configuration file."

**Evidence (measured):**
- `network/crypto/RsaCipher.kt`: `companion.DEFAULT_P` / `DEFAULT_Q` (1024-bit decimal strings, the OpenTibia/Canary textbook-RSA key pair), `E = 65537`, `BLOCK_SIZE = 128`; constructor already accepts `pString`/`qString`.
- Default-constructed at: `network/protocol/GameHandler.kt:52`, `network/protocol/LoginHandler.kt:23`, `network/CanaryServer.kt:82`.
- 8 test call sites rely on defaults (`CryptoTest`, `LoginProtocolTest` ×2, `GameProtocolTest`, `GameHandlerLoginLoadTest`, `GameHandlerGameWiringTest`, `CanaryServerSharedWorldStateTest`).

**Task (TDD):**
1. New config keys `CANARY_RSA_P` / `CANARY_RSA_Q` (env + `--config` properties, same layering as `ConfigManager.load`: defaults < env < properties < CLI).
2. Defaults remain the current Canary key material, so parity is unchanged when unset.
3. Wire through composition: `CanaryServer` reads config and passes explicit `RsaCipher(p, q)` into `LoginHandler`/`GameHandler`.
4. Never log key material; invalid key config fails fast with a clear error.
5. Tests: RED first — overriding env changes `n` (decrypt of `encrypt()` still round-trips); defaults unchanged; invalid key rejected.

**Validation:** `./gradlew clean test` green; zero warnings.

**Commit:** `feat(config): make RSA key material configurable (CANARY_RSA_*)`

---

### I4 — Port and adapt `canary/docs` into `docs/`

**Requirement:** "Documentation in `canary/docs` repository should be copied to this repository and updated to reflect the changes made to the codebase."

**Evidence (measured):** upstream `../.projects/canary/docs` = **45 files / 1.2 MB**:

- `README.md` (index), `architecture.md`, `operations.md`, `hardware-sizing.md`, `development.md`, `release.md`, `crypto-backend-evaluation.md`
- `building/` (8 files — Debian/Ubuntu/WSL/Windows CMake + local-validation + recompile; all C++-specific)
- `development/shared-build-cache.md`, `maps/startup-loading-performance.md`
- `docker/quickstart-for-beginners.md`
- `lua-api/` (4 generated files: `.d.lua`, `.json`, `.md`, quality baseline)
- `python-scripts/` (4 C++ maintenance scripts), `Doxyfile.in`, `config.ld.in`, logos
- `systems/` (17 files, incl. `multiprotocol.md`, `lua-shared-userdata.md`, `performance-lifetime/*`, `taskboard-module.md`, `weapon-proficiency.md`, `content-reference-auditor.md`)

**Task (curated port — Q3):**
1. Add `docs/README.md` (documentation index) and adapt: `architecture.md` (JVM/C Kotlin architecture), `operations.md`, `hardware-sizing.md`, `development.md`, `release.md` (Gradle dist), `docker/quickstart-for-beginners.md`.
2. Port `docs/systems/*` engine docs already referenced by completed waves (keep prose, mark Kotlin status where behaviors are ported).
3. Replace C++ `building/*` with Kotlin/Gradle equivalents (`docs/building/kotlin-local-validation.md` etc.); skip `python-scripts/`, `Doxyfile.in`, `config.ld.in` (C++ tooling), or port only if useful for Lua API docs.
4. Every ported file carries an "Adapted from `canary/docs/<path>`" provenance line and uses the I1 relative-path convention.

**Validation:** index links resolve; `git grep -nE "Users/[A-Za-z0-9._-]+"` still 0; no stale C++ build claims.

**Commit:** `docs: port and adapt canary documentation for the Kotlin engine`

---

### I5 — README parity and requested sections

**Requirement:** README compatible with the original `canary` README, updated for this codebase, with sections: installation, contributing, license, troubleshooting, code of conduct, security policy, roadmap, future improvements, known issues, FAQ, acknowledgments, architecture explanation.

**Evidence:** current `README.md` (10.3 KB) has: intro, architectural adaptations table, Getting Started, Docker Infrastructure, Project Structure, Parity & Verification, Lua Policy, Feature Coverage, Implementation Coverage. **None of the 12 requested sections exist**; badge/link point at `opentibiabr/canary`.

**Task:** add a table of contents and all 12 sections; keep existing current content and truthful metrics; link architecture to `docs/architecture.md` (I4); license section reflects the repo `LICENSE` (I5 dependency: add `LICENSE` — see Q5).

**Validation:** section checklist (12/12); relative links resolve.

**Commit:** `docs(readme): add installation, governance and operational sections`

---

### I6 — GitHub CI/CD pipeline

**Requirement:** "Github CI/CD pipeline should be set up to automate the test and build."

**Evidence:** **no `.github/` directory** in this repo. Upstream has 23 workflows; only the build/test pattern is relevant (Lua smoke tests, vcpkg and MySQL schema checks are C++-only).

**Task:**
1. `.github/workflows/ci.yml`: on push `main` + PRs — `actions/setup-java` Temurin **25**, Gradle cache (`gradle/actions/setup-gradle`), `./gradlew clean test`, upload JUnit XML + HTML reports on failure.
2. Optional `.github/workflows/docker.yml`: build `Dockerfile` on PR touching `Dockerfile|docker/**` (no push, no registry auth).
3. No invented jobs (no Sonar, no release automation) — add later if required.

**Validation:** YAML lint; first CI run green on push; suite mirrors local counts.

**Commit:** `ci: add GitHub Actions build and test pipeline`

---

### I7 — Add the `data-otservbr-global` datapack

**Requirement:** "`canary` repository contains `data-otserv-global` directory containing files for the game server and should be copied to this repository."

**Evidence (measured):**
- Upstream directory is actually named **`data-otservbr-global`** (38 MB) — the playable global datapack.
- This repo already ships `data/` (9.3 MB shared engine Lua/XML/items) and `data-canary/` (19 MB: lib, monster, npc, raids, scripts, world `canary.otbm`, migrations).
- The global datapack (its own world `.otbm`, scripts, NPCs, spawns) is absent.

**Task (Q2):** copy `../.projects/canary/data-otservbr-global/` **verbatim** (Lua stays Lua — zero content edits), verify boot can select it as datapack, document it in `docs/` and README project structure.

**Validation:** boot smoke with datapack selected; `./gradlew clean test` green; repo size note documented.

**Risk:** +38 MB git history; confirm whether world `.otbm` files should instead be fetched on boot via `MapDownload` (existing capability) — Q2.

**Commit:** `data: add data-otservbr-global playable datapack`

---

### I8 — Full Docker delivery and guides

**Requirement:** "A full `docker-compose.yml` should be created to automate the deployment … It has been created in the `docker` directory. All necessary data, configuration files, and environment variables should be included. All guides in `canary` repository should be copied to this repository and updated accordingly."

**Evidence (measured):**
- Root `docker-compose.yml` exists (Postgres 16 + server build-from-Dockerfile, `CANARY_*` env, healthcheck for db only, named volume) and root `Dockerfile` (gradle:9.8.1-jdk25 multi-stage → temurin 25 JRE).
- **No `docker/` directory**, no `.env.dist`, no `docker/DOCKER.md`, no quickstart guide — so the "created in the `docker` directory" statement describes the upstream layout, not this repo's.
- Upstream `canary/docker/`: `docker-compose.yml`, `DOCKER.md`, `.env.dist`(style), `config.sh`, `up.sh`/`up.ps1` (guarded start scripts), `Dockerfile.x86/arm/dev`, `quickstart/myaac/`, `data/start.sh`; guides at `canary/docs/docker/quickstart-for-beginners.md`.

**Task (Q4):**
1. Create `docker/` with: `docker-compose.yml` (Postgres 16 + server; healthchecks both; volumes; `CANARY_*` contract), `.env.dist` (all env vars documented), `DOCKER.md` (setup, env reference, troubleshooting), `quickstart-for-beginners.md` (adapted from upstream), guarded `up.sh`/`up.ps1`.
2. Reconcile duplicates: move root compose/Dockerfile into `docker/` (or keep root paths but avoid two sources of truth) — Q4.
3. Decide the fate of upstream quickstart extras (`MyAAC` website + `opentibiabr/login-server`) — they are C++-ecosystem components; this port's login is native — document the substitute instead of copying blindly.
4. All game data/config volume considerations documented (datapack I7, map, DB volume).

**Validation:** `docker compose -f docker/docker-compose.yml config` parses; optional local `docker compose up` smoke; guides reviewed against actual env.

**Commit:** `build(docker): add compose stack, environment template and operation guides`

---

### Execution strategy

1. **Sequential commits on `main`**, one per improvement, in order I1 → I8.
2. Docs for each improvement ride in that improvement's commit (satisfies "each improvement as separate commits" + "all improvements documented in `docs/`"). This plan is committed before starting (§6).
3. Full `./gradlew clean test` after I1, I3, I7 and a final run; zero warnings policy unchanged.
4. `CHANGELOG.md` gains one "Repository Improvements" phase entry; `docs/development/implementation-status.md` gets a status/log table as items complete (V-style record), or move to `docs/development/done/` style only if requested.
5. No parallel worktrees (file overlap), except I7 could be isolated if desired (pure add).

### Verification matrix

| Check | Command | Pass |
|---|---|---|
| Home paths gone | `git grep -nE "Users/[A-Za-z0-9._-]+"` | 0 matches |
| Fork refs | `git grep -n opentibiabr` | reviewed allow-list only |
| RSA override | new tests + `CryptoTest` | green |
| Docs links | relative-link audit | 0 broken |
| README sections | manual checklist | 12/12 |
| CI | YAML validation + first run | green |
| Datapack boot | boot smoke with datapack | green |
| Docker | `docker compose config` | valid |
| Suite | `./gradlew clean test` | 689+ suites / 0 failures / 0 warnings |

### Open questions (need user decision before execution)

- **Q1 (I2):** functional download URLs in `MapDownload.kt` — keep upstream `opentibiabr/canary` as the artifact source (annotated) or point at the fork and host the releases there?
- **Q2 (I7):** copy `data-otservbr-global` verbatim into git (~38 MB) or rely on `MapDownload` fetch-on-boot and skip committing the world files?
- **Q3 (I4):** full 45-file port (rewritten) or curated subset (architecture/operations/development/docker/systems + new Kotlin building guide), skipping C++-only tooling?
- **Q4 (I8):** move compose/Dockerfile into `docker/` (upstream layout) or keep root compose and add `docker/` guides only? Replicate MyAAC/login-server quickstart extras or document the native login substitute?
- **Q5 (I5):** this repo has **no `LICENSE`**; upstream is GPL-2.0. Adopt GPL-2.0 with attribution?
- **Q6 (I6):** CI scope OK as proposed (build+test, optional Docker build), GitHub Actions only?
- **Q7:** execution mode — sequential commits on `main` as planned (recommended), or parallel worktrees per item?

### Canary Original Code Reference

- **Upstream docs:** `../.projects/canary/docs/**` (45 files; index `README.md`, `architecture.md`, `operations.md`, `hardware-sizing.md`, `development.md`, `release.md`, `docker/quickstart-for-beginners.md`, `systems/**`)
- **Upstream docker:** `../.projects/canary/docker/{docker-compose.yml,DOCKER.md,up.sh,up.ps1,config.sh,quickstart/}`
- **Upstream CI:** `../.projects/canary/.github/workflows/{ci.yml,reusable-build-*.yml,reusable-docker-quickstart-smoke.yml}`
- **Upstream datapack:** `../.projects/canary/data-otservbr-global/`
- **Upstream governance:** `../.projects/canary/{LICENSE,CONTRIBUTING.md,CODE_OF_CONDUCT.md}` (no `SECURITY.md` upstream — content must be authored)
- **RSA key origin:** `../.projects/canary/key.pem` + the OpenTibia default decimal key pair mirrored in `RsaCipher.kt`

---

### Completion (2026-10-14)

All eight improvements landed as separate commits on `main` (see the status
table in `docs/development/implementation-status.md`), ending with **690 suites / 7627 tests green**
and zero Kotlin warnings. This plan moves to `docs/development/done/`.

