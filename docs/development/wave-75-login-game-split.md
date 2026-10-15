# Wave 75 Plan — Scaling Phase 1: Login/Game Protocol Split

**Status:** Completed (2026-10-15) — both workstreams merged; 820 suites / 8464 tests green
**Baseline:** `main` @ `1149cb26`; 817 suites / 8445 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/scaling-and-multi-world.md` Phase 1 (login/game service split only). No multi-world, no Redis, no zone sharding.
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Run the login protocol and the game protocol as **separately deployable
roles** of the same binary, so login instances can scale horizontally (N
behind a load balancer) while the game runs as its own instance. The session
handoff is already DB-mediated, so cross-process operation needs no new
protocol.

## 2. Design (fixed interfaces)

```kotlin
enum class ServerRole { ALL, LOGIN, GAME }   // config package

// ServerCli: --role <all|login|game> and CANARY_ROLE env (CLI > env > default ALL);
// unknown values fail fast naming the variable. ServerConfig gains `role`.

// Boot behavior:
//  - LOGIN: config -> database connect + migrate -> account/login repositories
//    -> listener binds login (CANARY_LOGIN_PORT) + status only.
//    No map, Lua, items, monsters, dispatcher, save manager.
//  - GAME:  everything boots except the login listener; game port only.
//  - ALL:   current behavior (both listeners).
```

## 3. Workstreams (parallel)

### L1 — Role core

**Scope:** `ServerRole` + CLI/env parsing + `ServerConfig.role`;
`ServerBootstrap`/`ServerBoot` role-aware boot (LOGIN skips the world boot and
the world seams; GAME skips the login listener); `CanaryServer` listener
selection by role; `Main`/`ServerLaunch` pass-through; the world block/seams
skipped for LOGIN.

**Files:** `config/ServerCli.kt`, `config/ConfigManager.kt` (ServerConfig),
`bootstrap/ServerBootstrap.kt`, `bootstrap/ServerLaunch.kt`/`Main.kt`,
`network/CanaryServer.kt`.

**Tests:** role parsing (aliases + fail-fast); login-only boot binds
login/status and does not boot the dispatcher/map (world seams absent);
game-only boot binds the game port and no login listener; ALL unchanged
(existing boot tests green).

### L2 — Split delivery and docs

**Scope:** compose split example (`docker/docker-compose.split.yml`: a
`login` service with `CANARY_ROLE=login` and a `game` service with
`CANARY_ROLE=game`, sharing `db`/`mariadb`; document the LB expectation);
`docker/.env.dist` (`CANARY_ROLE=all`); `DOCKER.md` section; scaling document
Phase 1 marked delivered with the operational notes (rate-limit store
follow-up); README note. Validate YAML (ruby) and keep CI compose validation
passing (add the new file to `.github/workflows/docker.yml` validation list).

**Files:** `docker/**`, `docs/development/scaling-and-multi-world.md`,
`README.md`, `.github/workflows/docker.yml`.

**Tests:** none (docs/compose); validate both YAMLs and the workflow.

## 4. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch; CI
Docker workflow validates the new compose file.

## 5. Documentation gate (AGENTS.md §6)

Update `implementation-status.md`, `README.md`, `CHANGELOG.md` (Phase 79), and
this document in place.

## 6. Out of scope

Multi-world (Phase 2), Redis coordination, zone sharding (Phase 3);
shared rate-limit store for multi-login-instance deployments (documented
follow-up).

---

## Completion (2026-10-15)

role-core (`91316125`) and split-delivery-docs (`ceadfc2e`) merged; the login
advertisement fix (configured world name/IP/game port) was applied at
integration. Integrated suite: **820 suites / 8464 tests, 0 failures, zero
Kotlin warnings**; ancestry verified. Phase 1 of the scaling program is
delivered; phases 2–3 remain deferred by decision.
