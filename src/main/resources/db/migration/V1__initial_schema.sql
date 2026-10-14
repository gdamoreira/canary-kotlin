-- Canary Database Initial Schema for PostgreSQL and H2 (PostgreSQL Mode)

CREATE TABLE IF NOT EXISTS server_config (
    config VARCHAR(50) NOT NULL PRIMARY KEY,
    "value" VARCHAR(256) NOT NULL DEFAULT ''
);

-- Portable seed (H2 and PostgreSQL): the table was just created, so a plain INSERT applies.
INSERT INTO server_config (config, "value") VALUES
('db_version', '1'),
('motd_hash', ''),
('motd_num', '1'),
('players_record', '0');

-- Table structure accounts
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    "password" VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL DEFAULT '',
    premdays INT NOT NULL DEFAULT 0,
    premdays_purchased INT NOT NULL DEFAULT 0,
    lastday BIGINT NOT NULL DEFAULT 0,
    "type" SMALLINT NOT NULL DEFAULT 1,
    coins INT NOT NULL DEFAULT 0,
    coins_transferable INT NOT NULL DEFAULT 0,
    tournament_coins INT NOT NULL DEFAULT 0,
    creation BIGINT NOT NULL DEFAULT 0,
    recruiter INT DEFAULT 0,
    house_bid_id INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts (email);
CREATE INDEX IF NOT EXISTS idx_accounts_password ON accounts ("password");

-- Table structure players
CREATE TABLE IF NOT EXISTS players (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    group_id INT NOT NULL DEFAULT 1,
    account_id BIGINT NOT NULL DEFAULT 0 REFERENCES accounts (id) ON DELETE CASCADE,
    level INT NOT NULL DEFAULT 1,
    vocation INT NOT NULL DEFAULT 0,
    health INT NOT NULL DEFAULT 150,
    healthmax INT NOT NULL DEFAULT 150,
    experience BIGINT NOT NULL DEFAULT 0,
    lookbody INT NOT NULL DEFAULT 0,
    lookfeet INT NOT NULL DEFAULT 0,
    lookhead INT NOT NULL DEFAULT 0,
    looklegs INT NOT NULL DEFAULT 0,
    looktype INT NOT NULL DEFAULT 136,
    lookaddons INT NOT NULL DEFAULT 0,
    maglevel INT NOT NULL DEFAULT 0,
    mana INT NOT NULL DEFAULT 0,
    manamax INT NOT NULL DEFAULT 0,
    manaspent BIGINT NOT NULL DEFAULT 0,
    soul INT NOT NULL DEFAULT 0,
    town_id INT NOT NULL DEFAULT 1,
    posx INT NOT NULL DEFAULT 1000,
    posy INT NOT NULL DEFAULT 1000,
    posz INT NOT NULL DEFAULT 7,
    cap DOUBLE PRECISION NOT NULL DEFAULT 400.0,
    sex INT NOT NULL DEFAULT 0,
    speed INT NOT NULL DEFAULT 220,
    lastlogin BIGINT NOT NULL DEFAULT 0,
    lastip BIGINT NOT NULL DEFAULT 0,
    "save" SMALLINT NOT NULL DEFAULT 1,
    deletion BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_players_account_id ON players (account_id);
CREATE INDEX IF NOT EXISTS idx_players_vocation ON players (vocation);

-- Table structure player_items
CREATE TABLE IF NOT EXISTS player_items (
    player_id BIGINT NOT NULL DEFAULT 0 REFERENCES players (id) ON DELETE CASCADE,
    pid INT NOT NULL DEFAULT 0,
    sid INT NOT NULL DEFAULT 0,
    itemtype INT NOT NULL DEFAULT 0,
    count INT NOT NULL DEFAULT 0,
    attributes BYTEA DEFAULT NULL,
    PRIMARY KEY (player_id, pid, sid)
);
