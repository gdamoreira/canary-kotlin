-- Seed default test accounts for PostgreSQL / H2

MERGE INTO accounts (id, name, "password", email, premdays, "type") KEY(id)
VALUES (1, '1', '1', 'test@opentibiabr.com', 90, 1);

MERGE INTO players (id, name, account_id, level, vocation, health, healthmax, mana, manamax, experience, town_id, posx, posy, posz, cap) KEY(id)
VALUES (1, 'Knight Hero', 1, 100, 1, 1800, 1800, 500, 500, 15000000, 1, 1000, 1000, 7, 2400);

MERGE INTO players (id, name, account_id, level, vocation, health, healthmax, mana, manamax, experience, town_id, posx, posy, posz, cap) KEY(id)
VALUES (2, 'Sorcerer Master', 1, 100, 2, 800, 800, 3000, 3000, 15000000, 1, 1000, 1000, 7, 1200);
