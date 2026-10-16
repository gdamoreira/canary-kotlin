# Feature: Canary Position Tables (Lua Value Shape)

## 1. Explanation
Canary's `Lua::pushPosition` pushes a **plain Lua table** carrying the fields
`x`, `y`, `z`, `stackpos` with the `Position` metatable attached
(lua_functions_loader.cpp:707-718). The datapack's own
`data/libs/functions/position.lua` depends on that shape: it mutates the fields
(`self.z = self.z - 1`), adds plain offset tables
(`self + Position.directionOffset[DIRECTION_SOUTH]`) and copies positions with
`Position(self)`. The port wrapped positions in userdata with a receiver-aware
`__index`, which cannot be written to and cannot be added to a table.

## 2. Impact
Every movement script routed through `Position:moveUpstairs`/`moveDownstairs`
(both defined in Lua), the ladder/rope/stairs datapack actions, teleport
destinations, `Position(pos)` copies and any script that stores or mutates a
position failed with `index expected, got userdata` — the live
`data/scripts/actions/items/ladder_up.lua` failure.

## 3. Functionality Affected
- Every Lua-visible position: `Tile:getPosition`, `Creature:getPosition`,
  `Item:getPosition`, `Teleport:getDestination`, `Town:getTemplePosition`,
  `House:getEntryPosition`, move-event `position(...)` setters, combat variants,
  zone positions, `NetworkMessage:getPosition`, event dispatch argument pushes.
- `__add`, `__sub`, `__eq` now read operands with `Lua::getPosition` semantics,
  including plain `{ x, y }` tables, and keep Canary's `stackpos` quirk.

## 4. Agent Implementation Details
- `bindings/core/CoreBindingSupport.kt`: `pushPosition(env, position, stackpos)`
  builds the field table and attaches the class metatable through
  `UserdataRegistry.attachMetatable` (`Lua::setMetatable`).
- `bindings/core/PositionFunctions.kt`: constructor/`__add`/`__sub`/`__eq`
  rewritten over `luaToPosition`; `positionArithmetic` mirrors
  `luaPositionAdd`/`luaPositionSub` (position_functions.cpp:75-103) including
  the `stackpos` propagation rule.
- `UserdataRegistry`: the userdata field-index hack is removed; positions are
  never userdata.
- All position readers across bindings use `luaToPosition`; all pushes use
  `pushPosition`.

## 5. Test Reference
- `PositionBindingsExtendedTest` — writable fields, table operands, copy
  constructor, equality.
- `CoreBindingsTest`, `VariantFunctionsBindingsTest`, `MoveEventsTest`,
  `ZoneFunctionsLibraryTest`, `NetworkMessageFunctionsLibraryTest`,
  `GlobalFunctionsLibraryTest`, `MonsterBindingsTest` — updated to the table
  contract.

## 6. Canary Original Code Reference
- **Source Files**: `src/lua/functions/lua_functions_loader.cpp`,
  `src/lua/functions/map/position_functions.cpp`,
  `src/lua/functions/creatures/combat/variant_functions.cpp`,
  `data/libs/functions/position.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods**: `Lua::pushPosition` (lua_functions_loader.cpp:707-718),
  `Lua::getPosition` (412-433), `PositionFunctions::luaPositionCreate` (47-72),
  `luaPositionAdd` (75-92), `luaPositionSub` (94-111), `luaPositionCompare`
  (113-119).

## Implementation Status: DELIVERED
Positions are Canary field tables again. `Position:moveUpstairs` and every
datapack script mutating a position work; full suite green.
