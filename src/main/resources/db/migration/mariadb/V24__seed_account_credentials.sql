-- Align the V2 seed with the login-server contract: the opentibiabr
-- login-server authenticates by `email` and verifies the stored SHA-1 hash, so
-- the seeded account becomes `@1` / `1`. Guarded so it only rewrites the
-- untouched seed (idempotent, never clobbers later edits).

UPDATE accounts
SET email = '@1', `password` = '356a192b7913b04c54574d18c28d46e6395428ab'
WHERE id = 1 AND name = '1' AND email = 'test@opentibiabr.com';
