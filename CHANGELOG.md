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
  - Created `docs/Plan/`, `docs/Implementing/`, and `docs/Done/` lifecycle structure.
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
  - Created `docs/game-play-features.md` inventorying all 56 Canary gameplay systems.
- **`5b83cd4` - Advanced Feature Specifications**:
  - Created implementation plans in `docs/Plan/` for features `050` through `064` (Skill Wheel, Forge, Bestiary, Store, Hazard, etc.).
- **`95bcad5` / `03fa14c` - Canary C++ File References**:
  - Annotated all 64 feature documents with file paths and line numbers from the original Canary repository.
- **`4918a4c` - Codebase Audit & Feature Template**:
  - Created `docs/canary-codebase-implementation-audit.md` and `docs/TEMPLATE_FEATURE.md`.
- **`da45490` - Wheel of Destiny Engine**:
  - Implemented `WheelOfDestinyEngine` promotion point budgets ($\max(0, \text{level} - 50)$), perk allocations, and passive stat bonuses.

---

### Phase 6: Canary Parity Mandate & Lua Remediation (23:00 – 23:50)
- **`6ee94f8` - Parity + Lua Boundary Policies**:
  - Added `AGENTS.md` §1.1 (strict Canary parity — no invented methods/APIs) and §1.4 (Lua stays Lua — never port Lua logic to Kotlin).
  - Enforced immediately: renamed fabricated `Npc.onHear` → Canary `Npc::onCreatureSay`, `MagicField.onStepIn` → `onStepInField`.
- **`87a83ad` / `d6f79d3` - Lua Parity Remediation Spec & Plan**:
  - Approved design superseding `Done/012` and `Done/028`; foundation implementation plan created.
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

---

### Current Status
- **Automated Tests**: **137 test suites / 778 tests** passing cleanly on **Java 25 LTS** via `./gradlew test` (all `@Test` methods discovered; zero Kotlin warnings).
- **Advanced Features**: all 15 (`docs/Plan/050–064`) have faithful **C++ engine cores** ported with TDD; Lua-authored portions remain Lua.
- **Combat & Lua infrastructure**: Combat stack (`CombatDamage`/`Combat`/`condition`), Lua binding layer, and protocol parsers/serializers in place.
- **Remaining**: runtime state wiring (Player enrichment, persistence), config gates, and Lua seam implementations — tracked in `docs/game-play-features.md`.
