# Feature: Real Items Loading Pipeline (OTB, items.xml, appearances.dat)

## 1. Explanation
Specifies the complete, high-volume item definition loading pipeline (`src/items/items.cpp`):
1. **Binary Item Definitions (`data/items/items.otb`)**:
   - Parses the complete catalogue of 30,000+ items.
   - Maps Server Item ID (internal game logic) to Client Sprite ID (visual representation).
   - Reads fundamental category flags: Ground speed, Stackable, Container, Splash, Fluid, Door, Teleport, Magic Field.
2. **Extended Item Properties (`data/items/items.xml`)**:
   - Augments items with RPG attributes: Attack, Defense, Extra Defense, Armor, Range, Weapon Type (Sword, Club, Axe, Distance, Wand/Rod), Charges, DecayTo item ID, Duration in seconds.
   - Slot placement: Head, Body, Legs, Feet, Two-Handed, Shield, Ring, Amulet, Backpack.
3. **Protobuf Appearances (`data/items/appearances.dat`)**:
   - For modern clients ($\ge 12.00$ / Tibia 13+), verifies item flags directly against CipSoft's Protobuf schema (`appearances.proto`).

## 2. Impact
Without this pipeline, equipment offers no stats, weapons do zero damage, containers cannot hold items, and corpses do not decay into skeletons or dust.

## 3. Functionality Affected
- **Item Type Registry**: Populates the global fast-lookup table (array indexed by Item ID).
- **Decay Chains**: Sets up the decay scheduler (e.g. Fresh Dragon Corpse $\to$ Rotting Corpse $\to$ Dragon Skeleton $\to$ Disappears).
- **Weapon Classification**: Drives hit chance, damage formulas, and projectile animation types.

## 4. Agent Implementation Details
- Implement `ItemPipelineService.kt` merging OTB and XML definitions into immutable, thread-safe `ItemType` instances.
- Optimize memory using primitive arrays (`Array<ItemType?>` indexed by ID $1..40000$) for $O(1)$ lookups without hash map overhead.
- Parse item abilities (elemental damage, resistance percentages, stat bonuses).

## 5. Test Reference
- `org.opentibia.canary.items.ItemPipelineServiceTest`: Loads real test snippets of `items.otb` and `items.xml`, asserting that a sword has attack, defense, weapon type, and correct sprite mapping.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
