# Wave 67 Plan — Combat Depth, Monster Tails, Login Bindings, Spell Consumers

**Status:** In progress — dispatched
**Baseline:** `main` @ `f1258f31`; 733 suites / 7979 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 items 1–3.
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Close the remaining combat/monster depth tails and the login/boot bindings, so
only the deliberately deferred list (differential harness, old protocol, OTel,
micro-seams) remains.

## 2. Workstreams

### D1 — Combat depth

**Scope**
- ExpertPvp field swaps + fight-tick branch; wheel beam mastery; per-creature
  `getCombatDamage` override; `applyExtensions` copy semantics.
- Adapter stubs: `isImmuneCondition` (monster immunity bitset), `isEnemyFaction`,
  `getMantra` (player mantra state).
- `PlayerSpellCaster` side effects: mana-spent progression, aggressive-action
  clock, cancel-message builder.
- `Monster::getCombatValues`; `combatTileEffects` COMBAT_NONE field path.

**Files (owner):** `combat/**`, `spells/**` (engine only), `creatures/players/pvp/**`,
`creatures/monsters/**` (combat bits).

**Canary sources:** `src/creatures/combat/combat.cpp`, `src/creatures/combat/spells.cpp`,
`src/creatures/players/player.cpp` (mantra, aggressive clock),
`src/creatures/monsters/monster.cpp` (`getCombatValues`).

**Tests:** mantra absorb path; immunity bitset denies a condition; expert-PvP
field swap; mana-spent progression; COMBAT_NONE field placement.

---

### D2 — Monster/map tails and Lua creature bindings

**Scope**
- `Monster::isPushable` (`info.pushable && baseSpeed != 0`) used by the Tile push
  loop; `creatureOnThink`/`creatureOnDeath` production call sites (player loops).
- Raid and `Game.createMonster` `onSpawn` call sites; exact spawn dispatch order.
- Lua `creature:isMoveLocked()`/`setMoveLocked()` bindings.

**Files (owner):** `creatures/Creature.kt`, `creatures/monsters/**`,
`creatures/spawns/**`, `game/Game.kt` (monster think/death/createMonster regions),
`lua/bindings/**` (creature bindings only).

**Canary sources:** `src/creatures/monsters/monster.cpp`,
`src/game/game.cpp` (`createMonster`), `src/game/raids/raids.cpp:408`.

**Tests:** unpushable monster rejects push; player think/death dispatch attached
events; raid spawn fires onSpawn; Lua move-locked round-trip.

---

### D3 — Login bindings

**Scope**
- Player placement at login (`ProtocolGame::login` → `placeCreature`).
- Login VIP burst (`sendVIPGroups` + one `sendVIP` per entry after login).
- `Groups::load` over `data/XML/groups.xml` + bootstrap binding; `CanaryServer`
  guild repository (offline guild loads); `SaveManager.savePlayer` production
  binding + `Game.playerVip`.

**Files (owner):** `network/protocol/GameHandler.kt`, `network/CanaryServer.kt`,
`bootstrap/ServerBootstrap.kt`, `io/xml/**` (groups loader), `game/Game.kt`
(placement region), `creatures/players/grouping/**` (Groups registry).

**Canary sources:** `src/network/protocol/protocolgame.cpp:1056, 8747-8777`,
`src/creatures/players/grouping/groups.cpp:78-169`, `src/io/iologindata.cpp`.

**Tests:** login places the player on the map (spectators see the appear); VIP
burst frames on login with a persisted list; groups.xml loads and login resolves
the group; savePlayer bound.

---

### D4 — Spell consumers and misc call sites

**Scope**
- Re-bind spell-registry consumers: `WorldPlayerSocialOperations.canLearnSpell`/
  `getInstantSpells`, `PlayerWheel` default `Spells()`, basic-data spell list,
  look rune seam; `WorldLuaOperations.attachCombat` boot call.
- Modal window sink (`sendModalWindow`) + the `0xF9` route; shop-owner
  composition; Lua `player:registerEvent`.

**Files (owner):** `lua/runtime/WorldLuaOperations.kt`, `lua/bindings/**`
(PlayerFunctions/CreatureFunctions/SpellBindings consumers), `network/protocol/**`
(modal sink), `game/Game.kt` (shop-owner region), `bootstrap/ServerBootstrap.kt`
(its own additive hunk).

**Canary sources:** `src/lua/functions/core/game/game_functions.cpp`,
`src/network/protocol/protocolgame.cpp` (`sendModalWindow`),
`src/creatures/players/player.cpp` (`registerEvent`, shop owner).

**Tests:** canLearnSpell consults the registry; wheel sees the registry; modal
sink frames + 0xF9 route; shop-owner resolves; Lua player:registerEvent attaches.

---

## 3. Conflict matrix

| File | Owners | Rule |
|---|---|---|
| `bootstrap/ServerBootstrap.kt` | D3, D4 | additive hunks |
| `network/CanaryServer.kt` | D3 | D4 uses the bootstrap hunk |
| `game/Game.kt` | D2 (monster regions), D3 (placement), D4 (shop-owner) | localized hunks |
| `lua/bindings/**` | D2 (creature), D4 (player/spell consumers) | disjoint files where possible |
| `combat/**`, `spells/**` engine | D1 | D4 touches consumer call sites only |

## 4. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch.

## 5. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`
(Phase 70), and this document in place.

## 6. Out of scope (final deferred list)

Live differential harness; CipSoft860/old-protocol branches; OpenTelemetry
SDK/OStream; micro-seams (KV LRU ordering, `DEBUG_LOG`, daemon flags,
`TransportCodec` tails).
