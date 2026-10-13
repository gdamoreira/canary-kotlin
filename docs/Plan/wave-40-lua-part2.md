# Wave 40 Plan — `src/lua/` Part 2

**Status:** In progress
**Process:** AGENTS.md §6. Tracking: `src/lua/` section (~420/1,246 methods after wave 39).

## File ownership

| Agent | Owns |
|---|---|
| A1 lua-player-3 | `bindings/core/PlayerFunctions.kt` |
| A2 lua-player-4 | `bindings/core/PlayerFunctionsExtra.kt` (new) |
| A3 lua-monster-npc | `bindings/MonsterFunctions.kt`, `bindings/NpcFunctions.kt` (new), `bindings/NpcTypeFunctions.kt` (new) |
| A4 lua-weapon-spell | `bindings/WeaponFunctions.kt` (new), `bindings/SpellFunctions.kt` (new) |
| A5 lua-libraries | `bindings/library/**` (new) |
| A6 lua-map-items | `bindings/map/**` (new), `bindings/items/**` (new) |
| A7 lua-creatures | `bindings/creatures/**` (new) |
| A8 lua-events | `bindings/events/**` (new) |

Each agent adds its own install line(s) in `lua/runtime/WorldLuaOperations.kt` (one contiguous block each, marked `// Wave 40 <agent>`); conflicts are resolved by union.

## Tasks

- **A1** `player_functions.cpp` batch 3 (~95): combat/targeting, spells/runes, death/health, walk/position, misc.
- **A2** `player_functions.cpp` batch 4 (~96): remaining groups not in batch 1–3.
- **A3** `monster_functions.cpp` (51), `npc_functions.cpp`, `npc_type_functions.cpp` (40), `shop_functions.cpp`, `charm_functions.cpp`, `monster_spell_functions.cpp`.
- **A4** `weapon_functions.cpp` (31), `spell_functions.cpp` (39), `variant_functions.cpp`, `condition_functions.cpp`, `combat_functions.cpp`.
- **A5** `core/libs/*` (db, kv, logger, metrics, result), `core/network/*` (network_message, webhook), `core/game/*` remaining (bank, batch_update, config, global, modal_window, zone), `lua_enums.cpp`.
- **A6** `map/*` (house, position, teleport, town), `items/*` remaining (container, imbuement, item_classification).
- **A7** `creatures/player/*` remaining (group, guild, mount, party, vocation).
- **A8** `events/*` remaining (action, creature_event, event_callback, events_scheduler, global_event, move_event, talk_action) — verify against existing ActionBindings/CreatureEventBindings/GlobalEventBindings/MoveEventBindings/TalkActionBindings and port only the missing.

## Rules

- Port only real Canary registrations; exact names/arg order; `lua_pushnil` guards.
- TDD: LuaJ tests through `LuaEnvironment` per batch (RED first).
- Build: `./gradlew test --tests "*Functions*"` then full suite once; zero warnings.
- Commit on your branch (no merge/push); report exact methods ported/remaining and gaps.

## Post-wave (§6)

Update the `src/lua/` rows and §1–§3; update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.
