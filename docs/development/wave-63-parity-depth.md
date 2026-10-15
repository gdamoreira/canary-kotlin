# Wave 63 Plan — Parity Depth: Combat/Spells, Events, Conditions, Visibility, Composition, Item Text

**Status:** Completed (2026-10-15) — six workstreams merged; 702 suites / 7781 tests green
**Baseline:** `main` @ `ae516ea9`; 694 suites / 7653 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 (ranked gaps) plus the highest-impact `PARITY GAP` annotations (530 across `src/main`, concentrated in `game/Game.kt` 107, `creatures/Creature.kt` 47, `lua/bindings/core/PlayerFunctions.kt` 42, `network/protocol/GamePacketRouter.kt` 33, `map/Tile.kt` 21).
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6); no lifecycle folders.

---

## 1. Objective

Close the six highest-impact remaining parity gaps so the tracked surface moves
from "engine-complete with depth seams" toward **100% C++ parity**. Every task
is a faithful port of a verified Canary symbol — no invented APIs (§1.1), Lua
stays Lua (§1.4), TDD with zero warnings (§4).

## 2. Workstreams

### A1 — Event fanout and monster speech

**Scope**
- Bind the normal say/yell fanout to monster events: `Game::internalCreatureSay`
  spectator loop must reach `Monster::onCreatureSay` (currently only
  whisper/private spectators do).
- Validate and filter named `CreatureEvent`s per creature (the
  `CreatureEvents::registerLuaEvent` registry) instead of dispatching globally;
  unknown names are rejected like `g_creatureEvents()`.
- `onSpawn` dispatch through the spawn path where feasible (document residual).

**Files (owner):** `game/Game.kt` (say/event paths), `chat/**`,
`creatures/monsters/MonsterPipelinesHost.kt`, `creatures/monsters/**`,
`lua/bindings/library/**` (event registry lookups only).

**Canary sources:** `src/game/game.cpp:7618` (`Game::internalCreatureSay`),
`src/creatures/monsters/monster.cpp:568` (`Monster::onCreatureSay`),
`src/lua/creature/creatureevent.cpp:23` (`registerLuaEvent`).

**Tests:** say/yell reaches a live monster's `onSay`; whisper keeps working;
named event registered after spawn filters per creature; unknown name rejected.

---

### A2 — Combat identity and spell execution

**Scope**
- Thread Monster identity through the combat path so `onPlayerAttack` fires for
  **spell damage** (`Combat::doCombatHealth`/`CombatTarget` carry the caster).
- Bind a Lua runner to registry-hit `MonsterRegisteredSpell.castSpell`
  (`CombatSpell::castSpell` → `spellBlock.spell->castSpell(monster, target)`,
  monster.cpp:1979/2220) so registered spells execute instead of staying inert.
- `Combat::canDoCombat(player, tile, aggressive)` port and use; `CombatManaFunc`
  black-skull halving; `params.valueCallback` min/max values;
  `applyInstantSpellDirection` and area-dispatch consumption tails.

**Files (owner):** `combat/**` (except `combat/condition/**`), `spells/**`,
`lua/bindings/SpellBindings.kt`, `creatures/monsters/MonsterCombatSpell.kt`.

**Canary sources:** `src/creatures/combat/combat.cpp:1645-1713`
(`doCombatHealth` overloads), `:373` (`canDoCombat` call sites),
`src/creatures/combat/spells.cpp:378/420` (`CombatSpell::castSpell`),
`:1312/1341` (`InstantSpell::castSpell`), `:1561/1569`
(`RuneSpell::castSpell`), `src/creatures/monsters/monster.cpp:1979,2220`.

**Tests:** spell damage on a monster fires `onPlayerAttack`; registry spell
executes its `onCastSpell` Lua body; `canDoCombat` denies PZ/aggressive cases;
black-skull mana halving.

---

### A3 — Condition payload fidelity

**Scope**
- Encode the flat `ConditionAttributes` arrays on save (skills/stats/buffs with
  sequential index, absorbs/increases via `setAbsorb`/`setIncrease`) so reload no
  longer zeroes them; keep `PERSISTENT` and `INTERVALDATA` behavior from wave 59.
- Preserve tick/add **sounds** (currently written as `SILENCE(0)`): retain the
  configured sound ids in `Condition.setParam` and serialize them.

**Files (owner):** `io/iologindata/PlayerConditionPersistence.kt`,
`combat/condition/**`.

**Canary sources:** `src/creatures/combat/condition.cpp:749-783`
(`ConditionAttributes::unserializeProp`), `:277-301` (`Condition::serialize`
family), `:1817-1824` (interval data).

**Tests:** skills/stats/buffs/absorbs/increases/charm round-trip; tick/add
sounds round-trip; malformed stream fails safely (documented UB divergence).

---

### A4 — Visibility and viewport overrides

**Scope**
- `Player::canSee(pos)` **client-viewport override** (11×11 window vs the
  creature 18×14 map viewport) and use it in `setAttackedCreature`/
  `setFollowCreature` sight windows.
- `Player::sendCreatureTurn` canSee gate + stackpos→`sendUpdateTile` fallback.
- `InMemoryProtocolSink` transports get the `sendCreatureAppear` fallback
  (currently only `PlayerSendSink`).
- Remove the stale "isInGhostMode is not modelled" comments in `map/Tile.kt`
  once verified true.
- CipSoft860 square branch: document as intentional current-profile-only
  (out of scope unless cheap).

**Files (owner):** `creatures/Creature.kt`, `network/protocol/PlayerSendSink.kt`,
`network/protocol/creature/**`, `map/Tile.kt`.

**Canary sources:** `src/creatures/players/player.cpp:1402` (`canSee(pos)`),
`:1409` (`canSeeCreature`), `:8809` (`sendCreatureTurn`),
`src/network/protocol/protocolgame.cpp:4723-4740` (square gate).

**Tests:** attack/follow windows use the client viewport; turn gated and
fallback frame emitted; in-memory sink fallback; ghost comments resolved.

---

### A5 — Composition and persistence wiring

**Scope**
- `ServerBootstrap` passes `subloadRepository`/`subloads` into `IOLoginData` so
  storage/condition/animus writes are reachable in production (wave 59 left the
  composition site unwired).
- `Player::isOffline` real wiring: the seam defaults to `false`; connect it to
  the client/session presence where the listener installs sessions.
- V13 interim animus table: resolve the leftover (keep + document, or add a
  migration that drops it — decide with Flyway discipline §2.1).
- Clean the stale `IOLoginDataSubloads` KDoc notes that reference pre-V20
  lookmount gaps.

**Files (owner):** `bootstrap/ServerBootstrap.kt`,
`io/iologindata/IOLoginData*.kt`, `creatures/PlayerFeatures.kt`,
`src/main/resources/db/migration/**`.

**Canary sources:** `src/io/iologindata_load_player.cpp:277-289` (animus),
`iologindata_save_player.cpp:279-299` (conditions + animus inline),
`src/creatures/players/player.hpp:481` (`isOffline`).

**Tests:** boot composition passes the sub-load repositories (registry
injection test); storage/condition writes reach the repositories in the
production path; migration chain stays green on H2.

---

### A6 — Item description renderer (lookAt text)

**Scope**
- Port `Item::getDescription` (static + instance): article/pluralization,
  weight, description text, charges/fluids, duration/decay text, subtype text,
  `lookDistance` rules and `addArticle`.
- `Cylinder.getDescription` returns `""` today — wire the renderer into the
  look-at output path.

**Files (owner):** `items/Item.kt`, `items/Thing.kt`, `items/Cylinder.kt`,
`items/functions/**`, `network/protocol/LookPacketHandler` (output only).

**Canary sources:** `src/items/item.cpp:2554` (static renderer), `:3352`
(instance), `src/items/thing.hpp` (`getDescription` contract).

**Tests:** single item, stackable with subtype, fluid container, charges,
duration text, distance-based variants (`lookDistance` 1 vs 2+), no-article
path.

---

## 3. Conflict matrix

| Area | Owner | Notes |
|---|---|---|
| `game/Game.kt` | A1 | A2 must not edit; combat identity lives in `combat/**` |
| `combat/**` top level | A2 | A3 owns `combat/condition/**` only |
| `combat/condition/**` | A3 | A2 stays out |
| `creatures/Creature.kt` | A4 | A5 uses `PlayerFeatures.kt` only |
| `io/iologindata/IOLoginData*.kt` | A5 | A3 owns `PlayerConditionPersistence.kt` |
| `bootstrap/**` | A5 | A1's event registry needs no boot edits |
| `items/**` | A6 | exclusive |

## 4. Commit strategy

- Each workstream: one feature commit on its own branch/worktree (TDD, full
  suite, zero warnings), merged sequentially into `main`.
- Then one documentation commit (see gate) pushed to `origin`.

## 5. Verification gate

- `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
  zero failures, **zero Kotlin warnings**; record real suite/test counts.
- Focused suites per workstream plus the full run before each merge.
- `git grep` checks: no new invented symbols (§1.1); Lua files untouched (§1.4).

## 6. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md` (§1 metrics, §3 ranked gaps,
§4 opcode notes where touched), `README.md` metrics, `CHANGELOG.md` (Phase 65),
and this document's status in place (no `Done/` move). Push the docs commit.

## 7. Out of scope (tracked, next waves)

- Live differential harness (packet-capture A/B vs a running Canary) — the
  strongest remaining proof, requires both servers running.
- CipSoft860/old-protocol wire branches; OpenTelemetry SDK + OStream reader.
- The remaining low-impact `PARITY GAP` micro-seams (KV LRU ordering, logger
  `DEBUG_LOG`, thread-pool daemon flags, `TransportCodec` tails).

---

## Completion (2026-10-15)

All six workstreams landed: event fanout (`f333648e`), combat/spell execution
(`50df4b83`), condition payload (`8a08fbad`), visibility/viewport (`3939c932`),
composition wiring (`a9e35c2b`) and item description (`e23e4552`) — merged into
`main` with the integrated suite at **702 suites / 7781 tests, 0 failures, zero
Kotlin warnings**. Remaining tails are recorded in
`implementation-status.md` §3 (persistence call-site wiring, combat/spell
wiring, event/transport tails, renderer seams, map depth).

**Recovery addendum (2026-10-15):** an ancestry audit found three wave-58
branches had silently failed to merge (`host-lua-unification`,
`known-creature-set`, `codec-fidelity`); they were recovered in the same
session (`4589ca3b`, `4b11d72e` and the host-lua merge), bringing the
integrated suite to **706 suites / 7817 tests**.
