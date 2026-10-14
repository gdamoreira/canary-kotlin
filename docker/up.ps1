# Guarded start script for the Canary Kotlin Docker stack (Windows PowerShell).
# Starts the stack (building the server image when needed) and prunes only safe
# Docker leftovers. Database volumes are never removed.
$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "docker is not installed or not on PATH"
    exit 1
}

Write-Host "Starting Canary Kotlin stack..."
docker compose up -d --build --remove-orphans

Write-Host ""
docker compose ps
Write-Host ""
Write-Host "Server logs: docker compose logs -f server"
Write-Host "Test account: 1 / 1"
