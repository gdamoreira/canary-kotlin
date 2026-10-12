# Feature: Mounts & Speed Buff System

## 1. Explanation
Implements creature mounts (`src/creatures/appearance/mounts/mounts.*`, `data/XML/mounts.xml`). Players tame creatures and toggle mounts, gaining a fixed movement-speed bonus while mounted. Mount definitions load from XML; the taming items and store catalog are Lua.

## 2. Impact
Movement speed progression, exploration pacing, and collection mechanics.

## 3. Functionality Affected
- **Definitions**: `struct Mount` / `class Mounts` (XML load, speed value).
- **Player state**: `setCurrentMount/toggleMount/tameMount/untameMount/hasMount/hasAnyMount/getRandomMountId/dismount`; storage range.
- **Speed**: mounted speed added to effective creature speed.
- **Protocol**: mount sprite in `sendCreatureOutfit` / map description.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port `Mount`/`Mounts` XML loader, the `Player` mount methods + storage range, the mount speed contribution, and the outfit/mount protocol framing. Bind `player:addMount/hasMount/toggleMount`.
- **Stays Lua (do not port):** `data/scripts/actions/items/usable_mount_items.lua` (taming items → `player:addMount`), `data/scripts/talkactions/god/add_mount.lua`, `data/modules/scripts/gamestore/catalog/cosmetics_mounts.lua`, `data/XML/mounts.xml` (data).

## 5. Test Reference
- `org.opentibia.canary.systems.MountSystemTest`: XML load, `toggleMount` applying the mount speed bonus, step-duration decrease, and dismount restoring base speed.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/appearance/mounts/mounts.hpp`, `src/creatures/appearance/mounts/mounts.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/creatures/players/components/player_storage.cpp`, `src/lua/functions/creatures/player/mount_functions.cpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/actions/items/usable_mount_items.lua`, `data/scripts/talkactions/god/add_mount.lua`, `data/modules/scripts/gamestore/catalog/cosmetics_mounts.lua`, `data/XML/mounts.xml`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `Mounts::loadFromXml`, `Player::toggleMount/hasMount/addMount`
