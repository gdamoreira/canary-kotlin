#!/usr/bin/env bash
# scripts/start.sh — run the local stack with default ports:
#   MariaDB (game database) + Canary Kotlin (login + game) + login-server.
#
# MyAAC is intentionally not started (website/admin only); the client logs in
# through the login-server webservice at http://127.0.0.1:8088/login.
#
# Usage:  scripts/start.sh          (from anywhere; runs from the repo root)
# Stop:   docker compose -f docker-compose.yml -f docker/docker-compose.mariadb.yml down
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
cd "$repo_root"

fail() { printf '%s\n' "$1" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "Docker was not found. Install Docker Desktop and start it first."
docker info >/dev/null 2>&1 || fail "Docker is installed but the daemon is not running."
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 was not found."

if [ ! -f .env ]; then
	cp docker/.env.dist .env
	printf '%s\n' "Created .env from docker/.env.dist."
fi

compose=(docker compose -f docker-compose.yml -f docker/docker-compose.mariadb.yml)

printf '%s\n' "Starting MariaDB + Canary Kotlin (login+game) + login-server..."
"${compose[@]}" up -d --build mariadb server login-server

printf '%s\n' "Waiting for the game server to accept connections..."
ready=false
for _ in $(seq 1 90); do
	if "${compose[@]}" exec -T server sh -c 'nc -z 127.0.0.1 7171' >/dev/null 2>&1; then
		ready=true
		break
	fi
	sleep 2
done
if [ "$ready" != "true" ]; then
	printf '%s\n' "Server did not become ready in time; check: ${compose[*]} logs server" >&2
	exit 1
fi

"${compose[@]}" ps

cat <<'EOF'

Canary Kotlin is running (MariaDB single-database mode):
  Login / status : 127.0.0.1:7171
  Game world     : 127.0.0.1:7172
  Login server   : http://127.0.0.1:8088/login   (client webservice)
  MariaDB        : internal service `mariadb` (canary / canary / canary)

Accounts (log in with the EMAIL; the login-server authenticates by email):
  @1 / 1        Knight Hero, Sorcerer Master
  @god / god    GOD (access group 6)

Client: ../otclient (init.lua already points at http://127.0.0.1:8088/login)
Logs:   docker compose -f docker-compose.yml -f docker/docker-compose.mariadb.yml logs -f server
Stop:   docker compose -f docker-compose.yml -f docker/docker-compose.mariadb.yml down
EOF
