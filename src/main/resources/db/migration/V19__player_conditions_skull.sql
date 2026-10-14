-- Player condition/skull columns for the `IOLoginDataLoad` sub-loads.
--
-- Canary defines the three columns on `players` (schema.sql:76, 83-84):
--   * `conditions` mediumblob NOT NULL -> PostgreSQL/H2 `BYTEA`. Read by
--     `IOLoginDataLoad::loadPlayerConditions`
--     (src/io/functions/iologindata_load_player.cpp:257-275) through
--     `Condition::createCondition(PropStream&)` + `Condition::unserialize`
--     (src/creatures/combat/condition.cpp:189-275, 414-462); the port decodes
--     it in `IOLoginDataSubloads`.
--   * `skull` tinyint(1) NOT NULL DEFAULT 0 and `skulltime` bigint NOT NULL
--     DEFAULT 0 -> `SMALLINT`/`BIGINT`. Read by
--     `IOLoginDataLoad::loadPlayerSkullSystem`
--     (iologindata_load_player.cpp:322-342).
--
-- The blob is nullable here because `ALTER TABLE ... ADD COLUMN` cannot give
-- existing rows Canary's `mediumblob NOT NULL` default without a byte literal;
-- the loader treats a missing blob as "no stored conditions", which is exactly
-- what an empty blob means to the C++ loop.
ALTER TABLE players ADD COLUMN IF NOT EXISTS conditions BYTEA;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skull SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skulltime BIGINT NOT NULL DEFAULT 0;
