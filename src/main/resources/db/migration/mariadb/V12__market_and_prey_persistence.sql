-- Market and prey persistence, mirroring Canary's `market_offers`,
-- `market_history`, `player_prey` and `player_taskhunt` tables (schema.sql:516-551,
-- 717-754) and because the expired-offer escrow refunds bank balance through
-- `IOLoginData::increaseBankBalance` (iologindata.cpp:357-361) the `players`
-- row needs Canary's `balance` column (schema.sql:98).
--
-- Type notes versus the MySQL schema:
--  * `raceid`, `bonus_percentage` and `bonus_time` are integer columns here
--    (MySQL declares them varchar but Canary always reads them through
--    `DBResult::getNumber`, so the round-trip is identical).
--  * `monster_list` stays a binary column: Canary writes the raw
--    `PropWriteStream` bytes of the uint16 race ids.

CREATE TABLE IF NOT EXISTS market_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    sale SMALLINT NOT NULL DEFAULT 0,
    itemtype INT NOT NULL,
    amount INT NOT NULL,
    price BIGINT NOT NULL DEFAULT 0,
    expires_at BIGINT NOT NULL,
    inserted BIGINT NOT NULL,
    state SMALLINT NOT NULL,
    tier SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_market_history_player_sale ON market_history (player_id, sale);

CREATE TABLE IF NOT EXISTS market_offers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    sale SMALLINT NOT NULL DEFAULT 0,
    itemtype INT NOT NULL,
    amount INT NOT NULL,
    created BIGINT NOT NULL,
    anonymous SMALLINT NOT NULL DEFAULT 0,
    price BIGINT NOT NULL DEFAULT 0,
    tier SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_market_offers_sale_itemtype ON market_offers (sale, itemtype);
CREATE INDEX IF NOT EXISTS idx_market_offers_created ON market_offers (created);
CREATE INDEX IF NOT EXISTS idx_market_offers_player_id ON market_offers (player_id);

CREATE TABLE IF NOT EXISTS player_prey (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    slot SMALLINT NOT NULL,
    state SMALLINT NOT NULL,
    raceid INT NOT NULL,
    option SMALLINT NOT NULL,
    bonus_type SMALLINT NOT NULL,
    bonus_rarity SMALLINT NOT NULL,
    bonus_percentage INT NOT NULL,
    bonus_time INT NOT NULL,
    free_reroll BIGINT NOT NULL,
    monster_list LONGBLOB,
    CONSTRAINT player_prey_pk PRIMARY KEY (player_id, slot)
);

CREATE TABLE IF NOT EXISTS player_taskhunt (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    slot SMALLINT NOT NULL,
    state SMALLINT NOT NULL,
    raceid INT NOT NULL,
    upgrade SMALLINT NOT NULL,
    rarity SMALLINT NOT NULL,
    kills INT NOT NULL,
    disabled_time BIGINT NOT NULL,
    free_reroll BIGINT NOT NULL,
    monster_list LONGBLOB,
    CONSTRAINT player_taskhunt_pk PRIMARY KEY (player_id, slot)
);

ALTER TABLE players ADD COLUMN IF NOT EXISTS balance BIGINT NOT NULL DEFAULT 0;
