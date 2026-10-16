-- `players_online` (schema.sql: online player registry consumed by
-- `Game::updatePlayersOnline`, game.cpp:12608-12653).
CREATE TABLE IF NOT EXISTS players_online (
    player_id INT NOT NULL,
    PRIMARY KEY (player_id)
);
