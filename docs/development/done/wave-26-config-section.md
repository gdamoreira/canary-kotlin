# Wave 26 Plan — `src/config/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** Third section of `docs/development/missing-features.md` §2 (`src/config/`). Goal: turn the `PARTIAL` rows into `IMPLEMENTED`.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `configmanager.cpp/.hpp` (`ConfigManager`) | PARTIAL | IMPLEMENTED — full typed getters, reload, OTC feature lists |
| `config_enums.hpp` | PARTIAL | IMPLEMENTED — full config key surface with Canary defaults |

## Tasks

### Task 1 — Config key surface (`feat/config-enums`)
- **Canary sources:** `src/config/config_enums.hpp`.
- **Deliverable:** `config/ConfigKeys.kt` — the full Canary config key surface: every `Boolean_config`/`Integer_config`/`Float_config`/`String_config` key with its exact name and default value, plus a key-type enum. New file only.
- **Tests:** spot-check defaults across each type; key-name parity with the C++.
- **Constraints:** `config` (new file) only; no Lua.

### Task 2 — Typed ConfigManager (`feat/config-manager`)
- **Canary sources:** `src/config/configmanager.cpp/.hpp`.
- **Deliverable:** extend `config/ConfigManager.kt` with typed getters (`getBoolean`/`getNumber`/`getFloat`/`getString`), `reload`, and the Lua OTC feature lists; resolve from an injected key map + env/properties with Canary precedence.
- **Tests:** typed getter resolution/precedence; reload; OTC feature list parsing.
- **Constraints:** `config` only; no Lua gameplay.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

## Post-wave (AGENTS.md §6)

Flip the `src/config/` rows to `IMPLEMENTED` in `docs/development/missing-features.md`; update `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

## Completion

Completed. `src/config/` section implemented: `ConfigKeys` (360 keys, exact names/types/defaults) and typed `ConfigManager` getters + `reload` + OTC feature lists. Rows flipped to IMPLEMENTED.
