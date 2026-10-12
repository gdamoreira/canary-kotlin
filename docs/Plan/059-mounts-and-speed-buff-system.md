# Feature: Mounts & Speed Buff System

## 1. Explanation
Implements creature mounts, taming items, and movement speed bonuses (`data/XML/mounts.xml`, `src/game/`). Players can tame wild creatures using specific items (e.g. Slingshot on Panda, Carrot on a Stick on Dromedary). Toggled via client hotkey (Ctrl+G / Mount button):
- Changes player visual sprite to mounted outfit.
- Grants a permanent $+10$ or $+15$ movement speed modifier while mounted.
- Decreases step duration in `MovementEngine`.

## 2. Impact
Movement speed progression, map exploration pacing, and collection mechanics.

## 3. Functionality Affected
- **Taming Action Engine**: Evaluates item use on monster: probability of success (unlocks mount), escape, or item break.
- **Speed Modifier Pipeline**: Dynamically recalculates creature speed: $\text{effectiveSpeed} = \text{baseSpeed} + \text{equipmentSpeed} + \text{mountSpeed}$.
- **Network Outfit Framing**: Includes mount sprite ID in `sendCreatureOutfit` / `sendMapDescription`.

## 4. Agent Implementation Details
- Implement `MountSystem.kt` parsing `data/XML/mounts.xml`.
- Add `mounted: Boolean` and `currentMountId: Int` to `Player`.
- Update `MovementEngine.calculateStepDuration` to include active mount speed bonuses.

## 5. Test Reference
- `org.opentibia.canary.systems.MountSystemTest`: Tests toggling mount applying $+10$ speed, step duration decreasing accordingly, and dismounting restoring base speed.
