-- Advanced-feature player state, keyed by player id.
--
-- Mirrors the fields Canary persists for the advanced gameplay systems:
--   * wheel points        -> `player_wheeldata` per-slot points
--                            (player_wheel.cpp: loadDBPlayerSlotPointsOnLogin /
--                             saveDBPlayerSlotPointsOnLogout)
--   * offline training    -> `players.offlinetraining_time` / `offlinetraining_skill`
--   * daily reward        -> `players.isreward`
--   * forge               -> `players.forge_dusts` / `players.forge_dust_level`
--   * bestiary kills      -> storage key STORAGEVALUE_BESTIARYKILLCOUNT (raceid -> count)
--   * charms              -> `player_charms` (points, rune bits, expansion)
--   * mounts              -> player storage reserved mount range + current mount
--   * familiars           -> player storage reserved familiar range + lookFamiliarsType
--   * weapon proficiency  -> KV scope `weapon-proficiency` (weapon id -> experience/mastered)

CREATE TABLE IF NOT EXISTS player_feature_state (
    player_id BIGINT NOT NULL PRIMARY KEY REFERENCES players (id) ON DELETE CASCADE,
    offlinetraining_time INT NOT NULL DEFAULT 43200,
    offlinetraining_skill INT NOT NULL DEFAULT -1,
    daily_reward INT NOT NULL DEFAULT 1,
    forge_dusts BIGINT NOT NULL DEFAULT 0,
    forge_dust_level BIGINT NOT NULL DEFAULT 100,
    charm_points INT NOT NULL DEFAULT 0,
    minor_charm_echoes INT NOT NULL DEFAULT 0,
    max_charm_points INT NOT NULL DEFAULT 0,
    max_minor_charm_echoes INT NOT NULL DEFAULT 0,
    charm_expansion BOOLEAN NOT NULL DEFAULT FALSE,
    used_runes_bit INT NOT NULL DEFAULT 0,
    unlocked_runes_bit INT NOT NULL DEFAULT 0,
    current_mount INT NOT NULL DEFAULT 0,
    randomize_mount INT NOT NULL DEFAULT 0,
    familiar_look_type INT NOT NULL DEFAULT 0
);

-- Wheel point allocation per slot (`player_wheeldata` slot blob: slot -> uint16 points).
CREATE TABLE IF NOT EXISTS player_wheel_slots (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    slot SMALLINT NOT NULL,
    points INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, slot)
);

-- Bestiary kill counters (`Player::getBestiaryKillCount` raceid -> count).
CREATE TABLE IF NOT EXISTS player_bestiary_kill_counts (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    raceid INT NOT NULL,
    kill_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, raceid)
);

-- Unlocked mounts (`Player::tameMount` compressed into the reserved storage range).
CREATE TABLE IF NOT EXISTS player_mounts (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    mount_id INT NOT NULL,
    PRIMARY KEY (player_id, mount_id)
);

-- Owned familiars (`PlayerFamiliars::getReservedRange`: lookType per entry).
CREATE TABLE IF NOT EXISTS player_familiars (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    look_type INT NOT NULL,
    PRIMARY KEY (player_id, look_type)
);

-- Weapon proficiency (`WeaponProficiencyData`: experience + mastered per weapon id).
CREATE TABLE IF NOT EXISTS player_weapon_proficiency (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    weapon_id INT NOT NULL,
    experience INT NOT NULL DEFAULT 0,
    mastered BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (player_id, weapon_id)
);
