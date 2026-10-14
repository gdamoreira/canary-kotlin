# Wave 58 Plan — Combat Spell Actor, Host/Lua Unification, Known-Creature Set, Codec Fidelity

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 combat-spell-actor | `lua/bindings/MonsterSpellFunctions.kt`, `lua/bindings/MonsterTypeFunctions.kt`, `creatures/monsters/**`, `bootstrap/**` (monster boot region) |
| A2 host-lua-unification | `creatures/Creature.kt`, `lua/runtime/WorldLuaOperations.kt`, `lua/bindings/core/PlayerFunctions.kt`, `io/**` (animus blob call site) |
| A3 known-creature-set | `network/protocol/**`, `creatures/players/PlayerSendSink.kt` |
| A4 codec-fidelity | `io/**`, `database/ItemAttributeCodec.kt`, `resources/db/migration/**` |

## Tasks

- **A1** port the `CombatSpell` actor for monster attack/defense blocks (resolve `sb.spell` into the ported `MonsterSpell`/spell model), nested `childLoot` in loot blocks, and capture inline `mtype.onSpawn`/`onThink` callbacks for live monsters.
- **A2** host `Player::storage()`/`getOutfits`/`getBankBalance` accessors; make the Lua storage/outfit shadow maps delegate to the composed `PlayerFeatures` state; wire the animus blob persistence call sites.
- **A3** session known-creature set (`checkCreatureAsKnown`, 1300-entry with canSee rules) so party/creature sends emit known-creature frames instead of fallbacks.
- **A4** condition codec fidelity (persist flat BUFFS/ABSORBS/INCREASES + PERSISTENT) and the `lookmount*` outfit columns (migration V20).

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push).

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: CombatSpell actor + nested loot + inline callbacks, host/Lua unification (storage/outfits/animus), known-creature set, condition codec + lookmount schema (V20). 685 suites / 7577 tests.
