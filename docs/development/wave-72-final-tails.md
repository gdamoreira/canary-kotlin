# Wave 72 Plan — Final Reduced-Port Tails

**Status:** Completed (2026-10-15) — four workstreams merged; 798 suites / 8308 tests green
**Baseline:** `main` @ `6d2ccaaa`; 792 suites / 8270 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 item 1. **Scaling proposals excluded.**
**Convention:** documents live directly in `docs/development` (AGENTS.md §5/§6).

---

## 1. Objective

Close the final reduced-port tails; only the structural `Player`-as-`Cylinder`
refactor and the deferred list remain afterwards.

## 2. Workstreams

### I1 — Drain credit tails

**Scope**
- Mana drain attribution: `Game::combatChangeMana` → `drainMana(attacker, …)` →
  `addDamagePoints` (game.cpp:9226).
- Hook `Player::onAttackedCreatureDrainHealth`'s party-tick credit into virtual
  dispatch (currently an extension outside the base member).
- Condition-damage drain credit (`ConditionTarget.drainHealth` owner
  resolution), Lua `creature:addHealth`, `MagicField.onStepInField`.

**Files (owner):** `combat/**`, `creatures/Creature.kt`, `creatures/players/**`,
`game/Game.kt` (combat regions), `items/**` (MagicField).

**Canary sources:** `src/game/game.cpp:8620,9226`,
`src/creatures/players/player.cpp:6563-6575`,
`src/creatures/combat/condition.cpp:2000-2004`.

**Tests:** mana loss credits the attacker; party tick credit on drain; field
step-in damage attributed.

---

### I2 — Light fold and tile spectators

**Scope**
- `Player::getCreatureLight` (player.cpp:6277-6281): fold the composed
  `itemsLight` into the light sent to clients (max of internal/items).
- `Tile::postRemoveNotification` spectator sends (the tile-source removal call
  is currently observable only through the call site).

**Files (owner):** `creatures/Creature.kt`, `map/Tile.kt`,
`network/protocol/**` (light/update writers only).

**Canary sources:** `src/creatures/players/player.cpp:6277-6299`,
`src/map/tile.cpp` (`postRemoveNotification`).

**Tests:** sent light reflects items light; tile removal notifies spectators.

---

### I3 — Livestream viewer and corpse updates

**Scope**
- `ProtocolGame::castViewerLogin` (protocolgame.cpp:12380): the livestream
  viewer login entry that sets `isLivestreamViewer` (the wave-71 0xCA resend
  routing is ready behind it).
- Bind the monster corpse container update path (`sendItemUpdateToClient`) so
  the dropCorpse tail's container update emits.

**Files (owner):** `network/protocol/**`, `game/Game.kt` (regions),
`bootstrap/ServerBootstrap.kt`.

**Canary sources:** `src/server/network/protocol/protocolgame.cpp:12380`,
`src/game/game.cpp` (`sendItemUpdateToClient`).

**Tests:** viewer login sets the flag and routes 0xCA; corpse container update
frame emitted.

---

### I4 — GAME_STATE_INIT trigger

**Scope**
- `Game::setGameState(GAME_STATE_INIT)` (game.cpp:963-993): invoke the ported
  INIT pieces (mounts `loadFromXml`, attached effects, MOTD/players-record
  loads, global events startup, wheel global data, items price, groups/chat
  loads where boot-owned) or document each as boot-owned/unported.

**Files (owner):** `game/Game.kt` (init region), `bootstrap/ServerBootstrap.kt`,
`network/CanaryServer.kt`.

**Canary sources:** `src/game/game.cpp:963-993`.

**Tests:** INIT runs the wired pieces exactly once; boot order preserved.

---

## 3. Conflict matrix

| File | Owners | Rule |
|---|---|---|
| `game/Game.kt` | I1 (combat), I3 (regions), I4 (init) | localized hunks |
| `creatures/Creature.kt` | I1, I2 | localized hunks |
| `network/protocol/**` | I2 (writers), I3 | disjoint files |
| `bootstrap/ServerBootstrap.kt` | I3, I4 | additive hunks |

## 4. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch.

## 5. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`
(Phase 76), and this document in place.

## 6. Out of scope (final)

- **Structural**: the `Player`-as-`Cylinder` refactor (notification parents) —
  a deliberate architectural decision, documented as a reduced-port
  characteristic, not rushed in this wave.
- **Deferred by design**: live differential harness; CipSoft860/old-protocol
  profile; OpenTelemetry SDK/OStream; micro-seams.

---

## Completion (2026-10-15)

drain-credit-tails (`ee0aa7c0`), light-spectators (`42ec0169`),
viewer-container-updates (`88f16f21`), game-state-init (`01b89e41`) merged with
zero conflicts. Integrated suite: **798 suites / 8308 tests, 0 failures, zero
Kotlin warnings**; ancestry verified. What remains is the structural
Player-as-Cylinder refactor, the documented reduced-model list and the
deferred-by-design program (differential harness, old-protocol profile, OTel).
