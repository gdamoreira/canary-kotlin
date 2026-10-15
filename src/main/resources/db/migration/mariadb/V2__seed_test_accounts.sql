-- Seed default test accounts for MariaDB (idempotent guard: MariaDB rejects a
-- WHERE clause without FROM, so the portable `INSERT ... SELECT ... FROM DUAL
-- WHERE NOT EXISTS` form keeps the seed re-runnable)

INSERT INTO accounts (id, name, `password`, email, premdays, `type`)
SELECT 1, '1', '1', 'test@opentibiabr.com', 90, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE id = 1);

INSERT INTO players (id, name, account_id, level, vocation, health, healthmax, mana, manamax, experience, town_id, posx, posy, posz, cap)
SELECT 1, 'Knight Hero', 1, 100, 1, 1800, 1800, 500, 500, 15000000, 1, 1000, 1000, 7, 2400 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM players WHERE id = 1);

INSERT INTO players (id, name, account_id, level, vocation, health, healthmax, mana, manamax, experience, town_id, posx, posy, posz, cap)
SELECT 2, 'Sorcerer Master', 1, 100, 2, 800, 800, 3000, 3000, 15000000, 1, 1000, 1000, 7, 1200 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM players WHERE id = 2);
