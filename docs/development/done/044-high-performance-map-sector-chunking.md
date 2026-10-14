# Feature: High-Performance Primitive Spatial Map (MapSector Chunking)

## 1. Explanation
Replaces sparse object hash maps for `Tile` storage with a chunked 2D/3D sector architecture matching Canary's `MapSector` (`src/map/utils/mapsector.hpp`). The global map is divided into 32x32 spatial sectors; unallocated wilderness areas consume zero memory, while populated sectors store tiles in fixed 2D arrays.

## 2. Impact
Solves the primary memory bottleneck on the JVM. In a flat `Map<Position, Tile>`, 50,000,000 tiles require $\approx 8\text{ GB}$ just for map entry references. In sector arrays, spatial lookup is $O(1)$ pointer math, and memory consumption drops by over 80%.

## 3. Functionality Affected
- **Sector Math**: Sector coordinates $(sx, sy, sz) = (x / 32, y / 32, z)$.
- **Internal Tile Array**: Inside each sector, tiles are stored in a flat `Array<Tile?>(32 * 32)` indexed by $(x \% 32) + (y \% 32) * 32$.
- **Fast Viewport Queries**: Spectator queries only check the 4 overlapping sectors rather than searching all active tiles.

## 4. Agent Implementation Details
- Implement `MapSector.kt` holding `Array<Tile?>(1024)`.
- Update `GameMap.kt` to index `MapSector` by a packed 64-bit integer key `(sz shl 32) or (sx shl 16) or sy`.
- Expose fast range iterator yielding non-null tiles within a bounding box.

## 5. Test Reference
- `org.opentibia.canary.map.MapSectorTest`: Populates 1,000 tiles across 4 adjacent sectors, asserts correct tile recovery, and verifies unallocated sectors consume zero allocations.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
