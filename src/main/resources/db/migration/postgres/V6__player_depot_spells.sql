-- Depot, inbox and learned-instant-spell persistence.
--
-- Canary keeps the depot and inbox item trees in `player_depotitems` /
-- `player_inboxitems` and the learned instant-spell names in `player_spells`
-- (schema.sql:612-657 and schema.sql:780-789). Saving replaces all rows per
-- player (`IOLoginDataSave::savePlayerDepotItems`,
-- src/io/functions/iologindata_save_player.cpp:566-597; `savePlayerInbox`,
-- :634-659; `savePlayerSpells`, :416-445) and loading rebuilds the containers
-- (`IOLoginDataLoad::loadPlayerDepotItems`,
-- src/io/functions/iologindata_load_player.cpp:662-706; `loadPlayerInboxItems`,
-- :708-753; `loadPlayerInstantSpellList`, :527-541).
--
-- The row layout matches Canary (`pid` carries the parent container sid, or the
-- depot chest id below 100; `sid` is the `IOLoginDataSave::saveItems` running id
-- starting at 101), but this port persists the flat rows only: rebuilding the
-- container tree from `pid`/`sid` remains a PARITY GAP. The `attributes` blob
-- reuses the V5 `Item::serializeAttr` codec.
CREATE TABLE IF NOT EXISTS player_depotitems (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    sid INT NOT NULL,
    pid INT NOT NULL DEFAULT 0,
    itemtype INT NOT NULL DEFAULT 0,
    count INT NOT NULL DEFAULT 1,
    attributes BYTEA DEFAULT NULL,
    PRIMARY KEY (player_id, sid)
);

CREATE TABLE IF NOT EXISTS player_inboxitems (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    sid INT NOT NULL,
    pid INT NOT NULL DEFAULT 0,
    itemtype INT NOT NULL DEFAULT 0,
    count INT NOT NULL DEFAULT 1,
    attributes BYTEA DEFAULT NULL,
    PRIMARY KEY (player_id, sid)
);

CREATE TABLE IF NOT EXISTS player_spells (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (player_id, name)
);
