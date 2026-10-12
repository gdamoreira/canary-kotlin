# Gameplay Features Matrix & Progress Tracker

This document establishes the master inventory of all gameplay features from Canary, mapping their specification, status, automated test coverage, and the **original Canary project source files** in **Canary-Kotlin**.

---

## 📊 1. Implemented & Verified Gameplay Features

| # | Gameplay System / Feature | Domain & Protocol Logic | Canary Original Code Source | Status | Automated Test Reference |
|---|---|---|---|:---:|---|
| **01** | **Character Locomotion** | 8-way movement (`0x65..0x68`, `0x6A..0x6D`), step duration timing based on creature and ground speed. | `src/server/network/protocol/protocolgame.cpp:1738-1763`, `src/creatures/creature.cpp` | ✅ **DONE** | `MovePacketHandlerTest.kt`, `MovementTest.kt` |
| **02** | **Spot Turns & Orientation** | Turn without changing tiles (`0x6F..0x72`). | `src/server/network/protocol/protocolgame.cpp:1768-1778` | ✅ **DONE** | `MovePacketHandlerTest.kt`, `PositionFunctionsTranscriptTest.kt` |
| **03** | **Item Actions & Interaction** | `onUse` (`0x82`), `onUseWith` (`0x83`), crosshair targeting. | `src/server/network/protocol/protocolgame.cpp:1825-1832`, `src/actions/` | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **04** | **Item Dragging & Throwing** | Moving items across ground, containers, and inventory slots (`0x78`). | `src/server/network/protocol/protocolgame.cpp:1795`, `src/game/game.cpp` | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **05** | **Directional Object Rotation** | Rotating chairs, tables, and statues (`0x85`). | `src/server/network/protocol/protocolgame.cpp:1834` | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **06** | **Container Management** | Open (`0x7D`), close (`0x7E`, `0x87`), parent container navigation (`0x88`), capacity limits. | `src/items/containers/container.cpp`, `src/server/network/protocol/protocolgame.cpp:1840-1844` | ✅ **DONE** | `ItemModelTest.kt`, `ItemPacketHandlerTest.kt` |
| **07** | **Physical Melee Combat** | Attack formulas: $2 \times \frac{\text{attack} \times (\text{skill} + 5.8)}{25} + \frac{\text{level}}{5}$. | `src/creatures/combat/combat.cpp:1100-1150` | ✅ **DONE** | `CombatEngineTest.kt` |
| **08** | **Armor Damage Mitigation** | Armor reduction: $P(\text{reduction}) \in \left[\frac{\text{armor}}{2}, \text{armor} - 1\right]$. | `src/creatures/combat/combat.cpp:1200-1230` | ✅ **DONE** | `CombatEngineTest.kt` |
| **09** | **Combat Stances & Stance Modes** | Full Attack, Balanced, Full Defense (`0xA0`). | `src/server/network/protocol/protocolgame.cpp:1912`, `src/creatures/players/player.hpp` | ✅ **DONE** | `CombatPacketHandlerTest.kt` |
| **10** | **Combat Targeting & Following** | Locking target creature (`0xA1`), following without attacking (`0xA2`). | `src/server/network/protocol/protocolgame.cpp:1915-1919` | ✅ **DONE** | `CombatPacketHandlerTest.kt` |
| **11** | **Magic Spellcasting System** | Instant spell words (`exura`, `exevo flam hur`), level & mana requirements, cooldowns. | `src/creatures/combat/spells.cpp`, `data/scripts/spells/` | ✅ **DONE** | `SpellSystemTest.kt` |
| **12** | **Vitals & Mana Regeneration** | Periodic health and mana recovery per vocation up to maximum pool caps. | `src/creatures/players/player.cpp`, `src/game/game.cpp` | ✅ **DONE** | `VitalsRegenerationTest.kt` |
| **13** | **Player Death Penalty** | Experience and skill reductions (10% base down to 2% with full blessings). | `src/game/game.cpp`, `src/enums/player_blessings.hpp` | ✅ **DONE** | `DeathPenaltyTest.kt` |
| **14** | **Corpse Generation & Temple Revive**| Spawning dead player/monster corpses, resetting conditions, temple teleport. | `src/game/game.cpp:playerDeath`, `src/items/item.cpp` | ✅ **DONE** | `DeathPenaltyTest.kt` |
| **15** | **Experience & Level Progression** | Exact cubic level curve formula: $\frac{50n^3 - 300n^2 + 850n - 600}{3}$. | `src/game/game.cpp:getExpForLevel` | ✅ **DONE** | `CreatureModelTest.kt` |
| **16** | **Item & Creature Inspection** | Look At (`0x8C`, `0x8D`) describing item attributes, weights, counts, creature HP. | `src/server/network/protocol/protocolgame.cpp:1855-1859` | ✅ **DONE** | `LookPacketHandlerTest.kt` |
| **17** | **In-Game Text Messaging** | Message classes (`MESSAGE_INFO_DESCR`, `MESSAGE_STATUS`, server warnings) (`0xB4`). | `src/server/network/protocol/protocolgame.cpp:sendTextMessage` | ✅ **DONE** | `LookPacketHandlerTest.kt` |
| **18** | **Speech & Chat Scopes** | Whisper (1 tile), Say (4 tiles), Yell (18 tiles), local spectator broadcasting (`0x96`). | `src/creatures/interactions/chat.cpp`, `src/server/network/protocol/protocolgame.cpp:1885` | ✅ **DONE** | `ChatManagerTest.kt` |
| **19** | **Chat Channels** | Public channels (World Chat, English, Guild, Party) with subscriber sets. | `src/creatures/interactions/chat.cpp`, `data/chatchannels/` | ✅ **DONE** | `ChatManagerTest.kt` |
| **20** | **Banking & Currency System** | Gold, Platinum, Crystal coin conversions, deposits, withdrawals, and wire transfers. | `src/game/bank/bank.cpp`, `data/npclib/npc_system/bank_system.lua` | ✅ **DONE** | `BankSystemTest.kt` |
| **21** | **Player Storage & Quest Log** | Key-value integer storage (`getStorage`/`setStorage`), multi-mission quest log states. | `src/io/player_storage_repository.cpp`, `src/creatures/players/components/player_storage.cpp` | ✅ **DONE** | `QuestStorageTest.kt` |
| **22** | **Player-to-Player Safe Trade** | Safe 2-way trade proposal (`0x7D`), inspection (`0x7E`), mutual accept (`0x7F`), cancel. | `src/server/network/protocol/protocolgame.cpp:1810-1820`, `src/game/game.cpp` | ✅ **DONE** | `TradeAndModalTest.kt` |
| **23** | **Server Modal Windows** | Popup modal dialogs with title, message, buttons, and choice options (`0xFA`). | `src/game/modal_window/modal_window.cpp`, `src/server/network/protocol/protocolgame.cpp` | ✅ **DONE** | `TradeAndModalTest.kt` |
| **24** | **Heartbeat & Safe Logout** | Ping / Pong keepalive (`0x1E`), combat battle sign logout prevention (`0x14`). | `src/server/network/protocol/protocolgame.cpp:1686-1694`, `src/game/game.cpp` | ✅ **DONE** | `KeepaliveProtocolTest.kt` |
| **25** | **Monster Spawner & Respawn Timer** | Spawn zones (coordinates, radius, respawn interval in seconds). | `src/map/spawns.cpp`, `data-canary/world/canary-monster.xml` | ✅ **DONE** | `MonsterAiTest.kt`, `MapPipelineServiceTest.kt` |
| **26** | **Monster Combat AI** | Nearest player targeting, pathfinding approach, melee attacks, spell casting. | `src/creatures/monsters/monster.cpp`, `src/creatures/monsters/monster_combat_intention.cpp` | ✅ **DONE** | `MonsterAiTest.kt` |
| **27** | **Loot Rolling Engine** | Dropping items on monster death according to basis-point probabilities ($1..100,000$). | `src/creatures/monsters/monster.cpp:dropLoot`, `data-canary/scripts/creaturescripts/drop_loot.lua` | ✅ **DONE** | `MonsterPipelineServiceTest.kt` |
| **28** | **NPC Dialogue & Conversations** | Greeting (`hi`), farewell (`bye`), trade requests (`trade`), keywords, and state focus. | `src/creatures/npcs/npc.cpp`, `data/npclib/npc_system/keyword_handler.lua` | ✅ **DONE** | `NpcDialogueTest.kt`, `NpcPipelineServiceTest.kt` |
| **29** | **NPC Merchant Trade Window** | In-game shop catalog with buyable/sellable goods, price checks, and gold deduction (`0x7A`). | `src/server/network/protocol/protocolgame.cpp:1800-1808`, `data/npclib/` | ✅ **DONE** | `NpcDialogueTest.kt`, `NpcPipelineServiceTest.kt` |
| **30** | **Houses & Private Ownership** | House tiles, door permissions (`aleta grav`, `aleta sio`), intruder eviction, rent. | `src/map/house/house.cpp`, `src/map/house/housetile.cpp` | ✅ **DONE** | `HouseTest.kt`, `MapPipelineServiceTest.kt` |
| **31** | **Guilds & Guild Wars** | Guild ranks (Leader, Vice-Leader, Member), invites, war declarations, frag tracking. | `src/creatures/players/grouping/guild.cpp`, `src/io/ioguild.cpp` | ✅ **DONE** | `GuildWarTest.kt` |
| **32** | **Global Player Market (IOMarket)** | In-game auction house buy/sell offer escrow, automatic price-matching engine. | `src/io/iomarket.cpp`, `src/server/network/protocol/protocolgame.cpp` | ✅ **DONE** | `MarketTest.kt` |
| **33** | **Town Depots & Supply Stash** | Multi-town depot locker chests, stackable supply stash storage. | `src/items/containers/depot/depotlocker.cpp`, `src/creatures/players/stash_definitions.hpp` | ✅ **DONE** | `DepotMailTest.kt` |
| **34** | **Mailbox & Postal Delivery** | Sending parcels and letters via mailboxes to recipient town inboxes. | `src/items/containers/mailbox/mailbox.cpp`, `src/items/containers/inbox/inbox.cpp` | ✅ **DONE** | `DepotMailTest.kt` |
| **35** | **Hazardous Magic Fields** | Fire, Poison, and Energy fields on tiles applying condition ticks and decaying over time. | `src/items/magicfield.cpp`, `src/creatures/combat/condition.cpp` | ✅ **DONE** | `WorldObjectsTest.kt` |
| **36** | **Teleporters** | Instant coordinate redirection upon entering teleport tiles. | `src/game/movement/teleport.cpp` | ✅ **DONE** | `WorldObjectsTest.kt` |
| **37** | **Doors & Keys** | Closed/open door toggles and key ID verification. | `src/items/doors/door.cpp`, `data/scripts/movements/closing_door.lua` | ✅ **DONE** | `WorldObjectsTest.kt` |
| **38** | **Imbuement System** | Item shrines, powerful/intricate/basic slots, active duration decay. | `src/enums/imbuement.hpp`, `data/XML/imbuements.xml`, `data/scripts/systems/imbuement_shrine.lua` | ✅ **DONE** | `ModernSystemsTest.kt` |
| **39** | **Prey Hunting System** | Monster target tracking, active bonus multipliers (damage, defense, XP, loot). | `src/io/ioprey.cpp`, `src/server/network/protocol/protocolgame.cpp` | ✅ **DONE** | `ModernSystemsTest.kt` |
| **40** | **Bosstiary & Lockout Tracker** | Kill counters across Bane tiers (25, 100, 300) and 20-hour boss room lever lockouts. | `src/io/io_bosstiary.cpp` | ✅ **DONE** | `BosstiaryTest.kt` |
| **41** | **Character Switching & Session Handoff**| Multi-character login, session cleanup on disconnect, and instant character switch. | `src/server/network/protocol/protocollogin.cpp`, `src/server/network/protocol/protocolgame.cpp` | ✅ **DONE** | `MultiCharacterSwitchMovementTest.kt` |

---

## ⏳ 2. Remaining Canary Gameplay Features (Pending Implementation)

> **Rule (AGENTS.md §1.4):** Lua stays Lua. For each feature, only the **C++ engine surface** is ported to Kotlin; player-facing logic authored in `data/**/*.lua` is executed as Lua through the scripting engine. Corrected plan docs are in `docs/Plan/050–064`.

| # | Feature Name | Nature | Kotlin scope (C++ engine only) | Stays Lua | Plan |
|---|---|---|---|:---:|---|
| **42** | Wheel of Destiny | **C++-only** | `IOWheel`, `PlayerWheel`, `wheel_gems`, `wheel_spells`, enums, protocol `0x61`/`0x62`/`0xE7` | — | `050` |
| **43** | Exaltation Forge | Mixed | `PlayerForgeHistory`, `Player` forge API, item tier attrs, forge protocol | `exaltation_forge.lua`, `item_tiers.lua` | `051` |
| **44** | Bestiary & Charms | Mixed | `IOBestiary`, `Charm`, Player charm state, bestiary protocol | `bestiary_charms.lua` | `052` |
| **45** | In-Game Store | Lua-dominant | `Account` coin primitives, coin columns, balance packet | whole `gamestore/*` module | `053` |
| **46** | Hazard System | Mixed | Monster hazard flags, Player hazard points/hooks, `handleHazardSystemAttack` | `hazard.lua`, `ondroploot_hazard.lua` | `054` |
| **47** | Daily Reward Shrine | Mixed | `Player::setDailyReward`, KV persistence, regen, packet | `daily_reward.lua`, shrine scripts | `055` |
| **48** | Boss Reward Chest | Mixed | `Reward`/`RewardChest`, `collectRewardChestItems`, protocol `0xFF` | `reward_chest.lua`, `reward_boss.lua` | `056` |
| **49** | Familiars | Mixed | `Familiars` XML loader, Player familiar state | `familiar.lua`, summon spells | `057` |
| **50** | Hirelings | Mixed | Game hireling maps, `canSpeakWithHireling`, protocol `0xEC` | `hireling.lua` | `058` |
| **51** | Mounts & Speed | Mixed | `Mount`/`Mounts` loader, Player mount methods | taming scripts, mount catalog | `059` |
| **52** | Weapon Proficiencies | **C++-dominant** | `WeaponProficiency` + JSON loader + enums + payload | 2 small scripts | `060` |
| **53** | Soul War / Taint | Mixed | Taint/Fear/Agony conditions, `ConditionFeared`, Zone primitives | `encounters.lua`, quest scripts | `061` |
| **54** | Scheduled World Raids | Mixed | legacy `Raids`/`Raid`/`RaidEvent` (or Encounter primitives) | `raids.lua`, `raids.xml` | `062` |
| **55** | Offline Training | Mixed | `Player` offline-training state, protocol `0x74`, persistence | `offline_training.lua` | `063` |
| **56** | Concoctions | Mixed | `Concoction_t`, `updateConcoction`, active state, packet | `concoctions.lua` | `064` |

**Scope note:** none of these is "Lua misread as a feature" — each has a real missing C++ engine surface. The prior assumption that they could be delivered as standalone Kotlin `*Engine` classes was wrong for the Lua-authored portions; those now stay Lua.

---

## 📈 Summary Metrics

- **Implemented & Verified Gameplay Systems**: **41 / 56** ($73\%$)
- **Planned Advanced Gameplay Systems**: **15 / 56** ($27\%$)
- **Automated Test Coverage**: 57 test suites passing cleanly on **Java 25 LTS** via `./gradlew test`.
