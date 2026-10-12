# Feature: OTB Items & appearances.dat Binary Loader

## 1. Explanation
Parses binary `items.otb` (`src/items/items.cpp`) and modern `appearances.dat` Protobuf files (`data/items/appearances.dat`). Reads item category groupings (Ground, Container, Weapon, Armor, Teleport, Magic Field, Writeable, Key), client sprite ID mapping, stackability, pickupability, and collision flags.

## 2. Impact
Essential for game assets. Without OTB loading, the server cannot know which server Item IDs correspond to client sprite representations, which items are walkable or blocking, or how containers behave.

## 3. Functionality Affected
- **Item Classification**: Ground items, top order items, containers, fluids, doors.
- **Server-to-Client ID Mapping**: Server Item ID $\to$ Client Sprite ID translation.
- **Item Flags**: `BLOCK_SOLID`, `BLOCK_PROJECTILE`, `BLOCK_PATHFIND`, `HAS_HEIGHT`, `USEABLE`, `PICKUPABLE`, `STACKABLE`.

## 4. Agent Implementation Details
- Implement `OtbReader.kt` utilizing the binary node parser to traverse `ROOT` and `ITEM` nodes.
- Implement attribute readers for `ATTR_SERVER_ID`, `ATTR_CLIENT_ID`, `ATTR_SPEED`, `ATTR_LIGHT`, `ATTR_TOPORDER`.
- Populate `ItemTypeRegistry` with populated `ItemType` instances ready for game simulation.

## 5. Test Reference
- `org.opentibia.canary.items.OtbLoaderTest`: Tests parsing sample binary OTB item nodes and asserts client sprite ID translation and flag bitmasks.
