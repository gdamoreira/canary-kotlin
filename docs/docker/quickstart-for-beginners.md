# Docker Quickstart For Beginners

> Adapted from canary/docs/docker/quickstart-for-beginners.md for the Kotlin/JVM
> engine.
> Canary-Kotlin status: adapted to the repository-root `docker-compose.yml`
> (PostgreSQL 16 + the Kotlin server built from `Dockerfile`). The upstream
> quickstart extras (MyAAC website and `opentibiabr/login-server`) are **not**
> part of this port; the native login server on `7171` is the entry point.

This guide starts a local Canary Kotlin test server with Docker. It builds the
server from this repository; no local JDK installation is required.

The stack starts:

- PostgreSQL 16 (`postgres:16-alpine`)
- Canary Kotlin game server (multi-stage build on `gradle:9.8.1-jdk25` and
  `eclipse-temurin:25-jre-alpine`)

## Difference From The Upstream C++ Quickstart

The upstream Canary quickstart also starts a MyAAC website and
`opentibiabr/login-server`, and it uses the published C++ runtime image. This
port does **not** include those extras:

- No MyAAC website, no `8080`/`8088` ports, no web login API.
- The client connects directly to the native login server on `7171`.
- The server image is built locally from the repository `Dockerfile`.

## Before You Start

Install Docker from the official Docker guide:

```text
https://docs.docker.com/get-started/get-docker/
```

After installing Docker, start Docker Desktop or the Docker service before
running any commands below.

You also need internet access on the first start so Gradle and Docker can
download dependencies, the Gradle image layer and the PostgreSQL image.

## Local Use Only

This guide is for local testing and LAN demos. Do not expose this quickstart
directly to the public Internet with the default settings.

The default setup uses simple local credentials and seeds a test account so a
new user can log in quickly. Before using it outside a trusted local network,
change `CANARY_DB_PASSWORD`, review the published ports, and review the host
firewall rules.

## Start On Your Own PC

Open a terminal in the repository root and run:

```bash
docker compose up -d --build
```

The `--build` flag compiles the server inside the image build. The first build
downloads dependencies and takes several minutes; later builds reuse Docker and
Gradle caches where available.

Check that both services are healthy:

```bash
docker compose ps
docker compose logs -f server
```

Expected server log sequence:

```text
Flyway migrations applied (V1–V21)
Loading map
Loading scripts
Starting network listeners
Server online
```

Connect a Tibia client to:

```text
Login server:  127.0.0.1:7171
Game world:    127.0.0.1:7172
```

Default test account:

```text
account:  1
password: 1
```

The Flyway seed migration creates the account and its test characters
(`Knight Hero`, `Sorcerer Master`).

## Start For Other PCs On Your Network

Use this when the client runs on another computer in the same local network.

1. Find the host machine's LAN IPv4 address (for example `192.168.1.50`).
2. Start the stack with `CANARY_IP` set to that address:

   ```bash
   CANARY_IP=192.168.1.50 docker compose up -d --build
   ```

3. On the other PC, connect the client to `192.168.1.50:7171` (login) and let
   the server direct the game connection to `192.168.1.50:7172`.

The character list returns the world address from `CANARY_IP`; if it is
`127.0.0.1`, other PCs can log in but cannot enter the game world. Make sure the
host firewall allows TCP `7171` and `7172`.

## Configuration

All public configuration is environment-driven (`CANARY_*`). Common values:

| Variable | Default in `docker-compose.yml` | Purpose |
| --- | --- | --- |
| `CANARY_DB_HOST` | `db` | PostgreSQL host inside the Compose network |
| `CANARY_DB_PORT` | `5432` | PostgreSQL port |
| `CANARY_DB_NAME` | `canary` | Database name |
| `CANARY_DB_USER` | `canary` | Database user |
| `CANARY_DB_PASSWORD` | `canary` | Database password |
| `CANARY_SERVER_NAME` | `Canary Kotlin Server` | Server name |
| `CANARY_IP` | `127.0.0.1` | Address returned in the character list |
| `CANARY_LOGIN_PORT` | `7171` | Login listener (host mapping and container) |
| `CANARY_GAME_PORT` | `7172` | Game world listener |
| `CANARY_STATUS_PORT` | `7171` | Status listener |

Set them in the shell before `docker compose up`, or create a `.env` file next
to `docker-compose.yml` (Compose reads it automatically).

## Useful Commands

Show running services:

```bash
docker compose ps
```

Watch server logs:

```bash
docker compose logs -f server
```

Watch database logs:

```bash
docker compose logs -f db
```

Stop the stack without deleting data:

```bash
docker compose down
```

Delete the database and persisted server data:

```bash
docker compose down -v
```

Open a PostgreSQL shell:

```bash
docker compose exec db psql -U canary -d canary
```

## Troubleshooting

If the script says Docker was not found, install Docker and restart your
terminal.

If the script says Docker is not running, start Docker Desktop or the Docker
service and try again.

If the first start is slow, wait for the Gradle dependency download and the
server build to finish. Later starts are faster because Docker and Gradle reuse
cached layers and dependencies.

If another PC can see the character list but cannot enter the game, check
`CANARY_IP`. It must be the LAN address reachable from the other PC, not
`127.0.0.1`.

If another PC cannot connect at all, check the firewall on the host machine.
The quickstart uses these TCP ports:

- `7171` (login and status)
- `7172` (game world)

If the server container exits immediately, inspect `docker compose logs server`.
Common causes are a database health-check timeout and a failed Flyway
migration; the log identifies the migration file.

If the build fails with a JDK or Gradle error, confirm the `Dockerfile` build
stage (`gradle:9.8.1-jdk25`) was used; the runtime stage alone cannot compile
the server.
