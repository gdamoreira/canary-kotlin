# Wave 73 Plan — MariaDB Support for the Kotlin Server

**Status:** In progress — round 1 dispatched
**Baseline:** `main` @ `d8916ec5`; 798 suites / 8308 tests green; zero Kotlin warnings
**Source of scope:** user directive: "Make it possible to run MariaDB instead of PostgreSQL in the kotlin server." **Scaling proposals excluded.**
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Run the game server on **MariaDB 11.4** (upstream Canary's engine) as an
alternative to PostgreSQL 16, selected by configuration, so the whole stack —
server, MyAAC and login-server — can share one MariaDB database. PostgreSQL
stays the default; H2 (PostgreSQL mode) stays the test engine.

## 2. Design (fixed interfaces for parallel work)

```kotlin
enum class DatabaseEngine { POSTGRESQL, MARIADB }

// DatabaseConfig gains: val engine: DatabaseEngine = DatabaseEngine.POSTGRESQL
// jdbcUrl: jdbc:postgresql://… (POSTGRESQL) | jdbc:mariadb://… (MARIADB)

// ServerCli.databaseConfig reads CANARY_DB_ENGINE:
//   postgres|postgresql -> POSTGRESQL (default; port default 5432)
//   maria|mariadb|mysql -> MARIADB    (port default 3306)
//   unknown             -> fail fast naming the variable

// DatabaseConnectionFactory:
//   migrationsLocationFor(engine) = "classpath:db/migration/postgres" | "classpath:db/migration/mariadb"
//   migrate() picks the tree from the connection product name
//   (H2/PostgreSQL -> postgres, MariaDB/MySQL -> mariadb) unless locations are passed.
```

Migrations move to dialect subfolders: `db/migration/postgres/V1..V22` (moved
as-is) and `db/migration/mariadb/V1..V22` (new, same versions; V15 is absent in
both trees, mirroring the current numbering).

## 3. Round 1 workstreams (parallel)

### K1 — Dialect core

**Scope:** the interfaces above; move the existing migrations to
`db/migration/postgres/`; add dependencies (`org.mariadb.jdbc:mariadb-java-client`,
`org.flywaydb:flyway-database-mariadb`, testcontainers core + mariadb for round
2); engine-aware Hikari driver/URL; H2 path unchanged.

**Files (owner):** `database/DatabaseConnectionFactory.kt`, `config/ServerCli.kt`,
`config/ConfigKeys.kt` (only if needed for defaults), `build.gradle.kts`,
`src/main/resources/db/migration/**` (move), `bootstrap/ServerBootstrap.kt`
(migrate call site only).

**Tests:** engine parsing (aliases + fail-fast), URL building, location
selection from product names, H2 suite stays green on the postgres tree.

### K2 — MariaDB migration tree

**Scope:** `db/migration/mariadb/V1..V22` transcribed 1:1 from the postgres tree
with MariaDB syntax: `BIGSERIAL` → `BIGINT AUTO_INCREMENT`,
`BYTEA` → `LONGBLOB`, `DOUBLE PRECISION` → `DOUBLE`; `IF NOT EXISTS` forms kept
(MariaDB 10.1+ supports them); no partial indexes exist. Same version numbers,
same table/column names, same seed semantics.

**Files (owner):** `src/main/resources/db/migration/mariadb/**`.

**Tests:** a structural test asserting the mariadb tree mirrors the postgres
tree (file set/versions) and contains no Postgres-only tokens
(`BIGSERIAL`, `BYTEA`, `ON CONFLICT`, `pg_`); the real execution is covered by
round 2's testcontainers test.

### K3 — Runtime SQL portability

**Scope:** introduce `SqlDialect` (detect from connection metadata:
H2 / PostgreSQL / MariaDB-MySQL) and use it in `IOMapSerialize`'s upserts
(MariaDB gets the C++ original `INSERT … ON DUPLICATE KEY UPDATE`, PostgreSQL
keeps `ON CONFLICT`, H2 keeps `MERGE … KEY`); audit the remaining SQL for
MariaDB portability (quoting, booleans, byte handling).

**Files (owner):** `database/SqlDialect.kt` (new), `io/IOMapSerialize.kt`.

**Tests:** dialect detection; SQL-builder shapes per dialect; existing map-save
tests green on H2.

## 4. Round 2 workstream (after round-1 merge)

### K4 — MariaDB integration test and docs

**Scope:**
- testcontainers `MariaDbDialectIntegrationTest`: MariaDB 11.4 container, engine
  MARIADB, full migration chain, key-table assertions, KV + repository round
  trip; `assumeTrue(Docker available)` so local no-Docker runs skip and CI runs it.
- Compose: `CANARY_DB_ENGINE` passthrough on the server; a
  `docker/docker-compose.mariadb.yml` override that points the server at the
  `mariadb` service (single-database mode where MyAAC/login-server read the game
  DB); `.env.dist` entries.
- Docs: `DOCKER.md` (single-DB mode), README (database section: PostgreSQL 16+
  or MariaDB 11.4+), `AGENTS.md` §2.1 (target engines + H2 tests),
  `CHANGELOG.md` (Phase 77).

**Files (owner):** `src/test/kotlin/**` (integration test only),
`docker/**`, `.env.dist` is inside `docker/`, `README.md`, `AGENTS.md`,
`CHANGELOG.md`, `docs/development/implementation-status.md` (§1.2 note).

## 5. Verification gate

Round 1: per-branch focused tests + full suite (H2), zero warnings; ancestry
check. Round 2: full suite (integration test skips locally without Docker) +
CI green (CI runners have Docker, so the MariaDB test executes there).

## 6. Documentation gate (AGENTS.md §6)

Update `implementation-status.md`, `README.md`, `CHANGELOG.md` (Phase 77), and
this document in place.

## 7. Out of scope

Scaling proposals; the deferred list; dropping PostgreSQL support (both engines
are supported, PostgreSQL remains the default).
