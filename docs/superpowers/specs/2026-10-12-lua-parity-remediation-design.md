# Design: Lua Parity Remediation

**Date:** 2026-10-12
**Status:** Approved (design) — pending implementation plan
**Supersedes:** `docs/Done/012-scripting-and-event-engine.md`, `docs/Done/028-lua-scripting-engine-bridge.md`

## 1. Problem

The Kotlin port currently **re-implements Canary Lua gameplay as Kotlin code**, violating
`AGENTS.md` §1.1 (Strict Canary Parity) and §1.4 (Lua Scripting Boundary):

- `scripting/ScriptRegistry.kt` — Kotlin lambda registry replacing Lua actions/talkactions.
- `spells/SpellRegistry.kt` — Kotlin `Spell` data class replacing Lua spells.
- `creatures/Npc.kt` — `onCreatureSay` hardcodes dialogue keywords/responses.
- `quest/QuestManager.kt` — Kotlin quest/mission choreography (Canary has no quest C++ class).
- `game/bank/BankService.kt` — `Currency` denominations (Canary: Lua `bank.lua`).
- `social/guild/Guild.kt` — `GuildWar.registerKill` frag/winner logic (Canary: Lua + DB).
- `lua/LuaScriptBridge.kt` — stub that never loads the shipped `.lua` files.

The project ships ~99 `.lua` files under `data-canary/` that are currently **never executed**.

## 2. Goal

Make the shipped `.lua` files the authoritative gameplay source. Kotlin provides only
C++-parity engine primitives plus the Lua binding surface. Lua logic stays Lua.

## 3. Constraints

- `AGENTS.md` §1.1: no invented methods/APIs; names mirror Canary.
- `AGENTS.md` §1.2: world mutations confined to the `GameDispatcher` WorldCommit lane.
- `AGENTS.md` §1.4: Lua files are loaded and executed as Lua; never translated.
- `AGENTS.md` §4: TDD — failing test first; `./gradlew test` green (Java 25) before commit.
- Runtime: **LuaJ 3.0.1** (pure JVM, Lua 5.2 dialect). Canary runs LuaJIT (Lua 5.1);
  dialect gaps are shimmed in a compat Lua lib.

## 4. Architecture

New package root `org.opentibia.canary.lua`.

### 4.1 `runtime/LuaEnvironment`  (mirrors Canary `LuaScriptInterface`)
- Owns a LuaJ `Globals` created once at boot.
- `initState()`: preloads compat + core libs, registers all binding libraries.
- `loadFile(Path)` / `loadChunk(String, chunkName)`: compile + execute, tracking the
  current script id for diagnostics.
- `LuaError` carries chunk name + line, mirroring Canary error reporting.

### 4.2 `loader/Scripts`  (mirrors Canary `Scripts::loadScripts`)
- Scans data roots in a deterministic order and `dofile`s each `.lua`.
- Order: `data/libs/libs.lua` → `data-canary/lib/lib.lua` → per-folder scripts
  (`scripts/actions`, `scripts/talkactions`, `scripts/spells`, `scripts/movements`,
  `scripts/creaturescripts`, `scripts/globalevents`, `scripts/npcs`, `monster`, `npc`).
- Sets globals `DATA_DIRECTORY`, `CORE_DIRECTORY`, `configKeys`.
- `clearAllScripts()` for reload.

### 4.3 `bindings/*`  (mirrors Canary `lua_functions_loader`)
One Kotlin file per Canary binding group, registering LuaJ `LibFunction`s with the
**exact Canary Lua API names**, delegating to Kotlin engine primitives:

- `GameFunctions` (`Game.createItem`, `Game.getPlayers`, `Game.getConfigNumber`, …)
- `PlayerFunctions` (`addItem`, `removeItem`, `getItemCount`, `addSkillTries`,
  `sendTextMessage`, `addStorageValue`, `getStorageValue`, …)
- `CreatureFunctions` (`getHealth`, `addHealth`, `getPosition`, …)
- `ItemFunctions` (`transform`, `decay`, `getId`, `getAttribute`, …)
- `ContainerFunctions`, `TileFunctions`, `PositionFunctions` (`sendMagicEffect`),
  `MapFunctions`, `NpcFunctions`, `MonsterFunctions`
- Registration constructors: `ActionFunctions`, `TalkActionFunctions`,
  `SpellFunctions`, `RuneSpellFunctions`, `GlobalEventFunctions`,
  `MoveEventFunctions`, `CreatureEventFunctions`, `EventCallbackFunctions`.

### 4.4 `userdata/*`
Per-type userdata wrappers with a metatable registry named `"Player"`, `"Monster"`,
`"Npc"`, `"Item"`, `"Container"`, `"Teleport"`. `__index` resolves Canary method
names. `rawgetmetatable` (LuaJIT-only) is shimmed in the compat lib. The
`get`/`set` auto-generation in `revscriptsys.lua` works against these method tables.

### 4.5 `registry/*`
Canary-parity registries populated by script constructors:
`Actions`, `TalkActions`, `Spells`, `Runes`, `GlobalEvents`, `MoveEvents`,
`CreatureEvents`. `register()` returns an int id like Canary.

### 4.6 Lua libs
Ship Canary's `data/libs/**` (`libs.lua`, `functions/*`, `tables/*`, `compat/*`,
`systems/*`, `core/*`) and `data-canary/lib/**` unmodified, plus one small
`compat/luaj_shim.lua` for Lua 5.1/5.2 and `rawgetmetatable` gaps.

## 5. Data Flow

1. Boot: `Scripts.loadScripts()` → `LuaEnvironment.initState()` → libs → scripts.
2. Scripts register handlers into `registry/*` keyed by Canary ids/words.
3. Client packets reach opcode handlers on Netty threads; they
   `dispatcher.dispatch(DispatcherLane.WorldCommit)` a closure.
4. WorldCommit invokes the registry handler, which calls back into Lua via the
   stored `LuaValue` function; the Lua code calls engine primitives through bindings.
5. Engine primitives mutate world state (already confined to WorldCommit).

## 6. Concurrency

- Loader runs once, single-threaded, before traffic.
- All Lua event dispatch occurs inside the `GameDispatcher` WorldCommit lane
  (`AGENTS.md` §1.2). No Lua call executes on a Netty worker thread.
- `LuaEnvironment`/`Globals` is accessed only from WorldCommit (and boot) after load.

## 7. Kotlin Deletions (Lua-layer logic only; primitives stay)

| Remove | Keep |
|--------|------|
| `scripting/ScriptRegistry.kt` | — |
| `spells/SpellRegistry.kt` Kotlin `Spell` type | spell engine primitives |
| `Npc.onCreatureSay` keyword body | `Npc` creature engine, `setPlayerInteraction` |
| `quest/QuestManager.kt` mission logic | `Player.addStorageValue/getStorageValue` |
| `BankService.Currency` denominations | `Bank` `credit`/`debit`/`balance`/`withdraw`/`deposit`/`transferTo` primitives (mirror `src/game/bank/bank.hpp`) |
| `Guild.GuildWar.registerKill` | `Guild` membership/rank primitives |

Corresponding tests (`NpcDialogueTest`, `QuestStorageTest`, `BankSystemTest`,
`GuildWarTest`, `ScriptingEngineTest`, `SpellSystemTest`) are rewritten to load the
real `.lua` files.

## 8. Migration Order

1. `LuaEnvironment` + compat lib + `Scripts` loader (no gameplay yet).
2. Registration constructors + one vertical slice: **actions** (`fishing.lua`).
3. Talkactions (`!online`, `!balance`).
4. Spells.
5. NPC dialogue/shop (`data-canary/npc`, `npclib`).
6. Quests (`data-canary/scripts/actions/quests`, `lib/core/quests`).
7. Bank, guild war, movements, creature/global events.
8. Delete each Kotlin counterpart as its Lua path goes green.

## 9. Testing

- `LuaEnvironmentTest`: compile/execute a chunk; error reporting.
- `ScriptsLoaderTest`: loads real files; asserts registration counts/ids.
- Integration tests load real `.lua` and assert engine effects (e.g. `fishing.lua`
  registered action grants a fish to `player`).
- Primitive bindings unit-tested directly.
- `./gradlew test` must be green on Java 25 before each commit.

## 10. Risks

- **LuaJ dialect gaps** (LuaJIT 5.1 vs LuaJ 5.2): mitigated by compat shim; discovered
  gaps are added to the shim as they appear.
- **Binding surface size** (Canary has ~53 binding source files): grown incrementally,
  only what the shipped scripts require.
- **`rawgetmetatable` absence in LuaJ**: provided by the shim.

## 11. Success Criteria

- The shipped `.lua` files execute through the loader and drive gameplay.
- No gameplay decision encoded in Kotlin that Canary authors in Lua.
- All remaining Kotlin domain methods correspond to Canary C++ (`src/`) counterparts.
- `./gradlew test` green on Java 25; each migration step has a test that ran RED then GREEN.
- `docs/Done/012` and `028` marked superseded by this design.
