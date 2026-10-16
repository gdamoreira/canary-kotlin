-- GOD account endgame loadout: level 4000 with the best available gear.
--
-- Run against the server database (PostgreSQL or MariaDB):
--   psql -U canary -d canary -f scripts/god-loadout.sql
--   mariadb -uroot -p canary < scripts/god-loadout.sql
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

DELETE FROM player_items
WHERE player_id = (SELECT id FROM players WHERE name = 'GOD');

INSERT INTO player_items (player_id, pid, sid, itemtype, count, attributes) VALUES
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 1, 28715, 1, NULL), -- falcon coif
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 2, 21170, 1, NULL), -- gearwheel chain
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 3, 3253,  1, NULL), -- backpack of holding
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 4, 28719, 1, NULL), -- falcon plate
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 5, 43865, 1, NULL), -- grand sanguine blade
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 6, 28722, 1, NULL), -- falcon escutcheon
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 7, 43876, 1, NULL), -- sanguine legs
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 8, 43884, 1, NULL), -- sanguine boots
  ((SELECT id FROM players WHERE name = 'GOD'), 0, 9, 32636, 1, NULL); -- ring of souls
