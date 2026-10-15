# Canary Kotlin Feature History (001–064)

> Aggregated from the former `docs/development/done/` feature specifications
> (one document per feature, in implementation order). Content is preserved
> verbatim — Canary C++ source references, opcode notes and test references
> included — with headings demoted one level and an index added. New documents
> are created directly in `docs/development/` (see `AGENTS.md` §5/§6).

## Index

- [001 — Networking & Login Protocol (ProtocolLogin)](#feature-001)
- [002 — Status Protocol & Server Monitoring (ProtocolStatus)](#feature-002)
- [003 — Game World Protocol & Connection Handshake (ProtocolGame)](#feature-003)
- [004 — Game Dispatcher & Tick Scheduler](#feature-004)
- [005 — Items & Object Serialization (OTB & XML / Protobuf)](#feature-005)
- [006 — Map Loader & Spatial Representation (OTBM Parser & QuadTree)](#feature-006)
- [007 — Creature System (Player, Monster, NPC)](#feature-007)
- [008 — Movement, Pathfinding & Viewport Synchronization](#feature-008)
- [009 — Combat, Spells, Damage & Condition Engine](#feature-009)
- [010 — Chat, Channels & Social Systems](#feature-010)
- [011 — Persistence & Database (Exposed / SQL & JSON Storage)](#feature-011)
- [012 — Scripting & Extensibility (Lua / Kotlin Scripting Engine)](#feature-012)
- [013 — Configuration & Server Environment Manager](#feature-013)
- [014 — Modern Client Features (Imbuements, Prey, Bestiary, Skill Wheel, Forge)](#feature-014)
- [015 — House System, House Tiles & Rent Management](#feature-015)
- [016 — Guild Management & Guild War System](#feature-016)
- [017 — Global Player Market System (IOMarket)](#feature-017)
- [018 — Depot, Inbox, Stash & Mailbox Systems](#feature-018)
- [019 — Bosstiary & Boss Cooldown Tracker](#feature-019)
- [020 — OTBM Binary Map Parser (IOMap)](#feature-020)
- [021 — OTB Items & appearances.dat Binary Loader](#feature-021)
- [022 — XML Content Loaders (Items, Monsters, Vocations & Spells)](#feature-022)
- [023 — Full Viewport & ProtocolGame Packet Serialization](#feature-023)
- [024 — Monster AI & Spawner Engine](#feature-024)
- [025 — NPC Interaction, Dialogue & Shop Window System](#feature-025)
- [026 — Magic Fields, Fluid Physics, Doors & Teleporters](#feature-026)
- [027 — Relational Database Persistence (Exposed / SQL)](#feature-027)
- [028 — Lua Scripting Engine Bridge (LuaJ / GraalVM)](#feature-028)
- [029 — End-to-End Client Transition (Login to Game World & Viewport Streaming)](#feature-029)
- [030 — Client Inbound Movement & Directional Turn Protocol](#feature-030)
- [031 — Client Item Manipulation & Container Interaction Protocol](#feature-031)
- [032 — Combat Targeting & Fight Modes Protocol](#feature-032)
- [033 — Inspection (Look At) & Text Message Protocol](#feature-033)
- [034 — Trade, Modal Windows & Client UI Protocol](#feature-034)
- [035 — Ping/Pong Heartbeat & Session Keepalive Protocol](#feature-035)
- [036 — Asset Pipeline & Canary Data Pack Resolution](#feature-036)
- [037 — Real Map Loading Pipeline (OTBM, House XML, Spawn XML)](#feature-037)
- [038 — Real Items Loading Pipeline (OTB, items.xml, appearances.dat)](#feature-038)
- [039 — Real Monster Definition & Loot Engine Pipeline](#feature-039)
- [040 — Real NPC Dialogue, Scripts & Shop Data Pipeline](#feature-040)
- [041 — Complete GamePacketRouter & Dispatcher for handleGamePacket](#feature-041)
- [042 — Argon2id Password Hashing & Security Engine](#feature-042)
- [043 — Network Rate Limiting, Flood Protection & Anti-DoS](#feature-043)
- [044 — High-Performance Primitive Spatial Map (MapSector Chunking)](#feature-044)
- [045 — Atomic Server Save & OS Signal Shutdown Handler (SIGTERM/SIGINT)](#feature-045)
- [046 — Player Death Penalty, Blessings & Corpse Drop Mechanics](#feature-046)
- [047 — Prometheus Metrics, Observability & Webhook Alerts](#feature-047)
- [048 — Banking, Currency & Vitals Regeneration Engines](#feature-048)
- [049 — Spells, Magic Engine & Player QuestLog System](#feature-049)
- [050 — Wheel of Destiny (Skill Wheel) Engine](#feature-050)
- [051 — Exaltation Forge (Item Tiers & Fusion) Engine](#feature-051)
- [052 — Bestiary & Charm Runes System](#feature-052)
- [053 — In-Game Store (GameStore) & Tibia Coins System](#feature-053)
- [054 — Gnomprona Hazard System & Primal Dungeons](#feature-054)
- [055 — Daily Reward Shrine & Consecutive Streak System](#feature-055)
- [056 — Boss Reward Chest & Damage Participation Scoring](#feature-056)
- [057 — Familiars Vocation Summon System](#feature-057)
- [058 — House Hirelings System](#feature-058)
- [059 — Mounts & Speed Buff System](#feature-059)
- [060 — Weapon Proficiencies System (Tibia 13.30+)](#feature-060)
- [061 — Soul War / Taint Encounters System](#feature-061)
- [062 — Scheduled World Raids & Global Announcements](#feature-062)
- [063 — Offline Training Shrines System (0x74)](#feature-063)
- [064 — Concoctions & Alchemical Flask Buffs](#feature-064)

---

<a id="feature-001"></a>

## 001 — Networking & Login Protocol (ProtocolLogin)

### 1. Explanation
Implements client TCP framing, Adler-32 checksum verification, RSA credential decryption, XTEA session cipher, and the complete OpenTibia Login Protocol (`ProtocolLogin` - Opcode `0x01`).

### 2. Impact
Critical entry point for players connecting to the server. Without this feature, clients cannot authenticate, retrieve the server's Message of the Day (MOTD), or obtain the list of characters and world endpoints to enter the game.

### 3. Functionality Affected
- **Client Handshake**: Validates client packets framed with a 2-byte length prefix.
- **Security & Decryption**: Authenticates credentials encrypted with 1024-bit RSA, sets up session XTEA keys.
- **Account Service**: Verifies account credentials against an `AccountRepository` and loads player characters.
- **Character List Response**: Constructs and encrypts character list packet (`0x64`), session key (`0x28`), and MOTD (`0x14`).

### 4. Agent Implementation Details
- Created `TibiaByteBufExt.kt` providing extension functions for reading/writing Tibia strings and variable-length integers ($\ge 15.00$).
- Created `TibiaFrameDecoder.kt` using Netty's `ByteToMessageDecoder` to parse the 2-byte Little-Endian packet header.
- Implemented `Adler32Checksum.kt` and `XteaCipher.kt` (64 rounds / 32 cycles) in Little-Endian byte order.
- Implemented `RsaCipher.kt` handling 128-byte raw modular exponentiation decryption using standard OpenTibia primes ($p, q$).
- Created `TibiaMessageEncoder.kt` automating 8-byte padding, in-place XTEA encryption, Adler-32 calculation, and 2-byte outer framing.
- Implemented `LoginHandler.kt` parsing client OS, protocol version, RSA credentials, and emitting the character list or error disconnect.
- Implemented `CanaryServer.kt` TCP server bootstrap using Netty.

### 5. Test Reference
- `org.opentibia.canary.network.crypto.Adler32ChecksumTest`: Tests Adler32 against known test vectors.
- `org.opentibia.canary.network.crypto.CryptoTest`: Tests XTEA encrypt/decrypt and RSA encrypt/decrypt round-trips.
- `org.opentibia.canary.network.message.TibiaByteBufExtTest`: Tests Tibia string and variable count serialization.
- `org.opentibia.canary.network.protocol.LoginProtocolTest`: Full end-to-end integration test with `EmbeddedChannel` simulating a real client packet, checking authentication, XTEA responses, and disconnects.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-002"></a>

## 002 — Status Protocol & Server Monitoring (ProtocolStatus)

### 1. Explanation
Provides the OpenTibia XML/JSON server status protocol (`ProtocolStatus` - Opcode `0xFF`). Allows status pingers, OTServ monitoring websites, and launcher clients to query server stats (online players, uptime, server name, version, map details).

### 2. Impact
Low risk, vital for community discovery and external monitoring tools. Keeps server statistics accessible without disrupting game or login flows.

### 3. Functionality Affected
- **Packet Ingestion**: Routes packets with protocol identifier `0xFF` on the login/status port (7171/7172).
- **XML / JSON Status Info**: Serializes uptime, server name, IP, port, current player count, maximum players, and server software metadata.

### 4. Agent Implementation Details
- Add status protocol handler (`StatusHandler.kt`) or route `0xFF` within `TibiaProtocolRouter`.
- Implement `ServerStatusService` collecting metrics: JVM memory, connected players, server startup time.
- Generate standard XML response `<tsqp version="1.0"><serverinfo .../><players .../><motd .../></tsqp>`.
- Optionally support JSON info format requested by modern web launchers.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.StatusProtocolTest`: Unit/integration tests simulating client ping with opcode `0xFF` (with and without Adler32) and validating the XML/JSON response body.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-003"></a>

## 003 — Game World Protocol & Connection Handshake (ProtocolGame)

### 1. Explanation
Manages player connection to the main game world server (port 7172). Handles the initial game handshake, RSA verification of character name and session key, player instantiation, and establishes the continuous bidirectional game packet stream.

### 2. Impact
Foundational system for gameplay. Bridges player network sessions with the internal game simulation and entity management.

### 3. Functionality Affected
- **Game Server Acceptor**: Listens on port 7172.
- **Game Handshake**: Receives OS, Protocol Version, Client Version, RSA block with character name and XTEA key.
- **Session Transition**: Links the TCP channel to an active `Player` entity in `GameWorld`.
- **Packet Streaming**: Establishes bidirectional message flow between player input events and outgoing world updates.

### 4. Agent Implementation Details
- Implement `GameConnectionHandler.kt` to manage channel lifecycle for game clients.
- Implement game login packet decoder verifying character existence, ban checks, and account status.
- Implement server-first or client-first prelude handling depending on target client version profile.
- Dispatch player connection event to the game world actor/dispatcher.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.GameHandshakeTest`: Tests game world login with valid character, invalid credentials, and duplicate logins.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-004"></a>

## 004 — Game Dispatcher & Tick Scheduler

### 1. Explanation
Canary runs game updates on a dedicated single-threaded dispatcher (`g_dispatcher()`) partitioned into prioritized execution lanes (`DispatcherLane::ProtocolInput`, `DispatcherLane::WorldCommit`, `DispatcherLane::Maintenance`). This guarantees thread-safety across map tiles, items, and creatures without lock contention.

### 2. Impact
Core engine architecture. In Kotlin/JVM, replacing C++ mutexes and Asio event loops with Kotlin Coroutines or Project Loom actor channels prevents race conditions, deadlocks, and high CPU lock synchronization.

### 3. Functionality Affected
- **Event Scheduling**: Timed tasks, decay cycles, creature AI think ticks, regeneration intervals.
- **Input Dispatching**: Decouples network I/O threads (Netty EventLoops) from world modification logic.
- **World Commit Loop**: Batch commit for position changes, battle steps, and map state changes.

### 4. Agent Implementation Details
- Implement `GameDispatcher` using Kotlin Coroutines `Channel<GameEvent>(Channel.UNLIMITED)` processed by a single dedicated dispatcher or virtual thread.
- Support scheduled events (`scheduleEvent(delayMs, block)`) using coroutine delays or Java `ScheduledExecutorService`.
- Implement priority lanes: high priority for creature movement/combat commits, standard for inputs, background for persistence/maintenance.

### 5. Test Reference
- `org.opentibia.canary.game.scheduling.GameDispatcherTest`: Test event ordering, priority execution, exception isolation, and graceful shutdown.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-005"></a>

## 005 — Items & Object Serialization (OTB & XML / Protobuf)

### 1. Explanation
Loads and parses item definitions from Tibia's `.otb` (OpenTibia Binary format) and `items.xml` / Protobuf schemas. Manages item attributes (weight, attack, defense, slot position, container capacity, decay, writeable, fluids).

### 2. Impact
Critical dependency for the map loader, creature inventory, container handling, combat mechanics, and ground interactions.

### 3. Functionality Affected
- **Item Registry**: Maps server item IDs to client sprite IDs.
- **Item Types & Flags**: `ItemType` definitions (unmoveable, block solid, block path, has height, stackable, pickupable, etc.).
- **Item Instances**: Factory creating `Item`, `Container`, `Teleport`, `TrashHolder`, etc.

### 4. Agent Implementation Details
- Implement binary OTBM node reader (`OtbmNodeReader.kt`) capable of parsing tree-structured OTBM/OTB files.
- Implement `ItemTypeLoader` parsing `items.otb` headers and node attributes.
- Implement XML/JSON parser for extended properties (decaying, transforms, armor, charges, weapon types).
- Provide high-performance item lookup by ID with fast primitive arrays.

### 5. Test Reference
- `org.opentibia.canary.items.ItemLoaderTest`: Parse test `items.otb` snippet and verify attribute assignment (stackable, containers, weapon types).


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-006"></a>

## 006 — Map Loader & Spatial Representation (OTBM Parser & QuadTree)

### 1. Explanation
Parses OpenTibia Map (`.otbm`) binary format files and loads towns, waypoints, houses, tiles, items, and spawns into a spatial data structure (2D/3D grid or QuadTree/Floor map).

### 2. Impact
Foundational for all gameplay and spatial awareness. The map structure dictates pathfinding, line of sight, creature perception, spectator broadcasting, and viewport rendering.

### 3. Functionality Affected
- **Tile Hierarchy**: Ground, items stack, creature list, tile flags (PZ, house, no-logout, refresh).
- **Spectator Resolution**: Fast querying of players and creatures within a 18x14 viewport around any position $(x, y, z)$.
- **OTBM Parser**: Decompresses nodes, loads header (map dimensions, description), areas, towns, and tile node trees.

### 4. Agent Implementation Details
- Implement `Position` value class or data class with $x, y, z$ coordinates and distance helpers.
- Implement `Tile` and `Map` classes holding spatial layers.
- Implement OTBM binary reader decoding OTBM node markers (`ESCAPE`, `NODE_START`, `NODE_END`).
- Provide spectator lookup caching per floor sector.

### 5. Test Reference
- `org.opentibia.canary.map.OtbmLoaderTest`: Parse a small synthetic OTBM test map and assert tile coordinates, ground item IDs, and towns.
- `org.opentibia.canary.map.SpectatorTest`: Test viewport queries around boundary positions $(x, y, z)$.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-007"></a>

## 007 — Creature System (Player, Monster, NPC)

### 1. Explanation
Defines the creature hierarchy: base `Creature`, `Player`, `Monster`, and `Npc`. Manages core RPG attributes (health, mana, speed, direction, look type/outfit, conditions, skull/shield status).

### 2. Impact
Central domain entities. Drives player interaction, monster behavior, AI, dialogue, combat calculations, and persistence.

### 3. Functionality Affected
- **State Machine**: Walking, attacking, casting, idling, dead.
- **Inventory & Containers**: Equippable slots (head, armor, legs, feet, back, left/right hand, ring, ammo).
- **Vitals & Stats**: Health/mana regeneration, capacity, stamina, soul points, skills (fist, club, sword, axe, distance, shielding, fishing, magic level).

### 4. Agent Implementation Details
- Implement `Creature` class with thread-confined mutations via the `GameDispatcher`.
- Implement `Player` entity with connection reference, storage values, guild info, VIP list.
- Implement `Monster` entity with monster type templates (spells, loot, immunities).
- Implement `Npc` entity with interaction queue and dialogue state.

### 5. Test Reference
- `org.opentibia.canary.creatures.CreatureTest`: Test health modifications, death triggers, inventory equipping, capacity checks.
- `org.opentibia.canary.creatures.PlayerStatsTest`: Test experience/level progression and skill formula computations.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-008"></a>

## 008 — Movement, Pathfinding & Viewport Synchronization

### 1. Explanation
Handles creature movement on the map, validation of walk requests (step north, east, south, west, diagonal), pathfinding (A* algorithm), and network synchronization (sending tile updates, map descriptions, creature turn packets).

### 2. Impact
Most frequent network operation in the MMORPG. Imperfect synchronization results in client-side "ghost walking", desyncs, or tile collisions.

### 3. Functionality Affected
- **Step Timing**: Speed-to-step-delay calculations (step duration depends on ground speed and creature speed).
- **Collision Checking**: Tile block solid, path blocking items, creature collision, elevation stairs/ramps/holes.
- **Network Viewport Packets**: `sendMapDescription`, `sendMoveCreature`, `sendAddTileItem`, `sendRemoveTileItem`.

### 4. Agent Implementation Details
- Implement A* pathfinder supporting diagonal penalty and avoid hazard flags.
- Implement step queue on `Creature` scheduling next step on the `GameDispatcher`.
- Implement map packet serializers building the floor description buffer (known 18x14 viewport tiles).
- Broadcast movement steps to all spectators within hearing/seeing distance.

### 5. Test Reference
- `org.opentibia.canary.game.movement.MovementTest`: Verify step delay calculation, tile collision rejection, and floor change transitions.
- `org.opentibia.canary.game.movement.PathfinderTest`: Test A* around obstacles and closed doors.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-009"></a>

## 009 — Combat, Spells, Damage & Condition Engine

### 1. Explanation
Manages combat interactions between creatures: auto-attacks, distance weapons, magic projectiles, damage calculation (formulas based on attack, defense, armor, resistance), conditions (poison, fire, energy, paralyze, invisible, haste), and death penalty.

### 2. Impact
Directly governs core gameplay balance, PvP, and PvE loops. High complexity due to stacking formulas, mitigation, and elemental affinities.

### 3. Functionality Affected
- **Attack Loop**: Auto-attack cooldowns (typically 2000ms attack round).
- **Damage Formulation**: Physical, Fire, Ice, Energy, Earth, Holy, Death, Drown, Lifedrain, Manadrain.
- **Spell Casting**: Mana verification, rune consumption, instant words, target validation, cooldown groups (attack, healing, support).
- **Conditions**: Tick-based condition updates (damage over time, stat buffs/debuffs).

### 4. Agent Implementation Details
- Implement `CombatEngine` with damage formulas matching Canary/Tibia specifications.
- Implement `Condition` classes with periodic tick execution on the scheduler.
- Implement spells registry with target selectors (single target, area of effect, self).
- Implement damage reduction pipeline (armor value check, defense reduction, elemental resist percentages).

### 5. Test Reference
- `org.opentibia.canary.combat.DamageCalculationTest`: Verify damage bounds for test weapon/skill values.
- `org.opentibia.canary.combat.ConditionTest`: Verify DOT damage ticks and expiration.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-010"></a>

## 010 — Chat, Channels & Social Systems

### 1. Explanation
Implements creature speech (say, yell, whisper), standard chat channels (Local Chat, Server Log, World Chat, English Chat, Guild, Party), private messages, and social systems (VIP list, friend requests, ignore list, party, guild).

### 2. Impact
Essential for multiplayer communication, moderation, party hunting, and server messaging.

### 3. Functionality Affected
- **Speech**: Public speech broadcasted to spectators within range (say: 1-4 tiles, yell: floor-wide/large radius, whisper: 1 tile).
- **Channels**: Channel join/leave permissions, custom channels, party channels.
- **Party System**: Shared experience calculations, party leader delegation, invitation flow, party buffs/analyzer.
- **Guild System**: Guild ranks, permissions, titles, guild chat.

### 4. Agent Implementation Details
- Implement `ChatChannelManager` managing active channels and subscriber sets.
- Implement speech parser delegating to spell words or standard talkactions.
- Implement `Party` class with shared experience logic and distance checking.
- Implement VIP list storage and online/offline status notifications.

### 5. Test Reference
- `org.opentibia.canary.chat.ChatChannelTest`: Join, leave, send message, mute permissions.
- `org.opentibia.canary.social.PartyTest`: Shared exp distribution tests based on member levels and proximity.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-011"></a>

## 011 — Persistence & Database (Exposed / SQL & JSON Storage)

### 1. Explanation
Handles database persistence for player accounts, characters, items, storage key-values, houses, bans, and guilds. Canary uses MySQL/MariaDB with prepared statements; in Kotlin, this maps to an asynchronous or pooled ORM/DAO layer such as JetBrains Exposed, Ktorm, or jOOQ.

### 2. Impact
Data integrity, server crash resilience, character save/load operations, and web integration (e.g., MyAAC, Gesior).

### 3. Functionality Affected
- **Account & Player Persistence**: Load on login, save on logout, periodic auto-save.
- **Player Items & Depot Serialization**: Recursive serialization of nested containers and item attributes.
- **House Ownership & Items**: Persistence of house tiles, door permissions, and rents.
- **Server Shutdown & Crash Recovery**: Atomic transaction commit on server stop.

### 4. Agent Implementation Details
- Integrate JetBrains Exposed or HikariCP + SQL connection pool with virtual threads.
- Define schemas for `accounts`, `players`, `player_items`, `player_depotitems`, `player_storage`, `houses`.
- Implement non-blocking asynchronous save worker dispatched off the main game tick loop.
- Support JSON fallback repository for local/testing development.

### 5. Test Reference
- `org.opentibia.canary.database.PlayerPersistenceTest`: Save player with nested backpack items, reload into new instance, assert identity and properties.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-012"></a>

## 012 — Scripting & Extensibility (Lua / Kotlin Scripting Engine)

### 1. Explanation
Canary provides an extensive Lua scripting layer (`data/scripts`) controlling actions, talkactions, spells, monsters, movements, weapon scripts, and quest callbacks. In Kotlin, this can be implemented via LuaJ / Kahlua / GraalVM Lua, or a native Kotlin DSL / script engine.

### 2. Impact
Extremely high. Over 80% of specific content, quests, events, boss mechanics, and custom behavior in Canary is driven by scripts rather than compiled C++ code.

### 3. Functionality Affected
- **Action Scripts**: `onUse(player, item, fromPosition, target, toPosition, isHotkey)`.
- **Movement Scripts**: `onStepIn(creature, item, position, fromPosition)` / `onStepOut`.
- **CreatureEvents**: `onDeath`, `onKill`, `onLogin`, `onLogout`, `onPrepareDeath`.
- **Global Events**: Timers, startup callbacks, periodic map events.

### 4. Agent Implementation Details
- Provide scripting bridge (via embedded Lua interpreter or native Kotlin DSL).
- Register Lua API functions mirroring Canary's Lua functions (`Player:addItem`, `Tile:getTopCreature`, `Position:sendMagicEffect`, etc.).
- Implement script hot-reloading mechanism (`/reload scripts`).

### 5. Test Reference
- `org.opentibia.canary.scripting.ScriptEngineTest`: Execute test script registering an item action, simulate trigger, assert state change.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-013"></a>

## 013 — Configuration & Server Environment Manager

### 1. Explanation
Manages server configuration loading, environment variable overrides, validation of network ports, rate limits, world type (PvP, Non-PvP, Retro-PvP), spawn rates, exp stages, and database credentials (`config.lua` in Canary).

### 2. Impact
Controls startup parameters, operational toggles, and server tuning without requiring code recompilation.

### 3. Functionality Affected
- **Configuration Parsing**: Reads configuration files (YAML / HOCON / .properties / Lua).
- **Environment Overrides**: Reads `CANARY_*` environment variables (Docker-friendly).
- **Runtime Reload**: Reloads non-critical configs via in-game command.

### 4. Agent Implementation Details
- Implement `ConfigManager` with type-safe Kotlin data classes (e.g. using `sksamuel/hoplite` or pure Kotlin parser).
- Expose defaults matching Canary's default `config.lua.dist`.
- Support configuration injection across networking, database, and game modules.

### 5. Test Reference
- `org.opentibia.canary.config.ConfigManagerTest`: Tests configuration loading with fallbacks and environment variable overrides.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-014"></a>

## 014 — Modern Client Features (Imbuements, Prey, Bestiary, Skill Wheel, Forge)

### 1. Explanation
Canary supports modern CipSoft Tibia (12.x / 13.x / 15.x) features including Imbuements, Prey system, Hunting Tasks, Bestiary & Charms, Forge (Tier upgrades/fusion), and the Skill Wheel of Destiny.

### 2. Impact
Necessary for 100% parity with modern Canary protocol versions ($\ge 12.00$).

### 3. Functionality Affected
- **Imbuement System**: Shrine interaction, powerful/intricate/basic slots, imbuement duration depletion.
- **Prey & Task Hunting**: Monster rerolls, damage/defense/loot/xp prey buffs.
- **Bestiary / Bosstiary**: Monster kill tracking, unlocking charm points, assigning runes.
- **Skill Wheel of Destiny**: Points distribution tree, perk unlocks, gem socketing.
- **Exaltation Forge**: Dust/slivers/cores, equipment fusion and transfer.

### 4. Agent Implementation Details
- Implement dedicated domain models and packet serializers for each subsystem.
- Implement packet handlers for each respective client modal/window opcodes.
- Persist state into player storage / JSON / database schemas.

### 5. Test Reference
- `org.opentibia.canary.systems.ImbuementTest`: Test slotting items, applying stat bonuses, and decay tracking.
- `org.opentibia.canary.systems.PreyTest`: Test roll logic and active buff multipliers.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-015"></a>

## 015 — House System, House Tiles & Rent Management

### 1. Explanation
Manages player houses, house doors, house tiles, sub-owners, guest lists, rent collection, and house auctions. In Canary, house data is parsed from the map (`house.xml`) and saved into MySQL/MariaDB (`houses` and `house_lists` tables).

### 2. Impact
Critical for player persistence, player economy, and map tile security. House tiles reject non-authorized occupants and prevent item loss across server restarts.

### 3. Functionality Affected
- **House Tile Protection**: Prevents unauthorized players from walking onto house tiles or taking house items.
- **Door Access Lists**: Manages guest lists (`aleta grav`), sub-owner lists (`aleta sio`), and door permissions (`aleta som`).
- **House State Transitions**: House purchase, transfer, eviction, and daily/monthly rent deductions.

### 4. Agent Implementation Details
- Implement `House` and `HouseTile` models in `org.opentibia.canary.map.house`.
- Implement `HouseManager` keeping active house registries and town associations.
- Parse house metadata from XML and map OTBM nodes.
- Expose permission evaluation methods (`canEnter(player, position)`).

### 5. Test Reference
- `org.opentibia.canary.map.house.HouseTest`: Tests permission check for owner, guest, and unauthorized intruder. Tests rent deduction and eviction.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-016"></a>

## 016 — Guild Management & Guild War System

### 1. Explanation
Implements player guilds, ranks (Leader, Vice-Leader, Member), invites, guild bank/treasury balance, guild chat channels, and Guild Wars (frag limits, war declarations, surrender, and war emblems on player nameplates).

### 2. Impact
Core endgame community and PvP system. Directly impacts player organization, PvP combat rules (green/red skull flags during wars), and broadcast notifications.

### 3. Functionality Affected
- **Guild Model**: Ranks, member lists, guild motd, treasury.
- **War System**: War declarations, acceptance, frag tracking, war scoreboard.
- **Visual Emblems**: Green shield for guildmates, blue shield for allies, red shield for war enemies.

### 4. Agent Implementation Details
- Implement `Guild`, `GuildRank`, and `GuildMember` classes in `org.opentibia.canary.social.guild`.
- Implement `GuildWarManager` handling declarations, scoring, and skull/emblem evaluations.
- Persist guild memberships and balances to storage.

### 5. Test Reference
- `org.opentibia.canary.social.guild.GuildWarTest`: Tests war declaration, kill score accumulation, frag limit victory, and emblem resolutions.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-017"></a>

## 017 — Global Player Market System (IOMarket)

### 1. Explanation
Implements the in-game auction house / Market window (`IOMarket`). Players can create buy/sell offers for items, browse active offers, track transaction history, and receive proceeds/items via town depots or player mailboxes.

### 2. Impact
High economic impact. Replaces trade chat spam with an asynchronous in-game economy. Affects gold balances, item ownership, and depot storage.

### 3. Functionality Affected
- **Offer Engine**: Buy offers (requiring gold escrow) and sell offers (requiring item escrow).
- **Matching Engine**: Auto-matches compatible buy/sell offers and transfers gold/items asynchronously.
- **Market Packets**: Opcode handlers for browse offers, my offers, offer history, and cancel offer.

### 4. Agent Implementation Details
- Implement `MarketOffer`, `MarketOfferType` (Buy, Sell), and `MarketManager` in `org.opentibia.canary.economy.market`.
- Implement transactional matching logic between buyers and sellers.
- Handle escrow release to depot on canceled or completed offers.

### 5. Test Reference
- `org.opentibia.canary.economy.market.MarketTest`: Tests offer creation, escrow deduction, matching, and fund distribution.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-018"></a>

## 018 — Depot, Inbox, Stash & Mailbox Systems

### 1. Explanation
Manages player private item storage: Town Depots (shared or town-specific), Depot Boxes, Character Inbox, Supply Stash, and Mailbox delivery (parcels and letters delivered to character town depots).

### 2. Impact
Critical inventory and logistical subsystem. Prevents item duplication and loss across towns and character mail deliveries.

### 3. Functionality Affected
- **Depot Boxes & Lockers**: Access to town depot chest and character depot items.
- **Supply Stash**: Stackable item storage window directly accessible from depot tiles or hotkeys.
- **Mail Delivery**: Mailbox item consumption, destination character/town lookup, parcel routing.

### 4. Agent Implementation Details
- Implement `DepotLocker`, `DepotChest`, and `SupplyStash` in `org.opentibia.canary.items.containers.depot`.
- Implement `MailService` parsing parcel labels, verifying recipient existence, and depositing into character inboxes.
- Ensure recursive serialization into player persistence.

### 5. Test Reference
- `org.opentibia.canary.items.containers.depot.DepotMailTest`: Tests sending a parcel through a mailbox and receiving it in the recipient's town depot inbox.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-019"></a>

## 019 — Bosstiary & Boss Cooldown Tracker

### 1. Explanation
Canary includes CipSoft's modern Bosstiary system (`io_bosstiary.cpp`). Tracks boss kill counts across 3 progression tiers (Bane, Archfoe, Nemesis), awards Boss Points, unlocks Boss Slot bonuses (e.g. equipment loot bonus), and manages daily boss room levers and lockout cooldowns.

### 2. Impact
Endgame bossing progression and reward mechanics. Affects boss room levers, instance room access, and loot generation multipliers.

### 3. Functionality Affected
- **Kill Count Tracker**: Increments boss kills per player / party upon boss death.
- **Boss Mastery Preamble**: Bane (25/100/300), Archfoe (5/20/60), Nemesis (1/3/5).
- **Boss Cooldown Management**: Lockouts preventing players from re-entering boss levers within 20 hours.

### 4. Agent Implementation Details
- Implement `BosstiaryManager` and `BossEntry` in `org.opentibia.canary.systems.bosstiary`.
- Implement boss kill listener integrated with `CreatureEvent` / death dispatcher.
- Expose boss bonus modifiers to the `LootEngine`.

### 5. Test Reference
- `org.opentibia.canary.systems.bosstiary.BosstiaryTest`: Tests boss kill accumulation, tier unlock, and bonus multiplier calculation.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-020"></a>

## 020 — OTBM Binary Map Parser (IOMap)

### 1. Explanation
Parses binary OpenTibia Map (`.otbm`) files (`src/io/iomap.cpp`, `fileloader.cpp`). Handles the tree-structured binary serialization format using node markers (`NODE_START = 0xFE`, `NODE_END = 0xFF`, `ESCAPE_CHAR = 0xFD`), decoding map metadata, header dimensions, tile areas, individual tiles, house tiles, and town definitions into `GameMap`.

### 2. Impact
Critical foundational loader. Without this, the server cannot load real Tibia world maps (e.g., standard Canary map, Real Server map, or custom maps) and cannot position tiles, towns, or houses.

### 3. Functionality Affected
- **Binary Node Decoding**: Unescapes bytes and traverses nested node trees.
- **Header Parsing**: Version, map dimensions ($width \times height$), major/minor version, item format versions.
- **Tile Area Resolution**: Base $(x, y, z)$ coordinates and offset tiles.
- **Tile Flag Parsing**: Protection Zone (PZ), No-Logout, Refresh, House tiles.
- **Town Loader**: Town ID, town name, temple spawn position $(x, y, z)$.

### 4. Agent Implementation Details
- Implement `OtbmNodeReader.kt` with byte unescaping and recursive/iterative node traversal.
- Implement `OtbmMapLoader.kt` decoding `OTBM_ROOT`, `OTBM_MAP_DATA`, `OTBM_TILE_AREA`, `OTBM_TILE`, `OTBM_HOUSETILE`, and `OTBM_TOWNS`.
- Populate `GameMap` instances with tiles and ground items parsed from binary nodes.

### 5. Test Reference
- `org.opentibia.canary.io.OtbmLoaderTest`: Tests parsing a synthetic binary OTBM buffer with header, town list, and 3x3 tile area with ground and house tiles.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-021"></a>

## 021 — OTB Items & appearances.dat Binary Loader

### 1. Explanation
Parses binary `items.otb` (`src/items/items.cpp`) and modern `appearances.dat` Protobuf files (`data/items/appearances.dat`). Reads item category groupings (Ground, Container, Weapon, Armor, Teleport, Magic Field, Writeable, Key), client sprite ID mapping, stackability, pickupability, and collision flags.

### 2. Impact
Essential for game assets. Without OTB loading, the server cannot know which server Item IDs correspond to client sprite representations, which items are walkable or blocking, or how containers behave.

### 3. Functionality Affected
- **Item Classification**: Ground items, top order items, containers, fluids, doors.
- **Server-to-Client ID Mapping**: Server Item ID $\to$ Client Sprite ID translation.
- **Item Flags**: `BLOCK_SOLID`, `BLOCK_PROJECTILE`, `BLOCK_PATHFIND`, `HAS_HEIGHT`, `USEABLE`, `PICKUPABLE`, `STACKABLE`.

### 4. Agent Implementation Details
- Implement `OtbReader.kt` utilizing the binary node parser to traverse `ROOT` and `ITEM` nodes.
- Implement attribute readers for `ATTR_SERVER_ID`, `ATTR_CLIENT_ID`, `ATTR_SPEED`, `ATTR_LIGHT`, `ATTR_TOPORDER`.
- Populate `ItemTypeRegistry` with populated `ItemType` instances ready for game simulation.

### 5. Test Reference
- `org.opentibia.canary.items.OtbLoaderTest`: Tests parsing sample binary OTB item nodes and asserts client sprite ID translation and flag bitmasks.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-022"></a>

## 022 — XML Content Loaders (Items, Monsters, Vocations & Spells)

### 1. Explanation
Parses content definitions from Canary's XML files:
- `data/items/items.xml`: Extended item stats (attack, defense, armor, slotPosition, decayTo, duration, charges).
- `data/XML/vocations.xml`: HP/MP gain per level, capacity gain, soul gain, attack speed formulas.
- `data/XML/outfits.xml` & `data/XML/spells.xml`: Available outfits/addons and base spell metadata.
- `data/monster/*.xml`: Monster definitions (looktype, corpse, attacks, defenses, immunities, loot).

### 2. Impact
Provides actual RPG stats, balance, and creature behavior templates. Without this, items have no attack/defense stats, players do not gain vocation-specific vitals, and monsters lack loot tables or spell sets.

### 3. Functionality Affected
- **Item Decoration**: Augments basic OTB items with XML attributes (e.g. Magic Sword having 48 Atk, 35 Def, 1-handed Sword).
- **Vocation Registry**: Dictates Sorcerer, Druid, Paladin, Knight stat curves and regeneration rates.
- **Monster Type Registry**: Monster templates used when spawning monsters on the map.

### 4. Agent Implementation Details
- Implement `XmlItemLoader.kt` using standard Java XML DOM / StAX streaming parser.
- Implement `VocationLoader.kt` building `Vocation` models with stat progression calculations.
- Implement `MonsterTypeLoader.kt` creating `MonsterType` templates with spell lists and loot items.

### 5. Test Reference
- `org.opentibia.canary.io.XmlContentLoaderTest`: Parses a sample XML snippet for items and vocations, asserting attack/defense and vocation stat gains per level.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-023"></a>

## 023 — Full Viewport & ProtocolGame Packet Serialization

### 1. Explanation
Implements complete server-to-client packet serializations for rendering the game world (`src/server/network/protocol/protocolgame.cpp`):
- `sendMapDescription(Position)`: Encodes $18 \times 14$ tiles across floor levels ($z$-range $\pm 2$ floors) with ground sprites, item stacks, and creature outfits.
- Floor transition packets: `sendFloorChangeUp` (`0xBE`), `sendFloorChangeDown` (`0xBF`).
- Viewport scrolling: `sendMoveNorth` (`0x65`), `sendMoveEast` (`0x66`), `sendMoveSouth` (`0x67`), `sendMoveWest` (`0x68`).
- Creature packets: `sendAddCreature` (`0x0C`), `sendRemoveCreature` (`0x0D`), `sendCreatureTurn` (`0x6B`), `sendCreatureSay` (`0xAA`).
- Inventory & Containers: `sendInventoryItem` (`0x78`), `sendOpenContainer` (`0x7D`), `sendCloseContainer` (`0x7E`).

### 2. Impact
The most visible and critical client-rendering component. Without this, real Tibia clients connect to port 7172 but display a black screen or disconnect immediately due to unparsed map streams.

### 3. Functionality Affected
- **Client Screen Rendering**: Translates spatial `GameMap` state into the exact byte stream expected by the Tibia client canvas.
- **Creature Appearance**: Serializes outfit looktypes, addons, colors (head, body, legs, feet), light radius, and health percent ($0..100\%$).
- **Tile Compaction**: Encodes skip byte runs ($0..255$ empty tiles) to compress map streams over TCP.

### 4. Agent Implementation Details
- Implement `MapPacketWriter.kt` providing `writeMapDescription`, `writeFloorChange`, and `writeTile`.
- Implement `CreaturePacketWriter.kt` serializing outfits, light, skulls, and turn directions.
- Implement `ContainerPacketWriter.kt` serializing container contents.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.MapPacketWriterTest`: Tests serializing a test $18 \times 14$ tile grid and verifies empty tile run compression and ground item IDs.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-024"></a>

## 024 — Monster AI & Spawner Engine

### 1. Explanation
Manages world spawns and autonomous monster behaviors (`src/map/spawns.cpp`, `src/creatures/monsters/`). Loads monster spawns from `spawns.xml` / OTBM spawn zones, tracks monster life cycles, spawn radius constraints, idle wandering, target acquisition (targeting players), combat AI loops (melee, ranged spells, self-healing, running on low health), and respawn timers.

### 2. Impact
Populates the game world with living PvE encounters. Governs hunting grounds, dungeons, and monster mechanics.

### 3. Functionality Affected
- **Spawn Zones**: Spawns monsters at $(x, y, z)$ within a configured radius.
- **Respawn Timers**: Checks whether players are standing on the spawn tile to block/delay respawning, triggering respawns after $N$ seconds.
- **AI Think Tick**: Evaluates surrounding creatures every 1000ms: acquires target, calculates A* path, approaches target, casts attack spells, flees if health $< \text{runAwayHealth}$.

### 4. Agent Implementation Details
- Implement `SpawnManager.kt` loading spawn points from XML and scheduling respawn events on `GameDispatcher`.
- Implement `MonsterAiEngine.kt` executing the think cycle for active monsters within range of at least one online player.
- Integrate loot dropping upon monster death.

### 5. Test Reference
- `org.opentibia.canary.creatures.MonsterAiTest`: Tests monster target acquisition, movement towards player, and spell casting trigger.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-025"></a>

## 025 — NPC Interaction, Dialogue & Shop Window System

### 1. Explanation
Manages Non-Player Characters (`Npc`), conversation state machines, keyword handlers, and the in-game NPC trade window (`data/npclib/`, `src/creatures/npcs/`). Supports player greetings (`hi`), trade invocations (`trade`), quest dialogues, buying/selling items, bank balances, and farewells (`bye`).

### 2. Impact
Key to character progression, buying basic equipment, potions, runes, traveling via boats/carpets, promoting vocations, and undertaking quests.

### 3. Functionality Affected
- **Dialogue State Machine**: Tracks conversation focus per player (`IDLE`, `TALKING`, `TRADING`).
- **Keyword Pattern Matching**: Matches player chat strings with configured keyword responses.
- **NPC Trade Window**: Sends `sendShop` (`0x7A`) with available goods, prices, and player balance; processes `0x7A` buy and `0x7B` sell requests.

### 4. Agent Implementation Details
- Implement `Npc.kt` with a thread-safe conversation context.
- Implement `KeywordHandler.kt` parsing patterns (e.g., regex/string tokens for "hi", "trade", "yes", "no").
- Implement `NpcShopHandler.kt` executing purchases and sales (verifying player gold, weight capacity, and inventory space).

### 5. Test Reference
- `org.opentibia.canary.creatures.NpcDialogueTest`: Tests player greeting NPC, initiating trade, and buying a potion.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-026"></a>

## 026 — Magic Fields, Fluid Physics, Doors & Teleporters

### 1. Explanation
Implements core world object behaviors (`src/items/`, `src/game/movement/`):
- **Magic Fields**: Fire fields, Poison fields, Energy fields. Create hazards on tiles, deal damage, apply condition ticks (`ConditionType.Fire`, `ConditionType.Poison`) when creatures step in, and decay over time.
- **Fluid Physics**: Vials, flasks, and fluid barrels (water, blood, mana, health, slime).
- **Doors & Keys**: Open/closed door toggles, locked doors checking key action IDs.
- **Teleporters**: Tiles routing stepping creatures instantly to destination coordinates $(x, y, z)$.

### 2. Impact
Pervasive world mechanics. Governs dungeon puzzles, map exploration, safe zones, and tactical combat field control.

### 3. Functionality Affected
- **Tile Step-In Triggers**: `onStepIn` listeners checking for fields or teleporters.
- **Door Transformations**: Item ID swap when used (closed $\to$ open door, open $\to$ closed door).
- **Field Decays**: Scheduling decay events on `GameDispatcher` to step through decay stages before vanishing.

### 4. Agent Implementation Details
- Implement `MagicFieldItem.kt` extending `Item` with condition application logic.
- Implement `DoorItem.kt` with open/close toggles and key ID verification.
- Implement `TeleportItem.kt` setting creature position upon tile entry.

### 5. Test Reference
- `org.opentibia.canary.items.WorldObjectsTest`: Tests stepping into a fire field applies burning damage, stepping onto teleporter changes coordinates, and using a closed door toggles state.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-027"></a>

## 027 — Relational Database Persistence (Exposed / SQL)

### 1. Explanation
Connects the in-memory persistence layer (`PlayerStorage`, `AccountRepository`) to a real relational database (MySQL/MariaDB/PostgreSQL/H2) via JetBrains Exposed (`src/database/`, `src/io/iologindata.cpp`). Defines tables for `accounts`, `players`, `player_items`, `player_storage`, `guilds`, `houses`, and `market_offers`.

### 2. Impact
Production data persistence. Allows external account managers (MyAAC, Znote AAC) to register accounts and characters, and ensures all player inventory items, stats, and positions survive server crashes and restarts.

### 3. Functionality Affected
- **Connection Pool**: HikariCP pooled database connections running non-blocking operations on IO/Virtual Threads.
- **Table Schemas**: Standard OpenTibia / Canary schema mapping.
- **Nested Item Tree Serialization**: Serializing player inventory slots (1..10) and nested backpacks with recursion depth and item attributes.

### 4. Agent Implementation Details
- Add `org.jetbrains.exposed:exposed-core`, `exposed-dao`, `exposed-jdbc`, and `com.zaxxer:HikariCP` to `build.gradle.kts`.
- Implement Exposed `Table` definitions: `AccountsTable`, `PlayersTable`, `PlayerItemsTable`.
- Implement `ExposedPlayerRepository.kt` and `ExposedAccountRepository.kt`.

### 5. Test Reference
- `org.opentibia.canary.database.ExposedDatabaseTest`: Tests running against an in-memory H2 database, persisting an account with multiple characters and reloading.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-028"></a>

## 028 — Lua Scripting Engine Bridge (LuaJ / GraalVM)

### 1. Explanation
Embeds a Lua runtime into the Kotlin server (`src/lua/`, `data/scripts/`). Enables direct execution of Canary's massive library of existing Lua scripts (hundreds of actions, movements, spells, weapons, creatureevents, and globalevents) without rewriting them into Kotlin.

### 2. Impact
Massive ecosystem compatibility. Allows drop-in usage of the existing `data/scripts` folder from Canary, preserving community-developed quests, mechanics, and boss scripts.

### 3. Functionality Affected
- **Lua Userdata & Metatables**: Exposes `Player`, `Creature`, `Item`, `Tile`, `Position`, `Game` objects to the Lua runtime.
- **Global Event Triggers**: Registers `onUse`, `onStepIn`, `onCastSpell`, `onKill`, `onDeath`, and `onThink` callbacks.
- **Hot-Reloading**: Re-reading modified Lua scripts while the server is running (`/reload scripts`).

### 4. Agent Implementation Details
- Integrate LuaJ (`org.luaj:luaj-jse`) or GraalVM Polyglot Lua into `build.gradle.kts`.
- Implement `LuaEnvironment.kt` binding Lua globals (`Game`, `Item`, `Player`, `Position`, `Tile`).
- Implement `LuaScriptLoader.kt` scanning `data/scripts/` and registering actions/movements into `ScriptRegistry`.

### 5. Test Reference
- `org.opentibia.canary.lua.LuaScriptBridgeTest`: Executes a real Lua snippet from Canary (`Player:getHealth()`, `player:addHealth(50)`) and asserts modification of the Kotlin `Player` instance.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-029"></a>

## 029 — End-to-End Client Transition (Login to Game World & Viewport Streaming)

### 1. Explanation
Connects the initial account authentication flow (Port 7171) with the live game world connection (Port 7172). Once the client selects their character from the database-backed character list, the server authenticates the game handshake, loads the player's entity into the spatial `GameMap`, and immediately transmits the `SelfAppear` (`0x0A`) and `MapDescription` (`0x64`) viewport packets to render the world on the client's screen.

### 2. Impact
This delivers the complete player entry loop. Real OpenTibia clients can now authenticate against the database, select a character, connect to the world port, and render the initial surrounding map tiles and ground sprites.

### 3. Functionality Affected
- **Handshake Validation**: Validates session keys, character ownership, and XTEA key registration on Port 7172.
- **World Entity Entry**: Spawns the player entity onto the `GameMap` at their persisted coordinates.
- **Viewport Streaming**: Encodes the initial $18 \times 14$ tile grid with tile compaction runs, delivering surrounding terrain to the client canvas.

### 4. Agent Implementation Details
- Updated `GameHandler.kt` to transmit both `PACKET_SELF_APPEAR` and `PACKET_MAP_DESCRIPTION` upon successful connection.
- Connected `MapPacketWriter` directly to `GameHandler` for zero-allocation ByteBuf serialization.
- Validated via `FullLoginToGameWorldFlowTest.kt` simulating both TCP channels sequentially against an in-memory Flyway-migrated database.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.FullLoginToGameWorldFlowTest`: Full end-to-end integration test verifying both TCP connections (Port 7171 and 7172), RSA decryption, XTEA encryption, and map stream decoding.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-030"></a>

## 030 — Client Inbound Movement & Directional Turn Protocol

### 1. Explanation
Implements client-to-server movement and orientation packets (`src/server/network/protocol/protocolgame.cpp` lines 1734-1779):
- **Single-Step Walking**:
  - `0x65`: Move North
  - `0x66`: Move East
  - `0x67`: Move South
  - `0x68`: Move West
  - `0x6A`: Move NorthEast
  - `0x6B`: Move SouthEast
  - `0x6C`: Move SouthWest
  - `0x6D`: Move NorthWest
- **Auto-Walking & Stop**:
  - `0x64` (`parseAutoWalk`): Ingests path byte arrays from client mouse-clicks.
  - `0x69` (`playerStopAutoWalk`): Cancels pending path steps upon obstacle or key press.
- **Directional Turning (Spot Turns)**:
  - `0x6F`: Turn North
  - `0x70`: Turn East
  - `0x71`: Turn South
  - `0x72`: Turn West

### 2. Impact
Foundational gameplay operation. Governs all locomotion, position changes, and orientation updates. Misaligned step validation or missing cancellation triggers causes client rubber-banding and desynchronization.

### 3. Functionality Affected
- **Step Delay Enforcer**: Prevents speed-hacking by rejecting movements before the calculated step duration interval has elapsed.
- **Collision & Floor Triggers**: Evaluates `Tile` flags, teleporters, and stairs.
- **Spectator Broadcast**: Emits `sendCreatureMove` (`0x6D`) or `sendCreatureTurn` (`0x6B`) to all players observing the moving entity.

### 4. Agent Implementation Details
- Implement `MovePacketHandler.kt` routing opcodes `0x64..0x72`.
- Enforce step timing on `Player` within the `GameDispatcher` thread lane.
- Transmit `sendCancelWalk` (`0xB5`) when movement is blocked.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.MovePacketHandlerTest`: Simulates walking in all 8 directions, verifies position updates and spectator broadcast packets, and tests blocked movement generating `0xB5`.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-031"></a>

## 031 — Client Item Manipulation & Container Interaction Protocol

### 1. Explanation
Implements all client-to-server item usage, dragging, equipment, and container browsing opcodes (`src/server/network/protocol/protocolgame.cpp` lines 1790-1884):
- **Item Usage**:
  - `0x82` (`parseUseItem`): Using an item on ground, inventory, or inside container.
  - `0x83` (`parseUseItemEx`): Using an item with crosshair onto another target position/item (e.g. shovel on loose sand, rope on hole).
  - `0x84` (`parseUseWithCreature`): Using an item with crosshair directly onto a creature ID (e.g. health potion on party member).
  - `0x85` (`parseRotateItem`): Rotating directional items (e.g. chairs, tables).
- **Item Movement (Dragging / Throwing)**:
  - `0x78` (`parseThrow`): Moving an item from source (Position / Slot / Container) to destination (Ground / Container / Slot) with count.
- **Container Browsing**:
  - `0x87` (`parseCloseContainer`): Closing an open container window index.
  - `0x88` (`parseUpArrowContainer`): Opening the parent container holding the current container.
  - `0x95` (`parseOpenParentContainer`): Navigating up container hierarchy.

### 2. Impact
Essential for player inventory management, using equipment, looting monsters, organizing backpacks, opening chests, and using tools.

### 3. Functionality Affected
- **Distance Verification**: Validates that ground items are within reach ($\le 1$ tile for usage, $\le 7$ tiles for throwing).
- **Weight & Capacity Validation**: Rejects moves if the player capacity is exceeded.
- **Container Slot Limits**: Rejects items if the destination container is full.
- **Visual Feedback Packets**: Emits `sendAddContainerItem`, `sendRemoveContainerItem`, `sendUpdateContainerItem`, or `sendCancelWalk`.

### 4. Agent Implementation Details
- Implement `ItemInteractionPacketHandler.kt` parsing source/destination parameters.
- Route actions to `ScriptRegistry.executeAction` or default container transfer logic.
- Update `Player` inventory and broadcast tile updates to spectators.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.ItemInteractionPacketHandlerTest`: Tests dragging item from ground to backpack, moving item between containers, and using a closed backpack emitting container open packets (`0x7D`).


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-032"></a>

## 032 — Combat Targeting & Fight Modes Protocol

### 1. Explanation
Implements combat targeting, stance selection, and attack/follow state synchronization (`src/server/network/protocol/protocolgame.cpp` lines 1912-1920):
- **Opcode `0xA0` (`parseFightModes`)**:
  - Sets fight stance: `FIGHTMODE_ATTACK` (Full Attack), `FIGHTMODE_BALANCED` (Balanced), `FIGHTMODE_DEFENSE` (Full Defense).
  - Chase mode: `CHASEMODE_STANDSTILL` (Don't Chase) or `CHASEMODE_FOLLOW` (Chase Opponent).
  - Secure mode: `SECUREMODE_ON` (Cannot attack non-skulled players) or `SECUREMODE_OFF`.
  - Expert PvP mode (for newer client versions).
- **Opcode `0xA1` (`parseAttack`)**:
  - Target Creature ID: Locks combat onto an opponent or clears target if `0`.
- **Opcode `0xA2` (`parseFollow`)**:
  - Follow Creature ID: Locks walking pathfinder onto creature without attacking.
- **Server Feedback**:
  - `sendCancelTarget` (`0xA3`): Signals that target is dead, out of range, or unattackable.

### 2. Impact
Directly governs PvE and PvP combat rounds. Without these opcodes, players cannot target monsters, switch to defensive stance to mitigate damage, or chase fleeing opponents.

### 3. Functionality Affected
- **Attack Scheduler**: Initiates periodic 2000ms attack events via `CombatEngine` on the `GameDispatcher`.
- **Damage Modifiers**: Modifies attack and armor mitigation factors based on active stance (Attack: $1.0\times$ atk / $0.5\times$ def, Balanced: $0.75\times$ / $0.75\times$, Defense: $0.5\times$ / $1.0\times$).
- **Chase Walking**: Schedules auto-follow movements when the target steps away.

### 4. Agent Implementation Details
- Implement `CombatPacketHandler.kt` parsing fight mode bytes and target IDs.
- Integrate targeting state into `Player` and link to `CombatEngine`.
- Dispatch combat cancellation packets if the target dies or enters a Protection Zone (PZ).

### 5. Test Reference
- `org.opentibia.canary.network.protocol.CombatPacketHandlerTest`: Tests sending `0xA0` (setting Full Attack & Chase), sending `0xA1` (targeting monster), and asserting attack loop execution.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-033"></a>

## 033 — Inspection (Look At) & Text Message Protocol

### 1. Explanation
Implements item, creature, and tile inspection (`Look At`) along with the server-to-client textual message dispatch system (`src/server/network/protocol/protocolgame.cpp` lines 1854-1859):
- **Opcode `0x8C` (`parseLookAt`)**:
  - Sent when a player Shift+Clicks or uses "Look" on a tile position, inventory slot, or container slot.
- **Opcode `0x8D` (`parseLookInBattleList`)**:
  - Sent when a player looks at a creature directly in the Battle List window.
- **Server Text Response (`sendTextMessage` - Opcode `0xB4`)**:
  - Dispatches inspection text or status notifications to the client screen with formatting and message classes:
    - `MESSAGE_STATUS` (White text in Server Log & Console)
    - `MESSAGE_INFO_DESCR` (Green text in center screen and console)
    - `MESSAGE_STATUS_WARNING` (Red warning banner)
    - `MESSAGE_EVENT_ADVANCE` (Level / Skill up notification)

### 2. Impact
Critical for player information feedback. Informs players of equipment stats (attack, defense, armor), item descriptions, container capacities, creature names, health percentages, and player guild/vocation info.

### 3. Functionality Affected
- **Item Description Builder**: Constructs description strings based on item type attributes (e.g. *"You see a demon shield (Def:35). It weighs 26.00 oz."*).
- **Creature Description Builder**: Constructs creature info strings (e.g. *"You see a dragon. Health: 100%."* or *"You see Alice (Level 120). She is an elite knight."*).
- **Text Packet Framing**: Serializes message class byte and description string.

### 4. Agent Implementation Details
- Implement `LookPacketHandler.kt` routing `0x8C` and `0x8D`.
- Implement `DescriptionFormatter.kt` generating standard Canary description texts.
- Implement `sendTextMessage` in `GameHandler` / `ProtocolGame`.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.LookPacketHandlerTest`: Tests sending `0x8C` on an item and asserts that response contains opcode `0xB4` and the correct attribute string.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-034"></a>

## 034 — Trade, Modal Windows & Client UI Protocol

### 1. Explanation
Implements safe player-to-player trade, server modal dialog windows, and in-game UI requests (`src/server/network/protocol/protocolgame.cpp` lines 1810-1821, `modal_window/`):
- **Player-to-Player Safe Trade**:
  - `0x7D` (`parseRequestTrade`): Proposes a trade with another player standing within 2 tiles.
  - `0x7E` (`parseLookInTrade`): Inspects an offered item in the trade window.
  - `0x7F` (`playerAcceptTrade`): Accepts the trade proposal.
  - `0x80` (`playerCloseTrade`): Cancels the trade and returns items to original owners.
- **Modal Windows**:
  - `0xFA` (`parseModalWindowAnswer`): Dispatches button ID and choice ID selected by player on a server popup dialog.
  - Server Packet `0xFA` (`sendModalWindow`): Serializes title, description, buttons (e.g. "Yes", "No", "Cancel"), and selectable options list.
- **Player Text & House Windows**:
  - `0x89` (`parseTextWindow`): Reading/writing letters and blackboards.
  - `0x8A` (`parseHouseWindow`): Submitting house access lists (`aleta grav`, `aleta sio`, `aleta som`).

### 2. Impact
Essential for safe player trading, in-game NPC confirmation prompts, quest dialog choices, and house management interfaces.

### 3. Functionality Affected
- **Two-Way Trade State Machine**: Handles item locking, capacity checks, distance verification (auto-cancels if either player walks away), and atomic item exchange.
- **Modal Dialog Dispatcher**: Bridges script callbacks waiting for player UI selections.

### 4. Agent Implementation Details
- Implement `TradeManager.kt` tracking active `TradeSession` instances between two players.
- Implement `ModalWindowManager.kt` assigning modal IDs and routing player button selections back to coroutine jobs or script callbacks.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.TradeProtocolTest`: Tests trade proposal between two players, both accepting, and verifying item swap. Tests walking away cancelling trade.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-035"></a>

## 035 — Ping/Pong Heartbeat & Session Keepalive Protocol

### 1. Explanation
Implements TCP connection keepalive and round-trip latency tracking (`src/server/network/protocol/protocolgame.cpp` lines 1689-1694):
- **Opcode `0x1E` (`playerReceivePing`)**:
  - Sent periodically by the client. The server responds with `0x1E` (`sendPingBack`).
- **Opcode `0x1D` (`playerReceivePingBack`)**:
  - Response from client when server issues a latency probe packet `0x1D` (`sendPing`).
- **Opcode `0x14` (`logout`)**:
  - Client logout request when clicking "Exit" / "Logout". Validates that player is not in combat (battle sign), not with a red/white skull, and not currently moving before closing session cleanly.

### 2. Impact
Prevents dead TCP socket accumulation, tracks player ping/latency, and ensures clean logout transitions without disconnecting players in combat.

### 3. Functionality Affected
- **Connection Keepalive**: Resets Netty idle timeouts (`ReadTimeoutHandler`).
- **Logout Validator**: Rejects logout if player has battle condition, emitting red status notification *"You may not logout during or immediately after a fight!"*.
- **Graceful Disconnect**: Saves player state via `PlayerStorage`, removes creature from `GameMap`, and notifies spectators.

### 4. Agent Implementation Details
- Add handlers for `0x1D`, `0x1E`, and `0x14` in `GameHandler.kt`.
- Respond immediately to `0x1E` with ping packet `0x1E`.
- On `0x14`, check battle sign flag; if safe, persist player, remove from map, and close channel.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.KeepaliveProtocolTest`: Tests receiving `0x1E` ping and asserting immediate `0x1E` pong response. Tests `0x14` logout saving player state and closing channel.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-036"></a>

## 036 — Asset Pipeline & Canary Data Pack Resolution

### 1. Explanation
Specifies the global asset ingestion pipeline that locates, resolves, validates, and loads real Canary game content files into the server runtime:
- **Data Pack Directory Structure**:
  - `data/world/`: Contains `.otbm` binary map files, `house.xml`, and `spawns.xml`.
  - `data/items/`: Contains `items.otb`, `items.xml`, `appearances.dat`, `proficiencies.json`.
  - `data/XML/`: Contains `vocations.xml`, `outfits.xml`, `mounts.xml`, `spells.xml`, `groups.xml`.
  - `data/monster/` & `data/scripts/`: Monster definitions, loot tables, and spell scripts.
  - `data/npclib/` & `data/npc/`: NPC definitions, dialogue scripts, and merchant inventories.
- **Data Pack Resolution Policy**:
  - Configurable via `CANARY_DATA_DIR` or `server.dataDir` in `ServerConfig`.
  - Automated downloader/resolver for map packs if running inside Docker (`CANARY_MAP_URL` e.g. `otservbr.otbm`).

### 2. Impact
Critical bridge between engine code and game content. Without a unified asset pipeline, individual parsers cannot establish relations (e.g. Map loader referencing unknown Item IDs from OTB, or Spawn loader referencing unparsed Monster types).

### 3. Functionality Affected
- **Startup Boot Order**:
  1. `OtbItemsLoader` + `XmlItemLoader` $\to$ builds `ItemTypeRegistry`.
  2. `XmlVocationLoader` + `SpellsLoader` $\to$ builds vocation & spell rules.
  3. `MonsterLoader` + `NpcLoader` $\to$ builds creature templates.
  4. `OtbmMapLoader` + `HouseXmlLoader` + `SpawnXmlLoader` $\to$ builds live `GameMap`.
- **Validation**:
  - Detects duplicate item IDs, missing sprite mappings, invalid town IDs, and out-of-bounds spawn coordinates.

### 4. Agent Implementation Details
- Implement `AssetManager.kt` coordinating the sequential boot sequence across all file loaders.
- Expose health status: total items loaded, total map tiles loaded, total monster templates, total NPCs.
- Provide detailed startup logging matching Canary's benchmark timings.

### 5. Test Reference
- `org.opentibia.canary.io.AssetPipelineTest`: Boots a synthetic data directory containing sample OTB, XML, and OTBM files, asserting complete registration across all registries without exceptions.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-037"></a>

## 037 — Real Map Loading Pipeline (OTBM, House XML, Spawn XML)

### 1. Explanation
Specifies the full map initialization pipeline reading actual OpenTibia world files (`src/io/iomap.cpp`):
1. **OTBM Binary Map File (`world/otservbr.otbm`)**:
   - Header: Map width ($0..65535$), height ($0..65535$), major/minor version.
   - Towns: Town IDs, town names, temple spawn positions $(x, y, z)$.
   - Tile Areas: 256x256 spatial blocks containing individual tiles, ground items, stacked decorative items, walls, doors, and teleporters.
   - House Tiles: Tiles marked with `OTBM_HOUSETILE` linking tile coordinates to specific House IDs.
2. **Houses XML (`world/otservbr-house.xml`)**:
   - Houses: House ID, house name, entry coordinate $(x, y, z)$, rent, size in tiles, guildhall flag.
   - Doors: Associates physical door coordinates with house entry permissions.
3. **Spawns XML (`world/otservbr-spawn.xml`)**:
   - Monster spawns: Monster name, center position $(x, y, z)$, radius, respawn interval in seconds.
   - NPC spawns: NPC name, static position $(x, y, z)$.

### 2. Impact
Constructs the actual spatial reality of the game world. Maps can exceed 100,000,000 tiles and gigabytes of memory; a streaming, memory-efficient loader is mandatory.

### 3. Functionality Affected
- **Memory Footprint**: Uses primitive collections or sparse spatial matrices to avoid huge object allocation overhead for unpopulated tiles.
- **Town Registry**: Temple coordinates for player login, death respawning, and depot access.
- **House Integration**: Automatically populates `House.tiles` from parsed `OTBM_HOUSETILE` nodes and links doors to house IDs.

### 4. Agent Implementation Details
- Implement `MapPipelineService.kt` orchestrating OTBM, House XML, and Spawn XML ingestion.
- Stream large OTBM files using Netty's chunked file reader (`FileRegion` / direct memory buffers).
- Validate that all house tiles belong to a registered house entry from `house.xml`.

### 5. Test Reference
- `org.opentibia.canary.map.MapPipelineServiceTest`: Loads an integration test suite comprising an OTBM file, a matching `house.xml`, and a `spawn.xml`, verifying towns, houses, tiles, and monster spawn points are loaded and linked.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-038"></a>

## 038 — Real Items Loading Pipeline (OTB, items.xml, appearances.dat)

### 1. Explanation
Specifies the complete, high-volume item definition loading pipeline (`src/items/items.cpp`):
1. **Binary Item Definitions (`data/items/items.otb`)**:
   - Parses the complete catalogue of 30,000+ items.
   - Maps Server Item ID (internal game logic) to Client Sprite ID (visual representation).
   - Reads fundamental category flags: Ground speed, Stackable, Container, Splash, Fluid, Door, Teleport, Magic Field.
2. **Extended Item Properties (`data/items/items.xml`)**:
   - Augments items with RPG attributes: Attack, Defense, Extra Defense, Armor, Range, Weapon Type (Sword, Club, Axe, Distance, Wand/Rod), Charges, DecayTo item ID, Duration in seconds.
   - Slot placement: Head, Body, Legs, Feet, Two-Handed, Shield, Ring, Amulet, Backpack.
3. **Protobuf Appearances (`data/items/appearances.dat`)**:
   - For modern clients ($\ge 12.00$ / Tibia 13+), verifies item flags directly against CipSoft's Protobuf schema (`appearances.proto`).

### 2. Impact
Without this pipeline, equipment offers no stats, weapons do zero damage, containers cannot hold items, and corpses do not decay into skeletons or dust.

### 3. Functionality Affected
- **Item Type Registry**: Populates the global fast-lookup table (array indexed by Item ID).
- **Decay Chains**: Sets up the decay scheduler (e.g. Fresh Dragon Corpse $\to$ Rotting Corpse $\to$ Dragon Skeleton $\to$ Disappears).
- **Weapon Classification**: Drives hit chance, damage formulas, and projectile animation types.

### 4. Agent Implementation Details
- Implement `ItemPipelineService.kt` merging OTB and XML definitions into immutable, thread-safe `ItemType` instances.
- Optimize memory using primitive arrays (`Array<ItemType?>` indexed by ID $1..40000$) for $O(1)$ lookups without hash map overhead.
- Parse item abilities (elemental damage, resistance percentages, stat bonuses).

### 5. Test Reference
- `org.opentibia.canary.items.ItemPipelineServiceTest`: Loads real test snippets of `items.otb` and `items.xml`, asserting that a sword has attack, defense, weapon type, and correct sprite mapping.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-039"></a>

## 039 — Real Monster Definition & Loot Engine Pipeline

### 1. Explanation
Specifies the monster template loading and loot generation pipeline (`data/monster/`, `data/scripts/lib/register_monster_type.lua`, `src/creatures/monsters/`):
1. **Monster Type Definitions**:
   - Vitals: Experience, Health, Speed, Defense, Armor.
   - LookType: Monster sprite looktype, body, head, legs, feet colors, corpse item ID.
   - Immunities: Paralyze, Fire, Earth, Energy, Ice, Holy, Death, Drown, Lifedrain.
   - Voices: Randomized yelling sentences (e.g. *"FCHHHHH"*, *"GROOOWL"*).
   - Combat Spells: Melee, Fire wave, Energy beam, Great fireball, Self-healing, Summoning minions.
2. **Loot Table Engine**:
   - Items dropped on death with drop chance in basis points ($1..100,000$ where $100,000 = 100\%$).
   - Max count for stackable items (gold coins, creature products).
   - Container loot nesting (dropping items inside a bag inside the monster corpse).

### 2. Impact
Drives all PvE encounters, hunting economics, experience gain, and equipment drop rates across the entire server.

### 3. Functionality Affected
- **Monster Spawner**: Instantiates monsters dynamically using the registered `MonsterType` template.
- **Corpse Generation**: When monster dies, drops the configured corpse item on the tile and rolls the loot table.
- **Loot Multipliers**: Applies server loot rate and active Prey/Bosstiary/Hazard multipliers to drop rolls.

### 4. Agent Implementation Details
- Implement `MonsterPipelineService.kt` loading monster definitions from XML or Lua registration tables.
- Implement `LootEngine.kt` simulating drop rolls using cryptographically secure or pseudo-random number generators (`ThreadLocalRandom`).
- Automatically nest generated loot into the spawned corpse container.

### 5. Test Reference
- `org.opentibia.canary.creatures.MonsterPipelineServiceTest`: Loads a sample monster definition (e.g. Dragon), kills monster, and verifies corpse generation with rolled loot items.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-040"></a>

## 040 — Real NPC Dialogue, Scripts & Shop Data Pipeline

### 1. Explanation
Specifies the full NPC ingestion pipeline reading real Canary NPC definitions, shop inventories, travel routes, and conversation scripts (`data/npc/`, `data/npclib/`):
1. **NPC XML / Lua Definitions (`data/npc/*.xml` or `.lua`)**:
   - Identity: Name, looktype, gender.
   - Spawn coordinates: Static position on map or patrol path.
   - Speech parameters: Walk radius, floor change awareness, yell intervals.
2. **Shop Catalogues**:
   - Buy list: Items the NPC sells to players (Item ID, cost in gold).
   - Sell list: Items the NPC buys from players (Item ID, reward in gold).
3. **Dialogue & Script Hooks (`data/npclib/keyword_handler.lua`)**:
   - Greeting triggers (`hi`, `hello`).
   - Keyword trees (`trade`, `deposit`, `withdraw`, `balance`, `travel`, `yes`, `no`).
   - Quest state condition checks (evaluating player storage values).

### 2. Impact
Powers all towns, merchant economies, travel networks (ships, carpets), banking systems, blessings, and quest hand-ins.

### 3. Functionality Affected
- **NPC Spawner**: Spawns NPCs into their fixed map coordinates during server boot.
- **Shop Inventory Cache**: Populates the merchant catalog displayed in client trade windows (`0x7A`).
- **Bank System Integration**: Interacts with player bank balances stored in the database.

### 4. Agent Implementation Details
- Implement `NpcPipelineService.kt` parsing NPC definitions and populating `Npc` instances on `GameMap`.
- Implement `NpcShopRegistry.kt` keeping buy/sell price catalogs per NPC name.
- Bind dialogue triggers to the `ScriptRegistry` or embedded Lua engine.

### 5. Test Reference
- `org.opentibia.canary.creatures.NpcPipelineServiceTest`: Loads a sample merchant NPC definition, asserts buy/sell items are available in the shop catalogue, and verifies bank deposit keyword responses.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-041"></a>

## 041 — Complete GamePacketRouter & Dispatcher for handleGamePacket

### 1. Explanation
Implements the central packet routing architecture that connects Netty I/O worker threads with the game simulation loop, replacing the stub inside `GameHandler.handleGamePacket(ctx, msg)`:
```kotlin
private fun handleGamePacket(ctx: ChannelHandlerContext, msg: ByteBuf) {
    GamePacketRouter.route(player, msg, gameMap, dispatcher, scripts)
}
```
In Canary, this corresponds to `ProtocolGame::parsePacket(NetworkMessage &msg)` and `ProtocolGame::parsePacketFromDispatcher(NetworkMessage &msg, uint8_t recvbyte)`.

### 2. Impact
Critical core bridge. Without this implementation, incoming packets sent after login (walking, talking, using items, attacking, opening backpacks) sit unread in the socket or trigger channel disconnects.

### 3. Functionality Affected & Opcode Routing Table

| Opcode | Packet Name | Destination Handler & Logic |
|---|---|---|
| **`0x14`** | Logout Request | `KeepaliveHandler.canLogout(player)` $\to$ save player & close channel |
| **`0x1D` / `0x1E`** | Ping / PingBack | `KeepaliveHandler.handlePing(player)` $\to$ immediate pong packet |
| **`0x64`** | AutoWalk | `MovePacketHandler.handleAutoWalk(player, msg, map)` $\to$ multi-step path |
| **`0x65..0x68`** | Move Cardinal | `MovePacketHandler.handle(player, opcode, msg, map)` (N, E, S, W) |
| **`0x69`** | Stop AutoWalk | Cancels current walking job on `GameDispatcher` |
| **`0x6A..0x6D`** | Move Diagonal | `MovePacketHandler.handle(player, opcode, msg, map)` (NE, SE, SW, NW) |
| **`0x6F..0x72`** | Spot Turns | `MovePacketHandler.handle(player, opcode, msg, map)` (Turn N, E, S, W) |
| **`0x78`** | Throw / Drag Item | `ItemPacketHandler.handleThrow(player, msg, map)` |
| **`0x82`** | Use Item | `ItemPacketHandler.handleUse(player, msg, map, scripts)` (`onUse`) |
| **`0x83`** | Use Item Ex | Crosshair use on tile/item (`onUseWith`) |
| **`0x84`** | Use on Creature | Crosshair use on creature ID |
| **`0x85`** | Rotate Item | Directional rotation of objects |
| **`0x87`** | Close Container | Closes open backpack index |
| **`0x88`** | Up Arrow Container | Navigates to parent container |
| **`0x8C` / `0x8D`** | Look At / Battle Look | `LookPacketHandler.handleLook(player, msg, map)` $\to$ `0xB4` text response |
| **`0x96`** | Player Say | `ChatManager.broadcastSpeech(player, type, text, listeners)` |
| **`0xA0`** | Fight Modes | `CombatPacketHandler.handleFightModes(player, msg)` |
| **`0xA1`** | Attack Target | `CombatPacketHandler.handleAttack(player, msg, targets)` |
| **`0xA2`** | Follow Target | Sets follow target without attack |
| **`0xFA`** | Modal Window Answer | `ModalWindowManager.handleResponse(player, windowId, buttonId, choiceId)` |

### 4. Agent Implementation Details
- Implement `GamePacketRouter.kt` with a single entry point `route(player: Player, buffer: ByteBuf, context: GameContext)`.
- Loop over multi-packet frames: Tibia bundles multiple opcodes into a single TCP packet. While `buffer.isReadable`, read `opcode = buffer.readUnsignedByte()` and dispatch to the designated handler.
- Concurrency rule: Network packets decode immutably on the Netty I/O thread, then dispatch mutations to `GameDispatcher.dispatch(DispatcherLane.WorldCommit) { ... }`.

### 5. Test Reference
- `org.opentibia.canary.network.protocol.GamePacketRouterTest`: Feeds a ByteBuf containing chained packets (Turn North + Move East + Say "Hello") and asserts all handlers execute in correct order.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-042"></a>

## 042 — Argon2id Password Hashing & Security Engine

### 1. Explanation
Implements **Argon2id** password hashing and verification (`src/security/argon.cpp`). Canary and modern web AACs (MyAAC, Znote AAC) generate database password hashes conforming to PHC format strings:
`$argon2id$v=19$m=65536,t=4,p=1$<salt>$<hash>`

### 2. Impact
Security compliance and real web AAC compatibility. Prevents plaintext password compromises and enables players registered on web portals to log into the Kotlin game server directly.

### 3. Functionality Affected
- **Account Authentication**: `AccountRepository.authenticate(name, password)` passes the plaintext login attempt through `Argon2Security.verify(attempt, hash)`.
- **Legacy Fallback**: If the stored hash is legacy SHA1 (40 characters) or MD5, verifies using fallback and flags account for upgrade on next successful authentication.

### 4. Agent Implementation Details
- Add `de.mkammerer:argon2-jvm:2.11` or BouncyCastle Argon2 to `build.gradle.kts`.
- Implement `Argon2Security.kt` with configurable memory cost (`m_cost = 65536`), time cost (`t_cost = 4`), and parallelism (`parallelism = 1`).
- Connect `Argon2Security` to `JdbcAccountRepository`.

### 5. Test Reference
- `org.opentibia.canary.security.Argon2SecurityTest`: Generates an Argon2id hash for a test password, verifies matching password succeeds, and verifies mismatching password fails.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-043"></a>

## 043 — Network Rate Limiting, Flood Protection & Anti-DoS

### 1. Explanation
Protects the server against malicious socket exhaustion, packet flooding, slowloris attacks, and connection spam (`src/server/network/connection/connection.cpp` lines 305-316). Enforces maximum packets per second limits (`MAX_PACKETS_PER_SECOND`) and max connections per IP subnet.

### 2. Impact
Production stability. Without rate limiting, a single bot client can send 10,000 TCP packets per second, saturating CPU cycles, exhausting Netty memory pools, and causing latency spikes for legitimate players.

### 3. Functionality Affected
- **Token Bucket Limiter**: Tracks packet counts per connection over a moving 1-second window.
- **Auto-Disconnect & Ban**: Automatically severs connections exceeding 50–100 packets/sec and logs the offending IP.
- **Idle Timeout**: Automatically closes sockets that remain silent or incomplete for $>30$ seconds (`CONNECTION_READ_TIMEOUT`).

### 4. Agent Implementation Details
- Implement `RateLimiterHandler.kt` as an early ChannelInboundHandler in Netty's pipeline.
- Implement token-bucket algorithm per socket channel.
- If threshold is violated, log warning and issue immediate `ctx.close()`.

### 5. Test Reference
- `org.opentibia.canary.network.codec.RateLimiterHandlerTest`: Floods a channel with 200 packets in 100ms and verifies the handler closes the connection and rejects further packets.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-044"></a>

## 044 — High-Performance Primitive Spatial Map (MapSector Chunking)

### 1. Explanation
Replaces sparse object hash maps for `Tile` storage with a chunked 2D/3D sector architecture matching Canary's `MapSector` (`src/map/utils/mapsector.hpp`). The global map is divided into 32x32 spatial sectors; unallocated wilderness areas consume zero memory, while populated sectors store tiles in fixed 2D arrays.

### 2. Impact
Solves the primary memory bottleneck on the JVM. In a flat `Map<Position, Tile>`, 50,000,000 tiles require $\approx 8\text{ GB}$ just for map entry references. In sector arrays, spatial lookup is $O(1)$ pointer math, and memory consumption drops by over 80%.

### 3. Functionality Affected
- **Sector Math**: Sector coordinates $(sx, sy, sz) = (x / 32, y / 32, z)$.
- **Internal Tile Array**: Inside each sector, tiles are stored in a flat `Array<Tile?>(32 * 32)` indexed by $(x \% 32) + (y \% 32) * 32$.
- **Fast Viewport Queries**: Spectator queries only check the 4 overlapping sectors rather than searching all active tiles.

### 4. Agent Implementation Details
- Implement `MapSector.kt` holding `Array<Tile?>(1024)`.
- Update `GameMap.kt` to index `MapSector` by a packed 64-bit integer key `(sz shl 32) or (sx shl 16) or sy`.
- Expose fast range iterator yielding non-null tiles within a bounding box.

### 5. Test Reference
- `org.opentibia.canary.map.MapSectorTest`: Populates 1,000 tiles across 4 adjacent sectors, asserts correct tile recovery, and verifies unallocated sectors consume zero allocations.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-045"></a>

## 045 — Atomic Server Save & OS Signal Shutdown Handler (SIGTERM/SIGINT)

### 1. Explanation
Implements graceful server shutdown and atomic persistence routines (`src/server/signals.cpp`, `src/game/scheduling/save_manager.cpp`). Catches Docker container termination signals (`SIGTERM`) and manual console interrupts (`SIGINT`), stops network acceptors, broadcasts warnings, and saves all players and world entities in an atomic PostgreSQL transaction.

### 2. Impact
Data loss prevention. Hard-killing a server process during a reboot rolls back player items, causes duplicate item exploits, and corrupts house auctions.

### 3. Functionality Affected
- **Signal Trap**: Binds `sun.misc.Signal` or JVM `Runtime.getRuntime().addShutdownHook`.
- **Shutdown Phases**:
  1. Set game state to `GAME_STATE_SHUTDOWN`.
  2. Reject new connections; disconnect active clients cleanly.
  3. Batch execute `UPDATE players` for all online players.
  4. Flush houses, depot items, and market escrows.
  5. Close HikariCP connection pool and Netty EventLoop groups.

### 4. Agent Implementation Details
- Implement `SaveManager.kt` scheduling background auto-saves every 15 minutes.
- Implement `ShutdownHookHandler.kt` executing the ordered shutdown phases within a guaranteed 10-second timeout.

### 5. Test Reference
- `org.opentibia.canary.game.scheduling.ShutdownHandlerTest`: Simulates shutdown trigger, verifies all active in-memory players are flushed to database, and verifies connection closure.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-046"></a>

## 046 — Player Death Penalty, Blessings & Corpse Drop Mechanics

### 1. Explanation
Implements RPG death consequences (`src/game/game.cpp`, `src/enums/player_blessings.hpp`). When a player dies (health reaches 0), the engine calculates experience loss, skill point loss, inventory drop chances based on active blessings, spawns a dead player corpse with dropped items, and teleports the player back to their town temple.

### 2. Impact
Core RPG high-stakes death loop. Governs blessing economics (Twist of Fate, 5 standard blessings), corpse looting, and death logging.

### 3. Functionality Affected
- **Experience & Skill Reduction**: Standard Tibia loss: $10\%$ base reduction, reduced by $8\%$ per blessing (down to $2\%$ with all 5 blessings).
- **Equipment Drop**: $100\%$ backpack drop, $10\%$ chance per equipment slot without blessings (reduced to $0\%$ with full blessings).
- **Corpse Generation**: Spawns player corpse item on death tile, holds dropped items, and labels description with killer name (e.g. *"You see the dead human (Vol: 10). He was killed by a Dragon."*).
- **Temple Respawn**: Resets health/mana and moves player to `town.templePosition`.

### 4. Agent Implementation Details
- Implement `DeathPenaltyEngine.kt` evaluating active blessings on `Player`.
- Implement `PlayerDeathEvent` creating corpse containers on `GameMap`.
- Teleport player to temple position and reset conditions.

### 5. Test Reference
- `org.opentibia.canary.game.DeathPenaltyTest`: Tests unblessed player dying drops backpack and loses 10% exp, and full-blessed player loses reduced exp and keeps all items.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-047"></a>

## 047 — Prometheus Metrics, Observability & Webhook Alerts

### 1. Explanation
Implements enterprise observability and alerts (`src/server/network/webhook/`, metrics telemetry). Exposes a lightweight Prometheus `/metrics` HTTP endpoint scraping internal server health, tick latency, memory allocations, and emits Discord webhook notifications on server lifecycle events.

### 2. Impact
Production monitoring. Operators can integrate Prometheus & Grafana dashboards to monitor tick lag spikes, memory growth, player count curves, and receive instant Discord alerts when crashes or server restarts occur.

### 3. Functionality Affected
- **Prometheus Scrape Endpoint**:
  - `canary_players_online_total`: Gauge tracking active player count.
  - `canary_game_tick_duration_ms`: Histogram measuring `GameDispatcher` loop cycle latency.
  - `canary_netty_direct_memory_bytes`: Tracking Netty off-heap buffer allocations.
  - `jvm_memory_used_bytes` & `jvm_gc_pause_seconds`.
- **Discord Webhooks**:
  - Emits embed messages to configured Discord Webhook URLs on server startup, shutdown, and error alerts.

### 4. Agent Implementation Details
- Add `io.micrometer:micrometer-registry-prometheus` to `build.gradle.kts`.
- Implement `MetricsServer.kt` serving Prometheus scrapes on port 9090.
- Implement `DiscordWebhookClient.kt` dispatching async JSON payload notifications.

### 5. Test Reference
- `org.opentibia.canary.monitoring.MetricsServerTest`: Queries the `/metrics` endpoint and verifies presence of `canary_players_online_total` and tick metrics.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-048"></a>

## 048 — Banking, Currency & Vitals Regeneration Engines

### 1. Explanation
Implements OpenTibia's core economic currency conversion, bank balances, and vocation vitals regeneration (`src/game/bank/bank.cpp`, `src/creatures/players/player.hpp`):
- **Currency Conversion (`Currency.kt`)**:
  - Automatically calculates exchanges between Gold Coins (ID 2148, 1 gp), Platinum Coins (ID 2152, 100 gp), and Crystal Coins (ID 2160, 10,000 gp).
  - Decomposes raw gold quantities into exact currency denominations.
- **Bank Service (`BankService.kt`)**:
  - Deposits, withdrawals, and account-to-account player wire transfers with balance validation.
- **Vitals Regeneration Engine (`VitalsRegenEngine.kt`)**:
  - Periodic tick loop restoring health and mana points up to maximum pool capacities based on vocation rules.

### 2. Impact
Powers NPC banking dialogues (`deposit`, `withdraw`, `transfer`), merchant transactions, and natural player survival/recovery out of combat.

### 3. Functionality Affected
- **Bank Operations**: Thread-safe balance ledger preventing negative balances or overdrafts.
- **Vitals Loop**: Bound checks ensuring health and mana do not overshoot player maximums or revive dead players.

### 4. Test Reference
- `org.opentibia.canary.game.bank.BankSystemTest`: Tests currency conversions, deposits, withdrawals, and inter-player transfers.
- `org.opentibia.canary.game.VitalsRegenerationTest`: Tests periodic health/mana regeneration up to maximum caps.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-049"></a>

## 049 — Spells, Magic Engine & Player QuestLog System

### 1. Explanation
Implements OpenTibia's spellcasting mechanisms, mana validation, and quest progression tracking (`src/creatures/combat/spells.hpp`, `src/io/player_storage_repository.hpp`):
- **Spell Engine (`SpellRegistry.kt`)**:
  - Validates caster level requirement and mana consumption.
  - Registers instant words (e.g. `exura`, `exura vita`, `exevo flam hur`).
  - Executes functional spell lambdas upon valid cast, consuming player mana.
- **Player Storage & Quest Log (`QuestManager.kt`)**:
  - Key-value integer storage per character (Canary's `getStorageValue` / `setStorageValue`).
  - Unset storage keys return standard `-1`.
  - Maps mission state progressions (`QuestMission`) to quest log entries and mission descriptions.

### 2. Impact
Powers character magic development, rune casting, healing loops, and questlines (Annihilator, Pits of Inferno, Desert Quest, custom quests).

### 3. Functionality Affected
- **Spellbook**: Prevents spellcasting when level is too low or mana is insufficient.
- **Quest State Machine**: Evaluates mission completions based on storage values set by actions, chests, or NPC conversations.

### 4. Test Reference
- `org.opentibia.canary.spells.SpellSystemTest`: Tests instant spell mana consumption, level gating, and health addition.
- `org.opentibia.canary.quest.QuestStorageTest`: Tests storage key-value transitions and multi-mission quest log evaluation.


### Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**:

---

<a id="feature-050"></a>

## 050 — Wheel of Destiny (Skill Wheel) Engine

### 1. Explanation
Implements CipSoft's Wheel of Destiny (`src/io/io_wheel.cpp`, `src/creatures/players/components/wheel/`, `src/enums/player_wheel.hpp`). Characters level 50+ earn Promotion Points (1 per level > 50) allocated across Canary's **37-slot** per-vocation perk tree — Dedication (raw stats), Conviction (secondary passives), and Revelation (ultimate spells/augmentations) — plus **gem sockets** with basic/supreme modifiers. Client opcodes: `0x61` (Open Wheel), `0x62` (Save Wheel), `0xE7` (Wheel Gem Action).

### 2. Impact
Endgame character customization and power progression for level 50..1000+ characters.

### 3. Functionality Affected
- **Point Budget**: `PlayerWheel::getWheelPoints` / `getUnusedPoints`.
- **37-slot tree**: per-vocation definitions and prerequisite gating (`IOWheel`, `wheel_definitions.hpp`).
- **Gem sockets**: basic/supreme gem modifiers (`wheel_gems.cpp/.hpp`).
- **Revelation/spell modifications** (`wheel_spells.cpp`).
- **Protocol**: `parseOpenWheel` (0x61), `parseSaveWheel` (0x62), `parseWheelGemAction` (0xE7); `Game::playerOpenWheel/SaveWheel/WheelGemAction`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only — parity):** port `IOWheel`, `PlayerWheel`, `wheel_gems`, `wheel_spells`, `wheel_definitions`, `enums/player_wheel.hpp`, the three protocol handlers, the `Game` entry points, and `Player::wheelUnlockScroll`. **Replace** the existing non-parity `systems/WheelOfDestinyEngine.kt` (4 quadrants / linear bonuses — not Canary's 37-slot tree).
- **Stays Lua (do not port):** `data/scripts/actions/items/wheel_scrolls.lua` (item action calling `player:wheelUnlockScroll`).

### 5. Test Reference
- `org.opentibia.canary.systems.WheelOfDestinyTest` (rewrite): point budget per level, 37-slot allocation, prerequisite gating, gem modifier application, protocol 0x61/0x62 serialization.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/io/io_wheel.cpp`, `src/io/io_wheel.hpp`, `src/creatures/players/components/wheel/player_wheel.cpp`, `src/creatures/players/components/wheel/player_wheel.hpp`, `src/creatures/players/components/wheel/wheel_gems.cpp`, `.../wheel_gems.hpp`, `.../wheel_spells.cpp`, `.../wheel_definitions.hpp`, `src/enums/player_wheel.hpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/actions/items/wheel_scrolls.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `IOWheel::loadFromJson`, `PlayerWheel::getWheelPoints`/`getUnusedPoints`/`addPromotionPoints`, `parseOpenWheel` (0x61), `parseSaveWheel` (0x62), `parseWheelGemAction` (0xE7)

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-051"></a>

## 051 — Exaltation Forge (Item Tiers & Fusion) Engine

### 1. Explanation
Implements the Exaltation Forge (`src/creatures/players/components/player_forge_history.*`, `src/enums/forge_conversion.hpp`). Players fuse duplicate items using Dust, Slivers, and Exalted Cores to raise equipment Tiers (1..10). Tiered items trigger passive combat procs: **Onslaught** (weapons, +crit damage), **Ruse** (armor, dodge), **Momentum** (helmets, cooldown reset), **Transcendence** (legs/boots).

### 2. Impact
Endgame item sink, economy driver, and equipment progression.

### 3. Functionality Affected
- **Fusion engine**: success probability and dust/gold cost per tier attempt (`Player::forgeFuseItems`).
- **Tier transfer / resource conversion**: `forgeTransferItemTier`, `forgeResourceConversion`.
- **Dust ledger**: `set/getAddForgeDusts`, `getForgeDustLevel`, `PlayerForgeHistory`.
- **Item tiers/classification**: item tier + classification attributes.
- **Protocol**: `parseForgeEnter`, `sendOpenForge`, `sendForgeResult`, `getForgeInfoMap`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port `PlayerForgeHistory`, the `Player` forge methods, item tier/classification attributes, the forge protocol handlers, and the `Game::playerForge*` methods. Bind `player:addForgeDusts/getForgeDusts/getForgeDustLevel`, `Game.createItemClassification`, and `Monster` forge-stack accessors for Lua.
- **Stays Lua (do not port):** `data/libs/systems/exaltation_forge.lua` (ForgeMonster death dust reward, fiendish/influenced spawn selection), `data/scripts/systems/item_tiers.lua` (classification price table registered via `Game.createItemClassification`).

### 5. Test Reference
- `org.opentibia.canary.systems.ExaltationForgeTest`: fusion success advancing tier, failure consuming dust, core protection preventing loss, tier transfer, and forge protocol serialization.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/enums/forge_conversion.hpp`, `src/creatures/players/components/player_forge_history.hpp`, `.../player_forge_history.cpp`, `src/creatures/players/player.hpp` (forge API ~L1409-1430), `src/items/item.cpp` (tier attrs), `src/game/game.cpp` (`playerForge*`), `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/exaltation_forge.lua`, `data/scripts/systems/item_tiers.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Player::forgeFuseItems`, `forgeTransferItemTier`, `forgeResourceConversion`, `parseForgeEnter`, `sendOpenForge`, `sendForgeResult`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-052"></a>

## 052 — Bestiary & Charm Runes System

### 1. Explanation
Implements CipSoft's Cyclopedia Bestiary and Charm Runes (`src/io/iobestiary.cpp`, `src/io/iobestiary.hpp`). Tracks monster kills across 3 unlock stages (e.g. 25/250/1000), reveals monster details (HP, loot, weaknesses), awards Charm Points, and unlocks socketable Charm Runes — offensive (Zap, Freeze, Wound, Enflame, Poison, Curse, Divine Wrath) and defensive/utility (Dodge, Parry, Low Blow).

### 2. Impact
Core mid-to-endgame hunting incentive; affects combat DPS on high-health creatures.

### 3. Functionality Affected
- **Kill progress**: `Player::addBestiaryKillCount` / `getBestiaryKillCount`, stage completion.
- **Charm state**: `charmsArray`, `charmPoints`, `setCharmTier`, `parseRacebyCharm`.
- **Monster metadata**: `MonsterType.info.bestiaryRace/Class/Stars`.
- **Protocol**: `parseBestiarySendRaces`, `parseBestiarysendMonsterData`, `parseBestiarySendCreatures`, `parseSendBuyCharmRune`, `sendBestiaryCharms`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port `IOBestiary`, `Charm`, the `Player` bestiary/charm state, the bestiary protocol handlers, and `Game().getBestiaryList()`. Bind `Game.createBestiaryCharm`, `player:addBestiaryKillCount/getBestiaryKillCount/charmPoints`.
- **Stays Lua (do not port):** `data/scripts/systems/bestiary_charms.lua` (the 25 charm configs registered via `Game.createBestiaryCharm(id):register(config)`), `data/scripts/lib/register_monster_type.lua` (bestiary mask).

### 5. Test Reference
- `org.opentibia.canary.systems.BestiaryTest`: kill thresholds awarding charm points, socketing a charm to a completed creature, charm combat proc, and bestiary protocol serialization.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/io/iobestiary.cpp`, `src/io/iobestiary.hpp`, `src/creatures/players/player.hpp` (bestiary/charm state), `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/systems/bestiary_charms.lua`, `data/scripts/lib/register_monster_type.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `IOBestiary::parseCharm`, `Player::addBestiaryKillCount`, `Game::createBestiaryCharm`, `parseBestiarySendRaces`, `parseSendBuyCharmRune`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-053"></a>

## 053 — In-Game Store (GameStore) & Tibia Coins System

### 1. Explanation
Implements the in-game premium shop (`data/modules/scripts/gamestore/*`, `data/libs/gamestore/*`). Players spend Tibia Coins / Transferable Coins on character services, cosmetics, and supplies. Applies to account coin balances (`src/enums/account_coins.hpp`, `src/account/account.*`).

### 2. Impact
Server monetization, premium item delivery, and automated service transactions.

### 3. Functionality Affected
- **Coin ledger**: `coins`, `coins_tournament`, `coins_transferable` account columns (`Account::getCoins/addCoins/removeCoins/registerCoinTransaction`).
- **Transferable balance**: `Player::coinTransferableBalance`.
- **Balance packet**: `Game::sendResourcesBalance` / resources packet.
- **Opcode layer**: store opcodes (`0xEF`, `0xFA`..`0xFE`) are **commented out / WIP** in Canary — no C++ store engine file exists.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the account coin primitives (`Account::getCoins/addCoins/removeCoins/registerCoinTransaction`), `CoinType`/`CoinTransactionType` enums, the three account coin DB columns, `Player::coinTransferableBalance`, and the resource-balance packet. The store opcode handlers stay unimplemented until Canary enables them.
- **Stays Lua (do not port):** the entire store catalog/purchase/delivery logic — `data/libs/gamestore/{store,parsers,purchases,senders,helpers,player,taskboard,constants}.lua` and `data/modules/scripts/gamestore/*` + `catalog/*`.

### 5. Test Reference
- `org.opentibia.canary.account.CoinLedgerTest`: adding/removing coins of each `CoinType`, transferable balance tracking, and resource-balance packet serialization.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/enums/account_coins.hpp`, `src/enums/account_type.hpp`, `src/account/account.hpp`, `src/account/account.cpp`, `src/database/account_repository_db.cpp`, `src/creatures/players/player.hpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/gamestore/*`, `data/modules/scripts/gamestore/*`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Account::getCoins/addCoins/removeCoins/registerCoinTransaction`, `Game::sendResourcesBalance`, `CoinType`, `CoinTransactionType`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-054"></a>

## 054 — Gnomprona Hazard System & Primal Dungeons

### 1. Explanation
Implements CipSoft's Hazard difficulty scaling (`src/game/game.cpp` `handleHazardSystemAttack`, monster/player hazard state). Used in Gnomprona primal hunting zones. Hazard level (1..12+) increases monster damage/defense and loot bonuses.

### 2. Impact
Pinnacle endgame team-hunting difficulty modifier.

### 3. Functionality Affected
- **Monster hazard flags**: `Monster::getHazard/setHazard/hazardCrit/hazardDodge/hazardDamageBoost/hazardDefenseBoost`.
- **Player hazard points**: `Player::set/getHazardSystemPoints`, `parseAttackRecvHazardSystem`, `parseAttackDealtHazardSystem`, exp bonus.
- **Combat hooks**: `Game::handleHazardSystemAttack`, dodge roll.
- **Config**: `HAZARD_*` / `TOGGLE_HAZARDSYSTEM`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the Monster hazard flags, Player hazard points + attack hooks, `Game::handleHazardSystemAttack`, and hazard config. Bind `monster:hazard()/hazardCrit()/hazardDodge()/hazardDamageBoost()/hazardDefenseBoost()` and `player:setHazardSystemPoints/updateHazard`.
- **Stays Lua (do not port):** `data/libs/systems/hazard.lua` (`Hazard` class, KV levels, `ZoneEvent` enter/leave, `HazardMonster.onSpawn`), `data/scripts/eventcallbacks/monster/ondroploot_hazard.lua` (extra loot rolls).

### 5. Test Reference
- `org.opentibia.canary.systems.HazardSystemTest`: monster hazard flags scaling damage, player hazard points accumulating, and the `handleHazardSystemAttack` dodge/crit rolls.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/monsters/monster.hpp`, `src/creatures/monsters/monster.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/game/game.cpp`, `src/config/config_enums.hpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/hazard.lua`, `data/scripts/eventcallbacks/monster/ondroploot_hazard.lua`, `data/scripts/lib/register_monster_type.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Game::handleHazardSystemAttack`, `Monster::getHazard/setHazard`, `Player::parseAttackDealtHazardSystem`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-055"></a>

## 055 — Daily Reward Shrine & Consecutive Streak System

### 1. Explanation
Implements the Daily Reward Wall (`data/libs/systems/daily_reward.lua`, `data/modules/scripts/daily_reward/daily_reward.lua`). Players claim rewards once per server day and advance a consecutive streak (Day 1..7+), gaining resting-zone regeneration and consumable rewards.

### 2. Impact
Daily player retention driver and resting-zone passive regeneration.

### 3. Functionality Affected
- **Player state**: `Player::setDailyReward` / `isDailyReward`, `DailyRewardBonus`/`DailyRewardStatus` enums.
- **KV streak**: `daily-reward` key/value used by regeneration (`condition.cpp`), cyclopedia streak, player titles.
- **Persistence**: `iologindata_save_player.cpp`, `iologindata_load_player.cpp`.
- **Protocol**: cyclopedia daily-streak packet.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Player` daily-reward state and enums, the `daily-reward` KV persistence, the regeneration-bonus application, and the cyclopedia streak packet. Bind `player:setDailyReward` / `player:getDailyReward`.
- **Stays Lua (do not port):** `data/libs/systems/daily_reward.lua` (stamina/soul regen events, global storage), `data/modules/scripts/daily_reward/daily_reward.lua` (shrine UI/claim), `data/scripts/actions/objects/daily_reward_shrine.lua` (item action).

### 5. Test Reference
- `org.opentibia.canary.systems.DailyRewardTest`: `setDailyReward` marking streak state, same-day re-claim rejection, streak advance, and KV persistence round-trip.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/creatures_definitions.hpp` (`DailyRewardBonus`/`DailyRewardStatus`), `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/creatures/combat/condition.cpp`, `src/io/iologindata_save_player.cpp`, `src/io/iologindata_load_player.cpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/daily_reward.lua`, `data/modules/scripts/daily_reward/daily_reward.lua`, `data/scripts/actions/objects/daily_reward_shrine.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Player::setDailyReward`, `Player::isDailyReward`, cyclopedia daily-streak send

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-056"></a>

## 056 — Boss Reward Chest & Damage Participation Scoring

### 1. Explanation
Implements CipSoft's Boss Reward Container & participation loot scoring (`src/items/containers/rewards/`). On boss/nemesis death, loot is delivered per-participant into each adventurer's guild Reward Chest instead of dropping on the floor, scored by damage dealt/taken and healing.

### 2. Impact
Eliminates loot theft and griefing in large boss encounters.

### 3. Functionality Affected
- **Reward containers**: `class RewardChest`, `class Reward` (`rewardchest.hpp/.cpp`, `reward.hpp/.cpp`).
- **Player chest API**: `Player::getRewardChest/getReward/addReward`.
- **Collection**: `Game::collectRewardChestItems`, `Game::playerRewardChestCollect`; `actions.cpp` opening a reward container.
- **Protocol**: opcode `0xFF`, `parseRewardChestCollect`.
- **Config**: `REWARD_CHEST_COLLECT_ENABLED`, `_MAX_COLLECT_ITEMS`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port `Reward`/`RewardChest`, `Player::getReward/getRewardChest/addReward`, `Game::collectRewardChestItems/playerRewardChestCollect`, the `0xFF` protocol handler, and config. Bind `container:registerReward/addRewardBossItems`, `creature:getDamageMap/getTargetList`, `monsterType:getBossReward/generateLootRoll`.
- **Stays Lua (do not port):** `data/scripts/systems/reward_chest.lua` (BossDeath score/participation, `BossParticipation.onHealthChange`), `data/libs/systems/reward_boss.lua` (`InsertRewardItems` DB insert, `GetPlayerStats`, `ResetAndSetTargetList`).

### 5. Test Reference
- `org.opentibia.canary.systems.RewardChestTest`: adding a reward to a player's chest, collecting items through `collectRewardChestItems`, collect-limit config, and `0xFF` serialization.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/items/containers/rewards/reward.hpp`, `src/items/containers/rewards/reward.cpp`, `src/items/containers/rewards/rewardchest.hpp`, `src/items/containers/rewards/rewardchest.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/game/game.cpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/systems/reward_chest.lua`, `data/libs/systems/reward_boss.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Game::collectRewardChestItems`, `Player::addReward`, `parseRewardChestCollect` (0xFF)

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-057"></a>

## 057 — Familiars Vocation Summon System

### 1. Explanation
Implements CipSoft's level 200+ Vocation Familiars (`src/creatures/players/grouping/familiars.*`, `data/libs/systems/familiar.lua`, `data/XML/familiars.xml`). Each vocation summons a timed companion (Knight/Paladin/Sorcerer/Druid/Monk familiar). The C++ engine stores familiar definitions (XML) and player familiar state; the summon spells and timers are Lua.

### 2. Impact
Mid-to-endgame solo hunting combat amplifier and boss support.

### 3. Functionality Affected
- **Definitions**: `class Familiars`/`Familiar`/`FamiliarEntry` (XML load).
- **Player state**: `canFamiliar/addFamiliar/removeFamiliar/getFamiliar/setFamiliarLooktype`; storage range.
- **Combat**: `Monster::isFamiliar`, `Player::isFamiliar`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Familiars` XML loader, the `Player` familiar collections + storage range, and the combat familiar flags.
- **Stays Lua (do not port):** `data/libs/systems/familiar.lua` (FAMILIAR_ID map, timers, `RemoveFamiliar`), `data/scripts/spells/familiar/*` (summon spells), `data/scripts/creaturescripts/familiar/*`, `data/XML/familiars.xml` (data).

### 5. Test Reference
- `org.opentibia.canary.systems.FamiliarSystemTest`: loading familiar definitions from XML, add/remove/has familiar on `Player`, and storage-range assignment.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/players/grouping/familiars.hpp`, `src/creatures/players/grouping/familiars.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/creatures/players/components/player_storage.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/familiar.lua`, `data/scripts/spells/familiar/*`, `data/scripts/creaturescripts/familiar/*`, `data/XML/familiars.xml`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Familiars::loadFromXml`, `Player::addFamiliar/removeFamiliar/canFamiliar`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-058"></a>

## 058 — House Hirelings System

### 1. Explanation
Implements House Hirelings (`data/libs/systems/hireling.lua`, `data/modules/scripts/hirelings/*`). House owners purchase/place customizable Hireling NPCs (Banker, Trader, Cook, Steward). The C++ engine provides the hireling skill/outfit maps, house-owner speech checks, and the naming protocol; the role logic and dialogues are Lua.

### 2. Impact
Private house utility, convenience for players, and gold sink.

### 3. Functionality Affected
- **Game maps**: `Game::m_hirelingSkills` / `m_hirelingOutfits` (+ accessors).
- **Interaction**: `Player::canSpeakWithHireling`.
- **Protocol**: `parseSetHirelingName` (0xEC); cyclopedia hireling summary (`Summary_t::HIRELINGS`).
- **Item**: `ItemID_t::HIRELING_LAMP` + custom attribute.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Game` hireling skill/outfit maps, `Player::canSpeakWithHireling`, the `0xEC` protocol handler, and the cyclopedia hireling summary.
- **Stays Lua (do not port):** `data/libs/systems/hireling.lua` (752 lines: skills/outfits/sex/foods, house access, save/load), `data/modules/scripts/hirelings/hireling_module.lua`, `data/scripts/actions/items/hireling_lamp.lua`, `hireling_foods.lua`, `globalevents/hireling_save.lua`.

### 5. Test Reference
- `org.opentibia.canary.systems.HirelingSystemTest`: hireling skill/outfit maps, `canSpeakWithHireling` ownership check, and `0xEC` name serialization.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/game/game.hpp`, `src/game/game.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/server/network/protocol/protocolgame.cpp`, `src/creatures/players/components/player_cyclopedia.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/hireling.lua`, `data/modules/scripts/hirelings/hireling_module.lua`, `data/scripts/actions/items/hireling_lamp.lua`, `data/scripts/actions/items/hireling_foods.lua`, `data/scripts/globalevents/hireling_save.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Player::canSpeakWithHireling`, `parseSetHirelingName` (0xEC)

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-059"></a>

## 059 — Mounts & Speed Buff System

### 1. Explanation
Implements creature mounts (`src/creatures/appearance/mounts/mounts.*`, `data/XML/mounts.xml`). Players tame creatures and toggle mounts, gaining a fixed movement-speed bonus while mounted. Mount definitions load from XML; the taming items and store catalog are Lua.

### 2. Impact
Movement speed progression, exploration pacing, and collection mechanics.

### 3. Functionality Affected
- **Definitions**: `struct Mount` / `class Mounts` (XML load, speed value).
- **Player state**: `setCurrentMount/toggleMount/tameMount/untameMount/hasMount/hasAnyMount/getRandomMountId/dismount`; storage range.
- **Speed**: mounted speed added to effective creature speed.
- **Protocol**: mount sprite in `sendCreatureOutfit` / map description.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port `Mount`/`Mounts` XML loader, the `Player` mount methods + storage range, the mount speed contribution, and the outfit/mount protocol framing. Bind `player:addMount/hasMount/toggleMount`.
- **Stays Lua (do not port):** `data/scripts/actions/items/usable_mount_items.lua` (taming items → `player:addMount`), `data/scripts/talkactions/god/add_mount.lua`, `data/modules/scripts/gamestore/catalog/cosmetics_mounts.lua`, `data/XML/mounts.xml` (data).

### 5. Test Reference
- `org.opentibia.canary.systems.MountSystemTest`: XML load, `toggleMount` applying the mount speed bonus, step-duration decrease, and dismount restoring base speed.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/appearance/mounts/mounts.hpp`, `src/creatures/appearance/mounts/mounts.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/creatures/players/components/player_storage.cpp`, `src/lua/functions/creatures/player/mount_functions.cpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/actions/items/usable_mount_items.lua`, `data/scripts/talkactions/god/add_mount.lua`, `data/modules/scripts/gamestore/catalog/cosmetics_mounts.lua`, `data/XML/mounts.xml`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Mounts::loadFromXml`, `Player::toggleMount/hasMount/addMount`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-060"></a>

## 060 — Weapon Proficiencies System (Tibia 13.30+)

### 1. Explanation
Implements CipSoft's Weapon Proficiencies (`src/creatures/players/components/weapon_proficiency.*`, `src/enums/weapon_proficiency.hpp`, `data/items/proficiencies.json`). Characters accumulate proficiency experience per weapon family (Swords, Axes, Clubs, Bows, Crossbows, Rods, Wands), unlocking permanent perks (damage, critical, spell bonuses). This is a C++-dominant feature; the Lua surface is minimal.

### 2. Impact
Long-term weapon mastery progression in protocol versions >= 13.30.

### 3. Functionality Affected
- **Enums/structs**: `WeaponProficiencyBonus_t`, `ProficiencyPerk/Level/Proficiency`, `WeaponProficiencyData`, spell-bonus structs.
- **Component**: `class WeaponProficiency` (JSON load, perks, combat application, kill tracking).
- **Protocol**: `OfficialWeaponProficiencyPayload` (`protocol_profile.hpp`).
- **Binding**: `player:addWeaponExperience`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `WeaponProficiency` component + JSON loader + enums + protocol payload, and bind `player:addWeaponExperience`.
- **Stays Lua (do not port):** `data/scripts/lib/proficiency_helper.lua` (catalyst item), `data/scripts/talkactions/god/weapon_proficiency.lua`.

### 5. Test Reference
- `org.opentibia.canary.systems.WeaponProficiencyTest`: JSON load, adding weapon experience per family, perk level thresholds, and applying the damage bonus to combat.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/enums/weapon_proficiency.hpp`, `src/creatures/players/components/weapon_proficiency.hpp`, `src/creatures/players/components/weapon_proficiency.cpp`, `src/server/network/protocol/protocol_profile.hpp`, `src/lua/functions/creatures/player/player_functions.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/lib/proficiency_helper.lua`, `data/scripts/talkactions/god/weapon_proficiency.lua`, `data/items/proficiencies.json`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `WeaponProficiency::loadFromJson`, `addWeaponExperience`/`addWeaponProficiencyExperience`, `OfficialWeaponProficiencyPayload`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-061"></a>

## 061 — Soul War / Taint Encounters System

### 1. Explanation
Implements Goshnar's Taint mechanics (`data/libs/systems/encounters.lua`, `data-otservbr-global/scripts/quests/soul_war/*`). Players accumulate Taints (1..5) that amplify damage from Goshnar bosses and drive fear/agony conditions. The C++ engine provides the taint/fear/agony condition types and combat fear checks; the encounter engine and quest logic are Lua.

### 2. Impact
Premier endgame questline mechanics and high-risk boss strategy.

### 3. Functionality Affected
- **Conditions**: `CONDITION_GOSHNARTAINT/FEARED/AGONY`, `class ConditionFeared`.
- **Combat**: `Combat::checkFearConditionAffected`.
- **Player**: `setImmuneFear`/`isImmuneFear`, GoshnarTaint player icons.
- **Primitives**: `Zone`, `ZoneEvent`, `addEvent` used by `encounters.lua`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the C++ condition types (GoshnarTaint/Fear/Agony), `ConditionFeared`, the combat fear check, Player fear immunity, the taint player icon, and the `Zone`/`ZoneEvent`/`addEvent` primitives.
- **Stays Lua (do not port):** `data/libs/systems/encounters.lua` (generic Encounter engine), `data/scripts/globalevents/encounters.lua`, `data-otservbr-global/scripts/quests/soul_war/*` (mechanics, eventcallbacks, spells, moveevents), `data-otservbr-global/world/quest/soul_war/*.otbm`.

### 5. Test Reference
- `org.opentibia.canary.systems.SoulWarEncounterTest`: taint condition stacking/amplification, `ConditionFeared` application, and the combat fear check.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/creatures_definitions.hpp` (conditions), `src/creatures/combat/condition.hpp`, `src/creatures/combat/condition.cpp`, `src/creatures/combat/combat.cpp`, `src/creatures/players/player.hpp`, `src/creatures/monsters/monsters.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/encounters.lua`, `data/scripts/globalevents/encounters.lua`, `data-otservbr-global/scripts/quests/soul_war/*`, `data-otservbr-global/world/quest/soul_war/*`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Combat::checkFearConditionAffected`, `ConditionFeared`, `Player::setImmuneFear`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-062"></a>

## 062 — Scheduled World Raids & Global Announcements

### 1. Explanation
Implements automated global raid encounters. Canary has **two coexisting systems**: the legacy C++ XML raids (`src/lua/creature/raids.*`, `data-canary/raids/raids.xml`) and a modern Lua scheduler (`data/libs/systems/raids.lua`) built on the Encounter engine. Multi-stage broadcasts spawn monster waves and boss encounters.

### 2. Impact
Server-wide community events, rare loot opportunities, and activity surges.

### 3. Functionality Affected
- **Legacy C++ engine**: `class Raids`/`Raid`/`RaidEvent`, `Game::raids`, startup load, reload hook, task scheduler.
- **Modern Lua scheduler**: `Raid` extends `Encounter` (chance/day gating), `onThink -> raid:tryStart()`.
- **Broadcasts**: global broadcast messages to all online players.
- **Spawning**: boss/minion waves onto the active map.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the legacy `Raids`/`Raid`/`RaidEvent` XML loader/engine + `Game::raids` load/reload, **or** provide the Encounter primitives (`Zone`/`ZoneEvent`/`addEvent`/`getPlayers`/kv/db) the modern Lua scheduler needs. (Decide which path at implementation time; the Lua `raids.lua` stays.)
- **Stays Lua (do not port):** `data/libs/systems/raids.lua`, `data/scripts/globalevents/raids.lua`, `data/scripts/globalevents/global_server_save.lua` raid hooks, `data-canary/raids/raids.xml` (data).

### 5. Test Reference
- `org.opentibia.canary.systems.RaidSchedulerTest`: loading raid definitions, multi-stage timed execution, broadcast delivery, and monster spawning at target coordinates.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/lua/creature/raids.hpp`, `src/lua/creature/raids.cpp`, `src/game/game.hpp`, `src/game/game.cpp`, `src/game/game_reload.cpp`, `src/tasks/task.hpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/raids.lua`, `data/scripts/globalevents/raids.lua`, `data/scripts/globalevents/global_server_save.lua`, `data-canary/raids/raids.xml`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Raids::loadFromXml`, `Raid::startRaid`, `RaidEvent::executeRaidEvent`, `Game::raids`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-063"></a>

## 063 — Offline Training Shrines System (0x74)

### 1. Explanation
Implements CipSoft's Offline Training (`src/server/network/protocol/protocolgame.cpp` opcode `0x74`). Players interact with training statues (Sword, Axe, Club, Distance, Magic Level); the client disconnects the player, records the logout time and target skill, and awards skill/magic tries on next login based on elapsed offline time.

### 2. Impact
Core daily progression for offline players.

### 3. Functionality Affected
- **Player state**: `addOfflineTrainingTries`, `add/remove/getOfflineTrainingTime`, `get/setOfflineTrainingSkill`; `onThink` accrual.
- **Dialog**: `Game::sendOfflineTrainingDialog`.
- **Protocol**: opcode `0x74` `parseStartOfflineTraining`; client send; `MESSAGE_OFFLINE_TRAINING`.
- **Persistence**: `iologindata_save_player.cpp` / `iologindata_load_player.cpp`.
- **Config**: `RATE_OFFLINE_TRAINING_SPEED`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Player` offline-training state/methods, the `onThink` accrual, `Game::sendOfflineTrainingDialog`, the `0x74` protocol handler + client send, persistence, and config. Bind `player:addOfflineTrainingTries/getOfflineTrainingTime`.
- **Stays Lua (do not port):** `data/scripts/creaturescripts/player/offline_training.lua` (onLogin computes tries from offline time/vocation).

### 5. Test Reference
- `org.opentibia.canary.systems.OfflineTrainingTest`: `0x74` parsing sets the target skill and requests logout, offline-time accrual capped at the training cap, and try conversion on login.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/game/game.hpp`, `src/game/game.cpp`, `src/server/network/protocol/protocolgame.cpp`, `src/io/iologindata_save_player.cpp`, `src/io/iologindata_load_player.cpp`, `src/config/config_enums.hpp`
- **Lua Source Files (stay Lua)**: `data/scripts/creaturescripts/player/offline_training.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `parseStartOfflineTraining` (0x74), `Game::sendOfflineTrainingDialog`, `Player::addOfflineTrainingTries`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

---

<a id="feature-064"></a>

## 064 — Concoctions & Alchemical Flask Buffs

### 1. Explanation
Implements CipSoft's Concoctions (`src/creatures/players/player.*` concoction state, `data/libs/systems/concoctions.lua`). Players drink 1-hour alchemical flasks granting elemental resistances, leech, or cure effects. The C++ engine owns the concoction enum, active-concoction state, and combat/cyclopedia integration; the effect logic is Lua.

### 2. Impact
Consumable gold sink and tactical preparation for bossing/hunting.

### 3. Functionality Affected
- **Enum**: `Concoction_t` (`utils_definitions.hpp`), Lua enum registration.
- **Player state**: `updateConcoction`, `getActiveConcoctions`, `isConcoctionActive`, `activeConcoctions` map.
- **Combat/cyclopedia**: bestiary multiplier use; cyclopedia misc-stats packet; critical contribution.
- **Config**: `TIBIADROME_CONCOCTION_*`.
- **Binding**: `player:updateConcoction`.

### 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Concoction_t` enum, the `Player` active-concoction state + `updateConcoction`, the cyclopedia packet contribution, and config. Bind `player:updateConcoction`.
- **Stays Lua (do not port):** `data/libs/systems/concoctions.lua` (`Concoction` class, conditions/cooldown/tick), `data/scripts/systems/concoctions.lua` (per-concoction configs + registration + onLogin).

### 5. Test Reference
- `org.opentibia.canary.systems.ConcoctionEngineTest`: `updateConcoction` activating a concoction, mutual exclusion, `isConcoctionActive`/`getActiveConcoctions`, and cyclopedia packet serialization.

### 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/utils/utils_definitions.hpp` (`Concoction_t`), `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/lua/functions/core/game/lua_enums.cpp`, `src/server/network/protocol/protocolgame.cpp`, `src/config/config_enums.hpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/concoctions.lua`, `data/scripts/systems/concoctions.lua`, `data/scripts/creaturescripts/player/login.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Player::updateConcoction`, `Player::getActiveConcoctions`, `player:updateConcoction`

### 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.

