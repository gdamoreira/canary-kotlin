# Development Guide

> Adapted from canary/docs/development.md for the Kotlin/JVM engine.
> Canary-Kotlin status: adapted to the Kotlin/Gradle workflow. The upstream
> standards that still apply (feature documentation gates, wave publication
> gate, Lua-first customization) are kept and referenced to `AGENTS.md`.

## Introduction

This document describes the development workflow used by Canary Kotlin Edition
and serves as a reference for contributors who want to:

- Build the server locally
- Develop new features
- Fix bugs
- Create Lua systems
- Write tests
- Submit pull requests

The engine is written primarily in:

- **Kotlin 2.4.21** (server core, targeting Java 25 LTS)
- **Lua** (gameplay systems, executed by LuaJ 3.0.1)
- **SQL** (Flyway migrations for PostgreSQL 16)
- **Gradle 9.8.1** (build system, through the bundled wrapper)

---

# Development Philosophy

The port follows a few important principles:

1. Keep the Kotlin core stable and dependency-injected.
2. Prefer gameplay customization through Lua (AGENTS.md §1.4).
3. Maintain byte-level compatibility with the upstream engine and its datapacks.
4. Favor readability over clever implementations.
5. Keep systems modular and testable.
6. Never invent methods or APIs that do not exist upstream (AGENTS.md §1.1).

---

# Development Environment

## Recommended Tools

### IDEs

Supported:

- IntelliJ IDEA (recommended; native Gradle/Kotlin support)
- Visual Studio Code with the Kotlin extension
- Any editor with Gradle and Kotlin language support

### Required Software

- **JDK 25 LTS** (`JAVA_HOME` pointing at it; see
  [building/kotlin-local-validation.md](building/kotlin-local-validation.md))
- Git
- No external database is required: H2 in-memory (PostgreSQL mode) covers local
  runs and tests.

The repository includes the Gradle wrapper (`gradlew`, `gradlew.bat`); you do
not need a system Gradle installation.

---

# Repository Structure

```text
canary-kotlin/
│
├── src/
│   ├── main/kotlin/org/opentibia/canary/
│   ├── main/resources/db/migration/   # Flyway V1–V21
│   └── test/kotlin/org/opentibia/canary/
├── data/                              # Lua resources
├── docs/
├── gradle/wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew / gradlew.bat
├── docker-compose.yml
├── Dockerfile
└── AGENTS.md
```

---

# Development Setup

## Option 1: Local (recommended)

```bash
git clone https://github.com/gdamoreira/canary-kotlin.git
cd canary-kotlin
./gradlew test          # full test suite; zero warnings is the gate
./gradlew run           # zero-configuration server on 7171/7172 (H2 in-memory)
```

`./gradlew run` seeds test account `1` / password `1` with the pre-seeded
characters from `V2__seed_test_accounts.sql`.

Docker is ideal when:

- Testing without a local JDK
- Validating the production-like PostgreSQL 16 path
- Running local or LAN demos

See [docker/quickstart-for-beginners.md](docker/quickstart-for-beginners.md).

## Option 2: Native development

Required software:

- Git
- JDK 25 LTS
- The bundled Gradle wrapper

Windows requires a JDK 25 installer; see
[building/windows-(gradle).md](<building/windows-(gradle).md>). Linux package
installations are covered by [building/debian.md](building/debian.md) and
[building/ubuntu.md](building/ubuntu.md).

---

# Cloning the Repository

```bash
git clone https://github.com/gdamoreira/canary-kotlin.git
cd canary-kotlin
```

No submodules or dependency bootstrap step is required; Gradle resolves
dependencies from Maven Central on the first build.

---

# Configuration

Configuration precedence is:

```text
CLI options > properties file (--config) > CANARY_* environment > defaults
```

Server CLI flags (`config/ServerCli.kt`):

```text
--config <path>       properties file with config.lua-style keys
--data-dir <path>     datapack directory override
--db-host, --db-port, --db-name, --db-user, --db-password
```

A properties file passed with `--config` uses the `config.lua` identifiers as
keys (for example `loginProtocolPort=7171`), mirroring the upstream
`config.lua` surface. Typed keys and defaults live in
`src/main/kotlin/org/opentibia/canary/config/ConfigKeys.kt`.

Important settings:

```text
CANARY_SERVER_NAME = "Canary Kotlin Server"
CANARY_IP          = "127.0.0.1"
CANARY_DB_HOST     = "127.0.0.1"
CANARY_DB_NAME     = "canary"
CANARY_DB_USER     = "canary"
CANARY_DB_PASSWORD = "canary"
```

Never commit personal configuration or secrets.

---

# Build System

Use the Gradle wrapper:

```bash
./gradlew build          # compile, test, assemble
./gradlew test           # test suite only
./gradlew installDist    # runnable distribution in build/install/canary-kotlin
./gradlew clean          # remove build outputs
```

The build targets Java 25 through `jvmToolchain(25)` and Kotlin 2.4.21. Do not
change the toolchain version without updating this document and the build gate.

---

# Building the Distribution

```bash
./gradlew clean installDist
```

Layout:

```text
build/install/canary-kotlin/
├── bin/canary-kotlin          # launcher script
└── lib/                       # application and dependency JARs
```

The same distribution is copied into the Docker image by the multi-stage
`Dockerfile`.

---

# Dependency Management

Dependencies are declared in `build.gradle.kts`. Examples:

```text
Netty (netty-all)
kotlinx-coroutines-core
Logback + kotlin-logging-jvm
PostgreSQL JDBC, H2, SQLite JDBC
HikariCP
Flyway core + flyway-database-postgresql
BouncyCastle (bcprov-jdk18on)
LuaJ (luaj-jse)
JUnit 5, Mockk, AssertJ
```

If dependency resolution fails:

```bash
./gradlew --refresh-dependencies installDist
```

Gradle caches dependencies under `~/.gradle/caches`; see
[development/shared-build-cache.md](development/shared-build-cache.md) before
managing caches across worktrees.

---

# Running the Server

After building:

```bash
./build/install/canary-kotlin/bin/canary-kotlin
```

or during development:

```bash
./gradlew run
```

Expected startup sequence:

```text
Loading config
Connecting database + applying Flyway migrations
Loading map (OTBM)
Loading items/monsters/NPCs
Loading Lua scripts
Starting network listeners (login/game/status)
Server online
```

---

# Database Development

The schema is owned by Flyway migrations under
`src/main/resources/db/migration/`. There is no `schema.sql` import step.

Main tables:

```text
accounts
players
guilds
houses
market
player_storage
player_skills
```

Rules (AGENTS.md §2.1):

- Every schema modification is a new `V<version>__<description>.sql` migration.
- Never edit a migration that has been applied.
- Migrations run automatically before network traffic is accepted.
- Keep migrations compatible with PostgreSQL 16 and H2 PostgreSQL mode.

---

# Lua Development

Most gameplay features belong in Lua.

Examples:

- Quests
- NPCs
- Actions
- Talkactions
- Spells
- Events

Typical locations:

```text
data/scripts/
data/libs/
data/items/
data/XML/
```

The engine loads the same script trees as upstream; Kotlin does not re-implement
script decision logic (AGENTS.md §1.4).

---

# Lua API

The server exposes Kotlin engine primitives through Canary-named Lua bindings.

Examples:

```lua
player:addExperience(1000)

player:addItem(2160)

Game.getPlayers()
```

Generated API documentation is available in:

```text
docs/lua-api/
```

The `generateLuaApiDocs` key exists in the configuration surface. The committed
`docs/lua-api/` files are the upstream-generated reference; the API surface is
unchanged because the Lua scripts run as-is.

---

# Adding New Features

## Prefer Lua First

Before modifying Kotlin:

```text
Can this be implemented entirely in Lua?
```

If yes, use Lua. If no, extend the Kotlin engine, then expose it through
`lua/bindings/` if script access is required.

---

# Adding a New Lua Event

Example:

```lua
local event = CreatureEvent("Example")

function event.onLogin(player)
    player:sendTextMessage(MESSAGE_STATUS, "Welcome!")
    return true
end

event:register()
```

Kotlin-side event plumbing lives in `lua/runtime/` and `lua/registry/`.

---

# Kotlin Development

## Source Layout

```text
org.opentibia.canary/
├── account/     creatures/   items/     network/
├── bootstrap/   database/    kv/        persistence/
├── chat/        economy/     lib/       protobuf/
├── combat/      game/        lua/       quest/
├── config/      io/          map/       social/
├── monitoring/  spells/      systems/   utils/
```

Before creating new modules:

- Check existing systems.
- Extend existing classes when appropriate.
- Avoid duplicate functionality.
- Follow AGENTS.md §1.1: every class, method and signature must correspond to
  an upstream counterpart.

---

# Coding Standards

## Naming

Classes and interfaces:

```kotlin
Player
Monster
GamePacketRouter
AccountRepository
```

Functions and properties:

```kotlin
addExperience()
getHealth()
sendTextMessage()
```

Variables:

```kotlin
playerLevel
damageAmount
targetPosition
```

Constants:

```kotlin
MAX_PLAYERS
MAX_CONTAINER_ITEMS
```

Domain names mirror the upstream C++ names wherever a counterpart exists; do
not "improve" names during a port.

## General Guidelines

Prefer immutable values and `val` over `var` unless mutation is required.

Use constructor injection; never reintroduce global singletons
(`g_game()`, `g_dispatcher()`, `g_configManager()`) — see AGENTS.md §1.1.

Use value classes (`@JvmInline value class Direction`) and sealed interfaces
instead of raw integer bitmasks.

Never capture `this`, references or mutable-container pointers across
dispatcher, timer, worker or Lua callback boundaries; capture immutable values
plus `std::weak_ptr` equivalents (`WeakReference`, identity + generation, or
re-resolvable IDs) as required by the repository's deferred-callback and
static-ownership lifetime rules in `AGENTS.md`.

Do not add comments unless they explain upstream parity or a non-obvious
constraint.

---

# Logging

Use the existing logging systems (`kotlin-logging` over SLF4J/Logback).

Good:

```kotlin
logger.info { "Player $playerName logged in" }
```

Bad:

```kotlin
println("debug")
```

Never commit debugging output.

---

# Debugging

## Local JVM

Run through the IDE debugger or:

```bash
./gradlew run --debug-jvm
```

Helper commands for a running server:

```bash
jcmd <pid> Thread.print
jcmd <pid> GC.heap_info
```

## Common Debug Targets

Investigate:

- Crashes (`hs_err_pid*.log`)
- Heap pressure and GC pauses
- Dispatcher stalls (thread dumps: is the world thread blocked in I/O?)
- Lua errors

---

# Testing

Canary Kotlin includes an automated JUnit 5 test suite.

Run it:

```bash
./gradlew test
```

The gate is zero failures and zero compile warnings (AGENTS.md §4).

Tests should be added for:

- New systems
- Bug fixes
- Edge cases
- Regression prevention

Test conventions:

- Use H2 in-memory (PostgreSQL mode) for persistence tests.
- Use Netty `EmbeddedChannel` for protocol tests; no live sockets.
- Mirror upstream C++ test cases as transcript tests where they exist.
- Follow TDD: write the failing test first (RED) before the minimal
  implementation (GREEN).

---

# Feature Documentation Gates

Every development document in `docs/development/` must:

- follow the standard template ([TEMPLATE_FEATURE.md](development/TEMPLATE_FEATURE.md));
- include exact upstream C++ source references (file paths, key methods,
  opcodes) verified against `../.projects/canary`;
- record the automated test classes that cover it.

This is the AGENTS.md §5 mandate. Never invent or assume upstream file paths.

## Wave publication gate

Before each wave, create `docs/development/wave-<N>-<topic>.md`. After each wave
(AGENTS.md §6):

1. Update `docs/development/implementation-status.md`, `docs/development/implementation-status.md`,
   `README.md` and `CHANGELOG.md`.
2. Update the wave document in place in `docs/development/` (there is no
   plan/implementing/done lifecycle folder).
3. Commit the documentation update separately from code changes.
4. Push to origin.

A wave is not finished until the documentation gate passes with truthful
metrics from a real `./gradlew test` run.

---

# Pull Request Workflow

## Create a Branch

```bash
git checkout -b feature/new-system
```

Examples:

```text
feature/
fix/
refactor/
docs/
```

## Commit Messages

Good:

```text
fix(player): prevent logout race condition

feat(wheel): add progression API

refactor(combat): simplify condition processing
```

Avoid:

```text
fix stuff

update

changes
```

## Before Opening a Pull Request

Checklist:

- `./gradlew test` passes with zero warnings
- No debug code
- Documentation updated (feature doc, wave gate)
- Lua scripts validated (they run unchanged)
- Flyway migrations added for schema changes

## Reviewing Changes

Focus on:

- Correctness
- Performance
- Security
- Maintainability
- Upstream parity (names, signatures, wire bytes)

Do not approve changes solely because they work locally.

---

# Performance Considerations

Critical systems:

- Pathfinding
- Combat
- Dispatcher and scheduler
- Networking
- Database access
- JVM memory behavior (allocation churn, GC pauses)

Before introducing changes:

```text
How does this behave with 1000 players?
```

Small inefficiencies become large server costs. Prefer immutable values, avoid
per-tick allocation in hot paths, and keep world mutation on the dispatcher.

---

# Common Development Tasks

## Add a Spell

```text
data/scripts/spells/
```

## Add a Monster

Monster types are parsed by
`src/main/kotlin/org/opentibia/canary/io/pipeline/MonsterPipelineService.kt`;
the shared Lua registration helpers live in
`data/scripts/lib/register_monster_type.lua` and
`data/libs/functions/monstertype.lua`.

## Add an NPC

```text
data/ (NPC Lua scripts, loaded through the Lua bridge)
```

## Add a Quest

```text
data/scripts/
```

## Add Engine Functionality

```text
src/main/kotlin/org/opentibia/canary/
```

and expose it through:

```text
src/main/kotlin/org/opentibia/canary/lua/bindings/
```

if Lua access is required.

---

# Troubleshooting

## JAVA_HOME / toolchain errors

Gradle reports the toolchain it needs (Java 25). Point `JAVA_HOME` at a JDK 25
installation and rerun. See
[building/kotlin-local-validation.md](building/kotlin-local-validation.md).

## Gradle daemon or cache issues

```bash
./gradlew --stop
./gradlew clean test
```

If a specific build-cache entry is corrupt:

```bash
./gradlew clean test --no-build-cache
```

Do not delete `~/.gradle` wholesale unless dependency caches are corrupted.

## Configuration cache issues

If the configuration cache fails after a build-script change:

```bash
./gradlew --no-configuration-cache test
```

and inspect the reported problem before re-enabling it. See
[development/shared-build-cache.md](development/shared-build-cache.md).

## Lua errors

Check server console output and the loaded script paths. Lua scripts run as-is;
syntax errors point at the script file and line.

---

# Development Workflow Summary

```text
Create Branch
      ↓
Write Failing Test (TDD)
      ↓
Implement Feature
      ↓
Build + Run Tests
      ↓
Manual Validation
      ↓
Feature Doc + Wave Doc Updates
      ↓
Open Pull Request
      ↓
Code Review
      ↓
Merge
```

---

# Conclusion

Canary Kotlin development is centered around:

- Kotlin/JVM for engine performance and type safety
- Lua for gameplay flexibility
- Gradle for reproducible builds
- JUnit 5 test coverage with H2 and `EmbeddedChannel`
- Automated testing and code review

Contributors are encouraged to keep changes modular, documented, tested, and
aligned with the project's goal of a modern, extensible MMORPG server platform
with strict upstream parity.
