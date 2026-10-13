# Wave 22 Plan — Monster Runtime, Lua Monster Bindings, More Senders, Deeper Persistence

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).

## Goal

Close more high-impact gaps: the monster targeting/loot runtime, monster Lua bindings, remaining outbound senders, and depot/inbox/spells persistence.

## Tasks

### Task 1 — Monster targeting + loot runtime (`feat/monster-targeting-loot`)
- **Canary sources:** `src/creatures/monsters/monster_targeting.cpp`, `src/creatures/monsters/monster.cpp` (`searchTarget`, `dropLoot`, `death`/corpse, `canSee`, `isTarget`), `src/creatures/monsters/monster_relevance.cpp`.
- **Deliverable:** `creatures/monsters/MonsterTargeting.kt` (target search: nearest/friend/attackable, distance + visibility) and `creatures/monsters/MonsterLoot.kt` (`dropLoot` over the monster's loot items with Canary's probability/`lootItems` semantics).
- **Tests:** target selection rules; loot rolls (basis points, unique rolls, corpse owner).
- **Constraints:** `creatures/monsters` (+ additive); no Lua.

### Task 2 — Monster Lua bindings (`feat/lua-monster-bindings`)
- **Canary sources:** `src/lua/functions/creatures/monster/monster_functions.cpp` (51), `monster_type_functions.cpp` (101), `loot_functions.cpp` (18), `monster_spell_functions.cpp` (23).
- **Deliverable:** a substantial subset of monster/monster-type/loot Lua methods registered with exact Canary names, delegating to seams.
- **Tests:** real Lua chunks via `LuaEnvironment` + install.
- **Constraints:** `lua/bindings` (+ additive); no Lua gameplay ported.

### Task 3 — Outbound senders batch 3 (`feat/outbound-senders-3`)
- **Canary sources:** `src/server/network/protocol/protocolgame.cpp` — prey (`0xE7–0xE9`), task hunting (`0xBB`), bosstiary (`0x61/0x62/0xAE–0xB0`), cyclopedia (`0xE5/0xDB`), imbuement window, VIP (`0xD2/0xD4` if not already), quest log window.
- **Deliverable:** serializers for a solid subset in exact Canary order/endianness.
- **Tests:** `Unpooled` byte-exact.
- **Constraints:** `network/protocol` only; no Lua.

### Task 4 — Depot/inbox/spells persistence (`feat/persistence-depth-2`)
- **Canary sources:** `src/io/iologindata_*_player.cpp` (depot/inbox item serialization, player spells), `src/items/containers/depot/*`, `inbox/*`.
- **Deliverable:** Flyway `V6__player_depot_spells.sql` + a JDBC repository persisting depot/inbox items (reusing the V5 item attribute codec) and the player's learned spells.
- **Tests:** H2 round-trip (Flyway V1–V6).
- **Constraints:** `database`, `persistence`, `src/main/resources/db/migration`; no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

## Post-wave (AGENTS.md §6)

Update `docs/missing-features.md`, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.
