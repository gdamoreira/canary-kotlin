#!/usr/bin/env bash
# docker/data/start.sh — apply the quickstart test accounts to the running
# PostgreSQL stack. Adapted from canary/docker/data/start.sh (which generated a
# MySQL config.lua and imported the MariaDB dumps inside the C++ image).
#
# The Kotlin server creates its schema through Flyway on boot, so this helper
# only waits for the database and imports the adapted test-account dumps:
#
#   CANARY_TEST_ACCOUNTS=true sh docker/data/start.sh
#
# Run it from the repository root after `docker compose up -d`. Pass
# --write-config to also generate a config.lua from the current environment
# (the server reads CANARY_* directly; the file is optional).
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(cd "$script_dir/../.." && pwd)
cd "$repo_root"

if [ -f .env ]; then
	# shellcheck disable=SC1091
	set -a && . ./.env && set +a
fi

CANARY_DB_HOST="${CANARY_DB_HOST:-db}"
CANARY_DB_PORT="${CANARY_DB_PORT:-5432}"
CANARY_DB_USER="${CANARY_DB_USER:-canary}"
CANARY_DB_PASSWORD="${CANARY_DB_PASSWORD:-canary}"
CANARY_DB_NAME="${CANARY_DB_NAME:-canary}"
CANARY_TEST_ACCOUNTS="${CANARY_TEST_ACCOUNTS:-true}"
CANARY_SERVER_NAME="${CANARY_SERVER_NAME:-Canary Kotlin Server}"
CANARY_IP="${CANARY_IP:-127.0.0.1}"
CANARY_LOGIN_PORT="${CANARY_LOGIN_PORT:-7171}"
CANARY_GAME_PORT="${CANARY_GAME_PORT:-7172}"
CANARY_STATUS_PORT="${CANARY_STATUS_PORT:-7171}"

write_config=false
for arg in "$@"; do
	case "$arg" in
		--write-config) write_config=true ;;
		*) echo "Unknown argument: $arg" >&2; exit 1 ;;
	esac
done

require_uint() {
	local name="$1"
	local value="$2"
	if [[ ! "$value" =~ ^[0-9]+$ ]]; then
		echo "Invalid ${name}: '${value}'. Use only unsigned integer values." >&2
		exit 1
	fi
}

require_uint "CANARY_DB_PORT" "$CANARY_DB_PORT"
require_uint "CANARY_LOGIN_PORT" "$CANARY_LOGIN_PORT"
require_uint "CANARY_GAME_PORT" "$CANARY_GAME_PORT"
require_uint "CANARY_STATUS_PORT" "$CANARY_STATUS_PORT"

if ! command -v docker >/dev/null 2>&1; then
	echo "Docker is required (the helper talks to the compose database container)." >&2
	exit 1
fi

psql_cmd() {
	docker compose exec -T db env PGPASSWORD="$CANARY_DB_PASSWORD" \
		psql -v ON_ERROR_STOP=1 -U "$CANARY_DB_USER" -d "$CANARY_DB_NAME" "$@"
}

echo "===== Waiting for PostgreSQL ====="
for _ in $(seq 1 60); do
	if docker compose exec -T db pg_isready -U "$CANARY_DB_USER" -d "$CANARY_DB_NAME" >/dev/null 2>&1; then
		break
	fi
	echo "DB offline, trying again"
	sleep 2
done

echo "===== Waiting for the Flyway schema ====="
for _ in $(seq 1 120); do
	if psql_cmd -c 'SELECT 1 FROM players LIMIT 1;' >/dev/null 2>&1; then
		break
	fi
	echo "Schema not ready yet, trying again"
	sleep 2
done

if [ "$write_config" = true ]; then
	echo "===== Writing config.lua ====="
	cat > config.lua <<EOF
serverName = "$CANARY_SERVER_NAME"
ip = "$CANARY_IP"
loginProtocolPort = ${CANARY_LOGIN_PORT}
gameProtocolPort = ${CANARY_GAME_PORT}
statusProtocolPort = ${CANARY_STATUS_PORT}
dataPackDirectory = "data-otservbr-global"
mysqlHost = "$CANARY_DB_HOST"
mysqlPort = ${CANARY_DB_PORT}
mysqlUser = "$CANARY_DB_USER"
mysqlPass = "$CANARY_DB_PASSWORD"
mysqlDatabase = "$CANARY_DB_NAME"
EOF
	echo "Wrote config.lua (pass --config config.lua to use it)"
fi

if [ "$CANARY_TEST_ACCOUNTS" != "true" ]; then
	echo "CANARY_TEST_ACCOUNTS is not 'true'; skipping test accounts"
	exit 0
fi

echo "===== Applying test accounts ====="
psql_cmd < "$script_dir/01-test_account.sql"
psql_cmd < "$script_dir/02-test_account_players.sql"
echo "Test accounts applied (password '1' for every seeded account)."
