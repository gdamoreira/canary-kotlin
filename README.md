# Canary Kotlin Edition

[![Kotlin](https://img.shields.io/badge/kotlin-2.1.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-21%20LTS-orange.svg?logo=openjdk)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/gradle-8.12-green.svg?logo=gradle)](https://gradle.org)
[![Netty](https://img.shields.io/badge/netty-4.1.118-blueviolet.svg)](https://netty.io)
[![PostgreSQL](https://img.shields.io/badge/postgresql-16-blue.svg?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/github/license/opentibiabr/canary.svg)](https://github.com/opentibiabr/canary/blob/main/LICENSE)

**Canary Kotlin Edition** is a modern, high-performance rewrite of the [Canary OpenTibia MMORPG Server](https://github.com/opentibiabr/canary) designed specifically for the **JVM stack** (Kotlin 2.1, Java 21 LTS, Netty 4.2, and Kotlin Coroutines).

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
| C-API Lua userdata bindings | **Embedded LuaJ Engine & Functional Script Registries** | Safe reflection without JNI crashes or memory corruptions. |

---

## 🛠️ Getting Started

### Prerequisites
- **JDK 21 LTS** installed and configured (`JAVA_HOME` pointing to Java 21).
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
│   ├── Done/              # 40 completed specifications (reverse engineering, packets, parsers, DB)
│   ├── Implementing/      # Features actively in progress
│   └── Plan/              # Architectural guidelines & execution roadmaps
├── src/
│   ├── main/
│   │   ├── kotlin/org/opentibia/canary/
│   │   │   ├── account/       # Account domain model & repository interfaces
│   │   │   ├── chat/          # Chat channels, speech scopes (Whisper, Say, Yell)
│   │   │   ├── combat/        # Damage formulas, armor mitigation, combat loops
│   │   │   ├── config/        # ServerConfig & environment variable loaders
│   │   │   ├── creatures/     # Creature, Player, Monster, NPC models & AI
│   │   │   ├── database/      # DatabaseConnectionFactory, HikariCP, JDBC Repositories
│   │   │   ├── economy/       # Player market (IOMarket) & trade matching engine
│   │   │   ├── game/          # GameDispatcher coroutine actor, MovementEngine
│   │   │   ├── io/            # OTBM binary parser, OTB items, XML loaders, AssetManager
│   │   │   ├── items/         # Item, Container, WorldObjects (MagicField, Teleport, Door)
│   │   │   ├── lua/           # LuaScriptBridge (LuaJ embedding)
│   │   │   ├── map/           # Position, Tile, GameMap, House & HouseTile
│   │   │   ├── network/       # Netty TCP pipeline, TibiaFrameDecoder, MessageEncoder
│   │   │   ├── persistence/   # Persistent PlayerStorage & PlayerData
│   │   │   ├── scripting/     # Action & TalkAction functional script registries
│   │   │   └── systems/       # Modern systems (Imbuements, Prey, Bosstiary)
│   │   └── resources/
│   │       └── db/migration/  # Flyway schema migrations (V1, V2)
│   └── test/                  # 100% green JUnit 5 + AssertJ + Mockk test suite
├── docker-compose.yml         # Containerized PostgreSQL 16 + Server stack
├── Dockerfile                 # Multi-stage alpine build
└── build.gradle.kts           # Kotlin 2.1 Gradle build file
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
