# Wave 27 Plan — `src/creatures/` Part 1 (Creature core, Outfits, Attached Effects, Chat)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** Fourth section of `docs/missing-features.md` §2 (`src/creatures/`, part 1). Goal: turn the covered `PARTIAL`/`MISSING` rows into `IMPLEMENTED`.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `creature.cpp` (2,239 lines) | PARTIAL | IMPLEMENTED — conditions (done), outfit/light/speed, walk/step, onThink/onAttacked/onTarget/onDeath, master/summon, spectator visibility, icons |
| `appearance/outfit/outfit.cpp` | MISSING | IMPLEMENTED — outfit registry + XML, addons, colors |
| `appearance/attached_effects.cpp` | MISSING | IMPLEMENTED — auras/wings/shader/effects registry + XML |
| `interactions/chat.cpp` | PARTIAL | IMPLEMENTED — channel registry/invites/users, NPC/private channels, muted checks, yell rules |

## Tasks

### Task 1 — Creature core (`feat/creature-core`)
- **Canary sources:** `src/creatures/creature.cpp/.hpp`.
- **Deliverable:** extend `creatures/Creature.kt` with the remaining state/methods: outfit + light + speed, `onThink`/`onAttacked`/`onTarget`/`onDeath`, master/summon linkage, spectator visibility (`canSee`), and creature icons.
- **Tests:** transcribe real Canary behavior (light/outfit state, onAttacked/onTarget hooks, master/summon, canSee).
- **Constraints:** `creatures` (+ additive); no Lua.

### Task 2 — Outfit registry (`feat/outfit-registry`)
- **Canary sources:** `src/creatures/appearance/outfit/outfit.cpp/.hpp`.
- **Deliverable:** `creatures/appearance/outfit/Outfits.kt` — outfit registry loaded from `data/XML/outfits.xml`, addons, colors, `getOutfitByLookType`.
- **Tests:** XML load counts, lookup by look type, addon/color handling.
- **Constraints:** `creatures/appearance/outfit` (new package); no Lua.

### Task 3 — Attached effects (`feat/attached-effects`)
- **Canary sources:** `src/creatures/appearance/attached_effects/attached_effects.cpp/.hpp`.
- **Deliverable:** `creatures/appearance/effects/AttachedEffects.kt` — auras/wings/shader/effects registry + XML loader.
- **Tests:** XML load, lookup, effect classification.
- **Constraints:** `creatures/appearance/effects` (new package); no Lua.

### Task 4 — Chat channels (`feat/chat-channels`)
- **Canary sources:** `src/creatures/interactions/chat.cpp/.hpp`.
- **Deliverable:** extend `chat/ChatManager.kt` with the channel registry (public/private/guild/party), invites/users, muted checks, and yell rules.
- **Tests:** channel create/join/leave, invite/remove, muted checks, yell scope.
- **Constraints:** `chat` (+ additive); no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings; tests transcribed from the cited Canary behavior.

## Post-wave (AGENTS.md §6)

Flip the covered `src/creatures/` rows to `IMPLEMENTED` in `docs/missing-features.md`; update `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.
