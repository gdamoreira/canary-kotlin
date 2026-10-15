# Scaling & Multi-World Architecture — Analysis and Plan

**Status:** Analysis; Phase 1 (login/game service split) delivered — wave 75
**Baseline:** `main` @ `686169ae`; 758 suites / 8091 tests green
**Scope:** splitting LoginProtocol from GameProtocol, horizontal scaling, Redis
state sharing, and multiple game worlds per account.

---

## 1. Current architecture (facts from the codebase)

- **One JVM process** runs everything: `ServerLaunch` → `ServerBootstrap` →
  `CanaryServer` (Netty) binds login (7171), game (7172) and status (7171).
  `LoginHandler` and `GameHandler` are already **separate classes**; the login
  handler depends only on `AccountRepository` + `RsaCipher` + config — **it has
  no `Game` dependency**.
- **World state is in-memory** (`Game`, `GameMap`, `Game.guilds`, houses,
  creatures) and mutated **only** on the single-threaded `GameDispatcher`
  (`Dispatchers.Default.limitedParallelism(1)`, AGENTS.md §1.2). This
  confinement is the core invariant of the port.
- **Persistence is external**: PostgreSQL 16 (HikariCP + Flyway V1–V22), plus
  the PostgreSQL-backed KV store (`KVSQL`), `SaveManager` rotation and monster
  compute service. Nothing about durability ties state to the process.
- **Login flow**: account auth → character list → session key (DB) → game
  server validates. The handoff is already DB-mediated, i.e. cross-process.
- **Single world per process**: no `worlds` table; `players` has no
  `world_id`; `dataPackDirectory` selects the datapack/world.

**Consequence:** the code is *structurally* ready to split into services
(DI everywhere, no singletons, external state) — the missing pieces are the
entrypoint split, a world registry, and world-scoped queries.

---

## 2. Scenario 1 — Login and Game as separate, scalable services

### 2.1 Login service (easy, do first)

- **Extract** a `LoginService` entrypoint: `LoginHandler` + `AccountRepository`
  + status handler + config/RSA. No world, no map, no dispatcher.
  *(Delivered as `CANARY_ROLE=login`; see §2.4.)*
- **Horizontal scale:** N instances behind a TCP load balancer (L4). The
  per-connection state (RSA/XTEA handshake) is connection-local; the session
  key is persisted in Postgres → any game instance can validate it.
- **What must move to shared storage when N > 1:** per-IP rate limiting and
  account lockout counters (Redis or a DB table), otherwise each instance
  enforces its own limits.
- **Seam:** `CanaryServer` already composes both handlers in one place — split
  the listener composition by role (`LoginServer` / `GameServer`); config gains
  a role key.

### 2.2 Game service — what horizontal scaling means here

A single world is single-threaded **by design**; cloning one world across
instances requires distributed ownership. The realistic ladder:

**A. One world per instance (recommended).** Scale by running *more worlds*,
not by cloning one world. Each instance owns one world (its map, creatures,
guilds) with its own Postgres schema/database or `world_id` partition. Login
routes the player to the chosen world's address. This is how OT fleets scale,
and it aligns exactly with Scenario 2.

**B. Split the non-world services (cheap wins).** Things that do not need
world confinement can become separate services against the same DB:
- market/auctions (already DB-backed; could be its own service),
- highscores/read models (read replicas),
- status/world-list aggregation,
- global chat via Redis pub/sub.

**C. Single-world horizontal scale (zone sharding — large program, not now).**
Partition the map into zones; each instance owns a set of zones; players and
creatures are owned by exactly one instance at a time. Requires:
- an **ownership registry with leases** (Redis) for player/creature location,
- a **message bus** for cross-zone events (Redis streams/pub-sub or Kafka),
- a **gateway** that routes client packets by owned zone (or handoffs at
  borders), distributed spectators, cross-border combat, guild/party spanning
  zones, houses, raids and global events.
The §1.2 confinement invariant becomes "single writer per entity" with
distributed ownership — a multi-month architecture program. For typical OT
populations (<2k players/world) a single JVM instance handles a world fine;
revisit only with evidence that one world exceeds a single box.

**D. Redis as the world store (not recommended).** Putting tiles/creatures in
Redis means a network round-trip per tick, no atomic multi-entity transactions,
and cache-coherence work that the single-writer model avoids. Redis is the
right tool for **coordination**, not world state:
- session/routing registry (which player is on which instance),
- presence/heartbeats, rate limits, distributed locks/leases,
- cross-instance pub/sub (chat, world events).
World durability stays in PostgreSQL (already) via `SaveManager` snapshots and
map saves.

### 2.3 Recommended roadmap (Scenario 1)

| Phase | Deliverable | Effort |
|---|---|---|
| 1 | Split binaries: `login-service` + `game-service` (shared Postgres, LB for login, shared rate-limit store) — **delivered** (wave 75) as one binary with `all`/`login`/`game` roles; shared rate-limit store still open | Low–medium |
| 2 | Multi-world fleet (see Scenario 2B): one game instance per world + world registry + routing | Medium |
| 3 | (Only if evidence demands) zone-sharding spike: ownership model, bus, gateway | Large |

### 2.4 Phase 1 delivered (wave 75)

The role split shipped as **one binary, three roles** (not two binaries):
`--role <all|login|game>` / `CANARY_ROLE` (CLI > env > default `all`), with an
unknown value failing fast at boot.

- **`login`** — config, database connect + migrations, account/login
  repositories, then the login + status listener only. No map, Lua, items,
  monsters, dispatcher or save manager.
- **`game`** — the full world boot as before, minus the login listener; game
  port only.
- **`all`** — the previous single-process default, unchanged.

Delivery:

- `docker/docker-compose.split.yml` runs `login` and `game` as separate
  services of the same image, sharing one database (`db`, or `mariadb` via the
  MariaDB override) and the game-data mounts:
  `docker compose -f docker-compose.yml -f docker/docker-compose.split.yml up -d --build login game`.
- The login role scales horizontally (`--scale login=N`). Instances are
  connection-stateless and sit behind a TCP (L4) load balancer; the session
  handoff is DB-mediated, so any login instance can hand a session to the game
  instance. The game role stays one world per instance (scale by adding
  worlds, Scenario 2B).
- The base `server` service keeps running `all` by default; Compose overrides
  cannot delete it, so split mode names the services explicitly
  (`... up login game`).
- Operational notes and the run commands live in `docker/DOCKER.md`
  ("Split login/game mode (scaling phase 1)").

**Open follow-up:** with more than one login instance, per-IP rate limiting and
account-lockout counters remain per-process, so each instance enforces its own
limits. Move them to a shared store (Redis or a database table) before
exposing multiple login instances to untrusted traffic.

---

## 3. Scenario 2 — Multiple game worlds per account

### 3.1 Is it possible with the current code?

Not as-is: there is **no `worlds` table, no `players.world_id`**, the 10.98
login returns the character list for the single world, and the game server
validates a session key without world scoping. But every piece needed is a
bounded change on top of the current architecture.

Client-side reality:
- **10.98 protocol (what the port implements):** one world per login endpoint;
  multi-world is normally a *client server-list* concern (classic OT model).
- **Modern clients (12+/15.x):** the world list comes from the HTTP login
  webservice (`opentibiabr/login-server` upstream) — the port does not
  implement that webservice; its native 10.98 login is the entry point.

### 3.2 What multi-world needs (code + data)

1. **Schema (Flyway V23+):** `worlds` (id, name, address, port, status),
   `players.world_id` (backfilled to the default world), unique
   `(world_id, name)`; per-world partitioning for guilds/market/houses
   (`world_id` columns or per-world schemas).
2. **Repositories:** `WorldRepository`; world-scoped character-list and login
   queries in `IOLoginData`/`AccountRepository`; session key carries the world;
   `GameHandler` rejects a session whose world differs from its own.
3. **Login routing:** choose the world at login — for 10.98, each world is its
   own login endpoint (per-instance config) or an extended handshake; for
   modern clients, the HTTP world list.
4. **Game:** one instance per world, same binary, `CANARY_WORLD_ID` config;
   shared login/account DB; per-world status reporting.
5. **Ops:** world registry + a world-list/status aggregation endpoint.

### 3.3 Alternatives (Scenario 2)

- **A. Classic OT model (cheapest, recommended first):** one world per
  deployment; the client's server list (or an AAC) presents worlds; only add a
  `worlds` registry table for tooling. Matches upstream Canary (single-world
  server).
- **B. Multi-world fleet (the real answer for "multiple worlds in my
  account"):** shared login + `world_id` partitioning + one game instance per
  world (Scenario 1 Phase 2). Medium effort; gives true horizontal scale.
- **C. Single shared world across instances:** Scenario 1 Phase 3; only for
  very large worlds.

---

## 4. Where to cut the code (concrete seams in this repo)

| Concern | Current location | Split |
|---|---|---|
| Listener composition | `network/CanaryServer.kt` | role-parameterized `LoginServer`/`GameServer` (handlers already separate) |
| Composition root | `bootstrap/ServerBootstrap.kt` | role parameter (`login` / `game`), world id |
| Config | `config/ConfigKeys.kt`, `ServerCli` | add `CANARY_ROLE`, `CANARY_WORLD_ID` (port-specific keys) |
| Login/character queries | `io/iologindata/**` | world-scoped queries + `WorldRepository` |
| KV store | `kv/**` (`KVSQL`) | already Postgres-backed → works cross-instance for market/global KV |
| Save rotation | `game/scheduling/SaveManager.kt` | per instance; unchanged |
| Coordination (rate limits, presence, routing) | — | new Redis integration (Phase 1/2 only) |

## 5. Risks and invariants

- **§1.2 confinement stays per instance.** Any cross-instance design must add
  explicit single-writer ownership (leases) before touching world state.
- **DB pressure:** per-service pools + pgbouncer when the fleet grows.
- **Session handoff:** keep it DB-mediated (already) or move to Redis for
  latency.
- **Backwards compatibility:** keep the single-process default; the role split
  is opt-in via config so tests and small deployments are unaffected.

## 6. Recommendation summary

1. **Split login/game services** (Phase 1) — high value, low risk, mostly
   mechanical (the handlers are already decoupled).
2. **Scale game via one-world-per-instance** (Scenario 2B) — the real
   horizontal scale for an OT server; pair it with the `worlds` registry and
   world-scoped schema.
3. **Do not put world state in Redis.** Use Redis for coordination only
   (routing, presence, rate limits, pub/sub).
4. **Zone sharding / shared world** — defer until one world demonstrably
   exceeds a single box; prefer vertical tuning + more worlds first.
