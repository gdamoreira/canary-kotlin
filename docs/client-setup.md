# Local Client Setup — OTClient Redemption

How to run the full stack locally and log in with a real client. **MyAAC is
optional** (website/admin only); the **login-server webservice is the client's
login path** for modern clients. This guide matches the current server profile
(protocol **1525**, `CLIENT_VERSION`).

---

## 1. What each piece is for

| Piece | Required to play? | Role |
| --- | --- | --- |
| Kotlin game server (`server`) | **Yes** | Game world + native status; hosts the game protocol |
| `opentibiabr/login-server` (port 8088) | **Yes, for modern clients** | Headless HTTP JSON login: authenticates the client and returns the character list + session |
| MariaDB (`mariadb`) | **Yes, with login-server** | login-server is MySQL-only; the server runs on it in single-database mode |
| MyAAC (port 8080) | No | Website/admin (registration, highscores, guilds); it also has login endpoints, but they are not needed when login-server runs |
| OTClient Redemption | Yes | The client (`../otclient`) |

> The port's native login on `7171` serves the status protocol and the classic
> direct login flow; modern clients (15.x) use the **login-server webservice**.

---

## 2. Server stack (Docker, recommended)

Single-database MariaDB mode, so server + login-server share one database:

```bash
cp docker/.env.dist .env            # optional; defaults work
docker compose -f docker-compose.yml -f docker/docker-compose.mariadb.yml up -d --build
```

- Game server: `127.0.0.1:7172` (login/status `7171`)
- Login webservice: `http://127.0.0.1:8088/login` (gRPC `9090`)
- MyAAC (optional): `http://localhost:8080`
- The `db` (Postgres) service still starts (Compose cannot delete it) — harmless.

**Test accounts:** Flyway `V2` seeds account **`1` / password `1`** with
`Knight Hero` and `Sorcerer Master` on both engines. The extra dumps under
`docker/data/` are PostgreSQL-format; in MariaDB mode seed with the upstream
MariaDB dumps (`../canary/docker/data/*.sql`) if you want the 100+ accounts.

### Split mode (optional)

`docker/docker-compose.split.yml` runs `login` and `game` as separate services
(`CANARY_ROLE=login|game`) — see `docker/DOCKER.md`.

### Native (no Docker, advanced)

- MariaDB locally (e.g. Homebrew) + `CANARY_DB_ENGINE=mariadb ./gradlew run`
- login-server natively (Go build) or in a container, pointed at the same
  MariaDB (`MYSQL_HOST/PORT/DBNAME/USER/PASS`).

---

## 3. Client — OTClient Redemption

Clone (already done on this machine at `../otclient`):

```bash
git clone --depth 1 https://github.com/opentibiabr/otclient.git ../otclient
```

### Build (macOS)

Requires Xcode command line tools, CMake, Ninja and [vcpkg](https://github.com/microsoft/vcpkg):

```bash
cd ../otclient
git clone https://github.com/microsoft/vcpkg ~/vcpkg && ~/vcpkg/bootstrap-vcpkg.sh
export VCPKG_ROOT=~/vcpkg
cmake --preset macos-release        # Apple Silicon
# Intel Macs: the preset pins the arm64-osx triplet — override it:
# cmake --preset macos-release -DVCPKG_TARGET_TRIPLET=x64-osx -DVCPKG_HOST_TRIPLET=x64-osx
cmake --build --preset macos-release
```

Upstream ships build guides for Debian/Ubuntu/Windows/WSL under
`../otclient/docs/building/`; there is no upstream macOS guide, so the preset
path above is the supported route (with the Intel triplet override).

### Configuration (already applied in `../otclient/init.lua`)

The client auto-selects a single configured server:

```lua
Servers_init = {
    ["http://127.0.0.1:8088/login"] = {
        port = 8088,          -- login-server HTTP port
        protocol = 1525,      -- matches the server's CLIENT_VERSION
        httpLogin = true,     -- webservice login (login-server)
        useAuthenticator = false
    }
}
```

The RSA key is already compatible: the client's `OTSERV_RSA`
(`modules/gamelib/const.lua`) equals the server's default modulus
(`DEFAULT_P * DEFAULT_Q`) — verified.

### First run

1. Start the client (the built binary under `../otclient/build/macos-release/`).
2. On first launch it downloads the Tibia assets (`clientAssets`, from
   `dudantas/tibia-client`) — needs internet access.
3. Enter account **`1`**, password **`1`**, pick `Knight Hero` or
   `Sorcerer Master`, and enter the world.

---

## 4. Troubleshooting

| Symptom | Fix |
| --- | --- |
| Client hangs at "Connecting to login server" | Check `docker compose logs login-server`; confirm `http://127.0.0.1:8088/login` responds and MariaDB is healthy. |
| "Wrong client version" / disconnect after login | The client must send protocol `1525`; the `Servers_init` entry pins it — remove other entries so the single-entry auto-select applies. |
| Character list empty | The account has no characters on this world; log in with account `1` (seeded) or create characters via the DB/MyAAC. |
| Game connects then drops | Server logs (`docker compose logs server`); verify the RSA match was not overridden (`CANARY_RSA_P/Q` unset). |
| Playing from another PC | Set `CANARY_IP`/`SERVER_IP` to your LAN address in `.env`; login-server advertises it in the character list. |
| Intel Mac build fails on vcpkg | Override the triplet as shown above (`x64-osx`). |

## 5. Direct (non-webservice) login

The client also supports `httpLogin = false` entries (classic direct login to
the login port). That path is **not recommended** with this port: the current
wire profile targets modern clients, and the supported flow is the
login-server webservice above.
