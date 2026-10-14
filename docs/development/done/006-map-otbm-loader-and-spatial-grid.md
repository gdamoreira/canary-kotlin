# Feature: Map Loader & Spatial Representation (OTBM Parser & QuadTree)

## 1. Explanation
Parses OpenTibia Map (`.otbm`) binary format files and loads towns, waypoints, houses, tiles, items, and spawns into a spatial data structure (2D/3D grid or QuadTree/Floor map).

## 2. Impact
Foundational for all gameplay and spatial awareness. The map structure dictates pathfinding, line of sight, creature perception, spectator broadcasting, and viewport rendering.

## 3. Functionality Affected
- **Tile Hierarchy**: Ground, items stack, creature list, tile flags (PZ, house, no-logout, refresh).
- **Spectator Resolution**: Fast querying of players and creatures within a 18x14 viewport around any position $(x, y, z)$.
- **OTBM Parser**: Decompresses nodes, loads header (map dimensions, description), areas, towns, and tile node trees.

## 4. Agent Implementation Details
- Implement `Position` value class or data class with $x, y, z$ coordinates and distance helpers.
- Implement `Tile` and `Map` classes holding spatial layers.
- Implement OTBM binary reader decoding OTBM node markers (`ESCAPE`, `NODE_START`, `NODE_END`).
- Provide spectator lookup caching per floor sector.

## 5. Test Reference
- `org.opentibia.canary.map.OtbmLoaderTest`: Parse a small synthetic OTBM test map and assert tile coordinates, ground item IDs, and towns.
- `org.opentibia.canary.map.SpectatorTest`: Test viewport queries around boundary positions $(x, y, z)$.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
