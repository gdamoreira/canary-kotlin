# Gameplay Features Matrix & Progress Tracker

This document establishes the master inventory of all gameplay features from Canary, mapping their specification, status, and automated test coverage in **Canary-Kotlin**.

---

## 📊 1. Implemented & Verified Gameplay Features

| # | Gameplay System / Feature | Domain & Protocol Logic | Status | Automated Test Reference |
|---|---|---|:---:|---|
| **01** | **Character Locomotion** | 8-way movement (`0x65..0x68`, `0x6A..0x6D`), step duration timing based on creature and ground speed. | ✅ **DONE** | `MovePacketHandlerTest.kt`, `MovementTest.kt` |
| **02** | **Spot Turns & Orientation** | Turn without changing tiles (`0x6F..0x72`). | ✅ **DONE** | `MovePacketHandlerTest.kt` |
| **03** | **Item Actions & Interaction** | `onUse` (`0x82`), `onUseWith` (`0x83`), crosshair targeting. | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **04** | **Item Dragging & Throwing** | Moving items across ground, containers, and inventory slots (`0x78`). | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **05** | **Directional Object Rotation** | Rotating chairs, tables, and statues (`0x85`). | ✅ **DONE** | `ItemPacketHandlerTest.kt` |
| **06** | **Container Management** | Open (`0x7D`), close (`0x7E`, `0x87`), parent container navigation (`0x88`), capacity limits. | ✅ **DONE** | `ItemModelTest.kt`, `ItemPacketHandlerTest.kt` |
| **07** | **Physical Melee Combat** | Attack formulas: $2 \times \frac{\text{attack} \times (\text{skill} + 5.8)}{25} + \frac{\text{level}}{5}$. | ✅ **DONE** | `CombatEngineTest.kt` |
| **08** | **Armor Damage Mitigation** | Armor reduction: $P(\text{reduction}) \in \left[\frac{\text{armor}}{2}, \text{armor} - 1\right]$. | ✅ **DONE** | `CombatEngineTest.kt` |
| **09** | **Combat Stances & Stance Modes** | Full Attack, Balanced, Full Defense (`0xA0`). | ✅ **DONE** | `CombatPacketHandlerTest.kt` |
| **10** | **Combat Targeting & Following** | Locking target creature (`0xA1`), following without attacking (`0xA2`). | ✅ **DONE** | `CombatPacketHandlerTest.kt` |
| **11** | **Magic Spellcasting System** | Instant spell words (`exura`, `exevo flam hur`), level & mana requirements, cooldowns. | ✅ **DONE** | `SpellSystemTest.kt` |
| **12** | **Vitals & Mana Regeneration** | Periodic health and mana recovery per vocation up to maximum pool caps. | ✅ **DONE** | `VitalsRegenerationTest.kt` |
| **13** | **Player Death Penalty** | Experience and skill reductions (10% base down to 2% with full blessings). | ✅ **DONE** | `DeathPenaltyTest.kt` |
| **14** | **Corpse Generation & Temple Revive**| Spawning dead player/monster corpses, resetting conditions, temple teleport. | ✅ **DONE** | `DeathPenaltyTest.kt` |
| **15** | **Experience & Level Progression** | Exact cubic level curve formula: $\frac{50n^3 - 300n^2 + 850n - 600}{3}$. | ✅ **DONE** | `CreatureModelTest.kt` |
| **16** | **Item & Creature Inspection** | Look At (`0x8C`, `0x8D`) describing item attributes, weights, counts, creature HP. | ✅ **DONE** | `LookPacketHandlerTest.kt` |
| **17** | **In-Game Text Messaging** | Message classes (`MESSAGE_INFO_DESCR`, `MESSAGE_STATUS`, server warnings) (`0xB4`). | ✅ **DONE** | `LookPacketHandlerTest.kt` |
| **18** | **Speech & Chat Scopes** | Whisper (1 tile), Say (4 tiles), Yell (18 tiles), local spectator broadcasting (`0x96`). | ✅ **DONE** | `ChatManagerTest.kt` |
| **19** | **Chat Channels** | Public channels (World Chat, English, Guild, Party) with subscriber sets. | ✅ **DONE** | `ChatManagerTest.kt` |
| **20** | **Banking & Currency System** | Gold, Platinum, Crystal coin conversions, deposits, withdrawals, and wire transfers. | ✅ **DONE** | `BankSystemTest.kt` |
| **21** | **Player Storage & Quest Log** | Key-value integer storage (`getStorage`/`setStorage`), multi-mission quest log states. | ✅ **DONE** | `QuestStorageTest.kt` |
| **22** | **Player-to-Player Safe Trade** | Safe 2-way trade proposal (`0x7D`), inspection (`0x7E`), mutual accept (`0x7F`), cancel. | ✅ **DONE** | `TradeAndModalTest.kt` |
| **23** | **Server Modal Windows** | Popup modal dialogs with title, message, buttons, and choice options (`0xFA`). | ✅ **DONE** | `TradeAndModalTest.kt` |
| **24** | **Heartbeat & Safe Logout** | Ping / Pong keepalive (`0x1E`), combat battle sign logout prevention (`0x14`). | ✅ **DONE** | `KeepaliveProtocolTest.kt` |
| **25** | **Monster Spawner & Respawn Timer** | Spawn zones (coordinates, radius, respawn interval in seconds). | ✅ **DONE** | `MonsterAiTest.kt`, `MapPipelineServiceTest.kt` |
| **26** | **Monster Combat AI** | Nearest player targeting, pathfinding approach, melee attacks, spell casting. | ✅ **DONE** | `MonsterAiTest.kt` |
| **27** | **Loot Rolling Engine** | Dropping items on monster death according to basis-point probabilities ($1..100,000$). | ✅ **DONE** | `MonsterPipelineServiceTest.kt` |
| **28** | **NPC Dialogue & Conversations** | Greeting (`hi`), farewell (`bye`), trade requests (`trade`), keywords, and state focus. | ✅ **DONE** | `NpcDialogueTest.kt`, `NpcPipelineServiceTest.kt` |
| **29** | **NPC Merchant Trade Window** | In-game shop catalog with buyable/sellable goods, price checks, and gold deduction (`0x7A`). | ✅ **DONE** | `NpcDialogueTest.kt`, `NpcPipelineServiceTest.kt` |
| **30** | **Houses & Private Ownership** | House tiles, door permissions (`aleta grav`, `aleta sio`), intruder eviction, rent. | ✅ **DONE** | `HouseTest.kt`, `MapPipelineServiceTest.kt` |
| **31** | **Guilds & Guild Wars** | Guild ranks (Leader, Vice-Leader, Member), invites, war declarations, frag tracking. | ✅ **DONE** | `GuildWarTest.kt` |
| **32** | **Global Player Market (IOMarket)** | In-game auction house buy/sell offer escrow, automatic price-matching engine. | ✅ **DONE** | `MarketTest.kt` |
| **33** | **Town Depots & Supply Stash** | Multi-town depot locker chests, stackable supply stash storage. | ✅ **DONE** | `DepotMailTest.kt` |
| **34** | **Mailbox & Postal Delivery** | Sending parcels and letters via mailboxes to recipient town inboxes. | ✅ **DONE** | `DepotMailTest.kt` |
| **35** | **Hazardous Magic Fields** | Fire, Poison, and Energy fields on tiles applying condition ticks and decaying over time. | ✅ **DONE** | `WorldObjectsTest.kt` |
| **36** | **Teleporters** | Instant coordinate redirection upon entering teleport tiles. | ✅ **DONE** | `WorldObjectsTest.kt` |
| **37** | **Doors & Keys** | Closed/open door toggles and key ID verification. | ✅ **DONE** | `WorldObjectsTest.kt` |
| **38** | **Imbuement System** | Item shrines, powerful/intricate/basic slots, active duration decay. | ✅ **DONE** | `ModernSystemsTest.kt` |
| **39** | **Prey Hunting System** | Monster target tracking, active bonus multipliers (damage, defense, XP, loot). | ✅ **DONE** | `ModernSystemsTest.kt` |
| **40** | **Bosstiary & Lockout Tracker** | Kill counters across Bane tiers (25, 100, 300) and 20-hour boss room lever lockouts. | ✅ **DONE** | `BosstiaryTest.kt` |
| **41** | **Character Switching & Session Handoff**| Multi-character login, session cleanup on disconnect, and instant character switch. | ✅ **DONE** | `MultiCharacterSwitchMovementTest.kt` |

---

## ⏳ 2. Remaining Canary Gameplay Features (Pending Implementation)

The following advanced and secondary gameplay systems from Canary are identified and categorized for implementation:

| # | Feature Name | Description & Canary Source Reference | Target Component |
|---|---|---|---|
| **42** | **Wheel of Destiny (Skill Wheel)** | `src/io/io_wheel.cpp`, `src/enums/player_wheel.hpp`. Vocation perk trees, points distribution per level $>50$, dedication perks, gem sockets, and active spell bonuses (`0x61` Open, `0x62` Save). | `WheelOfDestinyEngine.kt` |
| **43** | **Exaltation Forge (Item Tiers & Fusion)** | `data/libs/systems/exaltation_forge.lua`, `src/enums/forge_conversion.hpp`. Dust, sliver, and core currency. Equipment fusion (Tier 0 to Tier 10), Onslaught (bonus damage), Ruse (dodge), and Momentum (cooldown reduction). | `ExaltationForgeEngine.kt` |
| **44** | **Bestiary & Charm Runes** | `src/io/iobestiary.cpp`, `data/scripts/systems/bestiary_charms.lua`. Monster kill counter stages, unlocking charm points, assigning active charms (Zap, Freeze, Wound, Dodge, Parry, Low Blow). | `BestiaryCharmEngine.kt` |
| **45** | **In-Game Store (GameStore)** | `data/modules/scripts/gamestore/gamestore.lua`, `src/enums/account_coins.hpp`. Tibia coins / transferable coins balance, category browsing, instant delivery of outfits, mounts, XP boosts, and blessings. | `GameStoreService.kt` |
| **46** | **Hazard System (Gnomprona Primal Dungeons)** | `data/libs/systems/hazard.lua`. Hazard level scales ($1..12+$, Primal Pods, Plunder Patriarch). Increases monster damage, defense, and boosted loot drop rates. | `HazardSystemEngine.kt` |
| **47** | **Daily Reward Shrine** | `data/libs/systems/daily_reward.lua`. Consecutive daily streak tracking (1..7 days), rest area bonuses, granting daily potions, prey cards, and XP boosts. | `DailyRewardEngine.kt` |
| **48** | **Boss Reward Chest (Damage Participation Loot)**| `src/items/containers/rewards/`, `data/scripts/systems/reward_chest.lua`. Tracks damage dealt, damage taken, and healing contributions per player during boss fights; deposits individualized reward containers into player reward chests. | `RewardChestEngine.kt` |
| **49** | **Familiars (Summoned Vocation Companions)** | `data/libs/systems/familiar.lua`, `data/XML/familiars.xml`. 30-minute cooldown summons for level 200+ characters (Sorcerer: *Sorcerer Familiar*, Druid: *Druid Familiar*, Knight: *Knight Familiar*, Paladin: *Paladin Familiar*). | `FamiliarSystem.kt` |
| **50** | **Hirelings System** | `data/libs/systems/hireling.lua`. House hireling NPCs serving as bankers, traders, cooks, and potion sellers with customized dresses and dialogues. | `HirelingSystem.kt` |
| **51** | **Mounts & Speed Bonuses** | `data/XML/mounts.xml`. Equipping mounts via hotkey, applying fixed speed bonuses ($+10..+15$ speed), and taming item chances. | `MountSystem.kt` |
| **52** | **Weapon Proficiencies (Tibia 13.30+)** | `src/enums/weapon_proficiency.hpp`, `data/items/proficiencies.json`. Leveling weapon weapon families (Swords, Axes, Clubs, Bows, Crossbows, Rods, Wands) granting passive damage and critical chances. | `WeaponProficiencyEngine.kt` |
| **53** | **Soul War / Taint Encounters** | `data/libs/systems/encounters.lua`. Goshnar taint multipliers, fear/agony gauges, and mechanical environmental arena traps. | `EncounterEngine.kt` |
| **54** | **Scheduled World Raids** | `data-canary/raids/raids.xml`. Automated global raid announcements (e.g. *"The ancient dragon Ghazbaran has risen!"*) spawning boss encounters and minions. | `RaidScheduler.kt` |
| **55** | **Offline Training Shrines** | `src/server/network/protocol/protocolgame.cpp` opcode `0x74`. Clicking training statues in temples/houses to log out and accumulate skill/magic level progress over offline hours. | `OfflineTrainingEngine.kt` |
| **56** | **Concoctions (Alchemy Potions)** | `data/libs/systems/concoctions.lua`. Temporary 1-hour alchemical stat flasks granting elemental protections, skill buffs, and mana leech bonuses. | `ConcoctionEngine.kt` |

---

## 📈 Summary Metrics

- **Implemented & Verified Gameplay Systems**: **41 / 56** ($73\%$)
- **Planned Advanced Gameplay Systems**: **15 / 56** ($27\%$)
- **Automated Test Coverage**: 53 test suites passing cleanly on **Java 25 LTS** via `./gradlew test`.
