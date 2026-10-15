# Wave 71 Plan — Damage Attribution, Death Tails, Producer/Transport, Raids

**Status:** In progress — dispatched
**Baseline:** `main` @ `c80a5b52`; 779 suites / 8200 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 items 1–3. **Scaling proposals excluded.**
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Close the remaining engine tails so combat kills populate the damage map, the
death pipeline carries its full payload, and the last producer/transport/raid
call sites are wired.

## 2. Workstreams

### H1 — Damage attribution

**Scope**
- Thread the attacker through `Combat`/`CombatTarget.drainHealth` →
  `Creature::onAttackedCreatureDrainHealth` → `addDamagePoints` so the wave-70
  death pipeline receives a populated damage map for combat kills.

**Files (owner):** `combat/**`, `creatures/Creature.kt`, `game/Game.kt` (combat
regions).

**Canary sources:** `src/creatures/combat/combat.cpp` (`drainHealth`),
`src/creatures/creature.cpp` (`onAttackedCreatureDrainHealth`,
`addDamagePoints`).

**Tests:** combat damage records the attacker; last-hit/most-damage resolve
after a kill; summon/master attribution matches C++.

---

### H2 — Death tails

**Scope**
- `Creature::deprecatedOnKilledCreature`'s Lua `onKill` host.
- Player corpse: description/container + the PvP `lastHitIsPlayer`
  short-circuit.
- `dropCorpse` protocol tail: reward-corpse flag, loot message, auto-loot,
  container update; `MESSAGE_EXPERIENCE_OTHERS`; `player_death`/`monster_death`
  metric counters.

**Files (owner):** `creatures/Creature.kt`, `creatures/monsters/**`,
`creatures/players/**`, `game/**` (death regions), `network/protocol/**`
(corpse/experience messages), `monitoring/**` (counters).

**Canary sources:** `src/creatures/creature.cpp:786-828,1290-1327`,
`src/creatures/players/player.cpp` (`dropCorpse`, `onKilledCreature`).

**Tests:** Lua onKill fires with the killer; player corpse created; reward
flag/loot message emitted; counters increment.

---

### H3 — Producer/transport tails

**Scope**
- Source-cylinder `postRemoveNotification` for container/tile removals.
- `Player::queryRemove`/`Item::canRemove` gates on inventory removal.
- `updateItemsLight` composition; Lua `addItemEx` producer bypass.
- Livestream-viewer `0xCA` resend; `proficiencies.json` injection for `0xB3`
  validation.

**Files (owner):** `creatures/players/**`, `items/**`, `game/Game.kt`
(notification region), `network/protocol/**`, `bootstrap/ServerBootstrap.kt`,
`lua/runtime/WorldLuaOperations.kt`.

**Canary sources:** `src/game/game.cpp:2659` (source tile removal),
`src/creatures/players/player.cpp` (`queryRemove`), `src/items/item.cpp`
(`canRemove`), `src/network/protocol/protocolgame.cpp` (0xCA resend).

**Tests:** container removal emits the source refresh; queryRemove denies a
protected item; items-light update fires; 0xB3 selection validates against the
injected registry.

---

### H4 — Raids composition

**Scope**
- Compose raids at the server root: `Raids.startup()`/`loadFromXml`, the
  announcer, script-runner and clock bindings; wire `Game.startRaid` so raid
  events actually run through the wave-70 `GameRaidSpawner` bridge.

**Files (owner):** `systems/raid/**`, `bootstrap/ServerBootstrap.kt`,
`network/CanaryServer.kt`, `game/Game.kt` (raid region).

**Canary sources:** `src/game/raids/raids.cpp` (`startup`, announcer, clock),
`src/game/game.cpp` (`startRaid`).

**Tests:** boot composes raids; a raid starts on the clock; announcer messages
emitted; `startRaid` returns success for a loaded raid.

---

## 3. Conflict matrix

| File | Owners | Rule |
|---|---|---|
| `game/Game.kt` | H1 (combat), H2 (death), H3 (notification), H4 (raid) | localized hunks |
| `creatures/Creature.kt` | H1, H2 | localized hunks |
| `bootstrap/ServerBootstrap.kt` | H3, H4 | additive hunks |
| `network/protocol/**` | H2 (messages), H3 (0xCA) | disjoint files |
| `creatures/players/**` | H2 (corpse), H3 (queryRemove) | disjoint files |

## 4. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch.

## 5. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`
(Phase 75), and this document in place.

## 6. Out of scope

Scaling proposals; deferred list (differential harness, CipSoft860 profile,
OTel SDK/OStream, micro-seams).
