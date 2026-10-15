-- Forge history entries, mirroring Canary's `forge_history` table
-- (schema.sql:314-329) and `PlayerForgeHistory::load` / `save`
-- (src/creatures/players/components/player_forge_history.cpp:43-113).
--
-- Canary only reads/writes `action_type`, `description`, `done_at` and
-- `is_success`; `bonus`, `cost`, `gained` and `done_at_date` exist in Canary's
-- schema for table parity. The unique key on (`player_id`, `done_at`) backs the
-- `createdAt` collision-advance in `PlayerForgeHistory::add` (cpp:24-34).

CREATE TABLE IF NOT EXISTS forge_history (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    action_type INT NOT NULL DEFAULT 0,
    description TEXT NOT NULL,
    is_success BOOLEAN NOT NULL DEFAULT FALSE,
    bonus SMALLINT NOT NULL DEFAULT 0,
    done_at BIGINT NOT NULL,
    done_at_date TIMESTAMP DEFAULT NOW(),
    cost BIGINT NOT NULL DEFAULT 0,
    gained BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT forge_history_unique_player_done_at UNIQUE (player_id, done_at)
);
