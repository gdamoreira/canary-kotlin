-- Wave 36, task A4: bestiary/bosstiary persistence that was still missing from
-- the port. The scalar bestiary charm state, kill counters and charm
-- assignments already live in `player_feature_state`,
-- `player_bestiary_kill_counts` and `player_bestiary_charms` (V3/V4); this
-- migration only adds the pieces Canary keeps elsewhere:
--
--   * `player_charms`.`tracker list`      (schema.sql:576-591)
--   * `players`.`animus_mastery`          (schema.sql:156,
--                                          AnimusMastery::serialize)
--   * `player_bosstiary`                  (schema.sql:755-766)
--   * `players`.`boss_points`             (schema.sql:154)
--   * `boosted_boss`                      (schema.sql:266-279)
--
-- Guild tables are unchanged: `guilds`, `guild_ranks`, `guild_wars` and
-- `guild_invites` already exist in V9.

-- Bestiary tracker list: the uint16 monster race ids the player tracks in the
-- Cyclopedia, insertion ordered like Canary's serialized blob.
CREATE TABLE IF NOT EXISTS player_bestiary_tracker (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    position INT NOT NULL,
    raceid INT NOT NULL,
    PRIMARY KEY (player_id, position)
);

-- Animus mastery: the ordered lowercased monster names Canary serializes into
-- `players`.`animus_mastery` (`AnimusMastery::add` lowercases,
-- animus_mastery.cpp:27-32).
CREATE TABLE IF NOT EXISTS player_animus_mastery (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    position INT NOT NULL,
    monster_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (player_id, position)
);

-- Bosstiary boss slots, removal counter and tracker (Canary `player_bosstiary`,
-- schema.sql:755-766). `tracker` is the uint16 race-id sequence Canary
-- serializes into the `tracker` blob.
CREATE TABLE IF NOT EXISTS player_bosstiary (
    player_id BIGINT NOT NULL PRIMARY KEY REFERENCES players (id) ON DELETE CASCADE,
    boss_id_slot_one INT NOT NULL DEFAULT 0,
    boss_id_slot_two INT NOT NULL DEFAULT 0,
    remove_times INT NOT NULL DEFAULT 1,
    tracker LONGBLOB
);

-- Boss points (`Player::getBossPoints` / `Player::addBossPoints`,
-- player.cpp:12674-12686; persisted in `players`.`boss_points`, schema.sql:154).
ALTER TABLE players ADD COLUMN IF NOT EXISTS boss_points INT NOT NULL DEFAULT 0;

-- Daily boosted boss (Canary `boosted_boss`, schema.sql:266-279). Canary's
-- schema seeds the `('default', 0, 0)` row; `IOBosstiary::loadBoostedBoss`
-- expects exactly that row.
CREATE TABLE IF NOT EXISTS boosted_boss (
    boostname TEXT,
    `date` VARCHAR(250) NOT NULL DEFAULT '',
    raceid VARCHAR(250) NOT NULL DEFAULT '',
    looktypeEx INT NOT NULL DEFAULT 0,
    looktype INT NOT NULL DEFAULT 136,
    lookfeet INT NOT NULL DEFAULT 0,
    looklegs INT NOT NULL DEFAULT 0,
    lookhead INT NOT NULL DEFAULT 0,
    lookbody INT NOT NULL DEFAULT 0,
    lookaddons INT NOT NULL DEFAULT 0,
    lookmount INT DEFAULT 0,
    PRIMARY KEY (`date`)
);

INSERT INTO boosted_boss (boostname, `date`, raceid)
SELECT 'default', '0', '0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM boosted_boss);
