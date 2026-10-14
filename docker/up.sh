#!/usr/bin/env sh
# Guarded start script for the Canary Kotlin Docker stack.
# Starts the stack (building the server image when needed) and prunes only safe
# Docker leftovers. Database volumes are never removed.
set -eu

cd "$(dirname "$0")/.."

if ! command -v docker >/dev/null 2>&1; then
    echo "error: docker is not installed or not on PATH" >&2
    exit 1
fi

if docker compose version >/dev/null 2>&1; then
    compose="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    compose="docker-compose"
else
    echo "error: docker compose plugin is not available" >&2
    exit 1
fi

echo "Starting Canary Kotlin stack..."
$compose up -d --build --remove-orphans

echo
$compose ps
echo
echo "Server logs: $compose logs -f server"
echo "Test account: 1 / 1"
