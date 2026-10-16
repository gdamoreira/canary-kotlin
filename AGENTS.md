# Canary-Kotlin Directives and Architecture Decisions

This document records the binding architectural policies, lifetime safety rules, and implementation gates for **Canary-Kotlin**.

---

## 1. Core Architectural Mandates

### 1.1 Strict Canary Parity Mandate (No Invented Methods or APIs)
- **NEVER implement classes, methods, signatures, or logic that do not exist in the Canary project.**
- Every function, method name, signature, and state behavior in Kotlin must directly correspond to its C++ or Lua counterpart in Canary (e.g. `onCreatureSay`, `onCreatureAppear`, `setPlayerInteraction` instead of fabricated helper methods like `onHear`).
- If a method or helper does not exist in Canary's codebase (`src/` or `data/`), **DO NOT INVENT IT**. Verify the exact C++ method definition before writing code.
- Target **Java 25 LTS** runtime with **Kotlin 2.4+** and **Gradle 9+**.
- Eliminate global singletons (`g_game()`, `g_dispatcher()`, `g_configManager()`) in favor of clean **Dependency Injection** (constructor injection) and interface contracts (`AccountRepository`, `PlayerStorage`, `GameMap`).
- Use Kotlin **value classes** (`@JvmInline value class Direction`) and sealed interfaces for domain modeling instead of raw integer bitmasks.

### 1.2 World Mutation Concurrency & Thread Confinement
- **Single-Threaded Event Loop Confinement**:
  - All modifications to the spatial grid (`Tile`, `Item`, `Creature`, `Player`) MUST occur within the single-threaded `GameDispatcher` coroutine context (`Dispatchers.Default.limitedParallelism(1)`).
  - Netty I/O worker threads (`NioEventLoopGroup`) must NEVER mutate world entities directly. Network events must be dispatched via `dispatcher.dispatch(DispatcherLane.WorldCommit) { ... }`.
  - Never introduce `synchronized` blocks or `ReentrantLock` across game world entities; state confinement replaces lock synchronization entirely.

### 1.3 Memory & ByteBuf Lifecycle Safety
- **Zero-Copy & Direct Buffer Management**:
  - Netty `ByteBuf` instances allocated in inbound pipelines must be properly released across handler boundaries (`ReferenceCountUtil.release(msg)`).
  - Outbound frames must use Little-Endian byte order (`writeShortLE`, `writeIntLE`) to conform to OpenTibia wire contracts.

### 1.4 Lua Scripting Boundary (Do Not Port Lua Into Kotlin)
- **Lua stays Lua**: **NEVER re-implement Lua game logic (data files, `data/scripts`, `data-global`, NPC/quest/spell/action scripts, talkactions, movements, etc.) as Kotlin classes, methods, or hardcoded logic.**
- Lua behavior must remain authored and executed as Lua: keep the Lua files as Lua (`data/` trees) and run them through the Lua bridge (`LuaScriptBridge`/LuaJ) so the original scripts are the source of truth.
- Kotlin code may only provide the **C++-parity engine primitives and bindings** that Lua scripts call (e.g. `Creature:getHealth()`, `Game.createItem()`, event registration). Kotlin must not encode the script's decision logic itself.
- If a gameplay behavior in Canary lives in a `.lua` file, the Kotlin port must load that same `.lua` file, not a Kotlin translation of it. If it lives in C++ (`src/`), port it to Kotlin. Never move logic across that boundary.

---

## 2. Database & Persistence Directives

### 2.1 Database Compatibility & Migration Discipline
- **Target Relational Databases**: **MariaDB 11.4+** (default — the shared database of the Docker stack, MyAAC and the upstream login-server) **or PostgreSQL 16+**, selected with `CANARY_DB_ENGINE` (`maria`/`mariadb`/`mysql` — default; `postgres`/`postgresql` — PostgreSQL). An unknown value fails fast at boot.
- **Local Testing Mode**: Always provide zero-configuration local runs via **H2 In-Memory in PostgreSQL mode** (`MODE=PostgreSQL`); the automated suite never requires a live database daemon.
- **Flyway Migrations** (per-dialect trees):
  - Schema changes must NEVER be executed through ad-hoc manual SQL scripts.
  - Every schema modification must be added to **both** dialect trees at the same version: `src/main/resources/db/migration/postgres/V<version>__<description>.sql` and `src/main/resources/db/migration/mariadb/V<version>__<description>.sql`.
  - Flyway runs automatically upon server boot before accepting network traffic, selecting the tree from the detected connection engine.

---

## 3. Wire Protocol & Cryptography Gates

### 3.1 Network Framing & Cryptography Parity
- **Adler-32 Checksum**: All client-first packets must validate the 4-byte Adler-32 checksum prior to opcode parsing.
- **XTEA Cipher**:
  - 64 rounds (32 cycles) using DELTA constant `0x9E3779B9` (`-0x61C88647`).
  - Block size must strictly be 8 bytes; unaligned payloads are padded to multiples of 8.
- **RSA Handshake**:
  - 1024-bit raw modular exponentiation decryption (`c^d mod n`).
  - Payload must verify the leading check byte `0x00`.

---

## 4. Testing & Verification Requirements (TDD Gate)

- **Test-Driven Development (TDD)**:
  - Every new opcode handler, parser, formula, and persistence method must be backed by a failing test first (RED) before implementing the minimal code (GREEN).
  - Test suites must use real domain code and Netty's `EmbeddedChannel` without requiring live external sockets or network Daemons.
- **Verification Before Completion**:
  - Running `./gradlew test` must pass with zero failures and zero compile warnings before any commit or release claim.

---

## 5. Feature Documentation & Canary Code Reference Mandate

Whenever creating or proposing any new development document in `docs/development/`, the document **MUST** adhere to the standard template and **MUST include exact Canary C++ source file references**:

### 5.1 Mandatory Document Structure
Each document must follow this layout:
```markdown
# Feature: <Feature Name>

## 1. Explanation
[What the feature is, high-level summary of the domain logic]

## 2. Impact
[Why it matters, gameplay/system consequence]

## 3. Functionality Affected
[Specific opcodes, state machines, or mechanics modified]

## 4. Agent Implementation Details
[Kotlin components, architectural patterns, concurrency considerations]

## 5. Test Reference
[Automated unit and integration test class references]

## 6. Canary Original Code Reference
- **Source Files**: `<path/to/canary/file.cpp>, <path/to/header.hpp>`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `<e.g. ProtocolGame::parseAttack, Combat::doCombat>`
```

### 5.2 Rule of File Verification
- Before writing a feature document, locate the corresponding files in the Canary repository using `glob` or `grep`.
- Never invent or assume file paths. Record the exact relative path from the Canary root.
- If the feature has C++ tests in Canary (`tests/unit/` or `tests/integration/`), record them in the document and create the corresponding Kotlin transcript test.

---

## 6. Mandatory Wave Documentation & Publication Gate (MUST)

**Before each wave**, create a wave document `docs/development/wave-<N>-<topic>.md` describing what the wave will implement: the tasks, the Kotlin deliverables, the Canary C++ sources (exact paths), and the tests. Then run the wave. Documents are created directly in `docs/development/` — there is no plan/implementing/done lifecycle folder.

After **each wave** (a batch of parallel-agent implementations merged into `main`), the following is a **MUST** and is not complete until every step has run:

1. **Update every affected document.**
   - `docs/development/implementation-status.md` — parity coverage numbers, the affected inventory rows/ranked gaps, the gameplay matrix status, the improvement tracker and the current test-suite/test-count metrics.
   - `docs/development/` — **update the wave document in place** with its Implementation Status and completion notes; keep feature documents directly in `docs/development/`.
   - `README.md` — feature coverage, project structure, and test metrics.
   - `CHANGELOG.md` — add the new phase entry and refresh the Current Status metrics.
2. **Commit the documentation update in a separate commit** (never fold it into the code/merge commits).
3. **Push to `origin`** (`git push origin main`).

A wave is **not finished** until the wave document in `docs/development/` is updated and the documentation commit is pushed. Do not start the next wave before this gate passes. Keep the documented metrics truthful: run `./gradlew test` and record the real suite/test counts and any new residual gaps.
