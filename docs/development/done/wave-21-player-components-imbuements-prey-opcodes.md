# Wave 21 Plan — Player Components, Imbuements, Prey/Task Hunting, More Opcodes

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).

## Goal

Continue closing the highest-impact gaps in `docs/development/missing-features.md`: missing player components, the imbuement engine, the prey/task-hunting runtime, and a second batch of inbound opcodes.

## Tasks

### Task 1 — Player components: titles, badges, achievements (`feat/player-components`)
- **Canary sources:** `src/creatures/players/components/player_title.cpp`, `player_badge.cpp`, `player_achievement.cpp`.
- **Deliverable:** `creatures/players/components/` Kotlin components: `PlayerTitles` (title id/KV unlock, current title), `PlayerBadges`, `PlayerAchievements` (points, unlock, progress) — self-contained, injected KV/repository seam.
- **Tests:** unlock/select title, badge grant, achievement points/progress.
- **Constraints:** `creatures/players/components` (+ additive); no Lua.

### Task 2 — Imbuement engine (`feat/imbuements-engine`)
- **Canary sources:** `src/creatures/players/imbuements/imbuements.cpp/.hpp`, `src/enums/imbuement.hpp`.
- **Deliverable:** `systems/imbuement/ImbuementEngine.kt`: apply/clear/scroll imbuements to an item (slot tiers, duration decay, stat application), plus the imbuement definition registry from `data/XML/imbuements.xml`.
- **Tests:** apply basic/intricate/powerful, duration decay, clear, stat bonuses.
- **Constraints:** `systems/imbuement` (+ additive items); no Lua.

### Task 3 — Prey + Task Hunting runtime (`feat/prey-task-runtime`)
- **Canary sources:** `src/io/ioprey.cpp`, `src/creatures/players/…` prey/task-hunting, `src/game/game.cpp` prey handlers.
- **Deliverable:** `systems/prey/PreyEngine.kt` (slot state, reroll/select, bonus application, time-left) and `systems/taskhunting/TaskHuntingEngine.kt` (task list, points, kill progress) faithful to Canary.
- **Tests:** prey select/reroll/bonus, task progress/points.
- **Constraints:** `systems/prey`, `systems/taskhunting` (+ additive); no Lua.

### Task 4 — Inbound opcode batch 2 (`feat/inbound-opcodes-batch-2`)
- **Canary sources:** `src/server/network/protocol/protocolgame.cpp` — party `0xA3–0xA8`, imbuements `0xB2`/`0xD5–0xD7`, depot/stash `0x28/0x29/0x92–0x95`, quick loot `0x8F–0x91`, modal answer `0xF9`, quest log `0xF0/F1`.
- **Deliverable:** decoders into DTOs + `GamePacketRouter` wiring (parse + WorldCommit confinement).
- **Tests:** `Unpooled`/`EmbeddedChannel` decode + router recognition.
- **Constraints:** `network/protocol` only; no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

## Post-wave (AGENTS.md §6)

Update `docs/development/missing-features.md`, `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

## Completion

Completed. Player components (titles/badges/achievements), imbuement engine, prey + task-hunting engines, and inbound opcode batch 2 implemented and merged; full suite green. See `docs/development/missing-features.md` for updated coverage.
