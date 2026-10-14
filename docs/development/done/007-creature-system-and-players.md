# Feature: Creature System (Player, Monster, NPC)

## 1. Explanation
Defines the creature hierarchy: base `Creature`, `Player`, `Monster`, and `Npc`. Manages core RPG attributes (health, mana, speed, direction, look type/outfit, conditions, skull/shield status).

## 2. Impact
Central domain entities. Drives player interaction, monster behavior, AI, dialogue, combat calculations, and persistence.

## 3. Functionality Affected
- **State Machine**: Walking, attacking, casting, idling, dead.
- **Inventory & Containers**: Equippable slots (head, armor, legs, feet, back, left/right hand, ring, ammo).
- **Vitals & Stats**: Health/mana regeneration, capacity, stamina, soul points, skills (fist, club, sword, axe, distance, shielding, fishing, magic level).

## 4. Agent Implementation Details
- Implement `Creature` class with thread-confined mutations via the `GameDispatcher`.
- Implement `Player` entity with connection reference, storage values, guild info, VIP list.
- Implement `Monster` entity with monster type templates (spells, loot, immunities).
- Implement `Npc` entity with interaction queue and dialogue state.

## 5. Test Reference
- `org.opentibia.canary.creatures.CreatureTest`: Test health modifications, death triggers, inventory equipping, capacity checks.
- `org.opentibia.canary.creatures.PlayerStatsTest`: Test experience/level progression and skill formula computations.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
