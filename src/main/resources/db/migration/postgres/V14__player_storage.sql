-- Typed player storage key/value persistence.
--
-- Canary keeps one row per (player_id, key) in `player_storage` with a signed
-- int32 `value` (schema.sql:803-811). `DbPlayerStorageRepository` loads the
-- rows, deletes key batches and bulk-upserts key/value pairs
-- (src/io/player_storage_repository_db.cpp:25-57); `IOLoginDataSave::savePlayerStorage`
-- consumes the `PlayerStorage` delta as DELETE first and UPSERT second
-- (src/io/functions/iologindata_save_player.cpp:837-857).
--
-- Canary's `uint32_t key` column is mapped to a signed `INT` because the port
-- models storage keys as [Int] (reserved range tops out at 20,000,000), exactly
-- like `PlayerStorageRow` in the component port.
CREATE TABLE IF NOT EXISTS player_storage (
    player_id BIGINT NOT NULL DEFAULT 0 REFERENCES players (id) ON DELETE CASCADE,
    "key" INT NOT NULL DEFAULT 0,
    "value" INT NOT NULL DEFAULT 0,
    CONSTRAINT player_storage_pk PRIMARY KEY (player_id, "key")
);
