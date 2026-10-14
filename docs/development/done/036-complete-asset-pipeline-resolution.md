# Feature: Asset Pipeline & Canary Data Pack Resolution

## 1. Explanation
Specifies the global asset ingestion pipeline that locates, resolves, validates, and loads real Canary game content files into the server runtime:
- **Data Pack Directory Structure**:
  - `data/world/`: Contains `.otbm` binary map files, `house.xml`, and `spawns.xml`.
  - `data/items/`: Contains `items.otb`, `items.xml`, `appearances.dat`, `proficiencies.json`.
  - `data/XML/`: Contains `vocations.xml`, `outfits.xml`, `mounts.xml`, `spells.xml`, `groups.xml`.
  - `data/monster/` & `data/scripts/`: Monster definitions, loot tables, and spell scripts.
  - `data/npclib/` & `data/npc/`: NPC definitions, dialogue scripts, and merchant inventories.
- **Data Pack Resolution Policy**:
  - Configurable via `CANARY_DATA_DIR` or `server.dataDir` in `ServerConfig`.
  - Automated downloader/resolver for map packs if running inside Docker (`CANARY_MAP_URL` e.g. `otservbr.otbm`).

## 2. Impact
Critical bridge between engine code and game content. Without a unified asset pipeline, individual parsers cannot establish relations (e.g. Map loader referencing unknown Item IDs from OTB, or Spawn loader referencing unparsed Monster types).

## 3. Functionality Affected
- **Startup Boot Order**:
  1. `OtbItemsLoader` + `XmlItemLoader` $\to$ builds `ItemTypeRegistry`.
  2. `XmlVocationLoader` + `SpellsLoader` $\to$ builds vocation & spell rules.
  3. `MonsterLoader` + `NpcLoader` $\to$ builds creature templates.
  4. `OtbmMapLoader` + `HouseXmlLoader` + `SpawnXmlLoader` $\to$ builds live `GameMap`.
- **Validation**:
  - Detects duplicate item IDs, missing sprite mappings, invalid town IDs, and out-of-bounds spawn coordinates.

## 4. Agent Implementation Details
- Implement `AssetManager.kt` coordinating the sequential boot sequence across all file loaders.
- Expose health status: total items loaded, total map tiles loaded, total monster templates, total NPCs.
- Provide detailed startup logging matching Canary's benchmark timings.

## 5. Test Reference
- `org.opentibia.canary.io.AssetPipelineTest`: Boots a synthetic data directory containing sample OTB, XML, and OTBM files, asserting complete registration across all registries without exceptions.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
