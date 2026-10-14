-- Player combat-skill persistence for the `IOLoginData` load/save path.
--
-- Canary stores the 13 `skills_t` slots (`SKILL_FIST..SKILL_MANA_LEECH_AMOUNT`,
-- src/creatures/creatures_definitions.hpp:466-487) as level/tries column pairs on
-- `players` (schema.sql:102-127); the maximum level is read by
-- `IOLoginDataLoad::loadPlayerSkill` (iologindata_load_player.cpp:344-369) and
-- written back by `IOLoginDataSave::savePlayerFirst`
-- (iologindata_save_player.cpp:325-372). The seven combat skills default to
-- level 10, the six leech/critical slots to 0, exactly like `schema.sql`.

ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_fist INT NOT NULL DEFAULT 10;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_fist_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_club INT NOT NULL DEFAULT 10;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_club_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_sword INT NOT NULL DEFAULT 10;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_sword_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_axe INT NOT NULL DEFAULT 10;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_axe_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_dist INT NOT NULL DEFAULT 10;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_dist_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_shielding INT NOT NULL DEFAULT 10;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_shielding_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_fishing INT NOT NULL DEFAULT 10;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_fishing_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_critical_hit_chance INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_critical_hit_chance_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_critical_hit_damage INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_critical_hit_damage_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_life_leech_chance INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_life_leech_chance_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_life_leech_amount INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_life_leech_amount_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_mana_leech_chance INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_mana_leech_chance_tries BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_mana_leech_amount INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_mana_leech_amount_tries BIGINT NOT NULL DEFAULT 0;
