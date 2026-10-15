-- Player mount/familiar look columns for `IOLoginDataLoad::loadPlayerDefaultOutfit`.
--
-- Canary defines the five columns on `players` (schema.sql:144-148):
--   * `lookmountbody`, `lookmountfeet`, `lookmounthead`, `lookmountlegs`
--     tinyint(3) unsigned NOT NULL DEFAULT '0' -> `SMALLINT`.
--   * `lookfamiliarstype` int(11) unsigned NOT NULL DEFAULT '0' -> `INT`.
-- Read by `loadPlayerDefaultOutfit`
-- (src/io/functions/iologindata_load_player.cpp:291-317) and applied to
-- `player->defaultOutfit` before the `player->currentOutfit = 
-- player->defaultOutfit` copy.
ALTER TABLE players ADD COLUMN IF NOT EXISTS lookmounthead SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS lookmountbody SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS lookmountlegs SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS lookmountfeet SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS lookfamiliarstype INT NOT NULL DEFAULT 0;
