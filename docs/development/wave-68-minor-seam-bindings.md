# Wave 68 Plan — Minor Seam Bindings

**Status:** In progress — dispatched
**Baseline:** `main` @ `3f6d4d95`; 748 suites / 8041 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 item 1 (remaining minor seam bindings).
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Bind the last small seams documented after wave 67 so the only remaining work
is the deliberately deferred list.

## 2. Workstreams

### E1 — Event/modal/combat-state root bindings

**Scope**
- Bind the think/death Lua loops (`Creature.onThink`/`onDeath` event seams) to
  `ScriptingDispatch` at the root.
- Give `Game.modalWindowManager` its client (sink) so modal windows emit.
- Complete `WorldLuaOperations.attachCombat` with the production target
  state/effects (not just map/lookups).

**Files (owner):** `network/CanaryServer.kt`, `network/protocol/GameHandler.kt`,
`game/Game.kt` (modal region), `lua/runtime/WorldLuaOperations.kt`,
`bootstrap/ServerBootstrap.kt` (additive hunk).

**Canary sources:** `src/creatures/creature.cpp:157-163,819-824`,
`src/network/protocol/protocolgame.cpp` (`sendModalWindow`),
`src/lua/functions/core/game/game_functions.cpp` (combat state).

**Tests:** think/death attached events fire in production wiring; modal window
emits to the sink; attachCombat resolves target state/effects.

---

### E2 — Combat/group/player tails

**Scope**
- Wheel beam-mastery host adapter (`PlayerWheel` beam methods) + binding the
  `CombatPlayer` beam seam.
- `Game.combatTargetOf` adopts `MonsterPipelinesHost.combatTargetState()`.
- `Player.hasFlag` reads `Group.flags` (falling back to the local set only when
  no group is resolved).
- `Player.spells` populated at login so `sendSpellCooldowns` stops being inert.

**Files (owner):** `systems/wheel/**`, `game/Game.kt` (combatTargetOf region),
`creatures/players/**`, `creatures/PlayerFeatures.kt`, `io/iologindata/**`
(spells load hunk only).

**Canary sources:** `src/creatures/players/wheel/player_wheel.cpp` (beam),
`src/creatures/players/grouping/group.cpp` (`flags`), `src/creatures/players/player.cpp`.

**Tests:** beam mastery applies through the adapter; combatTargetOf exposes the
host state; `hasFlag` follows the group flags; login fills `Player.spells`.

---

### E3 — Protocol tails

**Scope**
- `sendSaleItemList` (shop sale-list refresh on open).
- `sendWeaponProficiencyWindow` (`INSPECT_PROFICIENCY`, 0xCD type 4).
- Login/placement tails: the self `0x6A` add-creature branch and the fuller
  login frame (stats/skills fields where the writer exists).

**Files (owner):** `network/protocol/**`, `game/Game.kt` (login/placement
regions only).

**Canary sources:** `src/network/protocol/protocolgame.cpp` (`sendSaleItemList`,
`sendWeaponProficiencyWindow`, login sequence).

**Tests:** sale list frames after shop open; proficiency window bytes;
self-appear branch emits the add-creature frame.

---

### E4 — Persistence/guild tails

**Scope**
- `savePlayerSystems` (virtue/harmony → `spells` scope) wired into the save path.
- Guild helper broadcasts (`Guild::updatePlayerHelpers`) to online members.
- Load-side guid wrap for exiva whitelists (uint32 semantics).

**Files (owner):** `io/iologindata/**`, `social/guild/**`,
`network/protocol/**` (guild writer files only).

**Canary sources:** `src/io/iologindata_save_player.cpp:861-876`,
`src/creatures/grouping/guild.cpp` (`updatePlayerHelpers`).

**Tests:** systems round-trip; helper broadcast frames; guid wrap round-trip.

---

## 3. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch.

## 4. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`
(Phase 71), and this document in place.

## 5. Out of scope

Deferred list unchanged: differential harness; CipSoft860/old protocol;
OpenTelemetry SDK/OStream; micro-seams.
