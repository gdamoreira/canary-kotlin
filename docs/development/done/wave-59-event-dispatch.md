# Wave 59 Plan — Event Dispatch, Spell Registry, Condition Save, Visibility

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 monster-event-dispatch | `creatures/monsters/**`, `bootstrap/**` (boot region), `lua/bindings/core/LuaEnums.kt` |
| A2 spell-registry-actor | `lua/bindings/SpellBindings.kt`, `lua/bindings/MonsterSpellFunctions.kt`, `spells/**` |
| A3 condition-save | `io/**`, `resources/db/migration/**` (V21) |
| A4 visibility-ghost | `creatures/Creature.kt`, `network/protocol/creature/**`, `network/protocol/PlayerSendSink.kt` |

## Tasks

- **A1** `registerEvent` pushes registered events to already-spawned monsters; dispatch `onAppear`/`onDisappear`/`onMove`/`onSay`/`onPlayerAttack`; load `data/libs/functions/revscriptsys.lua` at boot; register `MONSTERS_EVENT_*` Lua enums.
- **A2** wire `sb.spell = g_spells().getSpellByName(name)` (compiled spell registry hit) in `deserializeSpell`.
- **A3** port the condition save path (`iologindata_save_player.cpp:279-291`); single-column Canary animus schema; bind the storage persistence call sites in the composition root.
- **A4** `Player::canSeeCreature` override (ghost + access, invisibility); `sendCreatureSquare` canSee gate; `sendCreatureAppear` stackpos→`sendUpdateTile` fallback.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push).

## Post-wave (§6)

Update §1–§3, `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: monster event dispatch (14), spell registry hit, condition save + V21 animus column, visibility/ghost parity. 689 suites / 7620 tests.
