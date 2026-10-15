-- Canary god account for an EXISTING database (fresh databases get it from
-- Flyway migration V23). Password: god  (SHA-1: 21298df8a3277357ee55b01df9530b535cf08ec1)
--
-- MariaDB / MySQL:
INSERT INTO accounts (id, name, `password`, email, `type`)
SELECT 2, 'god', '21298df8a3277357ee55b01df9530b535cf08ec1', '@god', 6 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE id = 2);

INSERT INTO players (id, name, group_id, account_id, level, vocation, health, healthmax, experience,
                     lookbody, lookfeet, lookhead, looklegs, looktype, maglevel, mana, manamax,
                     town_id, cap, sex, skill_club, skill_sword, skill_axe, skill_dist)
SELECT 7, 'GOD', 6, 2, 2, 0, 155, 155, 100,
       113, 115, 95, 39, 75, 0, 60, 60,
       8, 410, 1, 10, 10, 10, 10 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM players WHERE id = 7);

-- PostgreSQL variant (same rows, portable guards):
-- INSERT INTO accounts (id, name, "password", email, "type")
-- SELECT 2, 'god', '21298df8a3277357ee55b01df9530b535cf08ec1', '@god', 6
-- WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE id = 2);
-- INSERT INTO players (id, name, group_id, account_id, level, vocation, health, healthmax, experience,
--                      lookbody, lookfeet, lookhead, looklegs, looktype, maglevel, mana, manamax,
--                      town_id, cap, sex, skill_club, skill_sword, skill_axe, skill_dist)
-- SELECT 7, 'GOD', 6, 2, 2, 0, 155, 155, 100,
--        113, 115, 95, 39, 75, 0, 60, 60,
--        8, 410, 1, 10, 10, 10, 10
-- WHERE NOT EXISTS (SELECT 1 FROM players WHERE id = 7);
