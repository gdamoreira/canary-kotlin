# Feature: Real Map Loading Pipeline (OTBM, House XML, Spawn XML)

## 1. Explanation
Specifies the full map initialization pipeline reading actual OpenTibia world files (`src/io/iomap.cpp`):
1. **OTBM Binary Map File (`world/otservbr.otbm`)**:
   - Header: Map width ($0..65535$), height ($0..65535$), major/minor version.
   - Towns: Town IDs, town names, temple spawn positions $(x, y, z)$.
   - Tile Areas: 256x256 spatial blocks containing individual tiles, ground items, stacked decorative items, walls, doors, and teleporters.
   - House Tiles: Tiles marked with `OTBM_HOUSETILE` linking tile coordinates to specific House IDs.
2. **Houses XML (`world/otservbr-house.xml`)**:
   - Houses: House ID, house name, entry coordinate $(x, y, z)$, rent, size in tiles, guildhall flag.
   - Doors: Associates physical door coordinates with house entry permissions.
3. **Spawns XML (`world/otservbr-spawn.xml`)**:
   - Monster spawns: Monster name, center position $(x, y, z)$, radius, respawn interval in seconds.
   - NPC spawns: NPC name, static position $(x, y, z)$.

## 2. Impact
Constructs the actual spatial reality of the game world. Maps can exceed 100,000,000 tiles and gigabytes of memory; a streaming, memory-efficient loader is mandatory.

## 3. Functionality Affected
- **Memory Footprint**: Uses primitive collections or sparse spatial matrices to avoid huge object allocation overhead for unpopulated tiles.
- **Town Registry**: Temple coordinates for player login, death respawning, and depot access.
- **House Integration**: Automatically populates `House.tiles` from parsed `OTBM_HOUSETILE` nodes and links doors to house IDs.

## 4. Agent Implementation Details
- Implement `MapPipelineService.kt` orchestrating OTBM, House XML, and Spawn XML ingestion.
- Stream large OTBM files using Netty's chunked file reader (`FileRegion` / direct memory buffers).
- Validate that all house tiles belong to a registered house entry from `house.xml`.

## 5. Test Reference
- `org.opentibia.canary.map.MapPipelineServiceTest`: Loads an integration test suite comprising an OTBM file, a matching `house.xml`, and a `spawn.xml`, verifying towns, houses, tiles, and monster spawn points are loaded and linked.
