# Wave 70 Plan — Engine Internals & Remaining Tails

**Status:** In progress — dispatched
**Baseline:** `main` @ `480a650b`; 766 suites / 8127 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 items 1–3. **Scaling proposals excluded.**
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Close the engine-internals and protocol/dispatcher tails so only the deferred
list remains.

## 2. Workstreams

### G1 — Death damage-map and corpse pipeline

**Scope**
- `Creature::onDeath` damage-map resolution: last-hit and most-damage killers
  (creature.cpp:579-720) feeding the death seam instead of nulls.
- Monster corpse creation + loot/experience on death wired into
  `Creature.onDeath` (`MonsterBehavior.death`/`dropCorpse` are test-only today).

**Files (owner):** `creatures/Creature.kt`, `creatures/monsters/**`,
`game/**` (death regions), `combat/**` (damage-map bookkeeping only).

**Canary sources:** `src/creatures/creature.cpp:577-720`,
`src/creatures/monsters/monster.cpp` (`death`/`dropCorpse`).

**Tests:** last-hit/most-damage resolution; corpse created with the right
owner; loot rolled on death; experience awarded to the top damager.

---

### G2 — Cylinder producers (inventory change → state updates)

**Scope**
- Port `Player::postAddNotification`/`postRemoveNotification` and the
  `updateState` producer chain so inventory changes emit the state refreshes
  (including the sale-list refresh wired in wave 69).

**Files (owner):** `creatures/players/**`, `items/**` (Cylinder producers),
`network/protocol/**` (updateState sink only), `game/**` (notification region).

**Canary sources:** `src/creatures/players/player.cpp` (`postAddNotification`/
`postRemoveNotification`, `updateState`), `src/items/cylinder.cpp`.

**Tests:** adding/removing an item emits the state update; open shop refreshes
the sale list through the producer chain.

---

### G3 — Lua createMonster and raid bridge

**Scope**
- Bind Lua `Game.createMonster`/`createSoulPitMonster`
  (`WorldLuaOperations.createMonster` inherits a null default today).
- Provide a production `RaidSpawner`/`RaidMonster` bridge so raids spawn real
  monsters through `MonsterSpawnPath` (the `systems/raid` stub is inert).

**Files (owner):** `lua/runtime/WorldLuaOperations.kt`, `lua/bindings/**`
(game bindings only), `systems/raid/**`, `creatures/spawns/**`.

**Canary sources:** `src/lua/functions/core/game/game_functions.cpp:589/641`,
`src/game/raids/raids.cpp:408`.

**Tests:** Lua `Game.createMonster` places a real monster with hosts bound;
raid event spawns through the bridge and fires `onSpawn`.

---

### G4 — Protocol and dispatcher tails

**Scope**
- Old-protocol `0x94` helper emission (the writer exists) and the `0xCA`
  exiva-restrictions frame on no-PVP worlds.
- `0xB3` actions `0x02-0x09` (perk mutations + trailing window/0x5C sends).
- Inline bug-report byte (`oldProtocol && ≥1054`) and the unsupported-version
  disconnect.
- Dispatcher: `stopEvent` race for a currently-executing cycle; per-event
  telemetry context names.

**Files (owner):** `network/protocol/**`, `game/scheduling/**`,
`io/iologindata/**` (exiva name resolution only).

**Canary sources:** `src/network/protocol/protocolgame.cpp` (0x94/0xCA/0xB3,
login), `src/game/scheduling/dispatcher.cpp`.

**Tests:** helper frames on old protocol; exiva frame on a no-PVP world;
`0xB3` action paths; stopEvent cancels an executing cycle; telemetry names.

---

## 3. Conflict matrix

| File | Owners | Rule |
|---|---|---|
| `game/**` | G1 (death), G2 (notification) | localized hunks |
| `creatures/players/**` | G2 | G1 touches `Creature.kt`/monsters only |
| `network/protocol/**` | G2 (sink), G4 | disjoint files where possible |
| `lua/**` | G3 | exclusive |
| `game/scheduling/**` | G4 | exclusive |

## 4. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch.

## 5. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`
(Phase 74), and this document in place.

## 6. Out of scope

Scaling proposals (`scaling-and-multi-world.md`); deferred list unchanged
(differential harness; CipSoft860 profile; OTel SDK/OStream; micro-seams).
