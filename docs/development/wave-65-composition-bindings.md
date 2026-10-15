# Wave 65 Plan — Composition Bindings, VIP Protocol, Spell Boot, Lua Entry Points, Shop/Inspection, CI

**Status:** In progress — dispatched
**Baseline:** `main` @ `de9e9270`; 718 suites / 7905 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 items 1–2 (composition bindings + protocol call sites).
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Turn the wave-63/64 seams **live in production**: bind them at the composition
root, add the missing VIP protocol senders, boot the engine spell registry, route
the Lua spell entry points through the combat core, and wire the shop/inspection
call sites. Same gates: §1.1, §1.4, §4 TDD, zero warnings.

## 2. Workstreams

### B1 — Composition bindings

**Scope**
- `ServerBootstrap`/`CanaryServer`: pass `Monsters` into `IOLoginData`/`IOLoginDataSubloads2`; inject `VipRepository` into `PlayerFeatures`; rebind `Player.kv` to the scoped `g_kv` store; bind the Tile `queryAdd` seams from `Game`/`MonsterPipelinesHost`; bind `Game.saveGuild`.
- Player-side event registration: `player:registerEvent` binding so players/NPCs stop using the permissive global fallback; modal manager gets the `scripting` dispatch.
- Verify the wave-64 `creatureEventRegistered`/`onSpawn`/turn injections stay correct after the additions.

**Files (owner):** `bootstrap/ServerBootstrap.kt`, `network/CanaryServer.kt`, `network/protocol/GameHandler.kt`.

**Canary sources:** `src/canary_server.cpp` (composition), `src/game/game.cpp` (`addGuild`, `saveGuild`), `src/map/tile.cpp` seam consumers.

**Tests:** boot composition passes `Monsters`/VIP repo/kv/Tile seams; bestiary race ids resolve in the production login path; kv writes land in the scoped store; saveGuild bound.

---

### B2 — VIP protocol senders

**Scope**
- Add `ProtocolGame::sendVIP`, `sendVIPGroups`, `sendUpdatedVIPStatus` to the sink surface (`ProtocolSink`/`PlayerSendSink`/`PlayerFrameSink` + in-memory transport) and wire `PlayerVip`'s client seam to them.

**Files (owner):** `network/protocol/**` (VIP writers + sink), `creatures/players/vip/PlayerVip.kt` (client seam only).

**Canary sources:** `src/network/protocol/protocolgame.cpp` (`sendVIP` 0xD2/0xD3/0xD4 family), `src/creatures/players/vip/player_vip.cpp`.

**Tests:** frames for list/groups/status updates; `PlayerVip` calls the sink through the seam.

---

### B3 — Engine spell registry boot

**Scope**
- Compose the engine `org.opentibia.canary.spells.Spells` registry at boot and populate it from the datapack spell scripts (the Lua registration path), so `getSpellByName` resolves compiled `InstantSpell`/`RuneSpell` actors and rune/vocation look text stops degrading.

**Files (owner):** `spells/**`, `lua/bindings/SpellBindings.kt`, `bootstrap/ServerBootstrap.kt` (its own wiring hunk only — B1 owns the rest).

**Canary sources:** `src/creatures/combat/spells.cpp` (`Spells::getSpellByName`), `src/lua/functions/creatures/combat/spell_functions.cpp`.

**Tests:** boot loads `data/spells` (or the test datapack) into the registry; `getSpellByName` returns the registered actor; monster `sb.spell` resolves to the compiled actor.

---

### B4 — Lua spell entry points

**Scope**
- Route `WorldCombatOperations.execute`, `GlobalFunctions.doTargetCombat*` and `MonsterSpellFunctions`' `canThrowSpell` TODO through the wave-64 combat core (adapter, `canThrowSpell`, area dispatch) instead of the legacy inert paths.

**Files (owner):** `lua/runtime/WorldLuaOperations.kt`, `lua/bindings/**` (combat/spell bindings only).

**Canary sources:** `src/lua/functions/core/game/game_functions.cpp` (`doTargetCombat*`), `src/lua/functions/creatures/monster/monster_spell_functions.cpp`.

**Tests:** Lua `doTargetCombatHealth`/`doAreaCombat*` fire through the adapter; `canThrowSpell` binding reflects the range/sight gate.

---

### B5 — Shop look and inspection window

**Scope**
- Wire shop opcode `0x79` to `Game.playerLookInShop` with the static renderer seams; port `Game.playerInspectItem`/`sendItemInspection` (inspection window consuming the plural `Item::getDescriptions`).

**Files (owner):** `network/protocol/GamePacketRouter.kt` (shop region), `game/Game.kt` (shop/inspection regions), `network/protocol/InspectionWindowWriter.kt`.

**Canary sources:** `src/game/game.cpp` (`playerLookInShop`, `playerInspectItem`), `src/network/protocol/protocolgame.cpp` (`sendItemInspection`).

**Tests:** shop look emits the renderer text with seams; inspection window lists the plural descriptions.

---

### B6 — CI hardening

**Scope**
- De-flake `ImbuementDecaySchedulerTest` (order/clock race under clean-build load) with a deterministic barrier/clock seam.
- Upgrade Actions: `actions/checkout@v5`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v5` (or current), `actions/upload-artifact@v5`; pin `runs-on` to `ubuntu-24.04` until the Ubuntu 26 migration is verified.

**Files (owner):** `.github/workflows/**`, the flaky test + its scheduler code.

**Tests:** repeated clean runs of the focused suite (5×) plus the full suite; workflows validated by YAML parse.

---

## 3. Conflict matrix

| File | Owners | Rule |
|---|---|---|
| `bootstrap/ServerBootstrap.kt` | B1 (main wiring), B3 (spells hunk) | additive hunks only; merge resolves |
| `game/Game.kt` | B5 (shop/inspection regions only) | keep localized |
| `network/protocol/**` | B2 (VIP), B5 (router shop region) | disjoint files where possible |
| `creatures/players/vip/**` | B2 | B1 injects the repo via the composition root only |

## 4. Commit strategy

One feature commit per workstream (TDD, focused tests, full suite, zero
warnings), merged sequentially; ancestry-verified before the docs commit.

## 5. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; record real suite/test counts;
`git merge-base --is-ancestor` per branch.

## 6. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md` (§1 metrics, §3),
`README.md` metrics, `CHANGELOG.md` (Phase 68), and this document in place.

## 7. Out of scope (waves 66–67)

Persistence save tails (logout tail, exiva save, save-all savers) → wave 66;
combat depth + monster/map tails → wave 67; differential harness and old
protocol/OTel stay deferred.
