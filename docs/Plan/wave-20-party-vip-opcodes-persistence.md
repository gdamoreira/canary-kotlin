# Wave 20 Plan — Party, VIP, Inbound Opcodes, Inventory Persistence

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after).

## Goal

Advance the highest-impact gaps from `docs/missing-features.md`: the party and VIP player components, a batch of remaining inbound protocol opcodes, and full player inventory persistence.

## Tasks

### Task 1 — Party system (`feat/party-system`)
- **Canary sources:** `src/creatures/players/grouping/party.cpp`, `party.hpp`.
- **Deliverable:** `creatures/grouping/Party.kt` extended from the identity-only stub to a faithful party: leader/members, invite/join/leave/revoke, shared-experience basics, party shields flag, `getPartyMembers`. Constructor-injected; no singleton.
- **Tests:** invite/join/leave/revoke, leader transfer on leave, member count, shared-exp split.
- **Constraints:** only `creatures/grouping` (+ additive `Creature.kt`); no Lua.

### Task 2 — VIP system (`feat/vip-system`)
- **Canary sources:** `src/creatures/players/components/player_vip.cpp`, `player_vip.hpp`.
- **Deliverable:** `creatures/players/vip/PlayerVip.kt` (VIP entries/groups, add/remove/edit, group management) + a VIP protocol serializer if straightforward.
- **Tests:** add/remove entries, group assignment, icon/status.
- **Constraints:** only `creatures/players/vip` (+ `network/protocol` additive); no Lua.

### Task 3 — Inbound opcode batch (`feat/inbound-opcodes-batch`)
- **Canary sources:** `src/server/network/protocol/protocolgame.cpp` — `parseTrade` (`0x7D`–`0x80`), `parseFollow` (`0xA2`), `parseContainer*` (`0x75`/`0x87`/`0x88`/`0x95`), `parseOutfit`/mount (`0xD3`/`0xD4`), `parseUseItemEx` (`0x83`).
- **Deliverable:** decoders for these opcodes into DTOs, routed through `GamePacketRouter` on the WorldCommit lane (parse + confinement dispatch where no engine action exists yet).
- **Tests:** `Unpooled`/`EmbeddedChannel` decode + router recognition.
- **Constraints:** `network/protocol` only; no Lua.

### Task 4 — Inventory persistence (`feat/persistence-inventory`)
- **Canary sources:** `src/io/iologindata_load_player.cpp` / `iologindata_save_player.cpp` — inventory/depot item serialization; `src/items/functions/item/attribute.cpp`.
- **Deliverable:** a Flyway `V5__player_items.sql` (item table with attributes) + a JDBC repository that serializes/deserializes a player's inventory items (id, count, attributes, slot), with the item-attribute blob mirroring Canary's serialization.
- **Tests:** H2 round-trip of an inventory with stackable + attributed items.
- **Constraints:** `database` + `persistence` + `src/main/resources/db/migration`; no Lua.

## Verification

- `./gradlew clean test` green, zero Kotlin warnings.
- Each task's tests transcribed from the cited Canary behavior.

## Post-wave (AGENTS.md §6)

Update `docs/missing-features.md`, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/Done/`; commit the docs separately and push to `origin`.
