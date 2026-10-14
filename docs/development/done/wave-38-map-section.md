# Wave 38 Plan — `src/map/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/map/` section (6 rows: 4 PARTIAL, 2 MISSING).

## File ownership (avoid cross-agent conflicts)

| Agent | Owns (may edit/create) |
|---|---|
| A1 map-core | `map/GameMap.kt`, `map/MapGrid.kt` |
| A2 map-cache | `map/cache/**` (new) |
| A3 spectators | `map/Spectators.kt` (new) |
| A4 house | `map/house/**` |
| A5 astar-town | `map/pathfinding/AStarNodes.kt`, `map/Town.kt` (new) |

Do not edit another agent's files; document cross-file needs in the report.

## Tasks

### Task A1 — Map core (`feat/map-core`)
- **Canary:** `src/map/map.cpp` (1,429 lines).
- **Deliverable:** `canThrowObjectTo`, `canWalkTo`, `checkSightLine`, `getPathMatching`, `placeCreature`, `moveCreature`, `save` (houses/spawns/zones loading already landed in wave 36 via `IOMap`).
- **Tests:** transcribe real Canary behavior.

### Task A2 — Map caches & navigation (`feat/map-cache`)
- **Canary:** `src/map/mapcache.cpp`, `src/map/navigation_snapshot.cpp`, `src/map/map_download.cpp`.
- **Deliverable:** map caches, navigation snapshots, map download.
- **Tests:** transcribe real Canary behavior.

### Task A3 — Spectators (`feat/map-spectators`)
- **Canary:** `src/map/spectators.cpp`.
- **Deliverable:** spectator sets, direction filtering, the full `getSpectators` variants.
- **Tests:** transcribe real Canary behavior.

### Task A4 — Houses (`feat/map-house`)
- **Canary:** `src/map/house/house.cpp`, `housetile.cpp`.
- **Deliverable:** rent/periods, transfers, auctions, access lists, eviction, persistence.
- **Tests:** transcribe real Canary behavior; migration only if needed: **V15**.

### Task A5 — A* nodes & towns (`feat/map-astar-town`)
- **Canary:** `src/map/utils/astarnodes.cpp`, `src/map/town.hpp`.
- **Deliverable:** `AStarNodes` (Canary's exact node/closed-set semantics), `Town` class/temple/ID registry.
- **Tests:** transcribe real Canary behavior.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip all `src/map/` rows to `IMPLEMENTED`; update `docs/development/missing-features.md` §1–§3, `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

## Completion

Completed. All 6 `src/map/` rows IMPLEMENTED (map core, caches/navigation, spectators, houses, AStarNodes, towns); full suite green at 410 suites / 4395 tests.
