# Feature: House System, House Tiles & Rent Management

## 1. Explanation
Manages player houses, house doors, house tiles, sub-owners, guest lists, rent collection, and house auctions. In Canary, house data is parsed from the map (`house.xml`) and saved into MySQL/MariaDB (`houses` and `house_lists` tables).

## 2. Impact
Critical for player persistence, player economy, and map tile security. House tiles reject non-authorized occupants and prevent item loss across server restarts.

## 3. Functionality Affected
- **House Tile Protection**: Prevents unauthorized players from walking onto house tiles or taking house items.
- **Door Access Lists**: Manages guest lists (`aleta grav`), sub-owner lists (`aleta sio`), and door permissions (`aleta som`).
- **House State Transitions**: House purchase, transfer, eviction, and daily/monthly rent deductions.

## 4. Agent Implementation Details
- Implement `House` and `HouseTile` models in `org.opentibia.canary.map.house`.
- Implement `HouseManager` keeping active house registries and town associations.
- Parse house metadata from XML and map OTBM nodes.
- Expose permission evaluation methods (`canEnter(player, position)`).

## 5. Test Reference
- `org.opentibia.canary.map.house.HouseTest`: Tests permission check for owner, guest, and unauthorized intruder. Tests rent deduction and eviction.
