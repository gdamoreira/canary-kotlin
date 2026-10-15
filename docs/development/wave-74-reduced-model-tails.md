# Wave 74 Plan — Reduced-Model Tails

**Status:** Completed (2026-10-15) — four workstreams merged; 817 suites / 8445 tests green
**Baseline:** `main` @ `8d75a740`; 807 suites / 8388 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 item 2. **Scaling proposals excluded** (phase 1 is wave 75).
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Close the documented reduced-model list so only the structural
`Player`-as-`Cylinder` refactor and the deferred-by-design program remain.

## 2. Workstreams

### M1 — Condition target and MagicField model

**Scope:** production `ConditionTarget` implementation behind
`Creature.conditionTarget` (so condition damage runs the owner-resolved drain in
production); the `MagicField` field model (production instance creation with the
owner stamp, `ownerCreatureProvider` bound at the root, `ItemType::conditionDamage`
application and the harmful-field gates).

**Files:** `combat/**`, `items/**`, `creatures/Creature.kt`, `game/Game.kt` (regions).

**Canary sources:** `src/creatures/combat/condition.cpp:2000-2040`,
`src/creatures/combat/combat.cpp:2590-2641`.

**Tests:** condition damage drains with the owner credited; field creation
stamps the owner; step-in applies the condition damage.

### M2 — World boot tails

**Scope:** `map.spawnsNpc.startup()` + the custom-map spawn loops; `ChatManager`
boot composition (`g_chat().load()`); per-player mount definitions loaded
(`PlayerFeatures`); players-record peak persistence (`Game::updatePlayersRecord`).

**Files:** `creatures/spawns/**`, `chat/**`, `creatures/players/**`,
`game/Game.kt` (record region), `network/CanaryServer.kt`, `bootstrap/**`.

**Canary sources:** `src/game/game.cpp:963-993` (INIT), `src/chat/chat.cpp`.

**Tests:** NPC spawns start on boot; chat loads; mounts resolve per player;
record peaks persist.

### M3 — Livestream broadcaster and state arms

**Scope:** the livestream broadcaster side (`PlayerFunctionsExtra.setLivestreamViewers`
inert → wire so `canWatch` admits); `setGameState` SHUTDOWN/CLOSED/maintain-mode
arms.

**Files:** `creatures/players/livestream/**`, `lua/bindings/core/PlayerFunctionsExtra.kt`,
`game/Game.kt` (state region).

**Canary sources:** `src/creatures/players/livestream/livestream.cpp`,
`src/game/game.cpp` (`setGameState`).

**Tests:** broadcaster toggle admits viewers; SHUTDOWN arm saves/kicks; maintain
mode refuses logins.

### M4 — Combat dispatch tails

**Scope:** `Player::changeMana` broadcast + the `CHARM_VOIDINVERSION` inversion;
`Player::onTargetCreatureGainHealth` virtual dispatch; movement-script hooks
(`onCreatureMove`/`onItemMove`).

**Files:** `creatures/Creature.kt`, `creatures/players/**`, `game/Game.kt`
(regions), `lua/**` (movement bindings only), `map/**` (hook call sites).

**Canary sources:** `src/creatures/players/player.cpp:6765-6772`,
`src/creatures/creature.cpp` (`onCreatureMove`), `src/map/tile.cpp`.

**Tests:** mana broadcast frame; charm inversion halves; gain-health dispatch;
movement hooks fire.

## 3. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch.

## 4. Documentation gate (AGENTS.md §6)

Update `implementation-status.md`, `README.md`, `CHANGELOG.md` (Phase 78), and
this document in place.

## 5. Out of scope

Structural `Player`-as-`Cylinder`; deferred list; scaling phase 1 (wave 75).

---

## Completion (2026-10-15)

condition-field-model, world-boot-tails (`75e624bf`), livestream-state-arms
(`5af2f334`), combat-dispatch-tails (`f6832e62`) merged (one union conflict in
Game.kt resolved); the pending root bindings (move-event hooks, global-event
save/shutdown, livestream Lua ops, login refusal, save seams) were applied at
integration. Integrated suite: **817 suites / 8445 tests, 0 failures, zero
Kotlin warnings**; ancestry verified.
