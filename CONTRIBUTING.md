# Contributing to Canary Kotlin Edition

Thanks for helping improve the Kotlin port of the Canary OpenTibia server.
This guide mirrors the upstream Canary contribution flow, adapted to the JVM
toolchain and this repository's gates.

## Before you start

- Read [`AGENTS.md`](AGENTS.md). It records the binding architectural policies:
  strict Canary parity (§1.1), WorldCommit thread confinement (§1.2), ByteBuf
  lifecycle safety (§1.3), "Lua stays Lua" (§1.4), Flyway migration discipline
  (§2.1), and the TDD/zero-warning gate (§4).
- Read [`docs/README.md`](docs/README.md) for the documentation index and
  [`docs/development.md`](docs/development.md) for the Gradle workflow.

## Toolchain

- JDK 25 LTS (`JAVA_HOME` must point at it)
- Gradle wrapper 9.8.1 (always use `./gradlew`)
- Kotlin 2.4.21, Netty 4.1, Coroutines 1.10, LuaJ 3.0, BouncyCastle 1.80
- PostgreSQL 16 for production runs; H2 in-memory (PostgreSQL mode) for tests

## Workflow

1. Fork the repository and create a topic branch.
2. Write a failing test first (RED), then the minimal implementation (GREEN).
   Tests use JUnit 5 + AssertJ + Mockk; network paths use Netty
   `EmbeddedChannel` — no live sockets or daemons.
3. Run the full gate before committing:

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS; see docs/building for other OSes
   ./gradlew clean test
   ```

   Zero test failures and zero Kotlin warnings are required.
4. Keep commits focused. Never commit secrets or key material.
5. Update the relevant documentation (`docs/`, `CHANGELOG.md`) in a separate
   commit when behavior or coverage changes.

## Parity rules (summary)

- Never invent methods, classes, values, or config keys that do not exist in
  Canary (`src/` or `data/`); port the C++ or run the Lua.
- Never re-implement Lua gameplay (`data/**/*.lua`) in Kotlin; load the same
  `.lua` file through the Lua bridge.
- Database schema changes go through `src/main/resources/db/migration/`
  (Flyway) — no ad-hoc SQL.

## Reporting issues

Use the GitHub issue tracker of `gdamoreira/canary-kotlin`. Include the JDK
version, the exact Gradle command, and the failing test or packet capture.
