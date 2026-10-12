# Feature: OTBM Binary Map Parser (IOMap)

## 1. Explanation
Parses binary OpenTibia Map (`.otbm`) files (`src/io/iomap.cpp`, `fileloader.cpp`). Handles the tree-structured binary serialization format using node markers (`NODE_START = 0xFE`, `NODE_END = 0xFF`, `ESCAPE_CHAR = 0xFD`), decoding map metadata, header dimensions, tile areas, individual tiles, house tiles, and town definitions into `GameMap`.

## 2. Impact
Critical foundational loader. Without this, the server cannot load real Tibia world maps (e.g., standard Canary map, Real Server map, or custom maps) and cannot position tiles, towns, or houses.

## 3. Functionality Affected
- **Binary Node Decoding**: Unescapes bytes and traverses nested node trees.
- **Header Parsing**: Version, map dimensions ($width \times height$), major/minor version, item format versions.
- **Tile Area Resolution**: Base $(x, y, z)$ coordinates and offset tiles.
- **Tile Flag Parsing**: Protection Zone (PZ), No-Logout, Refresh, House tiles.
- **Town Loader**: Town ID, town name, temple spawn position $(x, y, z)$.

## 4. Agent Implementation Details
- Implement `OtbmNodeReader.kt` with byte unescaping and recursive/iterative node traversal.
- Implement `OtbmMapLoader.kt` decoding `OTBM_ROOT`, `OTBM_MAP_DATA`, `OTBM_TILE_AREA`, `OTBM_TILE`, `OTBM_HOUSETILE`, and `OTBM_TOWNS`.
- Populate `GameMap` instances with tiles and ground items parsed from binary nodes.

## 5. Test Reference
- `org.opentibia.canary.io.OtbmLoaderTest`: Tests parsing a synthetic binary OTBM buffer with header, town list, and 3x3 tile area with ground and house tiles.
