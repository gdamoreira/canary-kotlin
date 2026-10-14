# Guarded start script for the Canary Kotlin Docker stack (adapted from
# canary/docker/up.ps1). Starts the stack from the repository root, creates
# .env from docker/.env.dist when missing, prunes only safe Docker leftovers,
# and never removes database volumes.
#
# Environment knobs: NO_BUILD=true, SKIP_CLEANUP=true, CLEANUP_UNTIL=168h,
# LAN=true (advertise the detected LAN IP), COMPOSE_PROJECT_NAME=canary-kotlin.
param(
	[switch]$NoBuild = ($env:NO_BUILD -eq "true"),
	[switch]$SkipCleanup = ($env:SKIP_CLEANUP -eq "true"),
	[switch]$Lan = ($env:LAN -eq "true"),
	[string]$CleanupUntil = $(if ($env:CLEANUP_UNTIL) { $env:CLEANUP_UNTIL } else { "168h" })
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

function Fail([string]$Message) {
	Write-Error $Message
	exit 1
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
	Fail "Docker was not found. Install Docker Desktop, start it, then run this script again."
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
	Fail "Docker is installed but the daemon is not running. Start Docker Desktop, then run this script again."
}

docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
	Fail "Docker Compose v2 was not found. Update Docker Desktop or install the Docker Compose plugin."
}

if (-not (Test-Path ".env")) {
	if (-not (Test-Path "docker/.env.dist")) {
		Fail "Missing docker/.env.dist. Run this script from a complete Canary Kotlin checkout."
	}
	Copy-Item "docker/.env.dist" ".env"
	Write-Host "Created .env from docker/.env.dist."
}

function Get-EnvValue {
	param([string]$Path, [string]$Name, [string]$DefaultValue = "")
	if (-not (Test-Path $Path)) { return $DefaultValue }
	$line = Get-Content $Path | Where-Object { $_ -match "^$([regex]::Escape($Name))=" } | Select-Object -Last 1
	if (-not $line) { return $DefaultValue }
	$value = $line.Substring($Name.Length + 1)
	if ([string]::IsNullOrWhiteSpace($value)) { return $DefaultValue }
	return $value
}

function Set-EnvValue {
	param([string]$Path, [string]$Name, [string]$Value)
	$lines = @()
	$found = $false
	if (Test-Path $Path) {
		foreach ($line in Get-Content $Path) {
			if ($line -match "^$([regex]::Escape($Name))=") {
				$lines += "$Name=$Value"
				$found = $true
			} else {
				$lines += $line
			}
		}
	}
	if (-not $found) { $lines += "$Name=$Value" }
	Set-Content -Path $Path -Value $lines
}

function Get-LanIp {
	$defaultRoute = Get-NetRoute -DestinationPrefix "0.0.0.0/0" -ErrorAction SilentlyContinue |
		Where-Object { $_.NextHop -and $_.NextHop -ne "0.0.0.0" } |
		Sort-Object RouteMetric, InterfaceMetric |
		Select-Object -First 1

	if ($defaultRoute) {
		$ip = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $defaultRoute.InterfaceIndex -ErrorAction SilentlyContinue |
			Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
			Select-Object -First 1
		if ($ip) { return $ip.IPAddress }
	}

	$ip = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
		Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.InterfaceAlias -notlike "vEthernet*" } |
		Sort-Object InterfaceMetric |
		Select-Object -First 1
	if ($ip) { return $ip.IPAddress }
	return $null
}

if ($Lan) {
	$lanIp = Get-LanIp
	if (-not $lanIp) {
		Fail "Could not detect a LAN IPv4 address. Edit .env manually and set CANARY_IP to the address other PCs can reach."
	}
	Set-EnvValue -Path ".env" -Name "CANARY_IP" -Value $lanIp
	Write-Host "Configured .env for LAN access at ${lanIp}."
}

$projectName = if ($env:COMPOSE_PROJECT_NAME) { $env:COMPOSE_PROJECT_NAME } else { "canary-kotlin" }

$composeArgs = @("compose", "up", "-d", "--remove-orphans")
if (-not $NoBuild) {
	$composeArgs += "--build"
}

& docker @composeArgs
if ($LASTEXITCODE -ne 0) {
	exit $LASTEXITCODE
}

if (-not $SkipCleanup) {
	& docker container prune --force --filter "label=com.docker.compose.project=$projectName"
	if ($LASTEXITCODE -ne 0) {
		exit $LASTEXITCODE
	}

	& docker image prune --force --filter "label=com.docker.compose.project=$projectName"
	if ($LASTEXITCODE -ne 0) {
		exit $LASTEXITCODE
	}

	if ($CleanupUntil) {
		& docker builder prune --force --filter "until=$CleanupUntil"
	} else {
		& docker builder prune --force
	}
	if ($LASTEXITCODE -ne 0) {
		exit $LASTEXITCODE
	}
}

& docker system df
