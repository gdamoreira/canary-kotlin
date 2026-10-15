-- Guild system tables, mirroring Canary's `guilds`, `guild_ranks`,
-- `guild_membership`, `guild_wars`, `guildwar_kills` and `guild_invites`
-- (schema.sql:338-455) and the persistence surface `IOGuild::loadGuild` /
-- `IOGuild::saveGuild` / `IOGuild::getGuildIdByName` / `IOGuild::getWarList`
-- (src/io/ioguild.cpp:15-77).
--
-- Canary creates the three canonical ranks ("The Leader" 3, "Vice-Leader" 2,
-- "Member" 1) through the `oncreate_guilds` trigger (schema.sql:423-427); the
-- Flyway/H2 migration cannot express that MySQL trigger, so the port resolves
-- those ranks from `GuildRank` whenever a guild has no loaded ranks (Guild.kt).

CREATE TABLE IF NOT EXISTS guilds (
    id BIGSERIAL PRIMARY KEY,
    level INT NOT NULL DEFAULT 1,
    name VARCHAR(255) NOT NULL,
    ownerid BIGINT NOT NULL DEFAULT 0,
    creationdata BIGINT NOT NULL DEFAULT 0,
    motd VARCHAR(255) NOT NULL DEFAULT '',
    residence INT NOT NULL DEFAULT 0,
    balance BIGINT NOT NULL DEFAULT 0,
    points INT NOT NULL DEFAULT 0,
    CONSTRAINT guilds_name_unique UNIQUE (name),
    CONSTRAINT guilds_owner_unique UNIQUE (ownerid)
);

CREATE TABLE IF NOT EXISTS guild_ranks (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    level INT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_guild_ranks_guild_id ON guild_ranks (guild_id);

CREATE TABLE IF NOT EXISTS guild_membership (
    player_id BIGINT NOT NULL PRIMARY KEY REFERENCES players (id) ON DELETE CASCADE,
    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
    rank_id BIGINT NOT NULL REFERENCES guild_ranks (id) ON DELETE CASCADE,
    nick VARCHAR(15) NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_guild_membership_guild_id ON guild_membership (guild_id);
CREATE INDEX IF NOT EXISTS idx_guild_membership_rank_id ON guild_membership (rank_id);

CREATE TABLE IF NOT EXISTS guild_wars (
    id BIGSERIAL PRIMARY KEY,
    guild1 BIGINT NOT NULL DEFAULT 0,
    guild2 BIGINT NOT NULL DEFAULT 0,
    name1 VARCHAR(255) NOT NULL DEFAULT '',
    name2 VARCHAR(255) NOT NULL DEFAULT '',
    status SMALLINT NOT NULL DEFAULT 0,
    started BIGINT NOT NULL DEFAULT 0,
    ended BIGINT NOT NULL DEFAULT 0,
    frags_limit SMALLINT NOT NULL DEFAULT 0,
    payment BIGINT NOT NULL DEFAULT 0,
    duration_days SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_guild_wars_guild1 ON guild_wars (guild1);
CREATE INDEX IF NOT EXISTS idx_guild_wars_guild2 ON guild_wars (guild2);

CREATE TABLE IF NOT EXISTS guildwar_kills (
    id BIGSERIAL PRIMARY KEY,
    killer VARCHAR(50) NOT NULL DEFAULT '',
    target VARCHAR(50) NOT NULL DEFAULT '',
    killerguild BIGINT NOT NULL DEFAULT 0,
    targetguild BIGINT NOT NULL DEFAULT 0,
    warid BIGINT NOT NULL REFERENCES guild_wars (id) ON DELETE CASCADE,
    "time" BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_guildwar_kills_warid ON guildwar_kills (warid);

CREATE TABLE IF NOT EXISTS guild_invites (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
    "date" BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, guild_id)
);

CREATE INDEX IF NOT EXISTS idx_guild_invites_guild_id ON guild_invites (guild_id);
