# Feature: Configuration & Server Environment Manager

## 1. Explanation
Manages server configuration loading, environment variable overrides, validation of network ports, rate limits, world type (PvP, Non-PvP, Retro-PvP), spawn rates, exp stages, and database credentials (`config.lua` in Canary).

## 2. Impact
Controls startup parameters, operational toggles, and server tuning without requiring code recompilation.

## 3. Functionality Affected
- **Configuration Parsing**: Reads configuration files (YAML / HOCON / .properties / Lua).
- **Environment Overrides**: Reads `CANARY_*` environment variables (Docker-friendly).
- **Runtime Reload**: Reloads non-critical configs via in-game command.

## 4. Agent Implementation Details
- Implement `ConfigManager` with type-safe Kotlin data classes (e.g. using `sksamuel/hoplite` or pure Kotlin parser).
- Expose defaults matching Canary's default `config.lua.dist`.
- Support configuration injection across networking, database, and game modules.

## 5. Test Reference
- `org.opentibia.canary.config.ConfigManagerTest`: Tests configuration loading with fallbacks and environment variable overrides.
