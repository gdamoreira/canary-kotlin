# Wave 37 Plan — `src/items/` Section

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/items/` section (10 rows: 7 PARTIAL, 3 MISSING).

## Tasks

### Task A1 — Item core (`feat/items-item-core`)
- **Canary:** `src/items/item.cpp` (71 methods).
- **Deliverable:** decay ticks, `moveTo`, `getDescription`, weapon checks, serialization, imbuement/forge stats on `Item`/`ItemType`.
- **Tests:** transcribe real Canary behavior.

### Task A2 — Tile & thing/cylinder/trashholder (`feat/items-tile-thing`)
- **Canary:** `src/items/tile.cpp`, `thing.cpp`, `cylinder.cpp`, `trashholder.cpp`.
- **Deliverable:** Thing/Cylinder abstraction, trashholder, tile creatures vector, flags/PZ, top/down ordering, house tile, fields.
- **Tests:** transcribe real Canary behavior.

### Task A3 — Decay + bed (`feat/items-decay-bed`)
- **Canary:** `src/items/decay/decay.cpp`, `src/items/bed.cpp`.
- **Deliverable:** decay engine/timer pipeline (bounded intervals, stale-callback safe), bed sleeping/regen.
- **Tests:** transcribe real Canary behavior; destroyed/replaced owner coverage.

### Task A4 — Containers + depot/inbox/mailbox (`feat/items-containers`)
- **Canary:** `src/items/containers/container.cpp`, `containers/depot/*`, `inbox/*`, `mailbox/*`.
- **Deliverable:** capacity/weight recursion, query add/remove, iterators, serialization; locker town mapping, mail routing/letters.
- **Tests:** transcribe real Canary behavior; persistence via assigned migration if needed: **V15**.

### Task A5 — Item functions + weapons (`feat/items-functions-weapons`)
- **Canary:** `src/items/functions/item/attribute.cpp`, `item_parse.cpp`, `src/items/weapons/weapons.cpp`.
- **Deliverable:** custom attributes, full XML item parse, weapon registry/max-damage tables.
- **Tests:** transcribe real Canary behavior.

### Task A6 — Items registry depth (`feat/items-registry`)
- **Canary:** `src/items/items.cpp`.
- **Deliverable:** appearances/protobuf merge, augment tables, loot type, reload seam.
- **Tests:** transcribe real Canary behavior.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip all `src/items/` rows to `IMPLEMENTED`; update `docs/missing-features.md` §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.

## Completion

Completed. All 10 `src/items/` rows IMPLEMENTED. The 6 parallel branches were integrated semantically (Item.kt 6-way merge) and the full suite is green at 395 suites / 4154 tests.
