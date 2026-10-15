-- Nested inventory container persistence.
--
-- Canary stores the inventory tree inside `player_items` with `pid` = the
-- inventory slot for direct slot items and otherwise the parent container's
-- `sid`, while `sid` is the `IOLoginDataSave::saveItems` running id assigned
-- from 101 upward (schema.sql:599-612; `IOLoginDataSave::savePlayerItem`,
-- src/io/functions/iologindata_save_player.cpp:34-188, 534-564;
-- `IOLoginDataLoad::loadPlayerInventoryItems`,
-- src/io/functions/iologindata_load_player.cpp:543-632). V5 carried a flat
-- `slot`-keyed layout that cannot represent nested containers (the PARITY GAP
-- recorded by wave 49), so this migration turns the table into the Canary
-- `(pid, sid)` tree layout.
--
-- The existing `slot` column is renamed to `sid` (the primary key follows the
-- rename) and existing flat rows migrate with `pid = sid`, which keeps their
-- direct-slot semantics unchanged. New rows use the `saveItems` ids from 101
-- upward because every save replaces all rows.
ALTER TABLE player_inventory_items RENAME COLUMN slot TO sid;
ALTER TABLE player_inventory_items ADD COLUMN IF NOT EXISTS pid INT NOT NULL DEFAULT 0;
UPDATE player_inventory_items SET pid = sid WHERE pid = 0;
