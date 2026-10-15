# Docker Deployment Guide — Canary Kotlin Edition

This guide covers the containerized stack for the Kotlin server. It is adapted
from `canary/docker/DOCKER.md`; the main differences are PostgreSQL 16 instead
of MariaDB and the native login server instead of `opentibiabr/login-server`.

The canonical compose file lives in this folder (`docker/docker-compose.yml`,
the upstream Canary layout); the repository-root `docker-compose.yml` includes
it so `docker compose up` also works from the root.

For a beginner walkthrough see
[`docs/docker/quickstart-for-beginners.md`](../docs/docker/quickstart-for-beginners.md).

---

## Folder layout

| Path | Purpose |
| --- | --- |
| `docker-compose.yml` | Canonical stack: PostgreSQL 16 + server (built from the root `Dockerfile`). |
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
| `db` | `postgres:16-alpine` | Game database (Flyway migrates on boot) | `${CANARY_DB_PORT:-5432}` |
| `server` | root `Dockerfile` | Login/status + game world | `7171`, `7172`, metrics `9464` |
| `mariadb` | `mariadb:11.4` | Database for the upstream components below (MySQL-only) | internal |
| `myaac` | `quickstart/myaac/Dockerfile` | Upstream website/admin (MyAAC 2.x, reads `mariadb`) | `${MYAAC_HTTP_PORT:-8080}` |
| `login-server` | `opentibiabr/login-server` | Upstream client login webservice (reads `mariadb`) | `8088`, `9090` |

Game data is bind-mounted read-only into the server container:

- `../data` → `/app/data` (engine Lua core, items, XML)
- `../data-canary` → `/app/data-canary` (Canary datapack)
- `../data-otservbr-global` → `/app/data-otservbr-global` (global datapack)

The main world map (`otservbr.otbm`) is not committed; the server fetches it on
boot via `mapDownloadUrl` (`MapDownload`). Ensure outbound network access or
place a local map file where the configuration expects it.

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
| `CANARY_DB_NAME` / `CANARY_DB_USER` / `CANARY_DB_PASSWORD` | `canary` / `canary` / `canary` | Applied to both the Postgres container and the server. |
| `CANARY_DB_PORT` | `5432` | Host port only. |
| `CANARY_SERVER_NAME` | `Canary Kotlin Server` | Shown in the status XML. |
| `CANARY_IP` | `127.0.0.1` | Public IP advertised to clients. |
| `CANARY_LOGIN_PORT` / `CANARY_GAME_PORT` | `7171` / `7172` | Must match the client. |
| `CANARY_STATUS_PORT` | `7171` | Status shares the login port in this port. |
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

**Important:** the Kotlin game server keeps its own **PostgreSQL** `db`
service (Flyway schema V1–V22); MyAAC and login-server cannot read it. They
serve upstream-C++ deployments and read the `mariadb` service, so seed that
database with the game data (upstream MariaDB dumps or an upstream C++ server)
if you want the website and webservice to show real accounts. The Kotlin
server's native login remains on `CANARY_LOGIN_PORT` (7171).

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Server exits during boot | `docker compose logs server` — most often the database was still starting; the compose healthcheck handles this, retry with `docker compose up -d`. |
| `address already in use` | Another server/container holds `7171`/`7172`/`9464`; stop it or override the port variables in `.env`. |
| Client cannot reach the game world | `CANARY_IP` must be the address the client dials; for LAN play run `LAN=true sh docker/up.sh` or set it manually. |
| Map download fails | Allow outbound HTTPS or provide a local map; see the log line from `MapDownload`. |
| Rebuild after code changes | `docker compose build server && docker compose up -d server`. |
| Stale database schema | Flyway migrates automatically on boot; never edit applied migrations (see `AGENTS.md` §2.1). |
| Metrics endpoint unreachable | Set `CANARY_METRICS_ENABLE_PROMETHEUS=true` and restart; the exporter only binds when enabled. |
| MyAAC/login-server show no accounts | They read the `mariadb` service, not the Kotlin PostgreSQL game DB — seed `mariadb` with your game data (see above). |
| MyAAC build takes long the first time | It builds MyAAC 2.x from source (PHP extensions + composer); subsequent builds use the layer cache. |
