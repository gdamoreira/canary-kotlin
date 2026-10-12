# Feature: Movement, Pathfinding & Viewport Synchronization

## 1. Explanation
Handles creature movement on the map, validation of walk requests (step north, east, south, west, diagonal), pathfinding (A* algorithm), and network synchronization (sending tile updates, map descriptions, creature turn packets).

## 2. Impact
Most frequent network operation in the MMORPG. Imperfect synchronization results in client-side "ghost walking", desyncs, or tile collisions.

## 3. Functionality Affected
- **Step Timing**: Speed-to-step-delay calculations (step duration depends on ground speed and creature speed).
- **Collision Checking**: Tile block solid, path blocking items, creature collision, elevation stairs/ramps/holes.
- **Network Viewport Packets**: `sendMapDescription`, `sendMoveCreature`, `sendAddTileItem`, `sendRemoveTileItem`.

## 4. Agent Implementation Details
- Implement A* pathfinder supporting diagonal penalty and avoid hazard flags.
- Implement step queue on `Creature` scheduling next step on the `GameDispatcher`.
- Implement map packet serializers building the floor description buffer (known 18x14 viewport tiles).
- Broadcast movement steps to all spectators within hearing/seeing distance.

## 5. Test Reference
- `org.opentibia.canary.game.movement.MovementTest`: Verify step delay calculation, tile collision rejection, and floor change transitions.
- `org.opentibia.canary.game.movement.PathfinderTest`: Test A* around obstacles and closed doors.
