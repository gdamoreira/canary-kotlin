# Feature: Modern Client Features (Imbuements, Prey, Bestiary, Skill Wheel, Forge)

## 1. Explanation
Canary supports modern CipSoft Tibia (12.x / 13.x / 15.x) features including Imbuements, Prey system, Hunting Tasks, Bestiary & Charms, Forge (Tier upgrades/fusion), and the Skill Wheel of Destiny.

## 2. Impact
Necessary for 100% parity with modern Canary protocol versions ($\ge 12.00$).

## 3. Functionality Affected
- **Imbuement System**: Shrine interaction, powerful/intricate/basic slots, imbuement duration depletion.
- **Prey & Task Hunting**: Monster rerolls, damage/defense/loot/xp prey buffs.
- **Bestiary / Bosstiary**: Monster kill tracking, unlocking charm points, assigning runes.
- **Skill Wheel of Destiny**: Points distribution tree, perk unlocks, gem socketing.
- **Exaltation Forge**: Dust/slivers/cores, equipment fusion and transfer.

## 4. Agent Implementation Details
- Implement dedicated domain models and packet serializers for each subsystem.
- Implement packet handlers for each respective client modal/window opcodes.
- Persist state into player storage / JSON / database schemas.

## 5. Test Reference
- `org.opentibia.canary.systems.ImbuementTest`: Test slotting items, applying stat bonuses, and decay tracking.
- `org.opentibia.canary.systems.PreyTest`: Test roll logic and active buff multipliers.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
