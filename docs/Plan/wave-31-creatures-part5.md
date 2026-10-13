# Wave 31 Plan — `src/creatures/` Part 5 (Player runtime 2, Spells/Mounts, Team Finder/Guild, Components)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/creatures/` part 5.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `players/player.cpp` chunk 2 | PARTIAL | IMPLEMENTED — blessings, container integration, harmony/serene |
| `combat/spells.cpp` | PARTIAL | IMPLEMENTED — `playerSaySpell`, `InstantSpell::canCast`, `CombatSpell` |
| `appearance/mounts/mounts.cpp` | PARTIAL | IMPLEMENTED — outfit protocol, persistence, tame runtime |
| `players/grouping/party.cpp`, `team_finder.hpp` | MISSING | IMPLEMENTED — team finder (party done) |
| `players/grouping/guild.cpp` | PARTIAL | IMPLEMENTED — ranks persistence, invites, wars, MOTD |
| `players/components/player_cyclopedia/attached_effects` | MISSING | IMPLEMENTED — cyclopedia + attached-effects components |
| `players/components/weapon_proficiency.cpp` | PARTIAL | IMPLEMENTED — gain-on-hit/kill runtime |
| `players/components/wheel/*` | PARTIAL | IMPLEMENTED — spell-table registration into the spell registry |

## Tasks

### Task 1 — Player runtime chunk 2 (`feat/player-runtime-2`)
- **Canary sources:** `src/creatures/players/player.cpp/.hpp` — blessings, container integration, harmony/serene.
- **Deliverable:** extend `creatures/Creature.kt` (`Player`) with blessing state/`getLostPercent` interactions, container integration (`getContainer`/inventory container ops), and harmony/serene state.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures` (+ additive) only; no Lua.

### Task 2 — Spells + mounts completion (`feat/spells-mounts-complete`)
- **Canary sources:** `src/creatures/combat/spells.cpp` (`playerSaySpell`, `InstantSpell::canCast`, `CombatSpell`), `src/creatures/appearance/mounts/mounts.cpp` (outfit protocol, persistence, tame runtime).
- **Deliverable:** complete `spells/*` and `systems/mount/*` for the remaining behavior.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `spells`, `systems/mount`; no Lua.

### Task 3 — Team finder + guild (`feat/team-finder-guild`)
- **Canary sources:** `src/creatures/players/grouping/team_finder.hpp`, `guild.cpp/.hpp`.
- **Deliverable:** `creatures/grouping/TeamFinder.kt` and complete `social/guild/Guild.kt` (ranks persistence, invites, wars, MOTD).
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/grouping`, `social/guild`; no Lua.

### Task 4 — Cyclopedia/attached-effects components + WP gain + wheel spell-table (`feat/player-components-2`)
- **Canary sources:** `src/creatures/players/components/player_cyclopedia.cpp`, `player_attached_effects.cpp`, `weapon_proficiency.cpp` (gain-on-hit/kill), `wheel/player_wheel.cpp` (spell-table registration).
- **Deliverable:** `creatures/players/components/PlayerCyclopedia.kt`, `PlayerAttachedEffects.kt`, the weapon-proficiency gain runtime, and the wheel spell-table registration into `spells` (standalone components; do NOT edit `Creature.kt`).
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/players/components`, `systems/proficiency`, `systems/wheel`; no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip the covered rows to `IMPLEMENTED` in `docs/missing-features.md`; update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.
