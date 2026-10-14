# Startup loading performance notes (Kotlin map loader)

> Adapted from canary/docs/maps/startup-loading-performance.md for the
> Kotlin/JVM engine.
> Canary-Kotlin status: adapted to the Kotlin OTBM pipeline, appearance
> protobuf and `MapDownload`. The upstream C++ profiling progression and
> measured table are retained below as upstream-engine evidence; they are not
> Kotlin measurements.

## Summary

The upstream profiling cycle showed that the startup bottleneck moves: first
Lua script loading and filesystem work, then map parsing and tile cache
construction, with monster spawn startup as a secondary hotspot. The Kotlin
port re-implements the same load order and the same cache concepts, so the same
guidance applies: profile before optimizing, expect map parsing to dominate, and
treat tile cache construction as the next cost.

These notes document the Kotlin pipeline and the invariants that must not be
broken while optimizing this path.

## Kotlin startup pipeline

Startup order (see `bootstrap/ServerBootstrap.kt` and `Main.kt`):

1. **Configuration** — `ConfigManager` resolves CLI > properties file >
   `CANARY_*` environment > defaults.
2. **Database** — HikariCP connects and Flyway applies `V1`–`V21` before any
   listener starts.
3. **Assets and items** — `io/pipeline/AssetManager.kt` and
   `ItemPipelineService` load item types from the OTB catalog
   (`io/otb/OtbItemsLoader.kt`) plus `data/items/items.xml` overrides;
   `io/appearances/Appearances.kt` parses the appearance protobuf (including
   the metadata consumed by the proficiency system).
4. **Map** — `io/pipeline/MapPipelineService.kt` reads the OTBM file into a
   Netty `ByteBuf` and parses it with `io/otbm/OtbmMapLoader.kt` into the
   `GameMap`/`MapGrid` spatial structures, then loads house and spawn XML.
5. **Lua scripts** — `lua/loader/Scripts.kt` and the LuaJ runtime load the
   `data/` trees; `LuaScriptBridge` installs the Canary-named bindings.
6. **Monsters/NPCs** — `io/pipeline/MonsterPipelineService.kt` and the NPC
   pipeline build runtime definitions; spawners are registered from step 4.
7. **Network** — `network/CanaryServer.kt` starts the login/game/status
   listeners only after the world is loaded.

Relevant configuration keys (`config/ConfigKeys.kt`):

```text
CORE_DIRECTORY        coreDirectory           (default "data")
DATA_DIRECTORY        dataPackDirectory       (default "data-otservbr-global")
MAP_NAME              mapName                 (default "canary")
MAP_DOWNLOAD_URL      mapDownloadUrl
TOGGLE_DOWNLOAD_MAP   toggleDownloadMap
TOGGLE_MAP_CUSTOM     toggleMapCustom
LUA_SCRIPT_BYTECODE_CACHE_PATH  luaScriptBytecodeCachePath
```

`CANARY_DATA_DIR` overrides the datapack directory for local runs
(`ConfigManager`).

### OTBM parsing

`OtbmMapLoader` reads the same node stream as upstream (`0xFE` node start,
`0xFF` node end, `0xFD` escape; tile area/item/tile/house-tile nodes and
attributes) through `io/PropStream.kt`. Tiles flow into `map/cache/MapCache.kt`
where `BasicTile` entries are hashed, deduplicated (`isCacheShareable`) and
retained. `GameMap`/`MapGrid` store the realized tiles in sector chunks
(`map/MapGrid.kt`, `MapCache.Floor`), and `NavigationSnapshot` exposes immutable
navigation sectors for pathing consumers.

### Appearance protobuf

`io/appearances/Appearances.kt` parses the appearance catalog, including the
weapon-proficiency metadata consumed by `systems/proficiency/`. Invalid or
out-of-domain objects must be reported and not promoted to definitions, matching
the upstream `Items::loadFromProtobuf` rules.

### MapDownload

`map/cache/MapDownload.kt` ports the upstream map URL validation: it extracts a
Canary release tag from the configured `mapDownloadUrl`, compares it with the
current `config.lua.dist` values (upstream artifacts) and warns when the
configured URL points at a different release. This is a startup warning path,
not a blocker.

## Profiling guidance (JVM)

Use a production-like invocation for meaningful startup profiles:

```bash
./gradlew installDist
JAVA_OPTS="-XX:+FlightRecorder ..." ./build/install/canary-kotlin/bin/canary-kotlin
```

- Run through the installed distribution, not `./gradlew run`; the Gradle
  daemon and configuration phase distort startup measurements.
- Let the JIT warm before comparing throughput; for a pure startup profile,
  record the wall time from process start to the server-online log.
- Use JDK Flight Recorder or async-profiler for sampled CPU and allocation
  profiles. Prefer allocation profiles first: map parsing is allocation-heavy
  on the JVM as well.
- Compare repeated captures. Startup measurements are sensitive to filesystem
  cache state, antivirus activity, database timing, CPU frequency and
  background load.
- Prefer comparing allocation/call shape across captures, not only percentages.
  The denominator changes when other startup phases get faster or slower.

Useful startup log timings:

```text
Flyway migrations applied
Loaded items
Loaded map
Loaded scripts
Server online
```

## Upstream observed progression (C++ evidence)

The broad upstream profile progression was:

1. Lua loading and filesystem metadata were initially the dominant startup cost.
2. Lua bytecode caching, metadata reuse, memory-buffer loading and packed cache
   entries reduced `Loaded modules and scripts` to low single-digit seconds.
3. After Lua improved, map loading became dominant, especially
   `IOMap::parseTileArea`, `MapCache::setBasicTile` and
   `MapCache::getOrCreateBasicTileFromCache`.
4. After map cache fast paths and reserve work, monster spawn startup became
   visible enough to optimize.
5. After spawn placement changes, the remaining hotspots were again mostly map
   parse/cache construction and floor allocation.

Representative post-optimization upstream profiles showed map parsing around
half of sampled startup CPU and tile cache construction around one fifth. These
ratios are upstream direction, not fixed Kotlin performance claims.

## Upstream measured before/after (C++ evidence)

**The table below is upstream C++ profiling evidence; it is not a Kotlin
measurement.** It is retained to prioritize future Kotlin profiling work.

| Area | Before | After | Result |
| --- | ---: | ---: | --- |
| `Loaded modules and scripts` wall time | ~54s | 1.516s best observed, 2.997s latest capture | About 94-97% less wall time |
| `Scripts::loadScripts` | 17.60% | 9.31-15.00% across later captures | Lua loader no longer primary startup bottleneck |
| `LuaScriptInterface::loadFile` | 18.20% | 11.83-19.06% across later captures | File loading cost reduced |
| `luaL_loadfile` | hot in the original profile | 1.60-2.80% | Raw file parsing no longer dominant |
| `CanaryServer::loadMaps` | 65.59% after Lua improved | 57.68% latest capture | Map loading remains the largest hotspot |
| `IOMap::parseTileArea` | 59.58% | 53.87% | Tile area parsing still the main map cost |
| `MapCache::setBasicTile` | 24.96% | 20.50% | Lower tile cache insertion/setup overhead |
| `MapSector::createFloor` | 7.17% | 5.76% | Less sector/floor growth overhead |
| `SpawnMonster::startup` | 11.74% | 9.56% | Spawn startup cost reduced |

## Kotlin map cache design notes

The Kotlin port preserves the upstream cache shape:

- **Deduplication**: shareable `BasicTile` instances are hashed and reused;
  complex/mutable tiles skip dedupe because merging mutable state is unsafe.
- **Retention**: `MapCache.retainedBasicTiles` owns the shared `BasicTile`
  instances; hot lookups reuse them without re-allocation.
- **Floor cursor**: `MapCacheFloorCursor` is a caller-owned, stack-local hint
  valid only inside one synchronous search or walk-check batch, mirroring
  upstream's floor cursor contract.
- **Reserve**: `MapCache.reserveForMap(width, height, fileSize)` pre-sizes the
  cache after the map header is known.
- **Sector chunking**: floors live in `MapCache.Floor` structures inside
  `MapGrid` sectors; `NavigationSnapshot` exposes immutable navigation sectors
  with topology/occupancy revisions for pathing consumers.
- **Flush**: `MapCache.flush()` clears retained entries and resets the lookup
  cache; do not keep references to cache-owned tiles after a flush.

## Lua startup notes

Upstream's Lua bytecode cache (`luaScriptBytecodeCache`,
`luaScriptBytecodeCachePath`, packed `.luapack` files) is a C++ feature that is
**not ported**. `lua/runtime/LuaScriptInterface.kt` documents this parity gap:
the port loads Lua sources through LuaJ directly. The configuration keys exist
for compatibility but the cache path is not consumed by the loader. Do not
claim bytecode-cache behavior for the Kotlin engine.

## Invariants to preserve

Map loading optimizations must preserve these behaviors:

- Do not dedupe complex tiles in a way that can merge mutable state.
- Do not change item creation order for action id, unique id, containers or item
  attributes.
- Do not let cached map tiles outlive the map cache that produced them; a
  `MapCacheFloorCursor` is valid only for one synchronous search.
- Do not skip zone callbacks during real creature placement.
- Keep monster spawn weighting behavior aligned with the upstream
  weighted-random selection.
- Do not enable Lua debug hooks by default.
- Do not leave startup telemetry enabled by default.
- Keep item/appearance loading tolerant but explicit: invalid protobuf objects
  are reported, not silently promoted.

## Validation checklist

For future profiling rounds:

1. Build the distribution (`./gradlew installDist`) and run it directly.
2. Keep the server log timings for each startup phase.
3. Capture an allocation profile and a CPU profile; inspect the Kotlin map
   parsing/cache frames first.
4. Compare repeated captures by wall-clock startup log timings and call shape.
5. Verify map behavior after any change: action ids, unique ids, containers,
   houses, zones, monster spawns, NPC/monster script loading.
6. Treat datapack warnings separately from performance changes unless the
   warning appears only after a specific code change with a reproducible minimal
   case.
