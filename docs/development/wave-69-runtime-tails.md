# Wave 69 Plan — Runtime Tails: Game Loop, Monster Hosts, Login/Protocol, Misc Bindings

**Status:** Completed (2026-10-15) — four workstreams merged; 766 suites / 8127 tests green
**Baseline:** `main` @ `bc02ddb6`; 758 suites / 8091 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 item 1 (remaining small tails). **Explicitly excludes the scaling proposals** (`scaling-and-multi-world.md`) — no service split, no multi-world schema, no Redis.
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Close the remaining runtime call-site tails so the engine is fully driven in
production and only the deliberately deferred list remains.

## 2. Workstreams

### F1 — Game loop driver

**Scope**
- Bind `Game.start()`'s `scheduleEvent`/`cycleEvent` seams (`Game.kt:13523-13524,
  13750-13770`) to the `GameDispatcher`'s delayed execution and start the loop
  after listeners bind, so forgeable/influenced monsters, light, the
  `checkCreatures` rotation, Lua GC, market price refresh and
  players-online persistence actually run on a cadence.
- Verify `checkCreatures` cadence drives the monster think pipelines
  (`Monster::trySchedulePostThink` host seam).

**Files (owner):** `game/Game.kt` (start/seam region), `game/scheduling/GameDispatcher.kt`,
`network/CanaryServer.kt`, `bootstrap/ServerBootstrap.kt`.

**Canary sources:** `src/game/game.cpp:862` (`Game::start`), `src/game/scheduling/dispatcher.cpp`
(`scheduleEvent`/`cycleEvent`).

**Tests:** loop started once; cycle events re-arm on the interval; `checkCreatures`
reaches the monster host; no double-start on repeated boot.

---

### F2 — Monster host coverage and death payload

**Scope**
- Bind `MonsterPipelinesHost` hooks inside `Game.internalPlaceCreature` so
  summons, raid and Lua-created monsters get think/death/combat hosts (today
  only the spawn path binds them).
- Thread the death payload (`corpse`, `killer`, `mostDamageKiller`,
  `unjustified`, `lastHit`) through `onDeathEventsHost` and unify it with the
  `DeathPenaltyEngine` call so Lua `onDeath` receives the C++ arguments.

**Files (owner):** `game/Game.kt` (placement region), `creatures/Creature.kt`
(seam signature), `creatures/monsters/**`, `game/DeathPenaltyEngine.kt`,
`network/CanaryServer.kt`/`network/protocol/GameHandler.kt` (binding updates).

**Canary sources:** `src/game/game.cpp:1495` (`internalPlaceCreature`),
`src/creatures/creature.cpp:819-824`, `src/creatures/players/player.cpp`
(`onDeath` payload).

**Tests:** a Lua-created monster gets its hosts; raid spawn hosts; death event
receives corpse/killer values; unjustified flag reaches Lua.

---

### F3 — Login and protocol tails

**Scope**
- Self-appear/login continuation: speed-formula doubles (`≥981`), second expert
  byte (`≥1058`), store URL/coin packet (`≥1080`), enter-world/allow-bug-report/
  tibia-time/pending-state sends, and the stats/skills frame parity where the
  writers exist.
- `0xB3` tracked-weapons windows (`0xCD` action 1 paths).
- Shop-open direct `sendSaleItemList` call site (producer chain
  `Player::updateSaleShopList` reachable from inventory changes).
- Weapon-proficiency detail-list client-build gate (parse the build string and
  emit the full shape when it matches).

**Files (owner):** `network/protocol/**`, `game/Game.kt` (login regions only).

**Canary sources:** `src/network/protocol/protocolgame.cpp` (login sequence,
`0xB3`/`0xCD` handlers, `sendSaleItemList` call sites).

**Tests:** login frame tail bytes; tracked-weapons window; sale list emitted on
inventory change with an open shop; detail-list gate both ways.

---

### F4 — Misc bindings

**Scope**
- Bind `Game.reduceAllSpellsCooldownTimer` to
  `PlayerWheel.reduceAllSpellsCooldownTimer` (Gift of Life path).
- Give `Game.getPlayerHelpers` a real source (guild/party helper count) so
  `updatePlayerHelpers` frames stop reporting 0.
- Decide and document the world-manager guild broadcast (per-session host is
  the production path; bind the world manager only if it has a client).

**Files (owner):** `game/Game.kt` (misc regions), `bootstrap/ServerBootstrap.kt`.

**Canary sources:** `src/creatures/players/wheel/player_wheel.cpp:3423`,
`src/game/game.cpp:9690` (`updatePlayerHelpers`).

**Tests:** Gift of Life reduces cooldowns through the binding; helper count
reflects guild/party membership.

---

## 3. Conflict matrix

| File | Owners | Rule |
|---|---|---|
| `game/Game.kt` | F1 (start), F2 (placement), F3 (login), F4 (misc) | localized hunks only |
| `network/CanaryServer.kt` | F1 (loop start), F2 (host binding) | additive hunks |
| `bootstrap/ServerBootstrap.kt` | F1, F4 | additive hunks |
| `network/protocol/**` | F3 | exclusive |

## 4. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch.

## 5. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`
(Phase 73), and this document in place.

## 6. Out of scope

- **The scaling proposals** (`scaling-and-multi-world.md`): login/game service
  split, multi-world schema/routing, Redis coordination — not touched.
- Deferred list unchanged: differential harness; CipSoft860/old protocol;
  OpenTelemetry SDK/OStream; micro-seams.

---

## Completion (2026-10-15)

game-loop-driver (`eaa3fa9e`), monster-host-death (`bef7a1ae`),
login-protocol-tails (`f242f3c3`), misc-bindings (`58d43c05`) merged with zero
conflicts. Integrated suite: **766 suites / 8127 tests, 0 failures, zero Kotlin
warnings**; ancestry verified. Remaining work is engine internals (damage-map/
corpse, cylinder producers), old-protocol frames, dispatcher detail and the
deferred list.
