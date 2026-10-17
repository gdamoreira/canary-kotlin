# Canary Kotlin Edition

[![CI](https://github.com/gdamoreira/canary-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/gdamoreira/canary-kotlin/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.21-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-25%20LTS-orange.svg?logo=openjdk)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/gradle-9.8.1-green.svg?logo=gradle)](https://gradle.org)
[![Netty](https://img.shields.io/badge/netty-4.1.118-blueviolet.svg)](https://netty.io)
[![MariaDB](https://img.shields.io/badge/mariadb-11.4-blue.svg?logo=mariadb)](https://mariadb.org/)
[![License](https://img.shields.io/github/license/gdamoreira/canary-kotlin.svg)](https://github.com/gdamoreira/canary-kotlin/blob/main/LICENSE)

**Canary Kotlin Edition** is a modern, high-performance rewrite of the [Canary OpenTibia MMORPG Server](https://github.com/gdamoreira/canary-kotlin) designed specifically for the **JVM stack** (Kotlin 2.4, Java 25 LTS, Gradle 9.8, Netty, and Kotlin Coroutines).

It translates 15+ years of C++20 engine evolution into idiomatic, testable, and memory-safe Kotlin architectures without sacrificing wire-level binary and mathematical parity.

---

## 📖 Table of Contents

- [Key Architectural Adaptations](#-key-architectural-adaptations)
- [Architecture](#-architecture)
- [Installation](#-installation)
- [Docker Infrastructure](#-docker-infrastructure)
- [Project Structure](#-project-structure)
- [Documentation](#-documentation)
- [Parity & Functional Verification](#-parity--functional-verification)
- [Lua Scripting & Parity Policy](#-lua-scripting--parity-policy)
- [Feature Coverage](#-feature-coverage)
- [Implementation Coverage](#-implementation-coverage)
- [Contributing](#-contributing)
- [Troubleshooting](#-troubleshooting)
- [Code of Conduct](#-code-of-conduct)
- [Security Policy](#-security-policy)
- [Roadmap](#-roadmap)
- [Future Improvements](#-future-improvements)
- [Known Issues](#-known-issues)
- [Frequently Asked Questions](#-frequently-asked-questions)
- [Acknowledgments](#-acknowledgments)
- [License](#-license)

---

## 🚀 Key Architectural Adaptations

| Canary C++20 Architecture | Canary Kotlin Architecture | Benefit on JVM Stack |
|---|---|---|
| Monolithic `switch (recvbyte)` in `ProtocolGame` (12,000+ lines) | **Modular Packet Handlers** (`MovePacketHandler`, `ItemPacketHandler`, `CombatPacketHandler`, `LookPacketHandler`) | High testability, decoupled responsibilities, zero giant god classes. |
| Asio Event Loops & `std::recursive_mutex` locks | **Kotlin Coroutine Actor (`GameDispatcher`)** | Lock-free sequential world mutation loop, zero lock contention, zero deadlocks. |
| Raw memory pointers & circular `std::shared_ptr` references | **Thread-confined Domain Entities** & Value Classes (`@JvmInline value class Direction`) | Memory safety, zero memory leaks, idiomatic GC lifecycle management. |
| Hardcoded MySQL raw string queries | **Flyway Database Migrations + HikariCP Connection Pool** | Automated database migrations, SQL injection prevention, zero-downtime schemas; runs on **MariaDB 11.4** (default) or **PostgreSQL 16** (`CANARY_DB_ENGINE`). |
| Mandatory external database daemons for tests | **H2 In-Memory (PostgreSQL mode)** | Instant out-of-the-box local testing without running external Docker/MariaDB containers. |
| C-API Lua userdata bindings | **Embedded LuaJ engine + Canary-named binding libraries** | Lua gameplay scripts run *as Lua* through `LuaEnvironment`/`UserdataRegistry`; no JNI, no memory corruption, and no Lua logic re-authored in Kotlin. |

---

## 📦 Installation

### Prerequisites
- **JDK 25 LTS** installed and configured (`JAVA_HOME` pointing to Java 25).
- No external database required for local testing! The server automatically initializes an in-memory database with test accounts.
- Optional for production-like runs: **MariaDB 11.4** (or the bundled Docker stack below); PostgreSQL 16 is supported via `CANARY_DB_ENGINE=postgres`.

### Building & Running Tests

```bash
# Verify entire test suite (unit tests, integration tests, DB migrations, network simulations)
./gradlew test

# Build application distribution
./gradlew build
```

### Server Configuration (`config.lua.dist`)

[`config.lua.dist`](config.lua.dist) is the upstream Canary configuration
template, shipped verbatim. In upstream Canary you copy it to `config.lua` and
the server executes it, reading globals such as `serverName`,
`dataPackDirectory` or `mapDownloadUrl`.

This port reads the same key surface through `ConfigManager`, with Canary boot
precedence (defaults < `CANARY_*` environment < `--config` file < CLI options):

- `--config config.lua` — a `.lua` path is **executed through LuaJ** exactly
  like `ConfigManager::load` and its globals (`serverName`, `loginProtocolPort`,
  `dataPackDirectory`, the `OTCRFeatures` table, …) are applied;
- `--config server.properties` — any other path is parsed as an
  `identifier = value` properties file keyed by the `config.lua` identifiers;
- `CANARY_*` environment variables.

Start from the template:

```bash
cp config.lua.dist config.lua
./gradlew run --args="--config config.lua"
```

**Built distribution:** `./gradlew installDist` bundles `config.lua.dist` and the
`data`, `data-canary` and `data-otservbr-global` folders next to the executable
(`build/install/canary-kotlin/`). The server resolves them from the executable's
own folder, so it runs from any working directory:

```bash
./gradlew installDist
cp build/install/canary-kotlin/config.lua.dist build/install/canary-kotlin/config.lua
build/install/canary-kotlin/bin/canary-kotlin
```

Without `--config`, a `config.lua` next to the executable (or in the working
directory for development runs) is picked up automatically.

### Running Locally (Zero-Configuration Mode)

```bash
# Starts the server in In-Memory mode on ports 7171 (Login) and 7172 (GameWorld)
./gradlew run
```

When running in local mode:
- **Database**: H2 In-Memory in PostgreSQL mode with automatic Flyway migrations (`V1__initial_schema.sql` through `V21__player_animus_mastery_column.sql`).
- **Test Credentials**: Account `1` / Password `1` with pre-seeded characters `Knight Hero` and `Sorcerer Master`.

---

## 🐳 Docker Infrastructure

For production-like environments using a real database (MariaDB by default):

```bash
cp docker/.env.dist .env      # optional; defaults work out of the box
docker compose up -d --build
```

The canonical stack lives in [`docker/docker-compose.yml`](docker/docker-compose.yml)
(the upstream Canary layout); the repository-root `docker-compose.yml` includes
it. The `docker/` folder also ships `.env.dist`, `config.sh`, the adapted
test-account dumps (`data/`), guarded `up.sh`/`up.ps1` start scripts,
`Dockerfile.dev`/`.x86`/`.arm` variants and [`docker/DOCKER.md`](docker/DOCKER.md).
Upstream quickstart extras that do not apply to this port (MyAAC, login-server)
are documented there.

Scaling phase 1 is opt-in: [`docker/docker-compose.split.yml`](docker/docker-compose.split.yml)
runs the login role (scalable behind a TCP load balancer) and the game role as
separate services sharing the database — see
[`docker/DOCKER.md`](docker/DOCKER.md#split-logingame-mode-scaling-phase-1).

Default container ports:
- **Login / Status Port**: `7171`
- **Game World Port**: `7172`
- **PostgreSQL Database**: `5432`
- **Prometheus exporter** (optional): `9464` — see [`metrics/README.md`](metrics/README.md)

---

## 📂 Project Structure

```
canary-kotlin/
├── docs/
│   ├── README.md          # Documentation hub (adapted from upstream canary/docs)
│   ├── architecture.md    # Kotlin engine architecture
│   ├── building/          # JDK 25 + Gradle build guides (Linux, Windows, WSL)
│   ├── docker/            # Docker quickstart guide
│   ├── systems/           # Engine subsystem contracts (upstream-adapted)
│   ├── development/       # Development docs (agents write here; no lifecycle folders)
│   │   ├── implementation-status.md  # Parity inventory, 56-feature matrix, audit, improvements
│   │   ├── architecture-and-roadmap.md  # Architecture mapping, parity strategy, roadmap
│   │   ├── feature-history.md    # Aggregated 64 feature specifications (001–064)
│   │   ├── parity-gaps-and-next-steps.md  # Ranked parity backlog (P0–P3) + live-validation checklist
│   │   └── wave-history.md       # Aggregated wave plans (20–60)
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
│   │       ├── db/migration/  # Flyway schema migrations (V1–V21)
│   │       └── lua/           # LuaJ compatibility shim
│   └── test/                  # JUnit 5 + AssertJ test suite (836 suites / 8534 tests, all green)
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

Consequently, each of the 15 advanced features (see `docs/development/feature-history.md`) is delivered as its **C++ engine core** in `systems/<feature>/`, with the Lua-authored portions left as Lua.

---

## 🧩 Feature Coverage

**All 56 Canary gameplay systems are implemented** (`docs/development/implementation-status.md`). The 15 advanced systems (`docs/development/feature-history.md`) are delivered as faithful C++ engine cores in `systems/<feature>/` with application services and protocol parsers/serializers, wired where applicable:

`wheel` · `forge` · `bestiary` · `gamestore` · `hazard` · `dailyreward` · `rewardchest` · `familiar` · `hireling` · `mount` · `proficiency` · `soulwar` · `raid` · `offlinetraining` · `concoction`

Protocol parsers/serializers for wheel (`0x61`/`0x62`/`0xE7`), offline training (`0x74`), reward chest (`0xFF`), bestiary (`0xE1`–`0xE4`), forge (`0xBF`) and weapon proficiencies are routed through `GamePacketRouter` on the WorldCommit lane. Residual per-feature parity gaps (protocol sends, deeper persistence, Lua-driven side effects) are tracked in `docs/development/implementation-status.md`.

---

## 📊 Implementation Coverage

The port is a faithful, incremental transcription of Canary's C++ engine. Two views of progress:

| View | Progress |
|---|---|
| **Tracked gameplay features** (`docs/development/implementation-status.md`) | **56 / 56 (100%)** — engine scope, tested |
| **Inbound `ProtocolGame` opcodes** | **141 / 141 decoded (100%)**; ~46 fully applied (~33%) |
| **Outbound `ProtocolGame` senders** | 187 / 187 (**100%**) |
| **Lua bindings** | 1,246 / 1,246 method registrations (**100%**) |
| **`Player` methods** | 754 / 821 (**~92%** raw count) |
| **Monster engine** | **~80%** (+ forge/type state) |
| **Map engine** | **~85%** |
| **Persistence** | **~80%** |
| **Estimated full-parity completion** | **~99.5%** |

The 56 tracked gameplay features are engine-complete, but Canary's full surface (`player.cpp` ~765 methods, `game.cpp` ~352 methods, ~1,310 Lua registrations, ~105 opcodes) is far larger. The complete per-subsystem inventory and ranked remaining work lives in **[`docs/development/implementation-status.md`](docs/development/implementation-status.md)**.

---

## 🏛️ Architecture

The engine keeps Canary's semantics while using JVM-native building blocks:

- **Single-threaded world mutation** — all spatial/entity changes run on the
  `GameDispatcher` coroutine (`Dispatchers.Default.limitedParallelism(1)`);
  Netty I/O threads never touch world state directly.
- **Dependency injection** — no `g_game()`/`g_configManager()` singletons; the
  `bootstrap/ServerBootstrap` composition root wires every service explicitly.
- **Wire parity** — Little-Endian `ByteBuf` framing, XTEA (64 rounds), 1024-bit
  raw RSA and Adler-32 exactly as Canary implements them.
- **Lua stays Lua** — datapack scripts execute unchanged through LuaJ; Kotlin
  provides the C++-parity engine primitives and bindings only.
- **Persistence** — Flyway migrations (V1–V21) on PostgreSQL 16, H2 in
  PostgreSQL mode for tests.

Full details: [`docs/architecture.md`](docs/architecture.md).

---

## 📚 Documentation

The documentation hub is [`docs/README.md`](docs/README.md). Highlights:

| Document | Contents |
|---|---|
| [`docs/architecture.md`](docs/architecture.md) | Engine architecture and invariants |
| [`docs/building/kotlin-local-validation.md`](docs/building/kotlin-local-validation.md) | Authorized local build/test entry point |
| [`docs/development.md`](docs/development.md) | Gradle workflow and coding standards |
| [`docs/operations.md`](docs/operations.md) | Deployment, monitoring, backups |
| [`docs/docker/quickstart-for-beginners.md`](docs/docker/quickstart-for-beginners.md) | Docker quickstart |
| [`docs/development/implementation-status.md`](docs/development/implementation-status.md) | Parity inventory, gameplay matrix, audit and improvement tracker |
| [`docs/development/scaling-and-multi-world.md`](docs/development/scaling-and-multi-world.md) | Scaling and multi-world architecture analysis |

---

## 🤝 Contributing

Contributions are welcome. Read [`CONTRIBUTING.md`](CONTRIBUTING.md) and
[`AGENTS.md`](AGENTS.md) first. The short version:

1. Write a failing test first (RED), then the minimal implementation (GREEN).
2. Run `./gradlew clean test` — zero failures and **zero Kotlin warnings**.
3. Never invent Canary APIs and never re-implement Lua gameplay in Kotlin.
4. Schema changes go through Flyway migrations only.

---

## 🩺 Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `Unsupported class file major version` or build failures | `JAVA_HOME` is not JDK 25. Run `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` (macOS) or see [`docs/building/`](docs/building). |
| `Address already in use` on startup | Ports `7171`/`7172` are taken. Stop the other instance or override `CANARY_LOGIN_PORT` / `CANARY_GAME_PORT`. |
| Database connection errors | Only set `CANARY_DB_HOST` when you want PostgreSQL; unset it for the zero-config H2 mode. |
| Gradle daemon killed / OOM | Cap memory: `./gradlew test -Dorg.gradle.jvmargs=-Xmx2g -Dkotlin.daemon.jvmargs=-Xmx2g`. |
| Map download fails at boot | `MapDownload` fetches the map/config from the upstream Canary release; check network access or provide a local map file. |
| Client cannot connect | Use login/status `7171` and game `7172`; test account `1` / password `1`. |

---

## 🤗 Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md) (v2.1).
Report unacceptable behavior to the maintainers through the repository's issue
tracker.

---

## 🔐 Security Policy

See [`SECURITY.md`](SECURITY.md). Report vulnerabilities privately — never in a
public issue. Test credentials and default key material are for local
development only; deployments should set `CANARY_RSA_P`/`CANARY_RSA_Q` and
change seeded accounts.

---

## 🗺️ Roadmap

- **Verification**: live differential harness (packet-capture A/B replay against
  a running Canary instance) — the strongest remaining parity proof.
- **Depth closure**: work through the ranked gaps in
  [`docs/development/implementation-status.md`](docs/development/implementation-status.md) §3 (persistence
  call-site tails, combat/spell wiring, renderer seams).
- **Operations**: expanded CI (matrix JDK builds, Docker smoke), metrics
  exporter parity.
- **Content**: multi-datapack boot selection and map distribution.

---

## 🔮 Future Improvements

- Automated Lua API documentation generated from the Kotlin binding registry.
- Container image publication pipeline (GHCR) with versioned tags.
- Performance benchmarks for the dispatcher, monster AI pipelines and map load.
- Extended fuzz/property tests for protocol parsers.

---

## 🐞 Known Issues

- No live differential verification against Canary yet (unit/integration tests
  only) — see [`docs/development/implementation-status.md`](docs/development/implementation-status.md) §3.
- Legacy protocol profiles (CipSoft860/old-protocol branches) are partially
  unmodeled; the current profile is fully wired.
- Persistence call-site tails (login condition delegation, guild registration,
  VIP/exiva sub-loads) remain unwired — tracked in
  [`docs/development/implementation-status.md`](docs/development/implementation-status.md) §3.
- Performance numbers in `docs/hardware-sizing.md` are upstream C++ measurements,
  labeled as such.

---

## ❓ Frequently Asked Questions

**Is this the same server as Canary?**
It is a Kotlin/JVM rewrite targeting byte-level and algorithmic parity with the
upstream C++ engine. Gameplay content (Lua datapacks) is shared, not rewritten.

**Can I use my existing Canary database?**
Not directly. This port targets PostgreSQL 16 with its own Flyway schema
(V1–V21); the upstream MySQL/MariaDB schema is not supported.

**Can I use my own datapack and Lua scripts?**
Yes — Lua stays Lua. Point the server at your `data/` tree and the scripts run
unchanged through the LuaJ bridge.

**Do I need Docker?**
No. `./gradlew run` starts a zero-configuration in-memory server on ports
7171/7172 with test account `1` / `1`.

**Why Kotlin?**
Memory safety, structured concurrency and testability, while keeping the wire
protocol and formulas identical to Canary.

---

## 🙏 Acknowledgments

- The **Canary / OpenTibiaBR** team and contributors, whose GPL-2.0 C++ engine
  this port transcribes (`../.projects/canary`).
- The **OTServBR-Global** lineage and the wider **OpenTibia** community.
- The JVM ecosystem this port builds on: Kotlin, Netty, Kotlin Coroutines,
  LuaJ, HikariCP, Flyway, BouncyCastle, JUnit 5 and AssertJ.

---

## 📜 License

Canary Kotlin Edition is distributed under the **MIT License** — see
[`LICENSE`](LICENSE). It is a re-implementation derived from the
[Canary project](https://github.com/opentibiabr/canary) (GPL-2.0); upstream
names, data files and Lua scripts remain subject to their original licenses.

