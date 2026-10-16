# Docker Deployment Guide — Canary Kotlin Edition

This guide covers the containerized stack for the Kotlin server. It is adapted
from `canary/docker/DOCKER.md`; the main differences are dual database support
(PostgreSQL 16 by default, MariaDB 11.4 optional) and the native login server
alongside the upstream `opentibiabr/login-server` webservice.

The canonical compose file lives in this folder (`docker/docker-compose.yml`,
the upstream Canary layout); the repository-root `docker-compose.yml` includes
it so `docker compose up` also works from the root.

For a beginner walkthrough see
[`docs/docker/quickstart-for-beginners.md`](../docs/docker/quickstart-for-beginners.md).
To connect a real client locally (OTClient Redemption + login-server; MyAAC is
optional), see [`docs/client-setup.md`](../docs/client-setup.md).

---

## Folder layout

| Path | Purpose |
| --- | --- |
| `docker-compose.yml` | Canonical stack: PostgreSQL 16 + server (built from the root `Dockerfile`). |
| `docker-compose.mariadb.yml` | Override: run the whole stack on MariaDB (see [Database engines](#database-engines)). |
| `docker-compose.split.yml` | Override: run the login and game roles as separate services (see [Split login/game mode](#split-logingame-mode-scaling-phase-1)). |
| `.env.dist` | Environment template; copy to `.env` at the repository root. |
| `DOCKER.md` | This guide. |
| `config.sh` | Substitutes `CANARY_*` values into `config.lua` and `.env`. |
| `data/` | Adapted test-account SQL dumps (PostgreSQL) + `start.sh` helper. |
| `up.sh` / `up.ps1` | Guarded start scripts (never remove database volumes). |
| `Dockerfile.dev` | Development image (JDK 25 + Gradle wrapper, sources mounted). |
| `Dockerfile.x86` / `Dockerfile.arm` | Platform-pinned server images. |
| `quickstart/myaac/` | Upstream MyAAC reference files (NOT wired; see below). |
| `.dockerignore` | Context exclusions for builds using this folder as context. |

The server image itself is built from the **repository-root `Dockerfile`**
(Gradle 9.8.1 + JDK 25 → Temurin 25 JRE).

---

## Stack

| Service | Image / build | Purpose | Ports |
| --- | --- | --- | --- |
| `db` | `postgres:16-alpine` | Game database in the default mode (Flyway migrates on boot) | `${CANARY_DB_PORT:-5432}` |
| `server` | root `Dockerfile` | Login/status + game world (`CANARY_DB_ENGINE` selects the engine) | `7171`, `7172`, metrics `9464` |
| `login` / `game` | root `Dockerfile` | Split-stack roles (`CANARY_ROLE`): login/status, or game world only | `7171` / `7172`, metrics `9464` (game) |
| `mariadb` | `mariadb:11.4` | Database for MyAAC/login-server; in MariaDB single-DB mode also the game database | internal |
| `myaac` | `quickstart/myaac/Dockerfile` | Upstream website/admin (MyAAC 2.x, reads `mariadb`) | `${MYAAC_HTTP_PORT:-8080}` |
| `login-server` | `opentibiabr/login-server` | Upstream client login webservice (reads `mariadb`) | `8088`, `9090` |

Game data is bind-mounted into the server container:

- `../data` → `/app/data` (engine Lua core, items, XML) — read-only
- `../data-canary` → `/app/data-canary` (Canary datapack) — read-only
- `../data-otservbr-global` → `/app/data-otservbr-global` (global datapack) — writable

The stack runs the full **`data-otservbr-global`** datapack by default
(`CANARY_DATA_DIR`), with `CANARY_MAP_NAME=otservbr`. The main world map
(`otservbr.otbm`, ~185 MB) is not committed; the server fetches it on boot via
`CANARY_MAP_DOWNLOAD_URL` (`MapDownload`) into the writable mount above.
Ensure outbound network access, or pre-place the file at
`data-otservbr-global/world/otservbr.otbm`.

The global world is much larger than the Canary test map: give Docker Desktop
at least **16 GB of memory** (Settings → Resources) — the server requests
`-Xmx12g` by default (`CANARY_JAVA_OPTS` overrides it). A smaller heap boots
the Canary datapack instead (`CANARY_DATA_DIR=data-canary`).

---

## Database engines

The server supports two relational engines, selected with `CANARY_DB_ENGINE`:

| Engine | Value | Server connection | Flyway tree |
| --- | --- | --- | --- |
| PostgreSQL 16+ (default) | `postgres` / `postgresql` | `jdbc:postgresql://<host>:5432/<db>` | `db/migration/postgres` |
| MariaDB 11.4+ | `maria` / `mariadb` / `mysql` | `jdbc:mariadb://<host>:3306/<db>` | `db/migration/mariadb` |

An unknown value fails the boot naming the variable. H2 in PostgreSQL mode
stays the test engine and is not used by the Docker stack.

### PostgreSQL (default)

`docker compose up -d --build` runs the `db` service; the server reads it
(`CANARY_DB_HOST=db`, port 5432). MyAAC and login-server read the separate
`mariadb` service (they only speak MySQL).

### MariaDB single-database mode

`docker/docker-compose.mariadb.yml` is an override that points the server at
the `mariadb` service, so the game server, MyAAC and login-server share one
database. Run it from the repository root:

```bash
docker compose -f docker-compose.yml -f docker/docker-compose.mariadb.yml up -d --build
```

Equivalent without the root include wrapper:

```bash
docker compose -f docker/docker-compose.yml -f docker/docker-compose.mariadb.yml up -d --build
```

What changes in this mode:

- The server runs with `CANARY_DB_ENGINE=mariadb` against `mariadb:3306` and
  migrates through the MariaDB Flyway tree (`db/migration/mariadb/V1..V22`)
  with MariaDB-native SQL (`BIGINT AUTO_INCREMENT`,
  `INSERT ... ON DUPLICATE KEY UPDATE`).
- MyAAC and login-server keep reading `mariadb` — now the game database
  itself, so the website and webservice show the real accounts and characters
  created in-game.
- The override pins `CANARY_DB_ENGINE`/`CANARY_DB_HOST`/`CANARY_DB_PORT` to
  the MariaDB literals, so the matching `.env` values do not apply in this
  mode (database name/user/password still do).
- The PostgreSQL `db` service stays in the merged model (Compose overrides
  cannot delete services) and `up` still starts it; the server no longer uses
  it. Stop it with the same `-f` pair plus `stop db` if you want it down.
- The split stack's `login`/`game` roles inherit the same pins through this
  override (the entries are gated behind the `split` profile); see
  [Split login/game mode](#split-logingame-mode-scaling-phase-1).
- Data is not migrated between engines: `db-volume` and `mariadb-volume` are
  separate; each engine keeps its own Flyway history and seed.

Switch back with the plain `docker compose up -d --build` and set
`CANARY_DB_ENGINE` to `postgres` (or remove it) in `.env`.

---

## Split login/game mode (scaling phase 1)

The server can run the login protocol and the game world as separate roles of
the same image: `CANARY_ROLE=login` binds the login/status listeners only,
`CANARY_ROLE=game` boots the world and binds the game listener only, and the
default `all` keeps today's single process. The session handoff is
DB-mediated, so the roles only need to share one database.

`docker/docker-compose.split.yml` provides the two role services on top of the
base stack. Run it from the repository root, naming the services explicitly:

```bash
docker compose -f docker-compose.yml -f docker/docker-compose.split.yml up -d --build login game
```

What changes in this mode:

- `login` publishes `${CANARY_LOGIN_PORT:-7171}` (login + status) and `game`
  publishes `${CANARY_GAME_PORT:-7172}` (game world) plus the optional
  Prometheus exporter on `9464`. The game role is the metrics owner; login
  replicas cannot share one fixed host metrics port.
- Both roles share the same database (`db` by default; `mariadb` when combined
  with the MariaDB override) and the read-only game-data mounts.
- Compose overrides cannot delete the base `server` service, so it stays in
  the merged model: always name `login game` on `up`, otherwise the `server`
  container starts too and collides on the host ports. Use either the base
  `server` or the split pair, never both — stop one before starting the other
  (`docker compose stop server` / `docker compose stop login game`).
- The `mariadb` override carries its database pins to both roles, so the
  three-file combination works unchanged:

```bash
docker compose -f docker-compose.yml -f docker/docker-compose.split.yml -f docker/docker-compose.mariadb.yml up -d --build login game
```

In the MariaDB combination, stop the roles by naming them (the override gates
them behind the `split` profile): `... down login game`, or add
`--profile split` to `down`.

### Scaling the login role behind a TCP load balancer

Login instances are connection-stateless (the RSA/XTEA handshake state lives
in the connection) and scale by replica count (`--scale login=N`). In
production, put an L4 (TCP) load balancer in front of the login role: it
terminates nothing, passes the raw TCP stream through to the replicas on the
compose network — reachable at the `login` service name (`login:7171`) — and
health-checks the port.

The published host port in the shipped file covers the single-instance case:
Docker cannot bind one host port for several replicas, so a scaled deployment
drops the `login` port publication and lets the L4 load balancer own the
public port. The game role is one world per instance: run a single `game`
service and scale by adding worlds, not by cloning it.

**Follow-up (not implemented yet):** with more than one login instance, per-IP
rate limiting and account-lockout counters remain per-process, so each
instance enforces its own limits. They need a shared store (Redis or a
database table) before multi-instance login is exposed to untrusted traffic —
tracked as the closing item of scaling Phase 1
([`docs/development/scaling-and-multi-world.md`](../docs/development/scaling-and-multi-world.md)).

---

## Quick start

```bash
cp docker/.env.dist .env          # optional; defaults work out of the box
docker compose up -d --build
docker compose ps
docker compose logs -f server
```

Guarded helpers (start the stack without touching database volumes):

```bash
sh docker/up.sh          # Linux / macOS / WSL
```

```powershell
.\docker\up.ps1          # Windows PowerShell
```

Useful knobs: `NO_BUILD=true`, `SKIP_CLEANUP=true`, `CLEANUP_UNTIL=168h`,
`LAN=true` (advertise the detected LAN IP in `.env`).

Stop and clean up:

```bash
docker compose down            # keep volumes
docker compose down -v         # also remove the database volume
```

---

## Environment variables

All variables use the `CANARY_*` contract. See [`docker/.env.dist`](.env.dist)
for the full list with defaults.

| Variable | Default | Notes |
| --- | --- | --- |
| `CANARY_DB_ENGINE` | `postgres` | `postgres`/`postgresql` or `maria`/`mariadb`/`mysql`; MariaDB also needs the compose override (see [Database engines](#database-engines)). |
| `CANARY_DB_NAME` / `CANARY_DB_USER` / `CANARY_DB_PASSWORD` | `canary` / `canary` / `canary` | Applied to the Postgres and MariaDB containers and to all services. |
| `CANARY_DB_PORT` | `5432` | PostgreSQL host port published on the host; the ports inside the network are fixed by the compose files (5432 / 3306). |
| `CANARY_SERVER_NAME` | `Canary Kotlin Server` | Shown in the status XML. |
| `CANARY_IP` | `127.0.0.1` | Public IP advertised to clients. |
| `CANARY_LOGIN_PORT` / `CANARY_GAME_PORT` | `7171` / `7172` | Must match the client. |
| `CANARY_STATUS_PORT` | `7171` | Status shares the login port in this port. |
| `CANARY_ROLE` | `all` | `all` / `login` / `game`; the base stack runs `all`, the [split override](#split-logingame-mode-scaling-phase-1) pins the role per service. |
| `CANARY_RSA_P` / `CANARY_RSA_Q` | built-in pair | Optional RSA override; both or neither. |
| `CANARY_METRICS_ENABLE_PROMETHEUS` | `false` | Enables the `/metrics` exporter. |
| `CANARY_METRICS_PROMETHEUS_ADDRESS` | `0.0.0.0:9464` | Exporter bind address. |
| `CANARY_METRICS_PORT` | `9464` | Host port published for the exporter. |

---

## Configuration files

Two mechanisms exist, matching upstream:

1. **Environment (`CANARY_*`)** — always applied; the compose stack uses this.
2. **`config.lua`** — copy `config.lua.dist` to `config.lua` and start the
   server with `--config config.lua` (or place it next to the executable; the
   installed distribution resolves it automatically). The same key surface as
   upstream Canary is read (`serverName`, `loginProtocolPort`,
   `dataPackDirectory`, `OTCRFeatures`, ...).

`docker/config.sh` rewrites a `config.lua` from `.env` values:

```bash
sh docker/config.sh                      # .env -> config.lua (repo root)
sh docker/config.sh serverName MyServer  # ad-hoc substitutions
```

---

## Test accounts

Two layers, mirroring upstream:

1. **Flyway seed (automatic)** — migration `V2` creates account `1` / password
   `1` with the characters `Knight Hero` and `Sorcerer Master`.
2. **Quickstart dumps (optional)** — `docker/data/01-test_account.sql` and
   `02-test_account_players.sql` are the upstream MariaDB dumps adapted to
   PostgreSQL (16 accounts, 178 characters; every account password is `1`).
   Apply them after the server's first boot (schema created by Flyway):

```bash
sh docker/data/start.sh                  # waits for the DB/schema, then imports
sh docker/data/start.sh --write-config   # also writes config.lua from .env
```

Or manually:

```bash
docker compose exec -T db psql -U canary -d canary < docker/data/01-test_account.sql
docker compose exec -T db psql -U canary -d canary < docker/data/02-test_account_players.sql
```

The dumps are idempotent (`ON CONFLICT DO NOTHING`) and keep the BIGSERIAL
sequences ahead of the seeded ids. Change or remove the accounts before
exposing the server to any untrusted network.

Both the dumps and `docker/data/start.sh` target the PostgreSQL `db` service,
so they do not apply to MariaDB single-DB mode: there only the Flyway seed
(account `1` / password `1`) is created automatically; import upstream MariaDB
dumps into the `mariadb` service if you want the full test dataset.

---

## Image variants

| File | Build | Use |
| --- | --- | --- |
| `Dockerfile` (root) | `docker build -t canary-kotlin .` | Default multi-stage image (also used by compose). |
| `docker/Dockerfile.x86` | `docker build -f docker/Dockerfile.x86 -t canary-kotlin:x86 .` | Pinned `linux/amd64`. |
| `docker/Dockerfile.arm` | `docker build -f docker/Dockerfile.arm -t canary-kotlin:arm .` | Pinned `linux/arm64`. |
| `docker/Dockerfile.dev` | `docker build -f docker/Dockerfile.dev -t canary-kotlin-dev .` | JDK 25 + Gradle wrapper; mount the repo and run `./gradlew run`. |

---

## Metrics

The Prometheus exporter is optional; enable it with
`CANARY_METRICS_ENABLE_PROMETHEUS=true` and scrape
`http://<host>:9464/metrics`. A full Prometheus + Grafana stack lives in
[`../metrics/`](../metrics/README.md).

---

## Upstream quickstart components (ported, MySQL-backed)

Upstream Canary's quickstart ships two components that are **MySQL-only**; this
stack includes them exactly like upstream, backed by its own `mariadb` service:

- **MyAAC website/admin** (PHP, `pdo_mysql`) — builds from
  `quickstart/myaac/Dockerfile` (upstream files) and reads the `mariadb`
  service. Default admin credentials come from the `MYAAC_*` variables; the
  site is served at `http://localhost:${MYAAC_HTTP_PORT:-8080}`.
- **`opentibiabr/login-server`** — the upstream client login webservice for
  modern clients, exposed at `${LOGIN_HTTP_PORT:-8088}` (HTTP) and
  `${LOGIN_GRPC_PORT:-9090}` (gRPC), reading the `mariadb` service.

**Database scope:** by default the Kotlin game server reads the **PostgreSQL**
`db` service (Flyway schema V1–V22), while MyAAC and login-server read the
separate `mariadb` service — seed it with game data (upstream MariaDB dumps or
an upstream C++ server) if you want the website and webservice to show real
accounts. In **MariaDB single-DB mode** (see
[Database engines](#database-engines)) all three read the same `mariadb` game
database, so in-game accounts and characters appear in MyAAC/login-server
without extra seeding. The Kotlin server's native login remains on
`CANARY_LOGIN_PORT` (7171).

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Server exits during boot | `docker compose logs server` — most often the database was still starting; the compose healthcheck handles this, retry with `docker compose up -d`. |
| `address already in use` | Another server/container holds `7171`/`7172`/`9464`; stop it or override the port variables in `.env`. |
| Split mode starts `server` too (port clash) | Name the role services (`up ... login game`); a bare `up` also starts the base `server` because overrides cannot delete it. See [Split login/game mode](#split-logingame-mode-scaling-phase-1). |
| Scaled `login` replicas fail with `port is already allocated` | Replicas cannot share one published host port; let the L4 load balancer reach `login:7171` on the compose network instead of publishing the port. |
| Client cannot reach the game world | `CANARY_IP` must be the address the client dials; for LAN play run `LAN=true sh docker/up.sh` or set it manually. |
| Map download fails | Allow outbound HTTPS or provide a local map; see the log line from `MapDownload`. |
| Rebuild after code changes | `docker compose build server && docker compose up -d server`. |
| Stale database schema | Flyway migrates automatically on boot; never edit applied migrations (see `AGENTS.md` §2.1). |
| Metrics endpoint unreachable | Set `CANARY_METRICS_ENABLE_PROMETHEUS=true` and restart; the exporter only binds when enabled. |
| MyAAC/login-server show no accounts | Default mode: they read the separate `mariadb` service, not the Kotlin PostgreSQL game DB — seed it, or run MariaDB single-DB mode so they read the game database (see above). |
| `Invalid CANARY_DB_ENGINE value` on boot | Use `postgres`/`postgresql` or `maria`/`mariadb`/`mysql`; for the Docker stack use the MariaDB override file instead of setting only the variable. |
| MyAAC build takes long the first time | It builds MyAAC 2.x from source (PHP extensions + composer); subsequent builds use the layer cache. |
