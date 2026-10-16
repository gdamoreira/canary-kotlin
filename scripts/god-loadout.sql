-- GOD account endgame loadout: level 4000 with the best available gear.
--
-- Run against the GAME database (MariaDB, the stack default):
--   docker exec -i canary-kotlin-mariadb mariadb -uroot -proot canary < scripts/god-loadout.sql
-- or in PostgreSQL mode:
--   docker exec -i canary-kotlin-postgres psql -U canary -d canary < scripts/god-loadout.sql
--
-- IMPORTANT (split-DB stacks): the character list page is served by the
-- upstream `opentibiabr/login-server`, which is MySQL-only and reads the
-- `mariadb` service, while the game server uses PostgreSQL. Run the player
-- UPDATE below against BOTH databases so the list shows level 4000; the
-- `player_inventory_items` INSERT belongs to the game database alone. Prefer
-- the single-DB mode (`docker-compose.mariadb.yml`) to avoid the divergence.
--
-- Experience is Canary's `getExpForLevel(4000)` threshold
-- (creature.cpp: `(50n^3 - 300n^2 + 850n - 600) / 3`).
-- Equipment item ids come from the shipped items.xml
-- (server `data/items/items.xml`); slots follow CONST_SLOT_HEAD..CONST_SLOT_RING
-- (creatures_definitions.hpp).

UPDATE players SET
  level = 4000,
  experience = 1065067799800,
  health = 60005,
  healthmax = 60005,
  mana = 20005,
  manamax = 20005,
  cap = 80380,
  soul = 200,
  speed = 1500,
  maglevel = 200,
  skill_fist = 500,
  skill_club = 500,
  skill_sword = 500,
  skill_axe = 500,
  skill_dist = 500,
  skill_shielding = 500,
  skill_fishing = 500,
  save = 1
WHERE name = 'GOD';

-- The port persists the inventory in `player_inventory_items` with `pid` =
-- slot (CONST_SLOT_FIRST..CONST_SLOT_LAST, iologindata_load_player.cpp:543-632)
-- and `sid` a unique per-player item id.
DELETE FROM player_inventory_items
WHERE player_id = (SELECT id FROM players WHERE name = 'GOD');

INSERT INTO player_inventory_items (player_id, sid, item_id, count, attributes, pid) VALUES
  ((SELECT id FROM players WHERE name = 'GOD'), 1001, 28715, 1, NULL, 1), -- falcon coif
  ((SELECT id FROM players WHERE name = 'GOD'), 1002, 21170, 1, NULL, 2), -- gearwheel chain
  ((SELECT id FROM players WHERE name = 'GOD'), 1003, 3253,  1, NULL, 3), -- backpack of holding
  ((SELECT id FROM players WHERE name = 'GOD'), 1004, 28719, 1, NULL, 4), -- falcon plate
  ((SELECT id FROM players WHERE name = 'GOD'), 1005, 43865, 1, NULL, 5), -- grand sanguine blade
  ((SELECT id FROM players WHERE name = 'GOD'), 1006, 28722, 1, NULL, 6), -- falcon escutcheon
  ((SELECT id FROM players WHERE name = 'GOD'), 1007, 43876, 1, NULL, 7), -- sanguine legs
  ((SELECT id FROM players WHERE name = 'GOD'), 1008, 43884, 1, NULL, 8), -- sanguine boots
  ((SELECT id FROM players WHERE name = 'GOD'), 1009, 32636, 1, NULL, 9); -- ring of souls
