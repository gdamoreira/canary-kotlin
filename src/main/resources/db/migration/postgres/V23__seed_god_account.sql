-- Seed the Canary god account (schema.sql:869-884 parity): account `god`/`god`
-- (SHA-1 hash), account type 6, and the `GOD` player in group 6 (access).
-- The upstream schema uses account id 1 for `god`; this port's V2 already owns
-- id 1 (account `1`), so the god account takes id 2 and the player keeps the
-- upstream id 7. Idempotent guards keep the seed re-runnable.

INSERT INTO accounts (id, name, "password", email, "type")
SELECT 2, 'god', '21298df8a3277357ee55b01df9530b535cf08ec1', '@god', 6
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE id = 2);

INSERT INTO players (id, name, group_id, account_id, level, vocation, health, healthmax, experience,
                     lookbody, lookfeet, lookhead, looklegs, looktype, maglevel, mana, manamax,
                     town_id, cap, sex, skill_club, skill_sword, skill_axe, skill_dist)
SELECT 7, 'GOD', 6, 2, 2, 0, 155, 155, 100,
       113, 115, 95, 39, 75, 0, 60, 60,
       8, 410, 1, 10, 10, 10, 10
WHERE NOT EXISTS (SELECT 1 FROM players WHERE id = 7);
