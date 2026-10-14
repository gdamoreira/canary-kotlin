# Feature: Items & Object Serialization (OTB & XML / Protobuf)

## 1. Explanation
Loads and parses item definitions from Tibia's `.otb` (OpenTibia Binary format) and `items.xml` / Protobuf schemas. Manages item attributes (weight, attack, defense, slot position, container capacity, decay, writeable, fluids).

## 2. Impact
Critical dependency for the map loader, creature inventory, container handling, combat mechanics, and ground interactions.

## 3. Functionality Affected
- **Item Registry**: Maps server item IDs to client sprite IDs.
- **Item Types & Flags**: `ItemType` definitions (unmoveable, block solid, block path, has height, stackable, pickupable, etc.).
- **Item Instances**: Factory creating `Item`, `Container`, `Teleport`, `TrashHolder`, etc.

## 4. Agent Implementation Details
- Implement binary OTBM node reader (`OtbmNodeReader.kt`) capable of parsing tree-structured OTBM/OTB files.
- Implement `ItemTypeLoader` parsing `items.otb` headers and node attributes.
- Implement XML/JSON parser for extended properties (decaying, transforms, armor, charges, weapon types).
- Provide high-performance item lookup by ID with fast primitive arrays.

## 5. Test Reference
- `org.opentibia.canary.items.ItemLoaderTest`: Parse test `items.otb` snippet and verify attribute assignment (stackable, containers, weapon types).


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
