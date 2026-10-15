-- House persistence schema, transcribed from Canary `schema.sql`:
-- `houses` (schema.sql:455-477), `house_lists` (schema.sql:489-499) and
-- `tile_store` (schema.sql:831-839).
--
-- Types keep the shared Kotlin port surface: Canary's integer columns that hold
-- 64-bit Java-side values (`paid`, bids) widen to BIGINT, and upstream
-- `longblob` columns stay LONGBLOB. `new_owner` defaults to -1 exactly like
-- Canary (it is the 'no pending transfer' marker `IOMapSerialize::loadHouseInfo`
-- tests with `newOwner >= 0`).

CREATE TABLE IF NOT EXISTS houses (
    id INT NOT NULL,
    owner BIGINT NOT NULL DEFAULT 0,
    new_owner BIGINT NOT NULL DEFAULT -1,
    paid BIGINT NOT NULL DEFAULT 0,
    warnings INT NOT NULL DEFAULT 0,
    name VARCHAR(255) NOT NULL DEFAULT '',
    rent BIGINT NOT NULL DEFAULT 0,
    town_id INT NOT NULL DEFAULT 0,
    size INT NOT NULL DEFAULT 0,
    guildid INT DEFAULT NULL,
    beds INT NOT NULL DEFAULT 0,
    bidder BIGINT NOT NULL DEFAULT 0,
    bidder_name VARCHAR(255) NOT NULL DEFAULT '',
    highest_bid BIGINT NOT NULL DEFAULT 0,
    internal_bid BIGINT NOT NULL DEFAULT 0,
    bid_end_date BIGINT NOT NULL DEFAULT 0,
    state SMALLINT NOT NULL DEFAULT 0,
    transfer_status SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT houses_pk PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_houses_owner ON houses (owner);
CREATE INDEX IF NOT EXISTS idx_houses_town_id ON houses (town_id);

CREATE TABLE IF NOT EXISTS house_lists (
    house_id INT NOT NULL,
    listid INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    list TEXT NOT NULL,
    CONSTRAINT house_lists_pk PRIMARY KEY (house_id, listid),
    CONSTRAINT houses_list_house_fk FOREIGN KEY (house_id) REFERENCES houses (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_house_lists_version ON house_lists (version);

CREATE TABLE IF NOT EXISTS tile_store (
    house_id INT NOT NULL,
    data LONGBLOB NOT NULL,
    CONSTRAINT tile_store_house_fk FOREIGN KEY (house_id) REFERENCES houses (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tile_store_house_id ON tile_store (house_id);
