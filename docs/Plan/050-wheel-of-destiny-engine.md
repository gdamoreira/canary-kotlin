# Feature: Wheel of Destiny (Skill Wheel) Engine

## 1. Explanation
Implements CipSoft's Wheel of Destiny skill progression system (`src/io/io_wheel.cpp`, `src/enums/player_wheel.hpp`). Characters level 50+ earn Promotion Points (1 point per level $>50$) to allocate across a 4-quadrant vocation talent tree. Allocates Dedication perks (raw stats: HP, MP, Cap, Mitigations), Conviction perks (secondary passives: leech, resistances), and Revelation perks (vocation ultimate spells and spell augmentations). Includes client window opcodes: `0x61` (Open Wheel) and `0x62` (Save Wheel).

## 2. Impact
Endgame character customization and power progression for level 50..1000+ characters.

## 3. Functionality Affected
- **Point Budget Engine**: Total points = $\max(0, \text{level} - 50)$.
- **Quadrant Prerequisites**: Ensures nodes are unlocked in topological order from center to outer ring.
- **Spell Modification Engine**: Modifies damage and cooldowns of specific spells (e.g. *Avatar of Steel*, *Avatar of Light*, *Executioner's Throw*).

## 4. Agent Implementation Details
- Implement `WheelOfDestinyEngine.kt` parsing vocation perk trees.
- Implement `PlayerWheelData` serialized into player storage / database.
- Wire packet handlers for client opcodes `0x61` and `0x62`.

## 5. Test Reference
- `org.opentibia.canary.systems.WheelOfDestinyTest`: Tests allocating points according to level budget, prerequisite gating, and applying perk stat modifiers to `Player`.
