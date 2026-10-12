# Canary-Kotlin Directives and Architecture Decisions

This document records the binding architectural policies, lifetime safety rules, and implementation gates for **Canary-Kotlin**.

---

## 1. Core Architectural Mandates

### 1.1 Idiomatic Kotlin over Direct C++ Translation
- **Never translate raw C++ pointers, singletons, or manual mutexes 1:1.**
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

---

## 2. Database & Persistence Directives

### 2.1 Database Compatibility & Migration Discipline
- **Target Relational Database**: **PostgreSQL 16+**.
- **Local Testing Mode**: Always provide zero-configuration local runs via **H2 In-Memory in PostgreSQL mode** (`MODE=PostgreSQL`).
- **Flyway Migrations**:
  - Schema changes must NEVER be executed through ad-hoc manual SQL scripts.
  - Every schema modification must be placed in `src/main/resources/db/migration/V<version>__<description>.sql`.
  - Flyway runs automatically upon server boot before accepting network traffic.

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
