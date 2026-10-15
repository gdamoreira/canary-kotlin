# Changelog: Canary-Kotlin Development Timeline

All notable technical milestones, architectural transformations, feature implementations, and test-driven developments for **Canary-Kotlin** are documented in chronological order.

---

## 📅 Chronological Timeline

### Phase 1: Inception & Network Foundations (15:50 – 16:07)
- **`53ce982` - Repository Initialization & Crypto Stack**:
  - Initialized Git repository, configured Gradle with Kotlin DSL, and integrated Netty 4.1.
  - Implemented 64-round Little-Endian `XteaCipher`, 1024-bit raw textbook `RsaCipher` with Canary's standard OpenTibia primes ($p, q$), and RFC 1950 `Adler32Checksum`.
  - Built `TibiaFrameDecoder` and `TibiaMessageEncoder`.
  - Implemented `LoginHandler` parsing client OS, protocol version, RSA credentials, and delivering character list opcode `0x64`.
- **`07e4b2f` - Documentation Structure**:
  - Created `docs/development/`, `docs/development/`, and `docs/development/` lifecycle structure.
- **`fa0f889` - Architectural Adaptation & Parity Strategy**:
  - Documented `000-kotlin-architectural-design.md`, replacing C++ singletons (`g_game`, `g_dispatcher`) and raw mutexes with dependency injection and single-threaded Coroutine Actor confinement.
  - Established `000-parity-validation-strategy.md` and sequential execution roadmap.
- **`e23dfac` - Configuration Manager**:
  - Implemented `ServerConfig` and `ConfigManager` supporting environment variable overrides (`CANARY_*`) and properties fallbacks.
- **`1acc187` - Coroutine Actor GameDispatcher**:
  - Implemented `GameDispatcher` utilizing Kotlin Coroutines with priority lanes (`WorldCommit`, `Maintenance`) for lock-free state modifications.
- **`175298e` - Items & Containers**:
  - Implemented `ItemType`, `ItemTypeRegistry`, `Item`, and `Container` with recursive weight calculations and capacity limits.
- **`e1ee6f5` - Spatial Map Grid**:
  - Implemented `Position` (Chebyshev distance and range checks), `Tile`, and `GameMap`.
- **`175605a` - Creatures & Progression**:
  - Implemented `Creature`, `Monster`, and `Player` with Canary's cubic experience formula: $\frac{50n^3 - 300n^2 + 850n - 600}{3}$.
- **`67cbf6a` - Movement Engine**:
  - Implemented `Direction` offsets and step duration calculation: $\frac{\text{groundSpeed} \times 1000}{\text{creatureSpeed}}$.
- **`5624fb4` - Status Protocol (0xFF)**:
  - Implemented `StatusHandler` serving TSQP XML server info pings.
- **`f04a323` - Game Protocol Handshake**:
  - Implemented `GameHandler` authenticating character credentials on port 7172 and delivering `SelfAppear` (`0x0A`).

---

### Phase 2: RPG Mechanics & Domain Systems (17:49 – 18:25)
- **`c18c875` - Combat & Mitigation Engine**:
  - Implemented `CombatEngine` with physical damage formula $2 \times \frac{\text{attack} \times (\text{skill} + 5.8)}{25} + \frac{\text{level}}{5}$ and randomized armor reduction $P \in [\frac{\text{armor}}{2}, \text{armor} - 1]$.
- **`f91b41f` - Chat & Channels**:
  - Implemented `SpeechType` hearing radiuses (Whisper: 1, Say: 4, Yell: 18) and public channel broadcasting in `ChatManager`.
- **`f324741` - Snapshot Persistence**:
  - Implemented `PlayerData` and `InMemoryPlayerStorage` decoupling network threads from persistence.
- **`3901d21` - Action Scripting Registry**:
  - Implemented `ScriptRegistry` handling item actions (`onUse`) and talkactions (`!commands`).
- **`1ae91e5` - Modern Systems Baseline**:
  - Implemented `ImbuementSlot` duration decay and `PreySlot` monster buff multipliers.
- **`7b330f0` - Houses & Private Ownership**:
  - Implemented `House` and `HouseTile` permission lists (`aleta grav`, `aleta sio`) and intruder eviction.
- **`9a62037` - Guilds & Guild Wars**:
  - Implemented `Guild`, member ranks, and `GuildWar` frag-tracking limits.
- **`c2f8575` - Global Player Market**:
  - Implemented `MarketManager` handling buy/sell offer escrow and automated trade matching.
- **`3fed633` - Depots & Mail Delivery**:
  - Implemented `DepotService` with multi-town lockers, supply stash, and parcel mailbox delivery.
- **`85954e7` - Bosstiary & Lockouts**:
  - Implemented `BosstiaryManager` with Bane tiers (25, 100, 300) and 20-hour boss room lever lockouts.
- **`c979ca8` - OTBM Binary Map Parser**:
  - Implemented `OtbmNodeReader` unescaping byte markers (`0xFD`) and `OtbmMapLoader`.
- **`17c8d7a` - OTB Binary Items Parser**:
  - Implemented `OtbItemsLoader` decoding `items.otb` nodes and sprite ID mappings.
- **`f685a10` - XML Content Loaders**:
  - Implemented `XmlItemLoader` (`items.xml`) and `XmlVocationLoader` (`vocations.xml`).
- **`8e5b212` - Viewport Serialization**:
  - Implemented `MapPacketWriter` streaming $18 \times 14$ tile canvas descriptions (`0x64`) with empty run compression skip bytes.
- **`4554ed9` - Monster AI & Spawners**:
  - Implemented `Spawner`, `SpawnManager`, and `MonsterAiEngine` autonomous player approach steps.
- **`1a6c56a` - NPC Dialogue & Shop State Machine**:
  - Implemented `Npc` conversations (`hi`, `bye`, `trade`) and merchant price catalogs.
- **`a94edca` - World Hazards & Teleporters**:
  - Implemented `MagicField` damage, `TeleportItem` coordinates, and `DoorItem` toggles.
- **`9e9d956` - Relational Database Layer**:
  - Implemented `SqlitePlayerDatabase` JDBC persistence.
- **`093f50a` - LuaJ Runtime Embedding**:
  - Implemented `LuaScriptBridge` executing Lua scripts against Kotlin entities.

---

### Phase 3: PostgreSQL, Flyway & End-to-End Transition (18:30 – 18:38)
- **`3faf75f` - Containerization & Flyway**:
  - Added Docker Compose, multi-stage `Dockerfile`, Flyway migrations, and HikariCP connection pooling.
- **`468ad99` - PostgreSQL & In-Memory Mode**:
  - Switched database engine to **PostgreSQL 16+** for production and **H2 In-Memory (PostgreSQL mode)** for local testing.
  - Updated Flyway schema migrations (`V1__initial_schema.sql`, `V2__seed_test_accounts.sql`).
- **`e204547` - Full Login to World Transition**:
  - Unified `GameHandler` and `MapPacketWriter`.
  - Added `FullLoginToGameWorldFlowTest.kt` verifying complete client authentication and map viewport streaming.

---

### Phase 4: ProtocolGame Inbound Loop & Production Readiness (18:54 – 22:01)
- **`1a0d290` - Inbound Movement & Turns**:
  - Implemented `MovePacketHandler` decoding walking (`0x65..0x68`, `0x6A..0x6D`) and spot turns (`0x6F..0x72`).
- **`b8480dc` - Item Interactions & Throwing**:
  - Implemented `ItemPacketHandler` decoding item use (`0x82`) and dragging/throwing across ground/containers (`0x78`).
- **`0947682` - Combat Stances & Target Locking**:
  - Implemented `CombatPacketHandler` decoding fight modes (`0xA0`) and target creature locking (`0xA1`).
- **`60a2c81` - Look At & Inspection Notifications**:
  - Implemented `LookPacketHandler` (`0x8C`) and `DescriptionFormatter` emitting text messages (`0xB4`).
- **`1b0c989` - Safe Trade & Modal Windows**:
  - Implemented `TradeManager` safe 2-way item exchange and `ModalWindowManager` dialog callbacks (`0xFA`).
- **`f5d1874` - Heartbeat & Safe Logout**:
  - Implemented `KeepaliveHandler` ping/pong (`0x1E`) and combat logout prevention (`0x14`).
- **`5bd83bb` - Unified GamePacketRouter**:
  - Implemented multi-packet frame unbundling in `GamePacketRouter.kt` and connected to `GameHandler`.
- **`c8295d0` - Argon2id Security Engine**:
  - Implemented `Argon2Security` ($m=65536, t=4, p=1$) verifying web AAC password hashes with BouncyCastle.
- **`d3d231c` - Anti-DoS & Rate Limiting**:
  - Implemented Netty `RateLimiterHandler` token-bucket flood defense enforcing max packets per second.
- **`ccf24f7` - High-Performance MapSector Chunking**:
  - Implemented 32x32 `MapSector` array spatial chunking in `GameMap`, reducing JVM memory consumption by $>80\%$.
- **`3442f88` - Atomic Server Save & Graceful Shutdown**:
  - Implemented `SaveManager` flushing all online player inventories and world states to database.
- **`f213a13` - Death Penalty & Temple Revive**:
  - Implemented `DeathPenaltyEngine` calculating blessing exp mitigation and temple revives.
- **`b86b5a4` - Prometheus Observability**:
  - Implemented `MetricsRegistry` scraping active player counts and tick latency.

---

### Phase 5: Modernization to Java 25 & Canary Parity (21:55 – 22:36)
- **`b42e887` - Java 25, Gradle 9.8.1, Kotlin 2.4.21 Upgrade**:
  - Upgraded build toolchain to **Gradle 9.8.1**, **Kotlin 2.4.21**, and target runtime to **Java 25 LTS**.
- **`b682325` - Dual TCP Listeners**:
  - Updated `CanaryServer` to bind both Port 7171 (Login/Status) and Port 7172 (GameWorld) simultaneously with rate limiters.
- **`fe0130c` - Multi-Character E2E Integration Suite**:
  - Created `MultiCharacterSwitchMovementTest.kt` verifying login, Char 1 move, logout, Char 2 switch, move, and disconnect.
- **`f8ac145` - Asset Ingestion**:
  - Copied Canary example map (`canary.otbm` - 19 MB), `items.xml` (3.5 MB), `appearances.dat`, monster templates, and XML definitions to `data-canary/`.
- **`9b49068` - Canary C++ Test Transcriptions**:
  - Transcripted C++ unit tests for numeric bounds, string replacements, SHA1/SHA256 vectors, coordinate directions, and IP player capping.
- **`43090e3` - Currency & Vitals Regeneration**:
  - Implemented `Currency` coin conversions (Gold, Platinum, Crystal), `BankService`, and `VitalsRegenEngine`.
- **`7d17b03` - Magic Spells & Player QuestLog**:
  - Implemented `SpellRegistry` (instant spells, mana/level checks) and `PlayerStorageManager` (`getStorage`/`setStorage`).
- **`1a14b87` / `20cc0ea` - Gameplay Feature Matrix**:
  - Created `docs/development/implementation-status.md` inventorying all 56 Canary gameplay systems.
- **`5b83cd4` - Advanced Feature Specifications**:
  - Created implementation plans in `docs/development/` for features `050` through `064` (Skill Wheel, Forge, Bestiary, Store, Hazard, etc.).
- **`95bcad5` / `03fa14c` - Canary C++ File References**:
  - Annotated all 64 feature documents with file paths and line numbers from the original Canary repository.
- **`4918a4c` - Codebase Audit & Feature Template**:
  - Created `docs/development/implementation-status.md` and `docs/development/TEMPLATE_FEATURE.md`.
- **`da45490` - Wheel of Destiny Engine**:
  - Implemented `WheelOfDestinyEngine` promotion point budgets ($\max(0, \text{level} - 50)$), perk allocations, and passive stat bonuses.

---

### Phase 6: Canary Parity Mandate & Lua Remediation (23:00 – 23:50)
- **`6ee94f8` - Parity + Lua Boundary Policies**:
  - Added `AGENTS.md` §1.1 (strict Canary parity — no invented methods/APIs) and §1.4 (Lua stays Lua — never port Lua logic to Kotlin).
  - Enforced immediately: renamed fabricated `Npc.onHear` → Canary `Npc::onCreatureSay`, `MagicField.onStepIn` → `onStepInField`.
- **`87a83ad` / `d6f79d3` - Lua Parity Remediation Spec & Plan**:
  - Approved design superseding the `012` and `028` feature specifications (`docs/development/feature-history.md`); foundation implementation plan created.
- **`24e04fb`…`92aed2d` - Lua Scripting Engine Foundation**:
  - Implemented `org.opentibia.canary.lua`: `LuaEnvironment` + `ScriptError` + LuaJ compat shim, `UserdataRegistry` (Canary-named metatables), `Actions`/`TalkActions` registries with `Action()`/`TalkAction()` constructors, and a boot-time `Scripts` loader.
  - Removed the Kotlin `ScriptRegistry`; item-use now dispatches Lua actions on the `GameDispatcher` WorldCommit lane.
- **`bade5ed` - Corrected Feature Plans 050–064**:
  - Rewrote every advanced-feature plan to separate the C++ engine scope from Lua, filling the previously empty Canary source references (C++ vs Lua).

### Phase 7: Advanced Feature Engine Cores — Parallel Agent Waves (23:50 – 00:40)
- **`c3aca76` - Wheel of Destiny (parity)** — replaced the non-parity stub with `systems/wheel/`: all enums, `IOWheel` (36 slot bonus functions), `PlayerWheel` budget; later `WheelSpellTables`/`WheelGems`.
- **`e806b3d` - Weapon Proficiencies** — `systems/proficiency/` enums, experience tables + level math; later the JSON loader.
- **`a3dfeaa` - Concoctions** — `systems/concoction/` `ConcoctionType` + active state.
- **Wave 1 (C++-centric, 7 parallel worktrees)**: Offline Training, Reward Chest, Bestiary/Charms, Exaltation Forge, Mounts, Familiars, Daily Reward.
- **Wave 2 (Lua-dominant, 5 parallel worktrees)**: GameStore coins, Hazard, Hirelings, Soul War conditions, Raids.
- **Wave 3–4**: Wheel spell tables + gems; Weapon-Proficiency JSON loader; Bestiary registry/economy/elements; Wheel/offline/reward protocol parsers; Weapon-Proficiency payload serializer.
- **`55a4cc8` - Test Discovery Fix**: `GameDispatcherTest`'s `= runBlocking { }` expression bodies silently skipped by JUnit; fixed to `runBlocking<Unit>` (discovered == `@Test` count).

### Phase 8: Combat Stack, Lua Bindings & Wiring (00:40 – 01:30)
- **Combat stack**: `combat/CombatDamage` + `Combat` (replacing the invented `CombatEngine`), `combat/condition/*` (Condition engine core + outfit/light/invisible/cooldown/serene).
- **Lua bindings**: core primitives (Game/Player/Creature/Item/Container/Position) and `Spell`/`CreatureEvent`/`GlobalEvent`/`MoveEvent` registries; installed from `LuaEnvironment.initState()`; best-effort `data/libs` load.
- **Wiring**: combat consumers (bestiary charm procs, weapon-proficiency combat application, hazard bridge); `GamePacketRouter` dispatch for `0x61`/`0x62`/`0xE7`/`0x74`/`0xFF`.
- All delivered via isolated git worktrees dispatched in parallel and merged to `main`.

### Phase 9: Runtime State Wiring Foundations (01:30 – 02:00)
- **Player feature state** — `creatures/PlayerFeatures.kt` composes the per-player engine cores (wheel, offline training, concoctions, daily reward, mounts, familiars, bestiary, forge, hazard) and is exposed as `Player.features`.
- **Config gates** — `config/FeatureFlags.kt` mirrors Canary's advanced-feature config keys and exact defaults with `CANARY_*` env overrides.
- **Persistence** — Flyway `V3__advanced_feature_state.sql` (wheel slots, bestiary kills, mounts, familiars, weapon proficiency, offline/daily/forge scalars) + `database/JdbcFeatureStateRepository.kt`.

### Phase 10: Runtime Wiring (02:00 – 02:30)
- **Player vocation/premium** — `Player.vocationId`/`getPlayerVocationEnum`/`premium`/`isPremium`, flowing into `PlayerFeatures` (so `features.wheel` uses the real vocation).
- **Command application** — `WheelApplication` (save validation + gem actions), `OfflineTrainingApplication`, `RewardChestApplication`.
- **Lua world seams** — `WorldLuaOperations` real domain-backed `GameOperations`/`PlayerOperations`/`CreatureOperations`/`ItemOperations`/`ContainerOperations`/`PositionOperations`.
- **Integration** — `GamePacketRouter` applies wheel/offline commands to `player.features` on the WorldCommit lane; `LuaEnvironment.initState()` installs the real world seams.

### Phase 11: Deeper Runtime Wiring (02:30 – 03:15)
- **Reward chest + config gates** — `player.features.rewardChest` wired into opcode `0xFF` via a Player-backed `RewardChestOwner`; `GamePacketContext` carries `FeatureFlags` and gates the wheel system.
- **Player domain** — inventory (`Slots_t`, add/remove/count items, capacity), skills (`skills_t`, tries), and Item attribute bag (`ItemAttribute`, action/unique id, duration); Lua world seams now delegate to them.
- **Feature persistence flow** — `PlayerPersistenceService` + `SaveManager` save-all flush feature state; load seam for the login path.
- **Vocations** — `Vocations`/`Vocation` XML registry; `Player.getPlayerVocationEnum()` normalizes internal→CIP ids; offline-training requirements sourced from the registry.
- **Config consumption** — offline rate, proficiency multiplier/limits, hazard multipliers, forge costs, and wheel points-per-level consume `FeatureFlags`.
- **Lua event dispatch** — `LuaEventDispatcher`/`ScriptingDispatch`; `ItemPacketHandler` routes `onUse` through the dispatcher.

### Phase 12: Command Application & Login Flow (03:15 – 03:45)
- **Bestiary application** — `BestiaryApplication` (races/creatures/monster-data/buy-charm-rune: charm-point costs, rune-slot limits 2/6/25, removal fee, reset-all, minor-echo formula); `0xE1–0xE4` routed and applied to `player.features.bestiary`.
- **Forge application** — `ForgeApplication` (fusion/transfer/resource-conversion with exact costs/success rolls/tier promotion); `0xBF` routed.
- **Login flow** — `GameHandler` loads feature state via `PlayerPersistenceService` and dispatches the Lua `creatureEvent` `onLogin` on the WorldCommit lane.

### Phase 13: Documentation Finalization (03:45 – 04:00)
- Moved feature specs `050–064` from `docs/development/` to `docs/development/` (engine scope implemented + tested); `docs/development/` now holds only the architectural `000-*` docs.
- Updated `docs/development/implementation-status.md` to **56 / 56 implemented** with per-feature residual gaps; refreshed README/CHANGELOG metrics.

### Phase 14: Protocol Sends & Item Classification (04:00 – 04:30)
- **Bestiary outbound** — `BestiaryWindowSerializer` for `0xD5`–`0xD9` (races/creatures/monster-data/charms/entry-changed), byte-exact.
- **Wheel & Forge sends** — `WheelWindowSerializer` (open-wheel window: slots/points/gems/scrolls) and `ForgeHistorySerializer` (history paging + skill stats).
- **Item classification** — `ItemType.upgradeClassification` loaded from `items.xml`, wired into `PlayerForgeAdapter` so forge costs resolve from real item classifications.
- **Process gate** — `AGENTS.md` §6 added: every wave MUST update docs, commit them separately, and push.

### Phase 15: Forge Registry, Wheel Gem Economy, Deeper Persistence (04:30 – 05:00)
- **Forge registry wiring** — `GamePacketContext.itemTypes` threaded into `PlayerForgeAdapter` so live forge packets resolve real item classifications.
- **Wheel gem economy** — `WheelEconomy`/`WheelPlayer` seam + atelier costs (reveal/rotate/improve/destroy, fragment refunds) in `WheelApplication`.
- **Deeper persistence** — Flyway `V4__feature_state_details.sql`: bestiary charm assignments, weapon-proficiency perks, wheel gems/active-gems/grades.
- **Missing-features tracker** — new `docs/development/implementation-status.md` (full C++ → Kotlin inventory + ranked gaps + opcode/binding coverage) and a README implementation-coverage section.

### Phase 16: Pathfinding, Spawns, Core Senders, Lua Bindings (05:00 – 05:30)
- **A\* pathfinding** — `AStarNodes` (512-slot heap, walk costs) + `MonsterPathfinder` (`find`, `canEnter`, sight-line) ported.
- **Spawn engines** — `SpawnMonster`/`SpawnNpc` (spawn blocks, respawn intervals, despawn ranges) + `SpawnXmlLoader`.
- **Core outbound senders** — `GameWindowWriter`: stats/skills/inventory/container/text/add-creature/remove-tile-thing.
- **Lua player bindings** — +19 Canary primitives (guid/speed/capacity/mana/magic level/skills/sex/town/premium days/fight modes) backed by `WorldPlayerOperations`.
- **Missing-features tracker** updated with new coverage numbers.

### Phase 17: Player Model, Game Facade, More Senders & Lua Bindings (05:30 – 06:00)
- **Player model** — conditions attach/tick/expire (`addCondition`/`executeConditions`/`onThink`), skull/party/flags, magic level, weapon id.
- **Game facade** — `game/Game.kt`: `internalAddItem`/`internalRemoveItem`/`internalMoveItem`, `internalPlaceCreature`/`removeCreature`, `getPlayers`/`getPlayerByName`.
- **More outbound senders** — `CreatureWindowWriter` (health/outfit/speed/light/icons/magic-effect/…), `SocialWindowWriter` (trade/channels/VIP/houses/modal), `HighscoresWindowSerializer`.
- **Lua bindings** — +11 `Game.*` and +23 `item:*` Canary methods with real domain delegation.

### Phase 18: Party, VIP, Inbound Opcodes, Inventory Persistence (06:00 – 06:30)
- **Party system** — full `Party` (leader/members, invite/join/leave/revoke, leader transfer, shared-exp split, shields).
- **VIP system** — `PlayerVip` (entries/groups CRUD, status/icon, caps).
- **Inbound opcodes** — trade `0x7D–0x80`, follow `0xA2`, containers `0x75/0x87/0x88/0x95`, outfit/mount `0xD3/0xD4`, use-item-ex `0x83` decoded and routed.
- **Inventory persistence** — Flyway `V5__player_items.sql` + `JdbcPlayerItemsRepository` with Canary attribute serialization.
- **Process** — `AGENTS.md` §6 now mandates a pre-wave plan doc in `docs/development/` (moved to `docs/development/` after the wave).

### Phase 19: Player Components, Imbuements, Prey/Task Hunting, Opcode Batch 2 (06:30 – 07:00)
- **Player components** — `PlayerTitles`, `PlayerBadges`, `PlayerAchievements` (+ scoped `KV`), 82 tests.
- **Imbuement engine** — `Imbuements` XML registry + `ImbuementEngine` (apply/clear/scroll, tiers, decay, stat bonuses), 61 tests.
- **Prey + Task Hunting** — `PreyEngine` (slots, reroll, bonuses, time-left) + `TaskHuntingEngine` (tasks, points, kills, rewards), 60 tests.
- **Inbound opcodes** — party `0xA3–0xA8`, imbuements `0xB2`/`0xD5–0xD7`, depot/stash `0x28/0x29/0x92–0x94`, quick loot `0x8F–0x91`, modal `0xF9`, quest `0xF0/F1` decoded and routed.

### Phase 20: Monster Runtime, Monster Lua Bindings, Senders 3, Depot/Spells Persistence (07:00 – 07:30)
- **Monster targeting + loot** — `MonsterTargeting` (search/isTarget/isFriend/rank) + `MonsterLoot` (basis-point rolls, unique, corpse owner), 59 tests.
- **Monster Lua bindings** — `MonsterFunctions`/`MonsterTypeFunctions`/`LootFunctions` (61 Canary methods), 37 tests.
- **Outbound senders 3** — prey, task hunting, bosstiary, imbuement window, cyclopedia (30 tests).
- **Depot/spells persistence** — Flyway `V6__player_depot_spells.sql` + depot/inbox/spells repositories (shared attribute codec), 14 tests.

### Phase 21: Lua Item/Tile Bindings, Zone Engine, Spell Engine, Server Edge (07:30 – 08:00)
- **Lua item/tile bindings** — `ItemTypeFunctions`/`TileFunctions` + extended `ContainerFunctions`/`PositionFunctions` (all Canary names), 29 tests.
- **Zone engine** — `Zone`/`Zones` (areas, flags, membership, refresh), 27 tests.
- **Spell engine** — `SpellRegistry`/`SpellEngine`/`SpellBook` (runes, groups/cooldowns, learn/forget) + `AreaCombat`, 106 tests.
- **Server edge** — `Webhook`, `ProtocolProfile`, `SessionHint`, 36 tests.
- Zero-warning gate fixes (redundant `else`, unnecessary `!!`).

### Phase 22: `src/` Bootstrap Section Complete (08:00 – 08:30)
- **Service bootstrap** — `bootstrap/ServerBootstrap`/`ServerLaunch`/`Signals`/`CrashHandler`/`ShutdownDrain`; CLI (`ServerCli`: `--config`/`--data-dir`/DB flags) with CLI > file > env > defaults; idempotent ordered shutdown drain (listeners → dispatcher → SaveManager → DB).
- **Appearances protobuf** — `io/appearances/Appearances` + dependency-free `ProtobufWireReader` parsing the real `data/items/appearances.dat` (42,107 objects / 1,443 outfits / 242 effects / 62 missiles).
- **Database tasks** — `database/DatabaseTasks` (FIFO async queue, flush, idempotent shutdown).
- `docs/development/implementation-status.md`: `src/` bootstrap + `appearances.proto` rows flipped to **IMPLEMENTED**.

### Phase 23: `src/account/` Section Complete (08:30 – 09:00)
- **Account core** — `Account` auth (`authenticate`/`authenticatePassword`/`authenticateSession`), `load`/`reload`/`save`, account type, house bid, premium days, coin ledger; `AccountInfo`/`AccountType`/`AccountErrors`. 67 tests.
- **Account repository** — `JdbcAccountRepository` create/save, coin ledger + `coins_transactions`, ban/session tables, `loadBySession`; Flyway `V7__account_details.sql`. 14 tests.
- `docs/development/implementation-status.md`: `src/account/` rows flipped to **IMPLEMENTED**.

### Phase 24: `src/config/` Section Complete (09:00 – 09:30)
- **Config keys** — `config/ConfigKeys.kt`: 360 Canary keys with exact names, types, and defaults (double-entry verified against `configmanager.cpp`). 8 tests.
- **Typed ConfigManager** — `getBoolean`/`getNumber`/`getFloat`/`getString`, `reload`, OTC feature lists, injected source + precedence. 14 tests.
- `docs/development/implementation-status.md`: `src/config/` rows flipped to **IMPLEMENTED**.

### Phase 25: `src/creatures/` Part 1 — Creature Core, Outfits, Attached Effects, Chat (09:30 – 10:00)
- **Creature core** — outfit/light/speed, `onAttacked`/`onTarget`/`onDeath` hooks, master/summon, spectator `canSee`, creature icons. 29 tests.
- **Outfit registry** — `Outfits`/`Outfit`/`PlayerOutfits` from `outfits.xml` (addons, look-type lookup). 23 tests.
- **Attached effects** — `AttachedEffects` (aura/wing/shader/effect XML registry). 8 tests.
- **Chat channels** — `ChatChannel`/`PrivateChatChannel` registry, invites/users, muted checks, yell rules. 40 tests.
- `docs/development/implementation-status.md`: the four rows flipped to **IMPLEMENTED**.

### Phase 26: `src/creatures/` Part 2 — Conditions, Monster Runtime, MonsterType (10:00 – 10:30)
- **Conditions** — full condition family, attribute params, `ConditionFeared`, Player suppression. 42 tests.
- **Monster runtime** — spells, death/corpse, defense/reflect, random/dance step, walk-back, despawn (`MonsterBehavior`). 47 tests.
- **Combat intention + relevance** — `MonsterCombatIntention` + `MonsterRelevance`. 11 tests.
- **MonsterType schema** — full `MonsterType`/`MonsterInfo` (80 fields) + `Monsters` registry. 26 tests.
- Rows flipped: `combat/condition.cpp`, `monster.cpp`, `monster_pathfinding.cpp`, `monster_combat_intention.cpp`, `monster_relevance.cpp`, `monster_targeting.cpp`, `monsters.cpp`, `spawn_monster.cpp`.
- Fixed a post-merge `RaceType` redeclaration.

### Phase 27: `src/creatures/` Part 3 — NPC, PvP, Management, Stash/Vocations/Imbuements (10:30 – 11:00)
- **NPC runtime** — interaction/focus, walk-to/queue, shop buy/sell runtime + `Npcs` registry. 51 tests.
- **PvP** — `PlayerPvp` (situations/unjustified/skull/PZ lock) + `ExpertPvp`. 134 tests.
- **Management** — `Ban`/`IOBan`, `WaitingList`, `LivestreamManager`. 84 tests.
- **Stash/vocations/imbuements** — `Stash` + `Storages` XML, Vocation req/promotion/wheel-gems, `createScrollImbuement`. 37 tests.
- Rows flipped to IMPLEMENTED (NPC, PvP, ban/waitlist/livestream, stash, vocations, imbuements).

### Phase 28: `src/creatures/` Part 4 — Player Runtime 1, Storage, Forge History, Combat (11:00 – 11:30)
- **Player runtime 1** — condition integration/suppression, learned spells + spell/group cooldowns, PZ lock/in-fight, async task queue. 45 tests.
- **PlayerStorage** — delta/dirty/ingest lifecycle + reserved ranges. 19 tests.
- **Forge history persistence** — JDBC repository + Flyway `V8`. 10 tests.
- **Combat completion** — full `getCombatDamage` matrix, absorb/increase/mantra, target validation, combat areas. 30 tests.
- Rows flipped: `combat/combat.cpp`, `player_storage.cpp`, `player_forge_history.cpp`.

### Phase 29: `src/creatures/` Part 5 — Player Runtime 2, Spells/Mounts, Team Finder/Guild, Components (11:30 – 12:00)
- **Player runtime 2** — blessings + `getLostPercent`, container integration, harmony/serene. 76 tests.
- **Spells + mounts** — `playerSaySpell`/`canCast`/`CombatSpell`, mount outfit protocol + persistence + tame runtime. 71 tests.
- **Team finder + guild** — `TeamFinder`, guild ranks/MOTD/wars/emblems + JDBC repo (Flyway V9). 39 tests.
- **Components + WP gain + wheel spells** — `PlayerCyclopedia`, `PlayerAttachedEffects`, weapon-proficiency gain runtime, wheel spell-table registration. 84 tests.
- Rows flipped (spells, mounts, components, weapon_proficiency, wheel, team_finder, guild).

### Phase 30: `src/creatures/` Section Complete (12:00 – 12:15)
- **Player feature hooks** — `Player` now exposes wheel/forge/prey/task-hunting/cyclopedia hooks delegating to `PlayerFeatures`; prey/task state + engines, cyclopedia adapter, tracker seams. 16 tests.
- `docs/development/implementation-status.md`: `player.cpp` flipped to **IMPLEMENTED** — the entire **`src/creatures/` section is now IMPLEMENTED** (0 PARTIAL/MISSING).

### Phase 31: `src/database/` Section Complete (12:15 – 12:45)
- **Database core** — `Database` (transactions, escaping, retry, recoverable errors, `MAX_QUERY_SIZE`, `DBInsert`). 27 tests.
- **Database manager** — pool lifecycle/reconnect + `executeQuery`/`storeQuery` parity + `DBResult`. 17 tests.
- **Definitions** — `TransactionStates_t`. 2 tests.
- `docs/development/implementation-status.md`: `src/database/` section added and flipped to **IMPLEMENTED**.

### Phase 32: `src/enums/` Section Complete (12:45 – 13:00)
- **Container/object categories** — `ContainerSpecial_t` + `ObjectCategory_t` with exact values. 3 tests.
- `docs/development/implementation-status.md`: `src/enums/` section added; all rows **IMPLEMENTED**.

---

### Phase 33: `src/game/` Section + Protocol Opcode Completion (13:00 – 14:30)
- **Game core** — `Game` class core (creature placement/removal, internal item add/remove/move, broadcast, lights/sounds, `playerStopAutoWalk`). 20 tests.
- **Movement & modal** — `Position` (offsets/distances/rectangles), `Teleport` (dest rules/effects), `ModalWindow` manager. 31 tests.
- **Scheduling** — WDRR fairness/budgets/telemetry, `EventsScheduler`, `MonsterComputeService`, `SaveManager` save-all/shutdown. 77 tests.
- **Game reload** — `GameReload` generation-checked runtime reload. 20 tests.
- **Inbound opcodes complete** — all **141/141 active** opcodes now decode; wave 35 added the final 64 across 4 batches (party analyzer/team finder, client options, extended opcode, channels, item interaction, text/house windows, bosstiary, highscores, weapon proficiency, task hunting, forge history, container browse/seek, inspection, VIP, cyclopedia, prey, hireling, resource balance, greet, object info). ~180 new tests.
- **Outbound senders** — trade/channel/house/VIP/store/inspection/blessing/stash/cyclopedia writers added. 63 tests.
- Verified per-module coverage percentages added to `docs/development/implementation-status.md` §1.
- **689 suites / 7620 tests, all green.**

---

### Phase 34: `src/io/` Section Complete (14:30 – 15:30)
- **iologindata** — 24/24 methods: auth/GUID/bank, load/save, inventory/depot/inbox/kills/stash/rewards/storage, VIP entries+groups. Migration V10. 78 tests.
- **iomapserialize + iomap** — house items/info, guards, tile/container/item serialization; houses/monsters/npcs/zones XML nodes. Migration V11. 54 tests.
- **iomarket + ioprey** — offers/history/statistics/expiration + prey/task-hunting persistence. Migration V12. 36 tests.
- **iobestiary + io_bosstiary + ioguild** — charms/kill counts/tracker/animus, boss points/slots/boosted boss, guild load/save. Migration V13. 32 tests.
- **player_storage_repository** — typed repo (load/deleteKeys/bulk upsert). Migration V14. 14 tests.
- **689 suites / 7620 tests, all green.**

---

### Phase 35: `src/items/` Section Complete (15:30 – 17:00)
- **Item core** — decay/forge/description primitives, weapon predicates, serialization, owner/tier/stats (56 tests).
- **Thing/Cylinder/TrashHolder + Tile** — full contracts, flags/PZ, ordering, cylinder queries, notifications (94 tests).
- **Decay + Bed** — bounded-interval decay pipeline with generation-gated stale-callback safety; sleep/regen (54 tests).
- **Containers** — Container/DepotChest/DepotLocker/Inbox/Mailbox, town mapping, mail routing (73 tests).
- **Item functions + weapons** — custom attributes, 69 XML `parse*` methods, weapon registry/damage formulas (64 tests).
- **Items registry** — protobuf merge, augments, loot types, reload (46 tests).
- 6 parallel branches integrated via a dedicated semantic-merge pass (Item.kt 6-way conflict resolved).
- **689 suites / 7620 tests, all green.**

---

### Phase 36: `src/map/` Section Complete (17:00 – 18:00)
- **Map core** — canThrowObjectTo, canWalkTo, checkSightLine/isSightClear, getPathMatching, placeCreature, moveCreature, save (47 tests).
- **Caches/navigation/download** — BasicItem/BasicTile dedup, nav snapshots, release-tag compare (78 tests).
- **Spectators** — sets, direction/multifloor filtering, cache (24 tests).
- **Houses** — rent cycle, transfers, auctions, access lists, eviction, depot transfer (71 tests).
- **A*/towns** — AStarNodes open/closed sets, Town/Towns registry (20 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 37: `src/lua/` Part 1 — Core Bindings (18:00 – 19:30)
- **MonsterType** — 101/101 bindings (flags, numerics, immunities, events, summons, sounds, bestiary/bosstiary fields).
- **Game** — 59/59 (createItem/Monster/Npc/Tile/Container/Teleport, achievements, raids, guild, bestiary catalog).
- **Item** — 55/55 (attributes, owner, tier, shader, imbuement slots, serialization).
- **ItemType** — 58/58 + **Tile** — 32/32.
- **Player** — 114/305 (vitals/skills/inventory/money/conditions + social/party/guild/modal/UI/house/account/kills/vocation).
- **689 suites / 7620 tests, all green.**

---

### Phase 38: `src/lua/` Part 2 (19:30 – 21:30)
- **Player** — batches 3+4: combat/wheel/forge/bosstiary/prey/task/achievements/badges/titles/loyalty/harmony/store/icons/livestream/outfits (~277/305 total).
- **Monster/NPC** — monster (54), npc (27), npc_type (39), monster_spell (23), charm (13), shop (9).
- **Weapon/Spell/Combat** — weapon (31), spell (38), combat (7), condition (13), variant (3).
- **Libraries** — db/kv/logger/metrics/result/network-message/webhook/bank/batch/config/global/modal/zone + Lua enums (~137).
- **Map/Items** — house (25), town (3), teleport (2), imbuement (8).
- **Creatures** — group (7), guild (10), mount (4), party (16), vocation (21).
- **Events** — 46 registrations across action/creature_event/event_callback/scheduler/global/move/talk.
- 8 branches integrated (WorldLuaOperations wiring union; 29 duplicate player registrations deduped).
- **689 suites / 7620 tests, all green.**

---

### Phase 39: Small Sections Sweep + Lua Completion + `game.cpp` (21:30 – 23:30)
- **KV engine** — KV/KVStore/ScopedKV, KVSQL (V16), ValueWrapper + `kv.proto` typed values (75 tests).
- **Lib** — DI container, leveled logger, message bus, thread pool, metrics (58 tests).
- **Utils** — 68 `tools.cpp` helpers, WildcardTree, BatchUpdate + Lua binding (68 tests).
- **Server edge** — connection state machine/transport codec, signals + shutdown drain, webhook, protocol profile/session hints (52 tests).
- **Lua complete** — `player_functions` 305/305, `modules` recvbyte, +645 enum constants; **all 1,246 method bindings registered**.
- **Game remainder** — trade/shop/party/guild/VIP/reports/highscores/`checkCreatures` (87 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 40: PARTIAL Rows + Sender Campaign (23:30 – 01:00)
- **game.cpp houses/auctions** — `playerCyclopediaHouse*` (8), `processBankAuction`, `wrapItem`/`unwrapItem`, house-item transfer, async highscores DB, MOTD (56 tests).
- **Lua sandboxing** — `LuaScriptInterface`/`ScriptEnvironment` (protected calls, class registration, environments pool, reload, error reporting) (65 tests).
- **Opcode application** — trade (0x7D–0x80), containers (0x87/0x88), follow (0xA2), quick-loot fallback (0x90/0x91), depot-search close (0x93), imbuement close (0xD7), market leave (0xF4), quest-log Lua callbacks (0xF0/F1) now apply real engine state (22 tests).
- **Outbound senders** — creature/map/tile writers (move/turn/say/helpers, map description, add/update tile item), player/UI writers (tutorial, cancel target/walk, fight modes, cooldowns, chat/text windows), cyclopedia computation helpers (64 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 41: Production Wiring + Depth (01:00 – 03:00)
- **Game wiring** — `CanaryServer`/`GameHandler` own `Game`, register players on login, pass it into the router context; fixed a buffer-lifetime bug that silently dropped routed packets (2 integration tests).
- **Opcode application** — depot search open/item request (0x92/0x94), managed containers (0x90), outfit (0xD3), imbuement PickItem (0xB2) (16 tests).
- **Senders** — 34 misc senders (session/data/effects): re-login, FYI box, bug report, PvP situations, basic data, sounds, shaders, attached effects (53 tests).
- **game.cpp families** — movement/combat/targeting (19), market (5), quest/forge/bosstiary/task (8), player handlers (9) (95 tests).
- **Player depth** — 68 methods (action timers, walk state, vitals, outfit, combat, social, UI, town) → 479/821 raw (78 tests).
- **Monster depth** — full targeting/combat/walk/summon/respawn; 89 name-matched methods (142 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 42: Sender Completion + Application + Depth (03:00 – 05:00)
- **Senders 100%** — 187/187 `ProtocolGame::send*` ported (party, podium, depot, container items, login/OTC, loot, team-finder, outfit window, forge/gem/livestream/emblem/kill-tracker).
- **Opcode application complete** — stash stow/withdraw (0x28), depot retrieve (0x29), corpse/nearby loot (0x8F), imbuement apply/clear (0xD5/0xD6), market create/cancel/accept (0xF6–0xF8), parent container (0x95); router market wired to `Game`.
- **game.cpp** — market offers (create/cancel/accept), `loadItemsPrice`, forge fuse, wheel handlers, NPC greet, modal answer, monster podium (79 tests).
- **Player** — `Player::death`, walk queue, `canWalkthrough`, scheduler variants → 496/821 raw (71 tests).
- **Monster async** — real target-search/combat-intention/follow-path/post-think/movement/visibility pipelines with generation safety (87 tests).
- **Lua class registry** — bindings migrated to Canary's registration flow + 106 sound enum names (7 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 43: Host Wiring & Composition (05:00 – 07:00)
- **game.cpp** — show-off socket, fiendish/influenced forge (10 methods), wheel combat application (2) + `PlayerWheel` accessors (15), monster registry (3) (69 tests).
- **Monster host** — pipelines instantiated (`MonsterPipelinesHost`), `trySchedulePostThink`, spectator scan, appear/remove wiring, post-think queues, lateness telemetry (17 tests).
- **Player host** — `death`/`onWalk`/`goToFollowCreature` overrides, walk queue → walk event, scheduler variants, accessors (manaSpent/levelPercent/damageMap/lastLogout/skillLoss) (7 tests).
- **Sender wiring** — `ProtocolSink` + `NettyProtocolSink`; creature/item/container/stats/cancel state changes emit frames (29 tests).
- **Runtime composition** — stash unified on `Player.stash`, item custom-attribute store + imbuement accessors, `ImbuementItem` over real items, `GameHandler` context with `ioMarket`/`itemTypes`/`imbuements`, market escrow (30 tests).
- **Player depth 2** — XP boosts, pronouns, loyalty, client/session, stats → 538/821 raw (47 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 44: Combat Pipeline & Plumbing (07:00 – 09:00)
- **Combat pipeline** — `combatChangeHealth`/`combatChangeMana`/`applyHealthChange`/Gift-of-Life/leech/PvP damage/spectator messages (35 tests); channels (12); host seams bound (`checkCreatures`→post-think, walk seams) (16).
- **Player depth 3** — equipment/attack/damage-reduction/capacity/magic-affinity states → 596/821 raw (66 tests).
- **Composition plumbing** — bootstrap loads appearances/items/imbuements into `GameHandler`; `CanaryServer` composes `IOMarket` + escrow; `Player.getImbuingItem` (8 tests).
- **Monster type state** — forge/hazard/soul-pit, attack/defense spells, strategies, `canUseAttack`/`castSpell` (23 tests).
- **Map spectator sectors** — 16×16 sector lists + `Map::moveCreature` fanout emits frames (13 tests).
- **Imbuement decay** — Maintenance-lane tick, generation-checked, registry validation (16 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 45: Game Remainder 2 & Creature Hooks (09:00 – 11:00)
- **game.cpp** — quick-loot/managed containers (15), say family (6), updaters/effects (22), records/helpers (13), seam wiring to map/monster (58 tests).
- **Creature hooks** — `onCreatureMove`/appear/remove virtuals + Player/Monster overrides, host states composed (action timers, town, ghost mode) (30 tests).
- **Player depth 4** — spell cooldown, container/UI, interaction, light, race icons → 648/821 raw (90 tests).
- **Imbuement composition** — dispatcher-backed decay engine at server root, registry assignment, lifecycle drain (5 tests).
- **Lua item attributes** — custom-attribute bindings + imbuement scroll/clear wired to real stores (10 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 46: Game Remainder 3 & Composition (11:00 – 13:00)
- **game.cpp** — stash/depot-search (9), item batch (6), use-item/container (11), cyclopedia helpers (26) → ~39 members left (120 tests).
- **Player depth 5** — inventory weight, exiva restrictions, `saySpell`, kill tracking (bestiary/bosstiary/hunting), `onKilledMonster`/`onKilledPlayer`, reward chest, async follow pipeline (50 tests).
- **Monster composition** — `checkCreatureAttack`/extra-swing/post-think/onThink_async/idle/navigation bound to host; lane hops (12 tests).
- **Item registry hub** — imbuement registry at `Item.create`/OTBM map load/`IOMapSerialize`/loot (10 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 47: Final Game Members & Login Items (13:00 – 15:00)
- **game.cpp final batch** — movement handlers (7), item interactions (8), reward chest (2), cyclopedia registry (4), `applyCharmRune`, startup/misc (11) → 7 members left (122 tests).
- **Player depth 6** — damage accuracy, cleave, perfect shot, reflect, active foods, message buffer, client icons → 672/821 raw (27 tests).
- **Composition final** — imbuement registry at `GameHandler` item creation; `Creature` async-batch `WorkerCompletion` hand-back; `Monster.onExecuteAsyncTasks` host seam (6 tests).
- **Login item materialization** — `IOLoginData.loadItems` + inventory/depot/inbox rebuild + `ATTR_CUSTOM` codec (16 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 48: Final Game Members & Nested Inventory (15:00 – 17:00)
- **game.cpp complete** — the final 7 members (`combatBlockHit`, `playerCyclopediaCharacterInfo`, map loaders, `loadAppearanceProtobuf`, `loadBoostedCreature`); **every real `Game::` member is now ported** (44 tests).
- **Player depth 7** — attack totals, augments, forge history, item abilities, quick loot, bosstiary points/slots, cyclopedia trackers → 710/821 raw (70 tests).
- **Host composition 3** — monster `asyncTaskDispatcher`/`onExecuteAsyncTasksHost` bound; `PlayerFeatures` composes combat bonuses/foods/message buffer (9 tests).
- **Nested inventory persistence** — `player_inventory_items` gains `pid`/`sid` (V17); nested container trees materialize (4 tests).
- **689 suites / 7620 tests, all green.**
- **The tracked inventory is now 100% IMPLEMENTED: 115/115 rows, 0 PARTIAL, 0 MISSING.**

---

### Phase 49: Depth Closure (17:00 – 19:00)
- **Player depth 8** — item summaries, skills equipment, description, shared-exp hooks, equip sweeps, Bakragore icons, party/impact/input trackers → 733/821 raw (51 tests).
- **Host bindings** — item abilities/boss points/cyclopedia trackers/aim-at-target/magic affinity composed into `PlayerFeatures`; cyclopedia router delegation; message buffer wired; imbuement adapter stats live (12 tests).
- **Persistence depth** — live inventory save walk (BFS `sid` from 101) + `OPENCONTAINER` re-arming (4 tests).
- **Barrier lanes** — 32 shared creature async buckets, slice budgets, promotion, requeue, rejection rollback, barrier-parallel fan-out (12 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 50: Composition One-Liners & Dispatcher Depth (19:00 – 21:00)
- **Composition liners** — `playerSay`→message buffer, aim-at-target router (0xC8), charged-item `transformItem`, `MAX_MESSAGEBUFFER` config, non-monster async buckets at login/placement; fixed a tile-insert ordering defect (14 tests).
- **Player depth 9** — setAccount, protection, dead state, coins, bed, VIP lists, disconnect, loot containers → 747/821 raw (22 tests).
- **Persistence guard** — `savePlayerGuard` now saves inventory+depot+reward+inbox live walks with reward expiry/order parity (5 tests).
- **Dispatcher depth** — serial-lane admission, adaptive budget refresh (250 ms), `safeCall` barrier continuation, `observeInternalWork` gating (11 tests).
- Fixed an order-dependent race in `ImbuementDecaySchedulerTest` (capture generation before advancing the clock).
- **689 suites / 7620 tests, all green.**

---

### Phase 51: Composition Root & Final Depth (21:00 – 23:00)
- **Composition root** — world-shared `muteCountMap`/`transformItemCharges`, shared async buckets, `CanaryServer` config + queue-latency telemetry, `Player::depotChests` (8 tests).
- **Player final depth** — loot message, market leave, party icons, prey/task data, tile sends; 38 seam-only `send*` methods verified against writers (15 tests).
- **Persistence final** — stash/bestiary/prey/task-hunting saves in `savePlayerGuard` + shared `PlayerSaveTransaction` with rollback (3 tests).
- **Dispatcher final** — periodic latency/runtime telemetry logging, bucket→`observeInternalWork`, suspension-safe context propagation (12 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 52: Seam Binding & Composition Details (23:00 – 01:00)
- **Seam binding** — all 38 `Player::send*` seams bound via `PlayerSendSink` with state-change call sites (53 tests).
- **Composition root 2** — `ServerBootstrap` config/buckets/compute into `CanaryServer`, production `MonsterPipelinesHost`, depot-chest persistence walk, root save transaction (9 tests).
- **Component accessors** — `achiev()` + faithful `AnimusMastery` component with serialize codec composed into `PlayerFeatures` (29 tests).
- **Monster async final** — `safeCall` continuation-lane hop, lateness → `observeInternalWork` (6 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 53: Live Transport & Login Integration (01:00 – 03:00)
- **Sink transport** — `NettyProtocolSink` implements the full `PlayerSendSink` surface over the live channel (5 tests).
- **Monster registration** — `internalPlaceCreature`/`removeCreature` register/unregister pipelines with the type provider (6 tests).
- **Lua delegation** — achievement/animus bindings delegate to the composed components (3 tests).
- **Login load** — `GameHandler` loads the persisted player via `IOLoginData.loadPlayerById` (V18 skill columns) (4 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 54: Registry Injection, Spawn Path, Persistence Sub-Loads (03:00 – 05:00)
- **Registry injection** — `Monsters`/`Vocations`/`IOLoginData` composed from `ServerBootstrap` into `CanaryServer`/`GameHandler` (5 tests).
- **Spawn path** — production `MonsterSpawnPath` + `GameMonsterFactory` place spawn monsters on boot and respawn (8 tests).
- **Persistence sub-loads** — conditions/skull/guild/storage/VIP/exiva (V19) and bestiary charms/store inbox/reward chest/prey/task (10 + 17 tests).
- **Lua extra-ops** — badges/titles/loyalty/concoction/WOD/OTCR delegate to composed components (5 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 55: Final Depth Polish (05:00 – 07:00)
- **Monster types from the datapack** — boot replays Canary's module order and registers **67/67 monster types** via the real Lua path (`MonsterType:register`), with the full field mapping (immunities, spells, voices, summons, bestiary/bosstiary, flags, sounds) (18 tests).
- **Persistence polish** — outfit sub-load; `Reward : Container` shape with prepend + DATE attribute (6 tests).
- **Wheel APIs** — spell upgrades, additional area/target/duration, scrolls, `onThink` checks, specialized magic (26 tests).
- **Storage composition** — `PlayerStorage` over composed outfits/familiars, attached-effects storage, host title/badge reads, animus blob persistence seam (19 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 56: Combat Actor & Codec Fidelity (07:00 – 09:00)
- **CombatSpell actor** — full `Monsters::deserializeSpell` (combat type/effect/shoot survive; `.spell` exposed), nested `childLoot`, inline `onSpawn`/`onThink` callback capture + dispatch (21 tests).
- **Host/Lua unification** — `Player.storage()`/`getOutfits`/`getBankBalance`; Lua storage/outfit shadows removed; animus blob persistence wired (19 tests).
- **Known-creature set** — 1300-entry `checkCreatureAsKnown` with eviction/canSee rules; known frames in party/creature sends (12 tests).
- **Codec fidelity** — condition flat stats/persistence/interval round-trip; `lookmount*`/`lookfamiliarstype` columns (V20) (5 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 57: Event Dispatch, Condition Save, Visibility (09:00 – 11:00)
- **Monster event dispatch** — appear/disappear/move/say/player-attack dispatch; late `registerEvent` reaches live monsters; fixed a real per-type script-interface collision bug (13 tests).
- **Compiled spell registry** — `getSpellByName` registry hit in `deserializeSpell` (3 tests).
- **Condition save** — `Condition::serialize` write path; single-column animus (V21); storage persistence (18 tests).
- **Visibility/ghost** — `Player::canSeeCreature` override, `sendCreatureAppear` stackpos fallback, square canSee gate, `sendUpdateTile` gate (6 tests).
- **689 suites / 7620 tests, all green.**

---

### Phase 58: Repository Improvements (11:00 – 13:00)
- **Path hygiene** — 57 files swept to relative `../.projects/canary` references; AGENTS.md/template now enforce the convention (`bd9015e`).
- **Fork branding** — README/status/webhook point at `gdamoreira/canary-kotlin`; MapDownload keeps the upstream artifact URLs annotated (`e64b190`).
- **RSA configuration** — `CANARY_RSA_P`/`CANARY_RSA_Q` override the built-in OpenTibia pair; invalid values fail the boot fast (7 new tests, `57beea9`).
- **Documentation port** — full 45-file port/adaptation of `canary/docs` with a new docs hub, Gradle build guides and JVM-adapted architecture/operations docs (`10d53b5`).
- **README + governance** — ToC and all 12 requested sections; MIT `LICENSE` (user decision), `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md` (`aa85506`).
- **CI/CD** — GitHub Actions build/test pipeline (JDK 25) and Docker build/compose validation (`29b09f0`).
- **Datapack** — `data-otservbr-global` copied verbatim (4757 files, 38 MB; Lua stays Lua) (`ca3e768`).
- **Docker delivery** — read-only data mounts, server healthcheck, `.env.dist`, `docker/DOCKER.md`, guarded `up.sh`/`up.ps1`, `.dockerignore` (`637db6f3`).
- **690 suites / 7627 tests, all green.**

---

### Phase 59: CI Heap Fix & Documentation Reorganization (13:00 – 14:00)
- **CI heap fix** — `gradle.properties` pins `org.gradle.jvmargs=-Xmx2g` and `kotlin.daemon.jvmargs=-Xmx3g`, fixing the `:compileKotlin` OutOfMemoryError on GitHub Actions (`d64d4b8a`).
- **Docs reorganization** — `docs/Plan`, `docs/Done`, `docs/Implementing` moved to lowercase `docs/development/{plan,implementing,done}`; the pre-port top-level docs (missing-features, game-play-features, improvements, TEMPLATE_FEATURE, canary-codebase-implementation-audit) moved into `docs/development/`; `docs/superpowers/` removed; all references (66 files) swept.
- **690 suites / 7627 tests, all green.**

---

### Phase 60: Lua Config Execution (14:00 – 15:00)
- **`config.lua` startup path** — `ConfigSource.ofLua` executes a `.lua` `--config` file through LuaJ and reads the `ConfigKeys` globals plus the `OTCRFeatures` table, matching `ConfigManager::load` (configmanager.cpp:26-417); `ServerConfig` resolves `serverName`/ports/`dataPackDirectory` from the same globals (9 new tests, TDD).
- **Template shipped** — byte-identical `config.lua.dist` at the repository root; local `config.lua` git-ignored like upstream.
- **CI heap fix verified** — GitHub Actions CI and the Docker image build both green after the `gradle.properties` daemon-heap pinning.
- **690 suites / 7636 tests, all green.**

---

### Phase 61: Executable-Relative Paths & Distribution Bundling (15:00 – 16:00)
- **AppPaths** — `config.lua` and the `data`/`data-canary`/`data-otservbr-global` folders resolve from the executable home (`APP_HOME` or the JAR location); `installDist` bundles them so the distribution is self-contained (verified: 67 monster types + listener on 7172 running from `/tmp`).
- **Lua require shim** — directory-prefixed `require(DATA_DIRECTORY .. ".lib.core.quests")` no longer breaks on dotted install paths.
- **693 suites / 7648 tests, all green.**

---

### Phase 62: Docker Folder & Metrics Delivery (16:00 – 18:00)
- **Docker folder ported** — canonical `docker/docker-compose.yml` (root compose now includes it), `.env.dist`, `config.sh`, adapted PostgreSQL test-account dumps + `data/start.sh`, `Dockerfile.dev`/`.x86`/`.arm`, guarded `up.sh`/`up.ps1`, `DOCKER.md`, MyAAC reference files; upstream extras (MyAAC/login-server) documented as not applicable.
- **Metrics stack** — `metrics/` folder ported and the Prometheus scrape endpoint implemented (`PrometheusEndpoint`, `metricsEnablePrometheus`/`metricsPrometheusAddress`), wired into the boot (5 new tests).
- **PostgreSQL portability fix** — V1/V2 used H2-only `MERGE ... KEY` syntax that cannot run on real PostgreSQL; rewritten to portable SQL so the Flyway chain works on both engines.
- **694 suites / 7653 tests, all green.**

---

### Phase 63: Development Docs Aggregation (18:00 – 19:00)
- **`docs/development/` flattened** — the `plan/`/`implementing/`/`done/` lifecycle folders were removed; agents create documents directly in `docs/development/` (AGENTS.md §5/§6 updated).
- **History aggregated** — 64 feature specs → `feature-history.md`, 41 wave plans → `wave-history.md`, the three `000-*` plan docs → `architecture-and-roadmap.md`; a folder `README.md` documents the conventions; all references swept repo-wide.
- **694 suites / 7653 tests, all green.**

---

### Phase 64: Status Documents Merged (19:00 – 20:00)
- **Single status document** — `missing-features.md`, `game-play-features.md`, `canary-codebase-implementation-audit.md` and `improvements.md` merged into `docs/development/implementation-status.md` (parity inventory, 56-feature matrix, codebase audit, improvement tracker; content preserved with an index).
- **References swept** — AGENTS.md §6, README, docs hub, development.md and the history aggregates now point at the merged document.
- **694 suites / 7653 tests, all green.**

---

### Phase 65: Wave 63 — Parity Depth (20:00 – 22:00)
- **Event fanout & monster speech** — say/yell spectator fanout reaches `Monster::onCreatureSay`; per-creature named `CreatureEvent` validation; whisper window corrected to the C++ 8x6 client viewport (11 tests).
- **Combat identity & spell execution** — `onAttackedByPlayer` from spell damage, black-skull mana halving, `valueCallback` min/max, `canDoCombat` usage, `applyInstantSpellDirection`, and a Lua `onCastSpell` runner for registry-hit monster spells (32 tests).
- **Condition payload fidelity** — class-level `Condition::serialize` family ported: flat skills/stats/buffs/absorbs/increases/charm + tick/add sounds round-trip (26 tests).
- **Visibility & viewport** — `Player::canSee(pos)` client-window override, `sendCreatureTurn` gate + stackpos fallback, in-memory sink fallback parity, Tile ghost comments resolved (10 tests).
- **Composition wiring** — production sub-load repositories + `Player::isOffline` session gate wired through `ServerBootstrap`; V22 drops the interim animus table (6 tests).
- **Item look-at text** — `Item::getDescription` renderer ported verbatim (article/plural, weight, charges, fluids, duration, distance rules) + LookPacketHandler wiring (43 tests).
- **702 suites / 7781 tests, all green.**

---

### Phase 66: Wave 58 Recovery — Ancestry Audit (22:00 – 23:00)
- **Silent merge failure discovered** — the wave-58 merge loop only landed `combat-spell-actor`; `host-lua-unification`, `known-creature-set` and `codec-fidelity` were recorded as done but never became ancestors of `main`.
- **Recovered and merged** — known-creature set (1300-entry session cache + known/unknown frames), host/Lua unification (`Player.storage()`/`getOutfits`/`getBankBalance`, Lua delegation) and the condition/outfit codec (flat records, mount/familiar look columns) with conflicts resolved against the newer wave-59/63 designs; V20/V21/V22 keep the single-column animus layout.
- **706 suites / 7817 tests, all green** (+36 tests recovered).
- **Process note**: future wave merges verify every agent commit with `git merge-base --is-ancestor` before recording completion.

---

### Phase 67: Wave 64 — Parity Tails (23:00 – 01:00)
- **VIP/Exiva composition** — `PlayerVip` composed + full kv `ValueWrapper` store; `loadPlayerVip`/`loadPlayerExivaRestrictions` wired; `Monsters::getMonsterTypeByRaceId` ported for bestiary race ids (7 net tests).
- **Guild/save registration** — guilds loaded at login register into `Game.guilds` (shared instance); `SaveManager.registerOnlinePlayer`/`unregisterOnlinePlayer` wired at session install/removal with a stale-session identity guard (5 tests).
- **Combat host wiring** — production `CreatureCombatTarget` adapter (`onPlayerAttack` from spell damage), `canThrowSpell` range/sight, `CombatFunc` area-dispatch tail, skill operand push, `getAimAtTargetSpells` (36 tests).
- **Events/transport** — `ScriptingDispatch` per-creature filtering + registry validation, boot injection of the event seam, spawn-path `onSpawn`, `Game.sendCreatureTurn` transport + base appear fallback (10 tests).
- **Renderer seams** — look/shop renderer seams, Lua `item:getDescription`, plural `Item::getDescriptions` (18 tests).
- **Tile.queryAdd** — full creature branch (monster push/fields, PZ-lock/account rules, expert-PvP step, `isMoveLocked`) (12 tests).
- **718 suites / 7905 tests, all green**; all six branch commits verified as ancestors of `main`.

---

### Phase 68: Wave 65 — Composition Bindings (01:00 – 03:00)
- **Composition bindings** — `Monsters` into the login path (bestiary race ids), VIP repository injection, `Player.kv` scoped-store rebind, Tile queryAdd seams, `Game.saveGuild`, save-all guild/KV savers, modal scripting, player-side event attachment (9 tests).
- **VIP protocol** — `sendVIP`/`sendVIPGroups`/`sendUpdatedVIPStatus` on the sink (0xD2/0xD3/0xD4) with `PlayerVip` wired to the transport (9 tests).
- **Spell registry boot** — engine `Spells` composed and populated from datapack scripts; `sb.spell` resolves compiled actors at boot (8 tests).
- **Lua spell entry points** — `combat:execute`, `doTargetCombat*`, area registry and the `canThrowSpell` gate route through the wave-64 combat core (20 tests).
- **Shop/inspection** — opcode `0x79` wired to `playerLookInShop`; `playerInspectItem` + the 0x76 inspection window consuming plural descriptions (8 tests).
- **CI hardening** — `ImbuementDecaySchedulerTest` race fixed with a deterministic barrier; Actions upgraded to v5; `runs-on` pinned to `ubuntu-24.04`.
- **729 suites / 7958 tests, all green**; all six commits ancestry-verified.

---

### Phase 69: Wave 66 — Persistence Save Tails (03:00 – 05:00)
- **Logout tail** — disconnect routes through `removeCreature(isLogout=true)`: guild `removeMember`, party leave, save-once; relogs no longer duplicate online members (8 tests).
- **Exiva save** — `savePlayerExivaRestrictions` ported into `savePlayerFirst` (flags + whitelists, write-before-record ordering) (4 tests).
- **Guild removal + map saver** — emptied guilds erase from `Game.guilds` via the repo-installed seam; `SaveManager.mapSaver` bound (5 tests).
- **Player group** — `player.group` resolved from the account's `group_id` through the `groups` seam at login (4 tests).
- **733 suites / 7979 tests, all green**; all four commits ancestry-verified.

---

### Phase 70: Wave 67 — Combat Depth, Monster Tails, Login Bindings (05:00 – 07:00)
- **Combat depth** — ExpertPvp wall swaps + fight-tick, beam-mastery seam, per-creature `getCombatDamage`, `applyExtensions` copy semantics, mantra absorb, monster combat-target state (immunity bitset/enemy faction/`getCombatValues`), `PlayerSpellCaster` mana progression + cancel sink + aggressive clock (23 tests).
- **Monster tails** — `Monster::isPushable` live in both push paths, think/death attached-event loops, spawn order fixed to the C++ call points, Lua `creature:isMoveLocked`/`setMoveLocked` (13 tests).
- **Login bindings** — player placement at login with the `addList` fanout, login VIP burst, `Groups::load` (XML) + boot binding, guild repository + `savePlayer`/`playerVip` bindings (16 tests).
- **Spell consumers** — registry re-binds (`canLearnSpell`/`getInstantSpells`/wheel/basic-data/rune look), `attachCombat` boot call, modal sink + 0xF9 route, shop-owner composition, Lua `player:registerEvent` (10 tests).
- **748 suites / 8041 tests, all green**; all commits ancestry-verified.

---

### Phase 71: Wave 68 — Minor Seam Bindings (07:00 – 09:00)
- **Event/modal/combat-state** — think/death Lua loops bound at the root, per-session modal windows (0xFA/0xF9 round trip), `attachCombat` production state/effects (8 tests).
- **Combat/group/player** — wheel beam-mastery adapter (+ Lua decorator call site), `combatTargetOf` adopts the host state, `Player.hasFlag` reads `Group.flags`, login fills `Player.spells` (22 tests).
- **Protocol** — sale-item-list frames, weapon-proficiency window (0xC4), self-appear add-creature branch (0x17) + stats/skills login continuation (9 tests).
- **Persistence/guild** — `savePlayerSystems` (+ read-back), guild helper broadcasts via the repo seam (bound at the root), exiva guid uint32 wrap (11 tests).
- **758 suites / 8091 tests, all green**; all commits ancestry-verified.

---

### Phase 72: Scaling & Multi-World Analysis (09:00 – 10:00)
- **Analysis document** — `docs/development/scaling-and-multi-world.md`: login/game service split, horizontal scaling ladder (one-world-per-instance vs zone sharding), the role of Redis (coordination, not world state), and the multi-world plan (schema, routing, alternatives). No code changes.

---

### Phase 73: Wave 69 — Runtime Tails (10:00 – 12:00)
- **Game loop driver** — `scheduleEvent`/`cycleEvent` bound to the dispatcher (lane-aware), `Game.start()` one-shot after listeners; the production loop now drives forgeable/influenced monsters, light, `checkCreatures`, Lua GC, market prices and players-online (9 tests).
- **Monster hosts + death payload** — hosts bound in `internalPlaceCreature` (summons/raid/Lua shape); the six-argument `onDeathEventsHost` threads corpse/killer/most-damage/unjustified through one unified Lua `onDeath` path (8 tests).
- **Login/protocol tails** — full self-appear tail (speed doubles, expert byte, store URL/coin packet, exiva byte), login continuation frames, `0xB3` tracked-weapons windows, proficiency detail-list gate, shop sale-list refresh on state updates (16 tests).
- **Misc bindings** — Gift-of-Life cooldown binding, real `getPlayerHelpers` count, guild-broadcast decision documented (3 tests).
- **766 suites / 8127 tests, all green**; all commits ancestry-verified.

---

### Phase 74: Wave 70 — Engine Internals & Remaining Tails (12:00 – 14:00)
- **Death/corpse pipeline** — `Creature::onDeath` damage-map resolution, corpse creation, loot/experience on death, per-monster death host (15 tests).
- **Cylinder producers** — `postAddNotification`/`postRemoveNotification` + the inventory-change → `updateState` → sale-refresh chain (26 tests).
- **Lua createMonster + raid bridge** — production create/place/onSpawn chain bound at the root; `GameRaidSpawner`/`GameRaidMonster` over real placement (12 tests).
- **Protocol/dispatcher tails** — old-protocol `0x94` helpers, `0xCA` exiva frame, `0xB3` perk actions + 0x5C, login bug-report/version gates; `stopEvent` executing-cycle fix (20 tests).
- **779 suites / 8200 tests, all green**; all commits ancestry-verified.

---

### Phase 75: Wave 71 — Combat/Death Tails, Producers, Raids (14:00 – 16:00)
- **Damage attribution** — attacker threaded through the combat drain; combat kills populate the damage map (last-hit/most-damage, summon/master credit) (7 tests).
- **Death tails** — Lua `onKill` host (+ monster binding at the root), player corpse with the kill description, `dropCorpse` protocol tail (reward gate, loot message, auto-loot, container update), `MESSAGE_EXPERIENCE_OTHERS`, death counters wired to the shared registry (24 tests).
- **Producer/transport** — source-cylinder notifications, `queryRemove`/`canRemove` gates, items-light composition, Lua addItem producers, `proficiencies.json` injection, 0xCA livestream resend (23 tests).
- **Raids composition** — `Raids` composed at the root (load/startup/clear, announcer, script runner, clock, `startRaid`) (16 tests).
- **792 suites / 8270 tests, all green**; all commits ancestry-verified.

---

### Phase 76: Wave 72 — Final Reduced-Port Tails (16:00 – 18:00)
- **Drain credit** — mana drain attribution through `combatChangeMana`, `Player` drain override in virtual dispatch (party tick), condition-damage owner resolution + the field halving, MagicField step-in attribution (14 tests).
- **Light & spectators** — `Player::getCreatureLight` folds the items light into the wire value; `Tile::postRemoveNotification` spectator sends (6 tests).
- **Viewer & corpse updates** — `castViewerLogin` (livestream viewer session, 0xCA routing), `sendItemUpdateToClient` binding so corpse container updates emit (9 tests).
- **GAME_STATE_INIT** — `Game::setGameState(INIT)` runs the wired INIT arm in C++ order (items price, spawns, raids, mounts, attached effects, MOTD, players record, global events, wheel data) (9 tests).
- **798 suites / 8308 tests, all green**; all commits ancestry-verified.

---

### Phase 77: Wave 73 — MariaDB Support (18:00 – 21:00)
- **Dialect core** — `DatabaseEngine` + `CANARY_DB_ENGINE` (`postgres` default, `maria|mariadb|mysql`), engine-aware JDBC URLs/ports, product-name migration-tree selection; the existing Flyway chain moved to `db/migration/postgres/` (13 tests).
- **MariaDB migration tree** — `db/migration/mariadb/V1..V22` transcribed 1:1 (`BIGINT AUTO_INCREMENT`, `LONGBLOB`, backticks, `FROM DUAL` seed guards) with a structural parity test (5 tests).
- **Runtime SQL portability** — `SqlDialect` (H2/PostgreSQL/MariaDB) with quoted-identifier and upsert helpers; repository/upsert fixes across ~14 files (quoted identifiers, MERGE → dialect upserts) and cross-engine game SQL (highscores/cyclopedia/livestream/tableExists) (29+27 tests).
- **Integration + delivery** — testcontainers MariaDB 11.4 integration suite (migrations, KV, house upsert, account/storage round trips; skips without Docker, runs in CI); compose `CANARY_DB_ENGINE` passthrough + `docker-compose.mariadb.yml` single-database mode; AGENTS.md §2.1 dual-engine policy.
- **807 suites / 8388 tests, all green** (6 MariaDB tests run on CI).

---

### Phase 78: Wave 74 — Reduced-Model Tails (21:00 – 00:00)
- **Condition target + MagicField** — production `ConditionTarget` behind `Creature.conditionTarget`; field instance creation with owner stamps, `conditionDamage` application, harmful-field gates (14 tests).
- **World boot** — NPC spawns start (NpcSpawnPath/GameNpcFactory), chat boot composition, per-player mount definitions, players-record peak persistence (14 tests).
- **Livestream + state arms** — broadcaster side wired (`setLivestreamViewers` → `canWatch`), `setGameState` SHUTDOWN/CLOSED arms, `loginRefusal` at the game-protocol gate (12 tests).
- **Combat dispatch** — `Player::changeMana` broadcast, `CHARM_VOIDINVERSION` inversion, `onTargetCreatureGainHealth` virtual dispatch, movement-script hooks (17 tests).
- **817 suites / 8445 tests, all green**; all commits ancestry-verified.

---

### Phase 79: Wave 75 — Login/Game Protocol Split (Scaling Phase 1) (00:00 – 02:00)
- **Roles** — `--role` / `CANARY_ROLE=all|login|game`: login instances boot config + DB + account repositories and bind login/status only (no world); game instances bind the game listener only; `all` unchanged (19 tests).
- **Split delivery** — `docker/docker-compose.split.yml` (`login`/`game` services, LB-ready replicas, shared DB), `.env.dist`, `DOCKER.md` split-mode guide, CI validation of the split pair; scaling document Phase 1 marked delivered.
- **Login advertisement** — the character list now advertises the configured world name/IP/game port so clients reach the game instance (or its LB).
- **820 suites / 8464 tests, all green**.

---

### Phase 80: Local Client Setup Guide (02:00 – 03:00)
- **OTClient Redemption configured** — cloned into `../otclient` and `init.lua` points at the local stack (`http://127.0.0.1:8088/login`, protocol 1525); the client's `OTSERV_RSA` verified equal to the server's default modulus.
- **`docs/client-setup.md`** — end-to-end local guide: MariaDB single-database mode (server + login-server share the game DB), MyAAC explicitly optional, client build steps (macOS preset + Intel triplet override), test accounts and troubleshooting.

---

### Current Status
- **Automated Tests**: **820 test suites / 8464 tests** passing cleanly on **Java 25 LTS** via `./gradlew test` (all `@Test` methods discovered; zero Kotlin warnings).
- **Gameplay Features**: **56 / 56 implemented**; the 15 advanced systems (`docs/development/feature-history.md`) are delivered as C++ engine cores + application services + protocol layer.
- **Combat & Lua infrastructure**: Combat stack (`CombatDamage`/`Combat`/`condition`), Lua binding layer + event dispatch, protocol parsers/serializers, feature persistence, vocations, and config gates in place.
- **Residual work**: per-feature protocol sends, deeper persistence, and Lua-driven side effects — tracked in `docs/development/implementation-status.md`.
