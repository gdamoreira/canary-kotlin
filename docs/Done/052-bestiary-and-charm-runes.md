# Feature: Bestiary & Charm Runes System

## 1. Explanation
Implements CipSoft's Cyclopedia Bestiary and Charm Runes (`src/io/iobestiary.cpp`, `src/io/iobestiary.hpp`). Tracks monster kills across 3 unlock stages (e.g. 25/250/1000), reveals monster details (HP, loot, weaknesses), awards Charm Points, and unlocks socketable Charm Runes — offensive (Zap, Freeze, Wound, Enflame, Poison, Curse, Divine Wrath) and defensive/utility (Dodge, Parry, Low Blow).

## 2. Impact
Core mid-to-endgame hunting incentive; affects combat DPS on high-health creatures.

## 3. Functionality Affected
- **Kill progress**: `Player::addBestiaryKillCount` / `getBestiaryKillCount`, stage completion.
- **Charm state**: `charmsArray`, `charmPoints`, `setCharmTier`, `parseRacebyCharm`.
- **Monster metadata**: `MonsterType.info.bestiaryRace/Class/Stars`.
- **Protocol**: `parseBestiarySendRaces`, `parseBestiarysendMonsterData`, `parseBestiarySendCreatures`, `parseSendBuyCharmRune`, `sendBestiaryCharms`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port `IOBestiary`, `Charm`, the `Player` bestiary/charm state, the bestiary protocol handlers, and `Game().getBestiaryList()`. Bind `Game.createBestiaryCharm`, `player:addBestiaryKillCount/getBestiaryKillCount/charmPoints`.
- **Stays Lua (do not port):** `data/scripts/systems/bestiary_charms.lua` (the 25 charm configs registered via `Game.createBestiaryCharm(id):register(config)`), `data/scripts/lib/register_monster_type.lua` (bestiary mask).

## 5. Test Reference
- `org.opentibia.canary.systems.BestiaryTest`: kill thresholds awarding charm points, socketing a charm to a completed creature, charm combat proc, and bestiary protocol serialization.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/io/iobestiary.cpp`, `src/io/iobestiary.hpp`, `src/creatures/players/player.hpp` (bestiary/charm state), `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/systems/bestiary_charms.lua`, `data/scripts/lib/register_monster_type.lua`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `IOBestiary::parseCharm`, `Player::addBestiaryKillCount`, `Game::createBestiaryCharm`, `parseBestiarySendRaces`, `parseSendBuyCharmRune`

## 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/game-play-features.md`.
