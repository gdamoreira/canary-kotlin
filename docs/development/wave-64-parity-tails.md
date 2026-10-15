# Wave 64 Plan — Parity Tails: Persistence, Guild/Save, Combat Host, Events, Renderer, Tile

**Status:** Completed (2026-10-15) — six workstreams merged; 718 suites / 7905 tests green
**Baseline:** `main` @ `b8e619f8`; 706 suites / 7817 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 (remaining ranked tails after wave 63 + the wave-58 recovery).
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Close the call-site and depth tails left after wave 63 so the port's remaining
gaps shrink to the deliberately deferred list (differential harness, old
protocol profiles, OTel SDK, micro-seams). Same gates: §1.1 strict parity, §1.4
Lua stays Lua, §4 TDD, zero warnings.

## 2. Workstreams

### A1 — VIP/Exiva composition and monster race lookup

**Scope**
- Compose `PlayerVip` into `Player` and give `Player.kv` the full `kv.ValueWrapper`
  store so `loadPlayerVip`/`loadPlayerExivaRestrictions` are no longer inert.
- Port `Monsters::getMonsterTypeByRaceId` (monsters.cpp:375-388) and wire the
  `monsterTypeByRaceId` seam so persisted bestiary race ids resolve at load.

**Files (owner):** `creatures/players/vip/**`, `creatures/players/PlayerFeatures.kt`,
`creatures/players/**` (kv facade), `io/iologindata/IOLoginData.kt`,
`io/iologindata/IOLoginDataSubloads.kt`, `io/iologindata/IOLoginDataSubloads2.kt`,
`creatures/monsters/Monsters.kt`, `kv/**`.

**Canary sources:** `src/io/iologindata_load_player.cpp` (`loadPlayerVip`,
`loadPlayerExivaRestrictions`), `src/creatures/monsters/monsters.cpp:375-388`.

**Tests:** VIP list/nick round-trip at login; exiva restrictions load; race-id
lookup returns the registered monster type (and null for unknown).

---

### A2 — Guild registration and save registration

**Scope**
- Register a guild loaded at login into the listener's `Game.guilds` so two
  members share one `Guild` instance (`Game::addGuild`, game.cpp).
- Wire `SaveManager.registerOnlinePlayer`/`unregisterOnlinePlayer` at session
  install/removal so online players enter the save rotation
  (save_manager.cpp:150-171).
- Wire the `Game.savePlayer`/`loadPlayerById` seams if the above requires them.

**Files (owner):** `social/guild/**`, `game/scheduling/SaveManager.kt`,
`network/CanaryServer.kt`, `game/Game.kt` (guild/save regions only).

**Canary sources:** `src/game/game.cpp` (`addGuild`, `savePlayer`),
`src/game/scheduling/save_manager.cpp:150-171`.

**Tests:** two logins of the same guild share one instance; register/unregister
called on session lifecycle; save rotation includes a registered player.

---

### A3 — Combat host wiring and spell tails

**Scope**
- Production `CombatTarget` adapter so player spell damage fires
  `onAttackedByPlayer` on monsters (the wave-63 core seam is ready).
- `InstantSpell::canThrowSpell` range/sight via a `Game::canThrowObjectTo` seam.
- `Combat::CombatFunc` consumption tail: spectators, `targetCasterOrTopMost`,
  tile/target callbacks, tile effects, `postCombatEffects`.
- Skill operand push for `params.valueCallback` (combat.cpp:2017-2027).
- Populate `getAimAtTargetSpells` from the composed state where reachable.

**Files (owner):** `combat/**`, `spells/**`, `game/Game.kt` (combat/spell
regions only — keep edits localized).

**Canary sources:** `src/creatures/combat/combat.cpp:895-899, 1645-1713,
2017-2027`, `src/creatures/combat/spells.cpp` (`canThrowSpell`).

**Tests:** spell hit fires onPlayerAttack; canThrowSpell rejects out-of-range;
area dispatch consumes the target list; skill operand reaches the callback.

---

### A4 — Event filtering, spawn onSpawn, turn transport

**Scope**
- `ScriptingDispatch` walks the per-creature attached event set (wave-63 left
  global dispatch) and validates names against the registry.
- Inject `creatureEventRegistered` at boot (`CanaryServer`), so unknown event
  names are rejected in production.
- Call `MonsterPipelinesHost.onSpawn` from the spawn path
  (`SpawnMonster`/`GameMonsterFactory`).
- Wire `Game.sendCreatureTurn` in the composition so `internalCreatureTurn`
  reaches the player sink; give non-`PlayerSendSink` transports the appear
  fallback parity where practical.

**Files (owner):** `lua/runtime/ScriptingDispatch.kt`, `creatures/spawns/**`,
`network/CanaryServer.kt`, `game/Game.kt` (turn region only),
`network/protocol/ProtocolFrameSink.kt`.

**Canary sources:** `src/lua/creature/creatureevent.cpp:23`,
`src/creatures/monsters/monster.cpp:627-648`, `src/creatures/players/player.cpp:8809`.

**Tests:** per-creature filtering with two monsters/events; unknown name
rejected when the seam is injected; spawn calls onSpawn once; turn reaches the
sink.

---

### A5 — Renderer seams and Lua/plural descriptions

**Scope**
- Pass the `Item::getDescription` seams (`Item::items`, `g_spells()`,
  `g_vocations()`, `Game::getObjectCategory`) where the look path is built, so
  fluid/splash/rune/imbuement text stops degrading.
- Wire Lua `item:getDescription(distance)` to the renderer (currently `""`).
- Port the plural `Item::getDescriptions` (inspection-window variant) and the
  shop look path (`Game.playerLookInShop` static-renderer seam).

**Files (owner):** `items/**`, `lua/bindings/core/ItemFunctions.kt`,
`network/protocol/LookPacketHandler.kt`, the handler/router construction site,
`game/Game.kt` (shop look region only).

**Canary sources:** `src/items/item.cpp:2554, 3352`, `src/items/thing.hpp`.

**Tests:** fluid/rune/imbuement look text with seams; Lua description equals
the renderer; plural descriptions for an inspection list; shop look text.

---

### A6 — Tile.queryAdd depth

**Scope**
- `Tile::queryAdd` tails: monster push/field rules, player account/PZ-lock
  step, expert-PvP step, `Creature::isMoveLocked` gate (tile.cpp:850-911).

**Files (owner):** `map/Tile.kt`, `creatures/Creature.kt` (isMoveLocked only).

**Canary sources:** `src/map/tile.cpp:850-911`, `src/creatures/creature.hpp`.

**Tests:** field blocks placement; PZ-lock denies; expert-PvP step returns the
C++ value; move-locked creature cannot be pushed.

---

## 3. Conflict matrix

| File | Owners | Rule |
|---|---|---|
| `game/Game.kt` | A2 (guild/save), A3 (combat/spell), A4 (turn), A5 (shop look) | minimal localized hunks only; no reformatting; merge resolves disjoint regions |
| `network/CanaryServer.kt` | A2 (save registration), A4 (event injection) | keep edits to the respective wiring blocks |
| `io/iologindata/**` | A1 | A2 stays out |
| `creatures/players/**` | A1 | A6 touches `Creature.kt` only |

## 4. Commit strategy

One feature commit per workstream (TDD, focused tests, full suite, zero
warnings), merged sequentially into `main`; one documentation commit after.

## 5. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero Kotlin warnings; record real suite/test counts. Confirm
every branch commit is an ancestor of `main` before recording completion
(the wave-58 lesson: `git merge-base --is-ancestor`).

## 6. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md` (§1 metrics, §3 tails),
`README.md` metrics, `CHANGELOG.md` (Phase 67), and this document in place.

## 7. Out of scope (unchanged)

Live differential harness; CipSoft860/old-protocol branches; OTel SDK/OStream;
remaining micro-seams (KV LRU ordering, `DEBUG_LOG`, daemon flags,
`TransportCodec` tails).

---

## Completion (2026-10-15)

All six workstreams landed and merged with zero conflicts: vip-exiva
(`a16d4da8`), guild-save (`38f26cf4`), combat-host (`50df4b83`), events-transport
(`3363a09d`), renderer-seams (`f34c8c08`), tile-queryadd (`93a9658c`).
Integrated suite: **718 suites / 7905 tests, 0 failures, zero Kotlin
warnings**; ancestry verified per commit. Remaining tails recorded in
`implementation-status.md` §3 (composition bindings, persistence save tails,
combat depth, monster/map tails).
