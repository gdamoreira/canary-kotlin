# Wave 34 Plan — `src/enums/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/enums/` section (newly added to `docs/development/missing-features.md`). All rows are already IMPLEMENTED except `container_type.hpp`/`object_category.hpp`.

## Row being closed

| C++ | Current | Target |
|---|---|---|
| `container_type.hpp`, `object_category.hpp` | MISSING | IMPLEMENTED — container/object category enums with exact Canary values |

## Task

### Task 1 — Container/object categories (`feat/container-object-categories`)
- **Canary sources:** `src/enums/container_type.hpp`, `src/enums/object_category.hpp`.
- **Deliverable:** `items/ContainerType.kt` (`ContainerType_t`) and `items/ObjectCategory.kt` (`ObjectCategory_t`) with the exact enum names/values.
- **Tests:** enum value parity.
- **Constraints:** `items` (new files); no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip the `src/enums/` rows to `IMPLEMENTED` in `docs/development/missing-features.md`; update `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

## Completion

Completed. `ContainerSpecial_t` + `ObjectCategory_t` ported. The `src/enums/` section is now fully IMPLEMENTED.
