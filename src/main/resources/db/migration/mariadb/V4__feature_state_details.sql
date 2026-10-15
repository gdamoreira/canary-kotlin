-- Detail tables for the advanced-feature player state introduced in V3.
--
-- Covers the state Canary keeps outside `player_feature_state`'s scalar
-- columns:
--   * bestiary charm assignments -> `Player::charmsArray`
--     (`CharmInfo.raceId` + `CharmInfo.tier`, saved as the `player_charms.charms`
--      blob in iologindata_save_player.cpp:487-497 and loaded in
--      iologindata_load_player.cpp:495-505)
--   * weapon-proficiency perks   -> `WeaponProficiencyData::perks` serialized
--     under the `weapon-proficiency` KV scope (weapon_proficiency.cpp:290,
--     serializePerk at weapon_proficiency.cpp:347-361)
--   * wheel revealed gems        -> `wheel-of-destiny/gems/revealed/<uuid>` KV
--     entries (player_wheel.cpp:4139-4152)
--   * wheel active gems          -> `wheel-of-destiny/gems/active/<affinity>`
--     gem uuid references (player_wheel.cpp:1751-1760)
--   * wheel gem modifier grades  -> `wheel-of-destiny/gems/<fragment>/<pos>/grade`
--     KV entries (player_wheel.cpp:1843-1878)

-- Charm rune assignment (`charm_id` -> race/tier), one row per assigned charm.
CREATE TABLE IF NOT EXISTS player_bestiary_charms (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    charm_id INT NOT NULL,
    race_id INT NOT NULL DEFAULT 0,
    tier INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, charm_id)
);

-- Ordered weapon-proficiency perks (`WeaponProficiencyData::perks`).
CREATE TABLE IF NOT EXISTS player_weapon_proficiency_perks (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    weapon_id INT NOT NULL,
    perk_order INT NOT NULL,
    perk_level INT NOT NULL DEFAULT 0,
    perk_index INT NOT NULL DEFAULT 0,
    perk_type INT NOT NULL DEFAULT 0,
    perk_value DOUBLE NOT NULL DEFAULT 0,
    spell_id INT NOT NULL DEFAULT 0,
    perk_range INT NOT NULL DEFAULT 0,
    bestiary_id INT NOT NULL DEFAULT 0,
    bestiary_name VARCHAR(255) NOT NULL DEFAULT '',
    augment_type INT NOT NULL DEFAULT 0,
    skill_id INT NOT NULL DEFAULT 0,
    element INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, weapon_id, perk_order)
);

-- Revealed wheel gems (`PlayerWheelGem`), ordered by `gem_order`.
CREATE TABLE IF NOT EXISTS player_wheel_gems (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    gem_order INT NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    affinity INT NOT NULL DEFAULT 0,
    quality INT NOT NULL DEFAULT 0,
    basic_modifier1 INT NOT NULL DEFAULT 0,
    basic_modifier2 INT,
    supreme_modifier INT,
    PRIMARY KEY (player_id, gem_order)
);

-- Active wheel gems: affinity index -> revealed gem order.
CREATE TABLE IF NOT EXISTS player_wheel_active_gems (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    affinity INT NOT NULL,
    gem_order INT NOT NULL,
    PRIMARY KEY (player_id, affinity)
);

-- Wheel gem modifier grades for non-zero entries only
-- (0 = Greater/supreme fragment, 1 = Lesser/basic fragment,
--  mirroring WheelFragmentType_t).
CREATE TABLE IF NOT EXISTS player_wheel_gem_grades (
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    fragment_type INT NOT NULL,
    gem_position INT NOT NULL,
    grade INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, fragment_type, gem_position)
);
