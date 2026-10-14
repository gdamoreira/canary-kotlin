# Docker Deployment Guide — Canary Kotlin Edition

This guide covers the containerized stack for the Kotlin server. The compose
file lives at the **repository root** (`docker-compose.yml`) because it builds
the server from the root `Dockerfile`; the guides and helpers live in this
`docker/` directory.

For a beginner walkthrough see
[`docs/docker/quickstart-for-beginners.md`](../docs/docker/quickstart-for-beginners.md).

> The upstream C++ quickstart extras (MyAAC website, `opentibiabr/login-server`)
> are **not** part of this port. The native Kotlin login/status server on port
> `7171` is the client entry point.

---

## Stack

| Service | Image / build | Purpose | Ports |
| --- | --- | --- | --- |
| `db` | `postgres:16-alpine` | Game database (Flyway migrates on boot) | `${CANARY_DB_PORT:-5432}` |
| `server` | built from root `Dockerfile` (Gradle 9.8.1 + JDK 25 → Temurin 25 JRE) | Login/status + game world | `7171`, `7172` |

Game data is bind-mounted read-only into the server container:

- `./data` → `/app/data` (engine Lua core, items, XML)
- `./data-canary` → `/app/data-canary` (Canary datapack)
- `./data-otservbr-global` → `/app/data-otservbr-global` (global datapack)

The main world map (`otservbr.otbm`) is not committed; the server fetches it
on boot via `mapDownloadUrl` (`MapDownload`). Ensure outbound network access or
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

---

## Test account

The database seed (Flyway `V2`) creates account **`1` / password `1`** with the
characters `Knight Hero` and `Sorcerer Master`. Change or remove these before
exposing the server to any untrusted network.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Server exits during boot | `docker compose logs server` — most often the database was still starting; the compose healthcheck handles this, retry with `docker compose up -d`. |
| `address already in use` | Another server/container holds `7171`/`7172`; stop it or override the port variables in `.env`. |
| Client cannot reach the game world | `CANARY_IP` must be the address the client dials; for LAN play set your host IP, not `127.0.0.1`. |
| Map download fails | Allow outbound HTTPS or provide a local map; see the log line from `MapDownload`. |
| Rebuild after code changes | `docker compose build server && docker compose up -d server`. |
| Stale database schema | Flyway migrates automatically on boot; never edit applied migrations (see `AGENTS.md` §2.1). |
