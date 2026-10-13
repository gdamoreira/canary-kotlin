# Wave 23 Plan — Lua Item/Tile Bindings, Zone Engine, Spell Engine, Server Edge

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).

## Goal

Close further high-impact gaps: item/tile/container Lua bindings, the Zone engine, the spell engine (runes/groups/cooldowns/learn-forget), and server edge infrastructure (webhook, protocol profile).

## Tasks

### Task 1 — Lua item/tile/container/position bindings (`feat/lua-item-tile-bindings`)
- **Canary sources:** `src/lua/functions/items/item_type_functions.cpp` (57), `container_functions.cpp` (17), `src/lua/functions/map/tile_functions.cpp` (31), `position_functions.cpp` (14), `house_functions.cpp` (26), `town_functions.cpp` (4).
- **Deliverable:** `ItemTypeFunctions`, `TileFunctions`, extended `ContainerFunctions`/`PositionFunctions` registering exact Canary names on the right metatables, delegating to seams.
- **Tests:** real Lua chunks via `LuaEnvironment` + install.
- **Constraints:** `lua/bindings` (+ additive `WorldLuaOperations`); no Lua gameplay.

### Task 2 — Zone engine (`feat/zone-engine`)
- **Canary sources:** `src/game/zones/zone.cpp/.hpp`.
- **Deliverable:** `game/zones/Zone.kt` + `Zones.kt`: zone definitions (area/flags), `addZone`/`removeZone`, `isInZone(pos, zoneId)`, `refreshZones`, and the zone flag checks used by combat/movement.
- **Tests:** zone membership, flag checks, refresh.
- **Constraints:** `game/zones` (+ additive); no Lua.

### Task 3 — Spell engine completion (`feat/spell-engine`)
- **Canary sources:** `src/creatures/combat/spells.cpp/.hpp` (`Spell`/`RuneSpell`: rune conjure/use, spell groups/cooldowns, areas, learn/forget, spell book).
- **Deliverable:** extend `spells/SpellRegistry.kt` with rune conjuring, spell groups + per-group cooldowns, area/effect metadata, and learn/forget/spell-book state.
- **Tests:** rune conjure, group cooldown, learn/forget, area data.
- **Constraints:** `spells` (+ additive); no Lua.

### Task 4 — Server edge infrastructure (`feat/server-edge`)
- **Canary sources:** `src/server/network/webhook/webhook.cpp`, `src/server/network/protocol/protocol_profile.cpp`, `protocol_session_hint.cpp`.
- **Deliverable:** `network/webhook/Webhook.kt` (Discord webhook POST payload builder + sender seam), `network/protocol/ProtocolProfile.kt` (profile detection/feature flags), `SessionHint.kt`.
- **Tests:** webhook payload shape, profile feature flags, session-hint handling.
- **Constraints:** `network` (+ additive); no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

## Post-wave (AGENTS.md §6)

Update `docs/missing-features.md`, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.
