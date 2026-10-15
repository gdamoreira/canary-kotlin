-- Player inventory item persistence, one row per equipped inventory slot.
--
-- Canary stores inventory slots inside the shared `player_items` tree
-- (`pid` = inventory slot, `sid` = generated item id, `itemtype`, `count` and
-- an `attributes` blob; see iologindata_save_player.cpp:543-563 and
-- iologindata_load_player.cpp:543-561). That table already exists from V1 and
-- is left untouched. This additive table carries the flat slot layout the
-- Kotlin port persists (`slot`, `item_id`, `count`, serialized `attributes`);
-- nested containers are a PARITY GAP.
CREATE TABLE IF NOT EXISTS player_inventory_items (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    slot INT NOT NULL,
    item_id INT NOT NULL,
    count INT NOT NULL DEFAULT 1,
    attributes LONGBLOB DEFAULT NULL,
    PRIMARY KEY (player_id, slot)
);
