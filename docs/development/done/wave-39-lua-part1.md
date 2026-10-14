# Wave 39 Plan — `src/lua/` Part 1 (core bindings)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/lua/` section — 167/1,246 method registrations. This wave covers the largest files; further waves continue the section.

## Architecture

Bindings live in `lua/bindings/**` as an operations interface + `install(env, operations)` per area, wired from `lua/runtime/WorldLuaOperations.kt` (`install` block, lines ~120–135). Each binding method corresponds 1:1 to a Canary `*Functions::lua*` in `src/lua/functions/`.

## File ownership

| Agent | Owns |
|---|---|
| A1 lua-player-1 | `bindings/core/PlayerFunctions.kt` |
| A2 lua-player-2 | `bindings/core/PlayerSocialFunctions.kt` (new) + one install line in `WorldLuaOperations.kt` |
| A3 lua-game-item | `bindings/core/GameFunctions.kt`, `bindings/core/ItemFunctions.kt` |
| A4 lua-monstertype | `bindings/MonsterTypeFunctions.kt` |
| A5 lua-itemtype-tile | `bindings/core/ItemTypeFunctions.kt`, `bindings/core/TileFunctions.kt` |

## Tasks

### A1 — Player bindings 1 (`feat/lua-player-1`)
Canary `src/lua/functions/creatures/player/player_functions.cpp`: vitals/attributes, inventory, money, conditions, skills, outfit basics.

### A2 — Player bindings 2 (`feat/lua-player-2`)
Same C++ file: social, party/guild, storage, modal windows, UI sends, house/account, premium, deaths/kills.

### A3 — Game + item bindings (`feat/lua-game-item`)
Canary `src/lua/functions/core/game/game_functions.cpp` (59) + `src/lua/functions/items/item_functions.cpp` (56).

### A4 — MonsterType bindings (`feat/lua-monstertype`)
Canary `src/lua/functions/creatures/monster/monster_type_functions.cpp` (101).

### A5 — ItemType + Tile bindings (`feat/lua-itemtype-tile`)
Canary `src/lua/functions/items/item_type_functions.cpp` (57) + `src/lua/functions/map/tile_functions.cpp` (31).

## Rules for all agents

- Port **only** methods that exist in the C++ file; exact names/arg order; `lua_pushnil` guards preserved.
- Unwired engines become documented operation-interface seams (never invented behavior).
- TDD: a LuaJ test per batch calling the binding through `LuaEnvironment` (RED first).
- Build: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test -Dorg.gradle.jvmargs=-Xmx2g -Dkotlin.daemon.jvmargs=-Xmx2g --tests "*Functions*" --console=plain` then full suite once; zero warnings.
- Commit on your branch (no merge/push). Report exactly which Canary methods are ported and which remain.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Update the `src/lua/` row counts in `docs/development/missing-features.md` (partial progress is recorded honestly); update `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

## Completion

Part 1 completed: monster_type 101/101, game 59/59, item 55/55, item_type 58/58, tile 32/32, player 114/305. The `src/lua/` section continues in wave 40 (player remainder, monster/npc/weapon/spell functions, library bindings, modules).
