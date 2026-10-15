-- Player login-data persistence for the `IOLoginData` surface.
--
-- Covers the load/save state that Canary keeps outside the existing V1-V9
-- tables (`schema.sql` is the Canary source of truth; the Kotlin port follows
-- it with MariaDB-compatible types):
--   * storage map        -> `player_storage`      (schema.sql:802-812); loaded by
--     `IOLoginDataLoad::loadPlayerStorageMap` (iologindata_load_player.cpp:760-768)
--     and flushed by `IOLoginDataSave::savePlayerStorage` (iologindata_save_player.cpp:837-859)
--     through `DbPlayerStorageRepository::load/deleteKeys/upsert`
--     (src/io/player_storage_repository_db.cpp:25-57).
--   * unjustified kills  -> `player_kills`        (schema.sql:688-698); loaded by
--     `IOLoginDataLoad::loadPlayerKills` (iologindata_load_player.cpp:371-392)
--     and replaced by `IOLoginDataSave::savePlayerKills` (iologindata_save_player.cpp:447-476).
--   * supply stash       -> `player_stash`        (schema.sql:791-800); loaded by
--     `IOLoginDataLoad::loadPlayerStashItems` (iologindata_load_player.cpp:444-470)
--     and replaced by `IOLoginDataSave::savePlayerStash` (iologindata_save_player.cpp:374-414).
--   * reward chest       -> `player_rewards`      (schema.sql:766-777); loaded by
--     `IOLoginDataLoad::loadRewardItems` (iologindata_load_player.cpp:645-661)
--     and replaced by `IOLoginDataSave::saveRewardItems` (iologindata_save_player.cpp:599-632).
--   * VIP list/groups    -> `account_viplist`, `account_vipgroups`,
--     `account_vipgrouplist` (schema.sql:206-266): read by
--     `IOLoginData::getVIPEntries` / `loadPlayerVip`
--     (src/io/iologindata.cpp:363-385, iologindata_load_player.cpp:770-806) and
--     written by the `IOLoginData` VIP mutators (iologindata.cpp:387-499).
--
-- `players.balance` backs `IOLoginData::increaseBankBalance`
-- (src/io/iologindata.cpp:357-361) and the `balance` column of
-- `IOLoginDataSave::savePlayerFirst` (iologindata_save_player.cpp:322).
-- `players.pronoun` and the eight `blessingsN` columns back
-- `IOLoginDataLoad::loadPlayerBasicInfo` / `loadPlayerBlessings`
-- (iologindata_load_player.cpp:150, 246-255) and the `savePlayerFirst` mirrors
-- (iologindata_save_player.cpp:247, 364-366).

ALTER TABLE players ADD COLUMN IF NOT EXISTS balance BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS pronoun INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS blessings1 INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS blessings2 INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS blessings3 INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS blessings4 INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS blessings5 INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS blessings6 INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS blessings7 INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS blessings8 INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS player_storage (
    player_id BIGINT NOT NULL DEFAULT 0 REFERENCES players (id) ON DELETE CASCADE,
    `key` INT NOT NULL DEFAULT 0,
    `value` INT NOT NULL DEFAULT 0,
    CONSTRAINT player_storage_pk PRIMARY KEY (player_id, `key`)
);

CREATE TABLE IF NOT EXISTS player_kills (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    `time` BIGINT NOT NULL DEFAULT 0,
    target BIGINT NOT NULL DEFAULT 0,
    unavenged BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_player_kills_player_id ON player_kills (player_id);

CREATE TABLE IF NOT EXISTS player_stash (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    item_id INT NOT NULL,
    item_count INT NOT NULL DEFAULT 0,
    CONSTRAINT player_stash_pk PRIMARY KEY (player_id, item_id)
);

CREATE TABLE IF NOT EXISTS player_rewards (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    sid INT NOT NULL,
    pid INT NOT NULL DEFAULT 0,
    itemtype INT NOT NULL DEFAULT 0,
    count INT NOT NULL DEFAULT 1,
    attributes LONGBLOB DEFAULT NULL,
    CONSTRAINT player_rewards_unique UNIQUE (player_id, sid)
);

CREATE TABLE IF NOT EXISTS account_viplist (
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    description VARCHAR(128) NOT NULL DEFAULT '',
    icon SMALLINT NOT NULL DEFAULT 0,
    notify BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT account_viplist_unique UNIQUE (account_id, player_id)
);

CREATE INDEX IF NOT EXISTS idx_account_viplist_account_id ON account_viplist (account_id);

CREATE TABLE IF NOT EXISTS account_vipgroups (
    id INT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    customizable BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT account_vipgroups_pk PRIMARY KEY (id, account_id)
);

CREATE TABLE IF NOT EXISTS account_vipgrouplist (
    account_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    vipgroup_id INT NOT NULL,
    CONSTRAINT account_vipgrouplist_unique UNIQUE (account_id, player_id, vipgroup_id),
    CONSTRAINT account_vipgrouplist_vipgroup_fk FOREIGN KEY (vipgroup_id, account_id)
        REFERENCES account_vipgroups (id, account_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_account_vipgrouplist_account_id ON account_vipgrouplist (account_id);
