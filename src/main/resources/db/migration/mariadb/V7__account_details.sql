-- Account detail persistence: coin ledger columns, coin-transaction history,
-- sessions and bans.
--
-- Canary reads and writes the coin balances through the `coinTypeToColumn` map
-- of `AccountRepositoryDB` (`coins`, `coins_tournament`, `coins_transferable`;
-- src/account/account_repository_db.cpp:20-25, read/written by
-- getCoins/setCoins/removeCoins at :97-230). V1 used the schema.sql:24 name
-- `tournament_coins`, while the repository (and the Kotlin `AccountCoinColumns`
-- mirror) addresses `coins_tournament`, so V7 renames the column to the name
-- the repository actually queries.
--
-- `coins_transactions` mirrors schema.sql:34-48 and is written by
-- `AccountRepositoryDB::registerCoinsTransaction`
-- (src/account/account_repository_db.cpp:232-262).
--
-- `account_sessions` mirrors schema.sql:852-859; `AccountRepositoryDB::loadBySession`
-- (src/account/account_repository_db.cpp:39-53) joins it through the SHA-256 /
-- SHA-1 session id and reads `expires`.
--
-- `account_bans` / `account_ban_history` mirror schema.sql:166-204 and are used
-- by `IOBan::isAccountBanned` (the expired-ban move-to-history path) in
-- src/creatures/players/management/ban.cpp:80-108.

ALTER TABLE accounts RENAME COLUMN tournament_coins TO coins_tournament;

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS coins INT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS coins_transferable INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS coins_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    type SMALLINT NOT NULL,
    coin_type SMALLINT NOT NULL DEFAULT 1,
    amount INT NOT NULL,
    description VARCHAR(3500) NOT NULL,
    `timestamp` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_coins_transactions_account_id ON coins_transactions (account_id);

CREATE TABLE IF NOT EXISTS account_sessions (
    id VARCHAR(191) NOT NULL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    expires BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_account_sessions_account_id ON account_sessions (account_id);

CREATE TABLE IF NOT EXISTS account_bans (
    account_id BIGINT NOT NULL PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    reason VARCHAR(255) NOT NULL,
    banned_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    banned_by BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_account_bans_banned_by ON account_bans (banned_by);

CREATE TABLE IF NOT EXISTS account_ban_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    reason VARCHAR(255) NOT NULL,
    banned_at BIGINT NOT NULL,
    expired_at BIGINT NOT NULL,
    banned_by BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_account_ban_history_account_id ON account_ban_history (account_id);
CREATE INDEX IF NOT EXISTS idx_account_ban_history_banned_by ON account_ban_history (banned_by);
