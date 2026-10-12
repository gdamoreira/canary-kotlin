# Feature: Magic Fields, Fluid Physics, Doors & Teleporters

## 1. Explanation
Implements core world object behaviors (`src/items/`, `src/game/movement/`):
- **Magic Fields**: Fire fields, Poison fields, Energy fields. Create hazards on tiles, deal damage, apply condition ticks (`ConditionType.Fire`, `ConditionType.Poison`) when creatures step in, and decay over time.
- **Fluid Physics**: Vials, flasks, and fluid barrels (water, blood, mana, health, slime).
- **Doors & Keys**: Open/closed door toggles, locked doors checking key action IDs.
- **Teleporters**: Tiles routing stepping creatures instantly to destination coordinates $(x, y, z)$.

## 2. Impact
Pervasive world mechanics. Governs dungeon puzzles, map exploration, safe zones, and tactical combat field control.

## 3. Functionality Affected
- **Tile Step-In Triggers**: `onStepIn` listeners checking for fields or teleporters.
- **Door Transformations**: Item ID swap when used (closed $\to$ open door, open $\to$ closed door).
- **Field Decays**: Scheduling decay events on `GameDispatcher` to step through decay stages before vanishing.

## 4. Agent Implementation Details
- Implement `MagicFieldItem.kt` extending `Item` with condition application logic.
- Implement `DoorItem.kt` with open/close toggles and key ID verification.
- Implement `TeleportItem.kt` setting creature position upon tile entry.

## 5. Test Reference
- `org.opentibia.canary.items.WorldObjectsTest`: Tests stepping into a fire field applies burning damage, stepping onto teleporter changes coordinates, and using a closed door toggles state.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
