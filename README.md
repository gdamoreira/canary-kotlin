# Canary Kotlin Edition

[![Kotlin](https://img.shields.io/badge/kotlin-2.4.21-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-25%20LTS-orange.svg?logo=openjdk)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/gradle-9.8.1-green.svg?logo=gradle)](https://gradle.org)
[![Netty](https://img.shields.io/badge/netty-4.1.118-blueviolet.svg)](https://netty.io)
[![PostgreSQL](https://img.shields.io/badge/postgresql-16-blue.svg?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/github/license/opentibiabr/canary.svg)](https://github.com/opentibiabr/canary/blob/main/LICENSE)

**Canary Kotlin Edition** is a modern, high-performance rewrite of the [Canary OpenTibia MMORPG Server](https://github.com/opentibiabr/canary) designed specifically for the **JVM stack** (Kotlin 2.4, Java 25 LTS, Gradle 9.8, Netty, and Kotlin Coroutines).

It translates 15+ years of C++20 engine evolution into idiomatic, testable, and memory-safe Kotlin architectures without sacrificing wire-level binary and mathematical parity.

---

## 🚀 Key Architectural Adaptations

| Canary C++20 Architecture | Canary Kotlin Architecture | Benefit on JVM Stack |
|---|---|---|
| Monolithic `switch (recvbyte)` in `ProtocolGame` (12,000+ lines) | **Modular Packet Handlers** (`MovePacketHandler`, `ItemPacketHandler`, `CombatPacketHandler`, `LookPacketHandler`) | High testability, decoupled responsibilities, zero giant god classes. |
| Asio Event Loops & `std::recursive_mutex` locks | **Kotlin Coroutine Actor (`GameDispatcher`)** | Lock-free sequential world mutation loop, zero lock contention, zero deadlocks. |
| Raw memory pointers & circular `std::shared_ptr` references | **Thread-confined Domain Entities** & Value Classes (`@JvmInline value class Direction`) | Memory safety, zero memory leaks, idiomatic GC lifecycle management. |
| Hardcoded MySQL raw string queries | **Flyway Database Migrations + HikariCP Connection Pool** | Automated database migrations, SQL injection prevention, zero-downtime schemas. |
| Mandatory external database daemons for tests | **H2 In-Memory (PostgreSQL mode)** | Instant out-of-the-box local testing without running external Docker/MariaDB containers. |
| C-API Lua userdata bindings | **Embedded LuaJ engine + Canary-named binding libraries** | Lua gameplay scripts run *as Lua* through `LuaEnvironment`/`UserdataRegistry`; no JNI, no memory corruption, and no Lua logic re-authored in Kotlin. |

---

## 🛠️ Getting Started

### Prerequisites
- **JDK 25 LTS** installed and configured (`JAVA_HOME` pointing to Java 25).
- No external database required for local testing! The server automatically initializes an in-memory database with test accounts.

### Building & Running Tests

```bash
# Verify entire test suite (unit tests, integration tests, DB migrations, network simulations)
./gradlew test

# Build application distribution
./gradlew build
```

### Running Locally (Zero-Configuration Mode)

```bash
# Starts the server in In-Memory mode on ports 7171 (Login) and 7172 (GameWorld)
./gradlew run
```

When running in local mode:
- **Database**: H2 In-Memory in PostgreSQL mode with automatic Flyway migrations (`V1__initial_schema.sql`, `V2__seed_test_accounts.sql`).
- **Test Credentials**: Account `1` / Password `1` with pre-seeded characters `Knight Hero` and `Sorcerer Master`.

---

## 🐳 Docker Infrastructure

For production-like environments using real PostgreSQL:

```bash
# Start PostgreSQL 16 and Canary Kotlin Server
docker compose up -d --build
```

Default container ports:
- **Login / Status Port**: `7171`
- **Game World Port**: `7172`
- **PostgreSQL Database**: `5432`

---

## 📂 Project Structure

```
canary-kotlin/
├── docs/
│   ├── Done/              # 64 completed specifications (networking, parsers, DB, systems 001-064)
│   ├── Implementing/      # Features actively in progress
│   ├── Plan/              # Architectural guidelines & roadmap (000-*)
│   ├── game-play-features.md   # Master 56-feature matrix (all implemented) & residual gaps
│   └── superpowers/       # Design specs & implementation plans
├── src/
│   ├── main/
│   │   ├── kotlin/org/opentibia/canary/
│   │   │   ├── account/       # Account domain model & repository interfaces
│   │   │   ├── chat/          # Chat channels, speech scopes (Whisper, Say, Yell)
│   │   │   ├── combat/        # CombatDamage + Combat (damage/armor/mitigation/crit) & condition/*
│   │   │   ├── config/        # ServerConfig & environment variable loaders
│   │   │   ├── creatures/     # Creature, Player, Monster, NPC models & AI
│   │   │   ├── database/      # DatabaseConnectionFactory, HikariCP, JDBC Repositories
│   │   │   ├── economy/       # Player market (IOMarket) & trade matching engine
│   │   │   ├── game/          # GameDispatcher coroutine actor, MovementEngine
│   │   │   ├── io/            # OTBM binary parser, OTB items, XML loaders, AssetManager
│   │   │   ├── items/         # Item, Container, WorldObjects (MagicField, Teleport, Door)
│   │   │   ├── lua/           # LuaJ engine: runtime/, userdata/, registry/, bindings/, loader/
│   │   │   ├── map/           # Position, Tile, GameMap, House & HouseTile
│   │   │   ├── network/       # Netty pipeline + protocol/ handlers, parsers & serializers
│   │   │   ├── persistence/   # Persistent PlayerStorage & PlayerData
│   │   │   └── systems/       # Advanced features: wheel, forge, bestiary, hazard, mounts,
│   │   │                      #   familiars, proficiency, gamestore, hireling, soulwar, raid,
│   │   │                      #   offlinetraining, dailyreward, rewardchest, concoction
│   │   └── resources/
│   │       ├── db/migration/  # Flyway schema migrations (V1, V2)
│   │       └── lua/           # LuaJ compatibility shim
│   └── test/                  # JUnit 5 + AssertJ test suite (355 suites / 3568 tests, all green)
├── docker-compose.yml         # Containerized PostgreSQL 16 + Server stack
├── Dockerfile                 # Multi-stage alpine build
└── build.gradle.kts           # Kotlin 2.4 / Java 25 Gradle build file
```

---

## 🛡️ Parity & Functional Verification

Every subsystem is validated against Canary's exact formulas and byte specifications:
- **Cryptography**: Little-Endian 64-round XTEA, 1024-bit raw textbook RSA (`c^d mod n`), and RFC 1950 Adler-32 checksums.
- **Experience Curve**: Mathematical formula $\frac{50n^3 - 300n^2 + 850n - 600}{3}$.
- **Step Duration**: Locomotion speed formula $\frac{\text{groundSpeed} \times 1000}{\text{creatureSpeed}}$.
- **Physical Damage**: Attack formula $2 \times \frac{\text{attack} \times (\text{skill} + 5.8)}{25} + \frac{\text{level}}{5}$.
- **Armor Reduction**: Randomized interval $P(\text{reduction}) \in \left[\frac{\text{armor}}{2}, \text{armor} - 1\right]$.
- **Viewport Streaming**: $18 \times 14$ tile canvas encoding with empty run compression skip bytes.
- **Wheel of Destiny**: 36-slot per-vocation tree, point budget $\max(0, \text{level} - 50)$, revelation stats and the exact per-slot bonus tables.
- **Weapon Proficiencies**: the three Canary experience tables (crossbow/knight/standard) and mastery offsets.
- **Conditions**: `Condition` lifecycle (ticks/stacking) and damage/regeneration/attributes/speed math.

---

## 🧭 Lua Scripting & Parity Policy

The port follows two binding rules (see `AGENTS.md` §1.1 and §1.4):

1. **Strict Canary parity** — no invented methods, classes, or values. Every Kotlin name mirrors its Canary C++ or Lua counterpart.
2. **Lua stays Lua** — gameplay authored in `data/**/*.lua` (NPC dialogue, quests, spells, actions, systems) is executed *as Lua* through `LuaEnvironment` (LuaJ) and the Canary-named binding libraries; it is never re-implemented as Kotlin. Kotlin provides only the C++ engine primitives and the bindings those scripts call.

Consequently, each of the 15 advanced features (`docs/Plan/050–064`) is delivered as its **C++ engine core** in `systems/<feature>/`, with the Lua-authored portions left as Lua.

---

## 🧩 Feature Coverage

**All 56 Canary gameplay systems are implemented** (`docs/game-play-features.md`). The 15 advanced systems (`docs/Done/050–064`) are delivered as faithful C++ engine cores in `systems/<feature>/` with application services and protocol parsers/serializers, wired where applicable:

`wheel` · `forge` · `bestiary` · `gamestore` · `hazard` · `dailyreward` · `rewardchest` · `familiar` · `hireling` · `mount` · `proficiency` · `soulwar` · `raid` · `offlinetraining` · `concoction`

Protocol parsers/serializers for wheel (`0x61`/`0x62`/`0xE7`), offline training (`0x74`), reward chest (`0xFF`), bestiary (`0xE1`–`0xE4`), forge (`0xBF`) and weapon proficiencies are routed through `GamePacketRouter` on the WorldCommit lane. Residual per-feature parity gaps (protocol sends, deeper persistence, Lua-driven side effects) are tracked in `docs/game-play-features.md`.

---

## 📊 Implementation Coverage

The port is a faithful, incremental transcription of Canary's C++ engine. Two views of progress:

| View | Progress |
|---|---|
| **Tracked gameplay features** (`docs/game-play-features.md`) | **56 / 56 (100%)** — engine scope, tested |
| **Inbound `ProtocolGame` opcodes** | **141 / 141 decoded (100%)**; ~46 fully applied (~33%) |
| **Outbound `ProtocolGame` senders** | 58 / 190 (**~31%**) |
| **Lua bindings** | 167 / 1,246 method registrations (**~13%**) |
| **`Player` methods** | ~409 / 821 (**~50%** raw count) |
| **Monster engine** | **~20%** (A* + targeting + loot + intention/relevance) |
| **Map engine** | **~35%** |
| **Persistence** | **~30%** |
| **Estimated full-parity completion** | **~50–55%** |

The 56 tracked gameplay features are engine-complete, but Canary's full surface (`player.cpp` ~765 methods, `game.cpp` ~352 methods, ~1,310 Lua registrations, ~105 opcodes) is far larger. The complete per-subsystem inventory and ranked remaining work lives in **[`docs/missing-features.md`](docs/missing-features.md)**.
