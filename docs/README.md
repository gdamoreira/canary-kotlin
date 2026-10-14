# Canary-Kotlin Documentation

This directory is the main documentation hub for **Canary Kotlin Edition**.

> Adapted from canary/docs/README.md for the Kotlin/JVM engine.
> Canary-Kotlin status: this index replaces the upstream C++ index and links both
> the ported/adapted upstream documentation and the repository's Kotlin-specific
> planning docs.

---

# Documentation Index

| Document | Purpose |
| --- | --- |
| [`README.md`](README.md) | Project overview and documentation index |
| [`architecture.md`](architecture.md) | Kotlin engine design: dispatcher confinement, DI composition root, Netty pipeline, Flyway/Hikari, LuaJ bridge |
| [`development.md`](development.md) | Gradle workflow, Kotlin coding standards, TDD gate and contribution workflow |
| [`operations.md`](operations.md) | Deployment, monitoring, security, backups and production operations on PostgreSQL 16 |
| [`release.md`](release.md) | Versioning policy and release steps for `./gradlew installDist` artifacts and the Docker image |
| [`hardware-sizing.md`](hardware-sizing.md) | JVM sizing (heap, GC, Netty direct memory) and upstream capacity-planning estimates |
| [`crypto-backend-evaluation.md`](crypto-backend-evaluation.md) | JVM crypto backend: BouncyCastle 1.80, Argon2id, XTEA/RSA parity gates |
| [`building/kotlin-local-validation.md`](building/kotlin-local-validation.md) | Authorized local build entry point, `JAVA_HOME` 25, `./gradlew clean test`, zero-warning gate |
| [`building/kotlin-rebuild.md`](building/kotlin-rebuild.md) | Gradle incremental/daemon builds, distribution install and JVM crash analysis |
| [`development/shared-build-cache.md`](development/shared-build-cache.md) | Gradle build cache and configuration cache across worktrees and forks |
| [`docker/quickstart-for-beginners.md`](docker/quickstart-for-beginners.md) | Docker quickstart: PostgreSQL 16 + Canary Kotlin server on ports 7171/7172 |
| [`lua-api/lua_api.md`](lua-api/lua_api.md) | Lua API reference (the API surface is unchanged because Lua stays Lua) |
| [`maps/startup-loading-performance.md`](maps/startup-loading-performance.md) | Kotlin map loader pipeline (OTBM, appearance protobuf, MapDownload) and startup notes |
| [`systems/README.md`](systems/README.md) | Cross-cutting system contracts |
| [`canary-codebase-implementation-audit.md`](canary-codebase-implementation-audit.md) | Per-subsystem audit of the C++ to Kotlin port |
| [`missing-features.md`](missing-features.md) | Complete per-subsystem inventory and ranked remaining work |
| [`game-play-features.md`](game-play-features.md) | 56-feature gameplay matrix and residual parity gaps |
| [`improvements.md`](improvements.md) | Repository improvement backlog |
| [`Plan/`](Plan/) | Architecture guidelines and wave plans |
| [`Done/`](Done/) | Completed specifications (001–064 and waves 20–59) |
| [`superpowers/`](superpowers/) | Design specs and implementation plans |

---

# What is Canary Kotlin Edition?

Canary Kotlin Edition is a Kotlin/JVM rewrite of the [Canary OpenTibia MMORPG
server](https://github.com/opentibiabr/canary). It preserves the upstream wire
protocol, formulas and Lua scripting surface while replacing the C++20 engine
toolchain with a modern JVM stack.

Key characteristics:

- Kotlin 2.4.21 core engine on Java 25 LTS
- Gradle 9.8.1 build with the Gradle wrapper
- Netty 4.1.118 network layer with `ByteBuf` framing
- Kotlin Coroutines `GameDispatcher` world-mutation actor
- LuaJ 3.0.1 execution of the upstream `data/` Lua scripts (Lua stays Lua)
- HikariCP 6.2.1 + Flyway 11.2.0 migrations on PostgreSQL 16
- H2 in-memory (PostgreSQL mode) for zero-configuration local runs and tests
- JUnit 5 + AssertJ + Mockk test suite (690 suites / 7627 tests as of 2026-10-14)
- Prometheus-compatible metrics registry and webhook integration

---

# Documentation Structure

```text
docs/
├── README.md                       # This hub
├── architecture.md
├── development.md
├── operations.md
├── hardware-sizing.md
├── release.md
├── crypto-backend-evaluation.md
├── building/                       # Local validation, rebuild, OS guides
├── development/                    # Shared build cache notes
├── docker/                         # Docker quickstart
├── lua-api/                        # Upstream Lua API reference
├── maps/                           # Map loading performance
├── python-scripts/                 # Upstream C++ maintenance scripts (parity only)
├── reference/                      # Upstream C++ Doxygen/LuaLS tooling
├── systems/                        # Cross-cutting system contracts
├── Plan/, Done/, superpowers/      # Kotlin planning and completed specs
├── missing-features.md
├── game-play-features.md
├── improvements.md
├── TEMPLATE_FEATURE.md
└── canary-codebase-implementation-audit.md
```

---

# Upstream Port Mapping

The full upstream documentation set (45 files, ~1.2 MB at `../.projects/canary/docs`)
was ported and adapted. The mapping below records every source and target file.

## Core guides

| Upstream (canary/docs) | Canary-Kotlin (docs) |
| --- | --- |
| `README.md` | `README.md` (this file) |
| `architecture.md` | `architecture.md` (rewritten for the Kotlin engine) |
| `development.md` | `development.md` |
| `operations.md` | `operations.md` |
| `hardware-sizing.md` | `hardware-sizing.md` |
| `release.md` | `release.md` |
| `crypto-backend-evaluation.md` | `crypto-backend-evaluation.md` |
| `building/local-validation.md` | `building/kotlin-local-validation.md` |
| `building/recompile.md` | `building/kotlin-rebuild.md` |
| `building/debian.md` | `building/debian.md` |
| `building/ubuntu.md` | `building/ubuntu.md` |
| `building/windows-(cmake).md` + `building/windows-(visual-studio-solution).md` | `building/windows-(gradle).md` (the two upstream Windows workflows merge into one Gradle/IDE guide) |
| `building/wsl-debian.md` | `building/wsl-debian.md` |
| `building/wsl-ubuntu.md` | `building/wsl-ubuntu.md` |
| `development/shared-build-cache.md` | `development/shared-build-cache.md` |
| `docker/quickstart-for-beginners.md` | `docker/quickstart-for-beginners.md` |
| `maps/startup-loading-performance.md` | `maps/startup-loading-performance.md` |

## Systems

| Upstream (canary/docs/systems) | Canary-Kotlin (docs/systems) | Notes |
| --- | --- | --- |
| `README.md` | `README.md` | Adapted index |
| `client-15-25-compatibility-update.md` | `client-15-25-compatibility-update.md` | Protocol profile ported; Task Board remains Lua |
| `content-reference-auditor.md` | `content-reference-auditor.md` | Upstream tooling reference (not ported) |
| `expert-pvp-porting-plan.md` | `expert-pvp-porting-plan.md` | Upstream engine reference with Kotlin status |
| `expert-pvp-implementation-roadmap.md` | `expert-pvp-implementation-roadmap.md` | Upstream engine reference with Kotlin status |
| `livestream/README.md` | `livestream/README.md` | Adapted index |
| `livestream/system-contract.md` | `livestream/system-contract.md` | Upstream engine contract with Kotlin status |
| `lua-api-docgen.md` | `lua-api-docgen.md` | Generator internals are upstream reference; the API docs are copied |
| `lua-shared-userdata.md` | `lua-shared-userdata.md` | Upstream C++ ownership contract; Kotlin LuaJ registry noted |
| `multiprotocol.md` | `multiprotocol.md` | Profile model ported; env/config mapping added |
| `performance-lifetime/README.md` | `performance-lifetime/README.md` | Upstream engine reference |
| `performance-lifetime/evidence.md` | `performance-lifetime/evidence.md` | Upstream engine reference |
| `performance-lifetime/parallel-monster-ai-roadmap.md` | `performance-lifetime/parallel-monster-ai-roadmap.md` | Upstream engine reference |
| `taskboard-module.md` | `taskboard-module.md` | Lua module (Lua stays Lua) |
| `weapon-proficiency.md` | `weapon-proficiency.md` | Implemented by the Kotlin port (Done/060) |

## Assets, Lua API and upstream tooling

| Upstream | Canary-Kotlin | Notes |
| --- | --- | --- |
| `lua-api/lua_api.md`, `lua_api.d.lua`, `lua_api.json`, `lua_api_quality_baseline.json` | `lua-api/` (same names) | Lua API surface is unchanged because Lua stays Lua (AGENTS.md §1.4). `lua_api.json` and `lua_api_quality_baseline.json` are generated JSON artifacts and cannot carry in-file comments; their provenance is recorded in this table and in `lua-api/lua_api.md`. |
| `python-scripts/*.py` | `python-scripts/` (same names + `README.md`) | Kept verbatim for parity maintenance against the upstream C++ sources at `../.projects/canary/src`; not rewritten as Kotlin. |
| `Doxyfile.in`, `config.ld.in` | `reference/Doxyfile.in`, `reference/config.ld.in` | Ported under `docs/reference/` with a header stating they target the upstream C++ engine; the Kotlin build does not consume them. |
| `canary-logo.png`, `canary-logo-transparent.png` | `canary-logo.png`, `canary-logo-transparent.png` | Branding assets copied byte-for-byte (binary files carry no in-file provenance line). |

Nothing existing in `canary-kotlin/docs` was removed or replaced by this port:
`missing-features.md`, `game-play-features.md`, `improvements.md`,
`TEMPLATE_FEATURE.md`, `canary-codebase-implementation-audit.md`, `Plan/`,
`Done/`, `Implementing/` and `superpowers/` remain the authoritative
Kotlin-specific documents.

---

# Architecture Guide

The [Architecture Guide](architecture.md) explains how the Kotlin engine is
built internally. Topics include:

- Dependency-injected composition root (`ServerBootstrap`) instead of singletons
- Single-threaded `GameDispatcher` coroutine confinement and dispatcher lanes
- Netty pipeline and `ByteBuf` lifecycle
- Packet handling (`GamePacketRouter` and modular handlers)
- Creature, combat, map and event systems
- LuaJ scripting bridge (Lua stays Lua)
- HikariCP/Flyway persistence on PostgreSQL 16 and H2 test mode
- Metrics and observability
- Gradle build and testing architecture

Recommended for engine contributors, system designers, technical reviewers and
advanced server developers.

---

# Development Guide

The [Development Guide](development.md) explains how to build, test and
contribute. Topics include:

- JDK 25 and Gradle wrapper setup
- `./gradlew test` / `./gradlew run` / `./gradlew installDist` workflows
- TDD gate and zero-warning build policy (AGENTS.md §4)
- Kotlin coding standards and package layout
- Lua development under `data/`
- Feature documentation gates (AGENTS.md §5) and the wave publication gate (AGENTS.md §6)
- Pull request and review workflow

---

# Operations Guide

The [Operations Guide](operations.md) focuses on operating a Kotlin server
safely. Topics include:

- JVM and PostgreSQL 16 deployment models
- Docker deployment (server built from the repository `Dockerfile`)
- Configuration via `CANARY_*` environment variables
- Security, monitoring and alerting
- `pg_dump`/`pg_restore` backup and recovery
- Flyway migration and upgrade procedures
- Incident response, including JVM thread/heap dumps

---

# Technology Stack

## Core

- Kotlin 2.4.21 / Java 25 LTS
- Gradle 9.8.1 (wrapper included)
- Netty 4.1.118 (`netty-all`)
- Kotlin Coroutines 1.10.1
- LuaJ 3.0.1 (`luaj-jse`)
- BouncyCastle 1.80 (`bcprov-jdk18on`)

## Persistence

- PostgreSQL 16 (production and Docker)
- H2 2.3.232 in PostgreSQL mode (local runs and tests)
- HikariCP 6.2.1 connection pool
- Flyway 11.2.0 migrations (`src/main/resources/db/migration/V1`–`V21`)

## Infrastructure

- Docker Compose quickstart (PostgreSQL 16 + server container)
- Prometheus-compatible metrics (`MetricsRegistry`)
- Webhook integration for operational events

## Supported Platforms

- Linux
- Windows
- macOS

Any platform with a JDK 25 installation and the bundled Gradle wrapper is
supported; Docker adds a reproducible server image on `eclipse-temurin:25-jre`.

---

# Deployment Options

## Docker

Recommended for local development, testing and LAN demos:

```bash
docker compose up -d --build
```

The stack builds the Canary Kotlin server from the repository `Dockerfile` and
starts PostgreSQL 16. The login protocol listens on `7171` and the game world
on `7172`. Test account `1` / `1` is seeded by the Flyway migrations.

The upstream C++ quickstart extras — the MyAAC website and
`opentibiabr/login-server` — are **not** part of this port. The native login
server on `7171` is the entry point for clients. See
[docker/quickstart-for-beginners.md](docker/quickstart-for-beginners.md).

## Native distribution

Recommended for production and custom infrastructure:

```bash
./gradlew installDist
./build/install/canary-kotlin/bin/canary-kotlin
```

The native distribution defaults to the H2 in-memory database unless
`CANARY_DB_*` variables point at a real PostgreSQL 16 instance.

---

# Contributing

Contributors are encouraged to:

- Report bugs
- Improve documentation
- Write tests
- Submit pull requests
- Improve Lua systems (as Lua)
- Enhance the Kotlin engine

Before contributing:

1. Read [`architecture.md`](architecture.md)
2. Read [`development.md`](development.md)
3. Read `AGENTS.md` (binding architecture, lifetime and documentation gates)
4. Follow the Kotlin coding standards
5. Run `./gradlew test` locally
6. Update documentation when necessary, including the wave documentation gate

---

# Additional Resources

- [Project repository](https://github.com/gdamoreira/canary-kotlin)
- [Upstream Canary repository](https://github.com/opentibiabr/canary)
- Upstream documentation snapshot: `../.projects/canary/docs`
- [Documentation index](README.md)

---

# Audience Guide

| Role | Recommended Reading |
| --- | --- |
| New Contributor | README → Development |
| Lua Developer | README → Development → `lua-api/` |
| Engine Developer | README → Architecture → Development |
| DevOps Engineer | README → Operations → `docker/` |
| Server Administrator | README → Operations → Hardware sizing |
| Technical Lead | README → Architecture → Operations |

---

# Conclusion

The Canary-Kotlin documentation is organized around three core perspectives:

- **Architecture** — how the Kotlin engine works
- **Development** — how the engine is built and tested
- **Operations** — how the server is run and maintained

Together with the ported upstream system contracts in [`systems/`](systems/),
these documents provide a complete reference for understanding, extending,
deploying and operating Canary Kotlin Edition.
